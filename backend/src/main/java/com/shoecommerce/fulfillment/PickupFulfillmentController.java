package com.shoecommerce.fulfillment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
public class PickupFulfillmentController {
    private final PickupFulfillmentService fulfillments;

    public PickupFulfillmentController(PickupFulfillmentService fulfillments) { this.fulfillments = fulfillments; }

    @PostMapping("/api/v1/orders/{orderId}/pickup-fulfillment")
    @ResponseStatus(HttpStatus.CREATED)
    PickupFulfillmentService.PickupFulfillmentView create(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId) {
        return fulfillments.create(actor, orderId);
    }

    @PostMapping("/api/v1/pickup-fulfillments/{fulfillmentId}/start-picking")
    PickupFulfillmentService.PickupFulfillmentView startPicking(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID fulfillmentId) {
        return fulfillments.startPicking(actor, fulfillmentId);
    }

    @PostMapping("/api/v1/pickup-fulfillments/{fulfillmentId}/prepare")
    PickupFulfillmentService.PickupFulfillmentView prepare(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID fulfillmentId) {
        return fulfillments.prepare(actor, fulfillmentId);
    }

    @PostMapping("/api/v1/pickup-fulfillments/{fulfillmentId}/handover")
    PickupFulfillmentService.PickupFulfillmentView handover(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID fulfillmentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return fulfillments.handover(actor, fulfillmentId, key);
    }
}
