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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.catalog.StorefrontCatalogService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.pricing.PriceQuoteService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(VerticalSlice3CheckoutExternalIT.TestClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice3CheckoutExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    private static final Instant TEST_NOW = Instant.parse("2026-08-26T12:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired StorefrontCatalogService storefront;
    @Autowired PriceQuoteService pricing;
    @Autowired CustomerOrderService orders;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void resetClock() {
        clock.set(TEST_NOW);
    }

    @Test
    void createsOwnedPendingOrderFromImmutableQuoteAndReplaysIdempotently() {
        Fixture fixture = fixture("success", 3);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        catalog.setPrice(fixture.operations(), fixture.variant(), 199_000);

        var created = orders.checkout(fixture.customerA(), quote.id(), "checkout-success");
        var replay = orders.checkout(fixture.customerA(), quote.id(), "checkout-success");

        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(created.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.unitPriceAmount()).isEqualTo(149_000);
        assertThat(created.totalAmount()).isEqualTo(149_000);
        assertThat(created.currency()).isEqualTo("VND");
        assertThat(created.priceQuoteId()).isEqualTo(quote.id());
        assertThat(created.priceVersionId()).isEqualTo(quote.priceVersionId());
        assertThat(created.sku()).startsWith("VS3C-SUCCESS-");
        assertThat(created.size()).isEqualTo("42");
        assertThat(orders.readOwn(fixture.customerA(), created.id())).extracting(
                CustomerOrderService.OrderView::id,
                CustomerOrderService.OrderView::reservationId,
                CustomerOrderService.OrderView::priceQuoteId,
                CustomerOrderService.OrderView::totalAmount)
                .containsExactly(created.id(), created.reservationId(), created.priceQuoteId(), created.totalAmount());
        assertThatThrownBy(() -> orders.readOwn(fixture.customerB(), created.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE price_quote_public_id = ?", Integer.class, quote.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE public_id = ? AND owner_account_public_id = ? AND status = 'ADOPTED'", Integer.class, created.reservationId(), fixture.customerA().publicId())).isOne();
        assertThat(balance(fixture).reserved()).isEqualTo(1);
        assertThat(balance(fixture).onHand()).isEqualTo(3);

        var secondQuote = pricing.quote(fixture.customerA(), fixture.variant());
        assertThatThrownBy(() -> orders.checkout(fixture.customerA(), secondQuote.id(), "checkout-success"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("idempotency key");
        assertThatThrownBy(() -> orders.checkout(fixture.customerA(), quote.id(), "another-key"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("already created");

        var customerBQuote = pricing.quote(fixture.customerB(), fixture.variant());
        var customerBOrder = orders.checkout(fixture.customerB(), customerBQuote.id(), "checkout-success");
        assertThat(customerBOrder.id()).isNotEqualTo(created.id());
        assertThat(customerBOrder.ownerAccountId()).isEqualTo(fixture.customerB().publicId());
    }

    @Test
    void rejectsExpiredForeignAndNonSellableQuotesWithoutHoldingStock() {
        Fixture fixture = fixture("validation", 4);
        var foreign = pricing.quote(fixture.customerA(), fixture.variant());
        assertThatThrownBy(() -> orders.checkout(fixture.customerB(), foreign.id(), "foreign"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Price quote not found.");

        var expired = pricing.quote(fixture.customerA(), fixture.variant());
        clock.set(expired.expiresAt());
        assertThatThrownBy(() -> orders.checkout(fixture.customerA(), expired.id(), "expired"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("expired");

        var unpublished = pricing.quote(fixture.customerA(), fixture.variant());
        jdbc.update("UPDATE catalog_product_variant SET lifecycle_status = 'DRAFT' WHERE public_id = ?", fixture.variant());
        assertThatThrownBy(() -> orders.checkout(fixture.customerA(), unpublished.id(), "unpublished"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("no longer available");
        assertThat(balance(fixture)).isEqualTo(new Balance(4, 0, 4));
    }

    @Test
    void realSqlServerRaceAllowsExactlyOneCheckoutForTheLastUnit() throws Exception {
        Fixture fixture = fixture("last-unit", 1);
        var quoteA = pricing.quote(fixture.customerA(), fixture.variant());
        var quoteB = pricing.quote(fixture.customerB(), fixture.variant());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> checkoutAfterBarrier(fixture.customerA(), quoteA.id(), "last-a", ready, start));
            var second = executor.submit(() -> checkoutAfterBarrier(fixture.customerB(), quoteB.id(), "last-b", ready, start));
            ready.await();
            start.countDown();
            outcomes = List.of(first.get(), second.get());
        }

        assertThat(outcomes).filteredOn(CustomerOrderService.OrderView.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessConflictException.class::isInstance).hasSize(1);
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order items JOIN commerce_order_item lines ON lines.order_id = items.id WHERE lines.variant_public_id = ? AND items.price_quote_public_id IS NOT NULL", Integer.class, fixture.variant())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation reservations JOIN catalog_product_variant variants ON variants.id = reservations.variant_id WHERE variants.public_id = ? AND reservations.status IN ('ACTIVE', 'ADOPTED')", Integer.class, fixture.variant())).isOne();
    }

    @Test
    void concurrentCheckoutsBothSucceedWhenTwoUnitsExist() throws Exception {
        Fixture fixture = fixture("two-units", 2);
        var quoteA = pricing.quote(fixture.customerA(), fixture.variant());
        var quoteB = pricing.quote(fixture.customerB(), fixture.variant());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> checkoutAfterBarrier(fixture.customerA(), quoteA.id(), "two-a", ready, start));
            var second = executor.submit(() -> checkoutAfterBarrier(fixture.customerB(), quoteB.id(), "two-b", ready, start));
            ready.await();
            start.countDown();
            outcomes = List.of(first.get(), second.get());
        }

        assertThat(outcomes).allMatch(CustomerOrderService.OrderView.class::isInstance);
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 2, 0));
    }

    @Test
    void databaseFailureAfterReservationRollsBackOrderReservationAndBalance() {
        Fixture fixture = fixture("rollback", 1);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        jdbc.execute("""
                CREATE TRIGGER TR_vs3_checkout_rollback ON commerce_order_item AFTER INSERT AS
                BEGIN
                    IF EXISTS (SELECT 1 FROM inserted WHERE sku_snapshot LIKE 'VS3C-ROLLBACK-%')
                        THROW 51000, 'Controlled checkout order failure', 1;
                END
                """);
        try {
            assertThatThrownBy(() -> orders.checkout(fixture.customerA(), quote.id(), "rollback"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER TR_vs3_checkout_rollback");
        }
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE price_quote_public_id = ?", Integer.class, quote.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation reservations JOIN catalog_product_variant variants ON variants.id = reservations.variant_id WHERE variants.public_id = ?", Integer.class, fixture.variant())).isZero();
    }

    @Test
    void nearExpiryQuoteCreatesAReservationWithItsOwnFreshDeadline() {
        Fixture fixture = fixture("near-expiry", 1);
        var quoteA = pricing.quote(fixture.customerA(), fixture.variant());
        clock.set(quoteA.expiresAt().minusSeconds(1));

        var orderA = orders.checkout(fixture.customerA(), quoteA.id(), "near-expiry-a");
        assertThat(orderA.reservationExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(orderA.reservationExpiresAt()).isAfter(quoteA.expiresAt());

        clock.set(quoteA.expiresAt());
        var detail = storefront.detail(fixture.customerB(), fixture.product());

        assertThat(detail.variants()).singleElement().extracting(StorefrontCatalogService.VariantView::availability)
                .isEqualTo("UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, orderA.reservationId())).isEqualTo("ADOPTED");
        assertThat(jdbc.queryForObject("SELECT status FROM commerce_order WHERE public_id = ?", String.class, orderA.id())).isEqualTo("PENDING_PAYMENT");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void expiredHoldIsNormalizedForAvailabilityAndCanBeReservedAgainExactlyOnce() {
        Fixture fixture = fixture("expiry", 1);
        var quoteA = pricing.quote(fixture.customerA(), fixture.variant());
        var orderA = orders.checkout(fixture.customerA(), quoteA.id(), "expiry-a");
        clock.set(orderA.reservationExpiresAt());

        var available = storefront.detail(fixture.customerB(), fixture.product());

        assertThat(available.variants()).singleElement().extracting(StorefrontCatalogService.VariantView::availability)
                .isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, orderA.reservationId())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM commerce_order WHERE public_id = ?", String.class, orderA.id())).isEqualTo("CANCELLED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));

        var quoteB = pricing.quote(fixture.customerB(), fixture.variant());
        var orderB = orders.checkout(fixture.customerB(), quoteB.id(), "expiry-b");
        storefront.detail(fixture.customerA(), fixture.product());

        assertThat(orderB.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, orderA.reservationId())).isEqualTo("EXPIRED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void cancellationReleaseIsNotRepeatedByLaterExpiryNormalization() {
        Fixture fixture = fixture("cancel-expiry", 1);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        var order = orders.checkout(fixture.customerA(), quote.id(), "cancel-expiry");

        assertThat(orders.cancelOwn(fixture.customerA(), order.id()).status()).isEqualTo("CANCELLED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        clock.set(order.reservationExpiresAt());
        storefront.detail(fixture.customerB(), fixture.product());

        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, order.reservationId())).isEqualTo("RELEASED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
    }

    @Test
    void checkoutApiEnforcesCsrfValidationStableErrorsAndOwnership() throws Exception {
        Fixture fixture = fixture("api", 1);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        Browser a = new Browser();
        Browser b = new Browser();
        login(a, fixture.customerALogin());
        login(b, fixture.customerBLogin());

        HttpResponse<String> missingKey = request(a, "/api/v1/orders/checkout", "{\"quoteId\":\"" + quote.id() + "\"}", null);
        assertThat(missingKey.statusCode()).isEqualTo(400);
        assertThat(missingKey.body()).contains("INVALID_IDEMPOTENCY_KEY");
        HttpResponse<String> malformed = request(a, "/api/v1/orders/checkout", "{}", "api-malformed");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).contains("VALIDATION_FAILED");
        clock.advance(Duration.ofMinutes(1));
        HttpResponse<String> created = request(a, "/api/v1/orders/checkout", "{\"quoteId\":\"" + quote.id() + "\"}", "api-created");
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode order = json.readTree(created.body());
        assertThat(order.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("unitPriceAmount").asLong()).isEqualTo(149_000);
        assertThat(Instant.parse(order.get("reservationExpiresAt").asString())).isAfter(quote.expiresAt());
        HttpResponse<String> replay = request(a, "/api/v1/orders/checkout", "{\"quoteId\":\"" + quote.id() + "\"}", "api-created");
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(json.readTree(replay.body()).get("id").asString()).isEqualTo(order.get("id").asString());
        assertThat(a.client.send(HttpRequest.newBuilder(uri("/api/v1/orders/" + order.get("id").asString())).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertThat(b.client.send(HttpRequest.newBuilder(uri("/api/v1/orders/" + order.get("id").asString())).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    private Object checkoutAfterBarrier(SessionPrincipal customer, UUID quoteId, String key,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try { return orders.checkout(customer, quoteId, key); }
        catch (BusinessConflictException exception) { return exception; }
    }

    private Fixture fixture(String suffix, long stock) {
        SessionPrincipal admin = bootstrapAdmin();
        String operationsLogin = "vs3c-" + suffix + "-ops-" + UUID.randomUUID() + "@example.com";
        String customerALogin = "vs3c-" + suffix + "-a-" + UUID.randomUUID() + "@example.com";
        String customerBLogin = "vs3c-" + suffix + "-b-" + UUID.randomUUID() + "@example.com";
        UUID operationsId = identities.createAccount(admin, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, customerALogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(admin, customerBLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, "VS3C-" + suffix + "-" + shortId(), "Checkout branch");
        UUID location = scopes.createLocation(admin, branch, "FLOOR-" + shortId(), "Sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        SessionPrincipal operations = principal(operationsLogin);
        UUID product = catalog.createProduct(operations, "Checkout Runner " + suffix);
        UUID variant = catalog.createVariant(operations, product, "VS3C-" + suffix + "-" + shortId(), "42", "Ink");
        catalog.setPrice(operations, variant, 149_000);
        catalog.setStock(operations, variant, location, stock);
        catalog.publish(operations, variant);
        return new Fixture(product, variant, operations, principal(customerALogin), principal(customerBLogin), customerALogin, customerBLogin);
    }

    private Balance balance(Fixture fixture) {
        return jdbc.queryForObject("SELECT balances.on_hand, balances.reserved, balances.on_hand - balances.reserved FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id WHERE variants.public_id = ?", (rs, row) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)), fixture.variant());
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID(); String login = "vs3c-admin-" + id + "@example.com"; Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
    private void login(Browser browser, String username) throws Exception {
        Csrf csrf = csrf(browser);
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/x-www-form-urlencoded").header(csrf.header(), csrf.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
    private HttpResponse<String> request(Browser browser, String path, String body, String idempotencyKey) throws Exception {
        Csrf csrf = csrf(browser);
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").header(csrf.header(), csrf.token());
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        return browser.client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Fixture(UUID product, UUID variant, SessionPrincipal operations, SessionPrincipal customerA,
            SessionPrincipal customerB, String customerALogin, String customerBLogin) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(TEST_NOW);
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        MutableClock(Instant initial) {
            this(new AtomicReference<>(initial), ZoneOffset.UTC);
        }

        private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        void set(Instant instant) { current.set(instant); }
        void advance(Duration duration) { current.updateAndGet(instant -> instant.plus(duration)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId newZone) { return new MutableClock(current, newZone); }
        @Override public Instant instant() { return current.get(); }
    }
}
