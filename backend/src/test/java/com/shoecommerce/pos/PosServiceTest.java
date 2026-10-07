package com.shoecommerce.pos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PosServiceTest {
    @Test
    void derivesCashierSafeSaleStatesWithoutCollapsingKnownVariants() {
        assertThat(PosService.saleState("PUBLISHED", 1_490_000L, 2)).isEqualTo(PosService.SaleState.SELLABLE);
        assertThat(PosService.saleState("PUBLISHED", 1_490_000L, 0)).isEqualTo(PosService.SaleState.SOLD_OUT_HERE);
        assertThat(PosService.saleState("RETIRED", 1_490_000L, 2)).isEqualTo(PosService.SaleState.RETIRED);
        assertThat(PosService.saleState("DRAFT", 1_490_000L, 2)).isEqualTo(PosService.SaleState.NOT_PUBLISHED);
        assertThat(PosService.saleState("PUBLISHED", null, 2)).isEqualTo(PosService.SaleState.PRICE_UNAVAILABLE);
    }
}
