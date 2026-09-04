package com.shoecommerce.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class StorefrontCatalogService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CheckoutHoldExpiryService checkoutExpiry;

    public StorefrontCatalogService(JdbcTemplate jdbc, Clock clock,
            CheckoutHoldExpiryService checkoutExpiry) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.checkoutExpiry = checkoutExpiry;
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> browse() {
        return browse(null);
    }

    @Transactional(readOnly = true)
    public List<ProductSummary> browse(String query) {
        normalizeExpiredCheckoutHolds(null);
        String search = query == null ? "" : query.trim();
        if (search.length() > 80) throw new IllegalArgumentException("Search query is too long");
        Timestamp at = Timestamp.from(clock.instant());
        String filter = search.isBlank() ? "" : """
                      AND (CHARINDEX(?, LOWER(products.name)) > 0
                           OR CHARINDEX(?, LOWER(variants.sku)) > 0
                           OR CHARINDEX(?, LOWER(variants.color)) > 0
                           OR CHARINDEX(?, LOWER(products.category)) > 0)
                """;
        List<Object> parameters = new ArrayList<>();
        if (!search.isBlank()) {
            String term = search.toLowerCase(Locale.ROOT);
            parameters.add(term); parameters.add(term); parameters.add(term); parameters.add(term);
        }
        parameters.add(at); parameters.add(at);
        String sql = """
                WITH visible AS (
                    SELECT products.public_id AS product_public_id, products.name,
                           products.category, products.collection, products.featured,
                           products.new_arrival, products.campaign_eligible,
                           products.merchandising_rank, products.hero_image, products.primary_image,
                           variants.id AS variant_id, prices.amount,
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
                """ + filter + """
                      AND prices.valid_from <= ?
                      AND (prices.valid_to IS NULL OR prices.valid_to > ?)
                )
                SELECT product_public_id, name, category, collection, featured, new_arrival,
                       campaign_eligible, merchandising_rank, hero_image, primary_image,
                       COUNT_BIG(*) AS variant_count,
                       SUM(available) AS available_variant_count, MIN(amount) AS from_amount
                FROM visible
                GROUP BY product_public_id, name, category, collection, featured, new_arrival,
                         campaign_eligible, merchandising_rank, hero_image, primary_image
                ORDER BY merchandising_rank, name, product_public_id
                """;
        return jdbc.query(sql, (rs, row) -> new ProductSummary(
                        UUID.fromString(rs.getString("product_public_id")),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("collection"),
                        rs.getBoolean("featured"),
                        rs.getBoolean("new_arrival"),
                        rs.getBoolean("campaign_eligible"),
                        rs.getInt("merchandising_rank"),
                        rs.getString("hero_image"),
                        rs.getString("primary_image"),
                        rs.getLong("variant_count"),
                        rs.getLong("available_variant_count"),
                        rs.getLong("from_amount")), parameters.toArray());
    }

    @Transactional(readOnly = true)
    public ProductDetail detail(UUID productId) {
        normalizeExpiredCheckoutHolds(productId);
        Timestamp at = Timestamp.from(clock.instant());
        List<VariantView> variants = jdbc.query("""
                SELECT products.public_id AS product_public_id, products.name,
                       variants.public_id AS variant_public_id, variants.sku, variants.size, variants.color,
                       prices.amount,
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
                        rs.getString("availability"),
                        rs.getLong("amount")), productId, at, at);
        if (variants.isEmpty()) throw new ResourceNotFoundException("STOREFRONT_PRODUCT_NOT_FOUND", "Product not found.");
        ProductMetadata metadata = jdbc.queryForObject("""
                SELECT name, category, collection, featured, new_arrival, campaign_eligible,
                       merchandising_rank, hero_image, primary_image,
                        CASE WHEN EXISTS (
                            SELECT 1 FROM catalog_shoe_fit_profile fit_profiles
                            WHERE fit_profiles.product_id = catalog_product.id
                              AND NOT EXISTS (
                                  SELECT 1 FROM catalog_product_variant variants
                                  WHERE variants.product_id = catalog_product.id
                                    AND variants.lifecycle_status = 'PUBLISHED'
                                    AND NOT EXISTS (
                                        SELECT 1 FROM catalog_shoe_fit_size_range size_ranges
                                        WHERE size_ranges.profile_id = fit_profiles.id AND size_ranges.size_label = variants.size
                                    )
                              )
                        ) THEN 1 ELSE 0 END AS fit_supported
                FROM catalog_product WHERE public_id = ?
                """, (rs, row) -> new ProductMetadata(rs.getString("name"), rs.getString("category"),
                        rs.getString("collection"), rs.getBoolean("featured"), rs.getBoolean("new_arrival"),
                        rs.getBoolean("campaign_eligible"), rs.getInt("merchandising_rank"),
                        rs.getString("hero_image"), rs.getString("primary_image"), rs.getBoolean("fit_supported")), productId);
        return new ProductDetail(productId, metadata.name(), metadata.category(), metadata.collection(),
                metadata.featured(), metadata.newArrival(), metadata.campaignEligible(),
                metadata.merchandisingRank(), metadata.heroImage(), metadata.primaryImage(), metadata.fitSupported(), variants);
    }

    @Transactional(readOnly = true)
    public HeroCarousel hero() {
        normalizeExpiredCheckoutHolds(null);
        Timestamp at = Timestamp.from(clock.instant());
        List<HeroProduct> products = jdbc.query("""
                WITH sales AS (
                    SELECT items.variant_public_id, items.quantity, attempts.resolved_at AS sold_at,
                           items.unit_price_amount * items.quantity AS amount
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'SUCCEEDED'
                      AND NOT EXISTS (
                          SELECT 1 FROM payment_void_allocation allocations
                          WHERE allocations.component_type = 'ORDER_ITEM'
                            AND allocations.component_public_id = items.public_id
                            AND allocations.status = 'SUCCEEDED'
                      )
                    UNION ALL
                    SELECT items.variant_public_id, items.quantity, tenders.created_at AS sold_at,
                           items.unit_price_amount * items.quantity AS amount
                    FROM cash_tender tenders
                    JOIN commerce_order orders ON orders.id = tenders.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE NOT EXISTS (
                          SELECT 1 FROM payment_void_allocation allocations
                          WHERE allocations.component_type = 'ORDER_ITEM'
                            AND allocations.component_public_id = items.public_id
                            AND allocations.status = 'SUCCEEDED'
                    )
                ), metrics AS (
                    SELECT variants.product_id,
                           SUM(CASE WHEN sales.sold_at >= DATEADD(DAY, -30, ?) THEN sales.quantity ELSE 0 END) AS recent_30_day_units,
                           SUM(CASE WHEN sales.sold_at >= DATEADD(DAY, -30, ?) THEN sales.amount ELSE 0 END) AS recent_30_day_revenue,
                           SUM(CASE WHEN sales.sold_at >= DATEADD(DAY, -7, ?) THEN sales.quantity ELSE 0 END) AS last_7_day_units,
                           SUM(CASE WHEN sales.sold_at >= DATEADD(DAY, -14, ?) AND sales.sold_at < DATEADD(DAY, -7, ?) THEN sales.quantity ELSE 0 END) AS previous_7_day_units
                    FROM sales
                    JOIN catalog_product_variant variants ON variants.public_id = sales.variant_public_id
                    WHERE sales.sold_at < ?
                    GROUP BY variants.product_id
                )
                SELECT products.public_id AS product_public_id, products.name, products.category, products.collection,
                       products.featured, products.new_arrival, products.campaign_eligible,
                       products.merchandising_rank, products.hero_image, products.primary_image,
                       COALESCE(metrics.recent_30_day_units, 0) AS recent_30_day_units,
                       COALESCE(metrics.recent_30_day_revenue, 0) AS recent_30_day_revenue,
                       COALESCE(metrics.last_7_day_units, 0) AS last_7_day_units,
                       COALESCE(metrics.previous_7_day_units, 0) AS previous_7_day_units,
                       COALESCE(metrics.last_7_day_units, 0) - COALESCE(metrics.previous_7_day_units, 0) AS growth_units
                FROM catalog_product products
                LEFT JOIN metrics ON metrics.product_id = products.id
                WHERE EXISTS (
                    SELECT 1
                    FROM catalog_product_variant variants
                    JOIN pricing_variant_price prices ON prices.variant_id = variants.id
                    WHERE variants.product_id = products.id
                      AND variants.lifecycle_status = 'PUBLISHED'
                      AND prices.valid_from <= ?
                      AND (prices.valid_to IS NULL OR prices.valid_to > ?)
                )
                ORDER BY products.merchandising_rank, products.name, products.public_id
                """, (rs, row) -> new HeroProduct(
                        rs.getObject("product_public_id", UUID.class), rs.getString("name"),
                        rs.getString("category"), rs.getString("collection"), rs.getBoolean("featured"),
                        rs.getBoolean("new_arrival"), rs.getBoolean("campaign_eligible"),
                        rs.getInt("merchandising_rank"), rs.getString("hero_image"),
                        rs.getString("primary_image"), rs.getLong("recent_30_day_units"),
                        rs.getLong("recent_30_day_revenue"), rs.getLong("last_7_day_units"),
                        rs.getLong("previous_7_day_units"), rs.getLong("growth_units")),
                at, at, at, at, at, at, at, at);
        Set<UUID> used = new HashSet<>();
        HeroProduct topSeller = products.stream().filter(product -> product.recent30DayUnits() > 0)
                .max(Comparator.comparingLong(HeroProduct::recent30DayUnits)
                        .thenComparingLong(HeroProduct::recent30DayRevenue))
                .orElse(null);
        markUsed(used, topSeller);
        HeroProduct trending = pick(products.stream().filter(product -> product.growthUnits() > 0).toList(), used,
                Comparator.comparingLong(HeroProduct::growthUnits).reversed()
                        .thenComparing(Comparator.comparingLong(HeroProduct::last7DayUnits).reversed()));
        HeroProduct newArrival = pick(products.stream().filter(HeroProduct::newArrival).toList(), used,
                Comparator.comparingInt(HeroProduct::merchandisingRank));
        HeroProduct featuredCollection = pick(products.stream().filter(HeroProduct::featured).toList(), used,
                Comparator.comparingInt(HeroProduct::merchandisingRank));
        return new HeroCarousel(topSeller, trending, newArrival, featuredCollection, products);
    }

    public record ProductSummary(UUID id, String name, String category, String collection, boolean featured,
            boolean newArrival, boolean campaignEligible, int merchandisingRank, String heroImage,
            String primaryImage, long variantCount, long availableVariantCount, long fromAmount) { }
    public record ProductDetail(UUID id, String name, String category, String collection, boolean featured,
            boolean newArrival, boolean campaignEligible, int merchandisingRank, String heroImage,
            String primaryImage, boolean fitSupported, List<VariantView> variants) { }
    public record VariantView(UUID id, String sku, String size, String color, String availability, long amount) { }
    public record HeroProduct(UUID id, String name, String category, String collection, boolean featured,
            boolean newArrival, boolean campaignEligible, int merchandisingRank, String heroImage,
            String primaryImage, long recent30DayUnits, long recent30DayRevenue, long last7DayUnits,
            long previous7DayUnits, long growthUnits) { }
    public record HeroCarousel(HeroProduct topSeller, HeroProduct trending, HeroProduct newArrival,
            HeroProduct featuredCollection, List<HeroProduct> candidates) { }
    private record ProductMetadata(String name, String category, String collection, boolean featured,
            boolean newArrival, boolean campaignEligible, int merchandisingRank, String heroImage,
            String primaryImage, boolean fitSupported) { }

    private static HeroProduct pick(List<HeroProduct> candidates, Set<UUID> used,
            Comparator<HeroProduct> order) {
        return candidates.stream().sorted(order).filter(product -> used.add(product.id())).findFirst().orElse(null);
    }

    private static void markUsed(Set<UUID> used, HeroProduct product) {
        if (product != null) used.add(product.id());
    }

    private void normalizeExpiredCheckoutHolds(UUID productId) {
        String productFilter = productId == null ? "" : " AND products.public_id = ?";
        Object[] parameters = productId == null
                ? new Object[] { Timestamp.from(clock.instant()) }
                : new Object[] { Timestamp.from(clock.instant()), productId };
        List<UUID> variants = jdbc.query("""
                SELECT DISTINCT variants.public_id
                FROM inventory_reservation reservations
                JOIN commerce_order_item items ON items.reservation_public_id = reservations.public_id
                JOIN commerce_order orders ON orders.id = items.order_id
                JOIN catalog_product_variant variants ON variants.id = reservations.variant_id
                JOIN catalog_product products ON products.id = variants.product_id
                WHERE reservations.status = 'ADOPTED'
                  AND reservations.expires_at <= ?
                  AND orders.status = 'PENDING_PAYMENT'
                  AND (orders.price_quote_public_id IS NOT NULL OR orders.cart_quote_public_id IS NOT NULL)
                  AND variants.lifecycle_status = 'PUBLISHED'
                """ + productFilter, (rs, row) -> UUID.fromString(rs.getString(1)), parameters);
        variants.forEach(checkoutExpiry::expireForVariant);
    }
}
