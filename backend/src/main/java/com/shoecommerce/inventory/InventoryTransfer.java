package com.shoecommerce.inventory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.shoecommerce.platform.api.BusinessConflictException;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_transfer")
class InventoryTransfer {
    enum Status { DRAFT, DISPATCHED, RECEIVED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "source_location_public_id", nullable = false) private UUID sourceLocationId;
    @Column(name = "destination_location_public_id", nullable = false) private UUID destinationLocationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "created_by_account_public_id", nullable = false) private UUID createdBy;
    @Column(name = "dispatched_by_account_public_id") private UUID dispatchedBy;
    @Column(name = "received_by_account_public_id") private UUID receivedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "received_at") private Instant receivedAt;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id") private List<InventoryTransferLine> lines = new ArrayList<>();

    protected InventoryTransfer() { }

    static InventoryTransfer create(UUID sourceLocationId, UUID destinationLocationId, List<LineDraft> drafts,
            UUID actorId, Instant now) {
        if (sourceLocationId == null || destinationLocationId == null || actorId == null || now == null) {
            throw new IllegalArgumentException("Inventory transfer identity is required");
        }
        if (sourceLocationId.equals(destinationLocationId)) {
            throw new BusinessConflictException("INVENTORY_TRANSFER_SAME_LOCATION",
                    "Source and destination locations must be different.");
        }
        if (drafts == null || drafts.isEmpty()) throw new IllegalArgumentException("Transfer lines are required");
        HashSet<UUID> variants = new HashSet<>();
        InventoryTransfer transfer = new InventoryTransfer();
        transfer.publicId = UUID.randomUUID();
        transfer.sourceLocationId = sourceLocationId;
        transfer.destinationLocationId = destinationLocationId;
        transfer.status = Status.DRAFT;
        transfer.createdBy = actorId;
        transfer.createdAt = now;
        for (LineDraft draft : drafts) {
            if (draft == null || draft.variantId() == null || draft.quantity() <= 0
                    || !variants.add(draft.variantId())) {
                throw new IllegalArgumentException("Transfer lines require distinct variants and positive quantities");
            }
            transfer.lines.add(InventoryTransferLine.create(transfer, draft.variantId(), draft.quantity()));
        }
        return transfer;
    }

    void dispatch(UUID actorId, Instant now) {
        require(Status.DRAFT);
        if (actorId == null || now == null) throw new IllegalArgumentException("Dispatch evidence is required");
        lines.forEach(InventoryTransferLine::dispatch);
        status = Status.DISPATCHED;
        dispatchedBy = actorId;
        dispatchedAt = now;
    }

    void receive(UUID actorId, Instant now) {
        require(Status.DISPATCHED);
        if (actorId == null || now == null) throw new IllegalArgumentException("Receipt evidence is required");
        lines.forEach(InventoryTransferLine::receive);
        status = Status.RECEIVED;
        receivedBy = actorId;
        receivedAt = now;
    }

    private void require(Status expected) {
        if (status != expected) throw new BusinessConflictException("INVENTORY_TRANSFER_INVALID_STATE",
                "Inventory transfer cannot perform this action from state " + status + ".");
    }

    long inTransitQuantity() {
        return lines.stream().mapToLong(line -> line.dispatchedQuantity() - line.receivedQuantity()).sum();
    }

    UUID publicId() { return publicId; }
    UUID sourceLocationId() { return sourceLocationId; }
    UUID destinationLocationId() { return destinationLocationId; }
    Status status() { return status; }
    UUID createdBy() { return createdBy; }
    UUID dispatchedBy() { return dispatchedBy; }
    UUID receivedBy() { return receivedBy; }
    Instant createdAt() { return createdAt; }
    Instant dispatchedAt() { return dispatchedAt; }
    Instant receivedAt() { return receivedAt; }
    List<InventoryTransferLine> lines() { return List.copyOf(lines); }

    record LineDraft(UUID variantId, long quantity) { }
}
