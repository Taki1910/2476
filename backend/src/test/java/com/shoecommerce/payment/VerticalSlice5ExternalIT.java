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
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice5ExternalIT {
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
    @Autowired CustomerOrderService orders;
    @Autowired PaymentAttemptService attempts;
    @Autowired PaymentProviderEventService providerEvents;
    @LocalServerPort int port;

    @Test
    void provesProviderAuthorizationAndHttpBoundary() throws Exception {
        Fixture fixture = fixture(4);
        Purchase purchase = purchase(fixture, 1, "auth-attempt");

        assertThatThrownBy(() -> providerEvents.apply(fixture.owner(), "denied-customer", purchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> providerEvents.apply(fixture.operations(), "denied-operations", purchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> providerEvents.apply(fixture.administrator(), "denied-admin", purchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE)).isInstanceOf(AccessDeniedException.class);

        Browser provider = login(fixture.providerLogin());
        Browser customer = login(fixture.ownerLogin());
        Browser operations = login(fixture.operationsLogin());
        Browser administrator = login(fixture.administratorLogin());
        String body = eventBody("http-provider-event", purchase.attemptId(), "FAILURE");
        assertThat(post(customer, body).statusCode()).isEqualTo(403);
        assertThat(post(operations, body).statusCode()).isEqualTo(403);
        assertThat(post(administrator, body).statusCode()).isEqualTo(403);
        assertThat(post(provider, eventBody("invalid", purchase.attemptId(), "UNKNOWN")).statusCode()).isEqualTo(400);
        HttpResponse<String> applied = post(provider, body);
        assertThat(applied.statusCode()).isEqualTo(201);
        assertThat(applied.body()).contains("http-provider-event").contains("FAILED").contains("PENDING_PAYMENT");
        assertThat(post(provider, body).statusCode()).isEqualTo(200);
        assertThat(post(provider, eventBody("http-provider-event", purchase.attemptId(), "SUCCESS")).statusCode()).isEqualTo(409);
        assertThat(post(provider, eventBody("unknown-attempt", UUID.randomUUID(), "SUCCESS")).statusCode()).isEqualTo(400);
        assertThat(eventCount(fixture.provider().publicId(), "http-provider-event")).isEqualTo(1);
    }

    @Test
    void provesSuccessConsumesTheHoldExactlyOnceAndBlocksPaidCancellation() {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "success-attempt");
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));

        PaymentProviderEventService.ApplicationResult first = providerEvents.apply(fixture.provider(), "success-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        assertThat(first.accepted()).isTrue();
        assertThat(first.created()).isTrue();
        assertThat(first.event().paymentAttemptStatus()).isEqualTo("SUCCEEDED");
        assertThat(first.event().orderStatus()).isEqualTo("PAID");
        assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("SUCCEEDED");
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PAID");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("CONSUMED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        assertThat(auditCount("PAYMENT_SUCCEEDED", purchase.attemptId())).isEqualTo(1);
        assertThat(auditActorType("PAYMENT_SUCCEEDED", purchase.attemptId())).isEqualTo("INTEGRATION");

        PaymentProviderEventService.ApplicationResult replay = providerEvents.apply(fixture.provider(), "success-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        assertThat(replay.accepted()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(eventCount(fixture.provider().publicId(), "success-event")).isEqualTo(1);
        assertThat(auditCount("PAYMENT_SUCCEEDED", purchase.attemptId())).isEqualTo(1);
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));

        assertThatThrownBy(() -> orders.cancelOwn(fixture.owner(), purchase.orderId())).isInstanceOf(BusinessConflictException.class);
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PAID");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("CONSUMED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
    }

    @Test
    void provesFailureChangesOnlyAttemptAndV10AllowsOneNewPendingRetry() {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "failure-attempt");
        providerEvents.apply(fixture.provider(), "failure-event", purchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE);

        assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("FAILED");
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));
        assertThat(auditCount("PAYMENT_FAILED", purchase.attemptId())).isEqualTo(1);
        var retry = attempts.initiate(fixture.owner(), purchase.orderId(), "v10-approved-retry").attempt();
        assertThat(retry.status()).isEqualTo("PENDING");
        assertThat(retry.id()).isNotEqualTo(purchase.attemptId());

        orders.cancelOwn(fixture.owner(), purchase.orderId());
        assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("FAILED");
        assertThat(attempts.readOwn(fixture.owner(), retry.id()).status()).isEqualTo("CANCELLED");
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("CANCELLED");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 0, 2));
    }

    @Test
    void provesMismatchedReuseAndLateSuccessAreStableConflicts() {
        Fixture fixture = fixture(3);
        Purchase first = purchase(fixture, 1, "first-attempt");
        Purchase second = purchase(fixture, 1, "second-attempt");
        providerEvents.apply(fixture.provider(), "opaque-Case-Event", first.attemptId(), PaymentProviderEvent.Outcome.FAILURE);

        PaymentProviderEventService.ApplicationResult wrongOutcome = providerEvents.apply(fixture.provider(), "opaque-Case-Event", first.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        PaymentProviderEventService.ApplicationResult wrongAttempt = providerEvents.apply(fixture.provider(), "opaque-Case-Event", second.attemptId(), PaymentProviderEvent.Outcome.FAILURE);
        assertThat(wrongOutcome.accepted()).isFalse();
        assertThat(wrongAttempt.accepted()).isFalse();
        assertThat(eventCount(fixture.provider().publicId(), "opaque-Case-Event")).isEqualTo(1);
        assertThat(providerEvents.apply(fixture.provider(), "opaque-case-event", second.attemptId(), PaymentProviderEvent.Outcome.FAILURE).accepted()).isTrue();

        Fixture lateFixture = fixture(1);
        Purchase late = purchase(lateFixture, 1, "late-attempt");
        orders.cancelOwn(lateFixture.owner(), late.orderId());
        PaymentProviderEventService.ApplicationResult rejected = providerEvents.apply(lateFixture.provider(), "late-success", late.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        PaymentProviderEventService.ApplicationResult replay = providerEvents.apply(lateFixture.provider(), "late-success", late.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        assertThat(rejected.accepted()).isFalse();
        assertThat(replay.accepted()).isFalse();
        assertThat(eventCount(lateFixture.provider().publicId(), "late-success")).isEqualTo(1);
        assertThat(attempts.readOwn(lateFixture.owner(), late.attemptId()).status()).isEqualTo("CANCELLED");
        assertThat(orders.readOwn(lateFixture.owner(), late.orderId()).status()).isEqualTo("CANCELLED");
        assertThat(reservations.readOwn(lateFixture.owner(), late.reservationId()).status()).isEqualTo("RELEASED");
        assertThat(balance(lateFixture)).isEqualTo(new Balance(1, 0, 1));
    }

    @Test
    void provesConcurrentDuplicateSuccessConsumesInventoryOnce() throws Exception {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "duplicate-race-attempt");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(purchase.orderId(), lockHeld, release));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<PaymentProviderEventService.ApplicationResult> first = executor.submit(() -> apply(fixture.provider(), "duplicate-race-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS, entered));
            Future<PaymentProviderEventService.ApplicationResult> second = executor.submit(() -> apply(fixture.provider(), "duplicate-race-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS, entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat(first.get(15, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS).accepted()).isTrue();
            assertThat(eventCount(fixture.provider().publicId(), "duplicate-race-event")).isEqualTo(1);
            assertThat(auditCount("PAYMENT_SUCCEEDED", purchase.attemptId())).isEqualTo(1);
            assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("SUCCEEDED");
            assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PAID");
            assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("CONSUMED");
            assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesConcurrentSuccessAndFailureHaveOneTerminalWinner() throws Exception {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "conflicting-race-attempt");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(purchase.orderId(), lockHeld, release));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<PaymentProviderEventService.ApplicationResult> success = executor.submit(() -> apply(fixture.provider(), "race-success", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS, entered));
            Future<PaymentProviderEventService.ApplicationResult> failure = executor.submit(() -> apply(fixture.provider(), "race-failure", purchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE, entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat((success.get(15, TimeUnit.SECONDS).accepted() ? 1 : 0) + (failure.get(15, TimeUnit.SECONDS).accepted() ? 1 : 0)).isEqualTo(1);
            assertCoherentTerminalState(fixture, purchase);
            assertThat(auditCount("PAYMENT_SUCCEEDED", purchase.attemptId()) + auditCount("PAYMENT_FAILED", purchase.attemptId())).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesSuccessAndCancellationSerializeToOneCoherentState() throws Exception {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "cancel-race-attempt");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(purchase.orderId(), lockHeld, release));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<PaymentProviderEventService.ApplicationResult> success = executor.submit(() -> apply(fixture.provider(), "cancel-race-success", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS, entered));
            Future<Boolean> cancellation = executor.submit(() -> { entered.countDown(); try { orders.cancelOwn(fixture.owner(), purchase.orderId()); return true; } catch (BusinessConflictException exception) { return false; } });
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            boolean successApplied = success.get(15, TimeUnit.SECONDS).accepted();
            boolean cancellationApplied = cancellation.get(15, TimeUnit.SECONDS);
            assertThat(successApplied).isNotEqualTo(cancellationApplied);
            String attemptStatus = attempts.readOwn(fixture.owner(), purchase.attemptId()).status();
            String orderStatus = orders.readOwn(fixture.owner(), purchase.orderId()).status();
            String reservationStatus = reservations.readOwn(fixture.owner(), purchase.reservationId()).status();
            if (successApplied) {
                assertThat(new State(attemptStatus, orderStatus, reservationStatus, balance(fixture))).isEqualTo(new State("SUCCEEDED", "PAID", "CONSUMED", new Balance(1, 0, 1)));
            } else {
                assertThat(new State(attemptStatus, orderStatus, reservationStatus, balance(fixture))).isEqualTo(new State("CANCELLED", "CANCELLED", "RELEASED", new Balance(2, 0, 2)));
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void provesSuccessAuditFailureRollsBackEverythingAndAllowsRetry() {
        Fixture fixture = fixture(2);
        Purchase purchase = purchase(fixture, 1, "rollback-attempt");
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> providerEvents.apply(fixture.provider(), "rollback-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS)).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(eventCount(fixture.provider().publicId(), "rollback-event")).isZero();
        assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("PENDING");
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 1, 1));

        assertThat(providerEvents.apply(fixture.provider(), "rollback-event", purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS).accepted()).isTrue();
        assertThat(eventCount(fixture.provider().publicId(), "rollback-event")).isEqualTo(1);
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));

        Fixture failureFixture = fixture(1);
        Purchase failurePurchase = purchase(failureFixture, 1, "failure-rollback-attempt");
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> providerEvents.apply(failureFixture.provider(), "failure-rollback-event", failurePurchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE)).isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(eventCount(failureFixture.provider().publicId(), "failure-rollback-event")).isZero();
        assertThat(attempts.readOwn(failureFixture.owner(), failurePurchase.attemptId()).status()).isEqualTo("PENDING");
        assertThat(orders.readOwn(failureFixture.owner(), failurePurchase.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(failureFixture.owner(), failurePurchase.reservationId()).status()).isEqualTo("ADOPTED");
        assertThat(balance(failureFixture)).isEqualTo(new Balance(1, 1, 0));
        assertThat(providerEvents.apply(failureFixture.provider(), "failure-rollback-event", failurePurchase.attemptId(), PaymentProviderEvent.Outcome.FAILURE).accepted()).isTrue();
    }

    @Test
    void provesV7HighValueSqlConstraints() {
        Fixture fixture = fixture(4);
        Purchase first = purchase(fixture, 1, "constraint-first");
        Purchase second = purchase(fixture, 1, "constraint-second");
        providerEvents.apply(fixture.provider(), "constraint-event", first.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_attempt SET status = 'INVALID' WHERE public_id = ?", second.attemptId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE commerce_order SET status = 'PAID' WHERE public_id = ?", second.orderId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_reservation SET status = 'CONSUMED' WHERE public_id = ?", second.reservationId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_balance SET reserved = on_hand + 1 WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", fixture.variantId(), fixture.locationId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO payment_provider_event(public_id, provider_account_public_id, provider_event_id, payment_attempt_public_id, outcome, disposition, attempt_status, order_status, received_at, applied_at) VALUES (?, ?, 'constraint-event', ?, 'SUCCESS', 'APPLIED', 'SUCCEEDED', 'PAID', ?, ?)", UUID.randomUUID(), fixture.provider().publicId(), second.attemptId(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PaymentProviderEventService.ApplicationResult apply(SessionPrincipal actor, String eventId, UUID attemptId,
            PaymentProviderEvent.Outcome outcome, CountDownLatch entered) {
        entered.countDown();
        return providerEvents.apply(actor, eventId, attemptId, outcome);
    }

    private void assertCoherentTerminalState(Fixture fixture, Purchase purchase) {
        String status = attempts.readOwn(fixture.owner(), purchase.attemptId()).status();
        if (status.equals("SUCCEEDED")) {
            assertThat(new State(status, orders.readOwn(fixture.owner(), purchase.orderId()).status(), reservations.readOwn(fixture.owner(), purchase.reservationId()).status(), balance(fixture))).isEqualTo(new State("SUCCEEDED", "PAID", "CONSUMED", new Balance(1, 0, 1)));
        } else {
            assertThat(new State(status, orders.readOwn(fixture.owner(), purchase.orderId()).status(), reservations.readOwn(fixture.owner(), purchase.reservationId()).status(), balance(fixture))).isEqualTo(new State("FAILED", "PENDING_PAYMENT", "ADOPTED", new Balance(2, 1, 1)));
        }
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
        String prefix = "S5" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String operationsLogin = prefix.toLowerCase() + "-ops@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String providerLogin = prefix.toLowerCase() + "-provider@example.com";
        SessionPrincipal administrator = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(administrator, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(administrator, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(administrator, providerLogin, PASSWORD, RoleCode.PROVIDER);
        UUID branchId = scopes.createBranch(administrator, prefix, "Vertical Slice 5");
        UUID locationId = scopes.createLocation(administrator, branchId, prefix + "-LOC", "Provider result floor");
        scopes.setAssignment(administrator, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(operationsLogin);
        UUID productId = catalog.createProduct(operations, "Provider Result Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        catalog.setStock(operations, variantId, locationId, stock);
        catalog.publish(operations, variantId);
        return new Fixture(variantId, locationId, adminLogin, operationsLogin, ownerLogin, providerLogin,
                administrator, operations, principal(ownerLogin), principal(providerLogin));
    }

    private Purchase purchase(Fixture fixture, long quantity, String key) {
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), quantity).id();
        UUID orderId = orders.create(fixture.owner(), reservationId).id();
        UUID attemptId = attempts.initiate(fixture.owner(), orderId, key).attempt().id();
        return new Purchase(orderId, reservationId, attemptId);
    }

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private int eventCount(UUID providerId, String eventId) { return jdbc.queryForObject("SELECT COUNT(*) FROM payment_provider_event WHERE provider_account_public_id = ? AND provider_event_id = ?", Integer.class, providerId, eventId); }
    private int auditCount(String action, UUID attemptId) { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND resource_public_id = ?", Integer.class, action, attemptId); }
    private String auditActorType(String action, UUID attemptId) { return jdbc.queryForObject("SELECT actor_type FROM audit_event WHERE action = ? AND resource_public_id = ?", String.class, action, attemptId); }
    private Balance balance(Fixture fixture) { return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved AS available FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", (result, row) -> new Balance(result.getLong("on_hand"), result.getLong("reserved"), result.getLong("available")), fixture.variantId(), fixture.locationId()); }

    private Browser login(String username) throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> response = request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, "application/x-www-form-urlencoded");
        assertThat(response.statusCode()).isEqualTo(200);
        return browser;
    }

    private HttpResponse<String> post(Browser browser, String body) throws Exception { return request(browser, "POST", "/api/v1/payment/provider-events", body, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, String type) throws Exception {
        Csrf csrf = csrf(browser);
        return browser.client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private String eventBody(String eventId, UUID attemptId, String outcome) { return "{\"providerEventId\":\"" + eventId + "\",\"paymentAttemptId\":\"" + attemptId + "\",\"outcome\":\"" + outcome + "\"}"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record State(String attempt, String order, String reservation, Balance balance) { }
    private record Purchase(UUID orderId, UUID reservationId, UUID attemptId) { }
    private record Fixture(UUID variantId, UUID locationId, String administratorLogin, String operationsLogin,
            String ownerLogin, String providerLogin, SessionPrincipal administrator, SessionPrincipal operations,
            SessionPrincipal owner, SessionPrincipal provider) { }
}
