package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ProductVariantTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void normalizesApprovedEuSizeIdentityWithoutChangingTheDisplayValue() {
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-041-BLK", " 041.00 ", " Black ", NOW);

        assertThat(variant.size()).isEqualTo("041.00");
        assertThat(variant.normalizedSize()).isEqualByComparingTo(new BigDecimal("41.00"));
        assertThat(ProductVariant.normalizeSize("41"))
                .isEqualByComparingTo(ProductVariant.normalizeSize("41.0"));
        assertThat(ProductVariant.normalizeSize("41.00"))
                .isEqualByComparingTo(ProductVariant.normalizeSize("041"));
    }

    @Test
    void rejectsNonNumericNonPositiveAndOverPrecisionEuSizes() {
        assertThatThrownBy(() -> ProductVariant.normalizeSize("EU 41"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductVariant.normalizeSize("0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductVariant.normalizeSize("41.001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductVariant.normalizeSize("4.1E1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesOnlyOuterSpaceAndCaseForColorIdentity() {
        assertThat(ProductVariant.normalizeColor(" Black ")).isEqualTo("BLACK");
        assertThat(ProductVariant.normalizeColor("black")).isEqualTo("BLACK");
        assertThat(ProductVariant.normalizeColor("Đen")).isEqualTo("ĐEN");
        assertThat(ProductVariant.normalizeColor("Đen")).isNotEqualTo(ProductVariant.normalizeColor("Den"));
        assertThat(ProductVariant.normalizeColor("Black / White"))
                .isNotEqualTo(ProductVariant.normalizeColor("White / Black"));
    }

    @Test
    void enforcesTheApprovedSaleLifecycle() {
        Product product = Product.create("Runner", NOW);
        ProductVariant draft = ProductVariant.create(product, "RUN-41-BLK", "41", "Black", NOW);

        draft.publish(0);
        assertThat(draft.status()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> draft.restore(0)).isInstanceOf(IllegalStateException.class);

        draft.retire(0);
        assertThat(draft.status()).isEqualTo("RETIRED");
        assertThatThrownBy(() -> draft.publish(0)).isInstanceOf(IllegalStateException.class);

        draft.restore(0);
        assertThat(draft.status()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsStaleLifecycleCommands() {
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", NOW);

        assertThatThrownBy(() -> variant.publish(1))
                .isInstanceOfSatisfying(com.shoecommerce.platform.api.BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_VARIANT_CHANGED"));
    }

    @Test
    void treatsBarcodeAsTrimmedOpaqueVariantIdentity() {
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", " 000123-Ab / 9 ", NOW);

        assertThat(variant.barcode()).isEqualTo("000123-Ab / 9");

        variant.updateBarcode("  000007-xY  ", 0);
        assertThat(variant.barcode()).isEqualTo("000007-xY");

        variant.updateBarcode("   ", 0);
        assertThat(variant.barcode()).isNull();
    }

    @Test
    void keepsBarcodeImmutableAfterVariantHasEverBeenPublished() {
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", "000123", NOW);

        variant.publish(0);
        variant.retire(0);
        variant.restore(0);

        assertThat(variant.everPublished()).isTrue();
        assertThatThrownBy(() -> variant.updateBarcode("000124", 0))
                .isInstanceOfSatisfying(com.shoecommerce.platform.api.BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_BARCODE_IMMUTABLE"));
        assertThat(variant.barcode()).isEqualTo("000123");
    }

    @Test
    void rejectsBarcodeLongerThanThePersistenceContract() {
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", NOW);

        assertThatThrownBy(() -> variant.updateBarcode("x".repeat(129), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
