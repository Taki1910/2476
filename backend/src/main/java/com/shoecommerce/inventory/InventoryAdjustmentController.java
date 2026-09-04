package com.shoecommerce.inventory;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@RestController
public class InventoryAdjustmentController {
    private final InventoryAdjustmentService adjustments;

    public InventoryAdjustmentController(InventoryAdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    @PutMapping("/api/v1/inventory/variants/{variantId}/locations/{locationId}")
    InventoryAdjustmentService.AdjustmentResult adjust(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID variantId, @PathVariable UUID locationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody AdjustmentRequest request) {
        return adjustments.adjust(actor, variantId, locationId, request.onHand(), request.reason(), key);
    }

    record AdjustmentRequest(@PositiveOrZero long onHand, @NotBlank @Size(max = 256) String reason) { }
}
