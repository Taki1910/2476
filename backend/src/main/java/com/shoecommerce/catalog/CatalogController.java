package com.shoecommerce.catalog;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }
    @PostMapping("/catalog/products") @ResponseStatus(HttpStatus.CREATED)
    IdResponse createProduct(@AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody ProductRequest request) { return new IdResponse(catalog.createProduct(actor, request.name())); }
    @PostMapping("/catalog/products/{productId}/variants") @ResponseStatus(HttpStatus.CREATED)
    IdResponse createVariant(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId, @Valid @RequestBody VariantRequest request) { return new IdResponse(catalog.createVariant(actor, productId, request.sku(), request.size(), request.color())); }
    @PutMapping("/pricing/variants/{variantId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPrice(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId, @Valid @RequestBody PriceRequest request) { catalog.setPrice(actor, variantId, request.amount()); }
    @PutMapping("/inventory/variants/{variantId}/locations/{locationId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void setStock(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId, @PathVariable UUID locationId, @Valid @RequestBody StockRequest request) { catalog.setStock(actor, variantId, locationId, request.onHand()); }
    @PostMapping("/catalog/variants/{variantId}/publish") @ResponseStatus(HttpStatus.NO_CONTENT)
    void publish(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId) { catalog.publish(actor, variantId); }
    @GetMapping("/catalog/sellable/variants/{variantId}")
    CatalogService.PublishedVariant published(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId) { return catalog.readPublished(actor, variantId); }
    record IdResponse(UUID id) { }
    record ProductRequest(@NotBlank String name) { }
    record VariantRequest(@NotBlank String sku, @NotBlank String size, @NotBlank String color) { }
    record PriceRequest(@Positive long amount) { }
    record StockRequest(@PositiveOrZero long onHand) { }
}
