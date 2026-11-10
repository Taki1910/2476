package com.shoecommerce.order;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.promotion.PromotionService;

@Service
public class CheckoutHoldExpiryService {
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final PaymentAttemptService payments;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final PromotionService promotions;
    private final JdbcTemplate jdbc;

    public CheckoutHoldExpiryService(CustomerOrderRepository orders, InventoryReservationService reservations,
            PaymentAttemptService payments, Clock clock, PlatformTransactionManager transactionManager,
            PromotionService promotions, JdbcTemplate jdbc) {
        this.orders = orders; this.reservations = reservations; this.payments = payments; this.clock = clock;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.promotions=promotions;
        this.jdbc = jdbc;
    }

    public void expireForVariant(UUID variantId) {
        Instant now = clock.instant();
        expire(orders.findExpiredCheckoutOrderIds(variantId, now), now);
    }

    public void expireRelevant(UUID productId) {
        Instant now = clock.instant();
        LocalDateTime databaseNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        String productFilter = productId == null ? "" : " AND products.public_id = ?";
        Object[] parameters = productId == null
                ? new Object[] { databaseNow }
                : new Object[] { databaseNow, productId };
        List<UUID> orderIds = jdbc.query("""
                SELECT DISTINCT orders.public_id
                FROM commerce_order orders WITH (READPAST)
                JOIN commerce_order_item items WITH (READPAST) ON items.order_id = orders.id
                JOIN inventory_reservation reservations WITH (READPAST)
                     ON reservations.public_id = items.reservation_public_id
                JOIN catalog_product_variant variants WITH (READPAST)
                     ON variants.public_id = items.variant_public_id
                JOIN catalog_product products WITH (READPAST) ON products.id = variants.product_id
                WHERE orders.status = 'PENDING_PAYMENT'
                  AND (orders.price_quote_public_id IS NOT NULL OR orders.cart_quote_public_id IS NOT NULL)
                  AND reservations.status = 'ADOPTED'
                  AND reservations.expires_at <= ?
                """ + productFilter + " ORDER BY orders.public_id",
                (row, index) -> row.getObject("public_id", UUID.class), parameters);
        expire(orderIds, now);
    }

    private void expire(List<UUID> orderIds, Instant now) {
        for (UUID orderId : orderIds) {
            transaction.executeWithoutResult(status -> {
                CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow();
                if (!order.pendingPayment()) return;
                payments.expirePendingForOrder(orderId, now);
                promotions.lockUsage(orderId);
                promotions.release(orderId, now);
                reservations.expireAdoptedForOrder(order.paymentFacts().reservationIds(), now);
                order.expire(now);
            });
        }
    }
}
