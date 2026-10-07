package com.shoecommerce.merchandising;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/operations/storefront/homepage")
public class StorefrontManagementController {
    private final StorefrontMerchandisingService merchandising;

    public StorefrontManagementController(StorefrontMerchandisingService merchandising) {
        this.merchandising = merchandising;
    }

    @GetMapping
    StorefrontMerchandisingService.ManagementState state(@AuthenticationPrincipal SessionPrincipal actor) {
        return merchandising.managementState(actor);
    }

    @PutMapping("/draft")
    StorefrontMerchandisingService.RevisionState save(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody StorefrontMerchandisingService.SaveDraft command) {
        return merchandising.saveDraft(actor, command);
    }

    @PostMapping("/draft-from-published")
    StorefrontMerchandisingService.RevisionState clonePublished(
            @AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody StorefrontMerchandisingService.ClonePublished command) {
        return merchandising.clonePublished(actor, command);
    }

    @PostMapping("/publish")
    StorefrontMerchandisingService.RevisionState publish(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestBody StorefrontMerchandisingService.PublishDraft command) {
        return merchandising.publish(actor, command);
    }
}
