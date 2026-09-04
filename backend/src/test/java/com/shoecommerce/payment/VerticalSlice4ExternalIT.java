package com.shoecommerce.payment;

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
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice4ExternalIT {
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
    @Autowired PaymentAttemptService payments;
    @LocalServerPort int port;

    @Test
    void provesOrderAmountReplayOwnershipAndCancellationInteraction() {
        Fixture fixture = fixture(5);
        OrderFixture order = order(fixture, 2);
        catalog.setPrice(fixture.operations(), fixture.variantId(), 180_000);

        PaymentAttemptService.InitiationResult created = payments.initiate(fixture.owner(), order.orderId(), "payment-Key-1");
        assertThat(created.created()).isTrue();
        assertThat(created.attempt().status()).isEqualTo("PENDING");
        assertThat(created.attempt().amount()).isEqualByComparingTo("240000");
        assertThat(created.attempt().currency()).isEqualTo("VND");
        assertThat(orders.readOwn(fixture.owner(), order.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(fixture.owner(), order.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 2, 3));

        PaymentAttemptService.InitiationResult replay = payments.initiate(fixture.owner(), order.orderId(), "payment-Key-1");
        assertThat(replay.created()).isFalse();
        assertThat(replay.attempt().id()).isEqualTo(created.attempt().id());
        assertThat(attemptCount(order.orderId())).isEqualTo(1);
        assertThat(auditCount("PAYMENT_ATTEMPT_CREATED", created.attempt().id())).isEqualTo(1);
        assertThat(payments.readOwn(fixture.owner(), created.attempt().id()).id()).isEqualTo(created.attempt().id());
        assertThatThrownBy(() -> payments.initiate(fixture.other(), order.orderId(), "other-key")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payments.readOwn(fixture.other(), created.attempt().id())).isInstanceOf(AccessDeniedException.class);
        assertThat(fixture.owner().permissions()).contains("PAYMENT_INITIATE").doesNotContain("PAYMENT_EVENT_APPLY");

        orders.cancelOwn(fixture.owner(), order.orderId());
        assertThat(orders.readOwn(fixture.owner(), order.orderId()).status()).isEqualTo("CANCELLED");
        assertThat(payments.readOwn(fixture.owner(), created.attempt().id()).status()).isEqualTo("CANCELLED");
        assertThat(reservations.readOwn(fixture.owner(), order.reservationId()).status()).isEqualTo("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 0, 5));
        orders.cancelOwn(fixture.owner(), order.orderId());
        assertThat(balance(fixture)).isEqualTo(new Balance(5, 0, 5));
        assertThatThrownBy(() -> payments.initiate(fixture.owner(), order.orderId(), "new-key-after-cancel")).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void provesKeyScopeMismatchOpenAttemptAndValidation() {
        Fixture fixture = fixture(4);
        OrderFixture first = order(fixture, 1);
        OrderFixture second = order(fixture, 1);
        payments.initiate(fixture.owner(), first.orderId(), "Case-Key");

        assertThatThrownBy(() -> payments.initiate(fixture.owner(), second.orderId(), "Case-Key")).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> payments.initiate(fixture.owner(), first.orderId(), "different-key")).isInstanceOf(BusinessConflictException.class);
        assertThat(payments.initiate(fixture.owner(), second.orderId(), "case-key").created()).isTrue();
        assertThatThrownBy(() -> payments.initiate(fixture.owner(), first.orderId(), null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency-Key is required");
        assertThatThrownBy(() -> payments.initiate(fixture.owner(), first.orderId(), " ")).isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency-Key is required");
        assertThatThrownBy(() -> payments.initiate(fixture.owner(), first.orderId(), "x".repeat(129))).isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency-Key exceeds 128 characters");
    }

    @Test
    void provesAuthenticatedHttpCreationReplayConflictAndRead() throws Exception {
        Fixture fixture = fixture(2);
        OrderFixture order = order(fixture, 1);
        Browser owner = new Browser();
        Browser other = new Browser();
        assertThat(login(owner, fixture.ownerLogin()).statusCode()).isEqualTo(200);
        assertThat(login(other, fixture.otherLogin()).statusCode()).isEqualTo(200);

        assertThat(paymentRequest(owner, order.orderId(), null, 400).statusCode()).isEqualTo(400);
        HttpResponse<String> created = paymentRequest(owner, order.orderId(), "http-key", 201);
        UUID attemptId = UUID.fromString(json.readTree(created.body()).get("attempt").get("id").asString());
        assertThat(created.body()).contains("PENDING").contains("120000").contains("VND");
        assertThat(UUID.fromString(json.readTree(paymentRequest(owner, order.orderId(), "http-key", 200).body()).get("attempt").get("id").asString())).isEqualTo(attemptId);
        assertThat(paymentRequest(owner, order.orderId(), "other-http-key", 409).statusCode()).isEqualTo(409);
        request(owner, "POST", "/api/v1/orders/" + order.orderId() + "/payment-attempts", "", 404,
                "application/json", "legacy-payment");
        assertThat(get(owner, "/api/v1/payment-attempts/" + attemptId).statusCode()).isEqualTo(200);
        assertThat(get(other, "/api/v1/payment-attempts/" + attemptId).statusCode()).isEqualTo(403);
        assertThat(paymentRequest(other, order.orderId(), "cross-owner", 403).statusCode()).isEqualTo(403);
    }

    @Test
    void provesConcurrentSameKeyReturnsOneLogicalAttempt() throws Exception {
        Fixture fixture = fixture(2);
        OrderFixture order = order(fixture, 1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(order.orderId(), lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<UUID> first = executor.submit(initiateId(fixture.owner(), order.orderId(), "same-race-key", entered));
            Future<UUID> second = executor.submit(initiateId(fixture.owner(), order.orderId(), "same-race-key", entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(attemptCount(order.orderId())).isEqualTo(1);
            assertThat(pendingAttemptCount(order.orderId())).isEqualTo(1);
            assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesConcurrentDifferentKeysCannotCreateTwoPendingAttempts() throws Exception {
        Fixture fixture = fixture(2);
        OrderFixture order = order(fixture, 1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(order.orderId(), lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> first = executor.submit(initiateAttempt(fixture.owner(), order.orderId(), "race-A", entered));
            Future<Boolean> second = executor.submit(initiateAttempt(fixture.owner(), order.orderId(), "race-B", entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(attemptCount(order.orderId())).isEqualTo(1);
            assertThat(pendingAttemptCount(order.orderId())).isEqualTo(1);
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesInitiationAndCancellationRaceEndsCoherently() throws Exception {
        Fixture fixture = fixture(2);
        OrderFixture order = order(fixture, 1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(order.orderId(), lockHeld, releaseBlocker));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> initiate = executor.submit(initiateAttempt(fixture.owner(), order.orderId(), "cancel-race", entered));
            Future<?> cancel = executor.submit(() -> { entered.countDown(); orders.cancelOwn(fixture.owner(), order.orderId()); });
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            releaseBlocker.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            initiate.get(15, TimeUnit.SECONDS);
            cancel.get(15, TimeUnit.SECONDS);
            assertThat(orders.readOwn(fixture.owner(), order.orderId()).status()).isEqualTo("CANCELLED");
            assertThat(reservations.readOwn(fixture.owner(), order.reservationId()).status()).isEqualTo("RELEASED");
            assertThat(pendingAttemptCount(order.orderId())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = ? AND attempts.status = 'CANCELLED'", Integer.class, order.orderId())).isBetween(0, 1);
            assertThat(balance(fixture)).isEqualTo(new Balance(2, 0, 2));
        } finally {
            releaseBlocker.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesAuditFailuresRollbackCreationAndCancellation() {
        Fixture creationFixture = fixture(1);
        OrderFixture creationOrder = order(creationFixture, 1);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> payments.initiate(creationFixture.owner(), creationOrder.orderId(), "rollback-key")).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(attemptCount(creationOrder.orderId())).isZero();
        assertThat(paymentCount(creationOrder.orderId())).isZero();
        assertThat(orders.readOwn(creationFixture.owner(), creationOrder.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(creationFixture.owner(), creationOrder.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(creationFixture)).isEqualTo(new Balance(1, 1, 0));
        assertThat(payments.initiate(creationFixture.owner(), creationOrder.orderId(), "rollback-key").created()).isTrue();

        Fixture cancellationFixture = fixture(1);
        OrderFixture cancellationOrder = order(cancellationFixture, 1);
        UUID attemptId = payments.initiate(cancellationFixture.owner(), cancellationOrder.orderId(), "cancel-rollback").attempt().id();
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> orders.cancelOwn(cancellationFixture.owner(), cancellationOrder.orderId())).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(orders.readOwn(cancellationFixture.owner(), cancellationOrder.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(payments.readOwn(cancellationFixture.owner(), attemptId).status()).isEqualTo("PENDING");
        assertThat(reservations.readOwn(cancellationFixture.owner(), cancellationOrder.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(cancellationFixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void provesV6HighValueConstraints() {
        Fixture fixture = fixture(4);
        OrderFixture first = order(fixture, 1);
        OrderFixture second = order(fixture, 1);
        PaymentAttemptService.PaymentAttemptView existing = payments.initiate(fixture.owner(), first.orderId(), "constraint-key").attempt();
        long firstPaymentId = paymentDatabaseId(first.orderId());
        long secondPaymentId = insertRawPayment(second.orderId(), fixture, Timestamp.from(Instant.now()));
        Timestamp now = Timestamp.from(Instant.now());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> insertRawAttempt(secondPaymentId, fixture.owner().publicId(), "constraint-key", "CANCELLED", 120_000, "VND", now, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAttempt(secondPaymentId, fixture.owner().publicId(), "invalid-status", "INVALID", 120_000, "VND", now, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAttempt(secondPaymentId, fixture.owner().publicId(), "zero-amount", "PENDING", 0, "VND", now, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAttempt(secondPaymentId, fixture.owner().publicId(), "bad-currency", "PENDING", 120_000, "USD", now, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRawAttempt(firstPaymentId, fixture.owner().publicId(), "second-pending", "PENDING", 120_000, "VND", now, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.readOwn(fixture.owner(), existing.id()).status()).isEqualTo("PENDING");
    }

    private Callable<UUID> initiateId(SessionPrincipal actor, UUID orderId, String key, CountDownLatch entered) {
        return () -> { entered.countDown(); return payments.initiate(actor, orderId, key).attempt().id(); };
    }

    private Callable<Boolean> initiateAttempt(SessionPrincipal actor, UUID orderId, String key, CountDownLatch entered) {
        return () -> { entered.countDown(); try { payments.initiate(actor, orderId, key); return true; } catch (BusinessConflictException exception) { return false; } };
    }

    private void holdOrderLock(UUID orderId, CountDownLatch lockHeld, CountDownLatch release) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM commerce_order WITH (UPDLOCK, HOLDLOCK) WHERE public_id = ?")) {
            connection.setAutoCommit(false);
            statement.setObject(1, orderId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new IllegalStateException("Order lock target missing"); }
            lockHeld.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Order lock was not released");
            connection.rollback();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Fixture fixture(long stock) {
        String prefix = "S4" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String otherLogin = prefix.toLowerCase() + "-other@example.com";
        SessionPrincipal admin = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(admin, prefix.toLowerCase() + "-ops@example.com", PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(admin, otherLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branchId = scopes.createBranch(admin, prefix, "Vertical Slice 4");
        UUID locationId = scopes.createLocation(admin, branchId, prefix + "-LOC", "Payment floor");
        scopes.setAssignment(admin, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(prefix.toLowerCase() + "-ops@example.com");
        UUID productId = catalog.createProduct(operations, "Payment Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        adjustments.adjust(operations, variantId, locationId, stock, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(operations, variantId);
        return new Fixture(variantId, branchId, locationId, ownerLogin, otherLogin, operations, principal(ownerLogin), principal(otherLogin));
    }

    private OrderFixture order(Fixture fixture, long quantity) {
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), quantity).id();
        UUID orderId = orders.create(fixture.owner(), reservationId).id();
        return new OrderFixture(orderId, reservationId);
    }

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private long insertRawPayment(UUID orderId, Fixture fixture, Timestamp now) { jdbc.update("INSERT INTO payment(public_id, order_id, currency, entity_version, created_at) SELECT ?, id, 'VND', 0, ? FROM commerce_order WHERE public_id = ?", UUID.randomUUID(), now, orderId); return paymentDatabaseId(orderId); }
    private void insertRawAttempt(long paymentId, UUID ownerId, String key, String status, long amount, String currency, Timestamp created, Timestamp cancelled) { jdbc.update("INSERT INTO payment_attempt(public_id, payment_id, owner_account_public_id, idempotency_key, status, amount, currency, entity_version, created_at, cancelled_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", UUID.randomUUID(), paymentId, ownerId, key, status, amount, currency, created, cancelled); }
    private long paymentDatabaseId(UUID orderId) { return jdbc.queryForObject("SELECT payments.id FROM payment payments JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = ?", Long.class, orderId); }
    private int paymentCount(UUID orderId) { return jdbc.queryForObject("SELECT COUNT(*) FROM payment payments JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = ?", Integer.class, orderId); }
    private int attemptCount(UUID orderId) { return jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = ?", Integer.class, orderId); }
    private int pendingAttemptCount(UUID orderId) { return jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = ? AND attempts.status = 'PENDING'", Integer.class, orderId); }
    private int auditCount(String action, UUID resourceId) { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND resource_public_id = ?", Integer.class, action, resourceId); }
    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private Balance balance(Fixture fixture) { return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved AS available FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", (result, row) -> new Balance(result.getLong("on_hand"), result.getLong("reserved"), result.getLong("available")), fixture.variantId(), fixture.locationId()); }
    private HttpResponse<String> login(Browser browser, String username) throws Exception { return request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, 200, "application/x-www-form-urlencoded", null); }
    private HttpResponse<String> get(Browser browser, String path) throws Exception { return browser.client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> paymentRequest(Browser browser, UUID orderId, String key, int expected) throws Exception { return request(browser, "POST", "/api/v1/orders/" + orderId + "/payments", "", expected, "application/json", key); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, int expected, String type, String key) throws Exception { Csrf csrf = csrf(browser); HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()); if (key != null) builder.header("Idempotency-Key", key); HttpResponse<String> response = browser.client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()); assertThat(response.statusCode()).isEqualTo(expected); return response; }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(get(browser, "/api/v1/auth/csrf").body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record OrderFixture(UUID orderId, UUID reservationId) { }
    private record Fixture(UUID variantId, UUID branchId, UUID locationId, String ownerLogin, String otherLogin, SessionPrincipal operations, SessionPrincipal owner, SessionPrincipal other) { }
}
