package com.shoecommerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.shoecommerce.platform.api.InvalidRequestException;

class PromotionP3PolicyTest {
    private static final Instant AS_OF = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    void derives_customer_facing_lifecycle_from_one_as_of() {
        assertThat(PromotionService.displayState("DRAFT", AS_OF, null, AS_OF, false)).isEqualTo("DRAFT");
        assertThat(PromotionService.displayState("PUBLISHED", AS_OF.plusSeconds(1), null, AS_OF, true)).isEqualTo("SCHEDULED");
        assertThat(PromotionService.displayState("PUBLISHED", AS_OF.minusSeconds(1), null, AS_OF, true)).isEqualTo("ACTIVE");
        assertThat(PromotionService.displayState("PUBLISHED", AS_OF.minusSeconds(10), AS_OF, AS_OF, true)).isEqualTo("ENDED");
        assertThat(PromotionService.displayState("RETIRED", AS_OF.minusSeconds(10), null, AS_OF, true)).isEqualTo("RETIRED");
        assertThat(PromotionService.displayState("INVALIDATED", AS_OF.minusSeconds(10), null, AS_OF, true)).isEqualTo("INVALIDATED");
    }

    @Test
    void claimable_policy_is_public_and_single_use_per_family() {
        PromotionService.validateAcquisitionPolicy("CLAIMABLE", true, 1L);
        PromotionService.validateAcquisitionPolicy("CLAIMABLE", true, null);

        assertThatThrownBy(() -> PromotionService.validateAcquisitionPolicy("CLAIMABLE", false, 1L))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PromotionService.validateAcquisitionPolicy("CLAIMABLE", true, 2L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void code_preview_uses_the_same_nfkc_normalization_as_persistence() {
        assertThat(PromotionService.normalizeCode("  sＡle_20  ")).isEqualTo("SALE_20");
    }
}
