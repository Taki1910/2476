package com.shoecommerce.inventory;

import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.branch.Location;
import com.shoecommerce.catalog.ProductVariant;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {
    enum Status { ACTIVE, ADOPTED, RELEASED, CONSUMED, EXPIRED, COMMITTED, CANCELLED_RESTORED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "owner_account_public_id", nullable = false) private UUID ownerAccountPublicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "variant_id") private ProductVariant variant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id") private Location location;
    @Column(nullable = false) private long quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "adopted_at") private Instant adoptedAt;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "expired_at") private Instant expiredAt;
    @Column(name = "committed_at") private Instant committedAt;
    @Column(name = "cancelled_restored_at") private Instant cancelledRestoredAt;

    protected InventoryReservation() { }

    static InventoryReservation create(UUID ownerAccountPublicId, ProductVariant variant, Location location, long quantity, Instant now) {
        if (ownerAccountPublicId == null || variant == null || location == null || quantity <= 0) throw new IllegalArgumentException("Reservation is invalid");
        InventoryReservation reservation = new InventoryReservation();
        reservation.publicId = UUID.randomUUID();
        reservation.ownerAccountPublicId = ownerAccountPublicId;
        reservation.variant = variant;
        reservation.location = location;
        reservation.quantity = quantity;
        reservation.status = Status.ACTIVE;
        reservation.createdAt = now;
        return reservation;
    }

    static InventoryReservation createCheckout(UUID ownerAccountPublicId, ProductVariant variant, Location location,
            long quantity, Instant now, Instant expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(now)) throw new IllegalArgumentException("Reservation expiry is invalid");
        InventoryReservation reservation = create(ownerAccountPublicId, variant, location, quantity, now);
        reservation.expiresAt = expiresAt;
        return reservation;
    }

    void release(Instant now) { if (status == Status.ACTIVE) { status = Status.RELEASED; releasedAt = now; } }
    void adopt(Instant now) { if (status != Status.ACTIVE) throw new IllegalStateException("Reservation is not adoptable"); status = Status.ADOPTED; adoptedAt = now; }
    void releaseAdopted(Instant now) { if (status != Status.ADOPTED) throw new IllegalStateException("Adopted reservation cannot be released"); status = Status.RELEASED; releasedAt = now; }
    void consume(Instant now) { if (status != Status.ADOPTED) throw new IllegalStateException("Adopted reservation cannot be consumed"); status = Status.CONSUMED; consumedAt = now; }
    void commit(Instant now) { if (status != Status.ADOPTED) throw new IllegalStateException("Adopted reservation cannot be committed"); status = Status.COMMITTED; committedAt = now; }
    void consumeCommitted(Instant now) { if (status != Status.COMMITTED) throw new IllegalStateException("Committed reservation cannot be handed over"); status = Status.CONSUMED; consumedAt = now; }
    void restoreCancelled(Instant now) { if (status != Status.COMMITTED) throw new IllegalStateException("Committed reservation cannot be restored"); status = Status.CANCELLED_RESTORED; cancelledRestoredAt = now; }
    void expire(Instant now) { if (status != Status.ADOPTED || !dueForExpiry(now)) throw new IllegalStateException("Reservation is not expired"); status = Status.EXPIRED; expiredAt = now; }
    boolean dueForExpiry(Instant now) { return expiresAt != null && !now.isBefore(expiresAt); }
    boolean active() { return status == Status.ACTIVE; }
    boolean adopted() { return status == Status.ADOPTED; }
    boolean consumed() { return status == Status.CONSUMED; }
    boolean committed() { return status == Status.COMMITTED; }
    UUID publicId() { return publicId; }
    UUID ownerAccountPublicId() { return ownerAccountPublicId; }
    ProductVariant variant() { return variant; }
    Location location() { return location; }
    long quantity() { return quantity; }
    String status() { return status.name(); }
    Instant createdAt() { return createdAt; }
    Instant adoptedAt() { return adoptedAt; }
    Instant releasedAt() { return releasedAt; }
    Instant consumedAt() { return consumedAt; }
    Instant expiresAt() { return expiresAt; }
    Instant expiredAt() { return expiredAt; }
    Instant committedAt() { return committedAt; }
    Instant cancelledRestoredAt() { return cancelledRestoredAt; }
}
