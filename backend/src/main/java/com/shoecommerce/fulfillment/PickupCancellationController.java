package com.shoecommerce.fulfillment;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.payment.VoidService;

@RestController
class PickupCancellationController {
    private final PickupCancellationService cancellations;
    PickupCancellationController(PickupCancellationService cancellations) { this.cancellations = cancellations; }

    @PostMapping("/api/v1/orders/{orderId}/void/retry")
    VoidService.VoidView retry(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return cancellations.retry(actor, orderId, key);
    }
}
