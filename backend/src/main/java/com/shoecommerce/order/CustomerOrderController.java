package com.shoecommerce.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.fulfillment.PickupCancellationService;
import com.shoecommerce.fulfillment.PickupFulfillment;
import com.shoecommerce.platform.api.InvalidRequestException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/orders")
public class CustomerOrderController {
    private final CustomerOrderService orders;
    private final PickupCancellationService cancellations;
    public CustomerOrderController(CustomerOrderService orders, PickupCancellationService cancellations) {
        this.orders = orders; this.cancellations = cancellations;
    }

    @PostMapping("/checkout") @ResponseStatus(HttpStatus.CREATED)
    CustomerOrderService.OrderView checkout(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(actor, request.quoteId(), request.requestedQuantity(), idempotencyKey);
    }

    @GetMapping
    CustomerOrderService.OrderPage list(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.readOwnOrders(actor, page, size);
    }

    @GetMapping("/{orderId}")
    CustomerOrderService.OrderView read(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) { return orders.readOwn(actor, orderId); }

    @PostMapping("/{orderId}/cancel")
    Object cancel(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return key == null || key.isBlank() ? orders.cancelOwn(actor, orderId) : cancellations.cancel(actor, orderId, key);
    }

    record CheckoutRequest(@NotNull UUID quoteId, @Positive @Max(10) Long quantity) {
        long requestedQuantity() { return quantity == null ? 1 : quantity; }
    }

    @PostMapping("/cart-checkout") @ResponseStatus(HttpStatus.CREATED)
    CustomerOrderService.OrderView cartCheckout(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CartCheckoutRequest request) {
        if (request.fulfillment().type() == PickupFulfillment.Type.PICKUP
                && request.fulfillment().pickupLocationId() == null) {
            throw new InvalidRequestException("INVALID_FULFILLMENT", "Select an eligible pickup location.");
        }
        return orders.checkoutCart(actor, request.quoteId(), request.items(), request.fulfillment(), key);
    }

    record CartCheckoutRequest(@NotNull UUID quoteId,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 50)
            @Valid java.util.List<com.shoecommerce.pricing.CartQuoteService.LineRequest> items,
            @NotNull @Valid CustomerOrderService.FulfillmentRequest fulfillment) { }
}
