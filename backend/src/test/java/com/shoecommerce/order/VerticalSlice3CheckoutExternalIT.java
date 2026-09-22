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
import com.shoecommerce.promotion.PromotionService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.fulfillment.PickupFulfillment;
import com.shoecommerce.merchandising.StorefrontMerchandisingService;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.CtaKind;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.MediaKey;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.PublishDraft;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SaveDraft;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SectionInput;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SectionType;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.CartQuoteService;

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
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired StorefrontCatalogService storefront;
    @Autowired PriceQuoteService pricing;
    @Autowired CartQuoteService cartPricing;
    @Autowired CustomerOrderService orders;
    @Autowired PaymentAttemptService payments;
    @Autowired StorefrontMerchandisingService merchandising;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void resetClock() {
        clock.set(TEST_NOW);
        jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE status='PUBLISHED'",Timestamp.from(TEST_NOW));
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
    void checkoutSnapshotsQuantityAndOrdersAreOwnerScoped() {
        Fixture fixture = fixture("quantity", 5);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());

        var order = orders.checkout(fixture.customerA(), quote.id(), 3, "checkout-quantity");

        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.totalAmount()).isEqualTo(447_000);
        assertThat(orders.readOwnOrders(fixture.customerA(), 0, 20).items())
                .extracting(CustomerOrderService.OrderView::id).containsExactly(order.id());
        assertThat(orders.readOwnOrders(fixture.customerB(), 0, 20).items()).isEmpty();
        assertThatThrownBy(() -> orders.checkout(fixture.customerA(), quote.id(), 2, "checkout-quantity"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("idempotency key");
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
        var detail = storefront.detail(fixture.product());

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

        var available = storefront.detail(fixture.product());

        assertThat(available.variants()).singleElement().extracting(StorefrontCatalogService.VariantView::availability)
                .isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, orderA.reservationId())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM commerce_order WHERE public_id = ?", String.class, orderA.id())).isEqualTo("CANCELLED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));

        var quoteB = pricing.quote(fixture.customerB(), fixture.variant());
        var orderB = orders.checkout(fixture.customerB(), quoteB.id(), "expiry-b");
        storefront.detail(fixture.product());

        assertThat(orderB.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id = ?", String.class, orderA.reservationId())).isEqualTo("EXPIRED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 1, 0));
    }

    @Test
    void storefrontPublicationDoesNotRunCheckoutExpiryInsideItsSerializableTransaction() {
        Fixture fixture = fixture("storefront-publish", 1);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        var order = orders.checkout(fixture.customerA(), quote.id(), "storefront-publish");
        clock.set(order.reservationExpiresAt());
        jdbc.update("DELETE FROM storefront_page_revision");
        jdbc.update("""
                INSERT INTO iam_account_permission(account_id,permission_id,granted_at)
                SELECT accounts.id,permissions.id,?
                FROM iam_user_account accounts CROSS JOIN iam_permission permissions
                WHERE accounts.public_id=? AND permissions.code='STOREFRONT_MANAGE'
                """, Timestamp.from(clock.instant()), fixture.operations().publicId());
        SessionPrincipal manager = principal(fixture.operations().getUsername());
        var draft = merchandising.saveDraft(manager, new SaveDraft(null, null, List.of(
                new SectionInput(SectionType.HERO_BANNER, true, "Giày", "Shoes", null, null,
                        "Xem ưu đãi", "View offers", CtaKind.OFFERS, null, null, null,
                        MediaKey.COURT_CLASSIC, List.of()))));

        assertThat(merchandising.publish(manager,
                new PublishDraft(draft.revisionId(), draft.entityVersion(), null)).status())
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id=?",
                String.class, order.reservationId())).isEqualTo("ADOPTED");

        storefront.browse();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservation WHERE public_id=?",
                String.class, order.reservationId())).isEqualTo("EXPIRED");
    }

    @Test
    void cancellationReleaseIsNotRepeatedByLaterExpiryNormalization() {
        Fixture fixture = fixture("cancel-expiry", 1);
        var quote = pricing.quote(fixture.customerA(), fixture.variant());
        var order = orders.checkout(fixture.customerA(), quote.id(), "cancel-expiry");

        assertThat(orders.cancelOwn(fixture.customerA(), order.id()).status()).isEqualTo("CANCELLED");
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        clock.set(order.reservationExpiresAt());
        storefront.detail(fixture.product());

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

        HttpResponse<String> legacy = request(a, "/api/v1/orders", "{\"reservationId\":\"" + UUID.randomUUID() + "\"}", "legacy-order");
        assertThat(legacy.statusCode()).isEqualTo(405); // GET /orders now exists; direct POST is still forbidden.

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

    @Test
    void twoLineCheckoutSnapshotsEveryPriceAndOwnerHistory() {
        Fixture f = fixture("multi-two", 5);
        UUID second = extraVariant(f, "41", 900_000, 5);
        var demand = List.of(line(f.variant(), 1), line(second, 2));
        var quote = cartPricing.quote(f.customerA(), demand);
        catalog.setPrice(f.operations(), f.variant(), 200_000);
        catalog.setPrice(f.operations(), second, 950_000);
        var order = orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-two");
        assertThat(order.itemCount()).isEqualTo(2);
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.totalAmount()).isEqualTo(1_949_000);
        assertThat(order.variantId()).isNull();
        assertThat(order.unitPriceAmount()).isNull();
        assertThat(order.items()).extracting(CustomerOrderService.OrderLine::totalAmount).containsExactlyInAnyOrder(149_000L, 1_800_000L);
        assertThat(order.items()).extracting(CustomerOrderService.OrderLine::reservationId).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(order.items()).extracting(CustomerOrderService.OrderLine::priceVersionId).containsExactlyInAnyOrderElementsOf(
                quote.items().stream().map(CartQuoteService.LineView::priceVersionId).toList());
        assertThat(order.items()).extracting(CustomerOrderService.OrderLine::locationId).containsOnly(location(f));
        assertThat(orders.readOwnOrders(f.customerA(), 0, 20).items()).singleElement().extracting(CustomerOrderService.OrderView::itemCount).isEqualTo(2);
        assertThat(orders.readOwnOrders(f.customerB(), 0, 20).items()).isEmpty();
        assertThatThrownBy(() -> orders.readOwn(f.customerB(), order.id())).isInstanceOf(AccessDeniedException.class);
        assertThat(balance(f).reserved()).isOne();
        assertThat(reserved(second)).isEqualTo(2);
    }

    @Test
    void threeLineCheckoutMergesDuplicatesAndRejectsOverLimit() {
        Fixture f = fixture("multi-three", 5);
        UUID second = extraVariant(f, "41", 900_000, 5);
        UUID third = extraVariant(f, "40", 1_490_000, 5);
        var demand = List.of(line(third, 1), line(second, 1), line(f.variant(), 1), line(second, 1));
        var quote = cartPricing.quote(f.customerA(), demand);
        var order = orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-three");
        assertThat(order.items()).hasSize(3);
        assertThat(order.totalAmount()).isEqualTo(3_439_000);
        assertThat(order.items().stream().filter(item -> item.variantId().equals(second)).findFirst().orElseThrow().quantity()).isEqualTo(2);
        assertThatThrownBy(() -> cartPricing.quote(f.customerA(), List.of(line(third, 6), line(third, 5))))
                .isInstanceOf(com.shoecommerce.platform.api.InvalidRequestException.class);
    }

    @Test
    void unavailableSecondLineRollsBackTheWholeCheckout() {
        Fixture f = fixture("multi-stock", 3);
        UUID second = extraVariant(f, "41", 900_000, 3);
        var demand = List.of(line(f.variant(), 2), line(second, 2));
        var quote = cartPricing.quote(f.customerA(), demand);
        adjustments.adjust(f.operations(), second, location(f), 1, "Competing stock change", "multi-stock-reduce");
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-stock"))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(balance(f)).isEqualTo(new Balance(3, 0, 3));
        assertThat(reserved(second)).isZero();
        assertNoCartEffects(f);
    }

    @Test
    void checkoutRejectsItemsThatNoLongerShareAPickupLocation() {
        Fixture f = fixture("multi-location", 3);
        UUID second = extraVariant(f, "41", 900_000, 3);
        var demand = List.of(line(f.variant(), 1), line(second, 1));
        var quote = cartPricing.quote(f.customerA(), demand);
        var admin = bootstrapAdmin();
        UUID branch = scopes.createBranch(admin, "CART-B-" + shortId(), "Other pickup branch");
        UUID otherLocation = scopes.createLocation(admin, branch, "CART-L-" + shortId(), "Other pickup floor");
        scopes.setAssignment(admin, f.operations().publicId(), branch, otherLocation, true);
        var reassignedOperations = principal(f.operations().getUsername());
        adjustments.adjust(reassignedOperations, second, otherLocation, 3, "Other location stock", UUID.randomUUID().toString());
        adjustments.adjust(reassignedOperations, second, location(f), 0, "Original location unavailable", UUID.randomUUID().toString());
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand, "different-locations"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("pickup location");
        assertNoCartEffects(f);
        assertThat(balance(f).reserved()).isZero(); assertThat(reserved(second)).isZero();
    }

    @Test
    void cartCannotExceedIntegerOrSupportedFullPaymentAmount() {
        Fixture f = fixture("multi-money", 3);
        catalog.setPrice(f.operations(), f.variant(), com.shoecommerce.pricing.VariantPrice.MAX_AMOUNT);
        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> cartPricing.quote(f.customerA(), List.of(line(f.variant(), 2))))
                .isInstanceOf(com.shoecommerce.platform.api.InvalidRequestException.class).hasMessageContaining("payment limit");
        assertNoCartEffects(f);
    }

    @Test
    void cartAuditFailureRollsBackEveryReservationAndOrderItem() {
        Fixture f = fixture("multi-audit", 3);
        UUID promotion = promotion(f, 1L, null, 100);
        UUID second = extraVariant(f, "41", 900_000, 3);
        var demand = List.of(line(f.variant(), 1), line(second, 2));
        var quote = cartPricing.quote(f.customerA(), demand);
        org.slf4j.MDC.put(com.shoecommerce.platform.api.CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-audit"))
                    .isInstanceOf(DataAccessException.class);
        } finally { org.slf4j.MDC.remove(com.shoecommerce.platform.api.CorrelationIdFilter.MDC_KEY); }
        assertNoCartEffects(f);
        assertThat(redemptions(promotion)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_adjustment a JOIN commerce_order o ON o.public_id=a.order_public_id WHERE o.owner_account_public_id=?", Integer.class,f.customerA().publicId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_item_adjustment a JOIN commerce_order_item i ON i.public_id=a.order_item_public_id JOIN commerce_order o ON o.id=i.order_id WHERE o.owner_account_public_id=?", Integer.class,f.customerA().publicId())).isZero();
        assertThat(balance(f).reserved()).isZero();
        assertThat(reserved(second)).isZero();
        var retryQuote = cartPricing.quote(f.customerA(), demand);
        assertThat(orders.checkoutCart(f.customerA(), retryQuote.id(), demand, "multi-audit-retry").id()).isNotNull();
        assertThat(redemptions(promotion)).isOne();
    }

    @Test
    void cartQuoteExpiryAndChangedDemandNeverReserve() {
        Fixture f = fixture("multi-expired", 3);
        UUID second = extraVariant(f, "41", 900_000, 3);
        var demand = List.of(line(f.variant(), 1), line(second, 1));
        var quote = cartPricing.quote(f.customerA(), demand);
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), List.of(line(f.variant(), 2), line(second, 1)), "mismatch"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("cart changed");
        assertThatThrownBy(() -> orders.checkoutCart(f.customerB(), quote.id(), demand, "foreign-cart"))
                .isInstanceOf(ResourceNotFoundException.class);
        clock.set(quote.expiresAt());
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand, "expired-cart"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("expired");
        assertNoCartEffects(f);
    }

    @Test
    void cartKeyCanonicalizesAllLinesAndReplaysAfterQuoteExpiry() {
        Fixture f = fixture("multi-replay", 5);
        UUID promotion = promotion(f, 1L, null, 100);
        UUID second = extraVariant(f, "41", 900_000, 5);
        var demand = List.of(line(f.variant(), 1), line(second, 2));
        var quote = cartPricing.quote(f.customerA(), demand);
        var order = orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-replay");
        var reversed = List.of(line(second, 1), line(f.variant(), 1), line(second, 1));
        assertThat(orders.checkoutCart(f.customerA(), quote.id(), reversed, "multi-replay").id()).isEqualTo(order.id());
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), List.of(line(f.variant(), 1), line(second, 1)), "multi-replay"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("idempotency key");
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand, "new-key"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("fresh quote");
        clock.set(quote.expiresAt());
        assertThat(orders.checkoutCart(f.customerA(), quote.id(), reversed, "multi-replay").id()).isEqualTo(order.id());
        assertThat(orders.readOwnOrders(f.customerA(), 0, 20).items()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id = ?", Integer.class, f.customerA().publicId())).isEqualTo(2);
        assertThat(redemptions(promotion)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_item_adjustment WHERE promotion_revision_public_id=?",Integer.class,promotion)).isEqualTo(2);
    }

    @Test
    void anyExpiredLineReleasesAllHoldsAndUnpaidCancellationIsIdempotent() {
        Fixture f = fixture("multi-holds", 5);
        UUID promotion = promotion(f, null, 1L, 100);
        UUID second = extraVariant(f, "41", 900_000, 5);
        var demand = List.of(line(f.variant(), 1), line(second, 2));
        var quote = cartPricing.quote(f.customerA(), demand);
        clock.set(quote.expiresAt().minusSeconds(1));
        var order = orders.checkoutCart(f.customerA(), quote.id(), demand, "multi-holds");
        assertThat(order.reservationExpiresAt()).isAfter(quote.expiresAt());
        clock.set(order.reservationExpiresAt());
        storefront.detail(f.product());
        assertThat(orders.readOwn(f.customerA(), order.id()).status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id = ? AND status = 'EXPIRED'", Integer.class, f.customerA().publicId())).isEqualTo(2);
        assertThat(balance(f).reserved()).isZero(); assertThat(reserved(second)).isZero();
        assertThat(status(promotion, order.id())).isEqualTo("RELEASED");
        var fresh = cartPricing.quote(f.customerA(), demand);
        var next = orders.checkoutCart(f.customerA(), fresh.id(), demand, "multi-cancel");
        orders.cancelOwn(f.customerA(), next.id()); orders.cancelOwn(f.customerA(), next.id());
        assertThat(balance(f).reserved()).isZero(); assertThat(reserved(second)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id = ? AND status = 'RELEASED'", Integer.class, f.customerA().publicId())).isEqualTo(2);
        assertThat(status(promotion, next.id())).isEqualTo("RELEASED");
    }

    @Test
    void quoteDoesNotConsumeUsageAndUnlimitedPromotionNeverCreatesRedemption() {
        Fixture f=fixture("usage-quote",3); UUID limited=promotion(f,1L,null,200);
        cartPricing.quote(f.customerA(),List.of(line(f.variant(),1)));
        assertThat(redemptions(limited)).isZero();
        jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE public_id=?",Timestamp.from(clock.instant()),limited);
        UUID unlimited=promotion(f,null,null,100);
        var quote=cartPricing.quote(f.customerA(),List.of(line(f.variant(),1)));
        var order=orders.checkoutCart(f.customerA(),quote.id(),List.of(line(f.variant(),1)),"unlimited");
        assertThat(redemptions(unlimited)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_item_adjustment WHERE order_item_public_id IN (SELECT public_id FROM commerce_order_item WHERE order_id=(SELECT id FROM commerce_order WHERE public_id=?))",Integer.class,order.id())).isOne();
    }

    @Test
    void globalLastUseAllowsExactlyOneOverlappingCheckoutWithoutLoserEffects() throws Exception {
        Fixture f=fixture("global-last",2); UUID promotion=promotion(f,1L,null,100);
        var demand=List.of(line(f.variant(),1)); var qa=cartPricing.quote(f.customerA(),demand); var qb=cartPricing.quote(f.customerB(),demand);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        List<Object> outcomes;
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->cartAfterBarrier(f.customerA(),qa.id(),demand,"global-a",ready,start));
            var b=executor.submit(()->cartAfterBarrier(f.customerB(),qb.id(),demand,"global-b",ready,start));
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            outcomes=List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        } finally {start.countDown();}
        assertThat(outcomes).filteredOn(CustomerOrderService.OrderView.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessConflictException.class::isInstance).hasSize(1);
        assertThat(redemptions(promotion)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE owner_account_public_id IN (?,?)",Integer.class,f.customerA().publicId(),f.customerB().publicId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id IN (?,?)",Integer.class,f.customerA().publicId(),f.customerB().publicId())).isOne();
        assertThat(balance(f).reserved()).isOne();
    }

    @Test
    void perCustomerLastUseAllowsOneOfTwoDistinctConcurrentCheckouts() throws Exception {
        Fixture f=fixture("customer-last",2); UUID promotion=promotion(f,null,1L,100);
        var demand=List.of(line(f.variant(),1)); var qa=cartPricing.quote(f.customerA(),demand); var qb=cartPricing.quote(f.customerA(),demand);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        List<Object> outcomes;
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->cartAfterBarrier(f.customerA(),qa.id(),demand,"customer-a",ready,start));
            var b=executor.submit(()->cartAfterBarrier(f.customerA(),qb.id(),demand,"customer-b",ready,start));
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            outcomes=List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        } finally {start.countDown();}
        assertThat(outcomes).filteredOn(CustomerOrderService.OrderView.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessConflictException.class::isInstance).hasSize(1);
        assertThat(redemptions(promotion)).isOne();
    }

    @Test
    void acceptedQuoteDetectsEveryFinancialPromotionChangeButAllowsRetirement() {
        Fixture f=fixture("stale",8); var demand=List.of(line(f.variant(),1));
        UUID expired=promotion(f,null,null,100); var q1=cartPricing.quote(f.customerA(),demand); jdbc.update("UPDATE promotion SET valid_to=? WHERE public_id=?",Timestamp.from(clock.instant().plusSeconds(1)),expired);clock.advance(Duration.ofSeconds(2)); stale(f,q1.id(),demand,"expired");
        clock.set(TEST_NOW); retire(expired); UUID invalid=promotion(f,null,null,100);var q2=cartPricing.quote(f.customerA(),demand);jdbc.update("UPDATE promotion SET status='INVALIDATED',invalidated_at=? WHERE public_id=?",Timestamp.from(clock.instant()),invalid);stale(f,q2.id(),demand,"invalid");
        UUID exhausted=promotion(f,1L,null,200);var q3=cartPricing.quote(f.customerA(),demand);var qOther=cartPricing.quote(f.customerB(),demand);orders.checkoutCart(f.customerB(),qOther.id(),demand,"exhaust");stale(f,q3.id(),demand,"exhausted");
        retire(exhausted);UUID low=promotion(f,null,null,10);var q4=cartPricing.quote(f.customerA(),demand);promotion(f,null,null,20);stale(f,q4.id(),demand,"winner");
        jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE status='PUBLISHED'",Timestamp.from(clock.instant()));var q5=cartPricing.quote(f.customerA(),demand);promotion(f,null,null,30);stale(f,q5.id(),demand,"new");
        jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE status='PUBLISHED'",Timestamp.from(clock.instant()));UUID retired=promotion(f,null,null,40);var q6=cartPricing.quote(f.customerA(),demand);retire(retired);assertThat(orders.checkoutCart(f.customerA(),q6.id(),demand,"retired").id()).isNotNull();
    }

    @Test
    void oppositeCartOrderingBothSucceedWithSufficientStock() throws Exception { cartRace(2, 2); }

    @Test
    void oppositeCartOrderingHasExactlyOneWinnerForLastUnits() throws Exception { cartRace(1, 1); }

    private void cartRace(long stock, int expectedWinners) throws Exception {
        Fixture f = fixture("multi-race-" + stock, stock);
        UUID second = extraVariant(f, "41", 900_000, stock);
        var a = List.of(line(f.variant(), 1), line(second, 1));
        var b = List.of(line(second, 1), line(f.variant(), 1));
        var qa = cartPricing.quote(f.customerA(), a); var qb = cartPricing.quote(f.customerB(), b);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> cartAfterBarrier(f.customerA(), qa.id(), a, ready, start));
            var other = executor.submit(() -> cartAfterBarrier(f.customerB(), qb.id(), b, ready, start));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            results = List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS), other.get(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally { start.countDown(); }
        assertThat(results).filteredOn(CustomerOrderService.OrderView.class::isInstance).hasSize(expectedWinners);
        assertThat(results).filteredOn(BusinessConflictException.class::isInstance).hasSize(2 - expectedWinners);
        assertThat(balance(f).reserved()).isEqualTo(expectedWinners);
        assertThat(reserved(second)).isEqualTo(expectedWinners);
    }

    private Object cartAfterBarrier(SessionPrincipal actor, UUID quote, List<CartQuoteService.LineRequest> demand,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        return cartAfterBarrier(actor,quote,demand,"multi-race",ready,start);
    }
    private Object cartAfterBarrier(SessionPrincipal actor, UUID quote, List<CartQuoteService.LineRequest> demand,String key,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
        try { return orders.checkoutCart(actor, quote, demand, key); }
        catch (BusinessConflictException conflict) { return conflict; }
    }

    @Test
    void cartHttpContractUsesOneOrderAndEnforcesBackendOwnership() throws Exception {
        Fixture f = fixture("multi-api", 3); UUID second = extraVariant(f, "41", 900_000, 3);
        Browser a = new Browser(), b = new Browser(); login(a, f.customerALogin()); login(b, f.customerBLogin());
        String items = "[{\"variantId\":\"" + f.variant() + "\",\"quantity\":1},{\"variantId\":\"" + second + "\",\"quantity\":2}]";
        var quoted = request(a, "/api/v1/storefront/cart-quotes", "{\"items\":" + items + "}", null);
        assertThat(quoted.statusCode()).isEqualTo(201);
        JsonNode quote = json.readTree(quoted.body());
        String quoteId = quote.get("id").asString();
        String locationId = quote.get("pickupLocations").get(0).get("id").asString();
        var created = request(a, "/api/v1/orders/cart-checkout", "{\"quoteId\":\"" + quoteId + "\",\"items\":" + items
                + ",\"fulfillment\":{\"type\":\"PICKUP\",\"pickupLocationId\":\"" + locationId + "\"}}", "cart-api");
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode order = json.readTree(created.body()); assertThat(order.get("items").size()).isEqualTo(2);
        assertThat(order.get("totalAmount").asLong()).isEqualTo(1_949_000);
        assertThat(order.get("fulfillmentType").asString()).isEqualTo("PICKUP");
        assertThat(order.get("fulfillmentStatus").asString()).isEqualTo("PENDING");
        assertThat(b.client.send(HttpRequest.newBuilder(uri("/api/v1/orders/" + order.get("id").asString())).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        var history = b.client.send(HttpRequest.newBuilder(uri("/api/v1/orders")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(history.statusCode()).isEqualTo(200); assertThat(json.readTree(history.body()).get("items").size()).isZero();
    }

    @Test
    void cartCheckoutSnapshotsDeliveryAndIncludesIntentInIdempotency() {
        Fixture f = fixture("delivery-intent", 3);
        var demand = List.of(line(f.variant(), 1));
        UUID rule=UUID.randomUUID();
        jdbc.update("INSERT INTO shipping_rate_rule(public_id,family_public_id,revision_number,status,origin_scope,destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES (?,?,1,'PUBLISHED','GLOBAL','79','760','INTER_PROVINCE',80000,1,?,?,?,?)",
                rule,UUID.randomUUID(),java.sql.Timestamp.from(clock.instant().minusSeconds(1)),f.customerA().publicId(),java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));
        var quote = cartPricing.quote(f.customerA(), demand, new CartQuoteService.FulfillmentQuote("DELIVERY","79","760"));
        var delivery = new CustomerOrderService.FulfillmentRequest(PickupFulfillment.Type.DELIVERY, null,
                new CustomerOrderService.DeliveryRequest("Nguyen Van A", "+84 912 345 678", "79", "760", "12 Nguyen Hue, Quan 1", "Giao gio hanh chinh"));

        var created = orders.checkoutCart(f.customerA(), quote.id(), demand, delivery, "delivery-intent");
        var replay = orders.checkoutCart(f.customerA(), quote.id(), demand, delivery, "delivery-intent");

        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(created.fulfillmentType()).isEqualTo("DELIVERY");
        assertThat(created.fulfillmentStatus()).isEqualTo("PENDING");
        assertThat(created.receiverName()).isEqualTo("Nguyen Van A");
        assertThat(created.receiverPhone()).isEqualTo("+84 912 345 678");
        assertThat(created.deliveryAddress()).isEqualTo("12 Nguyen Hue, Quan 1");
        assertThat(quote.merchandiseAmount()).isEqualTo(quote.items().getFirst().totalAmount());
        assertThat(quote.shippingFeeAmount()).isEqualTo(80_000);
        assertThat(created.merchandiseAmount()).isEqualTo(quote.merchandiseAmount());
        assertThat(created.deliveryFeeAmount()).isEqualTo(quote.shippingFeeAmount());
        assertThat(created.totalAmount()).isEqualTo(quote.totalAmount());
        assertThat(created.locationId()).isEqualTo(quote.originLocationId());
        assertThat(created.deliveryNote()).isEqualTo("Giao gio hanh chinh");
        assertThatThrownBy(() -> orders.checkoutCart(f.customerA(), quote.id(), demand,
                new CustomerOrderService.FulfillmentRequest(PickupFulfillment.Type.PICKUP, location(f), null), "delivery-intent"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("idempotency key");
    }

    @Test
    void deliveryCheckoutNeverSubstitutesAnotherLocationAfterQuote() {
        Fixture f=fixture("delivery-origin",3); UUID first=location(f);
        UUID branch=jdbc.queryForObject("SELECT branches.public_id FROM org_location locations JOIN org_branch branches ON branches.id=locations.branch_id WHERE locations.public_id=?",UUID.class,first);
        SessionPrincipal admin=bootstrapAdmin(); UUID second=scopes.createLocation(admin,branch,"SECOND-"+shortId(),"Second delivery stock");
        scopes.setAssignment(admin,f.operations().publicId(),branch,second,true);
        SessionPrincipal operations=(SessionPrincipal)users.loadUserByUsername(f.operations().getUsername());
        adjustments.adjust(operations,f.variant(),second,3,"Second origin fixture",UUID.randomUUID().toString());
        UUID rule=UUID.randomUUID(); jdbc.update("INSERT INTO shipping_rate_rule(public_id,family_public_id,revision_number,status,origin_scope,destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES (?,?,1,'PUBLISHED','GLOBAL','79','760','INTER_PROVINCE',80000,1,?,?,?,?)",
                rule,UUID.randomUUID(),Timestamp.from(clock.instant().minusSeconds(1)),f.customerA().publicId(),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
        var demand=List.of(line(f.variant(),1)); var quote=cartPricing.quote(f.customerA(),demand,new CartQuoteService.FulfillmentQuote("DELIVERY","79","760"));
        assertThat(quote.originLocationId()).isEqualTo(first);
        adjustments.adjust(operations,f.variant(),first,0,"Quoted origin unavailable",UUID.randomUUID().toString());
        var delivery=new CustomerOrderService.FulfillmentRequest(PickupFulfillment.Type.DELIVERY,null,
                new CustomerOrderService.DeliveryRequest("Nguyen Van A","+84 912 345 678","79","760","12 Nguyen Hue",null));
        assertThatThrownBy(()->orders.checkoutCart(f.customerA(),quote.id(),demand,delivery,"origin-no-switch"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("fresh quote");
        assertThat(jdbc.queryForObject("SELECT on_hand-reserved FROM inventory_balance WHERE variant_id=(SELECT id FROM catalog_product_variant WHERE public_id=?) AND location_id=(SELECT id FROM org_location WHERE public_id=?)",Long.class,f.variant(),second)).isEqualTo(3);
    }

    @Test
    void promotionFinancialsReconcileFromDeliveryQuoteThroughOrderAndPaymentAttempt() {
        Fixture f=fixture("promo-financial",3);promotion(f,"ITEM","ITEM_FIXED",10_000L,null,300);promotion(f,"ORDER_AUTOMATIC","ORDER_FIXED",20_000L,null,200);promotion(f,"SHIPPING","FREE_SHIPPING",null,null,100);
        UUID rule=UUID.randomUUID();jdbc.update("INSERT INTO shipping_rate_rule(public_id,family_public_id,revision_number,status,origin_scope,destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES (?,?,1,'PUBLISHED','GLOBAL','79','760','INTER_PROVINCE',80000,1,?,?,?,?)",rule,UUID.randomUUID(),Timestamp.from(clock.instant().minusSeconds(1)),f.customerA().publicId(),Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
        var demand=List.of(line(f.variant(),1));var quote=cartPricing.quote(f.customerA(),demand,new CartQuoteService.FulfillmentQuote("DELIVERY","79","760"));
        var delivery=new CustomerOrderService.FulfillmentRequest(PickupFulfillment.Type.DELIVERY,null,new CustomerOrderService.DeliveryRequest("Nguyen Van A","+84 912 345 678","79","760","12 Nguyen Hue",null));
        var order=orders.checkoutCart(f.customerA(),quote.id(),demand,delivery,"promo-financial");var attempt=payments.initiate(f.customerA(),order.id(),"pay-promo-financial").attempt();
        assertThat(quote.merchandiseAmount()).isEqualTo(149_000);assertThat(quote.merchandiseDiscountAmount()).isEqualTo(30_000);assertThat(quote.shippingFeeAmount()).isEqualTo(80_000);assertThat(quote.shippingDiscountAmount()).isEqualTo(80_000);assertThat(quote.totalAmount()).isEqualTo(119_000);
        assertThat(order.merchandiseAmount()).isEqualTo(quote.merchandiseAmount());assertThat(order.merchandiseDiscountAmount()).isEqualTo(quote.merchandiseDiscountAmount());assertThat(order.deliveryFeeAmount()).isEqualTo(quote.shippingFeeAmount());assertThat(order.shippingDiscountAmount()).isEqualTo(quote.shippingDiscountAmount());assertThat(order.totalAmount()).isEqualTo(quote.totalAmount());assertThat(attempt.amount()).isEqualByComparingTo("119000");
        assertThat(jdbc.queryForObject("SELECT SUM(applied_amount) FROM commerce_order_item_adjustment WHERE layer='ORDER_ALLOCATION' AND order_item_public_id IN (SELECT public_id FROM commerce_order_item WHERE order_id=(SELECT id FROM commerce_order WHERE public_id=?))",Long.class,order.id())).isEqualTo(20_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_adjustment WHERE order_public_id=?",Integer.class,order.id())).isEqualTo(2);
    }

    @Test
    void selectedCodeSnapshotsAllocationsOrdersPaymentAndIdempotentReplayWithoutRawCode() {
        Fixture f=fixture("voucher-code",3);UUID second=extraVariant(f,"41",101_000,3);Voucher v=voucher(f,"CODE","SAVE-C3-SECRET",1L,25_000L);
        var demand=List.of(line(f.variant(),1),line(second,1));var selection=PromotionService.VoucherSelection.code("  save-c3-secret  ");
        var quote=cartPricing.quote(f.customerA(),demand,new CartQuoteService.FulfillmentQuote("PICKUP",null,null),selection);
        assertThat(quote.merchandiseDiscountAmount()).isEqualTo(25_000);
        assertThat(jdbc.queryForObject("SELECT SUM(applied_amount) FROM pricing_cart_quote_item_adjustment WHERE quote_public_id=? AND layer='VOUCHER_ALLOCATION'",Long.class,quote.id())).isEqualTo(25_000);
        assertThat(jdbc.queryForObject("SELECT masked_code_snapshot FROM pricing_cart_quote_adjustment WHERE quote_public_id=? AND acquisition_mode='CODE'",String.class,quote.id())).doesNotContain("SAVE-C3-SECRET");
        var order=orders.checkoutCart(f.customerA(),quote.id(),demand,"voucher-replay");var replay=orders.checkoutCart(f.customerA(),quote.id(),demand,"voucher-replay");
        assertThat(replay.id()).isEqualTo(order.id());assertThat(redemptions(v.revision())).isOne();
        assertThat(jdbc.queryForObject("SELECT SUM(applied_amount) FROM commerce_order_item_adjustment WHERE layer='VOUCHER_ALLOCATION' AND order_item_public_id IN (SELECT public_id FROM commerce_order_item WHERE order_id=(SELECT id FROM commerce_order WHERE public_id=?))",Long.class,order.id())).isEqualTo(25_000);
        assertThat(payments.initiate(f.customerA(),order.id(),"voucher-pay").attempt().amount()).isEqualByComparingTo(Long.toString(quote.totalAmount()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_adjustment WHERE order_public_id=? AND masked_code_snapshot='SAVE-C3-SECRET'",Integer.class,order.id())).isZero();
    }

    @Test
    void acceptedClaimUsesExactRetiredRevisionAndAlwaysReservesUsage() {
        Fixture f=fixture("voucher-claim",2);Voucher first=voucher(f,"CLAIMABLE",null,null,20_000L);UUID claim=claim(first.family(),first.revision(),f.customerA());
        var demand=List.of(line(f.variant(),1));var quote=cartPricing.quote(f.customerA(),demand,null,PromotionService.VoucherSelection.claim(claim));
        retire(first.revision());voucherRevision(f,first.family(),2,30_000L);
        var order=orders.checkoutCart(f.customerA(),quote.id(),demand,"claim-retired");
        assertThat(order.totalAmount()).isEqualTo(quote.totalAmount());assertThat(redemptions(first.revision())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order_adjustment WHERE order_public_id=? AND promotion_revision_public_id=? AND voucher_claim_public_id=?",Integer.class,order.id(),first.revision(),claim)).isOne();
    }

    @Test
    void selectedVoucherStalesWithoutOrderReservationOrInventoryEffects() {
        Fixture f=fixture("voucher-stale",2);Voucher v=voucher(f,"CODE","STALE-C3",1L,10_000L);var demand=List.of(line(f.variant(),1));
        var quote=cartPricing.quote(f.customerA(),demand,null,PromotionService.VoucherSelection.code("STALE-C3"));
        jdbc.update("UPDATE promotion SET status='INVALIDATED',invalidated_at=? WHERE public_id=?",Timestamp.from(clock.instant()),v.revision());
        assertThatThrownBy(()->orders.checkoutCart(f.customerA(),quote.id(),demand,"voucher-stale")).isInstanceOf(BusinessConflictException.class).extracting(e->((BusinessConflictException)e).code()).isEqualTo("VOUCHER_STALE");
        assertNoCartEffects(f);assertThat(redemptions(v.revision())).isZero();
    }

    @Test
    void sameCheckoutKeyRejectsDifferentAcceptedVoucherEvidence() {
        Fixture f=fixture("voucher-key",3);Voucher v=voucher(f,"CODE","KEY-C3",null,10_000L);var demand=List.of(line(f.variant(),1));
        var none=cartPricing.quote(f.customerA(),demand);var code=cartPricing.quote(f.customerA(),demand,null,PromotionService.VoucherSelection.code("KEY-C3"));
        orders.checkoutCart(f.customerA(),none.id(),demand,"same-voucher-key");
        assertThatThrownBy(()->orders.checkoutCart(f.customerA(),code.id(),demand,"same-voucher-key")).isInstanceOf(BusinessConflictException.class).extracting(e->((BusinessConflictException)e).code()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(redemptions(v.revision())).isZero();
    }

    @Test
    void selectedVoucherRacesReserveExactlyOneWithoutLoserEffects() throws Exception {
        Fixture codeFixture=fixture("voucher-code-race",2);Voucher code=voucher(codeFixture,"CODE","RACE-C3",1L,10_000L);var demand=List.of(line(codeFixture.variant(),1));
        var codeA=cartPricing.quote(codeFixture.customerA(),demand,null,PromotionService.VoucherSelection.code("RACE-C3"));var codeB=cartPricing.quote(codeFixture.customerB(),demand,null,PromotionService.VoucherSelection.code("RACE-C3"));
        assertVoucherRace(codeFixture,List.of(codeFixture.customerA(),codeFixture.customerB()),List.of(codeA.id(),codeB.id()),demand,code.revision());

        Fixture claimFixture=fixture("voucher-claim-race",2);Voucher claimed=voucher(claimFixture,"CLAIMABLE",null,null,10_000L);UUID claim=claim(claimed.family(),claimed.revision(),claimFixture.customerA());
        var claimDemand=List.of(line(claimFixture.variant(),1));var claimA=cartPricing.quote(claimFixture.customerA(),claimDemand,null,PromotionService.VoucherSelection.claim(claim));var claimB=cartPricing.quote(claimFixture.customerA(),claimDemand,null,PromotionService.VoucherSelection.claim(claim));
        assertVoucherRace(claimFixture,List.of(claimFixture.customerA(),claimFixture.customerA()),List.of(claimA.id(),claimB.id()),claimDemand,claimed.revision());
    }

    private void assertVoucherRace(Fixture f,List<SessionPrincipal> actors,List<UUID> quoteIds,List<CartQuoteService.LineRequest> demand,UUID revision)throws Exception{
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);List<Object> outcomes;
        try(var executor=Executors.newFixedThreadPool(2)){var a=executor.submit(()->cartAfterBarrier(actors.get(0),quoteIds.get(0),demand,"voucher-race-a",ready,start));var b=executor.submit(()->cartAfterBarrier(actors.get(1),quoteIds.get(1),demand,"voucher-race-b",ready,start));assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();outcomes=List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));}finally{start.countDown();}
        assertThat(outcomes).filteredOn(CustomerOrderService.OrderView.class::isInstance).hasSize(1);assertThat(outcomes).filteredOn(BusinessConflictException.class::isInstance).hasSize(1);assertThat(redemptions(revision)).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id IN (?,?)",Integer.class,actors.get(0).publicId(),actors.get(1).publicId())).isOne();
    }

    private static CartQuoteService.LineRequest line(UUID id, long quantity) { return new CartQuoteService.LineRequest(id, quantity); }
    private UUID extraVariant(Fixture f, String size, long price, long stock) {
        UUID id = catalog.createVariant(f.operations(), f.product(), "CART-" + shortId(), size, "Black");
        catalog.setPrice(f.operations(), id, price);
        adjustments.adjust(f.operations(), id, location(f), stock, "Multi-item fixture", UUID.randomUUID().toString());
        catalog.publish(f.operations(), id); return id;
    }
    private UUID location(Fixture f) { return jdbc.queryForObject("SELECT locations.public_id FROM org_location locations JOIN inventory_balance balances ON balances.location_id = locations.id JOIN catalog_product_variant variants ON variants.id = balances.variant_id WHERE variants.public_id = ?", UUID.class, f.variant()); }
    private long reserved(UUID variant) { return jdbc.queryForObject("SELECT SUM(balances.reserved) FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id WHERE variants.public_id = ?", Long.class, variant); }
    private UUID promotion(Fixture f,Long global,Long customer,int priority){
        UUID id=UUID.randomUUID(),family=UUID.randomUUID(); Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,'AUTOMATIC',NULL,0,?)",family,now);
        jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,global_usage_limit,per_customer_usage_limit,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES(?,?,1,?,'PUBLISHED','ITEM','ITEM_FIXED',10000,?,?,?,?,?,?,?)",family,id,"C2 "+id,global,customer,priority,Timestamp.from(clock.instant().minusSeconds(60)),f.customerA().publicId(),now,now);
        jdbc.update("INSERT INTO promotion_product_scope(promotion_id,product_public_id) SELECT id,? FROM promotion WHERE public_id=?",f.product(),id);return id;
    }
    private UUID promotion(Fixture f,String layer,String type,Long fixed,Long global,int priority){UUID id=UUID.randomUUID(),family=UUID.randomUUID();Timestamp now=Timestamp.from(clock.instant());jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,'AUTOMATIC',NULL,0,?)",family,now);jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,global_usage_limit,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES(?,?,1,?,'PUBLISHED',?,?,?,?,?,?,?, ?,?)",family,id,"C2 "+type,layer,type,fixed,global,priority,Timestamp.from(clock.instant().minusSeconds(60)),f.customerA().publicId(),now,now);if("ITEM".equals(layer))jdbc.update("INSERT INTO promotion_product_scope(promotion_id,product_public_id) SELECT id,? FROM promotion WHERE public_id=?",f.product(),id);return id;}
    private Voucher voucher(Fixture f,String mode,String code,Long global,long fixed){UUID family=UUID.randomUUID();Timestamp now=Timestamp.from(clock.instant());jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,?,?,?,?)",family,mode,code,false,now);return new Voucher(family,voucherRevision(f,family,1,fixed,global));}
    private UUID voucherRevision(Fixture f,UUID family,int revision,long fixed){return voucherRevision(f,family,revision,fixed,null);}
    private UUID voucherRevision(Fixture f,UUID family,int revision,long fixed,Long global){UUID id=UUID.randomUUID();Timestamp now=Timestamp.from(clock.instant());jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,global_usage_limit,priority,valid_from,created_by_account_public_id,created_at,published_at,customer_summary,customer_terms) VALUES(?,?,?,?,'PUBLISHED','ORDER_AUTOMATIC','ORDER_FIXED',?, ?,100,?,?,?,?,?,?)",family,id,revision,"Voucher "+revision,fixed,global,Timestamp.from(clock.instant().minusSeconds(60)),f.customerA().publicId(),now,now,"Save now","Terms");return id;}
    private UUID claim(UUID family,UUID revision,SessionPrincipal customer){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO voucher_claim(public_id,promotion_family_public_id,claimed_revision_public_id,customer_account_public_id,status,claimed_at) VALUES(?,?,?,?,'CLAIMED',?)",id,family,revision,customer.publicId(),Timestamp.from(clock.instant()));return id;}
    private record Voucher(UUID family,UUID revision){}
    private int redemptions(UUID promotion){return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_redemption WHERE promotion_revision_public_id=?",Integer.class,promotion);}
    private String status(UUID promotion,UUID order){return jdbc.queryForObject("SELECT status FROM promotion_redemption WHERE promotion_revision_public_id=? AND order_public_id=?",String.class,promotion,order);}
    private void retire(UUID promotion){jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE public_id=? AND status='PUBLISHED'",Timestamp.from(clock.instant()),promotion);}
    private void stale(Fixture f,UUID quote,List<CartQuoteService.LineRequest> demand,String key){assertThatThrownBy(()->orders.checkoutCart(f.customerA(),quote,demand,"stale-"+key)).isInstanceOf(BusinessConflictException.class).hasMessageContaining("offer");}
    private void assertNoCartEffects(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE owner_account_public_id = ?", Integer.class, f.customerA().publicId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE owner_account_public_id = ?", Integer.class, f.customerA().publicId())).isZero();
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
        adjustments.adjust(operations, variant, location, stock, "Test fixture", UUID.randomUUID().toString());
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
