package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StockMovementReceiptTest {
    @Test
    void receiptCarriesReplayAndInventoryEvidenceDistinctFromAdjustment() {
        UUID actorId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        StockMovement movement = StockMovement.createReceipt("R:receipt-1", "fingerprint", "PO-123",
                actorId, variantId, locationId, 4, 5, 9, 3, Instant.parse("2026-10-08T00:00:00Z"));

        assertThat(movement.type()).isEqualTo(StockMovement.Type.RECEIVE);
        assertThat(movement.quantity()).isEqualTo(4);
        assertThat(movement.onHandDelta()).isEqualTo(4);
        assertThat(movement.reservedDelta()).isZero();
        assertThat(movement.beforeOnHand()).isEqualTo(5);
        assertThat(movement.afterOnHand()).isEqualTo(9);
        assertThat(movement.reservedAtOperation()).isEqualTo(3);
        assertThat(movement.reason()).isEqualTo("PO-123");
        assertThat(movement.matchesReceipt("fingerprint")).isTrue();
        assertThat(movement.matchesAdjustment("fingerprint")).isFalse();
    }

    @Test
    void transferMovementsCarryTransferProvenanceAndDistinctDirections() {
        UUID transferId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-12T00:00:00Z");

        StockMovement out = StockMovement.createTransfer(StockMovement.Type.TRANSFER_OUT,
                "T:" + transferId + ":OUT:" + variantId, transferId, actorId, variantId, sourceId,
                4, 7, 3, 2, now);
        StockMovement in = StockMovement.createTransfer(StockMovement.Type.TRANSFER_IN,
                "T:" + transferId + ":IN:" + variantId, transferId, actorId, variantId, destinationId,
                4, 1, 5, 0, now);

        assertThat(out.transferId()).isEqualTo(transferId);
        assertThat(out.onHandDelta()).isEqualTo(-4);
        assertThat(out.reservedDelta()).isZero();
        assertThat(out.beforeOnHand()).isEqualTo(7);
        assertThat(out.afterOnHand()).isEqualTo(3);
        assertThat(in.transferId()).isEqualTo(transferId);
        assertThat(in.onHandDelta()).isEqualTo(4);
        assertThat(in.beforeOnHand()).isEqualTo(1);
        assertThat(in.afterOnHand()).isEqualTo(5);
    }
}
