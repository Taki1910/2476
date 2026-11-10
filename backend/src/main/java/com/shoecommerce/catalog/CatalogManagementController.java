package com.shoecommerce.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/operations/catalog/products")
public class CatalogManagementController {
    private final CatalogManagementService catalog;

    public CatalogManagementController(CatalogManagementService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<CatalogManagementService.ProductListItem> products(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(name = "q", defaultValue = "") String query) {
        return catalog.products(actor, query);
    }

    @GetMapping("/{productId}")
    CatalogManagementService.ProductDetail product(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID productId) {
        return catalog.product(actor, productId);
    }
}
