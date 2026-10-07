package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogDiscoveryTest {
    private static final UUID COURT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RUNNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SOLD_OUT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void interpretsVietnameseColorAndExplicitMillionPrice() {
        CatalogDiscovery.QueryIntent intent = CatalogDiscovery.interpret("  giày trắng dưới 2 triệu  ");

        assertThat(intent.normalizedQuery()).isEqualTo("giay trang duoi 2 trieu");
        assertThat(intent.colorFamilies()).containsExactly("WHITE");
        assertThat(intent.maximumPrice()).isEqualTo(2_000_000L);
    }

    @Test
    void supportsAccentlessColorAndCategoryQueries() {
        CatalogDiscovery.Outcome accented = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("giày trắng chạy bộ"), documents());
        CatalogDiscovery.Outcome accentless = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("giay trang chay bo"), documents());

        assertThat(accented.resultIds()).containsExactly(RUNNER);
        assertThat(accentless.resultIds()).isEqualTo(accented.resultIds());
    }

    @Test
    void recognizesNaturalEverydayUseWithoutTreatingTheConnectorAsARequiredTerm() {
        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("giày đi hàng ngày"), documents());

        assertThat(outcome.resultIds()).contains(COURT);
    }

    @Test
    void parsesOnlyExplicitPriceIntent() {
        assertThat(CatalogDiscovery.interpret("dưới 1.5tr").maximumPrice()).isEqualTo(1_500_000L);
        assertThat(CatalogDiscovery.interpret("<= 1500000").maximumPrice()).isEqualTo(1_500_000L);
        assertThat(CatalogDiscovery.interpret("size 41").maximumPrice()).isNull();
        assertThat(CatalogDiscovery.interpret("Court Classic 41").maximumPrice()).isNull();
    }

    @Test
    void exactSkuLookupIsRecognizedWithoutTeachingSkuAsACommerceTerm() {
        CatalogDiscovery.QueryIntent intent = CatalogDiscovery.interpret("DEMO-CC-41");
        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(intent, documents());

        assertThat(intent.sku()).isEqualTo("demo-cc-41");
        assertThat(outcome.resultIds()).containsExactly(COURT);
    }

    @Test
    void aNaturalHyphenatedPhraseFallsBackToOrdinaryDiscoveryWhenItIsNotAnExactSku() {
        UUID slipOn = UUID.fromString("00000000-0000-0000-0000-000000000005");
        CatalogDiscovery.SearchDocument document = document(slipOn, "City Slip-On", "Everyday",
                "City Ease", Set.of("Cream"), Set.of("DEMO-CS-40"), Set.of("CASUAL_DAILY"),
                1_190_000, 1, 2);

        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("slip-on"), List.of(document));

        assertThat(outcome.resultIds()).containsExactly(slipOn);
    }

    @Test
    void anExactProductNameOutranksConflictingStructuredAliases() {
        UUID whiteLabel = UUID.fromString("00000000-0000-0000-0000-000000000006");
        CatalogDiscovery.SearchDocument document = document(whiteLabel, "White Label", "Studio",
                "Limited", Set.of("Black"), Set.of("DEMO-WL-40"), Set.of("CASUAL_DAILY"),
                1_690_000, 1, 3);

        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("White Label"), List.of(document));

        assertThat(outcome.resultIds()).containsExactly(whiteLabel);
    }

    @Test
    void removesOverlappingAliasesLongestFirst() {
        CatalogDiscovery.QueryIntent intent = CatalogDiscovery.interpret("all black");

        assertThat(intent.colorFamilies()).containsExactly("BLACK");
        assertThat(intent.terms()).isEmpty();
    }

    @Test
    void aStrongNameMatchOutranksWeakMetadataTokens() {
        CatalogDiscovery.SearchDocument weak = document(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "Studio Low", "Court", "Classic Studio", Set.of("Cream"), Set.of("DEMO-SL-40"),
                Set.of("CASUAL_DAILY"), 1_200_000, 2, 0);

        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("Court Classic"), List.of(weak, documents().getFirst()));

        assertThat(outcome.resultIds()).startsWith(COURT);
    }

    @Test
    void appliesColorAndPriceAsMeaningfulResultConstraints() {
        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("giày trắng dưới 2 triệu"), documents());

        assertThat(outcome.resultIds()).containsExactly(COURT, RUNNER);
        assertThat(outcome.suggestionIds()).isEmpty();
    }

    @Test
    void keepsNoMatchSeparateAndSuggestsOnlyAvailableProducts() {
        CatalogDiscovery.Outcome outcome = CatalogDiscovery.rank(
                CatalogDiscovery.interpret("violet formal"), documents());

        assertThat(outcome.resultIds()).isEmpty();
        assertThat(outcome.suggestionIds()).containsExactly(COURT, RUNNER);
        assertThat(outcome.suggestionIds()).doesNotContain(SOLD_OUT);
    }

    private static List<CatalogDiscovery.SearchDocument> documents() {
        return List.of(
                document(COURT, "Court Classic", "Court", "Court Originals",
                        Set.of("White / Black"), Set.of("DEMO-CC-39", "DEMO-CC-41"),
                        Set.of("CASUAL_DAILY", "SMOOTH_HARD_SURFACE"), 1_490_000, 3, 1),
                document(RUNNER, "Lite Runner", "Running", "Metro Motion",
                        Set.of("Grey / White"), Set.of("DEMO-LR-40"),
                        Set.of("ROAD_RUNNING", "ROAD_TRACK"), 1_290_000, 2, 13),
                document(SOLD_OUT, "Archive One", "Limited", "Archive 01",
                        Set.of("Black / Silver"), Set.of("DEMO-AO-40"),
                        Set.of("CASUAL_DAILY"), 2_490_000, 0, 6));
    }

    private static CatalogDiscovery.SearchDocument document(UUID id, String name, String category,
            String collection, Set<String> colors, Set<String> skus, Set<String> attributes,
            long price, long availableVariants, int rank) {
        return new CatalogDiscovery.SearchDocument(id, name, category, collection, colors, skus,
                attributes, price, availableVariants, rank);
    }
}
