package com.shoecommerce.pos;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/operations/pos")
public class PosController {
    private final PosService pos;

    public PosController(PosService pos) { this.pos = pos; }

    @GetMapping("/registers")
    List<PosService.RegisterView> registers(@AuthenticationPrincipal SessionPrincipal actor) {
        return pos.registers(actor);
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    PosService.ShiftView open(@AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody OpenShiftRequest request) {
        return pos.openShift(actor, request.registerId());
    }

    @GetMapping("/shifts/current")
    ResponseEntity<PosService.ShiftView> current(@AuthenticationPrincipal SessionPrincipal actor) {
        return ResponseEntity.ofNullable(pos.currentShift(actor));
    }

    @PostMapping("/shifts/{shiftId}/close")
    PosService.ShiftView close(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID shiftId) {
        return pos.closeShift(actor, shiftId);
    }

    @GetMapping("/variants")
    PosService.VariantView lookup(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam UUID shiftId, @RequestParam String sku) {
        return pos.lookup(actor, shiftId, sku);
    }

    @PostMapping("/sales")
    ResponseEntity<PosService.ReceiptView> sell(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody SaleRequest request) {
        PosService.SaleResult result = pos.sell(actor, request.shiftId(), request.variantId(), key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.receipt());
    }

    @GetMapping("/sales/{orderId}")
    PosService.ReceiptView receipt(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId) {
        return pos.receipt(actor, orderId);
    }

    record OpenShiftRequest(@NotNull UUID registerId) { }
    record SaleRequest(@NotNull UUID shiftId, @NotNull UUID variantId) { }
}
