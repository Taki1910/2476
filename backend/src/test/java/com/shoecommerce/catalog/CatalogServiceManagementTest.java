package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.pricing.VariantPrice;
import com.shoecommerce.pricing.VariantPriceRepository;

class CatalogServiceManagementTest {
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    @Test
    void mapsDuplicateSkuToAStableConflictAndFieldIdentifier() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        Product product = Product.create("Runner", NOW);
        when(products.findByPublicId(product.publicId())).thenReturn(Optional.of(product));
        when(variants.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UQ_catalog_product_variant_sku"));
        CatalogService service = service(products, variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class));

        assertThatThrownBy(() -> service.createVariant(mock(SessionPrincipal.class), product.publicId(),
                "RUN-41-BLK", "41", "Black"))
                .isInstanceOfSatisfying(BusinessConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("CATALOG_SKU_ALREADY_EXISTS");
                    assertThat(conflict.fieldErrors()).containsEntry("sku", "ALREADY_EXISTS");
                    assertThat(conflict.getMessage()).doesNotContain("private constraint");
                });
    }

    @Test
    void mapsDuplicateActiveOptionSeparatelyFromDuplicateSku() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        Product product = Product.create("Runner", NOW);
        when(products.findByPublicId(product.publicId())).thenReturn(Optional.of(product));
        when(variants.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UX_catalog_variant_active_option"));
        CatalogService service = service(products, variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class));

        assertThatThrownBy(() -> service.createVariant(mock(SessionPrincipal.class), product.publicId(),
                "RUN-41-BLK", "041.00", " black "))
                .isInstanceOfSatisfying(BusinessConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("CATALOG_OPTION_ALREADY_EXISTS");
                    assertThat(conflict.fieldErrors()).containsEntry("option", "ALREADY_EXISTS");
                    assertThat(conflict.getMessage()).doesNotContain("UX_catalog_variant_active_option");
                });
    }

    @Test
    void mapsDuplicateBarcodeToAStableConflictAndFieldIdentifier() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        Product product = Product.create("Runner", NOW);
        when(products.findByPublicId(product.publicId())).thenReturn(Optional.of(product));
        when(variants.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("UX_catalog_product_variant_barcode"));
        CatalogService service = service(products, variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class));

        assertThatThrownBy(() -> service.createVariant(mock(SessionPrincipal.class), product.publicId(),
                "RUN-41-BLK", "41", "Black", " 000123 "))
                .isInstanceOfSatisfying(BusinessConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("CATALOG_BARCODE_ALREADY_EXISTS");
                    assertThat(conflict.fieldErrors()).containsEntry("barcode", "ALREADY_EXISTS");
                    assertThat(conflict.getMessage()).doesNotContain("UX_catalog_product_variant_barcode");
                });
    }

    @Test
    void updatesDraftBarcodeUnderTheAuthoritativeVariantLock() {
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", NOW);
        when(variants.findLockedByPublicId(variant.publicId())).thenReturn(Optional.of(variant));
        when(variants.saveAndFlush(variant)).thenReturn(variant);
        CatalogService service = service(mock(ProductRepository.class), variants,
                mock(VariantPriceRepository.class), mock(InventoryBalanceRepository.class));

        service.updateBarcode(mock(SessionPrincipal.class), variant.publicId(), 0, " 000123 ");

        assertThat(variant.barcode()).isEqualTo("000123");
        verify(variants).saveAndFlush(variant);
    }

    @Test
    void publishesWithStableReadinessConflicts() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        VariantPriceRepository prices = mock(VariantPriceRepository.class);
        InventoryBalanceRepository balances = mock(InventoryBalanceRepository.class);
        Product product = Product.create("Runner", NOW);
        ProductVariant variant = ProductVariant.create(product, "RUN-41-BLK", "41", "Black", NOW);
        when(variants.findLockedByPublicId(variant.publicId())).thenReturn(Optional.of(variant));
        CatalogService service = service(products, variants, prices, balances);

        assertThatThrownBy(() -> service.publish(mock(SessionPrincipal.class), variant.publicId(), 0))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_PRICE_REQUIRED"));

        when(prices.findByVariant(variant)).thenReturn(Optional.of(mock(VariantPrice.class)));
        assertThatThrownBy(() -> service.publish(mock(SessionPrincipal.class), variant.publicId(), 0))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_ON_HAND_STOCK_REQUIRED"));

        verify(balances).existsPositiveOnHand(variant);
    }

    @Test
    void rejectsAStalePublishBeforeCheckingReadiness() {
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        VariantPriceRepository prices = mock(VariantPriceRepository.class);
        InventoryBalanceRepository balances = mock(InventoryBalanceRepository.class);
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW),
                "RUN-41-BLK", "41", "Black", NOW);
        when(variants.findLockedByPublicId(variant.publicId())).thenReturn(Optional.of(variant));
        CatalogService service = service(mock(ProductRepository.class), variants, prices, balances);

        assertThatThrownBy(() -> service.publish(mock(SessionPrincipal.class), variant.publicId(), 1))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("CATALOG_VARIANT_CHANGED"));

        verifyNoInteractions(prices, balances);
    }

    @Test
    void retiresAndRestoresUnderTheAuthoritativeVariantLock() {
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        ProductVariant variant = ProductVariant.create(Product.create("Runner", NOW), "RUN-41-BLK", "41", "Black", NOW);
        when(variants.findLockedByPublicId(variant.publicId())).thenReturn(Optional.of(variant));
        CatalogService service = service(mock(ProductRepository.class), variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class));

        service.retire(mock(SessionPrincipal.class), variant.publicId(), 0);
        assertThat(variant.status()).isEqualTo("RETIRED");
        service.restore(mock(SessionPrincipal.class), variant.publicId(), 0);
        assertThat(variant.status()).isEqualTo("DRAFT");
    }

    private static CatalogService service(ProductRepository products, ProductVariantRepository variants,
            VariantPriceRepository prices, InventoryBalanceRepository balances) {
        return new CatalogService(products, variants, prices, balances, mock(AuthorizationPolicy.class),
                mock(AuditWriter.class), mock(StorefrontCatalogService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
