package com.shoecommerce.merchandising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shoecommerce.merchandising.MerchandisingRecommendationService.Candidate;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.CandidateType;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.PromotionEvidence;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.PromotionState;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.ReadinessClass;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.ReadinessWarning;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.SalesEvidence;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.Sellability;
import com.shoecommerce.platform.api.InvalidRequestException;

class MerchandisingRecommendationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    @Test
    void ranksOnlyCompleteSellableBestSellersByUnitsValueThenProductId() {
        Candidate lowerValue = candidate("00000000-0000-0000-0000-000000000002", "Court", Sellability.SELLABLE,
                8, 10, 1_000_000, true, List.of(), List.of());
        Candidate higherValue = candidate("00000000-0000-0000-0000-000000000003", "Metro", Sellability.SELLABLE,
                4, 10, 2_000_000, true, List.of(), List.of());
        Candidate idTieBreak = candidate("00000000-0000-0000-0000-000000000001", "Studio", Sellability.SELLABLE,
                2, 10, 1_000_000, true, List.of(), List.of());
        Candidate incompleteVoid = candidate("00000000-0000-0000-0000-000000000004", "Void", Sellability.SELLABLE,
                7, 99, 9_000_000, false, List.of(), List.of());
        Candidate soldOut = candidate("00000000-0000-0000-0000-000000000005", "Sold out", Sellability.SOLD_OUT,
                0, 100, 10_000_000, true, List.of(), List.of());

        var page = MerchandisingRecommendationService.page(
                List.of(lowerValue, higherValue, idTieBreak, incompleteVoid, soldOut), null,
                CandidateType.BEST_SELLER, 0, 20);

        assertThat(page.candidates()).extracting(Candidate::productId)
                .containsExactly(higherValue.productId(), idTieBreak.productId(), lowerValue.productId());
        assertThat(page.candidates()).allSatisfy(candidate ->
                assertThat(candidate.reasonCodes()).contains("BEST_SELLER_30D"));
    }

    @Test
    void filtersActivePromotionsWarningsSearchAndPaginationWithoutPromotingScheduledCampaigns() {
        PromotionEvidence active = promotion(PromotionState.ACTIVE);
        PromotionEvidence scheduled = promotion(PromotionState.SCHEDULED);
        ReadinessWarning missingMedia = new ReadinessWarning("MISSING_PRODUCT_MEDIA",
                ReadinessClass.PRESENTATION_WEAK);
        Candidate promoted = candidate("00000000-0000-0000-0000-000000000011", "Court Classic",
                Sellability.SELLABLE, 3, 2, 400_000, true, List.of(active), List.of());
        Candidate planningOnly = candidate("00000000-0000-0000-0000-000000000012", "Metro Runner",
                Sellability.SELLABLE, 3, 1, 200_000, true, List.of(scheduled), List.of());
        Candidate warning = candidate("00000000-0000-0000-0000-000000000013", "Trail Form",
                Sellability.NO_EFFECTIVE_PRICE, 0, 0, 0, true, List.of(), List.of(missingMedia));

        assertThat(MerchandisingRecommendationService.page(List.of(planningOnly, warning, promoted), null,
                CandidateType.PROMOTED, 0, 20).candidates()).containsExactly(promoted);
        assertThat(MerchandisingRecommendationService.page(List.of(planningOnly, warning, promoted), null,
                CandidateType.NEEDS_REVIEW, 0, 20).candidates()).containsExactly(warning);
        assertThat(MerchandisingRecommendationService.page(List.of(planningOnly, warning, promoted), "  runner ",
                CandidateType.ALL, 0, 1).candidates()).containsExactly(planningOnly);
        assertThat(MerchandisingRecommendationService.page(List.of(planningOnly, warning, promoted), null,
                CandidateType.ALL, 1, 2).candidates()).containsExactly(warning);
    }

    @Test
    void rejectsUnsafeQueryAndPaginationBoundaries() {
        assertInvalid(() -> MerchandisingRecommendationService.page(List.of(), "x".repeat(81),
                CandidateType.ALL, 0, 20), "MERCHANDISING_QUERY_INVALID");
        assertInvalid(() -> MerchandisingRecommendationService.page(List.of(), null,
                CandidateType.ALL, -1, 20), "INVALID_PAGINATION");
        assertInvalid(() -> MerchandisingRecommendationService.page(List.of(), null,
                CandidateType.ALL, 0, 51), "INVALID_PAGINATION");
        assertThat(MerchandisingRecommendationService.page(List.of(candidate(
                "00000000-0000-0000-0000-000000000099", "Overflow", Sellability.SELLABLE,
                1, 1, 1, true, List.of(), List.of())), null,
                CandidateType.ALL, Integer.MAX_VALUE, 50).candidates()).isEmpty();
    }

    @Test
    void selectsBoundedAuthoritativeEvidenceInRequestedOrder() {
        Candidate first = candidate("00000000-0000-0000-0000-000000000031", "First", Sellability.SELLABLE,
                1, 1, 1, true, List.of(), List.of());
        Candidate second = candidate("00000000-0000-0000-0000-000000000032", "Second", Sellability.SELLABLE,
                1, 1, 1, true, List.of(), List.of());

        assertThat(MerchandisingRecommendationService.selectEvidence(List.of(first, second),
                List.of(second.productId(), first.productId()))).containsExactly(second, first);
        assertInvalid(() -> MerchandisingRecommendationService.selectEvidence(List.of(first),
                List.of(UUID.randomUUID())), "MERCHANDISING_AI_PRODUCT_NOT_FOUND");
        assertInvalid(() -> MerchandisingRecommendationService.selectEvidence(List.of(first),
                java.util.stream.IntStream.range(0, 9).mapToObj(index -> new UUID(0, index + 1)).toList()),
                "MERCHANDISING_AI_PRODUCTS_INVALID");
    }

    private static Candidate candidate(String id, String name, Sellability sellability, long available,
            long units, long value, boolean complete, List<PromotionEvidence> promotions,
            List<ReadinessWarning> warnings) {
        return new Candidate(UUID.fromString(id), name, "Sneaker", "Everyday", "/products/test.png",
                List.of("Black"), false, sellability, available,
                new SalesEvidence(units, value, NOW.minusSeconds(30L * 86_400), NOW, complete),
                promotions, List.of(), List.of(), warnings, List.of());
    }

    private static PromotionEvidence promotion(PromotionState state) {
        return new PromotionEvidence(UUID.randomUUID(), UUID.randomUUID(), "Campaign", "ITEM_PERCENT",
                10.0, null, NOW.minusSeconds(60), NOW.plusSeconds(3_600), state);
    }

    private static void assertInvalid(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(InvalidRequestException.class)
                .extracting(error -> ((InvalidRequestException) error).code()).isEqualTo(code);
    }
}
