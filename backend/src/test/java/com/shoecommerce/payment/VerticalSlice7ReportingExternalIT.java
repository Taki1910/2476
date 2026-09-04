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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.fulfillment.PickupCancellationService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.pos.PosService;
import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.CartQuoteService;
import com.shoecommerce.reporting.ReportingService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(VerticalSlice7ReportingExternalIT.TestClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice7ReportingExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    private static final Instant TEST_NOW = Instant.parse("2026-08-28T04:00:00Z");
    private static final LocalDate REPORT_FROM = LocalDate.parse("2026-08-28");
    private static final LocalDate REPORT_TO = LocalDate.parse("2026-08-29");

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired PriceQuoteService pricing;
    @Autowired CartQuoteService cartPricing;
    @Autowired CustomerOrderService orders;
    @Autowired PaymentAttemptService attempts;
    @Autowired VerifiedPaymentResultService paymentResults;
    @Autowired PickupCancellationService cancellations;
    @Autowired CheckoutHoldExpiryService expiry;
    @Autowired TestVoidProvider voidProvider;
    @Autowired PosService pos;
    @Autowired ReportingService reports;
    @Autowired MutableClock clock;
    @LocalServerPort int port;

    @BeforeEach
    void reset() {
        clock.set(TEST_NOW);
        voidProvider.next(VoidProvider.Outcome.SUCCEEDED);
    }

    @Test
    void coreMvpScenarioReconcilesOnlineVoidPosProductsAndInventory() {
        Fixture fixture = fixture("core", 2, 2);
        OnlineSale onlineA = onlineSale(fixture, fixture.variantA(), "online-a");
        OnlineSale onlineB = onlineSale(fixture, fixture.variantB(), "online-b");

        voidProvider.next(VoidProvider.Outcome.SUCCEEDED);
        cancellations.cancel(fixture.manager(), onlineB.orderId(), "void-b");
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        var posSale = pos.sell(fixture.cashier(), shiftId, fixture.variantB(), "pos-c").receipt();

        ReportingService.NetSalesReport net = reports.netSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(net.onlineGross()).isEqualTo("250000");
        assertThat(net.posGross()).isEqualTo("125000");
        assertThat(net.grossSales()).isEqualTo("375000");
        assertThat(net.successfulVoids()).isEqualTo("125000");
        assertThat(net.netSales()).isEqualTo("250000");
        assertThat(net.exceptionCount()).isZero();
        assertThat(net.context().businessTimezone()).isEqualTo("Asia/Ho_Chi_Minh");

        ReportingService.ProductSalesReport products = reports.productSales(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(products.grossSales()).isEqualTo("375000");
        assertThat(products.successfulVoids()).isEqualTo("125000");
        assertThat(products.netSales()).isEqualTo("250000").isEqualTo(net.netSales());
        assertThat(products.rows()).extracting(ReportingService.ProductSalesRow::sku)
                .containsExactlyInAnyOrder(fixture.skuA(), fixture.skuB());
        assertThat(products.rows().stream().filter(row -> row.sku().equals(fixture.skuB())).findFirst().orElseThrow())
                .extracting(ReportingService.ProductSalesRow::grossSales,
                        ReportingService.ProductSalesRow::successfulVoids,
                        ReportingService.ProductSalesRow::netSales)
                .containsExactly("250000", "125000", "125000");

        ReportingService.InventoryReport inventory = reports.inventory(fixture.manager(), fixture.locationId(), null);
        ReportingService.InventoryRow variantA = inventory.rows().stream()
                .filter(row -> row.variantId().equals(fixture.variantA())).findFirst().orElseThrow();
        ReportingService.InventoryRow variantB = inventory.rows().stream()
                .filter(row -> row.variantId().equals(fixture.variantB())).findFirst().orElseThrow();
        assertThat(variantA).extracting(ReportingService.InventoryRow::onHand,
                ReportingService.InventoryRow::reserved, ReportingService.InventoryRow::available)
                .containsExactly(2L, 1L, 1L);
        assertThat(variantB).extracting(ReportingService.InventoryRow::onHand,
                ReportingService.InventoryRow::reserved, ReportingService.InventoryRow::available)
                .containsExactly(1L, 0L, 1L);
        assertThat(inventory.movements()).extracting(ReportingService.MovementRow::type)
                .contains("CANCELLATION_RESTORE", "POS_CASH_SALE");
        assertThat(inventory.movements()).allMatch(row -> TEST_NOW.equals(row.occurredAt()));
        assertThat(inventory.reservations()).anyMatch(row -> row.variantId().equals(fixture.variantA())
                && "COMMITTED".equals(row.status()) && row.quantity() == 1);

        ReportingService.ReconciliationReport reconciliation = reports.reconciliation(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(reconciliation.entries()).filteredOn(entry -> "ONLINE_CAPTURE".equals(entry.category())).hasSize(2);
        assertThat(reconciliation.entries()).filteredOn(entry -> "POS_CASH".equals(entry.category())).hasSize(1);
        assertThat(reconciliation.entries()).filteredOn(entry -> "VOID".equals(entry.category())).hasSize(1);
        assertThat(reconciliation.entries()).noneMatch(ReportingService.ReconciliationEntry::exception);
        assertThat(reconciliation.entries()).allMatch(entry -> TEST_NOW.equals(entry.occurredAt()));
        assertThat(posSale.orderId()).isNotNull();
        assertThat(onlineA.orderId()).isNotNull();
    }

    @Test
    void unknownReleasedSuccessfulRetryAndPaymentReviewAreClassifiedWithoutFalseReversal() {
        Fixture fixture = fixture("exceptions", 5, 1);
        OnlineSale unknown = onlineSale(fixture, fixture.variantA(), "unknown-sale");
        voidProvider.next(VoidProvider.Outcome.UNKNOWN);
        cancellations.cancel(fixture.manager(), unknown.orderId(), "unknown-void");

        OnlineSale released = onlineSale(fixture, fixture.variantA(), "released-sale");
        voidProvider.next(VoidProvider.Outcome.DEFINITIVE_FAILED);
        cancellations.cancel(fixture.manager(), released.orderId(), "released-void");

        ReportingService.NetSalesReport beforeRetry = reports.netSales(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(beforeRetry.onlineGross()).isEqualTo("250000");
        assertThat(beforeRetry.successfulVoids()).isEqualTo("0");
        assertThat(beforeRetry.netSales()).isEqualTo("250000");
        ReportingService.ReconciliationReport beforeRetryEntries = reports.reconciliation(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(beforeRetryEntries.entries()).anyMatch(entry -> entry.exception() && "UNKNOWN".equals(entry.status()));
        assertThat(beforeRetryEntries.entries()).anyMatch(entry -> entry.exception() && "RELEASED".equals(entry.status()));

        voidProvider.next(VoidProvider.Outcome.SUCCEEDED);
        cancellations.retry(fixture.manager(), released.orderId(), "released-retry");
        ReportingService.NetSalesReport afterRetry = reports.netSales(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(afterRetry.successfulVoids()).isEqualTo("125000");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_void_allocation allocations
                JOIN payment_void_operation operations ON operations.id = allocations.void_operation_id
                WHERE operations.order_public_id = ? AND allocations.status = 'SUCCEEDED'
                """, Integer.class, released.orderId())).isOne();

        PendingSale review = pendingSale(fixture, fixture.variantA(), "review-sale");
        clock.advance(Duration.ofMinutes(16));
        expiry.expireForVariant(fixture.variantA());
        assertThat(paymentResults.apply(success(review, "99" + digits()))).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);

        ReportingService.NetSalesReport finalNet = reports.netSales(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(finalNet.onlineGross()).isEqualTo("250000");
        assertThat(finalNet.successfulVoids()).isEqualTo("125000");
        assertThat(finalNet.netSales()).isEqualTo("125000");
        ReportingService.ReconciliationReport exceptions = reports.reconciliation(
                fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(exceptions.entries()).anyMatch(entry -> entry.exception()
                && "PAYMENT_REVIEW".equals(entry.category()) && "REVIEW_REQUIRED".equals(entry.status())
                && entry.amount().equals("125000") && entry.netEffect().equals("0"));
        assertThat(exceptions.entries()).anyMatch(entry -> entry.exception() && "UNKNOWN".equals(entry.status()));
    }

    @Test
    void branchScopeTimeBoundariesHistoricalAttributionAndReadOnlyGetAreEnforced() throws Exception {
        Instant beforeFrom = Instant.parse("2026-08-27T16:59:00Z");
        clock.set(beforeFrom.minusSeconds(60));
        Fixture branchA = fixture("branch-a", 4, 1);
        Fixture branchB = fixture("branch-b", 1, 1);
        UUID shiftA = pos.openShift(branchA.cashier(), branchA.registerId()).id();
        UUID shiftB = pos.openShift(branchB.cashier(), branchB.registerId()).id();

        clock.set(Instant.parse("2026-08-27T16:59:59Z"));
        pos.sell(branchA.cashier(), shiftA, branchA.variantA(), "t0");
        clock.set(Instant.parse("2026-08-27T17:00:00Z"));
        pos.sell(branchA.cashier(), shiftA, branchA.variantA(), "t1");
        pos.sell(branchB.cashier(), shiftB, branchB.variantA(), "branch-b");
        clock.set(Instant.parse("2026-08-28T16:59:59Z"));
        pos.sell(branchA.cashier(), shiftA, branchA.variantA(), "t2");
        clock.set(Instant.parse("2026-08-28T17:00:00Z"));
        pos.sell(branchA.cashier(), shiftA, branchA.variantA(), "t3");

        scopes.setAssignment(branchA.admin(), branchA.cashierId(), branchA.branchId(), branchA.locationId(), false);
        ReportingService.NetSalesReport inRange = reports.netSales(
                branchA.manager(), REPORT_FROM, REPORT_TO, branchA.locationId());
        assertThat(inRange.posGross()).isEqualTo("250000");
        assertThat(inRange.context().from()).isEqualTo(Instant.parse("2026-08-27T17:00:00Z"));
        assertThat(inRange.context().to()).isEqualTo(Instant.parse("2026-08-28T17:00:00Z"));

        assertThatThrownBy(() -> reports.netSales(branchA.manager(), REPORT_FROM, REPORT_TO, branchB.locationId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reports.netSales(branchA.customer(), REPORT_FROM, REPORT_TO, branchA.locationId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reports.netSales(branchA.cashier(), REPORT_FROM, REPORT_TO, branchA.locationId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(reports.netSales(branchB.manager(), REPORT_FROM, REPORT_TO, branchB.locationId()).posGross())
                .isEqualTo("125000");

        Counts before = counts();
        reports.scope(branchA.manager());
        reports.productSales(branchA.manager(), REPORT_FROM, REPORT_TO, branchA.locationId());
        reports.inventory(branchA.manager(), branchA.locationId(), branchA.skuA());
        reports.reconciliation(branchA.manager(), REPORT_FROM, REPORT_TO, branchA.locationId());
        assertThat(counts()).isEqualTo(before);

        Browser manager = login(branchA.managerLogin());
        String query = "?fromDate=2026-08-28&toDate=2026-08-29&locationId=" + branchA.locationId();
        HttpResponse<String> allowed = manager.client.send(HttpRequest.newBuilder(
                uri("/api/v1/operations/reports/net-sales" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.body()).contains("\"posGross\":\"250000\"");

        Browser customer = login(branchA.customerLogin());
        HttpResponse<String> denied = customer.client.send(HttpRequest.newBuilder(
                uri("/api/v1/operations/reports/net-sales" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);

        ReportingService.NetSalesReport empty = reports.netSales(branchA.manager(),
                LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-02"), branchA.locationId());
        assertThat(empty.netSales()).isEqualTo("0");
    }

    @Test
    void multiItemCaptureAndFullVoidReconcileWithoutMultiplyingOrderTotals() {
        Fixture fixture = fixture("cart-core", 8, 8);
        // Repricing at the frozen fixture instant would become effective one microsecond later.
        clock.advance(Duration.ofSeconds(1));
        catalog.setPrice(fixture.manager(), fixture.variantA(), 1_490_000);
        catalog.setPrice(fixture.manager(), fixture.variantB(), 900_000);
        PendingSale sale = pendingCartSale(fixture, "cart-core", 1);
        clock.advance(Duration.ofSeconds(1));
        catalog.setPrice(fixture.manager(), fixture.variantA(), 1_590_000);
        paymentResults.apply(success(sale, digits()));

        var net = reports.netSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        var products = reports.productSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(net.onlineGross()).isEqualTo("2390000");
        assertThat(net.netSales()).isEqualTo(products.netSales()).isEqualTo("2390000");
        assertThat(products.rows()).filteredOn(row -> row.variantId().equals(fixture.variantA()))
                .extracting(ReportingService.ProductSalesRow::onlineGross).containsExactly("1490000");
        assertThat(products.rows()).filteredOn(row -> row.variantId().equals(fixture.variantB()))
                .extracting(ReportingService.ProductSalesRow::onlineGross).containsExactly("900000");
        var captured = reports.reconciliation(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(captured.entries()).filteredOn(row -> row.category().equals("ONLINE_CAPTURE")).hasSize(1);

        cancellations.cancel(fixture.manager(), sale.orderId(), "cart-core-void");
        cancellations.cancel(fixture.manager(), sale.orderId(), "cart-core-void");
        net = reports.netSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        products = reports.productSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(net.onlineGross()).isEqualTo("2390000");
        assertThat(net.successfulVoids()).isEqualTo("2390000");
        assertThat(net.netSales()).isEqualTo(products.netSales()).isEqualTo("0");
        assertThat(products.rows()).allMatch(row -> row.netSales().equals("0"));
        var reversed = reports.reconciliation(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(reversed.entries()).filteredOn(row -> row.category().equals("VOID")).hasSize(2);
        assertThat(reversed.entries().stream().map(row -> new BigDecimal(row.netEffect())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0");
    }

    @Test
    void multiItemExceptionsKeepOperationAndAllocationGranularityAndZeroReviewNetEffect() {
        Fixture fixture = fixture("cart-exceptions", 8, 12);
        for (var outcome : List.of(VoidProvider.Outcome.UNKNOWN, VoidProvider.Outcome.REVIEW_REQUIRED)) {
            PendingSale sale = pendingCartSale(fixture, "cart-" + outcome, 2);
            paymentResults.apply(success(sale, digits()));
            voidProvider.next(outcome);
            cancellations.cancel(fixture.manager(), sale.orderId(), "void-" + outcome);
        }
        PendingSale released = pendingCartSale(fixture, "cart-released", 2);
        paymentResults.apply(success(released, digits()));
        voidProvider.next(VoidProvider.Outcome.DEFINITIVE_FAILED);
        cancellations.cancel(fixture.manager(), released.orderId(), "void-released");
        PendingSale late = pendingCartSale(fixture, "cart-late", 2);
        clock.advance(Duration.ofMinutes(16));
        expiry.expireForVariant(fixture.variantB());
        paymentResults.apply(success(late, digits()));

        var net = reports.netSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(net.onlineGross()).isEqualTo("1125000");
        assertThat(net.successfulVoids()).isEqualTo("0");
        assertThat(net.exceptionCount()).isEqualTo(5);
        assertThat(net.exceptionAmount()).isEqualTo("1500000");
        var entries = reports.reconciliation(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(entries.entries()).filteredOn(row -> row.category().equals("ONLINE_CAPTURE")).hasSize(3);
        assertThat(entries.entries()).filteredOn(row -> row.status().equals("UNKNOWN")).hasSize(1);
        assertThat(entries.entries()).filteredOn(row -> row.category().equals("VOID_RECONCILIATION")
                && row.status().equals("REVIEW_REQUIRED")).hasSize(1);
        assertThat(entries.entries()).filteredOn(row -> row.status().equals("RELEASED")).hasSize(2);
        assertThat(entries.entries()).filteredOn(row -> row.category().equals("PAYMENT_REVIEW"))
                .singleElement().satisfies(row -> {
                    assertThat(row.amount()).isEqualTo("375000");
                    assertThat(row.netEffect()).isEqualTo("0");
                });
        assertThat(entries.entries()).filteredOn(ReportingService.ReconciliationEntry::exception)
                .allMatch(row -> row.netEffect().equals("0"));

        cancellations.retry(fixture.manager(), released.orderId(), "cart-successful-retry");
        cancellations.retry(fixture.manager(), released.orderId(), "cart-successful-retry");
        net = reports.netSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        var products = reports.productSales(fixture.manager(), REPORT_FROM, REPORT_TO, fixture.locationId());
        assertThat(net.successfulVoids()).isEqualTo("375000");
        assertThat(net.netSales()).isEqualTo(products.netSales()).isEqualTo("750000");
        assertThat(net.exceptionCount()).isEqualTo(5);
    }

    private PendingSale pendingCartSale(Fixture fixture, String key, long quantityB) {
        var lines = List.of(new CartQuoteService.LineRequest(fixture.variantA(), 1),
                new CartQuoteService.LineRequest(fixture.variantB(), quantityB));
        var quote = cartPricing.quote(fixture.customer(), lines);
        var order = orders.checkoutCart(fixture.customer(), quote.id(), lines, "checkout-" + key);
        var attempt = attempts.initiate(fixture.customer(), order.id(), "payment-" + key).attempt();
        return new PendingSale(order.id(), attempt.id(), attempt.merchantTransactionReference(), attempt.amount().longValueExact());
    }

    private OnlineSale onlineSale(Fixture fixture, UUID variantId, String key) {
        PendingSale pending = pendingSale(fixture, variantId, key);
        assertThat(paymentResults.apply(success(pending, digits())))
                .isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        return new OnlineSale(pending.orderId(), pending.attemptId());
    }

    private PendingSale pendingSale(Fixture fixture, UUID variantId, String key) {
        var quote = pricing.quote(fixture.customer(), variantId);
        var order = orders.checkout(fixture.customer(), quote.id(), "checkout-" + key);
        var attempt = attempts.initiate(fixture.customer(), order.id(), "payment-" + key).attempt();
        return new PendingSale(order.id(), attempt.id(), attempt.merchantTransactionReference(), attempt.amount().longValueExact());
    }

    private PaymentProvider.VerifiedResult success(PendingSale sale, String transactionNo) {
        return new PaymentProvider.VerifiedResult(sale.merchantReference(), sale.amount(), transactionNo,
                "00", "00", clock.instant(), "0".repeat(64));
    }

    private Fixture fixture(String suffix, long stockA, long stockB) {
        SessionPrincipal admin = bootstrapAdmin();
        String managerLogin = "v13-" + suffix + "-manager-" + UUID.randomUUID() + "@example.com";
        String cashierLogin = "v13-" + suffix + "-cashier-" + UUID.randomUUID() + "@example.com";
        String customerLogin = "v13-" + suffix + "-customer-" + UUID.randomUUID() + "@example.com";
        UUID managerId = identities.createAccount(admin, managerLogin, PASSWORD, RoleCode.OPERATIONS);
        UUID cashierId = identities.createAccount(admin, cashierLogin, PASSWORD, RoleCode.CASHIER);
        identities.createAccount(admin, customerLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branchId = scopes.createBranch(admin, "V13-" + suffix + "-" + shortId(), "Reporting branch " + suffix);
        UUID locationId = scopes.createLocation(admin, branchId, "LOC-" + shortId(), "Reporting floor " + suffix);
        scopes.setAssignment(admin, managerId, branchId, locationId, true);
        scopes.setAssignment(admin, cashierId, branchId, locationId, true);
        SessionPrincipal manager = principal(managerLogin);
        UUID productId = catalog.createProduct(manager, "Reporting runner " + suffix);
        String skuA = "V13-A-" + shortId();
        String skuB = "V13-B-" + shortId();
        UUID variantA = catalog.createVariant(manager, productId, skuA, "42", "Black");
        UUID variantB = catalog.createVariant(manager, productId, skuB, "43", "White");
        for (UUID variant : new UUID[] { variantA, variantB }) catalog.setPrice(manager, variant, 125_000);
        adjustments.adjust(manager, variantA, locationId, stockA, "Test fixture", UUID.randomUUID().toString());
        adjustments.adjust(manager, variantB, locationId, stockB, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(manager, variantA);
        catalog.publish(manager, variantB);
        UUID registerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pos_register(public_id, code, location_id, enabled, created_at)
                SELECT ?, ?, locations.id, 1, ? FROM org_location locations WHERE locations.public_id = ?
                """, registerId, "REG-" + shortId(), Timestamp.from(clock.instant()), locationId);
        return new Fixture(admin, manager, principal(cashierLogin), principal(customerLogin), cashierId,
                managerLogin, customerLogin, branchId, locationId, registerId, variantA, variantB, skuA, skuB);
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID();
        String login = "v13-admin-" + id + "@example.com";
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)",
                id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private Counts counts() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM commerce_order) AS orders,
                       (SELECT COUNT(*) FROM payment_attempt) AS attempts,
                       (SELECT COUNT(*) FROM payment_void_operation) AS voids,
                       (SELECT COUNT(*) FROM inventory_stock_movement) AS movements,
                       (SELECT COUNT(*) FROM inventory_balance) AS balances,
                       (SELECT COUNT(*) FROM cash_tender) AS tenders,
                       (SELECT COUNT(*) FROM audit_event) AS audits
                """, (rs, row) -> new Counts(rs.getLong("orders"), rs.getLong("attempts"), rs.getLong("voids"),
                        rs.getLong("movements"), rs.getLong("balances"), rs.getLong("tenders"), rs.getLong("audits")));
    }

    private Browser login(String username) throws Exception {
        Browser browser = new Browser();
        var csrfResponse = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String token = csrfResponse.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        String header = csrfResponse.body().replaceAll(".*\"headerName\":\"([^\"]+)\".*", "$1");
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header(header, token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return browser;
    }

    private SessionPrincipal principal(String login) {
        SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login);
        principal.eraseCredentials();
        return principal;
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
    private static String digits() { return Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()).substring(0, 10); }

    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private record PendingSale(UUID orderId, UUID attemptId, String merchantReference, long amount) { }
    private record OnlineSale(UUID orderId, UUID attemptId) { }
    private record Counts(long orders, long attempts, long voids, long movements, long balances, long tenders, long audits) { }
    private record Fixture(SessionPrincipal admin, SessionPrincipal manager, SessionPrincipal cashier,
            SessionPrincipal customer, UUID cashierId, String managerLogin, String customerLogin,
            UUID branchId, UUID locationId, UUID registerId, UUID variantA, UUID variantB,
            String skuA, String skuB) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(TEST_NOW); }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;
        MutableClock(Instant initial) { this(new AtomicReference<>(initial), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> current, ZoneId zone) { this.current = current; this.zone = zone; }
        void set(Instant instant) { current.set(instant); }
        void advance(Duration duration) { current.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone); }
        @Override public Instant instant() { return current.get(); }
    }
}
