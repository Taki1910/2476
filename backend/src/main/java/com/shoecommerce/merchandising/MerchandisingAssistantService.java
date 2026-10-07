package com.shoecommerce.merchandising;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.MerchandisingAssistant.Availability;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopyField;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopyLocale;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopyPrompt;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopySuggestion;
import com.shoecommerce.merchandising.MerchandisingAssistant.IntentInterpretation;
import com.shoecommerce.merchandising.MerchandisingAssistant.ProductFact;
import com.shoecommerce.merchandising.MerchandisingAssistant.ProviderFailure;
import com.shoecommerce.merchandising.MerchandisingAssistant.PromotionFact;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.Candidate;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.Placement;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.PromotionEvidence;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.PromotionState;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.promotion.VoucherService;

@Service
public class MerchandisingAssistantService {
    private final MerchandisingAssistant assistant;
    private final MerchandisingRecommendationService recommendations;
    private final VoucherService vouchers;
    private final AuthorizationPolicy authorization;

    public MerchandisingAssistantService(MerchandisingAssistant assistant,
            MerchandisingRecommendationService recommendations, VoucherService vouchers,
            AuthorizationPolicy authorization) {
        this.assistant = assistant;
        this.recommendations = recommendations;
        this.vouchers = vouchers;
        this.authorization = authorization;
    }

    public Availability availability(SessionPrincipal actor) {
        authorize(actor);
        return assistant.availability();
    }

    public CopySuggestion rewriteCopy(SessionPrincipal actor, CopyRequest request) {
        authorize(actor);
        CopyRequest valid = validate(request);
        List<ProductFact> products = recommendations.evidence(actor, valid.productIds()).stream()
                .map(MerchandisingAssistantService::fact).toList();
        PromotionFact promotion = valid.promotionFamilyId() == null ? null
                : promotion(vouchers.detail(valid.promotionFamilyId()));
        try {
            return assistant.rewriteCopy(new CopyPrompt(valid.locale(), valid.field(), clean(valid.currentText()),
                    valid.instruction().trim(), clean(valid.sectionPurpose()), products, promotion));
        } catch (ProviderFailure failure) {
            throw new MerchandisingAssistantUnavailableException(failure.category(), failure);
        }
    }

    public IntentInterpretation interpretIntent(SessionPrincipal actor, IntentRequest request) {
        authorize(actor);
        String text = request == null ? "" : clean(request.request()).trim();
        if (text.isEmpty() || text.length() > 300) throw invalid("MERCHANDISING_AI_INTENT_INVALID",
                "Manager intent must contain between 1 and 300 characters.");
        try {
            return assistant.interpretIntent(text);
        } catch (ProviderFailure failure) {
            throw new MerchandisingAssistantUnavailableException(failure.category(), failure);
        }
    }

    private void authorize(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
    }

    private static CopyRequest validate(CopyRequest request) {
        if (request == null || request.locale() == null || request.field() == null) {
            throw invalid("MERCHANDISING_AI_COPY_INVALID", "Copy locale and field are required.");
        }
        String instruction = clean(request.instruction()).trim();
        if (instruction.isEmpty() || instruction.length() > 300) throw invalid(
                "MERCHANDISING_AI_INSTRUCTION_INVALID", "Copy instruction must contain between 1 and 300 characters.");
        if (clean(request.currentText()).length() > 500 || clean(request.sectionPurpose()).length() > 200) {
            throw invalid("MERCHANDISING_AI_COPY_INVALID", "Copy context is too long.");
        }
        List<UUID> productIds = request.productIds() == null ? List.of() : List.copyOf(request.productIds());
        if (productIds.size() > 8 || productIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(productIds).size() != productIds.size()) {
            throw invalid("MERCHANDISING_AI_PRODUCTS_INVALID", "Choose up to 8 distinct Products.");
        }
        return new CopyRequest(request.locale(), request.field(), clean(request.currentText()), instruction,
                clean(request.sectionPurpose()), productIds, request.promotionFamilyId());
    }

    private static ProductFact fact(Candidate candidate) {
        List<String> promotionFacts = candidate.promotions().stream()
                .filter(promotion -> promotion.state() == PromotionState.ACTIVE)
                .map(MerchandisingAssistantService::promotionFact).toList();
        List<String> placements = new ArrayList<>();
        candidate.publishedPlacements().forEach(placement -> placements.add(placement("PUBLISHED", placement)));
        candidate.draftPlacements().forEach(placement -> placements.add(placement("DRAFT", placement)));
        return new ProductFact(candidate.name(), candidate.category(), candidate.collection(),
                candidate.aggregateAvailable(), candidate.sales().units30d(), candidate.sales().complete(),
                promotionFacts, candidate.warnings().stream().map(warning -> warning.code()).toList(),
                List.copyOf(placements));
    }

    private static String promotionFact(PromotionEvidence promotion) {
        if (promotion.percentageValue() != null) return "ACTIVE:"
                + BigDecimal.valueOf(promotion.percentageValue()).stripTrailingZeros().toPlainString() + "% off";
        if (promotion.fixedAmount() != null) return "ACTIVE:" + promotion.fixedAmount() + " VND off";
        return "ACTIVE:" + promotion.effectType();
    }

    private static String placement(String state, Placement placement) {
        String title = !clean(placement.titleVi()).isBlank() ? placement.titleVi() : placement.titleEn();
        return state + ":" + placement.sectionType() + ":" + clean(title);
    }

    private static PromotionFact promotion(VoucherService.PublicOffer offer) {
        return new PromotionFact(offer.name(), offer.effectType(),
                offer.percentageValue() == null ? null : offer.percentageValue().doubleValue(), offer.fixedAmount());
    }

    private static String clean(String value) { return value == null ? "" : value; }

    private static InvalidRequestException invalid(String code, String message) {
        return new InvalidRequestException(code, message);
    }

    public record CopyRequest(CopyLocale locale, CopyField field, String currentText, String instruction,
            String sectionPurpose, List<UUID> productIds, UUID promotionFamilyId) { }
    public record IntentRequest(String request) { }
}
