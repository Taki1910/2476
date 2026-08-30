package com.shoecommerce.pos;

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
import java.util.concurrent.TimeUnit;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.pricing.PriceQuoteService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(VerticalSlice6PosExternalIT.TestClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice6PosExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    private static final Instant TEST_NOW = Instant.parse("2026-08-28T04:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired LocationRepository locations;
    @Autowired PosRegisterRepository registers;
    @Autowired PosService pos;
    @Autowired PriceQuoteService pricing;
    @Autowired CustomerOrderService orders;
    @Autowired TransactionTemplate transactions;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void resetClock() { clock.set(TEST_NOW); }

    @Test
    void normalSalePersistsSharedFactsAndExactReplayOnce() {
        Fixture fixture = fixture("normal", 1);
        var shift = pos.openShift(fixture.cashier(), fixture.registerId());
        var lookup = pos.lookup(fixture.cashier(), shift.id(), fixture.sku());
        assertThat(lookup.amount()).isEqualTo(125_000);
        assertThat(lookup.available()).isOne();

        var first = pos.sell(fixture.cashier(), shift.id(), fixture.variantId(), "sale-normal");
        var replay = pos.sell(fixture.cashier(), shift.id(), fixture.variantId(), "sale-normal");

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.receipt()).isEqualTo(first.receipt());
        assertThat(first.receipt()).extracting(PosService.ReceiptView::total,
                PosService.ReceiptView::tender, PosService.ReceiptView::fulfillmentStatus)
                .containsExactly(125_000L, "CASH", "HANDED_OVER");
        assertThat(balance(fixture)).isEqualTo(new Balance(0, 0, 0));
        assertCounts(fixture, 1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order WHERE public_id = ? AND channel = 'POS' AND status = 'PAID' AND owner_account_public_id IS NULL AND reservation_public_id IS NULL", Integer.class, first.receipt().orderId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pickup_fulfillment WHERE order_id = (SELECT id FROM commerce_order WHERE public_id = ?) AND channel = 'POS' AND status = 'HANDED_OVER'", Integer.class, first.receipt().orderId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE order_public_id = ? AND operation_type = 'POS_CASH_SALE' AND on_hand_delta = -1 AND reserved_delta = 0", Integer.class, first.receipt().orderId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_public_id = ? AND action = 'POS_CASH_SALE'", Integer.class, first.receipt().orderId())).isOne();

        var closed = pos.closeShift(fixture.cashier(), shift.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.expectedCash()).isEqualTo(125_000);
        assertThat(pos.closeShift(fixture.cashier(), shift.id()).expectedCash()).isEqualTo(125_000);
        assertThat(pos.sell(fixture.cashier(), shift.id(), fixture.variantId(), "sale-normal").receipt()).isEqualTo(first.receipt());
    }

    @Test
    void concurrentDuplicateSaleCreatesOnePhysicalEffectAndFingerprintConflictIsStable() throws Exception {
        Fixture fixture = fixture("duplicate", 1);
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<PosService.SaleResult> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> sellAfterBarrier(fixture, shiftId, "duplicate-key", ready, start));
            var b = executor.submit(() -> sellAfterBarrier(fixture, shiftId, "duplicate-key", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertThat(results).extracting(result -> result.receipt().orderId()).containsOnly(results.getFirst().receipt().orderId());
        assertThat(results).filteredOn(PosService.SaleResult::created).hasSize(1);
        assertCounts(fixture, 1, 1, 1);
        assertThat(balance(fixture)).isEqualTo(new Balance(0, 0, 0));

        Fixture other = addVariant(fixture, "other", 1);
        assertThatThrownBy(() -> pos.sell(fixture.cashier(), shiftId, other.variantId(), "duplicate-key"))
                .isInstanceOf(BusinessConflictException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void closedForeignShiftForeignRegisterMissingPermissionAndInactiveAssignmentCannotSell() {
        Fixture fixture = fixture("security", 2);
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        pos.closeShift(fixture.cashier(), shiftId);
        assertThatThrownBy(() -> pos.sell(fixture.cashier(), shiftId, fixture.variantId(), "closed"))
                .isInstanceOf(BusinessConflictException.class).extracting("code").isEqualTo("SHIFT_CLOSED");
        assertCounts(fixture, 0, 0, 0);
        assertThat(balance(fixture)).isEqualTo(new Balance(2, 0, 2));

        SessionPrincipal otherCashier = createCashier(fixture, "foreign-shift");
        PosRegister secondRegister = registers.save(PosRegister.create("REG-" + shortId(),
                locations.findByPublicId(fixture.locationId()).orElseThrow(), clock.instant()));
        UUID foreignShift = pos.openShift(otherCashier, secondRegister.publicId()).id();
        assertThatThrownBy(() -> pos.sell(fixture.cashier(), foreignShift, fixture.variantId(), "foreign"))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("SHIFT_NOT_FOUND");

        UUID foreignBranch = scopes.createBranch(fixture.admin(), "FOREIGN-" + shortId(), "Foreign branch");
        UUID foreignLocation = scopes.createLocation(fixture.admin(), foreignBranch, "FLOOR-" + shortId(), "Foreign floor");
        PosRegister foreignRegister = registers.save(PosRegister.create("REG-" + shortId(),
                locations.findByPublicId(foreignLocation).orElseThrow(), clock.instant()));
        assertThatThrownBy(() -> pos.openShift(fixture.cashier(), foreignRegister.publicId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pos.registers(fixture.operations())).isInstanceOf(AccessDeniedException.class);

        scopes.setAssignment(fixture.admin(), fixture.cashierId(), fixture.branchId(), fixture.locationId(), false);
        assertThatThrownBy(() -> pos.registers(fixture.cashier())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentShiftOpenAllowsOneCashierOnOneRegister() throws Exception {
        Fixture fixture = fixture("shift-open", 1);
        SessionPrincipal otherCashier = createCashier(fixture, "shift-open-other");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> openAfterBarrier(fixture.cashier(), fixture.registerId(), ready, start));
            var b = executor.submit(() -> openAfterBarrier(otherCashier, fixture.registerId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertThat(outcomes).filteredOn(PosService.ShiftView.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessConflictException.class::isInstance).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cashier_shift shifts JOIN pos_register registers ON registers.id = shifts.register_id WHERE registers.public_id = ? AND shifts.status = 'OPEN'", Integer.class, fixture.registerId())).isOne();
    }

    @Test
    void saleAndCloseEachWinOneForcedOrdering() throws Exception {
        Fixture saleWins = fixture("sale-wins-close", 1);
        UUID firstShift = pos.openShift(saleWins.cashier(), saleWins.registerId()).id();
        CountDownLatch sold = new CountDownLatch(1);
        CountDownLatch allowSaleCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = pos.sell(saleWins.cashier(), firstShift, saleWins.variantId(), "sale-wins");
                sold.countDown(); await(allowSaleCommit); return result;
            }));
            assertThat(sold.await(10, TimeUnit.SECONDS)).isTrue();
            var closer = executor.submit(() -> pos.closeShift(saleWins.cashier(), firstShift));
            allowSaleCommit.countDown();
            assertThat(winner.get(15, TimeUnit.SECONDS).receipt().total()).isEqualTo(125_000);
            assertThat(closer.get(15, TimeUnit.SECONDS).status()).isEqualTo("CLOSED");
        }
        assertCounts(saleWins, 1, 1, 1);

        Fixture closeWins = fixture("close-wins-sale", 1);
        UUID secondShift = pos.openShift(closeWins.cashier(), closeWins.registerId()).id();
        CountDownLatch closed = new CountDownLatch(1);
        CountDownLatch allowCloseCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = pos.closeShift(closeWins.cashier(), secondShift);
                closed.countDown(); await(allowCloseCommit); return result;
            }));
            assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = executor.submit(() -> pos.sell(closeWins.cashier(), secondShift, closeWins.variantId(), "close-wins"));
            allowCloseCommit.countDown();
            assertThat(winner.get(15, TimeUnit.SECONDS).status()).isEqualTo("CLOSED");
            assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertCounts(closeWins, 0, 0, 0);
        assertThat(balance(closeWins)).isEqualTo(new Balance(1, 0, 1));
    }

    @Test
    void posAndOnlineEachWinTheFinalUnitUnderRealSqlOrdering() throws Exception {
        Fixture posWins = fixture("pos-wins", 1);
        UUID posShift = pos.openShift(posWins.cashier(), posWins.registerId()).id();
        var posQuote = pricing.quote(posWins.customer(), posWins.variantId());
        CountDownLatch sold = new CountDownLatch(1);
        CountDownLatch allowPosCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = pos.sell(posWins.cashier(), posShift, posWins.variantId(), "pos-race-win");
                sold.countDown(); await(allowPosCommit); return result;
            }));
            assertThat(sold.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = executor.submit(() -> orders.checkout(posWins.customer(), posQuote.id(), "online-race-lose"));
            allowPosCommit.countDown();
            assertThat(winner.get(15, TimeUnit.SECONDS).receipt().orderId()).isNotNull();
            assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertThat(balance(posWins)).isEqualTo(new Balance(0, 0, 0));
        assertRaceCounts(posWins, 0, 1, 1, 1);

        Fixture onlineWins = fixture("online-wins", 1);
        UUID onlineShift = pos.openShift(onlineWins.cashier(), onlineWins.registerId()).id();
        var onlineQuote = pricing.quote(onlineWins.customer(), onlineWins.variantId());
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch allowOnlineCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = orders.checkout(onlineWins.customer(), onlineQuote.id(), "online-race-win");
                reserved.countDown(); await(allowOnlineCommit); return result;
            }));
            assertThat(reserved.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = executor.submit(() -> pos.sell(onlineWins.cashier(), onlineShift, onlineWins.variantId(), "pos-race-lose"));
            allowOnlineCommit.countDown();
            assertThat(winner.get(15, TimeUnit.SECONDS).status()).isEqualTo("PENDING_PAYMENT");
            assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertThat(balance(onlineWins)).isEqualTo(new Balance(1, 1, 0));
        assertRaceCounts(onlineWins, 1, 0, 0, 0);
    }

    @Test
    void twoIndependentSalesConsumeTwoUnitsWithoutFalseConflict() throws Exception {
        Fixture fixture = fixture("two-stock", 2);
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> sellAfterBarrier(fixture, shiftId, "two-a", ready, start));
            var b = executor.submit(() -> sellAfterBarrier(fixture, shiftId, "two-b", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).receipt().orderId()).isNotEqualTo(b.get(15, TimeUnit.SECONDS).receipt().orderId());
        }
        assertThat(balance(fixture)).isEqualTo(new Balance(0, 0, 0));
        assertCounts(fixture, 2, 2, 2);
    }

    @Test
    void controlledDatabaseFailureRollsBackEveryLocalFact() {
        Fixture fixture = fixture("rollback", 1);
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        jdbc.execute("""
                CREATE TRIGGER TR_v12_pos_rollback ON inventory_stock_movement AFTER INSERT AS
                BEGIN
                    IF EXISTS (SELECT 1 FROM inserted WHERE operation_type = 'POS_CASH_SALE')
                        THROW 51000, 'Controlled POS movement failure', 1;
                END
                """);
        try {
            assertThatThrownBy(() -> pos.sell(fixture.cashier(), shiftId, fixture.variantId(), "rollback"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER TR_v12_pos_rollback");
        }
        assertThat(balance(fixture)).isEqualTo(new Balance(1, 0, 1));
        assertCounts(fixture, 0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pickup_fulfillment fulfillments JOIN commerce_order orders ON orders.id = fulfillments.order_id JOIN commerce_order_item items ON items.order_id = orders.id WHERE items.variant_public_id = ? AND orders.channel = 'POS'", Integer.class, fixture.variantId())).isZero();
    }

    @Test
    void pricingIsSharedClientCannotOverrideAndHistoricalReceiptIsImmutable() {
        Fixture fixture = fixture("pricing", 2);
        UUID shiftId = pos.openShift(fixture.cashier(), fixture.registerId()).id();
        var customerQuote = pricing.quote(fixture.customer(), fixture.variantId());
        var posLookup = pos.lookup(fixture.cashier(), shiftId, fixture.sku());
        assertThat(posLookup.amount()).isEqualTo(customerQuote.amount()).isEqualTo(125_000);

        var first = pos.sell(fixture.cashier(), shiftId, fixture.variantId(), "price-old").receipt();
        clock.advance(Duration.ofSeconds(1));
        catalog.setPrice(fixture.operations(), fixture.variantId(), 150_000);
        assertThat(pos.lookup(fixture.cashier(), shiftId, fixture.sku()).amount()).isEqualTo(150_000);
        var second = pos.sell(fixture.cashier(), shiftId, fixture.variantId(), "price-new").receipt();
        assertThat(pos.receipt(fixture.cashier(), first.orderId()).total()).isEqualTo(125_000);
        assertThat(second.total()).isEqualTo(150_000);
    }

    @Test
    void browserApiEnforcesCsrfAndIgnoresManipulatedPriceAuthority() throws Exception {
        Fixture fixture = fixture("api", 1);
        Browser browser = new Browser();
        login(browser, fixture.cashierLogin());
        HttpResponse<String> csrfDenied = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/operations/pos/shifts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"registerId\":\"" + fixture.registerId() + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrfDenied.statusCode()).isEqualTo(403);

        Csrf csrf = csrf(browser);
        HttpResponse<String> opened = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/operations/pos/shifts"))
                .header("Content-Type", "application/json").header(csrf.header(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString("{\"registerId\":\"" + fixture.registerId() + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(opened.statusCode()).isEqualTo(201);
        String shiftId = json.readTree(opened.body()).get("id").asString();
        HttpResponse<String> sold = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/operations/pos/sales"))
                .header("Content-Type", "application/json").header(csrf.header(), csrf.token())
                .header("Idempotency-Key", "api-sale")
                .POST(HttpRequest.BodyPublishers.ofString("{\"shiftId\":\"" + shiftId + "\",\"variantId\":\"" + fixture.variantId() + "\",\"unitPrice\":1}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(sold.statusCode()).isEqualTo(201);
        assertThat(json.readTree(sold.body()).get("total").asLong()).isEqualTo(125_000);
    }

    private PosService.SaleResult sellAfterBarrier(Fixture fixture, UUID shiftId, String key,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); start.await();
        return pos.sell(fixture.cashier(), shiftId, fixture.variantId(), key);
    }

    private Object openAfterBarrier(SessionPrincipal cashier, UUID registerId,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); start.await();
        try { return pos.openShift(cashier, registerId); }
        catch (BusinessConflictException exception) { return exception; }
    }

    private Fixture fixture(String suffix, long stock) {
        SessionPrincipal admin = bootstrapAdmin();
        String operationsLogin = "v12-" + suffix + "-ops-" + UUID.randomUUID() + "@example.com";
        String cashierLogin = "v12-" + suffix + "-cashier-" + UUID.randomUUID() + "@example.com";
        String customerLogin = "v12-" + suffix + "-customer-" + UUID.randomUUID() + "@example.com";
        UUID operationsId = identities.createAccount(admin, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        UUID cashierId = identities.createAccount(admin, cashierLogin, PASSWORD, RoleCode.CASHIER);
        identities.createAccount(admin, customerLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, "V12-" + suffix + "-" + shortId(), "POS branch");
        UUID location = scopes.createLocation(admin, branch, "FLOOR-" + shortId(), "Sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        scopes.setAssignment(admin, cashierId, branch, location, true);
        SessionPrincipal operations = principal(operationsLogin);
        UUID product = catalog.createProduct(operations, "POS Runner " + suffix);
        String sku = "V12-" + suffix + "-" + shortId();
        UUID variant = catalog.createVariant(operations, product, sku, "42", "Black");
        catalog.setPrice(operations, variant, 125_000);
        catalog.setStock(operations, variant, location, stock);
        catalog.publish(operations, variant);
        Location locationEntity = locations.findByPublicId(location).orElseThrow();
        PosRegister register = registers.save(PosRegister.create("REG-" + shortId(), locationEntity, clock.instant()));
        return new Fixture(admin, operations, principal(cashierLogin), principal(customerLogin), cashierId,
                cashierLogin, branch, location, register.publicId(), product, variant, sku);
    }

    private Fixture addVariant(Fixture fixture, String suffix, long stock) {
        String sku = "V12-" + suffix + "-" + shortId();
        UUID variant = catalog.createVariant(fixture.operations(), fixture.productId(), sku, "43", "White");
        catalog.setPrice(fixture.operations(), variant, 130_000);
        catalog.setStock(fixture.operations(), variant, fixture.locationId(), stock);
        catalog.publish(fixture.operations(), variant);
        return new Fixture(fixture.admin(), fixture.operations(), fixture.cashier(), fixture.customer(),
                fixture.cashierId(), fixture.cashierLogin(), fixture.branchId(), fixture.locationId(),
                fixture.registerId(), fixture.productId(), variant, sku);
    }

    private SessionPrincipal createCashier(Fixture fixture, String suffix) {
        String login = "v12-" + suffix + "-" + UUID.randomUUID() + "@example.com";
        UUID id = identities.createAccount(fixture.admin(), login, PASSWORD, RoleCode.CASHIER);
        scopes.setAssignment(fixture.admin(), id, fixture.branchId(), fixture.locationId(), true);
        return principal(login);
    }

    private void assertCounts(Fixture fixture, int orders, int tenders, int movements) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commerce_order orders JOIN commerce_order_item items ON items.order_id = orders.id WHERE orders.channel = 'POS' AND items.variant_public_id = ?", Integer.class, fixture.variantId())).isEqualTo(orders);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_tender tenders JOIN commerce_order orders ON orders.id = tenders.order_id JOIN commerce_order_item items ON items.order_id = orders.id WHERE items.variant_public_id = ?", Integer.class, fixture.variantId())).isEqualTo(tenders);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE operation_type = 'POS_CASH_SALE' AND variant_public_id = ?", Integer.class, fixture.variantId())).isEqualTo(movements);
    }

    private void assertRaceCounts(Fixture fixture, int onlineReservations, int posOrders, int tenders, int movements) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_reservation reservations JOIN catalog_product_variant variants ON variants.id = reservations.variant_id WHERE variants.public_id = ? AND reservations.status IN ('ACTIVE', 'ADOPTED')", Integer.class, fixture.variantId())).isEqualTo(onlineReservations);
        assertCounts(fixture, posOrders, tenders, movements);
    }

    private Balance balance(Fixture fixture) {
        return jdbc.queryForObject("SELECT balances.on_hand, balances.reserved, balances.on_hand - balances.reserved FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id JOIN org_location locations ON locations.id = balances.location_id WHERE variants.public_id = ? AND locations.public_id = ?",
                (rs, row) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)), fixture.variantId(), fixture.locationId());
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID();
        String login = "v12-admin-" + id + "@example.com";
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) {
        SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login);
        principal.eraseCredentials();
        return principal;
    }

    private void login(Browser browser, String username) throws Exception {
        Csrf csrf = csrf(browser);
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header(csrf.header(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private Csrf csrf(Browser browser) throws Exception {
        JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        return new Csrf(node.get("headerName").asString(), node.get("token").asString());
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
    private static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); } }

    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Fixture(SessionPrincipal admin, SessionPrincipal operations, SessionPrincipal cashier,
            SessionPrincipal customer, UUID cashierId, String cashierLogin, UUID branchId, UUID locationId,
            UUID registerId, UUID productId, UUID variantId, String sku) { }

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
