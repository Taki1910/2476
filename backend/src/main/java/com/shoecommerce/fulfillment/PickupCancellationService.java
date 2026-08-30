package com.shoecommerce.fulfillment;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.payment.VoidService;

@Service
public class PickupCancellationService {
    private final PickupCancellationTransactionService transactions;
    private final VoidService voids;

    PickupCancellationService(PickupCancellationTransactionService transactions, VoidService voids) {
        this.transactions = transactions; this.voids = voids;
    }

    public CancellationView cancel(SessionPrincipal actor, UUID orderId, String key) {
        validateKey(key);
        var local = transactions.cancel(actor, orderId, key);
        var financial = voids.execute(local.financial());
        return new CancellationView(orderId, local.fulfillmentId(), "CANCELLED", financial);
    }

    public VoidService.VoidView retry(SessionPrincipal actor, UUID orderId, String key) {
        validateKey(key);
        return voids.execute(transactions.retry(actor, orderId, key));
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 128 characters");
        }
    }

    public record CancellationView(UUID orderId, UUID fulfillmentId, String fulfillmentStatus,
            VoidService.VoidView financialVoid) { }
}
