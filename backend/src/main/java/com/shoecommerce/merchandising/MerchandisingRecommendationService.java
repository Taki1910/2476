package com.shoecommerce.merchandising;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.shoecommerce.platform.api.InvalidRequestException;

@Service
public class MerchandisingRecommendationService {
    private static final long THIRTY_DAYS_SECONDS = 30L * 86_400;

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public MerchandisingRecommendationService(JdbcTemplate jdbc, AuthorizationPolicy authorization, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CandidatePage candidates(SessionPrincipal actor, String query, CandidateType type, int page, int size) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        Instant end = clock.instant();
        return page(allCandidates(end), query, type, page, size);
    }

    @Transactional(readOnly = true)
    public List<Candidate> evidence(SessionPrincipal actor, List<UUID> productIds) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        return selectEvidence(allCandidates(clock.instant()), productIds);
    }

    private List<Candidate> allCandidates(Instant end) {
        Instant start = end.minusSeconds(THIRTY_DAYS_SECONDS);
        Map<UUID, SalesRow> sales = sales(start, end);
        Map<UUID, List<PromotionEvidence>> promotions = promotions(end);
        Map<UUID, List<String>> colors = colors();
        PlacementMaps placements = placements();
        List<Candidate> candidates = new ArrayList<>();
        for (BaseRow product : products(end)) {
            SalesRow productSales = sales.getOrDefault(product.productId(), SalesRow.empty());
            Sellability sellability = sellability(product);
            List<ReadinessWarning> warnings = warnings(product, productSales, sellability);
            List<PromotionEvidence> productPromotions = promotions.getOrDefault(product.productId(), List.of());
            List<String> reasons = new ArrayList<>();
            if (productPromotions.stream().anyMatch(promotion -> promotion.state() == PromotionState.ACTIVE)) {
                reasons.add("ACTIVE_ITEM_PROMOTION");
            }
            if (!warnings.isEmpty()) reasons.add("READINESS_WARNING");
            if (product.newArrival()) reasons.add("MARKED_NEW_ARRIVAL");
            candidates.add(new Candidate(product.productId(), product.name(), product.category(), product.collection(),
                    image(product.primaryImage(), product.heroImage()),
                    colors.getOrDefault(product.productId(), List.of()), product.newArrival(), sellability,
                    product.available(), new SalesEvidence(productSales.units(), productSales.grossValue(),
                            start, end, !productSales.incomplete()),
                    productPromotions, placements.published().getOrDefault(product.productId(), List.of()),
                    placements.draft().getOrDefault(product.productId(), List.of()), warnings,
                    List.copyOf(reasons)));
        }
        return List.copyOf(candidates);
    }

    static List<Candidate> selectEvidence(List<Candidate> source, List<UUID> productIds) {
        List<UUID> selected = productIds == null ? List.of() : List.copyOf(productIds);
        if (selected.size() > 8 || selected.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.HashSet<>(selected).size() != selected.size()) {
            throw new InvalidRequestException("MERCHANDISING_AI_PRODUCTS_INVALID",
                    "Choose up to 8 distinct Products for copy assistance.");
        }
        Map<UUID, Candidate> candidates = source.stream().collect(java.util.stream.Collectors.toMap(
                Candidate::productId, candidate -> candidate));
        List<Candidate> result = new ArrayList<>();
        for (UUID productId : selected) {
            Candidate candidate = candidates.get(productId);
            if (candidate == null) throw new InvalidRequestException("MERCHANDISING_AI_PRODUCT_NOT_FOUND",
                    "A selected Product is not available for merchandising assistance.");
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    static CandidatePage page(List<Candidate> source, String rawQuery, CandidateType type, int page, int size) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (query.length() > 80) {
            throw new InvalidRequestException("MERCHANDISING_QUERY_INVALID",
                    "Merchandising search must be at most 80 characters.");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidRequestException("INVALID_PAGINATION",
                    "Page must be non-negative and size must be between 1 and 50.");
        }
        CandidateType selected = type == null ? CandidateType.ALL : type;
        List<Candidate> filtered = source.stream()
                .filter(candidate -> query.isEmpty() || searchable(candidate).contains(query))
                .filter(candidate -> switch (selected) {
                    case ALL -> true;
                    case BEST_SELLER -> candidate.sellability() == Sellability.SELLABLE
                            && candidate.aggregateAvailable() > 0 && candidate.sales().complete()
                            && candidate.sales().units30d() > 0;
                    case PROMOTED -> candidate.promotions().stream()
                            .anyMatch(promotion -> promotion.state() == PromotionState.ACTIVE);
                    case NEEDS_REVIEW -> !candidate.warnings().isEmpty();
                }).toList();
        Comparator<Candidate> order = selected == CandidateType.BEST_SELLER
                ? Comparator.comparingLong((Candidate candidate) -> candidate.sales().units30d()).reversed()
                        .thenComparing(Comparator.comparingLong(
                                (Candidate candidate) -> candidate.sales().grossSalesValue30d()).reversed())
                        .thenComparing(Candidate::productId)
                : Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Candidate::productId);
        List<Candidate> ordered = filtered.stream().sorted(order)
                .map(candidate -> selected == CandidateType.BEST_SELLER
                        ? candidate.withReason("BEST_SELLER_30D") : candidate)
                .toList();
        long offset = (long) page * size;
        int from = offset >= ordered.size() ? ordered.size() : (int) offset;
        int to = Math.min(from + size, ordered.size());
        int totalPages = ordered.isEmpty() ? 0 : (ordered.size() + size - 1) / size;
        return new CandidatePage(List.copyOf(ordered.subList(from, to)), page, size, ordered.size(), totalPages);
    }

    private static String searchable(Candidate candidate) {
        return String.join(" ", candidate.name(), value(candidate.category()), value(candidate.collection()))
                .toLowerCase(Locale.ROOT);
    }

    private static String value(String value) { return value == null ? "" : value; }

    private List<BaseRow> products(Instant at) {
        Timestamp now = Timestamp.from(at);
        return jdbc.query("""
                SELECT products.public_id,products.name,products.category,products.collection,
                       products.primary_image,products.hero_image,products.new_arrival,
                       (SELECT COUNT(*) FROM catalog_product_variant variants
                        WHERE variants.product_id=products.id AND variants.lifecycle_status='PUBLISHED') AS published_variants,
                       (SELECT COUNT(DISTINCT variants.id) FROM catalog_product_variant variants
                        JOIN pricing_variant_price prices ON prices.variant_id=variants.id
                        WHERE variants.product_id=products.id AND variants.lifecycle_status='PUBLISHED'
                          AND prices.valid_from<=? AND (prices.valid_to IS NULL OR prices.valid_to>?)) AS priced_variants,
                       COALESCE((SELECT SUM(CASE WHEN balances.on_hand>balances.reserved
                                                THEN balances.on_hand-balances.reserved ELSE 0 END)
                        FROM catalog_product_variant variants
                        JOIN inventory_balance balances ON balances.variant_id=variants.id
                        JOIN org_location locations ON locations.id=balances.location_id AND locations.enabled=1
                        JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                        WHERE variants.product_id=products.id AND variants.lifecycle_status='PUBLISHED'
                          AND EXISTS (SELECT 1 FROM pricing_variant_price available_prices
                              WHERE available_prices.variant_id=variants.id
                                AND available_prices.valid_from<=?
                                AND (available_prices.valid_to IS NULL OR available_prices.valid_to>?))),0) AS available,
                       CASE WHEN EXISTS (SELECT 1 FROM product_presentation_revision presentations
                            WHERE presentations.product_id=products.id AND presentations.status='PUBLISHED')
                            THEN 1 ELSE 0 END AS has_presentation
                FROM catalog_product products
                ORDER BY products.name,products.public_id
                """, (rs, row) -> new BaseRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getLong(8),
                        rs.getLong(9), rs.getLong(10), rs.getBoolean(11)), now, now, now, now);
    }

    private Map<UUID, SalesRow> sales(Instant start, Instant end) {
        Map<UUID, SalesRow> result = new HashMap<>();
        jdbc.query("""
                WITH evidence AS (
                    SELECT items.public_id AS item_id,variants.product_id,items.quantity,
                           items.unit_price_amount*items.quantity AS gross_amount,
                           items.unit_price_amount*items.quantity-
                               COALESCE((SELECT SUM(adjustments.applied_amount)
                                   FROM commerce_order_item_adjustment adjustments
                                   WHERE adjustments.order_item_public_id=items.public_id),0) AS paid_amount,
                           COALESCE((SELECT SUM(allocations.amount) FROM payment_void_allocation allocations
                                   WHERE allocations.component_type='ORDER_ITEM'
                                     AND allocations.component_public_id=items.public_id
                                     AND allocations.status='SUCCEEDED'),0) AS voided_amount,
                           CASE WHEN orders.channel='ONLINE' THEN (
                               SELECT MIN(attempts.resolved_at) FROM payment_attempt attempts
                               JOIN payment payments ON payments.id=attempts.payment_id
                               WHERE payments.order_id=orders.id AND attempts.status='SUCCEEDED')
                           ELSE (SELECT MIN(tenders.created_at) FROM cash_tender tenders
                                 WHERE tenders.order_id=orders.id) END AS sold_at
                    FROM commerce_order_item items
                    JOIN commerce_order orders ON orders.id=items.order_id AND orders.status='PAID'
                    JOIN catalog_product_variant variants ON variants.public_id=items.variant_public_id
                )
                SELECT products.public_id,
                       SUM(CASE WHEN evidence.voided_amount=0 THEN evidence.quantity ELSE 0 END) AS units,
                       SUM(CASE WHEN evidence.voided_amount=0 THEN evidence.gross_amount ELSE 0 END) AS gross_value,
                       MAX(CASE WHEN evidence.voided_amount>0 AND evidence.voided_amount<evidence.paid_amount
                           THEN 1 ELSE 0 END) AS incomplete
                FROM evidence JOIN catalog_product products ON products.id=evidence.product_id
                WHERE evidence.sold_at>=? AND evidence.sold_at<?
                GROUP BY products.public_id
                """, rs -> {
                    result.put(rs.getObject(1, UUID.class),
                            new SalesRow(rs.getLong(2), rs.getLong(3), rs.getBoolean(4)));
                }, Timestamp.from(start), Timestamp.from(end));
        return result;
    }

    private Map<UUID, List<PromotionEvidence>> promotions(Instant at) {
        Map<UUID, List<PromotionEvidence>> result = new LinkedHashMap<>();
        Timestamp now = Timestamp.from(at);
        jdbc.query("""
                SELECT scopes.product_public_id,promotions.family_public_id,promotions.public_id,promotions.name,
                       promotions.effect_type,promotions.percentage_value,promotions.fixed_amount,
                       promotions.valid_from,promotions.valid_to,
                       CASE WHEN promotions.valid_from<=? THEN 'ACTIVE' ELSE 'SCHEDULED' END AS timing
                FROM promotion promotions
                JOIN promotion_product_scope scopes ON scopes.promotion_id=promotions.id
                WHERE promotions.status='PUBLISHED' AND promotions.layer='ITEM'
                  AND promotions.effect_type IN ('ITEM_PERCENT','ITEM_FIXED')
                  AND (promotions.valid_to IS NULL OR promotions.valid_to>?)
                ORDER BY scopes.product_public_id,
                         CASE WHEN promotions.valid_from<=? THEN 0 ELSE 1 END,
                         promotions.priority DESC,promotions.valid_from,promotions.public_id
                """, rs -> {
                    BigDecimal percentage = rs.getBigDecimal(6);
                    BigDecimal fixed = rs.getBigDecimal(7);
                    Timestamp validTo = rs.getTimestamp(9);
                    PromotionEvidence evidence = new PromotionEvidence(rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                            percentage == null ? null : percentage.doubleValue(),
                            fixed == null ? null : fixed.longValueExact(), rs.getTimestamp(8).toInstant(),
                            validTo == null ? null : validTo.toInstant(), PromotionState.valueOf(rs.getString(10)));
                    result.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>()).add(evidence);
                }, now, now, now);
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return result;
    }

    private Map<UUID, List<String>> colors() {
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT products.public_id,variants.color FROM catalog_product products
                JOIN catalog_product_variant variants ON variants.product_id=products.id
                WHERE variants.lifecycle_status='PUBLISHED'
                GROUP BY products.public_id,variants.color ORDER BY products.public_id,variants.color
                """, rs -> {
                    result.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(rs.getString(2));
                });
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return result;
    }

    private PlacementMaps placements() {
        Map<UUID, List<Placement>> published = new LinkedHashMap<>();
        Map<UUID, List<Placement>> draft = new LinkedHashMap<>();
        jdbc.query("""
                SELECT members.product_public_id,revisions.status,sections.public_id,sections.section_type,
                       sections.position,sections.title_vi,sections.title_en
                FROM storefront_page_revision revisions
                JOIN storefront_section sections ON sections.page_revision_id=revisions.id
                JOIN storefront_collection_product members ON members.section_id=sections.id
                WHERE revisions.page_key='HOME' AND revisions.status IN ('PUBLISHED','DRAFT')
                ORDER BY members.product_public_id,revisions.status,sections.position,sections.public_id
                """, rs -> {
                    Map<UUID, List<Placement>> target = "PUBLISHED".equals(rs.getString(2)) ? published : draft;
                    target.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new Placement(rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5),
                                    rs.getString(6), rs.getString(7)));
                });
        published.replaceAll((ignored, values) -> List.copyOf(values));
        draft.replaceAll((ignored, values) -> List.copyOf(values));
        return new PlacementMaps(published, draft);
    }

    private static Sellability sellability(BaseRow product) {
        if (product.publishedVariants() == 0) return Sellability.NO_PUBLISHED_VARIANT;
        if (product.pricedVariants() == 0) return Sellability.NO_EFFECTIVE_PRICE;
        return product.available() > 0 ? Sellability.SELLABLE : Sellability.SOLD_OUT;
    }

    private static List<ReadinessWarning> warnings(BaseRow product, SalesRow sales, Sellability sellability) {
        List<ReadinessWarning> warnings = new ArrayList<>();
        if (sellability == Sellability.NO_PUBLISHED_VARIANT) {
            warnings.add(new ReadinessWarning("NO_PUBLISHED_VARIANT", ReadinessClass.NOT_SELLABLE));
        } else if (sellability == Sellability.NO_EFFECTIVE_PRICE) {
            warnings.add(new ReadinessWarning("NO_EFFECTIVE_PRICE", ReadinessClass.NOT_SELLABLE));
        } else if (sellability == Sellability.SOLD_OUT) {
            warnings.add(new ReadinessWarning("SOLD_OUT", ReadinessClass.NOT_SELLABLE));
        }
        if (image(product.primaryImage(), product.heroImage()) == null) {
            warnings.add(new ReadinessWarning("MISSING_PRODUCT_MEDIA", ReadinessClass.PRESENTATION_WEAK));
        }
        if (!product.hasPresentation()) {
            warnings.add(new ReadinessWarning("MISSING_PRODUCT_PRESENTATION", ReadinessClass.PRESENTATION_WEAK));
        }
        if (sales.incomplete()) {
            warnings.add(new ReadinessWarning("PARTIAL_VOID_SALES_UNRESOLVED", ReadinessClass.EVIDENCE_INCOMPLETE));
        }
        return List.copyOf(warnings);
    }

    private static String image(String primary, String hero) {
        String selected = primary == null || primary.isBlank() ? hero : primary;
        return selected == null || selected.isBlank() ? null : selected.trim();
    }

    public enum CandidateType { ALL, BEST_SELLER, PROMOTED, NEEDS_REVIEW }
    public enum Sellability { SELLABLE, NO_PUBLISHED_VARIANT, NO_EFFECTIVE_PRICE, SOLD_OUT }
    public enum PromotionState { ACTIVE, SCHEDULED }
    public enum ReadinessClass { NOT_SELLABLE, PRESENTATION_WEAK, EVIDENCE_INCOMPLETE }

    public record SalesEvidence(long units30d, long grossSalesValue30d, Instant periodStart,
            Instant periodEnd, boolean complete) { }
    public record PromotionEvidence(UUID familyId, UUID revisionId, String name, String effectType,
            Double percentageValue, Long fixedAmount, Instant validFrom, Instant validTo,
            PromotionState state) { }
    public record Placement(UUID sectionId, String sectionType, int position, String titleVi,
            String titleEn) { }
    public record ReadinessWarning(String code, ReadinessClass classification) { }
    public record Candidate(UUID productId, String name, String category, String collection,
            String imageUrl, List<String> colors, boolean newArrival, Sellability sellability,
            long aggregateAvailable, SalesEvidence sales, List<PromotionEvidence> promotions,
            List<Placement> publishedPlacements, List<Placement> draftPlacements,
            List<ReadinessWarning> warnings, List<String> reasonCodes) {
        Candidate withReason(String reason) {
            if (reasonCodes.contains(reason)) return this;
            List<String> reasons = new ArrayList<>(reasonCodes);
            reasons.add(reason);
            return new Candidate(productId, name, category, collection, imageUrl, colors, newArrival,
                    sellability, aggregateAvailable, sales, promotions, publishedPlacements,
                    draftPlacements, warnings, List.copyOf(reasons));
        }
    }
    public record CandidatePage(List<Candidate> candidates, int page, int size, long totalItems,
            int totalPages) { }
    private record BaseRow(UUID productId, String name, String category, String collection,
            String primaryImage, String heroImage, boolean newArrival, long publishedVariants,
            long pricedVariants, long available, boolean hasPresentation) { }
    private record SalesRow(long units, long grossValue, boolean incomplete) {
        static SalesRow empty() { return new SalesRow(0, 0, false); }
    }
    private record PlacementMaps(Map<UUID, List<Placement>> published,
            Map<UUID, List<Placement>> draft) { }
}
