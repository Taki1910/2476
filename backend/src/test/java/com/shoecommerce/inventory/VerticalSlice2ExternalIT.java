package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.CorrelationIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice2ExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired InventoryReservationService reservations;
    @LocalServerPort int port;

    @Test
    void provesSellabilityOwnershipReleaseAdjustmentAndConstraints() {
        Fixture fixture = fixture(2);
        InventoryReservationService.ReservationView reservation = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1);
        assertThat(reservation.status()).isEqualTo("ACTIVE");
        assertThat(reservations.readOwn(fixture.owner(), reservation.id()).id()).isEqualTo(reservation.id());
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));

        assertThatThrownBy(() -> reservations.readOwn(fixture.other(), reservation.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reservations.releaseOwn(fixture.other(), reservation.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reservations.reserve(fixture.other(), fixture.variantId(), fixture.locationId(), 2)).isInstanceOf(IllegalStateException.class).hasMessage("Insufficient available stock");
        assertThatThrownBy(() -> catalog.setStock(fixture.operations(), fixture.variantId(), fixture.locationId(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));

        InventoryReservationService.ReservationView released = reservations.releaseOwn(fixture.owner(), reservation.id());
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(reservations.releaseOwn(fixture.owner(), reservation.id()).status()).isEqualTo("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 0, 2));

        UUID draft = catalog.createVariant(fixture.operations(), fixture.productId(), fixture.prefix() + "-DRAFT", "43", "Blue");
        catalog.setPrice(fixture.operations(), draft, 110_000);
        catalog.setStock(fixture.operations(), draft, fixture.locationId(), 1);
        assertThatThrownBy(() -> reservations.reserve(fixture.owner(), draft, fixture.locationId(), 1)).isInstanceOf(IllegalStateException.class).hasMessage("Variant is not published");

        jdbc.update("UPDATE org_location SET enabled = 0 WHERE public_id = ?", fixture.locationId());
        assertThatThrownBy(() -> reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1)).isInstanceOf(IllegalArgumentException.class).hasMessage("Location not found or disabled");
        assertV4Constraints(fixture);
    }

    @Test
    void provesAuthenticatedHttpOwnershipAndIdempotentRelease() throws Exception {
        Fixture fixture = fixture(1);
        Browser owner = new Browser();
        Browser other = new Browser();
        assertThat(login(owner, fixture.ownerLogin()).statusCode()).isEqualTo(200);
        assertThat(login(other, fixture.otherLogin()).statusCode()).isEqualTo(200);

        HttpResponse<String> created = request(owner, "POST", "/api/v1/inventory/reservations", "{\"variantId\":\"" + fixture.variantId() + "\",\"locationId\":\"" + fixture.locationId() + "\",\"quantity\":1}", 201);
        UUID reservationId = UUID.fromString(json.readTree(created.body()).get("id").asString());
        assertThat(created.body()).contains("ACTIVE");
        assertThat(get(owner, "/api/v1/inventory/reservations/" + reservationId).statusCode()).isEqualTo(200);
        assertThat(get(other, "/api/v1/inventory/reservations/" + reservationId).statusCode()).isEqualTo(403);
        assertThat(request(other, "DELETE", "/api/v1/inventory/reservations/" + reservationId, "", 403).statusCode()).isEqualTo(403);
        assertThat(request(owner, "DELETE", "/api/v1/inventory/reservations/" + reservationId, "", 200).body()).contains("RELEASED");
        assertThat(request(owner, "DELETE", "/api/v1/inventory/reservations/" + reservationId, "", 200).body()).contains("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
    }

    @Test
    void provesConcurrentLastUnitCannotBeOverReserved() throws Exception {
        Fixture fixture = fixture(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdBalanceLock(fixture, lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> first = executor.submit(attempt(fixture.owner(), fixture, ready, go, entered));
            Future<Boolean> second = executor.submit(attempt(fixture.other(), fixture, ready, go, entered));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertThat((firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?) AND status = 'ACTIVE'", Integer.class, fixture.variantId(), fixture.locationId())).isEqualTo(1);
            assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesAuditFailureRollsBackReservationAndAvailability() {
        Fixture fixture = fixture(1);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1)).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id = ? AND variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?)", Integer.class, fixture.owner().publicId(), fixture.variantId())).isZero();
    }

    private Callable<Boolean> attempt(SessionPrincipal actor, Fixture fixture, CountDownLatch ready, CountDownLatch go, CountDownLatch entered) {
        return () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
            entered.countDown();
            try {
                reservations.reserve(actor, fixture.variantId(), fixture.locationId(), 1);
                return true;
            } catch (IllegalStateException exception) {
                assertThat(exception).hasMessage("Insufficient available stock");
                return false;
            }
        };
    }

    private void holdBalanceLock(Fixture fixture, CountDownLatch lockHeld, CountDownLatch release) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT balances.id FROM inventory_balance balances WITH (UPDLOCK, HOLDLOCK) JOIN catalog_product_variant variants ON variants.id = balances.variant_id JOIN org_location locations ON locations.id = balances.location_id WHERE variants.public_id = ? AND locations.public_id = ?")) {
            connection.setAutoCommit(false);
            statement.setObject(1, fixture.variantId());
            statement.setObject(2, fixture.locationId());
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new IllegalStateException("Balance lock target missing"); }
            lockHeld.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Balance lock was not released");
            connection.rollback();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertV4Constraints(Fixture fixture) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balance SET reserved = -1 WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", fixture.variantId(), fixture.locationId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balance SET reserved = on_hand + 1 WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", fixture.variantId(), fixture.locationId())).isInstanceOf(DataIntegrityViolationException.class);
        Long variantId = jdbc.queryForObject("SELECT id FROM catalog_product_variant WHERE public_id = ?", Long.class, fixture.variantId());
        Long locationId = jdbc.queryForObject("SELECT id FROM org_location WHERE public_id = ?", Long.class, fixture.locationId());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_reservation(public_id, owner_account_public_id, variant_id, location_id, quantity, status, entity_version, created_at) VALUES (?, ?, ?, ?, 0, 'ACTIVE', 0, ?)", UUID.randomUUID(), fixture.owner().publicId(), variantId, locationId, Timestamp.from(Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(long stock) {
        String prefix = "S2" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String otherLogin = prefix.toLowerCase() + "-other@example.com";
        SessionPrincipal admin = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(admin, prefix.toLowerCase() + "-ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(admin, otherLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branchId = scopes.createBranch(admin, prefix, "Vertical Slice 2");
        UUID locationId = scopes.createLocation(admin, branchId, prefix + "-LOC", "Reservation floor");
        scopes.setAssignment(admin, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(prefix.toLowerCase() + "-ops@example.com");
        UUID productId = catalog.createProduct(operations, "Reservation Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        catalog.setStock(operations, variantId, locationId, stock);
        catalog.publish(operations, variantId);
        return new Fixture(prefix, productId, variantId, locationId, ownerLogin, otherLogin, operations, principal(ownerLogin), principal(otherLogin));
    }

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private Balance balance(Fixture fixture) { return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved AS available FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", (result, row) -> new Balance(result.getLong("on_hand"), result.getLong("reserved"), result.getLong("available")), fixture.variantId(), fixture.locationId()); }
    private HttpResponse<String> login(Browser browser, String username) throws Exception { return request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, 200, "application/x-www-form-urlencoded"); }
    private HttpResponse<String> get(Browser browser, String path) throws Exception { return browser.client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected) throws Exception { return request(browser, method, path, body, expected, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected, String type) throws Exception { Csrf csrf = csrf(browser); HttpRequest request = HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(); HttpResponse<String> response = browser.client.send(request, HttpResponse.BodyHandlers.ofString()); assertThat(response.statusCode()).isEqualTo(expected); return response; }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(get(browser, "/api/v1/auth/csrf").body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Fixture(String prefix, UUID productId, UUID variantId, UUID locationId, String ownerLogin, String otherLogin, SessionPrincipal operations, SessionPrincipal owner, SessionPrincipal other) { }
}
