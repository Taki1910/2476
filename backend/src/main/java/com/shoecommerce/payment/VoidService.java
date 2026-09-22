package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.shoecommerce.order.OrderPaidComponents;
import com.shoecommerce.order.OrderPaidComponents.PaidComponentKey;
import com.shoecommerce.order.OrderPaidComponents.PaidComponentType;
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
    private final OrderPaidComponents paidComponents;

    VoidService(PaymentRepository payments, PaymentAttemptRepository captureAttempts,
            VoidOperationRepository operations, VoidAttemptRepository attempts,
            VoidAllocationRepository allocations, LocationRepository locations, AuditWriter audit,
            VoidProvider provider, VoidResultService results, Clock clock, OrderPaidComponents paidComponents) {
        this.payments = payments; this.captureAttempts = captureAttempts; this.operations = operations;
        this.attempts = attempts; this.allocations = allocations; this.locations = locations; this.audit = audit;
        this.provider = provider; this.results = results; this.clock = clock;
        this.paidComponents = paidComponents;
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
        var components = expectedComponents(order, VoidAttempt.CalculationVersion.SNAPSHOT_V2, paidComponents);
        requireCapacity(components);
        if (capture.providerTransactionNo() == null || capture.providerPaidAt() == null) {
            throw new BusinessConflictException("CAPTURE_EVIDENCE_INCOMPLETE", "Capture evidence is not sufficient for VNPAY reversal.");
        }
        var now = clock.instant();
        VoidOperation operation = operations.save(VoidOperation.create(payment, order, actor.publicId(), key, now));
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, 1, actor.publicId(), key, now,
                VoidAttempt.CalculationVersion.SNAPSHOT_V2));
        List<VoidAllocation> reserved = allocate(components, operation, attempt, now);
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
        var components = expectedComponents(order, previous.calculationVersion(), paidComponents);
        requireCapacity(components);
        PaymentAttempt capture = captureAttempts.findByPaymentAndStatus(payment, PaymentAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new BusinessConflictException("PAID_CAPTURE_NOT_FOUND", "Successful capture was not found."));
        validateCapture(order, capture);
        var now = clock.instant();
        operation.retry();
        VoidAttempt attempt = attempts.save(VoidAttempt.create(operation, previous.generation() + 1,
                actor.publicId(), key, now, previous.calculationVersion()));
        List<VoidAllocation> reserved = allocate(components, operation, attempt, now);
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
        if (order.items().isEmpty() || itemTotal.compareTo(BigDecimal.valueOf(order.merchandiseAmount())) != 0
                || capture.amount().compareTo(BigDecimal.valueOf(order.totalAmount())) != 0
                || !capture.currency().equals(order.currency())) {
            throw new BusinessConflictException("CAPTURE_AMOUNT_MISMATCH", "Capture must cover all immutable Order components exactly.");
        }
    }

    private void requireCapacity(Map<PaidComponentKey,BigDecimal> components) {
        for (var entry:components.entrySet()) {
            BigDecimal capacity = entry.getValue();
            if (capacity.signum() <= 0 || allocations.usedCapacity(entry.getKey().type().name(), entry.getKey().publicId()).signum() > 0) {
                throw new BusinessConflictException("VOID_CAPACITY_EXCEEDED", "Captured component has no remaining reversal capacity.");
            }
        }
    }

    private List<VoidAllocation> allocate(Map<PaidComponentKey,BigDecimal> components, VoidOperation operation,
            VoidAttempt attempt, java.time.Instant now) {
        List<VoidAllocation> reserved = new ArrayList<>();
        components.forEach((item,amount)->reserved.add(allocations.save(VoidAllocation.create(operation,attempt,item,amount,now))));
        return reserved;
    }

    static Map<PaidComponentKey,BigDecimal> expectedComponents(CustomerOrder.PaymentFacts order,
            VoidAttempt.CalculationVersion version, OrderPaidComponents reader) {
        Map<PaidComponentKey,BigDecimal> result = new LinkedHashMap<>();
        if (version == VoidAttempt.CalculationVersion.SNAPSHOT_V2) {
            reader.read(order.orderId()).forEach(component -> result.put(component.key(), component.amount()));
        } else {
            legacyComponentAmounts(order).forEach((id, amount) -> result.put(new PaidComponentKey(PaidComponentType.ORDER_ITEM, id), amount));
        }
        return result;
    }

    // Historical attempts retain the original allocation rule; never use this for new attempts.
    static Map<UUID,BigDecimal> legacyComponentAmounts(CustomerOrder.PaymentFacts order){
        List<CustomerOrder.ItemFacts> items=order.items().stream().sorted(Comparator.comparing(line->line.orderItemId().toString())).toList();
        long gross=items.stream().mapToLong(CustomerOrder.ItemFacts::totalAmount).sum()+order.shippingFeeAmount();
        long target=order.totalAmount(),used=0;record Part(UUID id,long floor,BigDecimal remainder){}var parts=new ArrayList<Part>();
        for(int i=0;i<items.size();i++){var item=items.get(i);long base=item.totalAmount()+(i==0?order.shippingFeeAmount():0);BigDecimal exact=BigDecimal.valueOf(target).multiply(BigDecimal.valueOf(base)).divide(BigDecimal.valueOf(gross),12,RoundingMode.DOWN);long floor=exact.setScale(0,RoundingMode.DOWN).longValueExact();used+=floor;parts.add(new Part(item.orderItemId(),floor,exact.subtract(BigDecimal.valueOf(floor))));}
        var ranked=parts.stream().sorted(Comparator.comparing(Part::remainder).reversed().thenComparing(p->p.id().toString())).toList();Map<UUID,Long> extra=new java.util.HashMap<>();for(long i=0;i<target-used;i++)extra.merge(ranked.get((int)i).id(),1L,Long::sum);
        Map<UUID,BigDecimal> result=new LinkedHashMap<>();parts.forEach(p->result.put(p.id(),BigDecimal.valueOf(p.floor()+extra.getOrDefault(p.id(),0L))));return result;
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
