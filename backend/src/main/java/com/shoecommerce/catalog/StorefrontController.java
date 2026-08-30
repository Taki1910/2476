package com.shoecommerce.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.pricing.PriceQuoteService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontController {
    private final StorefrontCatalogService catalog;
    private final PriceQuoteService pricing;

    public StorefrontController(StorefrontCatalogService catalog, PriceQuoteService pricing) {
        this.catalog = catalog;
        this.pricing = pricing;
    }

    @GetMapping("/products")
    List<StorefrontCatalogService.ProductSummary> products(@AuthenticationPrincipal SessionPrincipal actor) {
        return catalog.browse(actor);
    }

    @GetMapping("/products/{productId}")
    StorefrontCatalogService.ProductDetail product(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID productId) {
        return catalog.detail(actor, productId);
    }

    @PostMapping("/price-quotes")
    @ResponseStatus(HttpStatus.CREATED)
    PriceQuoteService.QuoteView quote(@AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody QuoteRequest request) {
        return pricing.quote(actor, request.variantId());
    }

    record QuoteRequest(@NotNull UUID variantId) { }
}
