package com.shoecommerce.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PaymentAttemptController {
    private final PaymentAttemptService attempts;
    public PaymentAttemptController(PaymentAttemptService attempts) { this.attempts = attempts; }

    @PostMapping("/api/v1/orders/{orderId}/payments")
    ResponseEntity<PaymentAttemptService.InitiationResult> initiatePayment(
            @AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        PaymentAttemptService.InitiationResult result = attempts.initiate(actor, orderId, key, request.getRemoteAddr());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    @PostMapping("/api/v1/orders/{orderId}/payment-attempts")
    ResponseEntity<PaymentAttemptService.PaymentAttemptView> initiate(@AuthenticationPrincipal SessionPrincipal actor,
            @PathVariable UUID orderId, @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        PaymentAttemptService.InitiationResult result = attempts.initiate(actor, orderId, key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.attempt());
    }

    @GetMapping("/api/v1/payment-attempts/{attemptId}")
    PaymentAttemptService.PaymentAttemptView read(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID attemptId) { return attempts.readOwn(actor, attemptId); }
}
