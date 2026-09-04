package com.shoecommerce.fulfillment;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping({"/api/v1/operations/pickups", "/api/v1/operations/fulfillments"})
class PickupWorkQueueController {
    private final PickupWorkQueueService queue;
    PickupWorkQueueController(PickupWorkQueueService queue) { this.queue = queue; }

    @GetMapping List<PickupWorkQueueService.PickupTask> queue(@AuthenticationPrincipal SessionPrincipal actor) {
        return queue.queue(actor);
    }

    @GetMapping("/{orderId}") PickupWorkQueueService.PickupTask detail(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return queue.detail(actor, orderId);
    }
}
