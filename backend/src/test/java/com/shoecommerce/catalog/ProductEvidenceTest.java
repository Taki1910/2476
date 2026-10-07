package com.shoecommerce.catalog;

import static com.shoecommerce.catalog.ProductEvidence.CutProfile.HIGH_TOP;
import static com.shoecommerce.catalog.ProductEvidence.IntendedUse.CASUAL_DAILY;
import static com.shoecommerce.catalog.ProductEvidence.PrimarySoleProfile.CUPSOLE;
import static com.shoecommerce.catalog.ProductEvidence.PrimarySurface.SMOOTH_HARD_SURFACE;
import static com.shoecommerce.catalog.ProductEvidence.UpperConstruction.CANVAS_TEXTILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.shoecommerce.platform.api.BusinessConflictException;

class ProductEvidenceTest {
    @Test
    void replacesAllProductEvidenceOnlyAtTheExpectedEntityVersion() {
        Product product = Product.create("Court High", Instant.parse("2026-09-24T00:00:00Z"));
        ProductEvidence evidence = new ProductEvidence(
                CASUAL_DAILY, SMOOTH_HARD_SURFACE, CANVAS_TEXTILE, HIGH_TOP, CUPSOLE);

        ProductEvidence before = product.replaceEvidence(evidence, 0);

        assertThat(before).isEqualTo(ProductEvidence.empty());
        assertThat(product.evidence()).isEqualTo(evidence);
        assertThatThrownBy(() -> product.replaceEvidence(ProductEvidence.empty(), 1))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("PRODUCT_EVIDENCE_CONFLICT");
        assertThat(product.evidence()).isEqualTo(evidence);
    }

    @Test
    void recognizesOnlyAnEntirelyUnknownEvidenceSetAsEmpty() {
        assertThat(ProductEvidence.empty().isEmpty()).isTrue();
        assertThat(new ProductEvidence(CASUAL_DAILY, null, null, null, null).isEmpty()).isFalse();
    }
}
