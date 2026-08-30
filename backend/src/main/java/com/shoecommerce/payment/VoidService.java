package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.platform.api.BusinessConflictException;

@Service
public class VoidService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository captureAttempts;
    private final VoidOperationRepository operations;
    private final VoidAttemptRepository attempts;
    private final VoidAllocationRepository allocations;
    private final LocationRepository locations;
    private final AuditWriter audit;
    private final VoidProvider provider;
    private final VoidResultService results;
    private final Clock clock;

    VoidService(PaymentRepository payments, PaymentAttemptRepository captureAttempts,
            VoidOperationRepository operations, VoidAttemptRepository attempts,
            VoidAllocationRepository allocations, LocationRepository locations, AuditWriter audit,
            VoidProvider provider, VoidResultService results, Clock clock) {
        this.payments = payments; this.captureAttempts = captureAttempts; this.operations = operations;
        this.attempts = attempts; this.allocations = allocations; this.locations = locations; this.audit = audit;
        this.provider = provider; this.results = results; this.clock = clock;
    }

    @Transactional
    public Reservation reserve(SessionPrincipal actor, CustomerOrder.PaymentFacts order, String key) {
        VoidOperation scoped = operations.findScopedForUpdate(actor.publicId(), key).orElse(null);
        if (scoped != null) {
            if (!scoped.orderPublicId().equals(order.orderId())) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
            }
            return replay(scoped);
        }
        VoidOperation existing = operations.findByOrderPublicId(order.orderId()).orElse(null);
        if (existing != null) return replay(existing);

        Payment payment = payments.findLockedByOrderId(order.orderId())
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Paid capture was not found."));
        PaymentAttempt capture = captureAttempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Successful capture was not found."));
        BigDecimal amount = BigDecimal.valueOf(order.totalAmount());
        BigDecimal used = allocations.usedCapacity(order.orderItemId());
        if (used.add(amount).compareTo(amount) > 0) {
            throw new BusinessConflictException("VOID_CAPACITY_EXCEEDED", "Captured component has no remaining reversal capacity.");
        }
        if (capture.providerTransactionNo() == null || capture.providerPaidAt() == null) {
            throw new BusinessConflictException("CAPTURE_EVIDENCE_INCOMPLETE", "Capture evidence is not sufficient for VNPAY reversal.");
        }
        var now = clock.instant();
        VoidOperation operation = operations.save(VoidOperation.create(payment, order, actor.publicId(), key, now));
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, 1, actor.publicId(), key, now));
        VoidAllocation allocation = allocations.save(VoidAllocation.create(operation, attempt, order.orderItemId(), amount, now));
        Location location = locations.findByPublicId(order.locationId()).orElseThrow();
        audit.append(actor, "VOID_INITIATED", "PAYMENT_VOID_OPERATION", operation.publicId(),
                location.branchId(), location.id(), Map.of("orderId", order.orderId(), "attemptId", attempt.publicId(),
                        "generation", 1, "amount", amount, "currency", order.currency()));
        VoidProvider.Request request = new VoidProvider.Request(attempt.merchantRequestReference(),
                capture.merchantTransactionReference(), capture.providerTransactionNo(), capture.providerPaidAt(),
                amount.longValueExact(), now);
        return new Reservation(view(operation, attempt, List.of(allocation)), true,
                operation.publicId(), attempt.publicId(), request);
    }

    public VoidView execute(Reservation reservation) {
        if (!reservation.dispatch()) return reservation.view();
        try {
            VoidProvider.Result result = provider.reverse(reservation.request());
            return results.apply(reservation.view().orderId(), reservation.operationId(), reservation.attemptId(), result);
        } catch (RuntimeException exception) {
            return results.unknown(reservation.view().orderId(), reservation.operationId(), reservation.attemptId());
        }
    }

    @Transactional(readOnly = true)
    public VoidView findByOrder(UUID orderId) {
        return operations.findByOrderPublicId(orderId).map(this::replay).map(Reservation::view).orElse(null);
    }

    @Transactional
    public Reservation replay(SessionPrincipal actor, UUID orderId, String key) {
        VoidOperation operation = operations.findScopedForUpdate(actor.publicId(), key).orElse(null);
        if (operation == null) return null;
        if (!operation.orderPublicId().equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
        }
        return replay(operation);
    }

    @Transactional
    public Reservation retryReplay(SessionPrincipal actor, UUID orderId, String key) {
        VoidAttempt attempt = attempts.findScopedForUpdate(actor.publicId(), key).orElse(null);
        if (attempt == null) return null;
        if (!attempt.operation().orderPublicId().equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
        }
        return replay(attempt.operation());
    }

    @Transactional
    public Reservation retry(SessionPrincipal actor, CustomerOrder.PaymentFacts order, String key) {
        Payment payment = payments.findLockedByOrderId(order.orderId()).orElseThrow();
        VoidOperation existing = operations.findByOrderPublicId(order.orderId())
                .orElseThrow(() -> new BusinessConflictException("VOID_NOT_FOUND", "Cancellation has no financial Void operation."));
        VoidOperation operation = operations.findLockedByPublicId(existing.publicId()).orElseThrow();
        VoidAttempt previous = attempts.findFirstByOperationOrderByGenerationDesc(operation).orElseThrow();
        if (!operation.failedRetryable()) {
            throw new BusinessConflictException("VOID_RETRY_BLOCKED", "Only a definitively failed Void may be retried; unknown outcomes require reconciliation.");
        }
        BigDecimal amount = BigDecimal.valueOf(order.totalAmount());
        if (allocations.usedCapacity(order.orderItemId()).add(amount).compareTo(amount) > 0) {
            throw new BusinessConflictException("VOID_CAPACITY_EXCEEDED", "Captured component has no remaining reversal capacity.");
        }
        PaymentAttempt capture = captureAttempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Successful capture was not found."));
        var now = clock.instant();
        operation.retry();
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, previous.generation() + 1,
                actor.publicId(), key, now));
        VoidAllocation allocation = allocations.save(VoidAllocation.create(operation, attempt,
                order.orderItemId(), amount, now));
        Location location = locations.findByPublicId(order.locationId()).orElseThrow();
        audit.append(actor, "VOID_RETRY_INITIATED", "PAYMENT_VOID_OPERATION", operation.publicId(),
                location.branchId(), location.id(), Map.of("orderId", order.orderId(), "attemptId", attempt.publicId(),
                        "generation", attempt.generation(), "amount", amount));
        var request = new VoidProvider.Request(attempt.merchantRequestReference(),
                capture.merchantTransactionReference(), capture.providerTransactionNo(), capture.providerPaidAt(),
                amount.longValueExact(), now);
        return new Reservation(view(operation, attempt, List.of(allocation)), true,
                operation.publicId(), attempt.publicId(), request);
    }

    private Reservation replay(VoidOperation operation) {
        VoidAttempt attempt = attempts.findFirstByOperationOrderByGenerationDesc(operation).orElseThrow();
        return new Reservation(view(operation, attempt, allocations.findAllByAttempt(attempt)), false,
                operation.publicId(), attempt.publicId(), null);
    }

    static VoidView view(VoidOperation operation, VoidAttempt attempt, List<VoidAllocation> allocations) {
        return new VoidView(operation.publicId(), operation.orderPublicId(), operation.status(),
                operation.requestedAmount(), operation.currency(), attempt.publicId(), attempt.generation(),
                attempt.status(), allocations.stream().map(VoidAllocation::status).distinct().toList(),
                operation.createdAt(), operation.resolvedAt());
    }

    public record Reservation(VoidView view, boolean dispatch, UUID operationId, UUID attemptId,
            VoidProvider.Request request) { }
    public record VoidView(UUID id, UUID orderId, String status, BigDecimal amount, String currency,
            UUID attemptId, int generation, String attemptStatus, List<String> allocationStatuses,
            java.time.Instant createdAt, java.time.Instant resolvedAt) { }
}
