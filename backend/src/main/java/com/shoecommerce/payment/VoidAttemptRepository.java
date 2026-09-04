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

interface VoidAttemptRepository extends JpaRepository<VoidAttempt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoidAttempt> findLockedByPublicId(UUID publicId);
    Optional<VoidAttempt> findFirstByOperationOrderByGenerationDesc(VoidOperation operation);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT operations.order_public_id FROM payment_void_attempt attempts JOIN payment_void_operation operations ON operations.id = attempts.void_operation_id WHERE attempts.actor_account_public_id = :actorId AND attempts.idempotency_key = :key", nativeQuery = true)
    Optional<UUID> findScopedOrderId(@Param("actorId") UUID actorId, @Param("key") String key);

    Optional<VoidAttempt> findByActorAccountPublicIdAndIdempotencyKey(UUID actorAccountPublicId, String idempotencyKey);
}
