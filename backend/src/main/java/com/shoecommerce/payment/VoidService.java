package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
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
        Payment payment = payments.findLockedByOrderId(order.orderId())
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Paid capture was not found."));
        VoidOperation scoped = operations.findByActorAccountPublicIdAndIdempotencyKey(actor.publicId(), key).orElse(null);
        if (scoped != null) {
            if (!scoped.orderPublicId().equals(order.orderId())) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
            }
            return lockedReplay(scoped);
        }
        VoidOperation existing = operations.findByOrderPublicId(order.orderId()).orElse(null);
        if (existing != null) return lockedReplay(existing);
        PaymentAttempt capture = captureAttempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Successful capture was not found."));
        BigDecimal amount = BigDecimal.valueOf(order.totalAmount());
        validateCapture(order, capture);
        requireCapacity(order);
        if (capture.providerTransactionNo() == null || capture.providerPaidAt() == null) {
            throw new BusinessConflictException("CAPTURE_EVIDENCE_INCOMPLETE", "Capture evidence is not sufficient for VNPAY reversal.");
        }
        var now = clock.instant();
        VoidOperation operation = operations.save(VoidOperation.create(payment, order, actor.publicId(), key, now));
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, 1, actor.publicId(), key, now));
        List<VoidAllocation> reserved = allocate(order, operation, attempt, now);
        Location location = locations.findByPublicId(order.locationId()).orElseThrow();
        audit.append(actor, "VOID_INITIATED", "PAYMENT_VOID_OPERATION", operation.publicId(),
                location.branchId(), location.id(), Map.of("orderId", order.orderId(), "attemptId", attempt.publicId(),
                        "generation", 1, "amount", amount, "currency", order.currency()));
        VoidProvider.Request request = new VoidProvider.Request(attempt.merchantRequestReference(),
                capture.merchantTransactionReference(), capture.providerTransactionNo(), capture.providerPaidAt(),
                amount.longValueExact(), now);
        return new Reservation(view(operation, attempt, reserved), true,
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
        payments.findLockedByOrderId(orderId);
        VoidOperation operation = operations.findByActorAccountPublicIdAndIdempotencyKey(actor.publicId(), key).orElse(null);
        if (operation == null) return null;
        if (!operation.orderPublicId().equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
        }
        return lockedReplay(operation);
    }

    @Transactional
    public Reservation retryReplay(SessionPrincipal actor, UUID orderId, String key) {
        payments.findLockedByOrderId(orderId);
        VoidAttempt attempt = attempts.findByActorAccountPublicIdAndIdempotencyKey(actor.publicId(), key).orElse(null);
        if (attempt == null) return null;
        if (!attempt.operation().orderPublicId().equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
        }
        return lockedReplay(attempt.operation());
    }

    public void requireMatchingReplayOrder(SessionPrincipal actor, UUID orderId, String key, boolean retry) {
        UUID existingOrderId = (retry ? attempts.findScopedOrderId(actor.publicId(), key)
                : operations.findScopedOrderId(actor.publicId(), key)).orElse(null);
        if (existingOrderId != null && !existingOrderId.equals(orderId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key belongs to another Order.");
        }
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
        requireCapacity(order);
        PaymentAttempt capture = captureAttempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Successful capture was not found."));
        validateCapture(order, capture);
        var now = clock.instant();
        operation.retry();
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, previous.generation() + 1,
                actor.publicId(), key, now));
        List<VoidAllocation> reserved = allocate(order, operation, attempt, now);
        Location location = locations.findByPublicId(order.locationId()).orElseThrow();
        audit.append(actor, "VOID_RETRY_INITIATED", "PAYMENT_VOID_OPERATION", operation.publicId(),
                location.branchId(), location.id(), Map.of("orderId", order.orderId(), "attemptId", attempt.publicId(),
                        "generation", attempt.generation(), "amount", amount));
        var request = new VoidProvider.Request(attempt.merchantRequestReference(),
                capture.merchantTransactionReference(), capture.providerTransactionNo(), capture.providerPaidAt(),
                amount.longValueExact(), now);
        return new Reservation(view(operation, attempt, reserved), true,
                operation.publicId(), attempt.publicId(), request);
    }

    private Reservation replay(VoidOperation operation) {
        VoidAttempt attempt = attempts.findFirstByOperationOrderByGenerationDesc(operation).orElseThrow();
        return new Reservation(view(operation, attempt, allocations.findAllByAttempt(attempt)), false,
                operation.publicId(), attempt.publicId(), null);
    }

    private Reservation lockedReplay(VoidOperation existing) {
        VoidOperation operation = operations.findLockedByPublicId(existing.publicId()).orElseThrow();
        VoidAttempt latest = attempts.findFirstByOperationOrderByGenerationDesc(operation).orElseThrow();
        VoidAttempt attempt = attempts.findLockedByPublicId(latest.publicId()).orElseThrow();
        return new Reservation(view(operation, attempt, allocations.findLockedByAttempt(attempt)), false,
                operation.publicId(), attempt.publicId(), null);
    }

    private static void validateCapture(CustomerOrder.PaymentFacts order, PaymentAttempt capture) {
        BigDecimal itemTotal = order.items().stream().map(item -> BigDecimal.valueOf(item.totalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (order.items().isEmpty() || itemTotal.compareTo(BigDecimal.valueOf(order.totalAmount())) != 0
                || capture.amount().compareTo(itemTotal) != 0 || !capture.currency().equals(order.currency())) {
            throw new BusinessConflictException("CAPTURE_AMOUNT_MISMATCH", "Capture must cover all immutable Order components exactly.");
        }
    }

    private void requireCapacity(CustomerOrder.PaymentFacts order) {
        for (var item : order.items().stream().sorted(Comparator.comparing(line -> line.orderItemId().toString())).toList()) {
            BigDecimal capacity = BigDecimal.valueOf(item.totalAmount());
            if (capacity.signum() <= 0 || allocations.usedCapacity(item.orderItemId()).add(capacity).compareTo(capacity) > 0) {
                throw new BusinessConflictException("VOID_CAPACITY_EXCEEDED", "Captured component has no remaining reversal capacity.");
            }
        }
    }

    private List<VoidAllocation> allocate(CustomerOrder.PaymentFacts order, VoidOperation operation,
            VoidAttempt attempt, java.time.Instant now) {
        List<VoidAllocation> reserved = new ArrayList<>();
        for (var item : order.items().stream().sorted(Comparator.comparing(line -> line.orderItemId().toString())).toList()) {
            reserved.add(allocations.save(VoidAllocation.create(operation, attempt, item.orderItemId(),
                    BigDecimal.valueOf(item.totalAmount()), now)));
        }
        return reserved;
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
