package com.shoecommerce.merchandising;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.MerchandisingAssistant.Availability;
import com.shoecommerce.merchandising.MerchandisingAssistant.CopySuggestion;
import com.shoecommerce.merchandising.MerchandisingAssistant.IntentInterpretation;

@RestController
@RequestMapping("/api/v1/operations/storefront/assistant")
public class MerchandisingAssistantController {
    private final MerchandisingAssistantService assistant;

    public MerchandisingAssistantController(MerchandisingAssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/availability")
    Availability availability(@AuthenticationPrincipal SessionPrincipal actor) {
        return assistant.availability(actor);
    }

    @PostMapping("/copy")
    CopySuggestion copy(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody MerchandisingAssistantService.CopyRequest request) {
        return assistant.rewriteCopy(actor, request);
    }

    @PostMapping("/intent")
    IntentInterpretation intent(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody MerchandisingAssistantService.IntentRequest request) {
        return assistant.interpretIntent(actor, request);
    }
}
