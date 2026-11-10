package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.pricing.VariantPriceRepository;

class CatalogServiceEvidenceTest {
    @Test
    void batchesColorsAndFitForTheManagementList() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        StorefrontCatalogService storefront = mock(StorefrontCatalogService.class);
        Product first = Product.create("Court", Instant.parse("2026-09-24T00:00:00Z"));
        Product second = Product.create("Trail", Instant.parse("2026-09-24T00:00:00Z"));
        ProductVariantRepository.ProductColor color = mock(ProductVariantRepository.ProductColor.class);
        when(color.getProductId()).thenReturn(first.publicId());
        when(color.getColor()).thenReturn("White");
        when(products.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(first, second));
        when(variants.findDistinctProductColors()).thenReturn(List.of(color));
        when(storefront.fitSummaries()).thenReturn(Map.of(first.publicId(),
                new StorefrontCatalogService.FitSummary("TRUE_TO_SIZE", "REGULAR")));
        CatalogService service = new CatalogService(products, variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class), mock(AuthorizationPolicy.class), mock(AuditWriter.class),
                storefront, Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));

        List<CatalogService.ProductEvidenceState> states = service.products(mock(SessionPrincipal.class));

        assertThat(states).extracting(CatalogService.ProductEvidenceState::availableColors)
                .containsExactly(List.of("White"), List.of());
        assertThat(states.getFirst().fitProfile()).isNotNull();
        verify(variants, never()).findDistinctColorsByProduct(any());
        verify(storefront, never()).fitSummary(any());
    }

    @Test
    void replacesEvidenceAndWritesOneSemanticAuditEvent() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        VariantPriceRepository prices = mock(VariantPriceRepository.class);
        InventoryBalanceRepository balances = mock(InventoryBalanceRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        AuditWriter audit = mock(AuditWriter.class);
        StorefrontCatalogService storefront = mock(StorefrontCatalogService.class);
        Product product = Product.create("Runner", Instant.parse("2026-09-24T00:00:00Z"));
        when(products.findByPublicId(product.publicId())).thenReturn(java.util.Optional.of(product));
        when(products.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CatalogService service = new CatalogService(products, variants, prices, balances, authorization, audit, storefront,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));
        ProductEvidence evidence = new ProductEvidence(ProductEvidence.IntendedUse.ROAD_RUNNING,
                ProductEvidence.PrimarySurface.ROAD_TRACK, ProductEvidence.UpperConstruction.MESH_TEXTILE,
                ProductEvidence.CutProfile.LOW_TOP, ProductEvidence.PrimarySoleProfile.FLEX_GROOVED);

        CatalogService.ProductEvidenceState state = service.replaceEvidence(mock(SessionPrincipal.class),
                product.publicId(), 0, evidence);

        assertThat(state.evidence()).isEqualTo(evidence);
        verify(authorization).requirePermission(any(), eq(PermissionCode.CATALOG_MANAGE));
        verify(audit).append(any(), eq("PRODUCT_EVIDENCE_UPDATED"), eq("PRODUCT"), eq(product.publicId()),
                eq(null), eq(null), eq(Map.of("before", ProductEvidence.empty(), "after", evidence)));
    }

    @Test
    void mapsAPersistenceLevelOptimisticLockFailureToTheEvidenceConflictContract() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        Product product = Product.create("Runner", Instant.parse("2026-09-24T00:00:00Z"));
        when(products.findByPublicId(product.publicId())).thenReturn(java.util.Optional.of(product));
        when(products.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));
        CatalogService service = new CatalogService(products, variants, mock(VariantPriceRepository.class),
                mock(InventoryBalanceRepository.class), mock(AuthorizationPolicy.class), mock(AuditWriter.class),
                mock(StorefrontCatalogService.class), Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.replaceEvidence(mock(SessionPrincipal.class), product.publicId(), 0,
                new ProductEvidence(ProductEvidence.IntendedUse.ROAD_RUNNING, null, null, null, null)))
                .isInstanceOfSatisfying(com.shoecommerce.platform.api.BusinessConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("PRODUCT_EVIDENCE_CONFLICT"));
    }
}
