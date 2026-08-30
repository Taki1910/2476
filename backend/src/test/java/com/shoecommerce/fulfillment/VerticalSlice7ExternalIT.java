package com.shoecommerce.fulfillment;

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
import java.util.List;
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
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.payment.PaymentProviderEvent;
import com.shoecommerce.payment.PaymentProviderEventService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice7ExternalIT {
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
    @Autowired PickupFulfillmentService fulfillments;
    @LocalServerPort int port;

    @Test
    void startsPickingThroughTaskEndpointWithoutChangingCommercialOrInventoryFacts() throws Exception {
        Fixture fixture = fixture(3);
        Flow flow = flow(fixture, "happy");
        Balance before = balance(fixture);

        Browser operations = login(fixture.operationsLogin());
        HttpResponse<String> response = post(operations, flow.fulfillmentId(),
                "{\"status\":\"READY_FOR_PICKUP\",\"locationId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("id").asString()).isEqualTo(flow.fulfillmentId().toString());
        assertThat(body.get("status").asString()).isEqualTo("PICKING");
        assertThat(body.get("locationId").asString()).isEqualTo(fixture.locationId().toString());
        assertThat(body.get("pickingStartedAt").isNull()).isFalse();
        assertCommercialState(fixture, flow);
        assertThat(balance(fixture)).isEqualTo(before);
        assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isEqualTo(1);
        assertThat(auditActorType("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isEqualTo("HUMAN");
    }

    @Test
    void enforcesPermissionAndCurrentExactLocationScope() {
        Fixture fixture = fixture(8);
        Flow flow = flow(fixture, "scope");
        String prefix = fixture.prefix().toLowerCase();
        UUID wrongLocation = scopes.createLocation(fixture.administrator(), fixture.branchId(), fixture.prefix() + "-WRONG", "Wrong same branch");
        UUID otherBranch = scopes.createBranch(fixture.administrator(), fixture.prefix() + "B", "Other branch");
        UUID otherLocation = scopes.createLocation(fixture.administrator(), otherBranch, fixture.prefix() + "B-LOC", "Other branch floor");

        SessionPrincipal missingPermission = staff(fixture, prefix + "-cashier@example.com", RoleCode.CASHIER, fixture.branchId(), fixture.locationId(), true);
        SessionPrincipal branchOnly = staff(fixture, prefix + "-branch@example.com", RoleCode.OPERATIONS, fixture.branchId(), null, true);
        SessionPrincipal wrongSameBranch = staff(fixture, prefix + "-wrong@example.com", RoleCode.OPERATIONS, fixture.branchId(), wrongLocation, true);
        SessionPrincipal crossBranch = staff(fixture, prefix + "-cross@example.com", RoleCode.OPERATIONS, otherBranch, otherLocation, true);
        SessionPrincipal inactive = staff(fixture, prefix + "-inactive@example.com", RoleCode.OPERATIONS, fixture.branchId(), fixture.locationId(), false);

        for (SessionPrincipal denied : List.of(missingPermission, branchOnly, wrongSameBranch, crossBranch,
                inactive, fixture.administrator(), fixture.owner(), fixture.provider())) {
            assertThatThrownBy(() -> fulfillments.startPicking(denied, flow.fulfillmentId()))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(fulfillments.startPicking(fixture.operations(), flow.fulfillmentId()).status()).isEqualTo("PICKING");
    }

    @Test
    void deniesDisabledAuthoritativeLocationAndKeepsPending() {
        Fixture fixture = fixture(2);
        Flow flow = flow(fixture, "disabled");
        jdbc.update("UPDATE org_location SET enabled = 0 WHERE public_id = ?", fixture.locationId());

        assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), flow.fulfillmentId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(storedTransition(flow.fulfillmentId())).isEqualTo(new Transition("PENDING", null));
        assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isZero();
    }

    @Test
    void rejectsIncoherentTerminalCommercialFactsWithoutRepair() {
        Fixture fixture = fixture(8);
        Flow orderMismatch = flow(fixture, "bad-order");
        jdbc.update("UPDATE commerce_order SET status = 'PENDING_PAYMENT', paid_at = NULL WHERE public_id = ?", orderMismatch.orderId());
        assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), orderMismatch.fulfillmentId()))
                .isInstanceOf(BusinessConflictException.class);

        Flow paymentMismatch = flow(fixture, "bad-payment");
        jdbc.update("UPDATE payment_attempt SET status = 'FAILED' WHERE public_id = ?", paymentMismatch.attemptId());
        assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), paymentMismatch.fulfillmentId()))
                .isInstanceOf(BusinessConflictException.class);

        Flow reservationMismatch = flow(fixture, "bad-reservation");
        jdbc.update("UPDATE inventory_reservation SET status = 'ADOPTED', consumed_at = NULL WHERE public_id = ?", reservationMismatch.reservationId());
        assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), reservationMismatch.fulfillmentId()))
                .isInstanceOf(BusinessConflictException.class);

        assertThat(storedTransition(orderMismatch.fulfillmentId()).status()).isEqualTo("PENDING");
        assertThat(storedTransition(paymentMismatch.fulfillmentId()).status()).isEqualTo("PENDING");
        assertThat(storedTransition(reservationMismatch.fulfillmentId()).status()).isEqualTo("PENDING");
    }

    @Test
    void repeatedCommandIsStableConflictWithOneTimestampAndAudit() {
        Fixture fixture = fixture(2);
        Flow flow = flow(fixture, "duplicate");
        fulfillments.startPicking(fixture.operations(), flow.fulfillmentId());
        Transition afterFirst = storedTransition(flow.fulfillmentId());

        assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), flow.fulfillmentId()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Pickup fulfillment is not pending");
        Transition stored = storedTransition(flow.fulfillmentId());
        assertThat(stored.status()).isEqualTo("PICKING");
        assertThat(stored.pickingStartedAt()).isEqualTo(afterFirst.pickingStartedAt());
        assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isEqualTo(1);
    }

    @Test
    void concurrentStartsLockFulfillmentAndProduceOneTransition() throws Exception {
        Fixture fixture = fixture(2);
        Flow flow = flow(fixture, "concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdFulfillmentLock(flow.fulfillmentId(), lockHeld, release));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> first = executor.submit(() -> startResult(fixture.operations(), flow.fulfillmentId(), entered));
            Future<String> second = executor.submit(() -> startResult(fixture.operations(), flow.fulfillmentId(), entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("STARTED", "CONFLICT");
            assertThat(storedTransition(flow.fulfillmentId()).status()).isEqualTo("PICKING");
            assertThat(storedTransition(flow.fulfillmentId()).pickingStartedAt()).isNotNull();
            assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void auditFailureRollsBackTransitionAndRetrySucceedsExactlyOnce() {
        Fixture fixture = fixture(2);
        Flow flow = flow(fixture, "rollback");
        Balance before = balance(fixture);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> fulfillments.startPicking(fixture.operations(), flow.fulfillmentId()))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }

        assertThat(storedTransition(flow.fulfillmentId())).isEqualTo(new Transition("PENDING", null));
        assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isZero();
        assertCommercialState(fixture, flow);
        assertThat(balance(fixture)).isEqualTo(before);

        fulfillments.startPicking(fixture.operations(), flow.fulfillmentId());
        assertThat(storedTransition(flow.fulfillmentId()).status()).isEqualTo("PICKING");
        assertThat(auditCount("PICKUP_PICKING_STARTED", flow.fulfillmentId())).isEqualTo(1);
    }

    @Test
    void provesV9LifecycleConstraints() {
        Fixture fixture = fixture(4);
        Flow flow = flow(fixture, "constraints");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_fulfillment SET status = 'READY_FOR_PICKUP' WHERE public_id = ?", flow.fulfillmentId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_fulfillment SET picking_started_at = ? WHERE public_id = ?", Timestamp.from(Instant.now()), flow.fulfillmentId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_fulfillment SET status = 'PICKING' WHERE public_id = ?", flow.fulfillmentId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        fulfillments.startPicking(fixture.operations(), flow.fulfillmentId());
        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_fulfillment SET picking_started_at = NULL WHERE public_id = ?", flow.fulfillmentId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(long stock) {
        String prefix = "S7" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String operationsLogin = prefix.toLowerCase() + "-ops@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String providerLogin = prefix.toLowerCase() + "-provider@example.com";
        SessionPrincipal administrator = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(administrator, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(administrator, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(administrator, providerLogin, PASSWORD, RoleCode.PROVIDER);
        UUID branchId = scopes.createBranch(administrator, prefix, "Vertical Slice 7");
        UUID locationId = scopes.createLocation(administrator, branchId, prefix + "-LOC", "Picking floor");
        scopes.setAssignment(administrator, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(operationsLogin);
        UUID productId = catalog.createProduct(operations, "Picking Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        catalog.setStock(operations, variantId, locationId, stock);
        catalog.publish(operations, variantId);
        return new Fixture(prefix, variantId, branchId, locationId, operationsLogin, administrator, operations,
                principal(ownerLogin), principal(providerLogin));
    }

    private Flow flow(Fixture fixture, String key) {
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), 1).id();
        UUID orderId = orders.create(fixture.owner(), reservationId).id();
        UUID attemptId = attempts.initiate(fixture.owner(), orderId, key).attempt().id();
        providerEvents.apply(fixture.provider(), "paid-" + key, attemptId, PaymentProviderEvent.Outcome.SUCCESS);
        UUID fulfillmentId = fulfillments.create(fixture.operations(), orderId).id();
        return new Flow(orderId, reservationId, attemptId, fulfillmentId);
    }

    private SessionPrincipal staff(Fixture fixture, String login, RoleCode role, UUID branchId, UUID locationId,
            boolean active) {
        UUID accountId = identities.createAccount(fixture.administrator(), login, PASSWORD, role);
        scopes.setAssignment(fixture.administrator(), accountId, branchId, locationId, true);
        if (!active) scopes.setAssignment(fixture.administrator(), accountId, branchId, locationId, false);
        return principal(login);
    }

    private String startResult(SessionPrincipal actor, UUID fulfillmentId, CountDownLatch entered) {
        entered.countDown();
        try {
            fulfillments.startPicking(actor, fulfillmentId);
            return "STARTED";
        } catch (BusinessConflictException exception) {
            return "CONFLICT";
        }
    }

    private void holdFulfillmentLock(UUID fulfillmentId, CountDownLatch lockHeld, CountDownLatch release) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM pickup_fulfillment WITH (UPDLOCK, HOLDLOCK) WHERE public_id = ?")) {
            connection.setAutoCommit(false);
            statement.setObject(1, fulfillmentId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new IllegalStateException("Fulfillment lock target missing"); }
            lockHeld.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fulfillment lock was not released");
            connection.rollback();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertCommercialState(Fixture fixture, Flow flow) {
        assertThat(orders.readOwn(fixture.owner(), flow.orderId()).status()).isEqualTo("PAID");
        assertThat(attempts.readOwn(fixture.owner(), flow.attemptId()).status()).isEqualTo("SUCCEEDED");
        assertThat(reservations.readOwn(fixture.owner(), flow.reservationId()).status()).isEqualTo("CONSUMED");
    }

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private int auditCount(String action, UUID resourceId) { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND resource_public_id = ?", Integer.class, action, resourceId); }
    private String auditActorType(String action, UUID resourceId) { return jdbc.queryForObject("SELECT actor_type FROM audit_event WHERE action = ? AND resource_public_id = ?", String.class, action, resourceId); }
    private Transition storedTransition(UUID fulfillmentId) { return jdbc.queryForObject("SELECT status, picking_started_at FROM pickup_fulfillment WHERE public_id = ?", (row, index) -> new Transition(row.getString("status"), row.getTimestamp("picking_started_at") == null ? null : row.getTimestamp("picking_started_at").toInstant()), fulfillmentId); }
    private Balance balance(Fixture fixture) { return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved AS available FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", (row, index) -> new Balance(row.getLong("on_hand"), row.getLong("reserved"), row.getLong("available")), fixture.variantId(), fixture.locationId()); }

    private Browser login(String username) throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> response = request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, "application/x-www-form-urlencoded");
        assertThat(response.statusCode()).isEqualTo(200);
        return browser;
    }

    private HttpResponse<String> post(Browser browser, UUID fulfillmentId, String body) throws Exception { return request(browser, "POST", "/api/v1/pickup-fulfillments/" + fulfillmentId + "/start-picking", body, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, String type) throws Exception {
        Csrf csrf = csrf(browser);
        return browser.client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Transition(String status, Instant pickingStartedAt) { }
    private record Flow(UUID orderId, UUID reservationId, UUID attemptId, UUID fulfillmentId) { }
    private record Fixture(String prefix, UUID variantId, UUID branchId, UUID locationId, String operationsLogin,
            SessionPrincipal administrator, SessionPrincipal operations, SessionPrincipal owner, SessionPrincipal provider) { }
}
