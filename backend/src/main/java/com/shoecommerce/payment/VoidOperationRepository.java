package com.shoecommerce.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface VoidOperationRepository extends JpaRepository<VoidOperation, Long> {
    @Query(value = "SELECT operations.* FROM payment_void_operation operations WITH (UPDLOCK, HOLDLOCK) WHERE operations.actor_account_public_id = :actorId AND operations.idempotency_key = :key", nativeQuery = true)
    Optional<VoidOperation> findScopedForUpdate(@Param("actorId") UUID actorId, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoidOperation> findLockedByPublicId(UUID publicId);

    Optional<VoidOperation> findByOrderPublicId(UUID orderPublicId);
}
