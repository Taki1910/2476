package com.shoecommerce.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CustomerOrderTest {
    @Test
    void snapshotsTheExactStoredColorForAPosReceipt() {
        CustomerOrder order = CustomerOrder.createPos(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SKU-41-BLK", "41", "Black / White", 1_490_000,
                Instant.parse("2026-09-28T00:00:00Z"));

        assertThat(order.receiptFacts().color()).isEqualTo("Black / White");
    }
}
