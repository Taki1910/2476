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
import com.shoecommerce.inventory.InventoryAdjustmentService;
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
class VerticalSlice6ExternalIT {
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
    @Autowired PaymentAttemptService attempts;
    @Autowired PaymentProviderEventService providerEvents;
    @Autowired PickupFulfillmentService fulfillments;
    @LocalServerPort int port;

    @Test
    void provesPaidCommercialHandoffAndServerDerivedHttpLocationWithoutInventoryMutation() throws Exception {
        Fixture fixture = fixture(3);
        Purchase purchase = paidPurchase(fixture, 1, "handoff");
        Balance before = balance(fixture);

        Browser operations = login(fixture.operationsLogin());
        HttpResponse<String> response = post(operations, purchase.orderId(), "{\"locationId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        UUID fulfillmentId = UUID.fromString(body.get("id").asString());
        assertThat(body.get("orderId").asString()).isEqualTo(purchase.orderId().toString());
        assertThat(body.get("branchId").asString()).isEqualTo(fixture.branchId().toString());
        assertThat(body.get("locationId").asString()).isEqualTo(fixture.locationId().toString());
        assertThat(body.get("status").asString()).isEqualTo("PENDING");
        assertThat(orders.readOwn(fixture.owner(), purchase.orderId()).status()).isEqualTo("PAID");
        assertThat(attempts.readOwn(fixture.owner(), purchase.attemptId()).status()).isEqualTo("SUCCEEDED");
        assertThat(reservations.readOwn(fixture.owner(), purchase.reservationId()).status()).isEqualTo("CONSUMED");
        assertThat(balance(fixture)).isEqualTo(before);
        assertThat(storedScope(fulfillmentId)).isEqualTo(new Scope(fixture.branchId(), fixture.locationId()));
        assertThat(auditCount("PICKUP_FULFILLMENT_CREATED", fulfillmentId)).isEqualTo(1);
        assertThat(auditActorType("PICKUP_FULFILLMENT_CREATED", fulfillmentId)).isEqualTo("HUMAN");
    }

    @Test
    void provesPermissionAndExactActiveLocationScopeMatrix() {
        Fixture fixture = fixture(8);
        Purchase purchase = paidPurchase(fixture, 1, "scope");
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
                fixture.administrator(), fixture.owner(), fixture.provider(), inactive)) {
            assertThatThrownBy(() -> fulfillments.create(denied, purchase.orderId())).isInstanceOf(AccessDeniedException.class);
        }
        PickupFulfillmentService.PickupFulfillmentView created = fulfillments.create(fixture.operations(), purchase.orderId());
        assertThat(created.locationId()).isEqualTo(fixture.locationId());
        assertThat(fulfillmentCount(purchase.orderId())).isEqualTo(1);
    }

    @Test
    void rejectsPendingCancelledAndIncoherentPaidFacts() {
        Fixture fixture = fixture(8);
        Purchase pending = purchase(fixture, 1, "pending");
        assertThatThrownBy(() -> fulfillments.create(fixture.operations(), pending.orderId()))
                .isInstanceOf(BusinessConflictException.class);
        orders.cancelOwn(fixture.owner(), pending.orderId());
        assertThatThrownBy(() -> fulfillments.create(fixture.operations(), pending.orderId()))
                .isInstanceOf(BusinessConflictException.class);

        Purchase incoherent = paidPurchase(fixture, 1, "incoherent");
        jdbc.update("UPDATE inventory_reservation SET status = 'ADOPTED', consumed_at = NULL WHERE public_id = ?", incoherent.reservationId());
        assertThatThrownBy(() -> fulfillments.create(fixture.operations(), incoherent.orderId()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("reservation");
        assertThat(fulfillmentCount(incoherent.orderId())).isZero();
    }

    @Test
    void rejectsAContractLocationThatIsNoLongerEnabled() {
        Fixture fixture = fixture(2);
        Purchase purchase = paidPurchase(fixture, 1, "disabled-location");
        jdbc.update("UPDATE org_location SET enabled = 0 WHERE public_id = ?", fixture.locationId());
        assertThatThrownBy(() -> fulfillments.create(fixture.operations(), purchase.orderId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(fulfillmentCount(purchase.orderId())).isZero();
    }

    @Test
    void rejectsSequentialDuplicateWithStableConflict() {
        Fixture fixture = fixture(2);
        Purchase purchase = paidPurchase(fixture, 1, "sequential");
        fulfillments.create(fixture.operations(), purchase.orderId());
        assertThatThrownBy(() -> fulfillments.create(fixture.operations(), purchase.orderId()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Order already has a pickup fulfillment");
        assertThat(fulfillmentCount(purchase.orderId())).isEqualTo(1);
    }

    @Test
    void serializesConcurrentDuplicateCreationToOneWinner() throws Exception {
        Fixture fixture = fixture(2);
        Purchase purchase = paidPurchase(fixture, 1, "concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Future<?> blocker = executor.submit(() -> holdOrderLock(purchase.orderId(), lockHeld, release));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> first = executor.submit(() -> createResult(fixture.operations(), purchase.orderId(), entered));
            Future<String> second = executor.submit(() -> createResult(fixture.operations(), purchase.orderId(), entered));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "CONFLICT");
            assertThat(fulfillmentCount(purchase.orderId())).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void auditFailureRollsBackCreationAndRetrySucceeds() {
        Fixture fixture = fixture(2);
        Purchase purchase = paidPurchase(fixture, 1, "rollback");
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> fulfillments.create(fixture.operations(), purchase.orderId()))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(fulfillmentCount(purchase.orderId())).isZero();
        PickupFulfillmentService.PickupFulfillmentView retry = fulfillments.create(fixture.operations(), purchase.orderId());
        assertThat(fulfillmentCount(purchase.orderId())).isEqualTo(1);
        assertThat(auditCount("PICKUP_FULFILLMENT_CREATED", retry.id())).isEqualTo(1);
    }

    @Test
    void provesV8HighValueSqlConstraints() {
        Fixture fixture = fixture(8);
        Purchase createdPurchase = paidPurchase(fixture, 1, "sql-created");
        PickupFulfillmentService.PickupFulfillmentView created = fulfillments.create(fixture.operations(), createdPurchase.orderId());
        Purchase second = purchase(fixture, 1, "sql-second");
        Purchase third = purchase(fixture, 1, "sql-third");
        UUID otherBranch = scopes.createBranch(fixture.administrator(), fixture.prefix() + "C", "Constraint branch");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE pickup_fulfillment SET status = 'READY_FOR_PICKUP' WHERE public_id = ?", created.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFulfillment(UUID.randomUUID(), createdPurchase.orderId(), fixture.branchId(), fixture.locationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFulfillment(created.id(), second.orderId(), fixture.branchId(), fixture.locationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFulfillment(UUID.randomUUID(), third.orderId(), otherBranch, fixture.locationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pickup_fulfillment(public_id, order_id, branch_id, location_id, status, entity_version, created_at) SELECT NULL, orders.id, branches.id, locations.id, 'PENDING', 0, ? FROM commerce_order orders CROSS JOIN org_branch branches CROSS JOIN org_location locations WHERE orders.public_id = ? AND branches.public_id = ? AND locations.public_id = ?", Timestamp.from(Instant.now()), second.orderId(), fixture.branchId(), fixture.locationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(long stock) {
        String prefix = "S6" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String adminLogin = prefix.toLowerCase() + "-admin@example.com";
        String operationsLogin = prefix.toLowerCase() + "-ops@example.com";
        String ownerLogin = prefix.toLowerCase() + "-owner@example.com";
        String providerLogin = prefix.toLowerCase() + "-provider@example.com";
        SessionPrincipal administrator = bootstrapAdministrator(adminLogin);
        UUID operationsId = identities.createAccount(administrator, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(administrator, ownerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(administrator, providerLogin, PASSWORD, RoleCode.PROVIDER);
        UUID branchId = scopes.createBranch(administrator, prefix, "Vertical Slice 6");
        UUID locationId = scopes.createLocation(administrator, branchId, prefix + "-LOC", "Pickup floor");
        scopes.setAssignment(administrator, operationsId, branchId, locationId, true);
        SessionPrincipal operations = principal(operationsLogin);
        UUID productId = catalog.createProduct(operations, "Pickup Runner");
        UUID variantId = catalog.createVariant(operations, productId, prefix + "-SKU", "42", "Black");
        catalog.setPrice(operations, variantId, 120_000);
        adjustments.adjust(operations, variantId, locationId, stock, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(operations, variantId);
        return new Fixture(prefix, variantId, branchId, locationId, operationsLogin, administrator, operations,
                principal(ownerLogin), principal(providerLogin));
    }

    private SessionPrincipal staff(Fixture fixture, String login, RoleCode role, UUID branchId, UUID locationId,
            boolean active) {
        UUID accountId = identities.createAccount(fixture.administrator(), login, PASSWORD, role);
        scopes.setAssignment(fixture.administrator(), accountId, branchId, locationId, true);
        if (!active) scopes.setAssignment(fixture.administrator(), accountId, branchId, locationId, false);
        return principal(login);
    }

    private Purchase purchase(Fixture fixture, long quantity, String key) {
        UUID reservationId = reservations.reserve(fixture.owner(), fixture.variantId(), fixture.locationId(), quantity).id();
        UUID orderId = orders.create(fixture.owner(), reservationId).id();
        UUID attemptId = attempts.initiate(fixture.owner(), orderId, key).attempt().id();
        return new Purchase(orderId, reservationId, attemptId);
    }

    private Purchase paidPurchase(Fixture fixture, long quantity, String key) {
        Purchase purchase = purchase(fixture, quantity, key);
        providerEvents.apply(fixture.provider(), "paid-" + key, purchase.attemptId(), PaymentProviderEvent.Outcome.SUCCESS);
        return purchase;
    }

    private String createResult(SessionPrincipal actor, UUID orderId, CountDownLatch entered) {
        entered.countDown();
        try {
            fulfillments.create(actor, orderId);
            return "CREATED";
        } catch (BusinessConflictException exception) {
            return "CONFLICT";
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

    private SessionPrincipal bootstrapAdministrator(String login) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private void insertFulfillment(UUID publicId, UUID orderId, UUID branchId, UUID locationId) {
        jdbc.update("INSERT INTO pickup_fulfillment(public_id, order_id, branch_id, location_id, status, entity_version, created_at) SELECT ?, orders.id, branches.id, locations.id, 'PENDING', 0, ? FROM commerce_order orders CROSS JOIN org_branch branches CROSS JOIN org_location locations WHERE orders.public_id = ? AND branches.public_id = ? AND locations.public_id = ?", publicId, Timestamp.from(Instant.now()), orderId, branchId, locationId);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private int fulfillmentCount(UUID orderId) { return jdbc.queryForObject("SELECT COUNT(*) FROM pickup_fulfillment fulfillments JOIN commerce_order orders ON orders.id = fulfillments.order_id WHERE orders.public_id = ?", Integer.class, orderId); }
    private int auditCount(String action, UUID resourceId) { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND resource_public_id = ?", Integer.class, action, resourceId); }
    private String auditActorType(String action, UUID resourceId) { return jdbc.queryForObject("SELECT actor_type FROM audit_event WHERE action = ? AND resource_public_id = ?", String.class, action, resourceId); }
    private Scope storedScope(UUID fulfillmentId) { return jdbc.queryForObject("SELECT branches.public_id AS branch_id, locations.public_id AS location_id FROM pickup_fulfillment fulfillments JOIN org_branch branches ON branches.id = fulfillments.branch_id JOIN org_location locations ON locations.id = fulfillments.location_id WHERE fulfillments.public_id = ?", (row, index) -> new Scope(row.getObject("branch_id", UUID.class), row.getObject("location_id", UUID.class)), fulfillmentId); }
    private Balance balance(Fixture fixture) { return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved AS available FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)", (row, index) -> new Balance(row.getLong("on_hand"), row.getLong("reserved"), row.getLong("available")), fixture.variantId(), fixture.locationId()); }

    private Browser login(String username) throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> response = request(browser, "POST", "/api/v1/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD, "application/x-www-form-urlencoded");
        assertThat(response.statusCode()).isEqualTo(200);
        return browser;
    }

    private HttpResponse<String> post(Browser browser, UUID orderId, String body) throws Exception { return request(browser, "POST", "/api/v1/orders/" + orderId + "/pickup-fulfillment", body, "application/json"); }
    private HttpResponse<String> request(Browser browser, String method, String path, String body, String type) throws Exception {
        Csrf csrf = csrf(browser);
        return browser.client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", type).header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }

    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Scope(UUID branchId, UUID locationId) { }
    private record Purchase(UUID orderId, UUID reservationId, UUID attemptId) { }
    private record Fixture(String prefix, UUID variantId, UUID branchId, UUID locationId, String operationsLogin,
            SessionPrincipal administrator, SessionPrincipal operations, SessionPrincipal owner, SessionPrincipal provider) { }
}
