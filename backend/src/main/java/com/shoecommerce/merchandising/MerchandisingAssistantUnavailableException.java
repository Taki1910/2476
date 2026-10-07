package com.shoecommerce.merchandising;

import com.shoecommerce.merchandising.MerchandisingAssistant.ProviderFailureCategory;

public final class MerchandisingAssistantUnavailableException extends RuntimeException {
    private final ProviderFailureCategory category;

    public MerchandisingAssistantUnavailableException(ProviderFailureCategory category, Throwable cause) {
        super("Merchandising assistant unavailable", cause);
        this.category = category;
    }

    public ProviderFailureCategory category() { return category; }

    public String code() {
        return switch (category) {
            case DISABLED -> "MERCHANDISING_AI_DISABLED";
            case NOT_CONFIGURED -> "MERCHANDISING_AI_NOT_CONFIGURED";
            case TIMEOUT -> "MERCHANDISING_AI_TIMEOUT";
            case RATE_LIMITED -> "MERCHANDISING_AI_RATE_LIMITED";
            case REFUSED -> "MERCHANDISING_AI_REFUSED";
            case MALFORMED_OUTPUT -> "MERCHANDISING_AI_INVALID_OUTPUT";
            case UNAVAILABLE -> "MERCHANDISING_AI_UNAVAILABLE";
        };
    }
}
