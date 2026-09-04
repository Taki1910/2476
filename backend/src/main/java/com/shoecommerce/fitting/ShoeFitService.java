package com.shoecommerce.fitting;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public final class ShoeFitService {
    // ponytail: process-local cap; use a shared limiter only when multi-instance capacity needs it.
    private static final Semaphore PROCESSING_SLOTS = new Semaphore(2, true);
    private final JdbcTemplate jdbc;
    private final FitImageAnalyzer analyzer;
    private final FitRecommendationEngine engine;

    public ShoeFitService(JdbcTemplate jdbc, FitImageAnalyzer analyzer, FitRecommendationEngine engine) {
        this.jdbc = jdbc;
        this.analyzer = analyzer;
        this.engine = engine;
    }

    public FitResult analyze(UUID productId, String selectedColor, byte[] imageBytes) {
        if (!exists(productId)) throw new ResourceNotFoundException("STOREFRONT_PRODUCT_NOT_FOUND", "Product not found.");
        FitRecommendationEngine.Profile profile = profile(productId);
        if (profile == null || profile.ranges().isEmpty()) return FitResult.unsupported();
        if (!PROCESSING_SLOTS.tryAcquire()) throw new FitCapacityException();
        try {
            FitImageAnalyzer.Analysis image = analyzer.analyze(imageBytes);
            if (!image.successful()) return FitResult.retake(image.retakeReason().name());
            FitRecommendationEngine.Recommendation recommendation;
            try {
                recommendation = engine.recommend(image.footLengthMm(), image.footWidthMm(), profile, image.analysisScore());
            } catch (FitRecommendationEngine.FitAnalysisInsufficientException exception) {
                return FitResult.retake("ANALYSIS_INSUFFICIENT");
            } catch (FitRecommendationEngine.FitProfileCoverageException exception) {
                return FitResult.retake("FIT_PROFILE_OUT_OF_RANGE");
            }
            Availability availability = availability(productId, recommendation.recommendedSize(), selectedColor);
            return FitResult.success(image, recommendation, availability);
        } finally {
            PROCESSING_SLOTS.release();
        }
    }

    private FitRecommendationEngine.Profile profile(UUID productId) {
        List<FitRecommendationEngine.Profile> profiles = jdbc.query("""
                SELECT fit_profiles.size_system, fit_profiles.fit_tendency, fit_profiles.width_profile
                FROM catalog_shoe_fit_profile fit_profiles
                JOIN catalog_product products ON products.id = fit_profiles.product_id
                WHERE products.public_id = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM catalog_product_variant variants
                      WHERE variants.product_id = products.id
                        AND variants.lifecycle_status = 'PUBLISHED'
                        AND NOT EXISTS (
                            SELECT 1 FROM catalog_shoe_fit_size_range size_ranges
                            WHERE size_ranges.profile_id = fit_profiles.id AND size_ranges.size_label = variants.size
                        )
                  )
                """, (rs, row) -> new FitRecommendationEngine.Profile(
                rs.getString("size_system"), rs.getString("fit_tendency"), rs.getString("width_profile"), List.of()), productId);
        if (profiles.isEmpty()) return null;
        FitRecommendationEngine.Profile profile = profiles.getFirst();
        List<FitRecommendationEngine.SizeRange> ranges = jdbc.query("""
                SELECT size_ranges.size_label, size_ranges.min_foot_length_mm, size_ranges.max_foot_length_mm,
                       size_ranges.min_foot_width_mm, size_ranges.max_foot_width_mm
                FROM catalog_shoe_fit_size_range size_ranges
                JOIN catalog_shoe_fit_profile fit_profiles ON fit_profiles.id = size_ranges.profile_id
                JOIN catalog_product products ON products.id = fit_profiles.product_id
                WHERE products.public_id = ?
                ORDER BY TRY_CONVERT(DECIMAL(10,2), size_ranges.size_label), size_ranges.size_label
                """, (rs, row) -> new FitRecommendationEngine.SizeRange(rs.getString("size_label"),
                rs.getDouble("min_foot_length_mm"), rs.getDouble("max_foot_length_mm"),
                rs.getDouble("min_foot_width_mm"), rs.getDouble("max_foot_width_mm")), productId);
        return new FitRecommendationEngine.Profile(profile.sizeSystem(), profile.fitTendency(), profile.widthProfile(), ranges);
    }

    private Availability availability(UUID productId, String size, String selectedColor) {
        List<ColorAvailability> variants = jdbc.query("""
                SELECT variants.color,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM inventory_balance balances
                           JOIN org_location locations ON locations.id = balances.location_id
                           JOIN org_branch branches ON branches.id = locations.branch_id
                           WHERE balances.variant_id = variants.id AND locations.enabled = 1 AND branches.enabled = 1
                             AND balances.on_hand > balances.reserved
                       ) THEN 1 ELSE 0 END AS available
                FROM catalog_product_variant variants
                JOIN catalog_product products ON products.id = variants.product_id
                WHERE products.public_id = ? AND variants.size = ? AND variants.lifecycle_status = 'PUBLISHED'
                ORDER BY variants.color
                """, (rs, row) -> new ColorAvailability(rs.getString("color"), rs.getBoolean("available")), productId, size);
        boolean recommendedAvailable = variants.stream().anyMatch(ColorAvailability::available);
        Boolean selectedColorAvailable = selectedColor == null ? null : variants.stream()
                .anyMatch(item -> item.color().equals(selectedColor) && item.available());
        List<String> availableColors = variants.stream().filter(ColorAvailability::available)
                .map(ColorAvailability::color).distinct().toList();
        return new Availability(recommendedAvailable, selectedColorAvailable, availableColors);
    }

    private boolean exists(UUID productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM catalog_product WHERE public_id = ?", Integer.class, productId) > 0;
    }

    private record ColorAvailability(String color, boolean available) { }
    private record Availability(boolean recommendedAvailable, Boolean selectedColorAvailable, List<String> availableColors) { }

    public record FitResult(String status, String retakeReason, Double footLengthMm, Double footWidthMm,
            String recommendedSize, String alternativeSize, String analysisConfidence, Integer analysisScore,
            String explanation, String warning, String sizeSystem, String fitTendency, String widthProfile,
            Boolean recommendedAvailable, Boolean selectedColorAvailable, List<String> availableColors) {
        static FitResult unsupported() {
            return new FitResult("UNSUPPORTED_PRODUCT", null, null, null, null, null, null, null,
                    "This shoe model does not have a supported fit profile yet.", null, null, null, null, null, null, List.of());
        }
        static FitResult retake(String reason) {
            return new FitResult("RETAKE", reason, null, null, null, null, null, null,
                    "Use a fully visible A4 sheet and place one whole foot inside it, then try again.", null,
                    null, null, null, null, null, List.of());
        }
        static FitResult success(FitImageAnalyzer.Analysis image, FitRecommendationEngine.Recommendation recommendation,
                Availability availability) {
            return new FitResult("SUCCESS", null, image.footLengthMm(), image.footWidthMm(), recommendation.recommendedSize(),
                    recommendation.alternativeSize(), recommendation.confidence(), recommendation.confidenceScore(),
                    recommendation.explanation(), recommendation.warning(), recommendation.sizeSystem(),
                    recommendation.fitTendency(), recommendation.widthProfile(), availability.recommendedAvailable(),
                    availability.selectedColorAvailable(), availability.availableColors());
        }
    }

    public static final class FitCapacityException extends RuntimeException {
        FitCapacityException() { super("Fit analysis is busy. Please try again shortly."); }
    }
}
