package com.shoecommerce.order;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByPublicId(UUID publicId);
    Optional<CustomerOrder> findByOwnerAccountPublicIdAndCheckoutIdempotencyKey(UUID ownerAccountPublicId, String checkoutIdempotencyKey);
    Page<CustomerOrder> findByOwnerAccountPublicIdOrderByCreatedAtDesc(UUID ownerAccountPublicId, Pageable pageable);
    boolean existsByPriceQuotePublicId(UUID priceQuotePublicId);
    boolean existsByCartQuotePublicId(UUID cartQuotePublicId);
    @Query(value = """
            SELECT orders.public_id
            FROM commerce_order orders
            WHERE orders.status = 'PENDING_PAYMENT'
              AND (orders.price_quote_public_id IS NOT NULL OR orders.cart_quote_public_id IS NOT NULL)
              AND EXISTS (SELECT 1 FROM commerce_order_item items
                JOIN inventory_reservation reservations ON reservations.public_id = items.reservation_public_id
                WHERE items.order_id = orders.id AND items.variant_public_id = :variantId
                  AND reservations.status = 'ADOPTED' AND reservations.expires_at <= :now)
            ORDER BY orders.id
            """, nativeQuery = true)
    List<UUID> findExpiredCheckoutOrderIds(@Param("variantId") UUID variantId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from CustomerOrder orders where orders.publicId = :publicId")
    Optional<CustomerOrder> findLockedByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from CustomerOrder orders where orders.id = :id")
    Optional<CustomerOrder> findLockedById(@Param("id") Long id);
}
