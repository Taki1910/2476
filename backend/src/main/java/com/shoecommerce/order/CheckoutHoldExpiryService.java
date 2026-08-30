package com.shoecommerce.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.payment.PaymentAttemptService;

@Service
public class CheckoutHoldExpiryService {
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final PaymentAttemptService payments;
    private final Clock clock;

    public CheckoutHoldExpiryService(CustomerOrderRepository orders, InventoryReservationService reservations,
            PaymentAttemptService payments, Clock clock) {
        this.orders = orders; this.reservations = reservations; this.payments = payments; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireForVariant(UUID variantId) {
        Instant now = clock.instant();
        for (UUID orderId : orders.findExpiredCheckoutOrderIds(variantId, now)) {
            CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow();
            if (!order.pendingPayment()) continue;
            payments.expirePendingForOrder(orderId, now);
            reservations.expireAdoptedForOrder(order.reservationPublicId(), now);
            order.expire(now);
        }
    }
}
