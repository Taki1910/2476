package com.shoecommerce.catalog;

import java.util.UUID;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.pricing.VariantPrice;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }
    @PostMapping("/catalog/products") @ResponseStatus(HttpStatus.CREATED)
    CatalogService.ProductState createProduct(@AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody ProductRequest request) { return catalog.createProduct(actor, request.name(), request.category(), request.collection(), request.heroImage(), request.primaryImage()); }
    @PutMapping("/catalog/products/{productId}")
    CatalogService.ProductState updateProduct(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request) {
        return catalog.updateProduct(actor, productId, request.expectedEntityVersion(), request.name(),
                request.category(), request.collection(), request.heroImage(), request.primaryImage());
    }
    @PostMapping("/catalog/products/{productId}/variants") @ResponseStatus(HttpStatus.CREATED)
    IdResponse createVariant(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId, @Valid @RequestBody VariantRequest request) { return new IdResponse(catalog.createVariant(actor, productId, request.sku(), request.size(), request.color(), request.barcode())); }
    @PutMapping("/catalog/variants/{variantId}/barcode") @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateBarcode(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId,
            @Valid @RequestBody BarcodeRequest request) {
        catalog.updateBarcode(actor, variantId, request.expectedEntityVersion(), request.barcode());
    }
    @PutMapping("/pricing/variants/{variantId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPrice(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId, @Valid @RequestBody PriceRequest request) { catalog.setPrice(actor, variantId, request.amount()); }
    @PostMapping("/catalog/variants/{variantId}/publish") @ResponseStatus(HttpStatus.NO_CONTENT)
    void publish(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId,
            @Valid @RequestBody LifecycleRequest request) { catalog.publish(actor, variantId, request.expectedEntityVersion()); }
    @PostMapping("/catalog/variants/{variantId}/retire") @ResponseStatus(HttpStatus.NO_CONTENT)
    void retire(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId,
            @Valid @RequestBody LifecycleRequest request) { catalog.retire(actor, variantId, request.expectedEntityVersion()); }
    @PostMapping("/catalog/variants/{variantId}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId,
            @Valid @RequestBody LifecycleRequest request) { catalog.restore(actor, variantId, request.expectedEntityVersion()); }
    @GetMapping("/catalog/sellable/variants/{variantId}")
    CatalogService.PublishedVariant published(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID variantId) { return catalog.readPublished(actor, variantId); }
    @GetMapping("/catalog/products/{productId}/evidence")
    CatalogService.ProductEvidenceState evidence(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId) { return catalog.evidence(actor, productId); }
    @PutMapping("/catalog/products/{productId}/evidence")
    CatalogService.ProductEvidenceState replaceEvidence(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId, @Valid @RequestBody ProductEvidenceRequest request) {
        return catalog.replaceEvidence(actor, productId, request.expectedEntityVersion(), request.evidence());
    }
    @GetMapping("/catalog/products")
    List<CatalogService.ProductEvidenceState> products(@AuthenticationPrincipal SessionPrincipal actor) { return catalog.products(actor); }
    record IdResponse(UUID id) { }
    record ProductRequest(@NotBlank String name, String category, String collection, String heroImage,
            String primaryImage) { }
    record ProductUpdateRequest(@NotNull @PositiveOrZero Long expectedEntityVersion, @NotBlank String name,
            String category, String collection, String heroImage, String primaryImage) { }
    record VariantRequest(@NotBlank String sku, @NotBlank String size, @NotBlank String color,
            @Size(max = 128) String barcode) { }
    record BarcodeRequest(@NotNull @PositiveOrZero Long expectedEntityVersion, @Size(max = 128) String barcode) { }
    record LifecycleRequest(@NotNull @PositiveOrZero Long expectedEntityVersion) { }
    record PriceRequest(@Positive @Max(VariantPrice.MAX_AMOUNT) long amount) { }
    record ProductEvidenceRequest(@NotNull @PositiveOrZero Long expectedEntityVersion,
            ProductEvidence.IntendedUse intendedUse, ProductEvidence.PrimarySurface primarySurface,
            ProductEvidence.UpperConstruction upperConstruction, ProductEvidence.CutProfile cutProfile,
            ProductEvidence.PrimarySoleProfile primarySoleProfile) {
        ProductEvidence evidence() { return new ProductEvidence(intendedUse, primarySurface, upperConstruction, cutProfile, primarySoleProfile); }
    }
}
