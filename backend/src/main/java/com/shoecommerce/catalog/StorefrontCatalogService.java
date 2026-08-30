package com.shoecommerce.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class StorefrontCatalogService {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final Clock clock;
    private final CheckoutHoldExpiryService checkoutExpiry;

    public StorefrontCatalogService(JdbcTemplate jdbc, AuthorizationPolicy authorization, Clock clock,
            CheckoutHoldExpiryService checkoutExpiry) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.clock = clock;
        this.checkoutExpiry = checkoutExpiry;
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> browse(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_BROWSE);
        normalizeExpiredCheckoutHolds(null);
        Timestamp at = Timestamp.from(clock.instant());
        return jdbc.query("""
                WITH visible AS (
                    SELECT products.public_id AS product_public_id, products.name,
                           variants.id AS variant_id,
                           CASE WHEN EXISTS (
                               SELECT 1
                               FROM inventory_balance balances
                               JOIN org_location locations ON locations.id = balances.location_id
                               JOIN org_branch branches ON branches.id = locations.branch_id
                               WHERE balances.variant_id = variants.id
                                 AND locations.enabled = 1 AND branches.enabled = 1
                                 AND balances.on_hand > balances.reserved
                           ) THEN 1 ELSE 0 END AS available
                    FROM catalog_product products
                    JOIN catalog_product_variant variants ON variants.product_id = products.id
                    JOIN pricing_variant_price prices ON prices.variant_id = variants.id
                    WHERE variants.lifecycle_status = 'PUBLISHED'
                      AND prices.valid_from <= ?
                      AND (prices.valid_to IS NULL OR prices.valid_to > ?)
                )
                SELECT product_public_id, name, COUNT_BIG(*) AS variant_count,
                       SUM(available) AS available_variant_count
                FROM visible
                GROUP BY product_public_id, name
                ORDER BY name, product_public_id
                """, (rs, row) -> new ProductSummary(
                        UUID.fromString(rs.getString("product_public_id")),
                        rs.getString("name"),
                        rs.getLong("variant_count"),
                        rs.getLong("available_variant_count")), at, at);
    }

    @Transactional(readOnly = true)
    public ProductDetail detail(SessionPrincipal actor, UUID productId) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_BROWSE);
        normalizeExpiredCheckoutHolds(productId);
        Timestamp at = Timestamp.from(clock.instant());
        List<VariantView> variants = jdbc.query("""
                SELECT products.public_id AS product_public_id, products.name,
                       variants.public_id AS variant_public_id, variants.sku, variants.size, variants.color,
                       CASE WHEN EXISTS (
                           SELECT 1
                           FROM inventory_balance balances
                           JOIN org_location locations ON locations.id = balances.location_id
                           JOIN org_branch branches ON branches.id = locations.branch_id
                           WHERE balances.variant_id = variants.id
                             AND locations.enabled = 1 AND branches.enabled = 1
                             AND balances.on_hand > balances.reserved
                       ) THEN 'AVAILABLE' ELSE 'UNAVAILABLE' END AS availability
                FROM catalog_product products
                JOIN catalog_product_variant variants ON variants.product_id = products.id
                JOIN pricing_variant_price prices ON prices.variant_id = variants.id
                WHERE products.public_id = ?
                  AND variants.lifecycle_status = 'PUBLISHED'
                  AND prices.valid_from <= ?
                  AND (prices.valid_to IS NULL OR prices.valid_to > ?)
                ORDER BY TRY_CONVERT(DECIMAL(10,2), variants.size), variants.size, variants.color, variants.public_id
                """, (rs, row) -> new VariantView(
                        UUID.fromString(rs.getString("variant_public_id")),
                        rs.getString("sku"),
                        rs.getString("size"),
                        rs.getString("color"),
                        rs.getString("availability")), productId, at, at);
        if (variants.isEmpty()) throw new ResourceNotFoundException("STOREFRONT_PRODUCT_NOT_FOUND", "Product not found.");
        String name = jdbc.queryForObject("SELECT name FROM catalog_product WHERE public_id = ?", String.class, productId);
        return new ProductDetail(productId, name, variants);
    }

    public record ProductSummary(UUID id, String name, long variantCount, long availableVariantCount) { }
    public record ProductDetail(UUID id, String name, List<VariantView> variants) { }
    public record VariantView(UUID id, String sku, String size, String color, String availability) { }

    private void normalizeExpiredCheckoutHolds(UUID productId) {
        String productFilter = productId == null ? "" : " AND products.public_id = ?";
        Object[] parameters = productId == null
                ? new Object[] { Timestamp.from(clock.instant()) }
                : new Object[] { Timestamp.from(clock.instant()), productId };
        List<UUID> variants = jdbc.query("""
                SELECT DISTINCT variants.public_id
                FROM inventory_reservation reservations
                JOIN commerce_order orders ON orders.reservation_public_id = reservations.public_id
                JOIN catalog_product_variant variants ON variants.id = reservations.variant_id
                JOIN catalog_product products ON products.id = variants.product_id
                WHERE reservations.status = 'ADOPTED'
                  AND reservations.expires_at <= ?
                  AND orders.status = 'PENDING_PAYMENT'
                  AND orders.price_quote_public_id IS NOT NULL
                  AND variants.lifecycle_status = 'PUBLISHED'
                """ + productFilter, (rs, row) -> UUID.fromString(rs.getString(1)), parameters);
        variants.forEach(checkoutExpiry::expireForVariant);
    }
}
