package com.shoecommerce.fulfillment;

import java.time.Clock;
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
import com.shoecommerce.payment.VoidService;
import com.shoecommerce.platform.api.BusinessConflictException;

@Service
public class PickupCancellationTransactionService {
    private final CustomerOrderRepository orders;
    private final PickupFulfillmentRepository fulfillments;
    private final BranchRepository branches;
    private final LocationRepository locations;
    private final AuthorizationPolicy authorization;
    private final VoidService voids;
    private final InventoryReservationService reservations;
    private final StockMovementRepository movements;
    private final AuditWriter audit;
    private final Clock clock;

    PickupCancellationTransactionService(CustomerOrderRepository orders, PickupFulfillmentRepository fulfillments,
            BranchRepository branches, LocationRepository locations, AuthorizationPolicy authorization,
            VoidService voids, InventoryReservationService reservations, StockMovementRepository movements,
            AuditWriter audit, Clock clock) {
        this.orders = orders; this.fulfillments = fulfillments; this.branches = branches; this.locations = locations;
        this.authorization = authorization; this.voids = voids; this.reservations = reservations;
        this.movements = movements; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public Result cancel(SessionPrincipal actor, UUID orderId, String key) {
        VoidService.Reservation replay = voids.replay(actor, orderId, key);
        if (replay != null) {
            CustomerOrder replayOrder = orders.findLockedByPublicId(orderId).orElseThrow();
            authorize(actor, replayOrder.paymentFacts());
            PickupFulfillment replayFulfillment = fulfillments.findLockedByOrder(replayOrder).orElseThrow();
            return new Result(replay, replayFulfillment.publicId());
        }

        CustomerOrder order = orders.findLockedByPublicId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        authorize(actor, facts);
        if (!facts.paid()) {
            throw new BusinessConflictException("ORDER_NOT_CONFIRMED_CANCELLABLE", "Only a paid pre-handover Order can use confirmed cancellation.");
        }
        PickupFulfillment fulfillment = fulfillments.findLockedByOrder(order).orElse(null);
        if (fulfillment != null && fulfillment.handedOver()) {
            throw new BusinessConflictException("PICKUP_ALREADY_HANDED_OVER", "A handed-over Order requires the future Return workflow.");
        }
        if (fulfillment == null) {
            Branch branch = branches.findByPublicId(facts.responsibleBranchId()).filter(Branch::enabled).orElseThrow();
            Location location = locations.findByPublicId(facts.locationId()).filter(Location::enabled).orElseThrow();
            fulfillment = fulfillments.save(PickupFulfillment.create(order, branch, location, clock.instant()));
        }

        VoidService.Reservation financial = voids.reserve(actor, facts, key);
        var now = clock.instant();
        fulfillment.cancel(actor.publicId(), now);
        order.cancelPaid(now);
        InventoryReservationService.FulfillmentStock stock = reservations.restoreCommittedCancellation(facts.reservationId(), now);
        movements.save(StockMovement.create(StockMovement.Type.CANCELLATION_RESTORE,
                "C:" + financial.operationId(), orderId, actor.publicId(), stock, now));
        audit.append(actor, "ORDER_CANCELLATION_ACCEPTED", "ORDER", orderId,
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("fulfillmentId", fulfillment.publicId(),
                        "voidOperationId", financial.operationId(), "onHandDelta", 0,
                        "reservedDelta", -stock.quantity()));
        audit.append(actor, "CANCELLATION_RESTORE", "INVENTORY_STOCK_MOVEMENT", orderId,
                fulfillment.branch().id(), fulfillment.location().id(), Map.of("reservationId", stock.reservationId(),
                        "quantity", stock.quantity(), "onHandDelta", 0, "reservedDelta", -stock.quantity()));
        return new Result(financial, fulfillment.publicId());
    }

    @Transactional
    public VoidService.Reservation retry(SessionPrincipal actor, UUID orderId, String key) {
        VoidService.Reservation replay = voids.retryReplay(actor, orderId, key);
        if (replay != null) {
            CustomerOrder replayOrder = orders.findLockedByPublicId(orderId).orElseThrow();
            authorize(actor, replayOrder.paymentFacts());
            PickupFulfillment replayFulfillment = fulfillments.findLockedByOrder(replayOrder).orElseThrow();
            if (!replayFulfillment.cancelled()) throw new BusinessConflictException("VOID_RETRY_BLOCKED", "Order is not cancelled.");
            return replay;
        }
        CustomerOrder order = orders.findLockedByPublicId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        CustomerOrder.PaymentFacts facts = order.paymentFacts();
        authorize(actor, facts);
        PickupFulfillment fulfillment = fulfillments.findLockedByOrder(order)
                .orElseThrow(() -> new BusinessConflictException("CANCELLATION_NOT_FOUND", "Order has no confirmed cancellation."));
        if (!fulfillment.cancelled() || !"CANCELLED".equals(order.paymentStatus())) {
            throw new BusinessConflictException("VOID_RETRY_BLOCKED", "Only an operationally cancelled Order may retry financial reversal.");
        }
        return voids.retry(actor, facts, key);
    }

    private void authorize(SessionPrincipal actor, CustomerOrder.PaymentFacts facts) {
        if (actor.publicId().equals(facts.ownerAccountId())) {
            authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
            return;
        }
        authorization.requirePermission(actor, PermissionCode.ORDER_CANCEL);
        authorization.requireLocationAccess(actor, facts.locationId());
    }

    public record Result(VoidService.Reservation financial, UUID fulfillmentId) { }
}
