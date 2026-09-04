package com.shoecommerce.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

interface VoidOperationRepository extends JpaRepository<VoidOperation, Long> {
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT order_public_id FROM payment_void_operation WHERE actor_account_public_id = :actorId AND idempotency_key = :key", nativeQuery = true)
    Optional<UUID> findScopedOrderId(@Param("actorId") UUID actorId, @Param("key") String key);

    Optional<VoidOperation> findByActorAccountPublicIdAndIdempotencyKey(UUID actorAccountPublicId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoidOperation> findLockedByPublicId(UUID publicId);

    Optional<VoidOperation> findByOrderPublicId(UUID orderPublicId);
}
