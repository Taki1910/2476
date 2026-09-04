package com.shoecommerce.fitting;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public final class FitRecommendationEngine {
    public Recommendation recommend(double footLengthMm, double footWidthMm, Profile profile, int analysisScore) {
        if (profile == null || profile.ranges().isEmpty()) throw new UnsupportedFitProfileException();
        List<SizeRange> ranges = profile.ranges().stream().sorted(Comparator.comparing(FitRecommendationEngine::sizeNumber)
                .thenComparing(SizeRange::sizeLabel)).toList();
        // Product-authored ranges are the authoritative fit mapping. Tendency and width
        // profile describe that mapping; applying another offset would double-count them.
        int index = findLengthIndex(ranges, footLengthMm);
        if (index < 0) throw new FitProfileCoverageException();
        SizeRange selected = ranges.get(index);
        boolean widthUp = footWidthMm > selected.maxWidthMm()
                && index + 1 < ranges.size()
                && contains(ranges.get(index + 1), footLengthMm, footWidthMm);
        if (widthUp) selected = ranges.get(++index);

        double boundary = boundaryScore(selected, footLengthMm, footWidthMm);
        int score = (int) Math.round(Math.min(100, analysisScore * .78 + boundary * .22));
        if (score < 58) throw new FitAnalysisInsufficientException();
        String confidence = score >= 80 ? "HIGH" : "MEDIUM";
        String warning = widthUp ? "WIDTH_SIZE_UP"
                : containsWidth(selected, footWidthMm) ? null : "WIDTH_MAY_NOT_MATCH";
        String explanation = switch (profile.fitTendency()) {
            case "RUNS_SMALL" -> "FIT_TENDENCY_SMALL";
            case "RUNS_LARGE" -> "FIT_TENDENCY_LARGE";
            default -> "FIT_TENDENCY_TRUE";
        };
        String alternative = index + 1 < ranges.size() ? ranges.get(index + 1).sizeLabel()
                : index > 0 ? ranges.get(index - 1).sizeLabel() : null;
        return new Recommendation(selected.sizeLabel(), alternative, confidence, score, explanation, warning,
                profile.sizeSystem(), profile.fitTendency(), profile.widthProfile());
    }

    private static int findLengthIndex(List<SizeRange> ranges, double length) {
        for (int index = 0; index < ranges.size(); index++) {
            SizeRange range = ranges.get(index);
            if (length >= range.minLengthMm() && length <= range.maxLengthMm()) return index;
        }
        return -1;
    }

    private static boolean contains(SizeRange range, double length, double width) {
        return length >= range.minLengthMm() && length <= range.maxLengthMm() && containsWidth(range, width);
    }

    private static boolean containsWidth(SizeRange range, double width) {
        return width >= range.minWidthMm() && width <= range.maxWidthMm();
    }

    private static double boundaryScore(SizeRange range, double length, double width) {
        double lengthSpan = range.maxLengthMm() - range.minLengthMm();
        double widthSpan = range.maxWidthMm() - range.minWidthMm();
        double lengthDistance = Math.min(length - range.minLengthMm(), range.maxLengthMm() - length);
        double widthDistance = Math.min(width - range.minWidthMm(), range.maxWidthMm() - width);
        return Math.max(0, Math.min(100, Math.min(lengthDistance / lengthSpan, widthDistance / widthSpan) * 260));
    }

    private static double sizeNumber(SizeRange range) {
        try { return Double.parseDouble(range.sizeLabel()); } catch (NumberFormatException ignored) { return Double.MAX_VALUE; }
    }

    public record Profile(String sizeSystem, String fitTendency, String widthProfile, List<SizeRange> ranges) { }
    public record SizeRange(String sizeLabel, double minLengthMm, double maxLengthMm,
            double minWidthMm, double maxWidthMm) { }
    public record Recommendation(String recommendedSize, String alternativeSize, String confidence,
            int confidenceScore, String explanation, String warning, String sizeSystem,
            String fitTendency, String widthProfile) { }

    public static final class UnsupportedFitProfileException extends RuntimeException {
        UnsupportedFitProfileException() { super("This shoe model does not have a supported fit profile."); }
    }
    public static final class FitProfileCoverageException extends RuntimeException {
        FitProfileCoverageException() { super("The measurement is outside this model's supported fit ranges."); }
    }
    public static final class FitAnalysisInsufficientException extends RuntimeException {
        FitAnalysisInsufficientException() { super("The image quality is not sufficient for a useful recommendation."); }
    }
}
