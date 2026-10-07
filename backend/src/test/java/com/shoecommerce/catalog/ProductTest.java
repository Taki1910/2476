package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.shoecommerce.platform.api.BusinessConflictException;

class ProductTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void createsAResumableProductAndUpdatesOnlyApprovedIdentity() {
        Product product = Product.create(" Runner ", " Shoes ", " Fall ", " hero.jpg ", " primary.jpg ", NOW);

        assertThat(product.name()).isEqualTo("Runner");
        assertThat(product.category()).isEqualTo("Shoes");
        assertThat(product.collection()).isEqualTo("Fall");
        assertThat(product.heroImage()).isEqualTo("hero.jpg");
        assertThat(product.primaryImage()).isEqualTo("primary.jpg");

        Product.Identity before = product.updateIdentity("Runner 2", "Lifestyle", null, null, "new.jpg", 0);

        assertThat(before.name()).isEqualTo("Runner");
        assertThat(product.name()).isEqualTo("Runner 2");
        assertThat(product.collection()).isNull();
        assertThat(product.heroImage()).isNull();
        assertThat(product.primaryImage()).isEqualTo("new.jpg");
    }

    @Test
    void rejectsAStaleProductUpdate() {
        Product product = Product.create("Runner", NOW);

        assertThatThrownBy(() -> product.updateIdentity("Changed", null, null, null, null, 1))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_PRODUCT_CHANGED"));
    }
}
