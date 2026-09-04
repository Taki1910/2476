package com.shoecommerce.fulfillment;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Branch;
import com.shoecommerce.branch.BranchRepository;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.inventory.StockMovement;
import com.shoecommerce.inventory.StockMovementRepository;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.platform.api.BusinessConflictException;

@Service
public class PickupFulfillmentService {
    private final PickupFulfillmentRepository fulfillments;
    private final CustomerOrderRepository orders;
    private final BranchRepository branches;
    private final LocationRepository locations;
    private final PaymentAttemptService payments;
    private final InventoryReservationService reservations;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final StockMovementRepository movements;
    private final Clock clock;
    private final com.shoecommerce.identity.UserAccountRepository accounts;

    public PickupFulfillmentService(PickupFulfillmentRepository fulfillments, CustomerOrderRepository orders,
            BranchRepository branches, LocationRepository locations, PaymentAttemptService payments,
            InventoryReservationService reservations, AuthorizationPolicy authorization, AuditWriter audit,
            StockMovementRepository movements, Clock clock, com.shoecommerce.identity.UserAccountRepository accounts) {
        this.fulfillments = fulfillments; this.orders = orders; this.branches = branches; this.locations = locations;
        this.payments = payments; this.reservations = reservations; this.authorization = authorization;
        this.audit = audit; this.movements = movements; this.clock = clock;
        this.accounts = accounts;
    }

