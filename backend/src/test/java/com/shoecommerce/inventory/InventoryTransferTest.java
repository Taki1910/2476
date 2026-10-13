package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shoecommerce.platform.api.BusinessConflictException;

class InventoryTransferTest {
    private static final Instant CREATED = Instant.parse("2026-10-12T01:00:00Z");
    private static final Instant DISPATCHED = Instant.parse("2026-10-12T02:00:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-10-12T03:00:00Z");

    @Test
    void draftCarriesMultipleLinesWithoutMovingStock() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        InventoryTransfer transfer = InventoryTransfer.create(source, destination,
                List.of(new InventoryTransfer.LineDraft(first, 2), new InventoryTransfer.LineDraft(second, 3)),
                actor, CREATED);

        assertThat(transfer.status()).isEqualTo(InventoryTransfer.Status.DRAFT);
        assertThat(transfer.lines()).extracting(InventoryTransferLine::requestedQuantity).containsExactly(2L, 3L);
        assertThat(transfer.lines()).extracting(InventoryTransferLine::dispatchedQuantity).containsOnly(0L);
        assertThat(transfer.lines()).extracting(InventoryTransferLine::receivedQuantity).containsOnly(0L);
        assertThat(transfer.inTransitQuantity()).isZero();
    }

    @Test
    void rejectsSameLocationDuplicateVariantsAndNonPositiveQuantities() {
        UUID location = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID variant = UUID.randomUUID();

        assertThatThrownBy(() -> InventoryTransfer.create(location, location,
                List.of(new InventoryTransfer.LineDraft(variant, 1)), actor, CREATED))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code").isEqualTo("INVENTORY_TRANSFER_SAME_LOCATION");
        assertThatThrownBy(() -> InventoryTransfer.create(location, UUID.randomUUID(),
                List.of(new InventoryTransfer.LineDraft(variant, 1), new InventoryTransfer.LineDraft(variant, 2)),
                actor, CREATED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryTransfer.create(location, UUID.randomUUID(),
                List.of(new InventoryTransfer.LineDraft(variant, 0)), actor, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dispatchAndReceiveAreStrictAllOrNothingTransitions() {
        InventoryTransfer transfer = transfer();
        UUID dispatcher = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        transfer.dispatch(dispatcher, DISPATCHED);
        assertThat(transfer.status()).isEqualTo(InventoryTransfer.Status.DISPATCHED);
        assertThat(transfer.lines()).allSatisfy(line -> assertThat(line.dispatchedQuantity())
                .isEqualTo(line.requestedQuantity()));
        assertThat(transfer.inTransitQuantity()).isEqualTo(5);
        assertThatThrownBy(() -> transfer.dispatch(dispatcher, DISPATCHED))
                .isInstanceOf(BusinessConflictException.class);

        transfer.receive(receiver, RECEIVED);
        assertThat(transfer.status()).isEqualTo(InventoryTransfer.Status.RECEIVED);
        assertThat(transfer.lines()).allSatisfy(line -> assertThat(line.receivedQuantity())
                .isEqualTo(line.dispatchedQuantity()));
        assertThat(transfer.inTransitQuantity()).isZero();
        assertThatThrownBy(() -> transfer.receive(receiver, RECEIVED))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void cannotReceiveADraft() {
        assertThatThrownBy(() -> transfer().receive(UUID.randomUUID(), RECEIVED))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("code").isEqualTo("INVENTORY_TRANSFER_INVALID_STATE");
    }

    private static InventoryTransfer transfer() {
        return InventoryTransfer.create(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new InventoryTransfer.LineDraft(UUID.randomUUID(), 2),
                new InventoryTransfer.LineDraft(UUID.randomUUID(), 3)), UUID.randomUUID(), CREATED);
    }
}
