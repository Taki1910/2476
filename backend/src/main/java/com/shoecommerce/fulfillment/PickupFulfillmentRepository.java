package com.shoecommerce.fulfillment;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.LockModeType;

public interface PickupFulfillmentRepository extends JpaRepository<PickupFulfillment, Long> {
    boolean existsByOrder(CustomerOrder order);
    Optional<PickupFulfillment> findByOrder(CustomerOrder order);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PickupFulfillment> findLockedByOrder(CustomerOrder order);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT orders.public_id FROM pickup_fulfillment fulfillments JOIN commerce_order orders ON orders.id = fulfillments.order_id WHERE fulfillments.public_id = :fulfillmentId", nativeQuery = true)
    Optional<UUID> findOrderPublicId(@Param("fulfillmentId") UUID fulfillmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PickupFulfillment> findLockedByPublicId(UUID publicId);

    @Query(value = "SELECT fulfillments.* FROM pickup_fulfillment fulfillments WITH (UPDLOCK, HOLDLOCK) WHERE fulfillments.handed_over_by_account_public_id = :actorId AND fulfillments.handover_idempotency_key = :key", nativeQuery = true)
    Optional<PickupFulfillment> findHandoverReplayForUpdate(@Param("actorId") UUID actorId, @Param("key") String key);
}
