package com.shoecommerce.order;

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
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice3ExternalIT {
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
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired CustomerOrderService orders;
    @LocalServerPort int port;

    @Test
    void provesSnapshotOwnershipAdoptionAndIdempotentCancellation() {
        Fixture fixture = fixture(5);
        InventoryReservationService.ReservationView reservation = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 2);
        catalog.setPrice(fixture.operations(), fixture.variantId(), 130_000);

        assertThatThrownBy(() -> orders.create(fixture.other(), reservation.id())).isInstanceOf(AccessDeniedException.class);
        CustomerOrderService.OrderView order = orders.create(fixture.owner(), reservation.id());
        assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.unitPriceAmount()).isEqualTo(130_000);
        assertThat(order.totalAmount()).isEqualTo(260_000);
        assertThat(order.currency()).isEqualTo("VND");
        assertThat(reservations.readOwn(fixture.owner(), reservation.id()).status()).isEqualTo("ADOPTED");
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 2, 3));
        assertThat(orders.readOwn(fixture.owner(), order.id()).id()).isEqualTo(order.id());

        catalog.setPrice(fixture.operations(), fixture.variantId(), 140_000);
        assertThat(orders.readOwn(fixture.owner(), order.id()).unitPriceAmount()).isEqualTo(130_000);
        assertThatThrownBy(() -> reservations.releaseOwn(fixture.owner(), reservation.id())).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> orders.create(fixture.owner(), reservation.id())).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> orders.readOwn(fixture.other(), order.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> orders.cancelOwn(fixture.other(), order.id())).isInstanceOf(AccessDeniedException.class);

        CustomerOrderService.OrderView cancelled = orders.cancelOwn(fixture.owner(), order.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(reservations.readOwn(fixture.owner(), reservation.id()).status()).isEqualTo("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 0, 5));
        assertThat(orders.cancelOwn(fixture.owner(), order.id()).status()).isEqualTo("CANCELLED");
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 0, 5));
        assertThatThrownBy(() -> orders.create(fixture.owner(), reservation.id())).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void rejectsLegacyDirectOrderHttpBoundary() throws Exception {
        Fixture fixture = fixture(1);
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        Browser owner = new Browser();
        assertThat(login(owner, fixture.ownerLogin()).statusCode()).isEqualTo(200);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order", Integer.class);

        request(owner, "POST", "/api/v1/orders", "{\"reservationId\":\"" + reservationId + "\"}", 405);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order", Integer.class)).isEqualTo(before);
    }

    @Test
    void provesConcurrentDuplicateAdoptionCreatesExactlyOneOrder() throws Exception {
        Fixture fixture = fixture(2);
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdReservationLock(reservationId, lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> first = executor.submit(createAttempt(fixture.owner(), reservationId, entered));
            Future<Boolean> second = executor.submit(createAttempt(fixture.owner(), reservationId, entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(orderCount(reservationId)).isEqualTo(1);
            assertThat(reservationStatus(reservationId)).isEqualTo("ADOPTED");
            assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesAdoptionAndReleaseSerializeWithoutForbiddenState() throws Exception {
        Fixture fixture = fixture(2);
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdReservationLock(reservationId, lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> create = executor.submit(createAttempt(fixture.owner(), reservationId, entered));
            Future<Boolean> release = executor.submit(releaseAttempt(fixture.owner(), reservationId, entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            boolean created = create.get(15, TimeUnit.SECONDS);
            boolean released = release.get(15, TimeUnit.SECONDS);
            assertThat(created).isNotEqualTo(released);
            if (created) {
                assertThat(orderCount(reservationId)).isEqualTo(1);
                assertThat(reservationStatus(reservationId)).isEqualTo("ADOPTED");
                assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));
            } else {
                assertThat(orderCount(reservationId)).isZero();
                assertThat(reservationStatus(reservationId)).isEqualTo("RELEASED");
                assertThat(balance(fixture)).isEqualTo(new Balance(2, 0, 2));
            }
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesCreationAuditFailureRollsBackOrderAndAdoption() {
        Fixture fixture = fixture(1);
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> orders.create(fixture.owner(), reservationId)).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(orderCount(reservationId)).isZero();
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void provesCancellationAuditFailureRollsBackReleaseAndOrderState() {
        Fixture fixture = fixture(1);
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        UUID orderId = orders.create(fixture.owner(), reservationId).id();
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> orders.cancelOwn(fixture.owner(), orderId)).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(orders.readOwn(fixture.owner(), orderId).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservationStatus(reservationId)).isEqualTo("ADOPTED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void provesV5HighValueConstraints() {
        Fixture fixture = fixture(4);
        UUID firstReservation = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        orders.create(fixture.owner(), firstReservation);
        UUID secondReservation = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        Timestamp now = Timestamp.from(Instant.now());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> insertRawOrder(UUID.randomUUID(), fixture, firstReservation, "PENDING_PAYMENT", now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawOrder(UUID.randomUUID(), fixture, secondReservation, "INVALID", now)).isInstanceOf(DataIntegrityViolationException.class);

        UUID rawOrderId = UUID.randomUUID();
        insertRawOrder(rawOrderId, fixture, secondReservation, "PENDING_PAYMENT", now);
        Long databaseOrderId = jdbc.queryForObject("SELECT id FROM commerce_order WHERE public_id = ?", Long.class, rawOrderId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO commerce_order_item(order_id, variant_public_id, location_public_id, quantity, unit_price_amount) VALUES (?, ?, ?, 0, 120000)", databaseOrderId, fixture.variantId(), fixture.locationId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO commerce_order_item(order_id, variant_public_id, location_public_id, quantity, unit_price_amount) VALUES (?, ?, ?, 1, 0)", databaseOrderId, fixture.variantId(), fixture.locationId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Callable<Boolean> createAttempt(SessionPrincipal actor, UUID reservationId, CountDownLatch entered) {
        return () -> { entered.countDown(); try { orders.create(actor, reservationId); return true; } catch (BusinessConflictException exception) { return false; } };
    }

    private Callable<Boolean> releaseAttempt(SessionPrincipal actor, UUID reservationId, CountDownLatch entered) {
        return () -> { entered.countDown(); try { reservations.releaseOwn(actor, reservationId); return true; } catch (BusinessConflictException exception) { return false; } };
    }

    private void holdReservationLock(UUID reservationId, CountDownLatch lockHeld, CountDownLatch release) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM inventory_reservation WITH (UPDLOCK, HOLDLOCK) WHERE public_id = ?")) {
            connection.setAutoCommit(false);
            statement.setObject(1, reservationId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new IllegalStateException("Reservation lock target missing"); }
            lockHeld.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Reservation lock was not released");
            connection.rollback();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void insertRawOrder(UUID orderId, Fixture fixture, UUID reservationId, String status, Timestamp now) {
        jdbc.update("INSERT INTO commerce_order(public_id, owner_account_public_id, responsible_branch_public_id, reservation_public_id, currency, status, entity_version, created_at) VALUES (?, ?, ?, ?, 'VND', ?, 0, ?)", orderId, fixture.owner().publicId(), fixture.branchId(), reservationId, status, now);
    }

    private Fixture fixture(long stock) {
        String prefix = "S3" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String otherLogin = prefix.toLowerCase() + "-other@example.com";
        SessionPrincipal admin = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(admin, prefix.toLowerCase() + "-ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(admin, otherLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branchId = scopes.createBranch(admin, prefix, "Vertical Slice 3");
        UUID locationId = scopes.createLocation(admin, branchId, prefix + "-LOC", "Order floor");
        scopes.setAssignment(admin, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(prefix.toLowerCase() + "-ops@example.com");
        UUID productId = catalog.createProduct(operations, "Pending Order Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        adjustments.adjust(operations, variantId, locationId, stock, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(operations, variantId);
        return new Fixture(variantId, branchId, locationId, ownerLogin, otherLogin, operations, principal(ownerLogin), principal(otherLogin));
    }

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private int orderCount(UUID reservationId) { return jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE reservation_public_id = ?", Integer.class, reservationId); }
    private String reservationStatus(UUID reservationId) { return jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, reservationId); }
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
    private record Fixture(UUID variantId, UUID branchId, UUID locationId, String ownerLogin, String otherLogin, SessionPrincipal operations, SessionPrincipal owner, SessionPrincipal other) { }
}
