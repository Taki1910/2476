package com.shoecommerce.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.fulfillment.PickupCancellationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    private final PickupCancellationService cancellations;
    public CustomerOrderController(CustomerOrderService orders, PickupCancellationService cancellations) {
        this.orders = orders; this.cancellations = cancellations;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    CustomerOrderService.OrderView create(@AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody CreateOrderRequest request) { return orders.create(actor, request.reservationId()); }

    @PostMapping("/checkout") @ResponseStatus(HttpStatus.CREATED)
    CustomerOrderService.OrderView checkout(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(actor, request.quoteId(), idempotencyKey);
    }

    @GetMapping("/{orderId}")
    CustomerOrderService.OrderView read(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) { return orders.readOwn(actor, orderId); }

    @PostMapping("/{orderId}/cancel")
    Object cancel(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return key == null || key.isBlank() ? orders.cancelOwn(actor, orderId) : cancellations.cancel(actor, orderId, key);
    }

    record CreateOrderRequest(@NotNull UUID reservationId) { }
    record CheckoutRequest(@NotNull UUID quoteId) { }
}
