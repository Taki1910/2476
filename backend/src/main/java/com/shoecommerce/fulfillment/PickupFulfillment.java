package com.shoecommerce.fulfillment;

import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.branch.Branch;
import com.shoecommerce.branch.Location;
import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

@Entity
@Table(name = "pickup_fulfillment")
public class PickupFulfillment {
    enum Status { PENDING, PICKING, PREPARED, HANDED_OVER, CANCELLED }
    enum Channel { ONLINE, POS }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true) private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "branch_id", nullable = false) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id", nullable = false) private Location location;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Channel channel;
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

    protected PickupFulfillment() { }

    static PickupFulfillment create(CustomerOrder order, Branch branch, Location location, Instant now) {
        PickupFulfillment fulfillment = new PickupFulfillment();
        fulfillment.publicId = UUID.randomUUID();
        fulfillment.order = order;
        fulfillment.branch = branch;
        fulfillment.location = location;
        fulfillment.status = Status.PENDING;
        fulfillment.channel = Channel.ONLINE;
        fulfillment.createdAt = now;
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
        fulfillment.status = Status.HANDED_OVER;
        fulfillment.createdAt = now;
        fulfillment.handedOverAt = now;
        fulfillment.handedOverByAccountPublicId = actorId;
        fulfillment.handoverIdempotencyKey = operationKey;
        return fulfillment;
    }

    void startPicking(Instant now) {
        if (status != Status.PENDING) throw new IllegalStateException("Pickup fulfillment is not pending");
        status = Status.PICKING;
        pickingStartedAt = now;
    }

    void prepare(UUID actorId, Instant now) {
        if (status != Status.PENDING && status != Status.PICKING) {
            throw new IllegalStateException("Pickup fulfillment cannot be prepared");
        }
        if (pickingStartedAt == null) pickingStartedAt = now;
        status = Status.PREPARED;
        preparedAt = now;
        preparedByAccountPublicId = actorId;
    }

    void handover(UUID actorId, String key, Instant now) {
        if (status != Status.PREPARED) throw new IllegalStateException("Pickup fulfillment is not prepared");
        status = Status.HANDED_OVER;
        handedOverAt = now;
        handedOverByAccountPublicId = actorId;
        handoverIdempotencyKey = key;
    }

    void cancel(UUID actorId, Instant now) {
        if (status == Status.HANDED_OVER) throw new IllegalStateException("Handed-over pickup cannot be cancelled");
        if (status == Status.CANCELLED) return;
        status = Status.CANCELLED;
        cancelledAt = now;
        cancelledByAccountPublicId = actorId;
    }

    boolean pending() { return status == Status.PENDING; }
    boolean prepared() { return status == Status.PREPARED; }
    boolean handedOver() { return status == Status.HANDED_OVER; }
    boolean cancelled() { return status == Status.CANCELLED; }
    boolean sameHandover(UUID actorId, String key) { return handedOver() && actorId.equals(handedOverByAccountPublicId) && key.equals(handoverIdempotencyKey); }
    UUID publicId() { return publicId; }
    CustomerOrder order() { return order; }
    Branch branch() { return branch; }
    Location location() { return location; }
    String status() { return status.name(); }
    Instant createdAt() { return createdAt; }
    Instant pickingStartedAt() { return pickingStartedAt; }
    Instant preparedAt() { return preparedAt; }
    UUID preparedByAccountPublicId() { return preparedByAccountPublicId; }
    Instant handedOverAt() { return handedOverAt; }
    UUID handedOverByAccountPublicId() { return handedOverByAccountPublicId; }
    Instant cancelledAt() { return cancelledAt; }
    UUID cancelledByAccountPublicId() { return cancelledByAccountPublicId; }
}
