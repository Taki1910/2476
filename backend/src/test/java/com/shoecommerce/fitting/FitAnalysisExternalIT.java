package com.shoecommerce.fitting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FitAnalysisExternalIT {
    private static final String BOUNDARY = "----shoe-commerce-fit-boundary";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void runsTheGuestMultipartFlowAgainstSqlBackedFitAndInventoryData() throws Exception {
        Fixture fixture = fixture();
        byte[] image = demoFixture("valid-a4-foot.png");
        Browser browser = new Browser();

        assertThat(post(browser, fixture.productId(), "Ink", image, false).statusCode()).isEqualTo(403);

        HttpResponse<String> detail = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products/" + fixture.productId()))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).get("fitSupported").asBoolean()).isTrue();

        HttpResponse<String> response = post(browser, fixture.productId(), "Ink", image, true);
        JsonNode body = json.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(body.get("recommendedSize").asString()).isEqualTo("40");
        assertThat(body.get("footLengthMm").asDouble()).isBetween(246d, 254d);
        assertThat(body.get("footWidthMm").asDouble()).isBetween(94d, 101d);
        assertThat(body.get("recommendedAvailable").asBoolean()).isTrue();
        assertThat(body.get("selectedColorAvailable").asBoolean()).isFalse();
        assertThat(body.get("availableColors")).extracting(JsonNode::asString).containsExactly("Chalk");
    }

    @Test
    void returnsSafeRetakeAndInputErrorsWithoutSkippingTheRealAnalyzer() throws Exception {
        Fixture fixture = fixture();
        Browser browser = new Browser();

        HttpResponse<String> retake = post(browser, fixture.productId(), null, demoFixture("invalid-no-reference.png"), true);
        assertThat(retake.statusCode()).isEqualTo(200);
        JsonNode retakeBody = json.readTree(retake.body());
        assertThat(retakeBody.get("status").asString()).isEqualTo("RETAKE");
        assertThat(retakeBody.get("retakeReason").asString()).isEqualTo("REFERENCE_NOT_FOUND");

        HttpResponse<String> malformed = post(browser, fixture.productId(), null, new byte[] {1, 2, 3}, true);
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(json.readTree(malformed.body()).get("code").asString()).isEqualTo("FIT_IMAGE_FORMAT_UNSUPPORTED");

        HttpResponse<String> oversized = post(browser, fixture.productId(), null,
                new byte[FitImageAnalyzer.MAX_BYTES + 1024], true);
        assertThat(oversized.statusCode()).isEqualTo(400);
        assertThat(json.readTree(oversized.body()).get("code").asString()).isEqualTo("FIT_IMAGE_TOO_LARGE");
    }

    @Test
    void marksAnExistingProductWithoutACompleteProfileAsUnsupported() throws Exception {
        UUID product = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(public_id, name, entity_version, created_at) VALUES (?, ?, 0, ?)",
                product, "Fit unsupported " + product, Timestamp.from(Instant.now()));

        Browser browser = new Browser();
        HttpResponse<String> response = post(browser, product, null, demoFixture("valid-a4-foot.png"), true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("status").asString()).isEqualTo("UNSUPPORTED_PRODUCT");
    }

    private Fixture fixture() {
        UUID product = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID inkVariant = UUID.randomUUID();
        UUID chalkVariant = UUID.randomUUID();
        String suffix = product.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO catalog_product(public_id, name, entity_version, created_at) VALUES (?, ?, 0, ?)",
                product, "Fit acceptance " + suffix, now);
        long productId = jdbc.queryForObject("SELECT id FROM catalog_product WHERE public_id = ?", Long.class, product);
        jdbc.update("INSERT INTO catalog_shoe_fit_profile(public_id, product_id, size_system, fit_tendency, width_profile, created_at) VALUES (?, ?, 'EU', 'TRUE_TO_SIZE', 'REGULAR', ?)",
                profile, productId, now);
        long profileId = jdbc.queryForObject("SELECT id FROM catalog_shoe_fit_profile WHERE public_id = ?", Long.class, profile);
        jdbc.update("INSERT INTO catalog_shoe_fit_size_range(profile_id, size_label, min_foot_length_mm, max_foot_length_mm, min_foot_width_mm, max_foot_width_mm) VALUES (?, '40', 246, 254, 92, 102)", profileId);
        jdbc.update("INSERT INTO catalog_product_variant(public_id, product_id, sku, size, color, lifecycle_status, entity_version, created_at) VALUES (?, ?, ?, '40', 'Ink', 'PUBLISHED', 0, ?)",
                inkVariant, productId, "FIT-INK-" + suffix, now);
        jdbc.update("INSERT INTO catalog_product_variant(public_id, product_id, sku, size, color, lifecycle_status, entity_version, created_at) VALUES (?, ?, ?, '40', 'Chalk', 'PUBLISHED', 0, ?)",
                chalkVariant, productId, "FIT-CHALK-" + suffix, now);
        long inkId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, inkVariant);
        long chalkId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, chalkVariant);
        jdbc.update("INSERT INTO pricing_variant_price(public_id, variant_id, amount, entity_version, valid_from, valid_to, updated_at) VALUES (?, ?, 1200000, 0, ?, NULL, ?)",
                UUID.randomUUID(), inkId, now, now);
        jdbc.update("INSERT INTO pricing_variant_price(public_id, variant_id, amount, entity_version, valid_from, valid_to, updated_at) VALUES (?, ?, 1200000, 0, ?, NULL, ?)",
                UUID.randomUUID(), chalkId, now, now);
        jdbc.update("INSERT INTO org_branch(public_id, code, name, enabled, created_at) VALUES (?, ?, ?, 1, ?)",
                branch, "FIT" + suffix, "Fit acceptance", now);
        long branchId = jdbc.queryForObject("SELECT id FROM org_branch WHERE public_id = ?", Long.class, branch);
        jdbc.update("INSERT INTO org_location(public_id, branch_id, code, name, enabled, created_at) VALUES (?, ?, 'FLOOR', 'Fit floor', 1, ?)",
                location, branchId, now);
        long locationId = jdbc.queryForObject("SELECT id FROM org_location WHERE public_id = ?", Long.class, location);
        jdbc.update("INSERT INTO inventory_balance(variant_id, location_id, on_hand, reserved, entity_version, updated_at) VALUES (?, ?, 2, 0, 0, ?)",
                chalkId, locationId, now);
        return new Fixture(product);
    }

    private HttpResponse<String> post(Browser browser, UUID productId, String selectedColor, byte[] image, boolean withCsrf)
            throws Exception {
        String query = selectedColor == null ? "" : "?selectedColor=" + URLEncoder.encode(selectedColor, StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/v1/storefront/products/" + productId + "/fit-analysis" + query))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(image)));
        if (withCsrf) {
            Csrf csrf = csrf(browser);
            request.header(csrf.header(), csrf.token());
        }
        return browser.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Csrf csrf(Browser browser) throws Exception {
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode body = json.readTree(response.body());
        return new Csrf(body.get("headerName").asString(), body.get("token").asString());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static byte[] multipart(byte[] image) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"foot.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(image);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static byte[] demoFixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("..", "docs", "demo-assets", "fit", name));
    }

    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private record Fixture(UUID productId) { }
    private record Csrf(String header, String token) { }
}
