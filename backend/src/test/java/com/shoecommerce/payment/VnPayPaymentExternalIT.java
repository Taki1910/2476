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
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.catalog.CatalogService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.fulfillment.PickupFulfillmentService;
import com.shoecommerce.fulfillment.PickupCancellationService;
import com.shoecommerce.fulfillment.PickupCancellationTransactionService;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.order.CustomerOrderRepository;
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.pricing.PriceQuoteService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(VnPayPaymentExternalIT.TestClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VnPayPaymentExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    private static final String HASH_SECRET = "test-only-vnpay-hash-secret";
    private static final String RUN_PROVIDER_PREFIX = "%08d".formatted(
            Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
    private static final Instant TEST_NOW = Instant.parse("2026-08-27T02:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired PriceQuoteService pricing;
    @Autowired CustomerOrderService orders;
    @Autowired CustomerOrderRepository orderRepository;
    @Autowired InventoryReservationService reservations;
    @Autowired PaymentAttemptService payments;
    @Autowired PaymentProvider provider;
    @Autowired VerifiedPaymentResultService results;
    @Autowired CheckoutHoldExpiryService expiry;
    @Autowired PickupFulfillmentService fulfillments;
    @Autowired PickupCancellationService cancellations;
    @Autowired PickupCancellationTransactionService cancellationTransactions;
    @Autowired VoidService voids;
    @Autowired TestVoidProvider voidProvider;
    @Autowired TransactionTemplate transactions;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void resetClock() { clock.set(TEST_NOW); }

    @Test
    void verifiedSuccessCommitsInventoryExactlyOnceAndPaidHoldCannotExpire() {
        Flow flow = flow("success", 2);
        var verified = verified(flow, "8100000001", 125_000, "00", "00");
        Balance before = balance(flow);

        assertThat(results.apply(verified)).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        assertThat(results.apply(verified)).isEqualTo(VerifiedPaymentResultService.Result.ALREADY_PROCESSED);

        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo("SUCCEEDED");
        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo("PAID");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("COMMITTED");
        assertThat(balance(flow)).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'PAYMENT_SUCCEEDED' AND resource_public_id = ?",
                Integer.class, flow.attempt().id())).isOne();

        clock.set(flow.expiresAt().plusSeconds(1));
        expiry.expireForVariant(flow.variantId());
        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo("PAID");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("COMMITTED");
        assertThatThrownBy(() -> orders.cancelOwn(flow.customer(), flow.orderId()))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> payments.initiate(flow.customer(), flow.orderId(), "after-paid"))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void signedMismatchesAndProviderTransactionReuseCannotPay() {
        Flow amount = flow("amount", 1);
        assertThat(results.apply(verified(amount, "8100000002", 124_999, "00", "00")))
                .isEqualTo(VerifiedPaymentResultService.Result.AMOUNT_MISMATCH);
        assertPending(amount);

        Map<String, String> invalidSignature = callback(amount, "8100000003", 125_000, "00", "00");
        invalidSignature.put("vnp_SecureHash", "00".repeat(64));
        assertThatThrownBy(() -> provider.verify(invalidSignature))
                .isInstanceOf(VnPayPaymentProvider.InvalidSignatureException.class);
        assertPending(amount);

        Map<String, String> wrongMerchant = callback(amount, "8100000004", 125_000, "00", "00");
        wrongMerchant.put("vnp_TmnCode", "WRONG");
        sign(wrongMerchant);
        assertThatThrownBy(() -> provider.verify(wrongMerchant))
                .isInstanceOf(VnPayPaymentProvider.MerchantMismatchException.class);
        assertPending(amount);

        Map<String, String> unknown = callback(amount, "8100000005", 125_000, "00", "00");
        unknown.put("vnp_TxnRef", "UNKNOWN");
        sign(unknown);
        assertThat(results.apply(provider.verify(unknown))).isEqualTo(VerifiedPaymentResultService.Result.NOT_FOUND);
        assertPending(amount);

        Flow first = flow("provider-txn-a", 1);
        Flow second = flow("provider-txn-b", 1);
        assertThat(results.apply(verified(first, "8100000099", 125_000, "00", "00")))
                .isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        assertThat(results.apply(verified(second, "8100000099", 125_000, "00", "00")))
                .isEqualTo(VerifiedPaymentResultService.Result.ALREADY_PROCESSED);
        assertPending(second);
    }

    @Test
    void failedAttemptPreservesEvidenceAndAllowsOneDeliberateRetry() {
        Flow flow = flow("retry", 1);
        assertThat(results.apply(verified(flow, "8100000006", 125_000, "24", "02")))
                .isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo("FAILED");
        assertPendingOrderAndReservation(flow);

        var retry = payments.initiate(flow.customer(), flow.orderId(), "retry-key-2");
        assertThat(retry.attempt().id()).isNotEqualTo(flow.attempt().id());
        assertThat(retry.attempt().status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id "
                        + "JOIN commerce_order orders ON orders.id = payments.order_id "
                        + "WHERE orders.public_id = ? AND attempts.status = 'PENDING'",
                Integer.class, flow.orderId())).isOne();
    }

    @Test
    void browserReturnIsNavigationOnlyAndIpnIsPublicButCryptographicallyAuthenticated() throws Exception {
        Pending pending = pending("http", 1);
        Browser owner = new Browser();
        Browser other = new Browser();
        login(owner, pending.customerLogin());
        login(other, pending.otherLogin());

        HttpResponse<String> missingCsrf = owner.client.send(HttpRequest.newBuilder(
                uri("/api/v1/orders/" + pending.order().id() + "/payments"))
                .header("Idempotency-Key", "http-payment")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);

        HttpResponse<String> forbidden = initiate(other, pending.order().id(), "other-payment");
        assertThat(forbidden.statusCode()).isEqualTo(403);
        HttpResponse<String> created = initiate(owner, pending.order().id(), "http-payment");
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        UUID attemptId = UUID.fromString(body.get("attempt").get("id").asString());
        String reference = body.get("attempt").get("merchantTransactionReference").asString();
        assertThat(body.get("paymentUrl").asString()).startsWith("https://sandbox.vnpayment.vn/");

        HttpResponse<String> forgedReturn = owner.client.send(HttpRequest.newBuilder(uri(
                "/api/v1/payments/vnpay/return?vnp_TxnRef=" + reference
                        + "&vnp_ResponseCode=00&vnp_TransactionStatus=00")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(forgedReturn.statusCode()).isEqualTo(303);
        assertThat(orders.readOwn(pending.customer(), pending.order().id()).status()).isEqualTo("PENDING_PAYMENT");

        Map<String, String> invalid = callback(reference, "8100000007", 125_000, "00", "00");
        invalid.put("vnp_SecureHash", "00".repeat(64));
        HttpResponse<String> badIpn = getPublic("/api/v1/payments/vnpay/ipn?" + query(invalid));
        assertThat(badIpn.body()).contains("\"RspCode\":\"97\"");
        assertThat(orders.readOwn(pending.customer(), pending.order().id()).status()).isEqualTo("PENDING_PAYMENT");

        Map<String, String> valid = callback(reference, "8100000007", 125_000, "00", "00");
        sign(valid);
        HttpResponse<String> applied = getPublic("/api/v1/payments/vnpay/ipn?" + query(valid));
        assertThat(applied.statusCode()).isEqualTo(200);
        assertThat(applied.body()).contains("\"RspCode\":\"00\"");
        assertThat(payments.readOwn(pending.customer(), attemptId).status()).isEqualTo("SUCCEEDED");
        assertThat(orders.readOwn(pending.customer(), pending.order().id()).status()).isEqualTo("PAID");
    }

    @Test
    void realSqlPaymentWinsExpiryRace() throws Exception {
        Flow flow = flow("payment-wins", 1);
        var verified = verified(flow, "8100000008", 125_000, "00", "00");
        CountDownLatch applied = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var payment = executor.submit(() -> transactions.execute(status -> {
                var outcome = results.apply(verified);
                applied.countDown();
                await(commit);
                return outcome;
            }));
            assertThat(applied.await(10, TimeUnit.SECONDS)).isTrue();
            clock.set(flow.expiresAt());
            var expirer = executor.submit(() -> expiry.expireForVariant(flow.variantId()));
            commit.countDown();
            assertThat(payment.get(15, TimeUnit.SECONDS)).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
            expirer.get(15, TimeUnit.SECONDS);
        }
        assertPaidCommitted(flow);
    }

    @Test
    void realSqlExpiryWinsAndLateVerifiedSuccessRequiresReview() throws Exception {
        Flow flow = flow("expiry-wins", 1);
        var verified = verified(flow, "8100000009", 125_000, "00", "00");
        clock.set(flow.expiresAt());
        CountDownLatch expired = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch paymentStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var expirer = executor.submit(() -> transactions.executeWithoutResult(status -> {
                var order = orderRepository.findLockedByPublicId(flow.orderId()).orElseThrow();
                payments.expirePendingForOrder(flow.orderId(), clock.instant());
                reservations.expireAdoptedForOrder(flow.reservationId(), clock.instant());
                order.expire(clock.instant());
                expired.countDown();
                await(commit);
            }));
            assertThat(expired.await(10, TimeUnit.SECONDS)).isTrue();
            var payment = executor.submit(() -> {
                paymentStarted.countDown();
                return results.apply(verified);
            });
            assertThat(paymentStarted.await(10, TimeUnit.SECONDS)).isTrue();
            commit.countDown();
            expirer.get(15, TimeUnit.SECONDS);
            assertThat(payment.get(15, TimeUnit.SECONDS)).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        }

        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo("CANCELLED");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("EXPIRED");
        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(balance(flow)).isEqualTo(new Balance(1, 0, 1));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce_order orders JOIN inventory_reservation reservations "
                        + "ON reservations.public_id = orders.reservation_public_id "
                        + "WHERE orders.public_id = ? AND orders.status = 'PAID' AND reservations.status = 'EXPIRED'",
                Integer.class, flow.orderId())).isZero();
    }

    @Test
    void pickupHandoverIsIdempotentAndLocationScoped() {
        Flow flow = paidFlow("pickup", 1);
        var pickup = fulfillments.create(flow.operations(), flow.orderId());

        String outsiderLogin = "v11-outsider-" + UUID.randomUUID() + "@example.com";
        SessionPrincipal admin = bootstrapAdmin();
        UUID outsiderId = identities.createAccount(admin, outsiderLogin, PASSWORD, RoleCode.OPERATIONS);
        UUID otherBranch = scopes.createBranch(admin, "V11-X-" + shortId(), "Other branch");
        UUID otherLocation = scopes.createLocation(admin, otherBranch, "OTHER-" + shortId(), "Other location");
        scopes.setAssignment(admin, outsiderId, otherBranch, otherLocation, true);
        SessionPrincipal outsider = principal(outsiderLogin);
        assertThatThrownBy(() -> fulfillments.prepare(outsider, pickup.id()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(fulfillments.prepare(flow.operations(), pickup.id()).status()).isEqualTo("PREPARED");
        var handedOver = fulfillments.handover(flow.operations(), pickup.id(), "handover-key");
        var replay = fulfillments.handover(flow.operations(), pickup.id(), "handover-key");
        assertThat(handedOver.status()).isEqualTo("HANDED_OVER");
        assertThat(replay.handedOverAt()).isEqualTo(handedOver.handedOverAt());
        assertThat(balance(flow)).isEqualTo(new Balance(0, 0, 0));
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("CONSUMED");
        assertThat(movementCount(flow, "PICKUP_HANDOVER")).isOne();
        assertThatThrownBy(() -> fulfillments.handover(flow.operations(), pickup.id(), "different-key"))
                .isInstanceOf(BusinessConflictException.class);
        assertThat(movementCount(flow, "PICKUP_HANDOVER")).isOne();
    }

    @Test
    void confirmedCancellationRestoresOnlyReservedAndReplaysWithoutProviderCall() {
        Flow flow = paidFlow("cancel", 1);
        var pickup = fulfillments.create(flow.operations(), flow.orderId());
        fulfillments.prepare(flow.operations(), pickup.id());
        int beforeCalls = voidProvider.calls();

        var cancelled = cancellations.cancel(flow.customer(), flow.orderId(), "cancel-key");
        var replay = cancellations.cancel(flow.customer(), flow.orderId(), "cancel-key");

        assertThat(cancelled.financialVoid().status()).isEqualTo("SUCCEEDED");
        assertThat(replay.financialVoid().id()).isEqualTo(cancelled.financialVoid().id());
        assertThat(voidProvider.calls()).isEqualTo(beforeCalls + 1);
        assertThat(balance(flow)).isEqualTo(new Balance(1, 0, 1));
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("CANCELLED_RESTORED");
        assertThat(movementCount(flow, "CANCELLATION_RESTORE")).isOne();
        assertThat(movementCount(flow, "PICKUP_HANDOVER")).isZero();
        assertThatThrownBy(() -> fulfillments.handover(flow.operations(), pickup.id(), "late-handover"))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void unknownRetainsCapacityAndDefinitiveFailureReleasesOnceForDeliberateRetry() {
        Flow unknown = paidFlow("void-unknown", 1);
        voidProvider.next(VoidProvider.Outcome.UNKNOWN);
        var unknownResult = cancellations.cancel(unknown.customer(), unknown.orderId(), "unknown-key");
        assertThat(unknownResult.financialVoid().status()).isEqualTo("UNKNOWN");
        assertThat(allocationStatus(unknown, 1)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> cancellations.retry(unknown.customer(), unknown.orderId(), "unknown-retry"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("unknown");

        Flow failed = paidFlow("void-failed", 1);
        voidProvider.next(VoidProvider.Outcome.DEFINITIVE_FAILED);
        var failedResult = cancellations.cancel(failed.customer(), failed.orderId(), "failed-key");
        assertThat(failedResult.financialVoid().status()).isEqualTo("FAILED_RETRYABLE");
        assertThat(allocationStatus(failed, 1)).isEqualTo("RELEASED");
        voidProvider.next(VoidProvider.Outcome.SUCCEEDED);
        var retried = cancellations.retry(failed.customer(), failed.orderId(), "retry-key");
        var retryReplay = cancellations.retry(failed.customer(), failed.orderId(), "retry-key");
        assertThat(retried.generation()).isEqualTo(2);
        assertThat(retried.status()).isEqualTo("SUCCEEDED");
        assertThat(retryReplay.attemptId()).isEqualTo(retried.attemptId());
        assertThat(allocationStatus(failed, 2)).isEqualTo("SUCCEEDED");
        assertThat(movementCount(failed, "CANCELLATION_RESTORE")).isOne();
    }

    @Test
    void realSqlHandoverAndCancellationEachWinOneForcedOrdering() throws Exception {
        Flow handoverWins = paidFlow("race-handover", 1);
        var firstPickup = fulfillments.create(handoverWins.operations(), handoverWins.orderId());
        fulfillments.prepare(handoverWins.operations(), firstPickup.id());
        CountDownLatch handedOver = new CountDownLatch(1);
        CountDownLatch allowHandoverCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = fulfillments.handover(handoverWins.operations(), firstPickup.id(), "race-handover-key");
                handedOver.countDown(); await(allowHandoverCommit); return result;
            }));
            assertThat(handedOver.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = executor.submit(() -> cancellations.cancel(handoverWins.customer(), handoverWins.orderId(), "race-cancel-key"));
            allowHandoverCommit.countDown();
            assertThat(winner.get(15, TimeUnit.SECONDS).status()).isEqualTo("HANDED_OVER");
            assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertThat(balance(handoverWins)).isEqualTo(new Balance(0, 0, 0));
        assertThat(movementCount(handoverWins, "PICKUP_HANDOVER")).isOne();
        assertThat(movementCount(handoverWins, "CANCELLATION_RESTORE")).isZero();
        assertThat(voidCount(handoverWins)).isZero();

        Flow cancellationWins = paidFlow("race-cancel", 1);
        var secondPickup = fulfillments.create(cancellationWins.operations(), cancellationWins.orderId());
        fulfillments.prepare(cancellationWins.operations(), secondPickup.id());
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch allowCancellationCommit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var winner = executor.submit(() -> transactions.execute(status -> {
                var result = cancellationTransactions.cancel(cancellationWins.customer(), cancellationWins.orderId(), "race-cancel-win");
                cancelled.countDown(); await(allowCancellationCommit); return result;
            }));
            assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
            var loser = executor.submit(() -> fulfillments.handover(cancellationWins.operations(), secondPickup.id(), "race-handover-lose"));
            allowCancellationCommit.countDown();
            var local = winner.get(15, TimeUnit.SECONDS);
            voids.execute(local.financial());
            assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
        }
        assertThat(balance(cancellationWins)).isEqualTo(new Balance(1, 0, 1));
        assertThat(movementCount(cancellationWins, "PICKUP_HANDOVER")).isZero();
        assertThat(movementCount(cancellationWins, "CANCELLATION_RESTORE")).isOne();
        assertThat(voidCount(cancellationWins)).isOne();
    }

    private Flow flow(String suffix, long stock) {
        Pending pending = pending(suffix, stock);
        var attempt = payments.initiate(pending.customer(), pending.order().id(), "pay-" + suffix).attempt();
        return new Flow(pending.variantId(), pending.locationId(), pending.customer(), pending.operations(), pending.order().id(),
                pending.order().reservationId(), pending.order().reservationExpiresAt(), attempt);
    }

    private Flow paidFlow(String suffix, long stock) {
        Flow flow = flow(suffix, stock);
        assertThat(results.apply(verified(flow, Long.toString(Math.abs(UUID.randomUUID().getLeastSignificantBits())),
                125_000, "00", "00"))).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        return flow;
    }

    private Pending pending(String suffix, long stock) {
        SessionPrincipal admin = bootstrapAdmin();
        String operationsLogin = "v10-" + suffix + "-ops-" + UUID.randomUUID() + "@example.com";
        String customerLogin = "v10-" + suffix + "-customer-" + UUID.randomUUID() + "@example.com";
        String otherLogin = "v10-" + suffix + "-other-" + UUID.randomUUID() + "@example.com";
        UUID operationsId = identities.createAccount(admin, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, customerLogin, PASSWORD, RoleCode.CUSTOMER);
        identities.createAccount(admin, otherLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, "V10-" + suffix + "-" + shortId(), "VNPAY branch");
        UUID location = scopes.createLocation(admin, branch, "FLOOR-" + shortId(), "Sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        SessionPrincipal operations = principal(operationsLogin);
        SessionPrincipal customer = principal(customerLogin);
        UUID product = catalog.createProduct(operations, "VNPAY Runner " + suffix);
        UUID variant = catalog.createVariant(operations, product, "V10-" + suffix + "-" + shortId(), "42", "Ink");
        catalog.setPrice(operations, variant, 125_000);
        catalog.setStock(operations, variant, location, stock);
        catalog.publish(operations, variant);
        var quote = pricing.quote(customer, variant);
        var order = orders.checkout(customer, quote.id(), "checkout-" + suffix);
        return new Pending(variant, location, customer, operations, customerLogin, otherLogin, order);
    }

    private PaymentProvider.VerifiedResult verified(Flow flow, String transactionNo, long amount,
            String response, String status) {
        return provider.verify(signedCallback(flow.attempt().merchantTransactionReference(),
                transactionNo, amount, response, status));
    }
    private Map<String, String> callback(Flow flow, String transactionNo, long amount,
            String response, String status) {
        return callback(flow.attempt().merchantTransactionReference(), transactionNo, amount, response, status);
    }
    private Map<String, String> signedCallback(String reference, String transactionNo, long amount,
            String response, String status) {
        Map<String, String> callback = callback(reference, transactionNo, amount, response, status);
        sign(callback);
        return callback;
    }
    private Map<String, String> callback(String reference, String transactionNo, long amount,
            String response, String status) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("vnp_TmnCode", "TESTTMNCODE");
        values.put("vnp_TxnRef", reference);
        values.put("vnp_Amount", Long.toString(Math.multiplyExact(amount, 100)));
        values.put("vnp_CurrCode", "VND");
        values.put("vnp_ResponseCode", response);
        values.put("vnp_TransactionStatus", status);
        values.put("vnp_TransactionNo", RUN_PROVIDER_PREFIX
                + transactionNo.substring(Math.max(0, transactionNo.length() - 7)));
        values.put("vnp_PayDate", "20260827090500");
        return values;
    }
    private static void sign(Map<String, String> values) {
        values.remove("vnp_SecureHash");
        String canonical = VnPayPaymentProvider.canonical(values);
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            values.put("vnp_SecureHash", HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertPending(Flow flow) {
        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo("PENDING");
        assertPendingOrderAndReservation(flow);
    }
    private void assertPendingOrderAndReservation(Flow flow) {
        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("ADOPTED");
    }
    private void assertPaidCommitted(Flow flow) {
        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo("PAID");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status()).isEqualTo("COMMITTED");
        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo("SUCCEEDED");
        assertThat(balance(flow)).isEqualTo(new Balance(1, 1, 0));
    }
    private Balance balance(Flow flow) {
        return jdbc.queryForObject("SELECT on_hand, reserved, on_hand - reserved FROM inventory_balance "
                        + "WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) "
                        + "AND location_id = (SELECT id FROM org_location WHERE public_id = ?)",
                (rs, row) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                flow.variantId(), flow.locationId());
    }
    private int movementCount(Flow flow, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE order_public_id = ? AND operation_type = ?",
                Integer.class, flow.orderId(), type);
    }
    private int voidCount(Flow flow) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_void_operation WHERE order_public_id = ?",
                Integer.class, flow.orderId());
    }
    private String allocationStatus(Flow flow, int generation) {
        return jdbc.queryForObject("SELECT allocations.status FROM payment_void_allocation allocations JOIN payment_void_attempt attempts ON attempts.id = allocations.void_attempt_id JOIN payment_void_operation operations ON operations.id = attempts.void_operation_id WHERE operations.order_public_id = ? AND attempts.generation = ?",
                String.class, flow.orderId(), generation);
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID();
        String login = "v10-admin-" + id + "@example.com";
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)",
                id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id "
                        + "FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles "
                        + "WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }
    private SessionPrincipal principal(String login) {
        SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login);
        principal.eraseCredentials();
        return principal;
    }

    private HttpResponse<String> initiate(Browser browser, UUID orderId, String key) throws Exception {
        Csrf csrf = csrf(browser);
        return browser.client.send(HttpRequest.newBuilder(uri("/api/v1/orders/" + orderId + "/payments"))
                .header("Idempotency-Key", key).header(csrf.header(), csrf.token())
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void login(Browser browser, String username) throws Exception {
        Csrf csrf = csrf(browser);
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrf.header(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
    private Csrf csrf(Browser browser) throws Exception {
        JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        return new Csrf(node.get("headerName").asString(), node.get("token").asString());
    }
    private HttpResponse<String> getPublic(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static String query(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }

    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private record Csrf(String header, String token) { }
    private record Balance(long onHand, long reserved, long available) { }
    private record Pending(UUID variantId, UUID locationId, SessionPrincipal customer, SessionPrincipal operations,
            String customerLogin, String otherLogin, CustomerOrderService.OrderView order) { }
    private record Flow(UUID variantId, UUID locationId, SessionPrincipal customer, SessionPrincipal operations, UUID orderId,
            UUID reservationId, Instant expiresAt, PaymentAttemptService.PaymentAttemptView attempt) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(TEST_NOW); }
    }
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;
        MutableClock(Instant initial) { this(new AtomicReference<>(initial), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current; this.zone = zone;
        }
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone); }
        @Override public Instant instant() { return current.get(); }
    }
}
