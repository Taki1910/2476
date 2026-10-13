package com.shoecommerce.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class InventoryBalanceTest {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void receiveIncreasesOnHandWithoutChangingReserved() {
        InventoryBalance balance = InventoryBalance.create(null, null, 5, NOW);
        balance.reserve(3, NOW);

        balance.receive(4, NOW.plusSeconds(1));

        assertThat(balance.onHand()).isEqualTo(9);
        assertThat(balance.reserved()).isEqualTo(3);
        assertThat(balance.available()).isEqualTo(6);
        assertThat(balance.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void receiveRejectsNonPositiveQuantity() {
        InventoryBalance balance = InventoryBalance.create(null, null, 5, NOW);

        assertThatThrownBy(() -> balance.receive(0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.receive(-1, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.onHand()).isEqualTo(5);
    }
}
