package com.shoecommerce.inventory;

import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_transfer_line")
class InventoryTransferLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transfer_id")
    private InventoryTransfer transfer;
    @Column(name = "variant_public_id", nullable = false) private UUID variantId;
    @Column(name = "requested_quantity", nullable = false) private long requestedQuantity;
    @Column(name = "dispatched_quantity", nullable = false) private long dispatchedQuantity;
    @Column(name = "received_quantity", nullable = false) private long receivedQuantity;

    protected InventoryTransferLine() { }

    static InventoryTransferLine create(InventoryTransfer transfer, UUID variantId, long quantity) {
        InventoryTransferLine line = new InventoryTransferLine();
        line.transfer = transfer;
        line.variantId = variantId;
        line.requestedQuantity = quantity;
        return line;
    }

    void dispatch() { dispatchedQuantity = requestedQuantity; }
    void receive() { receivedQuantity = dispatchedQuantity; }
    UUID variantId() { return variantId; }
    long requestedQuantity() { return requestedQuantity; }
    long dispatchedQuantity() { return dispatchedQuantity; }
    long receivedQuantity() { return receivedQuantity; }
}
