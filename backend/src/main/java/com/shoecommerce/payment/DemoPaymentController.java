package com.shoecommerce.payment;

import java.net.URI;
import java.time.Clock;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shoecommerce.identity.OwnershipPolicy;
import com.shoecommerce.identity.SessionPrincipal;

@RestController
@Profile("demo")
final class DemoPaymentController {
    private final PaymentAttemptRepository attempts;
    private final OwnershipPolicy ownership;
    private final VerifiedPaymentResultService results;
    private final Clock clock;

    DemoPaymentController(PaymentAttemptRepository attempts, OwnershipPolicy ownership,
            VerifiedPaymentResultService results, Clock clock) {
        this.attempts = attempts; this.ownership = ownership; this.results = results; this.clock = clock;
    }

    @GetMapping("/api/v1/payments/demo/complete")
    ResponseEntity<Void> complete(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam String reference) {
        PaymentAttempt attempt = attempts.findByMerchantTransactionReference(reference).orElseThrow();
        ownership.requireOwnership(actor, attempt.ownerAccountPublicId());
        results.apply(new PaymentProvider.VerifiedResult(reference, attempt.amount().longValueExact(),
                "DEMO-" + reference, "00", "00", clock.instant(), "demo"));
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION,
                URI.create("http://localhost:5173/payment/result?attemptId=" + attempt.publicId()).toASCIIString()).build();
    }
}
