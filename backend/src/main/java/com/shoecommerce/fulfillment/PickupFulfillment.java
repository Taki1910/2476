package com.shoecommerce.fulfillment;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import com.shoecommerce.branch.Branch;
import com.shoecommerce.branch.Location;
import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

/** Generalized Order Fulfillment; the historical name remains for migration compatibility. */
@Entity
@Table(name = "pickup_fulfillment")
public class PickupFulfillment {
    enum Status { PENDING, PICKING, PREPARED, OUT_FOR_DELIVERY, DELIVERED, HANDED_OVER, CANCELLED }
    enum Channel { ONLINE, POS }
    public enum Type { PICKUP, DELIVERY }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true) private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "branch_id", nullable = false) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id", nullable = false) private Location location;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Channel channel;
    @Enumerated(EnumType.STRING) @Column(name = "fulfillment_type", nullable = false, length = 16) private Type type;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "picking_started_at") private Instant pickingStartedAt;
    @Column(name = "prepared_at") private Instant preparedAt;
    @Column(name = "prepared_by_account_public_id") private UUID preparedByAccountPublicId;
    @Column(name = "handed_over_at") private Instant handedOverAt;
    @Column(name = "handed_over_by_account_public_id") private UUID handedOverByAccountPublicId;
    @Column(name = "handover_idempotency_key", length = 128) private String handoverIdempotencyKey;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancelled_by_account_public_id") private UUID cancelledByAccountPublicId;
    @Nationalized @Column(name = "receiver_name", length = 120) private String receiverName;
    @Column(name = "receiver_phone", length = 32) private String receiverPhone;
    @Nationalized @Column(name = "delivery_address", length = 500) private String deliveryAddress;
    @Nationalized @Column(name = "delivery_note", length = 500) private String deliveryNote;
    @Column(name = "delivery_fee_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal deliveryFeeAmount;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "dispatched_by_account_public_id") private UUID dispatchedByAccountPublicId;
    @Column(name = "dispatch_idempotency_key", length = 128) private String dispatchIdempotencyKey;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "delivered_by_account_public_id") private UUID deliveredByAccountPublicId;
    @Column(name = "delivery_idempotency_key", length = 128) private String deliveryIdempotencyKey;

    protected PickupFulfillment() { }

    static PickupFulfillment create(CustomerOrder order, Branch branch, Location location, Instant now) {
        return create(order, branch, location, Type.PICKUP, null, now);
    }

    public static PickupFulfillment create(CustomerOrder order, Branch branch, Location location, Type type,
            DeliveryDetails delivery, Instant now) {
        if (order == null || branch == null || location == null || type == null || now == null
                || (type == Type.DELIVERY) != (delivery != null)) {
            throw new IllegalArgumentException("Fulfillment intent is invalid");
        }
        PickupFulfillment fulfillment = new PickupFulfillment();
        fulfillment.publicId = UUID.randomUUID();
        fulfillment.order = order;
        fulfillment.branch = branch;
        fulfillment.location = location;
        fulfillment.status = Status.PENDING;
        fulfillment.channel = Channel.ONLINE;
        fulfillment.type = type;
        fulfillment.createdAt = now;
        fulfillment.deliveryFeeAmount = BigDecimal.ZERO;
        if (delivery != null) {
            fulfillment.receiverName = delivery.receiverName();
            fulfillment.receiverPhone = delivery.receiverPhone();
            fulfillment.deliveryAddress = delivery.address();
            fulfillment.deliveryNote = delivery.note();
        }
        return fulfillment;
    }

    public static PickupFulfillment createPosHandedOver(CustomerOrder order, Branch branch, Location location,
            UUID actorId, String operationKey, Instant now) {
        if (order == null || branch == null || location == null || actorId == null || operationKey == null
                || operationKey.isBlank() || operationKey.length() > 128 || now == null) {
            throw new IllegalArgumentException("POS fulfillment evidence is invalid");
        }
        PickupFulfillment fulfillment = new PickupFulfillment();
        fulfillment.publicId = UUID.randomUUID();
        fulfillment.order = order;
        fulfillment.branch = branch;
        fulfillment.location = location;
        fulfillment.channel = Channel.POS;
        fulfillment.type = Type.PICKUP;
        fulfillment.status = Status.HANDED_OVER;
        fulfillment.createdAt = now;
        fulfillment.deliveryFeeAmount = BigDecimal.ZERO;
        fulfillment.handedOverAt = now;
        fulfillment.handedOverByAccountPublicId = actorId;
        fulfillment.handoverIdempotencyKey = operationKey;
        return fulfillment;
    }

    void startPicking(Instant now) {
        if (status != Status.PENDING) throw new IllegalStateException("Fulfillment is not pending");
        status = Status.PICKING;
        pickingStartedAt = now;
    }

    void prepare(UUID actorId, Instant now) {
        if (status != Status.PENDING && status != Status.PICKING) {
            throw new IllegalStateException("Fulfillment cannot be marked ready");
        }
        if (pickingStartedAt == null) pickingStartedAt = now;
        status = Status.PREPARED;
        preparedAt = now;
        preparedByAccountPublicId = actorId;
    }

    void handover(UUID actorId, String key, Instant now) {
        if (type != Type.PICKUP || status != Status.PREPARED) throw new IllegalStateException("Pickup is not ready");
        status = Status.HANDED_OVER;
        handedOverAt = now;
        handedOverByAccountPublicId = actorId;
        handoverIdempotencyKey = key;
    }

    void dispatch(UUID actorId, String key, Instant now) {
        if (type != Type.DELIVERY || status != Status.PREPARED) throw new IllegalStateException("Delivery is not ready for dispatch");
        status = Status.OUT_FOR_DELIVERY;
        dispatchedAt = now;
        dispatchedByAccountPublicId = actorId;
        dispatchIdempotencyKey = key;
    }

    void deliver(UUID actorId, String key, Instant now) {
        if (type != Type.DELIVERY || status != Status.OUT_FOR_DELIVERY) throw new IllegalStateException("Delivery is not out for delivery");
        status = Status.DELIVERED;
        deliveredAt = now;
        deliveredByAccountPublicId = actorId;
        deliveryIdempotencyKey = key;
    }

    void cancel(UUID actorId, Instant now) {
        if (status == Status.HANDED_OVER || status == Status.OUT_FOR_DELIVERY || status == Status.DELIVERED) {
            throw new IllegalStateException("Physical fulfillment has already started or completed");
        }
        if (status == Status.CANCELLED) return;
        status = Status.CANCELLED;
        cancelledAt = now;
        cancelledByAccountPublicId = actorId;
    }

    boolean pending() { return status == Status.PENDING; }
    boolean prepared() { return status == Status.PREPARED; }
    boolean handedOver() { return status == Status.HANDED_OVER; }
    boolean dispatched() { return status == Status.OUT_FOR_DELIVERY || status == Status.DELIVERED; }
    boolean delivered() { return status == Status.DELIVERED; }
    boolean cancelled() { return status == Status.CANCELLED; }
    boolean sameHandover(UUID actorId, String key) { return handedOver() && actorId.equals(handedOverByAccountPublicId) && key.equals(handoverIdempotencyKey); }
    boolean sameDispatch(UUID actorId, String key) { return dispatched() && actorId.equals(dispatchedByAccountPublicId) && key.equals(dispatchIdempotencyKey); }
    boolean sameDelivery(UUID actorId, String key) { return delivered() && actorId.equals(deliveredByAccountPublicId) && key.equals(deliveryIdempotencyKey); }
    UUID publicId() { return publicId; }
    CustomerOrder order() { return order; }
    Branch branch() { return branch; }
    Location location() { return location; }
    String status() { return status.name(); }
    String type() { return type.name(); }
    Type fulfillmentType() { return type; }
    Instant createdAt() { return createdAt; }
    Instant pickingStartedAt() { return pickingStartedAt; }
    Instant preparedAt() { return preparedAt; }
    UUID preparedByAccountPublicId() { return preparedByAccountPublicId; }
    Instant handedOverAt() { return handedOverAt; }
    UUID handedOverByAccountPublicId() { return handedOverByAccountPublicId; }
    Instant dispatchedAt() { return dispatchedAt; }
    Instant deliveredAt() { return deliveredAt; }
    Instant cancelledAt() { return cancelledAt; }
    UUID cancelledByAccountPublicId() { return cancelledByAccountPublicId; }
    String receiverName() { return receiverName; }
    String receiverPhone() { return receiverPhone; }
    String deliveryAddress() { return deliveryAddress; }
    String deliveryNote() { return deliveryNote; }
    long deliveryFeeAmount() { return deliveryFeeAmount.longValueExact(); }

    public record DeliveryDetails(String receiverName, String receiverPhone, String address, String note) {
        public DeliveryDetails {
            receiverName = required(receiverName, 120, "Receiver name");
            receiverPhone = required(receiverPhone, 32, "Receiver phone");
            if (receiverPhone.length() < 8 || !receiverPhone.matches("[0-9+(). -]+")) {
                throw new IllegalArgumentException("Receiver phone is invalid");
            }
            address = required(address, 500, "Delivery address");
            note = note == null || note.isBlank() ? null : note.trim();
            if (note != null && note.length() > 500) throw new IllegalArgumentException("Delivery note is too long");
        }

        private static String required(String value, int max, String label) {
            if (value == null || value.isBlank() || value.trim().length() > max) {
                throw new IllegalArgumentException(label + " is invalid");
            }
            return value.trim();
        }
    }
}
