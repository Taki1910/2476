package com.shoecommerce.payment;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@Profile("test")
class PaymentProviderEventController {
    private final PaymentProviderEventService providerEvents;
    PaymentProviderEventController(PaymentProviderEventService providerEvents) { this.providerEvents = providerEvents; }

    @PostMapping("/api/v1/payment/provider-events")
    ResponseEntity<PaymentProviderEventService.ProviderEventView> apply(
            @AuthenticationPrincipal SessionPrincipal actor,
            @Valid @RequestBody ApplyProviderEventRequest request) {
        PaymentProviderEventService.ApplicationResult result = providerEvents.apply(
                actor, request.providerEventId(), request.paymentAttemptId(), request.outcome());
        if (!result.accepted()) throw new BusinessConflictException(result.conflict());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.event());
    }

    record ApplyProviderEventRequest(@NotBlank @Size(max = 128) String providerEventId,
            @NotNull UUID paymentAttemptId, @NotNull PaymentProviderEvent.Outcome outcome) { }
}
