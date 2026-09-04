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
import java.util.List;
import java.util.ArrayList;
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
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.fulfillment.PickupFulfillmentService;
import com.shoecommerce.fulfillment.PickupFulfillment;
import com.shoecommerce.fulfillment.PickupFulfillmentRepository;
import com.shoecommerce.fulfillment.PickupCancellationService;
import com.shoecommerce.fulfillment.PickupCancellationTransactionService;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.order.CustomerOrderRepository;
import com.shoecommerce.order.CustomerOrderService;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.CartQuoteService;
import org.slf4j.MDC;
import com.shoecommerce.platform.api.CorrelationIdFilter;

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
    @Autowired CartQuoteService cartPricing;
    @Autowired CustomerOrderService orders;
    @Autowired CustomerOrderRepository orderRepository;
    @Autowired InventoryReservationService reservations;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired PaymentAttemptService payments;
    @Autowired PaymentProvider provider;
    @Autowired VerifiedPaymentResultService results;
    @Autowired CheckoutHoldExpiryService expiry;
    @Autowired PickupFulfillmentService fulfillments;
    @Autowired PickupFulfillmentRepository pickupRepository;
    @Autowired PickupCancellationService cancellations;
    @Autowired PickupCancellationTransactionService cancellationTransactions;
    @Autowired VoidService voids;
    @Autowired VoidResultService voidResults;
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
    void postHandoverCancellationWithNewKeyIsRejectedWithoutMutation() {
        Flow flow = paidFlow("late", 1);
        var pickup = fulfillments.create(flow.operations(), flow.orderId());
        fulfillments.prepare(flow.operations(), pickup.id());
        assertThat(fulfillments.handover(flow.operations(), pickup.id(), "post-handover-key").status())
                .isEqualTo("HANDED_OVER");

        String orderBefore = orders.readOwn(flow.customer(), flow.orderId()).status();
        String fulfillmentBefore = fulfillmentStatus(flow);
        String reservationBefore = reservations.readOwn(flow.customer(), flow.reservationId()).status();
        String captureBefore = payments.readOwn(flow.customer(), flow.attempt().id()).status();
        Balance balanceBefore = balance(flow);
        int movementsBefore = movementTotal(flow);
        int handoversBefore = movementCount(flow, "PICKUP_HANDOVER");
        int restoresBefore = movementCount(flow, "CANCELLATION_RESTORE");
        int voidsBefore = voidCount(flow);
        int voidAttemptsBefore = voidAttemptCount(flow);
        int providerCallsBefore = voidProvider.calls();

        assertThatThrownBy(() -> cancellations.cancel(flow.customer(), flow.orderId(), "new-post-handover-cancel-key"))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("FULFILLMENT_ALREADY_ISSUED"));

        assertThat(orders.readOwn(flow.customer(), flow.orderId()).status()).isEqualTo(orderBefore);
        assertThat(fulfillmentStatus(flow)).isEqualTo(fulfillmentBefore).isEqualTo("HANDED_OVER");
        assertThat(reservations.readOwn(flow.customer(), flow.reservationId()).status())
                .isEqualTo(reservationBefore).isEqualTo("CONSUMED");
        assertThat(payments.readOwn(flow.customer(), flow.attempt().id()).status()).isEqualTo(captureBefore).isEqualTo("SUCCEEDED");
        assertThat(balance(flow)).isEqualTo(balanceBefore);
        assertThat(movementTotal(flow)).isEqualTo(movementsBefore);
        assertThat(movementCount(flow, "PICKUP_HANDOVER")).isEqualTo(handoversBefore).isOne();
        assertThat(movementCount(flow, "CANCELLATION_RESTORE")).isEqualTo(restoresBefore).isZero();
        assertThat(voidCount(flow)).isEqualTo(voidsBefore).isZero();
        assertThat(voidAttemptCount(flow)).isEqualTo(voidAttemptsBefore).isZero();
        assertThat(voidProvider.calls()).isEqualTo(providerCallsBefore);
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

    @Test
    void multiItemPaymentSnapshotsFullAmountAndCommitsEveryReservationExactlyOnce() {
        CartFlow cart = cartFlow("cart-paid", 3);
        var before = cartBalances(cart);
        clock.set(clock.instant().plusSeconds(1));
        catalog.setPrice(cart.operations(), cart.facts().items().getFirst().variantId(), 2_000_000);
        assertThat(cart.attempt().amount()).isEqualByComparingTo("3690000");
        assertThat(payments.initiate(cart.customer(), cart.facts().orderId(), "pay-cart-paid").attempt().id())
                .isEqualTo(cart.attempt().id());
        var success = cartSuccess(cart);
        assertThat(results.apply(success)).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        assertThat(results.apply(success)).isEqualTo(VerifiedPaymentResultService.Result.ALREADY_PROCESSED);
        assertCartState(cart, "PAID", "COMMITTED");
        assertThat(cartBalances(cart)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = 'PAYMENT_SUCCEEDED' AND resource_public_id = ?",
                Integer.class, cart.attempt().id())).isOne();
    }

    @Test
    void multiItemCancellationRestoresEveryLineAndVoidRetryPreservesComponentCapacity() {
        CartFlow cart = cartFlow("cart-void", 3);
        results.apply(cartSuccess(cart));
        var before = cartBalances(cart);
        voidProvider.next(VoidProvider.Outcome.DEFINITIVE_FAILED);
        var cancelled = cancellations.cancel(cart.customer(), cart.facts().orderId(), "cart-cancel");
        assertThat(cancelled.financialVoid().amount()).isEqualByComparingTo("3690000");
        assertThat(cancelled.financialVoid().status()).isEqualTo("FAILED_RETRYABLE");
        assertCartState(cart, "CANCELLED", "CANCELLED_RESTORED");
        assertCartAllocations(cart, 1, "RELEASED");
        var restored = cartBalances(cart);
        for (int index = 0; index < before.size(); index++) {
            assertThat(restored.get(index).onHand()).isEqualTo(before.get(index).onHand());
            assertThat(restored.get(index).reserved()).isZero();
        }
        int calls = voidProvider.calls();
        assertThat(cancellations.cancel(cart.customer(), cart.facts().orderId(), "cart-cancel").financialVoid().id())
                .isEqualTo(cancelled.financialVoid().id());
        assertThat(voidProvider.calls()).isEqualTo(calls);
        assertThat(cancellations.retry(cart.customer(), cart.facts().orderId(), "cart-retry").status()).isEqualTo("SUCCEEDED");
        assertThat(cancellations.retry(cart.customer(), cart.facts().orderId(), "cart-retry").generation()).isEqualTo(2);
        assertCartAllocations(cart, 1, "RELEASED");
        assertCartAllocations(cart, 2, "SUCCEEDED");
        assertCartMovements(cart, "CANCELLATION_RESTORE", 3);
        assertThat(cartBalances(cart)).isEqualTo(restored);

        CartFlow unknown = cartFlow("cart-unknown", 2);
        results.apply(cartSuccess(unknown));
        voidProvider.next(VoidProvider.Outcome.UNKNOWN);
        cancellations.cancel(unknown.customer(), unknown.facts().orderId(), "unknown-cancel");
        assertCartAllocations(unknown, 1, "ACTIVE");
        assertThatThrownBy(() -> cancellations.retry(unknown.customer(), unknown.facts().orderId(), "unknown-retry"))
                .isInstanceOf(BusinessConflictException.class);
        assertCartMovements(unknown, "CANCELLATION_RESTORE", 2);
    }

    @Test
    void multiItemExpiryThroughOneVariantReleasesWholeOrderAndLatePaymentRequiresReview() {
        CartFlow cart = cartFlow("cart-expiry", 2);
        clock.set(cart.attempt().expiresAt());
        expiry.expireForVariant(cart.facts().items().getLast().variantId());
        assertCartState(cart, "CANCELLED", "EXPIRED");
        assertThat(results.apply(cartSuccess(cart))).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        assertThat(payments.readOwn(cart.customer(), cart.attempt().id()).status()).isEqualTo("REVIEW_REQUIRED");
        assertCartState(cart, "CANCELLED", "EXPIRED");
        assertThat(cartBalances(cart)).allMatch(balance -> balance.onHand() == 8 && balance.reserved() == 0);
    }

    @Test
    void multiItemPaymentWinsExpiryRaceWithoutPartiallyExpiringTheOrder() throws Exception {
        CartFlow cart = cartFlow("cart-payment-race", 3);
        var success = cartSuccess(cart);
        var before = cartBalances(cart);
        CountDownLatch applied = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var payment = executor.submit(() -> transactions.execute(status -> {
                var outcome = results.apply(success);
                applied.countDown(); await(commit); return outcome;
            }));
            assertThat(applied.await(10, TimeUnit.SECONDS)).isTrue();
            clock.set(cart.attempt().expiresAt());
            var expirer = executor.submit(() -> expiry.expireForVariant(cart.facts().items().getLast().variantId()));
            commit.countDown();
            assertThat(payment.get(15, TimeUnit.SECONDS)).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
            expirer.get(15, TimeUnit.SECONDS);
        } finally { commit.countDown(); }
        assertCartState(cart, "PAID", "COMMITTED");
        assertThat(cartBalances(cart)).isEqualTo(before);
    }

    @Test
    void multiItemPaymentAndCancellationAuditFailureRollBackAllLines() {
        CartFlow cart = cartFlow("cart-rollback", 2);
        var before = cartBalances(cart);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> results.apply(cartSuccess(cart))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally { MDC.remove(CorrelationIdFilter.MDC_KEY); }
        assertCartState(cart, "PENDING_PAYMENT", "ADOPTED");
        assertThat(payments.readOwn(cart.customer(), cart.attempt().id()).status()).isEqualTo("PENDING");
        assertThat(cartBalances(cart)).isEqualTo(before);
        results.apply(cartSuccess(cart));
        int calls = voidProvider.calls();
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> cancellations.cancel(cart.customer(), cart.facts().orderId(), "rollback-cancel"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally { MDC.remove(CorrelationIdFilter.MDC_KEY); }
        assertCartState(cart, "PAID", "COMMITTED");
        assertThat(cartBalances(cart)).isEqualTo(before);
        assertThat(voidProvider.calls()).isEqualTo(calls);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_void_operation WHERE order_public_id = ?",
                Integer.class, cart.facts().orderId())).isZero();
        assertCartMovements(cart, "CANCELLATION_RESTORE", 0);
        cancellations.cancel(cart.customer(), cart.facts().orderId(), "rollback-cancel");
        assertCartMovements(cart, "CANCELLATION_RESTORE", 2);
    }

    @Test
    void multiItemVoidResultAndCancellationReplayUseConsistentLockOrder() throws Exception {
        CartFlow cart = cartFlow("cart-replay", 2);
        results.apply(cartSuccess(cart));
        var local = cancellationTransactions.cancel(cart.customer(), cart.facts().orderId(), "replay-cancel");
        assertThat(local.financial().request().amountVnd()).isEqualTo(cart.facts().totalAmount());
        CountDownLatch applied = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch replayStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var result = executor.submit(() -> transactions.execute(status -> {
                orderRepository.findLockedByPublicId(cart.facts().orderId()).orElseThrow();
                applied.countDown(); await(commit);
                return voidResults.apply(cart.facts().orderId(), local.financial().operationId(), local.financial().attemptId(),
                        new VoidProvider.Result(VoidProvider.Outcome.SUCCEEDED, "test", "00", "00", null, "0".repeat(64)));
            }));
            assertThat(applied.await(10, TimeUnit.SECONDS)).isTrue();
            var replay = executor.submit(() -> {
                replayStarted.countDown();
                return cancellations.cancel(cart.customer(), cart.facts().orderId(), "replay-cancel");
            });
            assertThat(replayStarted.await(10, TimeUnit.SECONDS)).isTrue();
            commit.countDown();
            assertThat(result.get(15, TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
            assertThat(replay.get(15, TimeUnit.SECONDS).financialVoid().status()).isEqualTo("SUCCEEDED");
        } finally { commit.countDown(); }
        assertCartAllocations(cart, 1, "SUCCEEDED");
        assertCartMovements(cart, "CANCELLATION_RESTORE", 2);
    }

    @Test
    void multiItemHandoverAndCancellationHaveOneWholeOrderWinner() throws Exception {
        for (boolean handoverWins : new boolean[] {true, false}) {
            CartFlow cart = cartFlow("cart-terminal-" + handoverWins, 2);
            results.apply(cartSuccess(cart));
            var pickup = fulfillments.create(cart.operations(), cart.facts().orderId());
            fulfillments.prepare(cart.operations(), pickup.id());
            CountDownLatch changed = new CountDownLatch(1);
            CountDownLatch commit = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var winner = executor.submit(() -> transactions.execute(status -> {
                    if (handoverWins) fulfillments.handover(cart.operations(), pickup.id(), "cart-handover");
                    else cancellationTransactions.cancel(cart.customer(), cart.facts().orderId(), "cart-cancel");
                    changed.countDown(); await(commit); return true;
                }));
                assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
                var loser = executor.submit(() -> {
                    if (handoverWins) cancellations.cancel(cart.customer(), cart.facts().orderId(), "cart-cancel");
                    else fulfillments.handover(cart.operations(), pickup.id(), "cart-handover");
                });
                commit.countDown();
                assertThat(winner.get(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> loser.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessConflictException.class);
            } finally { commit.countDown(); }
            assertCartState(cart, handoverWins ? "PAID" : "CANCELLED", handoverWins ? "CONSUMED" : "CANCELLED_RESTORED");
            assertCartMovements(cart, "PICKUP_HANDOVER", handoverWins ? 2 : 0);
            assertCartMovements(cart, "CANCELLATION_RESTORE", handoverWins ? 0 : 2);
            assertThat(cartBalances(cart)).allMatch(balance -> balance.reserved() == 0);
        }
    }

    @Test
    void multiItemDeliveryDispatchesAndDeliversExactlyOnce() {
        CartFlow cart = deliveryCartFlow("cart-delivery", 3);
        assertThat(results.apply(cartSuccess(cart))).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        var delivery = fulfillments.create(cart.operations(), cart.facts().orderId());
        assertThat(delivery.type()).isEqualTo("DELIVERY");
        assertThat(delivery.receiverName()).isEqualTo("Nguyen Van A");
        assertThat(fulfillments.startPicking(cart.operations(), delivery.id()).status()).isEqualTo("PICKING");
        assertThat(fulfillments.prepare(cart.operations(), delivery.id()).status()).isEqualTo("PREPARED");

        var dispatched = fulfillments.dispatch(cart.operations(), delivery.id(), "delivery-dispatch");
        var dispatchReplay = fulfillments.dispatch(cart.operations(), delivery.id(), "delivery-dispatch");
        assertThat(dispatched.status()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(dispatchReplay.dispatchedAt()).isEqualTo(dispatched.dispatchedAt());
        assertCartState(cart, "PAID", "CONSUMED");
        assertCartMovements(cart, "DELIVERY_DISPATCH", 3);
        for (int index = 0; index < cart.facts().items().size(); index++) {
            assertThat(cartBalances(cart).get(index).reserved()).isZero();
            assertThat(cartBalances(cart).get(index).onHand())
                    .isEqualTo(8 - cart.facts().items().get(index).quantity());
        }

        var delivered = fulfillments.deliver(cart.operations(), delivery.id(), "delivery-complete");
        assertThat(fulfillments.deliver(cart.operations(), delivery.id(), "delivery-complete").deliveredAt())
                .isEqualTo(delivered.deliveredAt());
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertCartMovements(cart, "DELIVERY_DISPATCH", 3);
        assertThatThrownBy(() -> cancellations.cancel(cart.customer(), cart.facts().orderId(), "late-delivery-cancel"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("Return workflow");
    }

    @Test
    void deliveryCancellationBeforeDispatchRestoresEveryLine() {
        CartFlow cart = deliveryCartFlow("delivery-cancel", 2);
        results.apply(cartSuccess(cart));
        var delivery = fulfillments.create(cart.operations(), cart.facts().orderId());
        fulfillments.prepare(cart.operations(), delivery.id());

        cancellations.cancel(cart.customer(), cart.facts().orderId(), "delivery-cancel");

        assertCartState(cart, "CANCELLED", "CANCELLED_RESTORED");
        assertCartMovements(cart, "CANCELLATION_RESTORE", 2);
        assertCartMovements(cart, "DELIVERY_DISPATCH", 0);
        assertThat(cartBalances(cart)).allMatch(balance -> balance.reserved() == 0 && balance.onHand() == 8);
        assertThatThrownBy(() -> fulfillments.dispatch(cart.operations(), delivery.id(), "late-dispatch"))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void multiItemPickupCorrelationReturnsWhileCancellationWriterHoldsOrderX() throws Exception {
        CartFlow cart = cartFlow("cart-lock", 2);
        assertThat(results.apply(cartSuccess(cart))).isEqualTo(VerifiedPaymentResultService.Result.APPLIED);
        var pickup = fulfillments.create(cart.operations(), cart.facts().orderId());
        fulfillments.prepare(cart.operations(), pickup.id());
        var before = cartBalances(cart);
        var reader = Executors.newSingleThreadExecutor();
        try {
            var local = transactions.execute(status -> {
                // Table X covers every parent index access path; the lookup must touch only Fulfillment.
                Long internalOrderId = jdbc.queryForObject(
                        "SELECT id FROM commerce_order WITH (XLOCK, TABLOCK) WHERE public_id = ?",
                        Long.class, cart.facts().orderId());
                assertThat(jdbc.queryForObject(
                        "SELECT id FROM pickup_fulfillment WITH (UPDLOCK, ROWLOCK) WHERE public_id = ?",
                        Long.class, pickup.id())).isNotNull();
                var lookup = reader.submit(() -> pickupRepository.findOrderId(pickup.id()).orElseThrow());
                assertThat(lookup).succeedsWithin(10, TimeUnit.SECONDS).isEqualTo(internalOrderId);
                var cancellation = cancellationTransactions.cancel(cart.customer(), cart.facts().orderId(), "correlation-cancel");
                orderRepository.flush();
                return cancellation;
            });
            voidProvider.next(VoidProvider.Outcome.SUCCEEDED);
            assertThat(voids.execute(local.financial()).status()).isEqualTo("SUCCEEDED");
            var handover = reader.submit(() -> fulfillments.handover(cart.operations(), pickup.id(), "correlation-handover"));
            assertThatThrownBy(() -> handover.get(10, TimeUnit.SECONDS)).cause()
                    .isInstanceOfSatisfying(BusinessConflictException.class,
                            conflict -> assertThat(conflict.code()).isEqualTo("CANCELLATION_WON"));
        } finally {
            // TransactionTemplate has committed or rolled back before waiting for the reader to exit.
            reader.shutdownNow();
            assertThat(reader.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertCartState(cart, "CANCELLED", "CANCELLED_RESTORED");
        assertCartAllocations(cart, 1, "SUCCEEDED");
        assertCartMovements(cart, "CANCELLATION_RESTORE", 2);
        assertCartMovements(cart, "PICKUP_HANDOVER", 0);
        assertThat(cartBalances(cart)).isEqualTo(before.stream()
                .map(balance -> new Balance(balance.onHand(), 0, balance.onHand())).toList());
    }

    private CartFlow cartFlow(String suffix, int count) {
        return cartFlow(suffix, count, null);
    }

    private CartFlow deliveryCartFlow(String suffix, int count) {
        return cartFlow(suffix, count, new CustomerOrderService.FulfillmentRequest(PickupFulfillment.Type.DELIVERY, null,
                new CustomerOrderService.DeliveryRequest("Nguyen Van A", "+84 912 345 678", "12 Nguyen Hue, Quan 1", null)));
    }

    private CartFlow cartFlow(String suffix, int count, CustomerOrderService.FulfillmentRequest fulfillment) {
        Pending setup = pending(suffix, 8);
        orders.cancelOwn(setup.customer(), setup.order().id());
        // Repricing at the frozen fixture instant would become effective one microsecond later.
        clock.set(clock.instant().plusSeconds(1));
        catalog.setPrice(setup.operations(), setup.variantId(), 1_490_000);
        List<CartQuoteService.LineRequest> lines = new ArrayList<>();
        lines.add(new CartQuoteService.LineRequest(setup.variantId(), 1));
        UUID product = catalog.createProduct(setup.operations(), "Cart companion " + suffix);
        for (int index = 1; index < count; index++) {
            UUID variant = catalog.createVariant(setup.operations(), product, "CART-" + shortId(), "4" + index, "Ink");
            catalog.setPrice(setup.operations(), variant, index == 1 ? 900_000 : 400_000);
            adjustments.adjust(setup.operations(), variant, setup.locationId(), 8, "Test cart fixture", UUID.randomUUID().toString());
            catalog.publish(setup.operations(), variant);
            lines.add(new CartQuoteService.LineRequest(variant, index == 1 ? 2 : 1));
        }
        var quote = cartPricing.quote(setup.customer(), lines);
        var order = fulfillment == null
                ? orders.checkoutCart(setup.customer(), quote.id(), lines, "checkout-cart-" + suffix)
                : orders.checkoutCart(setup.customer(), quote.id(), lines, fulfillment, "checkout-cart-" + suffix);
        var facts = transactions.execute(status -> orderRepository.findLockedByPublicId(order.id()).orElseThrow().paymentFacts());
        var attempt = payments.initiate(setup.customer(), order.id(), "pay-" + suffix).attempt();
        return new CartFlow(setup.customer(), setup.operations(), facts, attempt);
    }

    private PaymentProvider.VerifiedResult cartSuccess(CartFlow cart) {
        return provider.verify(signedCallback(cart.attempt().merchantTransactionReference(),
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), cart.facts().totalAmount(), "00", "00"));
    }

    private List<Balance> cartBalances(CartFlow cart) {
        return cart.facts().items().stream().map(item -> jdbc.queryForObject(
                "SELECT on_hand, reserved, on_hand - reserved FROM inventory_balance WHERE variant_id = (SELECT id FROM catalog_product_variant WHERE public_id = ?) AND location_id = (SELECT id FROM org_location WHERE public_id = ?)",
                (rs, row) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)), item.variantId(), item.locationId())).toList();
    }

    private void assertCartState(CartFlow cart, String orderStatus, String reservationStatus) {
        assertThat(orders.readOwn(cart.customer(), cart.facts().orderId()).status()).isEqualTo(orderStatus);
        assertThat(cart.facts().reservationIds()).allSatisfy(id ->
                assertThat(reservations.readOwn(cart.customer(), id).status()).isEqualTo(reservationStatus));
    }

    private void assertCartAllocations(CartFlow cart, int generation, String status) {
        var rows = jdbc.query("""
                SELECT a.component_public_id, a.amount, a.status FROM payment_void_allocation a
                JOIN payment_void_attempt attempts ON attempts.id = a.void_attempt_id
                JOIN payment_void_operation operations ON operations.id = attempts.void_operation_id
                WHERE operations.order_public_id = ? AND attempts.generation = ?
                """, (rs, row) -> new Component(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3)),
                cart.facts().orderId(), generation);
        assertThat(rows).hasSize(cart.facts().items().size());
        assertThat(rows).containsExactlyInAnyOrderElementsOf(cart.facts().items().stream()
                .map(item -> new Component(item.orderItemId(), item.totalAmount(), status)).toList());
    }

    private void assertCartMovements(CartFlow cart, String type, int expected) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE order_public_id = ? AND operation_type = ?",
                Integer.class, cart.facts().orderId(), type)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT reservation_public_id) FROM inventory_stock_movement WHERE order_public_id = ? AND operation_type = ?",
                Integer.class, cart.facts().orderId(), type)).isEqualTo(expected);
    }

    private record Component(UUID id, long amount, String status) { }
    private record CartFlow(SessionPrincipal customer, SessionPrincipal operations, CustomerOrder.PaymentFacts facts,
            PaymentAttemptService.PaymentAttemptView attempt) { }

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
        adjustments.adjust(operations, variant, location, stock, "Test fixture", UUID.randomUUID().toString());
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
    private int movementTotal(Flow flow) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stock_movement WHERE order_public_id = ?",
                Integer.class, flow.orderId());
    }
    private int voidCount(Flow flow) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_void_operation WHERE order_public_id = ?",
                Integer.class, flow.orderId());
    }
    private int voidAttemptCount(Flow flow) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_void_attempt attempts JOIN payment_void_operation operations ON operations.id = attempts.void_operation_id WHERE operations.order_public_id = ?",
                Integer.class, flow.orderId());
    }
    private String fulfillmentStatus(Flow flow) {
        return jdbc.queryForObject("SELECT status FROM pickup_fulfillment WHERE order_id = (SELECT id FROM commerce_order WHERE public_id = ?)",
                String.class, flow.orderId());
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
