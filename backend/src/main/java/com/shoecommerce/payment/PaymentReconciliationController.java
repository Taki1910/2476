package com.shoecommerce.payment;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.SessionPrincipal;

@RestController
final class PaymentReconciliationController {
    private final PaymentReconciliationService reconciliations;
    PaymentReconciliationController(PaymentReconciliationService reconciliations) { this.reconciliations = reconciliations; }

    @PostMapping("/api/v1/operations/payment-attempts/{attemptId}/reconcile")
    PaymentReconciliationService.View reconcile(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID attemptId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return reconciliations.reconcile(actor, attemptId, idempotencyKey);
    }
}