    @Transactional
    public PickupFulfillmentView create(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        CustomerOrder order = orders.findLockedByPublicId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        authorization.requireLocationAccess(actor, facts.locationId());
        if (!"PAID".equals(order.paymentStatus())) {
            throw new BusinessConflictException("Order is not eligible for pickup fulfillment");
        }
        PickupFulfillment existing = fulfillments.findLockedByOrder(order).orElse(null);
        if (existing != null && order.cartCheckout()) return view(existing);
        if (existing != null) {
            throw new BusinessConflictException("Order already has a pickup fulfillment");
        }

        Branch branch = branches.findByPublicId(facts.responsibleBranchId()).filter(Branch::enabled)
                .orElseThrow(() -> new BusinessConflictException("Order Branch is not eligible for fulfillment"));
        Location location = locations.findByPublicId(facts.locationId()).filter(Location::enabled)
                .orElseThrow(() -> new BusinessConflictException("Order Location is not eligible for fulfillment"));
        if (!location.branchPublicId().equals(branch.publicId())) {
            throw new BusinessConflictException("Order Branch and fulfillment Location are inconsistent");
        }
        payments.requireSucceededForFulfillment(orderId);
        reservations.requireConsumedForFulfillment(facts.reservationIds(), facts.locationId());

        PickupFulfillment fulfillment = fulfillments.save(PickupFulfillment.create(order, branch, location, clock.instant()));
        audit.append(actor, "PICKUP_FULFILLMENT_CREATED", "PICKUP_FULFILLMENT", fulfillment.publicId(),
                branch.id(), location.id(), Map.of("orderId", orderId));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView createIntent(SessionPrincipal actor, CustomerOrder order, Location location,
            PickupFulfillment.Type type, PickupFulfillment.DeliveryDetails delivery, Instant now) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        if (fulfillments.existsByOrder(order)) throw new BusinessConflictException("Order already has a fulfillment intent");
        Branch branch = branches.findByPublicId(location.branchPublicId()).filter(Branch::enabled)
                .orElseThrow(() -> new BusinessConflictException("Fulfillment Branch is not enabled"));
        if (!location.enabled()) throw new BusinessConflictException("Fulfillment Location is not enabled");
        PickupFulfillment fulfillment = fulfillments.save(PickupFulfillment.create(order, branch, location, type, delivery, now));
        audit.append(actor, "FULFILLMENT_INTENT_CREATED", "FULFILLMENT", fulfillment.publicId(),
                branch.id(), location.id(), Map.of("orderId", order.publicId(), "type", type.name()));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView startPicking(SessionPrincipal actor, UUID fulfillmentId) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        Long orderId = fulfillments.findOrderId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Pickup fulfillment not found"));
        orders.findLockedById(orderId).orElseThrow();
        PickupFulfillment fulfillment = fulfillments.findLockedByPublicId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Pickup fulfillment not found"));
        Location location = fulfillment.location();
        authorization.requireLocationAccess(actor, location.publicId());
        if (!fulfillment.pending()) {
            throw new BusinessConflictException("Pickup fulfillment is not pending");
        }

        Branch branch = fulfillment.branch();
        CustomerOrder order = fulfillment.order();
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        if (!branch.enabled() || !location.enabled()
                || !location.branchPublicId().equals(branch.publicId())
                || !facts.responsibleBranchId().equals(branch.publicId())
                || !facts.locationId().equals(location.publicId())) {
            throw new BusinessConflictException("Pickup fulfillment Branch and Location are inconsistent");
        }
        if (!"PAID".equals(order.paymentStatus())) {
            throw new BusinessConflictException("Order is not eligible to start picking");
        }
        payments.requireSucceededForFulfillment(facts.orderId());
        reservations.requireConsumedForFulfillment(facts.reservationIds(), facts.locationId());

        fulfillment.startPicking(clock.instant());
        audit.append(actor, "PICKUP_PICKING_STARTED", "PICKUP_FULFILLMENT", fulfillment.publicId(),
                branch.id(), location.id(), Map.of("orderId", facts.orderId()));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView prepare(SessionPrincipal actor, UUID fulfillmentId) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        Long internalOrderId = fulfillments.findOrderId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Pickup fulfillment not found"));
        CustomerOrder order = orders.findLockedById(internalOrderId).orElseThrow();
        UUID orderId = order.paymentFacts().orderId();
        PickupFulfillment fulfillment = fulfillments.findLockedByPublicId(fulfillmentId).orElseThrow();
        authorization.requireLocationAccess(actor, fulfillment.location().publicId());
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        if (!facts.paid()) throw new BusinessConflictException("ORDER_NOT_PAID", "Only a paid Order can be prepared.");
        reservations.requireConsumedForFulfillment(facts.reservationIds(), facts.locationId());
        try { fulfillment.prepare(actor.publicId(), clock.instant()); }
        catch (IllegalStateException exception) { throw new BusinessConflictException("PICKUP_NOT_PREPARABLE", exception.getMessage()); }
        audit.append(actor, "PICKUP_PREPARED", "PICKUP_FULFILLMENT", fulfillment.publicId(),
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("orderId", orderId));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView handover(SessionPrincipal actor, UUID fulfillmentId, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        validateKey(idempotencyKey);
        // ponytail: serialize one employee's handover keys; use command claims if per-employee throughput requires it.
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow();
        UUID replayId = fulfillments.findHandoverReplayId(actor.publicId(), idempotencyKey).orElse(null);
        if (replayId != null && !replayId.equals(fulfillmentId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This handover key belongs to another pickup.");
        }
        // Resolve only the immutable FK, releasing the child read before locking its parent.
        // A joined correlation query can hold Fulfillment S while waiting for Order X.
        Long internalOrderId = fulfillments.findOrderId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Pickup fulfillment not found"));
        CustomerOrder order = orders.findLockedById(internalOrderId).orElseThrow();
        UUID orderId = order.paymentFacts().orderId();
        PickupFulfillment fulfillment = fulfillments.findLockedByPublicId(fulfillmentId).orElseThrow();
        authorization.requireLocationAccess(actor, fulfillment.location().publicId());
        if (fulfillment.handedOver()) {
            if (replayId != null) return view(fulfillment);
            throw new BusinessConflictException("PICKUP_ALREADY_HANDED_OVER", "Pickup was already handed over.");
        }
        if (fulfillment.cancelled()) {
            throw new BusinessConflictException("CANCELLATION_WON", "Order cancellation already won the fulfillment fence.");
        }
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        if (!facts.paid()) throw new BusinessConflictException("ORDER_NOT_PAID", "Only a paid Order can be handed over.");
        var now = clock.instant();
        var stocks = reservations.handoverCommitted(facts.reservationIds(), now);
        try { fulfillment.handover(actor.publicId(), idempotencyKey, now); }
        catch (IllegalStateException exception) { throw new BusinessConflictException("PICKUP_NOT_READY", exception.getMessage()); }
        String movementKey = "H:" + UUID.nameUUIDFromBytes((actor.publicId() + ":" + idempotencyKey).getBytes());
        for (var stock : stocks) movements.save(StockMovement.create(StockMovement.Type.PICKUP_HANDOVER,
                movementKey + ":" + stock.reservationId(), orderId, actor.publicId(), stock, now));
        audit.append(actor, "PICKUP_HANDED_OVER", "PICKUP_FULFILLMENT", fulfillment.publicId(),
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("orderId", orderId,
                        "items", stocks, "onHandDelta", -facts.quantity(), "reservedDelta", -facts.quantity()));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView dispatch(SessionPrincipal actor, UUID fulfillmentId, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        validateKey(idempotencyKey);
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow();
        UUID replayId = fulfillments.findDispatchReplayId(actor.publicId(), idempotencyKey).orElse(null);
        if (replayId != null && !replayId.equals(fulfillmentId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This dispatch key belongs to another fulfillment.");
        }
        Long internalOrderId = fulfillments.findOrderId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Fulfillment not found"));
        CustomerOrder order = orders.findLockedById(internalOrderId).orElseThrow();
        PickupFulfillment fulfillment = fulfillments.findLockedByPublicId(fulfillmentId).orElseThrow();
        authorization.requireLocationAccess(actor, fulfillment.location().publicId());
        if (fulfillment.dispatched()) {
            if (replayId != null) return view(fulfillment);
            throw new BusinessConflictException("DELIVERY_ALREADY_DISPATCHED", "Delivery was already dispatched.");
        }
        if (fulfillment.cancelled()) {
            throw new BusinessConflictException("CANCELLATION_WON", "Order cancellation already won the fulfillment fence.");
        }
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        if (!facts.paid()) throw new BusinessConflictException("ORDER_NOT_PAID", "Only a paid Order can be dispatched.");
        var now = clock.instant();
        var stocks = reservations.handoverCommitted(facts.reservationIds(), now);
        try { fulfillment.dispatch(actor.publicId(), idempotencyKey, now); }
        catch (IllegalStateException exception) { throw new BusinessConflictException("DELIVERY_NOT_READY", exception.getMessage()); }
        String movementKey = "D:" + UUID.nameUUIDFromBytes((actor.publicId() + ":" + idempotencyKey).getBytes());
        for (var stock : stocks) movements.save(StockMovement.create(StockMovement.Type.DELIVERY_DISPATCH,
                movementKey + ":" + stock.reservationId(), facts.orderId(), actor.publicId(), stock, now));
        audit.append(actor, "DELIVERY_DISPATCHED", "FULFILLMENT", fulfillment.publicId(),
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("orderId", facts.orderId(),
                        "items", stocks, "onHandDelta", -facts.quantity(), "reservedDelta", -facts.quantity()));
        return view(fulfillment);
    }

    @Transactional
    public PickupFulfillmentView deliver(SessionPrincipal actor, UUID fulfillmentId, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        validateKey(idempotencyKey);
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow();
        UUID replayId = fulfillments.findDeliveryReplayId(actor.publicId(), idempotencyKey).orElse(null);
        if (replayId != null && !replayId.equals(fulfillmentId)) {
            throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This delivery key belongs to another fulfillment.");
        }
        Long internalOrderId = fulfillments.findOrderId(fulfillmentId)
                .orElseThrow(() -> new IllegalArgumentException("Fulfillment not found"));
        CustomerOrder order = orders.findLockedById(internalOrderId).orElseThrow();
        PickupFulfillment fulfillment = fulfillments.findLockedByPublicId(fulfillmentId).orElseThrow();
        authorization.requireLocationAccess(actor, fulfillment.location().publicId());
        if (fulfillment.delivered()) {
            if (replayId != null) return view(fulfillment);
            throw new BusinessConflictException("DELIVERY_ALREADY_COMPLETED", "Delivery was already completed.");
        }
        if (!order.paymentFacts().paid()) throw new BusinessConflictException("ORDER_NOT_PAID", "Only a paid Order can be delivered.");
        try { fulfillment.deliver(actor.publicId(), idempotencyKey, clock.instant()); }
        catch (IllegalStateException exception) { throw new BusinessConflictException("DELIVERY_NOT_DISPATCHED", exception.getMessage()); }
        audit.append(actor, "DELIVERY_COMPLETED", "FULFILLMENT", fulfillment.publicId(),
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("orderId", order.publicId()));
        return view(fulfillment);
    }

    public record PickupFulfillmentView(UUID id, UUID orderId, UUID branchId, UUID locationId, String type,
            String status, Instant createdAt, Instant pickingStartedAt, Instant preparedAt, Instant handedOverAt,
            Instant dispatchedAt, Instant deliveredAt, Instant cancelledAt, String receiverName,
            String receiverPhone, String deliveryAddress, String deliveryNote, long deliveryFeeAmount) { }

    private static PickupFulfillmentView view(PickupFulfillment fulfillment) {
        return new PickupFulfillmentView(fulfillment.publicId(), fulfillment.order().paymentFacts().orderId(),
                fulfillment.branch().publicId(), fulfillment.location().publicId(), fulfillment.type(), fulfillment.status(),
                fulfillment.createdAt(), fulfillment.pickingStartedAt(), fulfillment.preparedAt(),
                fulfillment.handedOverAt(), fulfillment.dispatchedAt(), fulfillment.deliveredAt(),
                fulfillment.cancelledAt(), fulfillment.receiverName(), fulfillment.receiverPhone(),
                fulfillment.deliveryAddress(), fulfillment.deliveryNote(), fulfillment.deliveryFeeAmount());
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 128 characters");
        }
    }
}
