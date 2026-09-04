package com.shoecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shoecommerce.identity.OwnershipPolicy;

class DemoPaymentControllerTest {
    @Test
    void redirectsToConfiguredFrontendResultOrigin() {
        PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
        OwnershipPolicy ownership = mock(OwnershipPolicy.class);
        VerifiedPaymentResultService results = mock(VerifiedPaymentResultService.class);
        PaymentAttempt attempt = mock(PaymentAttempt.class);
        UUID attemptId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String reference = "demo-payment-reference";
        when(attempts.findByMerchantTransactionReference(reference)).thenReturn(Optional.of(attempt));
        when(attempt.ownerAccountPublicId()).thenReturn(ownerId);
        when(attempt.amount()).thenReturn(BigDecimal.valueOf(5270000));
        when(attempt.publicId()).thenReturn(attemptId);
        doNothing().when(ownership).requireOwnership(null, ownerId);

        var response = new DemoPaymentController(attempts, ownership, results,
                Clock.fixed(Instant.parse("2026-09-04T05:00:00Z"), ZoneOffset.UTC),
                "http://127.0.0.1:5173/payment/result").complete(null, reference);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("http://127.0.0.1:5173/payment/result?attemptId=" + attemptId));
    }
}
