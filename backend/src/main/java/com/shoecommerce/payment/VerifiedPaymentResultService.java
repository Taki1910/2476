package com.shoecommerce.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;

@Service
public class VerifiedPaymentResultService {
    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final LocationRepository locations;
    private final AuditWriter audit;
    private final Clock clock;

    VerifiedPaymentResultService(PaymentAttemptRepository attempts, PaymentRepository payments,
            CustomerOrderRepository orders, InventoryReservationService reservations,
            LocationRepository locations, AuditWriter audit, Clock clock) {
        this.attempts = attempts; this.payments = payments; this.orders = orders;
        this.reservations = reservations; this.locations = locations; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public Result apply(PaymentProvider.VerifiedResult result) {
        UUID attemptId = attempts.findPublicIdByMerchantReference(result.merchantReference()).orElse(null);
        if (attemptId == null) return Result.NOT_FOUND;
        UUID orderId = attempts.findOrderPublicId(attemptId).orElse(null);
        if (orderId == null) return Result.NOT_FOUND;

        CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow();
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        Payment payment = payments.findLockedByOrderId(orderId).orElseThrow();
        PaymentAttempt attempt = attempts.findLockedByPublicId(attemptId).orElseThrow();

        if (!"VNPAY".equals(attempt.provider())
                || !attempt.merchantTransactionReference().equals(result.merchantReference())) {
            return Result.NOT_FOUND;
        }
        if (!"VND".equals(attempt.currency())
                || attempt.amount().longValueExact() != result.amountVnd()
                || facts.totalAmount() != result.amountVnd()) {
            return Result.AMOUNT_MISMATCH;
        }
        if (result.providerTransactionNo() != null
                && attempts.existsByProviderAndProviderTransactionNoAndPublicIdNot(
                        attempt.provider(), result.providerTransactionNo(), attempt.publicId())) {
            return Result.ALREADY_PROCESSED;
        }
        if (attempt.sameProviderResult(result)) return Result.ALREADY_PROCESSED;

        Instant now = clock.instant();
        Location location = locations.findByPublicId(facts.locationId()).orElseThrow();
        if (!result.successful()) {
            if (!attempt.pending() || !facts.pendingPayment()) return Result.ALREADY_PROCESSED;
            attempt.applyFailure(result, now);
            audit(location, attempt, orderId, "PAYMENT_FAILED", result);
            return Result.APPLIED;
        }

        if (attempt.succeeded()) return Result.ALREADY_PROCESSED;
        if (!attempt.pending() || !facts.pendingPayment()) {
            attempt.requireReview(result, now);
            audit(location, attempt, orderId, "PAYMENT_REVIEW_REQUIRED", result);
            return Result.APPLIED;
        }

        InventoryReservationService.PaymentCommit commitment =
                reservations.commitForSuccessfulPayment(facts.reservationIds(), now);
        if (commitment == InventoryReservationService.PaymentCommit.COMMITTED) {
            attempt.applySuccess(result, now);
            order.markPaid(now);
            audit(location, attempt, orderId, "PAYMENT_SUCCEEDED", result);
            return Result.APPLIED;
        }

        if (order.paymentFacts().pendingPayment()) order.expire(now);
        attempt.requireReview(result, now);
        audit(location, attempt, orderId, "PAYMENT_REVIEW_REQUIRED", result);
        return Result.APPLIED;
    }

    private void audit(Location location, PaymentAttempt attempt, UUID orderId, String action,
            PaymentProvider.VerifiedResult result) {
        audit.appendIntegration("VNPAY", action, "PAYMENT_ATTEMPT", attempt.publicId(),
                location.branchId(), location.id(), Map.of(
                        "orderId", orderId,
                        "merchantReference", attempt.merchantTransactionReference(),
                        "providerTransactionNo", result.providerTransactionNo() == null ? "" : result.providerTransactionNo(),
                        "responseCode", result.responseCode(),
                        "transactionStatus", result.transactionStatus(),
                        "amount", attempt.amount(), "currency", attempt.currency(),
                        "paymentAttemptStatus", attempt.status()));
    }

    public enum Result { APPLIED, ALREADY_PROCESSED, NOT_FOUND, AMOUNT_MISMATCH }
}
