package com.shoecommerce.inventory;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class InventoryReservationController {
    private final InventoryReservationService reservations;
    public InventoryReservationController(InventoryReservationService reservations) { this.reservations = reservations; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    InventoryReservationService.ReservationView reserve(@AuthenticationPrincipal SessionPrincipal actor, @Valid @RequestBody ReserveRequest request) { return reservations.reserve(actor, request.variantId(), request.locationId(), request.quantity()); }

    @GetMapping("/{reservationId}")
    InventoryReservationService.ReservationView read(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID reservationId) { return reservations.readOwn(actor, reservationId); }

    @DeleteMapping("/{reservationId}")
    InventoryReservationService.ReservationView release(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID reservationId) { return reservations.releaseOwn(actor, reservationId); }

    record ReserveRequest(@NotNull UUID variantId, @NotNull UUID locationId, @Positive long quantity) { }
}
