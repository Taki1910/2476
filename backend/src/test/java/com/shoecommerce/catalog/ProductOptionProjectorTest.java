package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProductOptionProjectorTest {
    @Test
    void groupsExactStoredColorsAndProjectsTheGlobalSizeSetPerColor() {
        var options = ProductOptionProjector.project(List.of(
                variant("Black", "40", "UNAVAILABLE", 1),
                variant("Chalk", "40", "AVAILABLE", 2),
                variant("Black", "41", "AVAILABLE", 3)));

        assertThat(options).extracting(StorefrontCatalogService.ColorOption::color)
                .containsExactly("Black", "Chalk");
        assertThat(options).allSatisfy(option -> assertThat(option.sizes())
                .extracting(StorefrontCatalogService.SizeOption::size)
                .containsExactly("40", "41"));

        StorefrontCatalogService.SizeOption black40 = options.getFirst().sizes().getFirst();
        assertThat(black40.availability()).isEqualTo("UNAVAILABLE");
        assertThat(black40.availableAlternativeColors()).containsExactly("Chalk");

        StorefrontCatalogService.SizeOption chalk41 = options.get(1).sizes().get(1);
        assertThat(chalk41.variantId()).isNull();
        assertThat(chalk41.availability()).isEqualTo("UNAVAILABLE");
        assertThat(chalk41.availableAlternativeColors()).containsExactly("Black");
    }

    @Test
    void keepsACompositeStoredColorAsOneCustomerOption() {
        var options = ProductOptionProjector.project(List.of(
                variant("White / Black", "39", "AVAILABLE", 1),
                variant("White / Black", "40", "AVAILABLE", 2)));

        assertThat(options).singleElement()
                .extracting(StorefrontCatalogService.ColorOption::color)
                .isEqualTo("White / Black");
    }

    @Test
    void doesNotSuggestAnUnavailableAlternativeColor() {
        var options = ProductOptionProjector.project(List.of(
                variant("Black", "41", "UNAVAILABLE", 1),
                variant("Cream", "41", "UNAVAILABLE", 2)));

        assertThat(options).allSatisfy(option -> assertThat(option.sizes().getFirst()
                .availableAlternativeColors()).isEmpty());
    }

    @Test
    void keepsTheFirstExactOptionWhenLegacyDataContainsADuplicateColorAndSize() {
        var first = variant("Black", "41", "AVAILABLE", 1);
        var duplicate = variant("Black", "41", "UNAVAILABLE", 2);

        var projected = ProductOptionProjector.project(List.of(first, duplicate));

        assertThat(projected).singleElement().satisfies(color -> assertThat(color.sizes())
                .singleElement().satisfies(size -> {
                    assertThat(size.variantId()).isEqualTo(first.id());
                    assertThat(size.availability()).isEqualTo("AVAILABLE");
                }));
    }

    private static StorefrontCatalogService.VariantView variant(String color, String size,
            String availability, int suffix) {
        return new StorefrontCatalogService.VariantView(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + suffix),
                "SKU-" + suffix, size, color, availability, 1_490_000 + suffix);
    }
}
