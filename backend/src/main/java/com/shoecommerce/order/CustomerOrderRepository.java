package com.shoecommerce.order;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByPublicId(UUID publicId);
    Optional<CustomerOrder> findByOwnerAccountPublicIdAndCheckoutIdempotencyKey(UUID ownerAccountPublicId, String checkoutIdempotencyKey);
    boolean existsByPriceQuotePublicId(UUID priceQuotePublicId);
    @Query(value = """
            SELECT orders.public_id
            FROM commerce_order orders
            JOIN commerce_order_item items ON items.order_id = orders.id
            JOIN inventory_reservation reservations ON reservations.public_id = orders.reservation_public_id
            WHERE orders.status = 'PENDING_PAYMENT'
              AND orders.price_quote_public_id IS NOT NULL
              AND items.variant_public_id = :variantId
              AND reservations.status = 'ADOPTED'
              AND reservations.expires_at <= :now
            ORDER BY orders.id
            """, nativeQuery = true)
    List<UUID> findExpiredCheckoutOrderIds(@Param("variantId") UUID variantId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from CustomerOrder orders where orders.publicId = :publicId")
    Optional<CustomerOrder> findLockedByPublicId(@Param("publicId") UUID publicId);
}
