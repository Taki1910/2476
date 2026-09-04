package com.shoecommerce.catalog;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.pricing.VariantPrice;
import com.shoecommerce.pricing.VariantPriceRepository;

@Service
public class CatalogService {
    private final ProductRepository products; private final ProductVariantRepository variants; private final VariantPriceRepository prices; private final InventoryBalanceRepository balances; private final AuthorizationPolicy authorization; private final AuditWriter audit; private final Clock clock;
    public CatalogService(ProductRepository products, ProductVariantRepository variants, VariantPriceRepository prices, InventoryBalanceRepository balances, AuthorizationPolicy authorization, AuditWriter audit, Clock clock) { this.products = products; this.variants = variants; this.prices = prices; this.balances = balances; this.authorization = authorization; this.audit = audit; this.clock = clock; }
    @Transactional public UUID createProduct(SessionPrincipal actor, String name) { authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE); Product product = products.save(Product.create(name, clock.instant())); audit.append(actor, "PRODUCT_CREATED", "PRODUCT", product.publicId(), null, null, Map.of()); return product.publicId(); }
    @Transactional public UUID createVariant(SessionPrincipal actor, UUID productId, String sku, String size, String color) { authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE); Product product = products.findByPublicId(productId).orElseThrow(() -> new IllegalArgumentException("Product not found")); ProductVariant variant = variants.save(ProductVariant.create(product, sku, size, color, clock.instant())); audit.append(actor, "VARIANT_CREATED", "PRODUCT_VARIANT", variant.publicId(), null, null, Map.of("sku", variant.sku())); return variant.publicId(); }
    @Transactional public void setPrice(SessionPrincipal actor, UUID variantId, long amount) { authorization.requirePermission(actor, PermissionCode.PRICE_MANAGE); ProductVariant variant = variants.findLockedByPublicId(variantId).orElseThrow(() -> new IllegalArgumentException("Variant not found")); var current = prices.findByVariant(variant); var effectiveAt = clock.instant(); if (current.isPresent()) { if (!effectiveAt.isAfter(current.get().validFrom())) effectiveAt = current.get().validFrom().plusNanos(1_000); current.get().close(effectiveAt); prices.saveAndFlush(current.get()); } prices.save(VariantPrice.create(variant, amount, effectiveAt)); audit.append(actor, "VARIANT_PRICE_SET", "PRODUCT_VARIANT", variant.publicId(), null, null, Map.of("amount", amount, "currency", "VND")); }
    @Transactional public void publish(SessionPrincipal actor, UUID variantId) { authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE); ProductVariant variant = variant(variantId); if (prices.findByVariant(variant).isEmpty()) throw new IllegalStateException("Variant requires a price before publication"); if (!balances.existsPublishableStock(variant)) throw new IllegalStateException("Variant requires stock before publication"); variant.publish(); audit.append(actor, "VARIANT_PUBLISHED", "PRODUCT_VARIANT", variant.publicId(), null, null, Map.of("sku", variant.sku())); }
    @Transactional(readOnly = true) public PublishedVariant readPublished(SessionPrincipal actor, UUID variantId) { authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE); ProductVariant variant = variant(variantId); if (!variant.published()) throw new IllegalArgumentException("Published variant not found"); VariantPrice price = prices.findByVariant(variant).orElseThrow(() -> new IllegalStateException("Published variant has no price")); return new PublishedVariant(variant.publicId(), variant.product().publicId(), variant.product().name(), variant.sku(), variant.size(), variant.color(), price.amount(), "VND"); }
    private ProductVariant variant(UUID id) { return variants.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Variant not found")); }
    public record PublishedVariant(UUID id, UUID productId, String productName, String sku, String size, String color, long priceAmount, String currency) { }
}
