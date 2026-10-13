package com.shoecommerce.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/operations/inventory/transfers")
class InventoryTransferController {
    private final InventoryTransferService transfers;
    InventoryTransferController(InventoryTransferService transfers) { this.transfers = transfers; }

    @GetMapping
    List<InventoryTransferService.TransferView> list(@AuthenticationPrincipal SessionPrincipal actor) {
        return transfers.list(actor);
    }

    @GetMapping("/options")
    InventoryTransferService.TransferOptions options(@AuthenticationPrincipal SessionPrincipal actor) {
        return transfers.options(actor);
    }

    @PostMapping
    ResponseEntity<InventoryTransferService.TransferView> create(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateRequest request) {
        InventoryTransferService.TransferView result = transfers.create(actor,
                new InventoryTransferService.CreateCommand(request.sourceLocationId(), request.destinationLocationId(),
                        request.items().stream().map(line -> new InventoryTransferService.LineCommand(
                                line.variantId(), line.quantity())).toList()), key);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{transferId}/dispatch")
    InventoryTransferService.TransferView dispatch(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID transferId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return transfers.dispatch(actor, transferId, key);
    }

    @PostMapping("/{transferId}/receive")
    InventoryTransferService.TransferView receive(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID transferId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return transfers.receive(actor, transferId, key);
    }

    record CreateRequest(@NotNull UUID sourceLocationId, @NotNull UUID destinationLocationId,
            @NotNull @Size(min = 1, max = 100) List<@Valid LineRequest> items) { }
    record LineRequest(@NotNull UUID variantId, @Positive long quantity) { }
}
