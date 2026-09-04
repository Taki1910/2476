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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.CartQuoteService;
import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontController {
    private final StorefrontCatalogService catalog;
    private final PriceQuoteService pricing;
    private final CartQuoteService cartPricing;

    public StorefrontController(StorefrontCatalogService catalog, PriceQuoteService pricing, CartQuoteService cartPricing) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.cartPricing = cartPricing;
    }

    @GetMapping("/products")
    List<StorefrontCatalogService.ProductSummary> products(@RequestParam(required = false) String q) {
        return catalog.browse(q);
    }

    @GetMapping("/products/{productId}")
    StorefrontCatalogService.ProductDetail product(@PathVariable UUID productId) {
        return catalog.detail(productId);
    }

    @GetMapping("/hero")
    StorefrontCatalogService.HeroCarousel hero() {
        return catalog.hero();
    }

    @PostMapping("/price-quotes")
    @ResponseStatus(HttpStatus.CREATED)
    PriceQuoteService.QuoteView quote(@AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody QuoteRequest request) {
        return pricing.quote(actor, request.variantId());
    }

    record QuoteRequest(@NotNull UUID variantId) { }

    @PostMapping("/cart-quotes") @ResponseStatus(HttpStatus.CREATED)
    CartQuoteService.QuoteView cartQuote(@AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody CartQuoteRequest request) { return cartPricing.quote(actor, request.items()); }

    record CartQuoteRequest(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 50)
            @Valid List<CartQuoteService.LineRequest> items) { }
}
