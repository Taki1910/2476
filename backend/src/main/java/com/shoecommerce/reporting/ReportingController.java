package com.shoecommerce.reporting;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
@RequestMapping("/api/v1/operations/reports")
public class ReportingController {
    private final ReportingService reports;

    public ReportingController(ReportingService reports) {
        this.reports = reports;
    }

    @GetMapping("/scope")
    ReportingService.ScopeReport scope(@AuthenticationPrincipal SessionPrincipal actor) {
        return reports.scope(actor);
    }

    @GetMapping("/net-sales")
    ReportingService.NetSalesReport netSales(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam LocalDate fromDate, @RequestParam LocalDate toDate, @RequestParam UUID locationId) {
        return reports.netSales(actor, fromDate, toDate, locationId);
    }

    @GetMapping("/product-sales")
    ReportingService.ProductSalesReport productSales(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam LocalDate fromDate, @RequestParam LocalDate toDate, @RequestParam UUID locationId) {
        return reports.productSales(actor, fromDate, toDate, locationId);
    }

    @GetMapping("/inventory")
    ReportingService.InventoryReport inventory(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam UUID locationId, @RequestParam(required = false) String sku) {
        return reports.inventory(actor, locationId, sku);
    }

    @GetMapping("/reconciliation")
    ReportingService.ReconciliationReport reconciliation(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam LocalDate fromDate, @RequestParam LocalDate toDate, @RequestParam UUID locationId) {
        return reports.reconciliation(actor, fromDate, toDate, locationId);
    }
}
