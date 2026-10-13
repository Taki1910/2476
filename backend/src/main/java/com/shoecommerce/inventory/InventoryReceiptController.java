package com.shoecommerce.inventory;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
public class InventoryReceiptController {
    private final InventoryReceiptService receipts;

    public InventoryReceiptController(InventoryReceiptService receipts) {
        this.receipts = receipts;
    }

    @PostMapping("/api/v1/operations/inventory/variants/{variantId}/locations/{locationId}/receipts")
    ResponseEntity<InventoryReceiptService.ReceiptResult> receive(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID variantId, @PathVariable UUID locationId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReceiptRequest request) {
        InventoryReceiptService.ReceiptResult result = receipts.receive(actor, variantId, locationId,
                request.quantity(), request.note(), key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    record ReceiptRequest(@Positive long quantity, @Size(max = 256) String note) { }
}
