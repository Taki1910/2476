package com.shoecommerce.merchandising;

import java.util.UUID;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.ProductPresentationService.ManagementState;
import com.shoecommerce.merchandising.ProductPresentationService.PublishDraft;
import com.shoecommerce.merchandising.ProductPresentationService.RevisionView;
import com.shoecommerce.merchandising.ProductPresentationService.SaveDraft;

@RestController
@RequestMapping("/api/v1/operations/product-presentations")
public class ProductPresentationController {
    private final ProductPresentationService presentations;

    public ProductPresentationController(ProductPresentationService presentations) {
        this.presentations = presentations;
    }

    @GetMapping("/products")
    List<ProductPresentationService.ProductReference> products(
            @AuthenticationPrincipal SessionPrincipal actor) {
        return presentations.products(actor);
    }

    @GetMapping("/{productId}")
    ManagementState state(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId) {
        return presentations.managementState(actor, productId);
    }

    @PutMapping("/{productId}/draft")
    RevisionView save(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId,
            @RequestBody SaveDraft command) {
        return presentations.saveDraft(actor, productId, command);
    }

    @PostMapping("/{productId}/publish")
    RevisionView publish(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId,
            @RequestBody PublishDraft command) {
        return presentations.publish(actor, productId, command);
    }
}
