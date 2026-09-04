package com.shoecommerce.payment;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;

@Service
class VoidResultService {
    private final CustomerOrderRepository orders;
    private final PaymentRepository payments;
    private final VoidOperationRepository operations;
    private final VoidAttemptRepository attempts;
    private final VoidAllocationRepository allocations;
    private final LocationRepository locations;
    private final AuditWriter audit;
    private final Clock clock;

    VoidResultService(CustomerOrderRepository orders, PaymentRepository payments,
            VoidOperationRepository operations, VoidAttemptRepository attempts,
            VoidAllocationRepository allocations, LocationRepository locations,
            AuditWriter audit, Clock clock) {
        this.orders = orders; this.payments = payments; this.operations = operations; this.attempts = attempts;
        this.allocations = allocations; this.locations = locations; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public VoidService.VoidView apply(UUID orderId, UUID operationId, UUID attemptId, VoidProvider.Result result) {
        CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow();
        Payment payment = payments.findLockedByOrderId(orderId).orElseThrow();
        VoidOperation operation = operations.findLockedByPublicId(operationId).orElseThrow();
        VoidAttempt attempt = attempts.findLockedByPublicId(attemptId).orElseThrow();
        if (operation.payment() != payment || attempt.operation() != operation || !operation.processing()) {
            throw new IllegalStateException("Void result does not match the active operation");
        }
        List<VoidAllocation> reserved = allocations.findLockedByAttempt(attempt);
        if (reserved.isEmpty() || reserved.stream().anyMatch(allocation -> !"ACTIVE".equals(allocation.status()))) {
            throw new IllegalStateException("Void attempt has no active component capacity");
        }
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        var expected = facts.items().stream().collect(Collectors.toMap(CustomerOrder.ItemFacts::orderItemId,
                item -> BigDecimal.valueOf(item.totalAmount())));
        BigDecimal allocated = reserved.stream().map(VoidAllocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reserved.size() != expected.size()
                || reserved.stream().map(VoidAllocation::componentPublicId).distinct().count() != expected.size()
                || reserved.stream().anyMatch(allocation -> !expected.containsKey(allocation.componentPublicId())
                    || expected.get(allocation.componentPublicId()).compareTo(allocation.amount()) != 0)
                || allocated.compareTo(attempt.amount()) != 0
                || allocated.compareTo(operation.requestedAmount()) != 0
                || allocated.compareTo(BigDecimal.valueOf(facts.totalAmount())) != 0) {
            throw new IllegalStateException("Void allocations do not cover the complete Order snapshot");
        }
        Instant now = clock.instant();
        attempt.apply(result, now);
        switch (result.outcome()) {
            case SUCCEEDED -> { reserved.forEach(allocation -> allocation.succeed(now)); operation.succeed(now); }
            case DEFINITIVE_FAILED -> { reserved.forEach(allocation -> allocation.release(now)); operation.fail(now); }
            case UNKNOWN -> operation.unknown(now);
            case REVIEW_REQUIRED -> operation.requireReview(now);
        }
        Location location = locations.findByPublicId(facts.locationId()).orElseThrow();
        String action = switch (result.outcome()) {
            case SUCCEEDED -> "VOID_SUCCEEDED";
            case DEFINITIVE_FAILED -> "VOID_FAILED";
            case UNKNOWN -> "VOID_UNKNOWN";
            case REVIEW_REQUIRED -> "VOID_REVIEW_REQUIRED";
        };
        audit.appendIntegration("VNPAY", action, "PAYMENT_VOID_OPERATION", operation.publicId(),
                location.branchId(), location.id(), Map.of("orderId", orderId, "attemptId", attempt.publicId(),
                        "generation", attempt.generation(), "responseCode", safe(result.responseCode()),
                        "transactionStatus", safe(result.transactionStatus()), "amount", attempt.amount()));
        return VoidService.view(operation, attempt, reserved);
    }

    @Transactional
    public VoidService.VoidView unknown(UUID orderId, UUID operationId, UUID attemptId) {
        return apply(orderId, operationId, attemptId,
                new VoidProvider.Result(VoidProvider.Outcome.UNKNOWN, null, null, null, null, null));
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
