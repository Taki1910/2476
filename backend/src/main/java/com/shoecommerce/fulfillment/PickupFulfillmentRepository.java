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
    @Query(value = "SELECT order_id FROM pickup_fulfillment WHERE public_id = :fulfillmentId", nativeQuery = true)
    Optional<Long> findOrderId(@Param("fulfillmentId") UUID fulfillmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PickupFulfillment> findLockedByPublicId(UUID publicId);

    @Query(value = "SELECT fulfillments.public_id FROM pickup_fulfillment fulfillments WHERE fulfillments.handed_over_by_account_public_id = :actorId AND fulfillments.handover_idempotency_key = :key", nativeQuery = true)
    Optional<UUID> findHandoverReplayId(@Param("actorId") UUID actorId, @Param("key") String key);

    @Query(value = "SELECT public_id FROM pickup_fulfillment WHERE dispatched_by_account_public_id = :actorId AND dispatch_idempotency_key = :key", nativeQuery = true)
    Optional<UUID> findDispatchReplayId(@Param("actorId") UUID actorId, @Param("key") String key);

    @Query(value = "SELECT public_id FROM pickup_fulfillment WHERE delivered_by_account_public_id = :actorId AND delivery_idempotency_key = :key", nativeQuery = true)
    Optional<UUID> findDeliveryReplayId(@Param("actorId") UUID actorId, @Param("key") String key);
}
