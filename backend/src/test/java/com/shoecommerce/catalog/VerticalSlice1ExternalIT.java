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
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.platform.api.CorrelationIdFilter;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.pricing.VariantPrice;

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
    @Autowired InventoryAdjustmentService adjustments;
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
        UUID noPrice = catalog.createVariant(operations, product, "RUN-NOPRICE", "41", "Grey");
        assertThatThrownBy(() -> catalog.publish(operations, noPrice))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_PRICE_REQUIRED"));
        catalog.setPrice(operations, noPrice, 100_000);
        assertThatThrownBy(() -> catalog.publish(operations, noPrice))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_ON_HAND_STOCK_REQUIRED"));

        UUID variant = catalog.createVariant(operations, product, "RUN-42-BLK", "42", "Black");
        catalog.setPrice(operations, variant, 120_000);
        assertThatThrownBy(() -> adjustments.adjust(operations, variant, location, 4, "Initial receipt", "denied-branch"))
                .isInstanceOf(AccessDeniedException.class);
        scopes.setAssignment(admin, operationsId, branch, location, true);
        scopes.setAssignment(admin, cashierId, branch, location, true);
        SessionPrincipal locationOperations = principal("ops@example.com");
        SessionPrincipal cashier = principal("vs1-cashier@example.com");
        assertThatThrownBy(() -> adjustments.adjust(cashier, variant, location, 4, "Initial receipt", "denied-role"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adjustments.adjust(locationOperations, variant, otherLocation, 4, "Initial receipt", "denied-location"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adjustments.adjust(locationOperations, variant, location, -1, "Initial receipt", "negative"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id JOIN org_location locations ON locations.id = balances.location_id WHERE variants.public_id = ? AND locations.public_id = ?", Integer.class, variant, location)).isZero();
        adjustments.adjust(locationOperations, variant, location, 4, "Initial receipt", "stock-main");
        assertThatThrownBy(() -> catalog.readPublished(locationOperations, variant)).isInstanceOf(IllegalArgumentException.class);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> catalog.publish(locationOperations, variant)).isInstanceOf(DataAccessException.class);
        } finally { MDC.remove(CorrelationIdFilter.MDC_KEY); }
        assertThatThrownBy(() -> catalog.readPublished(locationOperations, variant)).isInstanceOf(IllegalArgumentException.class);
        catalog.publish(locationOperations, variant);
        assertThat(catalog.readPublished(locationOperations, variant).sku()).isEqualTo("RUN-42-BLK");
        UUID priceBoundary = catalog.createVariant(locationOperations, product, "RUN-PRICE-MAX", "45", "Green");
        catalog.setPrice(locationOperations, priceBoundary, 1);
        catalog.setPrice(locationOperations, priceBoundary, VariantPrice.MAX_AMOUNT);
        assertThatThrownBy(() -> catalog.setPrice(locationOperations, priceBoundary, VariantPrice.MAX_AMOUNT + 1))
                .isInstanceOf(IllegalArgumentException.class);
        UUID fullyReserved = catalog.createVariant(locationOperations, product, "RUN-RESERVED", "47", "Navy");
        catalog.setPrice(locationOperations, fullyReserved, 100_000);
        adjustments.adjust(locationOperations, fullyReserved, location, 10, "Initial receipt", "fully-reserved");
        jdbc.update("""
                UPDATE balances SET reserved = on_hand
                FROM inventory_balance balances
                JOIN catalog_product_variant variants ON variants.id = balances.variant_id
                WHERE variants.public_id = ?
                """, fullyReserved);
        catalog.publish(locationOperations, fullyReserved);
        assertThat(catalog.readPublished(locationOperations, fullyReserved).sku()).isEqualTo("RUN-RESERVED");
        UUID retiredOption = catalog.createVariant(locationOperations, product, "RUN-RETIRED-OPTION", "48", "Copper");
        catalog.retire(locationOperations, retiredOption, 0);
        catalog.createVariant(locationOperations, product, "RUN-REPLACEMENT-OPTION", "048.00", " copper ");
        long retiredVersion = jdbc.queryForObject(
                "SELECT entity_version FROM catalog_product_variant WHERE public_id=?", Long.class, retiredOption);
        assertThatThrownBy(() -> catalog.restore(locationOperations, retiredOption, retiredVersion))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_OPTION_ALREADY_EXISTS"));
        UUID disabledStock = catalog.createVariant(locationOperations, product, "RUN-DISABLED-STOCK", "46", "Grey");
        catalog.setPrice(locationOperations, disabledStock, 100_000);
        adjustments.adjust(locationOperations, disabledStock, location, 1, "Initial receipt", "disabled-stock");
        jdbc.update("UPDATE org_location SET enabled = 0 WHERE public_id = ?", location);
        assertThatThrownBy(() -> catalog.publish(locationOperations, disabledStock))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_ON_HAND_STOCK_REQUIRED"));
        assertThatThrownBy(() -> catalog.createVariant(locationOperations, product, "RUN-42-BLK", "43", "Blue"))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_SKU_ALREADY_EXISTS"));
        Long variantDbId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, variant);
        UUID constraintVariant = catalog.createVariant(locationOperations, product, "RUN-CONSTRAINT", "44", "Red");
        Long constraintVariantDbId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, constraintVariant);
        Long locationDbId = jdbc.queryForObject("SELECT id FROM org_location WHERE public_id = ?", Long.class, location);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pricing_variant_price(variant_id, amount, entity_version, updated_at) VALUES (?, 0, 0, ?)", constraintVariantDbId, Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pricing_variant_price(variant_id, amount, entity_version, updated_at) VALUES (?, ?, 0, ?)", constraintVariantDbId, VariantPrice.MAX_AMOUNT + 1, Timestamp.from(Instant.now())))
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
        assertThat(request(browser, "POST", "/api/v1/catalog/variants/" + variant + "/publish", "{\"expectedEntityVersion\":0}", 409).statusCode()).isEqualTo(409);
        request(browser, "PUT", "/api/v1/pricing/variants/" + variant, "{\"amount\":125000}", 204);
        request(browser, "PUT", "/api/v1/inventory/variants/" + variant + "/locations/" + location,
                "{\"onHand\":3,\"reason\":\"Initial receipt\"}", 200);
        request(browser, "POST", "/api/v1/catalog/variants/" + variant + "/publish", "{\"expectedEntityVersion\":0}", 204);
        HttpResponse<String> read = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/catalog/sellable/variants/" + variant)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).contains("API-RUN-42").contains("125000").contains("VND");
    }

    @Test void managesControlledProductEvidenceWithAuthorizationConcurrencyAndAudit() throws Exception {
        SessionPrincipal admin = bootstrapAdmin();
        UUID operationsId = identities.createAccount(admin, "evidence-ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, "evidence-customer@example.com", PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, "EVIDENCE", "Evidence branch");
        UUID location = scopes.createLocation(admin, branch, "EVIDENCE-FLOOR", "Evidence floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);

        Browser operations = new Browser();
        assertThat(login(operations, "evidence-ops@example.com").statusCode()).isEqualTo(200);
        UUID product = id(request(operations, "POST", "/api/v1/catalog/products", "{\"name\":\"Evidence Product\"}", 201));

        JsonNode initial = json.readTree(request(operations, "GET", "/api/v1/catalog/products/" + product + "/evidence", "", 200).body());
        assertThat(initial.get("entityVersion").asLong()).isZero();
        assertThat(initial.get("evidence").isNull()).isTrue();

        String evidence = """
                {"expectedEntityVersion":0,"intendedUse":"ROAD_RUNNING","primarySurface":"ROAD_TRACK",
                 "upperConstruction":"MESH_TEXTILE","cutProfile":"LOW_TOP","primarySoleProfile":"FLEX_GROOVED"}
                """;
        JsonNode saved = json.readTree(request(operations, "PUT", "/api/v1/catalog/products/" + product + "/evidence", evidence, 200).body());
        assertThat(saved.get("entityVersion").asLong()).isEqualTo(1);
        assertThat(saved.at("/evidence/intendedUse").asString()).isEqualTo("ROAD_RUNNING");
        assertThat(saved.at("/evidence/primarySurface").asString()).isEqualTo("ROAD_TRACK");

        HttpResponse<String> stale = request(operations, "PUT", "/api/v1/catalog/products/" + product + "/evidence", evidence, 409);
        assertThat(stale.body()).contains("PRODUCT_EVIDENCE_CONFLICT");
        assertThat(json.readTree(request(operations, "GET", "/api/v1/catalog/products/" + product + "/evidence", "", 200).body())
                .at("/evidence/intendedUse").asString()).isEqualTo("ROAD_RUNNING");

        assertThat(request(operations, "PUT", "/api/v1/catalog/products/" + product + "/evidence",
                "{\"expectedEntityVersion\":1,\"intendedUse\":\"INVENTED\"}", 400).statusCode()).isEqualTo(400);
        assertThat(request(operations, "GET", "/api/v1/catalog/products/" + UUID.randomUUID() + "/evidence", "", 404).statusCode()).isEqualTo(404);

        Browser customer = new Browser();
        assertThat(login(customer, "evidence-customer@example.com").statusCode()).isEqualTo(200);
        assertThat(request(customer, "GET", "/api/v1/catalog/products/" + product + "/evidence", "", 403).statusCode()).isEqualTo(403);

        var audits = jdbc.queryForList("SELECT action, resource_type, details_json FROM audit_event WHERE action = 'PRODUCT_EVIDENCE_UPDATED' AND resource_public_id = ?", product);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().get("resource_type")).isEqualTo("PRODUCT");
        JsonNode details = json.readTree((String) audits.getFirst().get("details_json"));
        assertThat(details.at("/before/intendedUse").isNull()).isTrue();
        assertThat(details.at("/after/intendedUse").asString()).isEqualTo("ROAD_RUNNING");
    }

    @Test void managesDraftCatalogInventoryAndIndependentPermissions() throws Exception {
        SessionPrincipal admin = bootstrapAdmin();
        String operationsLogin = "p2a-ops@example.com";
        UUID operationsId = identities.createAccount(admin, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        grant(operationsId, "STOREFRONT_MANAGE");
        UUID branch = scopes.createBranch(admin, "P2A", "P2A branch");
        UUID location = scopes.createLocation(admin, branch, "P2A-FLOOR", "P2A sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        Browser operations = new Browser();
        assertThat(login(operations, operationsLogin).statusCode()).isEqualTo(200);

        UUID product = id(request(operations, "POST", "/api/v1/catalog/products",
                "{\"name\":\"P2A Draft Runner\"}", 201));
        UUID variant = id(request(operations, "POST", "/api/v1/catalog/products/" + product + "/variants",
                "{\"sku\":\"P2A-RUN-41\",\"size\":\"41\",\"color\":\"Black\"}", 201));
        request(operations, "PUT", "/api/v1/pricing/variants/" + variant, "{\"amount\":1490000}", 204);
        request(operations, "PUT", "/api/v1/inventory/variants/" + variant + "/locations/" + location,
                "{\"onHand\":8,\"reason\":\"Initial receipt\"}", 200);

        JsonNode list = json.readTree(request(operations, "GET",
                "/api/v1/operations/catalog/products?q=P2A-RUN-41", "", 200).body());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("publishedVariantCount").asInt()).isZero();
        assertThat(list.get(0).get("minCurrentPrice").asLong()).isEqualTo(1_490_000);
        assertThat(list.get(0).at("/inventory/available").asLong()).isEqualTo(8);

        JsonNode detail = json.readTree(request(operations, "GET",
                "/api/v1/operations/catalog/products/" + product, "", 200).body());
        assertThat(detail.at("/variants/0/status").asString()).isEqualTo("DRAFT");
        assertThat(detail.at("/variants/0/currentPrice").asLong()).isEqualTo(1_490_000);
        assertThat(detail.at("/variants/0/publishReady").asBoolean()).isTrue();

        JsonNode inventory = json.readTree(request(operations, "GET",
                "/api/v1/operations/inventory/products/" + product, "", 200).body());
        assertThat(inventory.at("/balances/0/onHand").asLong()).isEqualTo(8);
        assertThat(inventory.at("/balances/0/reserved").asLong()).isZero();
        assertThat(inventory.at("/balances/0/available").asLong()).isEqualTo(8);
        long version = inventory.at("/balances/0/balanceVersion").asLong();
        request(operations, "PUT", "/api/v1/operations/inventory/variants/" + variant + "/locations/" + location,
                "{\"onHand\":10,\"reason\":\"Cycle count\",\"expectedBalanceVersion\":" + version + "}", 200);
        HttpResponse<String> stale = request(operations, "PUT",
                "/api/v1/operations/inventory/variants/" + variant + "/locations/" + location,
                "{\"onHand\":11,\"reason\":\"Stale count\",\"expectedBalanceVersion\":" + version + "}", 409);
        assertThat(stale.body()).contains("INVENTORY_BALANCE_CHANGED");

        HttpResponse<String> duplicate = request(operations, "POST",
                "/api/v1/catalog/products/" + product + "/variants",
                "{\"sku\":\"P2A-RUN-41\",\"size\":\"42\",\"color\":\"Blue\"}", 409);
        assertThat(json.readTree(duplicate.body()).at("/fieldErrors/sku").asString()).isEqualTo("ALREADY_EXISTS");
        assertThat(json.readTree(request(operations, "GET",
                "/api/v1/operations/product-presentations/products", "", 200).body()).toString())
                .contains(product.toString());

        UUID catalogOnlyId = identities.createAccount(admin, "p2a-catalog@example.com", PASSWORD, RoleCode.CASHIER);
        grant(catalogOnlyId, "CATALOG_MANAGE");
        Browser catalogOnly = new Browser();
        login(catalogOnly, "p2a-catalog@example.com");
        JsonNode catalogOnlyDetail = json.readTree(request(catalogOnly, "GET",
                "/api/v1/operations/catalog/products/" + product, "", 200).body());
        assertThat(catalogOnlyDetail.at("/variants/0/inventory").isNull()).isTrue();
        request(catalogOnly, "GET", "/api/v1/operations/inventory/products/" + product, "", 403);

        UUID inventoryOnlyId = identities.createAccount(admin, "p2a-inventory@example.com", PASSWORD, RoleCode.CASHIER);
        grant(inventoryOnlyId, "INVENTORY_VIEW");
        scopes.setAssignment(admin, inventoryOnlyId, branch, location, true);
        Browser inventoryOnly = new Browser();
        login(inventoryOnly, "p2a-inventory@example.com");
        request(inventoryOnly, "GET", "/api/v1/operations/inventory/products/" + product, "", 200);
        request(inventoryOnly, "GET", "/api/v1/operations/catalog/products/" + product, "", 403);

        request(operations, "POST", "/api/v1/catalog/variants/" + variant + "/publish", "{\"expectedEntityVersion\":0}", 204);
        assertThat(json.readTree(request(operations, "GET",
                "/api/v1/operations/catalog/products/" + product, "", 200).body())
                .at("/variants/0/status").asString()).isEqualTo("PUBLISHED");
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID(); String login = "admin-" + id + "@example.com"; Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }
    private void grant(UUID accountId, String permission) {
        jdbc.update("""
                INSERT INTO iam_account_permission(account_id,permission_id,granted_at)
                SELECT accounts.id,permissions.id,? FROM iam_user_account accounts
                CROSS JOIN iam_permission permissions
                WHERE accounts.public_id=? AND permissions.code=?
                """, Timestamp.from(Instant.now()), accountId, permission);
    }
    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private HttpResponse<String> login(Browser browser, String username) throws Exception { return request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, 200, "application/x-www-form-urlencoded"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected) throws Exception { return request(browser, method, path, body, expected, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected, String type) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", type);
        if (!method.equals("GET")) {
            Csrf csrf = csrf(browser);
            builder.header("Idempotency-Key", UUID.randomUUID().toString()).header(csrf.header(), csrf.token());
        }
        builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = browser.client.send(builder.build(), HttpResponse.BodyHandlers.ofString()); assertThat(response.statusCode()).isEqualTo(expected); return response;
    }
    private Csrf csrf(Browser browser) throws Exception { HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()); JsonNode node = json.readTree(response.body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private UUID id(HttpResponse<String> response) throws Exception { return UUID.fromString(json.readTree(response.body()).get("id").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
}
