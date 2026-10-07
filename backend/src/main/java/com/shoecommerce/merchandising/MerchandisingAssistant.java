package com.shoecommerce.merchandising;

import java.util.List;

public interface MerchandisingAssistant {
    Availability availability();
    CopySuggestion rewriteCopy(CopyPrompt prompt);
    IntentInterpretation interpretIntent(String request);

    enum AvailabilityStatus { AVAILABLE, DISABLED, NOT_CONFIGURED }
    enum CopyLocale { VI, EN }
    enum CopyField { TITLE, SUPPORTING_TEXT, CTA_LABEL }
    enum Intent { BEST_SELLERS, PROMOTED, NEEDS_REVIEW, SEARCH, UNSUPPORTED }
    enum ProviderFailureCategory { DISABLED, NOT_CONFIGURED, TIMEOUT, RATE_LIMITED, UNAVAILABLE, REFUSED, MALFORMED_OUTPUT }

    record Availability(boolean enabled, boolean available, AvailabilityStatus status) { }
    record ProductFact(String name, String category, String collection, long available,
            long unitsSold30d, boolean salesComplete, List<String> promotionFacts,
            List<String> readinessWarnings, List<String> placements) { }
    record PromotionFact(String name, String effectType, Double percentageValue, Long fixedAmount) { }
    record CopyPrompt(CopyLocale locale, CopyField field, String currentText, String instruction,
            String sectionPurpose, List<ProductFact> products, PromotionFact promotion) { }
    record CopySuggestion(String primary, List<String> alternatives, String notes) { }
    record IntentInterpretation(Intent intent, String query, String explanation) { }

    final class ProviderFailure extends RuntimeException {
        private final ProviderFailureCategory category;

        public ProviderFailure(ProviderFailureCategory category) {
            super("Merchandising assistant provider failure: " + category);
            this.category = category;
        }

        public ProviderFailure(ProviderFailureCategory category, Throwable cause) {
            super("Merchandising assistant provider failure: " + category, cause);
            this.category = category;
        }

        public ProviderFailureCategory category() { return category; }
    }
}
