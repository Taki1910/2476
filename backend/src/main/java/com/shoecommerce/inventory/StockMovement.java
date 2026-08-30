package com.shoecommerce.inventory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_stock_movement", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_inventory_stock_movement_operation", columnNames = {"operation_type", "order_public_id"}),
        @UniqueConstraint(name = "UQ_inventory_stock_movement_key", columnNames = "operation_key")
})
public class StockMovement {
    public enum Type { PICKUP_HANDOVER, CANCELLATION_RESTORE, POS_CASH_SALE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 32) private Type type;
    @Column(name = "operation_key", nullable = false, length = 128) private String operationKey;
    @Column(name = "order_public_id", nullable = false) private UUID orderId;
    @Column(name = "reservation_public_id") private UUID reservationId;
    @Column(name = "pos_register_public_id") private UUID registerId;
    @Column(name = "cashier_shift_public_id") private UUID shiftId;
    @Column(name = "variant_public_id", nullable = false) private UUID variantId;
    @Column(name = "location_public_id", nullable = false) private UUID locationId;
    @Column(name = "actor_account_public_id", nullable = false) private UUID actorId;
    @Column(nullable = false) private long quantity;
    @Column(name = "on_hand_delta", nullable = false) private long onHandDelta;
    @Column(name = "reserved_delta", nullable = false) private long reservedDelta;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected StockMovement() { }

    public static StockMovement create(Type type, String operationKey, UUID orderId, UUID actorId,
            InventoryReservationService.FulfillmentStock stock, Instant now) {
        if (type == null || operationKey == null || operationKey.isBlank() || operationKey.length() > 128
                || orderId == null || actorId == null || stock == null) {
            throw new IllegalArgumentException("Stock movement is invalid");
        }
        StockMovement movement = new StockMovement();
        movement.publicId = UUID.randomUUID();
        movement.type = type;
        movement.operationKey = operationKey;
        movement.orderId = orderId;
        movement.reservationId = stock.reservationId();
        movement.variantId = stock.variantId();
        movement.locationId = stock.locationId();
        movement.actorId = actorId;
        movement.quantity = stock.quantity();
        movement.onHandDelta = type == Type.PICKUP_HANDOVER ? -stock.quantity() : 0;
        movement.reservedDelta = -stock.quantity();
        movement.occurredAt = now;
        return movement;
    }

    public static StockMovement createPos(String operationKey, UUID orderId, UUID registerId, UUID shiftId,
            UUID actorId, UUID variantId, UUID locationId, long quantity, Instant now) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128 || orderId == null
                || registerId == null || shiftId == null || actorId == null || variantId == null
                || locationId == null || quantity <= 0 || now == null) {
            throw new IllegalArgumentException("POS stock movement is invalid");
        }
        StockMovement movement = new StockMovement();
        movement.publicId = UUID.randomUUID();
        movement.type = Type.POS_CASH_SALE;
        movement.operationKey = operationKey;
        movement.orderId = orderId;
        movement.registerId = registerId;
        movement.shiftId = shiftId;
        movement.actorId = actorId;
        movement.variantId = variantId;
        movement.locationId = locationId;
        movement.quantity = quantity;
        movement.onHandDelta = -quantity;
        movement.reservedDelta = 0;
        movement.occurredAt = now;
        return movement;
    }

    public UUID publicId() { return publicId; }
}
