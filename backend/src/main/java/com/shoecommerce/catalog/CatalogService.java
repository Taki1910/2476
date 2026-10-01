package com.shoecommerce.catalog;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.pricing.VariantPrice;
import com.shoecommerce.pricing.VariantPriceRepository;

@Service
public class CatalogService {
    private final ProductRepository products; private final ProductVariantRepository variants; private final VariantPriceRepository prices; private final InventoryBalanceRepository balances; private final AuthorizationPolicy authorization; private final AuditWriter audit; private final StorefrontCatalogService storefront; private final Clock clock;
    public CatalogService(ProductRepository products, ProductVariantRepository variants, VariantPriceRepository prices, InventoryBalanceRepository balances, AuthorizationPolicy authorization, AuditWriter audit, StorefrontCatalogService storefront, Clock clock) { this.products = products; this.variants = variants; this.prices = prices; this.balances = balances; this.authorization = authorization; this.audit = audit; this.storefront = storefront; this.clock = clock; }
    @Transactional public UUID createProduct(SessionPrincipal actor, String name) {
        return createProduct(actor, name, null, null, null, null).id();
    }
    @Transactional public ProductState createProduct(SessionPrincipal actor, String name, String category,
            String collection, String heroImage, String primaryImage) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        Product product = products.save(Product.create(name, category, collection, heroImage, primaryImage,
                clock.instant()));
        audit.append(actor, "PRODUCT_CREATED", "PRODUCT", product.publicId(), null, null, Map.of());
        return productState(product);
    }
    @Transactional public ProductState updateProduct(SessionPrincipal actor, UUID productId,
            long expectedEntityVersion, String name, String category, String collection, String heroImage,
            String primaryImage) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        Product product = product(productId);
        Product.Identity before = product.updateIdentity(name, category, collection, heroImage, primaryImage,
                expectedEntityVersion);
        try {
            products.saveAndFlush(product);
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new BusinessConflictException("CATALOG_PRODUCT_CHANGED",
                    "The Product changed. Reload the latest state and retry.");
        }
        audit.append(actor, "PRODUCT_UPDATED", "PRODUCT", product.publicId(), null, null,
                Map.of("before", before, "after", product.identity()));
        return productState(product);
    }
    @Transactional public UUID createVariant(SessionPrincipal actor, UUID productId, String sku, String size, String color) {
        return createVariant(actor, productId, sku, size, color, null);
    }
    @Transactional public UUID createVariant(SessionPrincipal actor, UUID productId, String sku, String size,
            String color, String barcode) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        Product product = product(productId);
        ProductVariant variant;
        try {
            variant = variants.saveAndFlush(ProductVariant.create(product, sku, size, color, barcode, clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            throw variantConflict(duplicate);
        }
        Map<String, Object> details = new HashMap<>();
        details.put("sku", variant.sku());
        if (variant.barcode() != null) details.put("barcode", variant.barcode());
        audit.append(actor, "VARIANT_CREATED", "PRODUCT_VARIANT", variant.publicId(), null, null, details);
        return variant.publicId();
    }
    @Transactional public void updateBarcode(SessionPrincipal actor, UUID variantId, long expectedEntityVersion,
            String barcode) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        ProductVariant variant = lockedVariant(variantId);
        String before = variant.barcode();
        variant.updateBarcode(barcode, expectedEntityVersion);
        try {
            variants.saveAndFlush(variant);
        } catch (DataIntegrityViolationException duplicate) {
            throw variantConflict(duplicate);
        } catch (OptimisticLockingFailureException | OptimisticLockException changed) {
            throw new BusinessConflictException("CATALOG_VARIANT_CHANGED",
                    "The Variant changed. Reload the latest state and retry.");
        }
        Map<String, Object> details = new HashMap<>();
        details.put("before", before);
        details.put("after", variant.barcode());
        audit.append(actor, "VARIANT_BARCODE_UPDATED", "PRODUCT_VARIANT", variant.publicId(), null, null, details);
    }
    @Transactional public void setPrice(SessionPrincipal actor, UUID variantId, long amount) { authorization.requirePermission(actor, PermissionCode.PRICE_MANAGE); ProductVariant variant = variants.findLockedByPublicId(variantId).orElseThrow(() -> new IllegalArgumentException("Variant not found")); var current = prices.findByVariant(variant); var effectiveAt = clock.instant(); if (current.isPresent()) { if (!effectiveAt.isAfter(current.get().validFrom())) effectiveAt = current.get().validFrom().plusNanos(1_000); current.get().close(effectiveAt); prices.saveAndFlush(current.get()); } prices.save(VariantPrice.create(variant, amount, effectiveAt)); audit.append(actor, "VARIANT_PRICE_SET", "PRODUCT_VARIANT", variant.publicId(), null, null, Map.of("amount", amount, "currency", "VND")); }
    @Transactional public void publish(SessionPrincipal actor, UUID variantId) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        ProductVariant variant = lockedVariant(variantId);
        publish(actor, variant, variant.version());
    }
    @Transactional public void publish(SessionPrincipal actor, UUID variantId, long expectedEntityVersion) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        publish(actor, lockedVariant(variantId), expectedEntityVersion);
    }
    private void publish(SessionPrincipal actor, ProductVariant variant, long expectedEntityVersion) {
        transition(() -> variant.requirePublishable(expectedEntityVersion));
        if (prices.findByVariant(variant).isEmpty()) {
            throw new BusinessConflictException("CATALOG_PRICE_REQUIRED",
                    "Variant requires a current price before publication.");
        }
        if (!balances.existsPositiveOnHand(variant)) {
            throw new BusinessConflictException("CATALOG_ON_HAND_STOCK_REQUIRED",
                    "Variant requires positive on-hand stock before publication.");
        }
        variant.publish(expectedEntityVersion);
        audit.append(actor, "VARIANT_PUBLISHED", "PRODUCT_VARIANT", variant.publicId(), null, null,
                Map.of("sku", variant.sku()));
    }
    @Transactional public void retire(SessionPrincipal actor, UUID variantId, long expectedEntityVersion) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        ProductVariant variant = lockedVariant(variantId);
        transition(() -> variant.retire(expectedEntityVersion));
        audit.append(actor, "VARIANT_RETIRED", "PRODUCT_VARIANT", variant.publicId(), null, null,
                Map.of("sku", variant.sku()));
    }
    @Transactional public void restore(SessionPrincipal actor, UUID variantId, long expectedEntityVersion) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        ProductVariant variant = lockedVariant(variantId);
        transition(() -> variant.restore(expectedEntityVersion));
        try {
            variants.saveAndFlush(variant);
        } catch (DataIntegrityViolationException duplicate) {
            throw variantConflict(duplicate);
        }
        audit.append(actor, "VARIANT_RESTORED_TO_DRAFT", "PRODUCT_VARIANT", variant.publicId(), null, null,
                Map.of("sku", variant.sku()));
    }
    @Transactional(readOnly = true) public PublishedVariant readPublished(SessionPrincipal actor, UUID variantId) { authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE); ProductVariant variant = variant(variantId); if (!variant.published()) throw new IllegalArgumentException("Published variant not found"); VariantPrice price = prices.findByVariant(variant).orElseThrow(() -> new IllegalStateException("Published variant has no price")); return new PublishedVariant(variant.publicId(), variant.product().publicId(), variant.product().name(), variant.sku(), variant.size(), variant.color(), price.amount(), "VND"); }
    @Transactional(readOnly = true) public ProductEvidenceState evidence(SessionPrincipal actor, UUID productId) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        return evidenceState(product(productId));
    }
    @Transactional(readOnly = true) public List<ProductEvidenceState> products(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        Map<UUID, List<String>> colors = new HashMap<>();
        variants.findDistinctProductColors().forEach(row -> colors
                .computeIfAbsent(row.getProductId(), ignored -> new java.util.ArrayList<>()).add(row.getColor()));
        Map<UUID, StorefrontCatalogService.FitSummary> fits = storefront.fitSummaries();
        return products.findAll(Sort.by("name")).stream().map(product -> evidenceState(product,
                colors.getOrDefault(product.publicId(), List.of()), fits.get(product.publicId()))).toList();
    }
    @Transactional public ProductEvidenceState replaceEvidence(SessionPrincipal actor, UUID productId, long expectedEntityVersion, ProductEvidence evidence) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        Product product = product(productId);
        ProductEvidence before = product.replaceEvidence(evidence, expectedEntityVersion);
        try {
            products.saveAndFlush(product);
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw evidenceConflict();
        }
        audit.append(actor, "PRODUCT_EVIDENCE_UPDATED", "PRODUCT", product.publicId(), null, null,
                Map.of("before", before, "after", product.evidence()));
        return evidenceState(product);
    }
    private Product product(UUID id) { return products.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found.")); }
    private ProductEvidenceState evidenceState(Product product) {
        return evidenceState(product, variants.findDistinctColorsByProduct(product),
                storefront.fitSummary(product.publicId()));
    }
    private ProductEvidenceState evidenceState(Product product, List<String> colors,
            StorefrontCatalogService.FitSummary fit) {
        ProductEvidence evidence = product.evidence();
        return new ProductEvidenceState(product.publicId(), product.name(), product.category(), product.collection(),
                colors, fit,
                evidence.isEmpty() ? null : evidence, product.version());
    }
    private BusinessConflictException evidenceConflict() { return new BusinessConflictException("PRODUCT_EVIDENCE_CONFLICT", "The Product evidence changed. Reload the latest state and retry."); }
    private static void transition(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException invalid) {
            throw new BusinessConflictException("CATALOG_VARIANT_STATE_CONFLICT", invalid.getMessage());
        }
    }
    private static BusinessConflictException variantConflict(DataIntegrityViolationException conflict) {
        String details = conflict.toString();
        Throwable cause = conflict.getCause();
        while (cause != null) { details += " " + cause; cause = cause.getCause(); }
        if (details.contains("UX_catalog_variant_active_option")) {
            return new BusinessConflictException("CATALOG_OPTION_ALREADY_EXISTS",
                    "An active variant already uses this Product, color, and EU size.", null,
                    Map.of("option", "ALREADY_EXISTS"));
        }
        if (details.contains("UQ_catalog_product_variant_sku")) {
            return new BusinessConflictException("CATALOG_SKU_ALREADY_EXISTS",
                    "A variant already uses this SKU.", null, Map.of("sku", "ALREADY_EXISTS"));
        }
        if (details.contains("UX_catalog_product_variant_barcode")) {
            return new BusinessConflictException("CATALOG_BARCODE_ALREADY_EXISTS",
                    "A variant already uses this barcode.", null, Map.of("barcode", "ALREADY_EXISTS"));
        }
        throw conflict;
    }
    private ProductState productState(Product product) {
        return new ProductState(product.publicId(), product.name(), product.category(), product.collection(),
                product.heroImage(), product.primaryImage(), product.version());
    }
    private ProductVariant variant(UUID id) { return variants.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Variant not found")); }
    private ProductVariant lockedVariant(UUID id) { return variants.findLockedByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("VARIANT_NOT_FOUND", "Variant not found.")); }
    public record PublishedVariant(UUID id, UUID productId, String productName, String sku, String size, String color, long priceAmount, String currency) { }
    public record ProductState(UUID id, String name, String category, String collection, String heroImage,
            String primaryImage, long entityVersion) { }
    public record ProductEvidenceState(UUID productId, String name, String category, String collection,
            List<String> availableColors, StorefrontCatalogService.FitSummary fitProfile,
            ProductEvidence evidence, long entityVersion) { }
}
