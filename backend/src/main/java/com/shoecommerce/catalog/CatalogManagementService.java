package com.shoecommerce.catalog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class CatalogManagementService {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final CheckoutHoldExpiryService checkoutExpiry;

    public CatalogManagementService(JdbcTemplate jdbc, AuthorizationPolicy authorization,
            CheckoutHoldExpiryService checkoutExpiry) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.checkoutExpiry = checkoutExpiry;
    }

    @Transactional(readOnly = true)
    public List<ProductListItem> products(SessionPrincipal actor, String query) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 80) {
            throw new InvalidRequestException("CATALOG_SEARCH_INVALID", "Search query must not exceed 80 characters.",
                    Map.of("q", "TOO_LONG"));
        }
        checkoutExpiry.expireRelevant(null);
        String filter = search.isEmpty() ? "" : """
                WHERE CHARINDEX(?, LOWER(products.name)) > 0
                   OR CHARINDEX(?, LOWER(COALESCE(products.category, ''))) > 0
                   OR EXISTS (
                       SELECT 1 FROM catalog_product_variant search_variants
                       WHERE search_variants.product_id = products.id
                         AND (CHARINDEX(?, LOWER(search_variants.sku)) > 0
                              OR CHARINDEX(?, LOWER(search_variants.color)) > 0)
                   )
                """;
        Object[] parameters = search.isEmpty() ? new Object[0] : new Object[] { search, search, search, search };
        List<ProductFact> facts = jdbc.query("""
                WITH option_counts AS (
                    SELECT variants.id, variants.product_id, variants.lifecycle_status,
                           SUM(CASE WHEN variants.lifecycle_status <> 'RETIRED' THEN 1 ELSE 0 END) OVER (
                               PARTITION BY variants.product_id, variants.normalized_color, variants.normalized_size
                           ) active_option_count
                    FROM catalog_product_variant variants
                ), variant_facts AS (
                    SELECT options.product_id,
                           COUNT(*) variant_count,
                           SUM(CASE WHEN options.lifecycle_status='PUBLISHED' THEN 1 ELSE 0 END) published_variant_count,
                           SUM(CASE WHEN options.lifecycle_status='DRAFT' THEN 1 ELSE 0 END) draft_variant_count,
                           SUM(CASE WHEN options.lifecycle_status='RETIRED' THEN 1 ELSE 0 END) retired_variant_count,
                           MIN(CASE WHEN options.lifecycle_status <> 'RETIRED' THEN prices.amount END) min_price,
                           MAX(CASE WHEN options.lifecycle_status <> 'RETIRED' THEN prices.amount END) max_price,
                           SUM(CASE WHEN options.lifecycle_status <> 'RETIRED' AND prices.id IS NULL THEN 1 ELSE 0 END) missing_price_count,
                           MAX(CASE WHEN options.active_option_count > 1 THEN 1 ELSE 0 END) has_duplicate_options
                    FROM option_counts options
                    LEFT JOIN pricing_variant_price prices ON prices.variant_id=options.id AND prices.valid_to IS NULL
                    GROUP BY options.product_id
                )
                SELECT products.public_id,products.name,products.category,products.collection,
                       COALESCE(facts.variant_count,0) variant_count,
                       COALESCE(facts.published_variant_count,0) published_variant_count,
                       COALESCE(facts.draft_variant_count,0) draft_variant_count,
                       COALESCE(facts.retired_variant_count,0) retired_variant_count,
                       facts.min_price,facts.max_price,
                       COALESCE(facts.missing_price_count,0) missing_price_count,
                       COALESCE(facts.has_duplicate_options,0) has_duplicate_options
                FROM catalog_product products
                LEFT JOIN variant_facts facts ON facts.product_id=products.id
                """ + filter + " ORDER BY products.name,products.id",
                CatalogManagementService::productFact, parameters);
        Map<UUID, InventorySummary> inventory = authorization.hasPermission(actor, PermissionCode.INVENTORY_VIEW)
                ? productInventory(actor.accountId()) : null;
        return facts.stream().map(fact -> new ProductListItem(fact.id(), fact.name(), fact.category(),
                fact.collection(), fact.variantCount(), fact.publishedVariantCount(), fact.draftVariantCount(),
                fact.retiredVariantCount(), fact.minPrice(), fact.maxPrice(),
                fact.variantCount() > fact.retiredVariantCount() && fact.missingPriceCount() == 0,
                fact.hasDuplicateOptions(), publication(fact.draftVariantCount(), fact.publishedVariantCount()),
                inventory == null ? null : inventory.getOrDefault(fact.id(), InventorySummary.none()))).toList();
    }

    @Transactional(readOnly = true)
    public ProductDetail product(SessionPrincipal actor, UUID productId) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_MANAGE);
        checkoutExpiry.expireRelevant(productId);
        List<DetailRow> rows = jdbc.query("""
                WITH global_stock AS (
                    SELECT balances.variant_id,SUM(balances.on_hand) on_hand,
                           SUM(balances.on_hand-balances.reserved) available
                    FROM inventory_balance balances
                    JOIN org_location locations ON locations.id=balances.location_id AND locations.enabled=1
                    JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                    GROUP BY balances.variant_id
                ), options AS (
                    SELECT variants.*,
                           SUM(CASE WHEN variants.lifecycle_status <> 'RETIRED' THEN 1 ELSE 0 END) OVER (
                               PARTITION BY variants.product_id,variants.normalized_color,variants.normalized_size
                           ) active_option_count
                    FROM catalog_product_variant variants
                )
                SELECT products.public_id product_public_id,products.name,products.category,products.collection,
                       products.hero_image,products.primary_image,
                       products.entity_version,products.intended_use,products.primary_surface,
                       products.upper_construction,products.cut_profile,products.primary_sole_profile,
                       options.public_id variant_public_id,options.sku,options.barcode,options.ever_published,
                       options.size,options.color,
                       options.lifecycle_status,options.entity_version variant_entity_version,
                       options.active_option_count,prices.amount,
                       COALESCE(global_stock.on_hand,0) global_on_hand,
                       COALESCE(global_stock.available,0) global_available
                FROM catalog_product products
                LEFT JOIN options ON options.product_id=products.id
                LEFT JOIN pricing_variant_price prices ON prices.variant_id=options.id AND prices.valid_to IS NULL
                LEFT JOIN global_stock ON global_stock.variant_id=options.id
                WHERE products.public_id=?
                ORDER BY options.color,options.size,options.sku
                """, CatalogManagementService::detailRow, productId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found.");
        boolean canViewInventory = authorization.hasPermission(actor, PermissionCode.INVENTORY_VIEW);
        Map<UUID, InventorySummary> inventory = canViewInventory ? variantInventory(actor.accountId(), productId) : Map.of();
        DetailRow first = rows.getFirst();
        List<VariantDetail> variants = rows.stream().filter(row -> row.variantId() != null).map(row -> {
            List<String> readiness = new ArrayList<>();
            if ("DRAFT".equals(row.status())) {
                if (row.price() == null) readiness.add("CATALOG_PRICE_REQUIRED");
                if (row.globalOnHand() <= 0) readiness.add("CATALOG_ON_HAND_STOCK_REQUIRED");
            }
            return new VariantDetail(row.variantId(), row.sku(), row.barcode(),
                    "DRAFT".equals(row.status()) && !row.everPublished(), row.size(), row.color(), row.status(),
                    row.variantEntityVersion(), row.price(), row.price() != null, row.activeOptionCount() > 1,
                    canViewInventory ? inventory.getOrDefault(row.variantId(), InventorySummary.none()) : null,
                    "DRAFT".equals(row.status()) && readiness.isEmpty(), List.copyOf(readiness));
        }).toList();
        long published = variants.stream().filter(variant -> "PUBLISHED".equals(variant.status())).count();
        ProductEvidence evidence = evidence(first);
        return new ProductDetail(first.productId(), first.name(), first.category(), first.collection(),
                first.heroImage(), first.primaryImage(),
                evidence.isEmpty() ? null : evidence, first.entityVersion(), variants.size(), (int) published,
                variants.stream().anyMatch(VariantDetail::duplicateOptionCombination), variants);
    }

    private Map<UUID, InventorySummary> productInventory(long accountId) {
        return inventory("products.public_id", "", accountId, null);
    }

    private Map<UUID, InventorySummary> variantInventory(long accountId, UUID productId) {
        return inventory("variants.public_id", " AND products.public_id=?", accountId, productId);
    }

    private Map<UUID, InventorySummary> inventory(String identity, String filter, long accountId, UUID productId) {
        Object[] parameters = productId == null ? new Object[] { accountId } : new Object[] { accountId, productId };
        return jdbc.query("""
                SELECT %s public_id,COUNT(*) balance_count,SUM(balances.on_hand) on_hand,
                       SUM(balances.reserved) reserved,SUM(balances.on_hand-balances.reserved) available,
                       MAX(balances.updated_at) updated_at
                FROM inventory_balance balances
                JOIN catalog_product_variant variants ON variants.id=balances.variant_id
                JOIN catalog_product products ON products.id=variants.product_id
                JOIN org_location locations ON locations.id=balances.location_id AND locations.enabled=1
                JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                JOIN iam_staff_assignment assignments ON assignments.location_id=locations.id
                     AND assignments.branch_id=locations.branch_id AND assignments.account_id=? AND assignments.active=1
                WHERE 1=1%s
                GROUP BY %s
                """.formatted(identity, filter, identity), rows -> {
            Map<UUID, InventorySummary> result = new LinkedHashMap<>();
            while (rows.next()) {
                long available = rows.getLong("available");
                result.put(rows.getObject("public_id", UUID.class), new InventorySummary(rows.getLong("on_hand"),
                        rows.getLong("reserved"), available, available > 0 ? "AVAILABLE" : "SOLD_OUT",
                        instant(rows, "updated_at")));
            }
            return result;
        }, parameters);
    }

    private static String publication(int drafts, int published) {
        if (published == 0) return "UNPUBLISHED";
        return drafts == 0 ? "PUBLISHED" : "PARTIALLY_PUBLISHED";
    }

    private static ProductFact productFact(ResultSet row, int index) throws SQLException {
        return new ProductFact(row.getObject("public_id", UUID.class), row.getString("name"),
                row.getString("category"), row.getString("collection"), row.getInt("variant_count"),
                row.getInt("published_variant_count"), row.getInt("draft_variant_count"),
                row.getInt("retired_variant_count"), amount(row, "min_price"), amount(row, "max_price"),
                row.getInt("missing_price_count"), row.getBoolean("has_duplicate_options"));
    }

    private static DetailRow detailRow(ResultSet row, int index) throws SQLException {
        return new DetailRow(row.getObject("product_public_id", UUID.class), row.getString("name"),
                row.getString("category"), row.getString("collection"), row.getString("hero_image"),
                row.getString("primary_image"), row.getLong("entity_version"),
                row.getString("intended_use"), row.getString("primary_surface"),
                row.getString("upper_construction"), row.getString("cut_profile"),
                row.getString("primary_sole_profile"), row.getObject("variant_public_id", UUID.class),
                row.getString("sku"), row.getString("barcode"), row.getBoolean("ever_published"),
                row.getString("size"), row.getString("color"),
                row.getString("lifecycle_status"), row.getLong("variant_entity_version"),
                row.getInt("active_option_count"), amount(row, "amount"), row.getLong("global_on_hand"),
                row.getLong("global_available"));
    }

    private static Long amount(ResultSet row, String column) throws SQLException {
        BigDecimal value = row.getBigDecimal(column);
        return value == null ? null : value.longValueExact();
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static ProductEvidence evidence(DetailRow row) {
        return new ProductEvidence(enumValue(ProductEvidence.IntendedUse.class, row.intendedUse()),
                enumValue(ProductEvidence.PrimarySurface.class, row.primarySurface()),
                enumValue(ProductEvidence.UpperConstruction.class, row.upperConstruction()),
                enumValue(ProductEvidence.CutProfile.class, row.cutProfile()),
                enumValue(ProductEvidence.PrimarySoleProfile.class, row.primarySoleProfile()));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    public record InventorySummary(long onHand, long reserved, long available, String state, Instant updatedAt) {
        static InventorySummary none() { return new InventorySummary(0, 0, 0, "NO_INVENTORY_EVIDENCE", null); }
    }
    public record ProductListItem(UUID id, String name, String category, String collection, int variantCount,
            int publishedVariantCount, int draftVariantCount, int retiredVariantCount,
            Long minCurrentPrice, Long maxCurrentPrice, boolean pricingComplete,
            boolean hasDuplicateOptions, String publication, InventorySummary inventory) { }
    public record VariantDetail(UUID id, String sku, String barcode, boolean barcodeEditable,
            String size, String color, String status, long entityVersion,
            Long currentPrice, boolean pricingComplete, boolean duplicateOptionCombination, InventorySummary inventory,
            boolean publishReady, List<String> publishReadinessCodes) { }
    public record ProductDetail(UUID id, String name, String category, String collection, String heroImage,
            String primaryImage, ProductEvidence evidence,
            long entityVersion, int variantCount, int publishedVariantCount, boolean hasDuplicateOptions,
            List<VariantDetail> variants) { }
    private record ProductFact(UUID id, String name, String category, String collection, int variantCount,
            int publishedVariantCount, int draftVariantCount, int retiredVariantCount,
            Long minPrice, Long maxPrice, int missingPriceCount,
            boolean hasDuplicateOptions) { }
    private record DetailRow(UUID productId, String name, String category, String collection, String heroImage,
            String primaryImage, long entityVersion,
            String intendedUse, String primarySurface, String upperConstruction, String cutProfile,
            String primarySoleProfile, UUID variantId, String sku, String barcode, boolean everPublished,
            String size, String color, String status,
            long variantEntityVersion, int activeOptionCount, Long price, long globalOnHand,
            long globalAvailable) { }
}
