package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.CorrelationIdFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice1ExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test void provesCatalogPriceScopedStockPublicationAndConstraints() {
        SessionPrincipal admin = bootstrapAdmin();
        UUID operationsId = identities.createAccount(admin, "ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        UUID cashierId = identities.createAccount(admin, "vs1-cashier@example.com", PASSWORD, RoleCode.CASHIER);
        UUID branch = scopes.createBranch(admin, "VS1-HCM", "Ho Chi Minh");
        UUID location = scopes.createLocation(admin, branch, "VS1-HCM-FLOOR", "Sales floor");
        UUID otherBranch = scopes.createBranch(admin, "VS1-HN", "Ha Noi");
        UUID otherLocation = scopes.createLocation(admin, otherBranch, "VS1-HN-FLOOR", "Other floor");
        scopes.setAssignment(admin, operationsId, branch, null, true);
        SessionPrincipal operations = principal("ops@example.com");

        assertThatThrownBy(() -> catalog.createProduct(admin, "Denied")).isInstanceOf(AccessDeniedException.class);
        UUID product = catalog.createProduct(operations, "Runner");
        UUID noPrice = catalog.createVariant(operations, product, "RUN-NOPRICE", "42", "Black");
        assertThatThrownBy(() -> catalog.publish(operations, noPrice)).isInstanceOf(IllegalStateException.class);
        catalog.setPrice(operations, noPrice, 100_000);
        assertThatThrownBy(() -> catalog.publish(operations, noPrice)).isInstanceOf(IllegalStateException.class);

        UUID variant = catalog.createVariant(operations, product, "RUN-42-BLK", "42", "Black");
        catalog.setPrice(operations, variant, 120_000);
        assertThatThrownBy(() -> catalog.setStock(operations, variant, location, 4)).isInstanceOf(AccessDeniedException.class);
        scopes.setAssignment(admin, operationsId, branch, location, true);
        scopes.setAssignment(admin, cashierId, branch, location, true);
        SessionPrincipal locationOperations = principal("ops@example.com");
        SessionPrincipal cashier = principal("vs1-cashier@example.com");
        assertThatThrownBy(() -> catalog.setStock(cashier, variant, location, 4)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> catalog.setStock(locationOperations, variant, otherLocation, 4)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> catalog.setStock(locationOperations, variant, location, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id JOIN org_location locations ON locations.id = balances.location_id WHERE variants.public_id = ? AND locations.public_id = ?", Integer.class, variant, location)).isZero();
        catalog.setStock(locationOperations, variant, location, 4);
        assertThatThrownBy(() -> catalog.readPublished(locationOperations, variant)).isInstanceOf(IllegalArgumentException.class);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> catalog.publish(locationOperations, variant)).isInstanceOf(DataAccessException.class);
        } finally { MDC.remove(CorrelationIdFilter.MDC_KEY); }
        assertThatThrownBy(() -> catalog.readPublished(locationOperations, variant)).isInstanceOf(IllegalArgumentException.class);
        catalog.publish(locationOperations, variant);
        assertThat(catalog.readPublished(locationOperations, variant).sku()).isEqualTo("RUN-42-BLK");
        assertThatThrownBy(() -> catalog.createVariant(locationOperations, product, "RUN-42-BLK", "43", "Blue"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long variantDbId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, variant);
        UUID constraintVariant = catalog.createVariant(locationOperations, product, "RUN-CONSTRAINT", "44", "Red");
        Long constraintVariantDbId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, constraintVariant);
        Long locationDbId = jdbc.queryForObject("SELECT id FROM org_location WHERE public_id = ?", Long.class, location);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pricing_variant_price(variant_id, amount, entity_version, updated_at) VALUES (?, 0, 0, ?)", constraintVariantDbId, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_balance(variant_id, location_id, on_hand, entity_version, updated_at) VALUES (?, ?, -1, 0, ?)", variantDbId, locationDbId, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_balance(variant_id, location_id, on_hand, entity_version, updated_at) VALUES (?, ?, 1, 0, ?)", variantDbId, locationDbId, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void provesTheApiVerticalSlice() throws Exception {
        SessionPrincipal admin = bootstrapAdmin();
        UUID operationsId = identities.createAccount(admin, "api-ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        UUID branch = scopes.createBranch(admin, "DN", "Da Nang");
        UUID location = scopes.createLocation(admin, branch, "DN-FLOOR", "Sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        Browser browser = new Browser();
        assertThat(login(browser, "api-ops@example.com").statusCode()).isEqualTo(200);
        UUID product = id(request(browser, "POST", "/api/v1/catalog/products", "{\"name\":\"API Runner\"}", 201));
        UUID variant = id(request(browser, "POST", "/api/v1/catalog/products/" + product + "/variants", "{\"sku\":\"API-RUN-42\",\"size\":\"42\",\"color\":\"White\"}", 201));
        HttpResponse<String> draft = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/catalog/sellable/variants/" + variant)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(draft.statusCode()).isEqualTo(400);
        assertThat(request(browser, "POST", "/api/v1/catalog/variants/" + variant + "/publish", "{}", 400).statusCode()).isEqualTo(400);
        request(browser, "PUT", "/api/v1/pricing/variants/" + variant, "{\"amount\":125000}", 204);
        request(browser, "PUT", "/api/v1/inventory/variants/" + variant + "/locations/" + location, "{\"onHand\":3}", 204);
        request(browser, "POST", "/api/v1/catalog/variants/" + variant + "/publish", "{}", 204);
        HttpResponse<String> read = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/catalog/sellable/variants/" + variant)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("API-RUN-42").contains("125000").contains("VND");
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID(); String login = "admin-" + id + "@example.com"; Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }
    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private HttpResponse<String> login(Browser browser, String username) throws Exception { return request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, 200, "application/x-www-form-urlencoded"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected) throws Exception { return request(browser, method, path, body, expected, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected, String type) throws Exception {
        Csrf csrf = csrf(browser);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = browser.client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); assertThat(response.statusCode()).isEqualTo(expected); return response;
    }
    private Csrf csrf(Browser browser) throws Exception { HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()); JsonNode node = json.readTree(response.body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private UUID id(HttpResponse<String> response) throws Exception { return UUID.fromString(json.readTree(response.body()).get("id").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
}
