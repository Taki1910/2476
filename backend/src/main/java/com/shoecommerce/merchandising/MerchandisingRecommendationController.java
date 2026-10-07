package com.shoecommerce.merchandising;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.CandidatePage;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.CandidateType;

@RestController
@RequestMapping("/api/v1/operations/storefront")
public class MerchandisingRecommendationController {
    private final MerchandisingRecommendationService recommendations;

    public MerchandisingRecommendationController(MerchandisingRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping("/candidates")
    CandidatePage candidates(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") CandidateType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recommendations.candidates(actor, query, type, page, size);
    }
}
