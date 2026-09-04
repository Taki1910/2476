package com.shoecommerce.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.payment.PaymentAttemptService;

@Service
public class CheckoutHoldExpiryService {
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final PaymentAttemptService payments;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CheckoutHoldExpiryService(CustomerOrderRepository orders, InventoryReservationService reservations,
            PaymentAttemptService payments, Clock clock, PlatformTransactionManager transactionManager) {
        this.orders = orders; this.reservations = reservations; this.payments = payments; this.clock = clock;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void expireForVariant(UUID variantId) {
        Instant now = clock.instant();
        for (UUID orderId : orders.findExpiredCheckoutOrderIds(variantId, now)) {
            transaction.executeWithoutResult(status -> {
            CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow();
            if (!order.pendingPayment()) return;
            payments.expirePendingForOrder(orderId, now);
            reservations.expireAdoptedForOrder(order.paymentFacts().reservationIds(), now);
            order.expire(now);
            });
        }
    }
}
