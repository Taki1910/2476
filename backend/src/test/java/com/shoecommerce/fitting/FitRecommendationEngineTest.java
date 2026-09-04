package com.shoecommerce.fitting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FitRecommendationEngineTest {
    private final FitRecommendationEngine engine = new FitRecommendationEngine();
    private final FitRecommendationEngine.Profile court = profile("TRUE_TO_SIZE", "REGULAR", List.of(
            range("39", 242, 249, 86, 96), range("40", 249, 256, 88, 99),
            range("41", 256, 263, 90, 101), range("42", 263, 270, 92, 103)));

    @Test
    void mapsTheMiddleOfARangeAndOffersAnAdjacentAlternative() {
        FitRecommendationEngine.Recommendation result = engine.recommend(252, 94, court, 95);

        assertThat(result.recommendedSize()).isEqualTo("40");
        assertThat(result.alternativeSize()).isEqualTo("41");
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    void doesNotSizeUpWhenTheNextRangeCannotFitTheMeasuredLength() {
        FitRecommendationEngine.Recommendation result = engine.recommend(252, 105, court, 95);

        assertThat(result.recommendedSize()).isEqualTo("40");
        assertThat(result.warning()).isEqualTo("WIDTH_MAY_NOT_MATCH");
    }

    @Test
    void sizesUpForWidthOnlyWhenTheNextSizeAlsoFitsTheMeasuredLength() {
        FitRecommendationEngine.Recommendation result = engine.recommend(252, 105,
                profile("TRUE_TO_SIZE", "REGULAR", List.of(
                        range("40", 249, 256, 88, 99), range("41", 252, 260, 90, 108))), 95);

        assertThat(result.recommendedSize()).isEqualTo("41");
        assertThat(result.warning()).isEqualTo("WIDTH_SIZE_UP");
    }

    @Test
    void productRangesAreAuthoritativeAndTendencyIsNotAppliedTwice() {
        FitRecommendationEngine.Recommendation smallNarrow = engine.recommend(250, 92,
                profile("RUNS_SMALL", "NARROW", List.of(range("39", 236, 244, 82, 91),
                        range("40", 244, 251, 84, 93), range("41", 251, 258, 86, 95))), 95);
        FitRecommendationEngine.Recommendation largeWide = engine.recommend(250, 94,
                profile("RUNS_LARGE", "WIDE", List.of(range("39", 248, 255, 90, 106),
                        range("40", 255, 262, 92, 108), range("41", 262, 269, 94, 110))), 95);

        assertThat(smallNarrow.recommendedSize()).isEqualTo("40");
        assertThat(largeWide.recommendedSize()).isEqualTo("39");
        assertThat(smallNarrow.recommendedSize()).isNotEqualTo(largeWide.recommendedSize());
        assertThat(smallNarrow.explanation()).isEqualTo("FIT_TENDENCY_SMALL");
        assertThat(largeWide.explanation()).isEqualTo("FIT_TENDENCY_LARGE");
    }

    @Test
    void rejectsAnUnusableAnalysisScoreAndMissingProfile() {
        FitRecommendationEngine.Recommendation result = engine.recommend(252, 94, court, 65);
        assertThat(result.confidence()).isEqualTo("MEDIUM");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.recommend(252, 94,
                new FitRecommendationEngine.Profile("EU", "TRUE_TO_SIZE", "REGULAR", List.of()), 95))
                .isInstanceOf(FitRecommendationEngine.UnsupportedFitProfileException.class);
    }

    private static FitRecommendationEngine.Profile profile(String tendency, String width,
            List<FitRecommendationEngine.SizeRange> ranges) {
        return new FitRecommendationEngine.Profile("EU", tendency, width, ranges);
    }
    private static FitRecommendationEngine.SizeRange range(String size, double minLength, double maxLength,
            double minWidth, double maxWidth) {
        return new FitRecommendationEngine.SizeRange(size, minLength, maxLength, minWidth, maxWidth);
    }
}
