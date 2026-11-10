package com.shoecommerce.inventory;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/operations/inventory/products")
public class InventoryManagementController {
    private final InventoryManagementService inventory;

    public InventoryManagementController(InventoryManagementService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/{productId}")
    InventoryManagementService.ProductInventory product(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID productId) {
        return inventory.product(actor, productId);
    }
}
