package com.shoecommerce.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface VoidAttemptRepository extends JpaRepository<VoidAttempt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoidAttempt> findLockedByPublicId(UUID publicId);
    Optional<VoidAttempt> findFirstByOperationOrderByGenerationDesc(VoidOperation operation);

    @Query(value = "SELECT attempts.* FROM payment_void_attempt attempts WITH (UPDLOCK, HOLDLOCK) WHERE attempts.actor_account_public_id = :actorId AND attempts.idempotency_key = :key", nativeQuery = true)
    Optional<VoidAttempt> findScopedForUpdate(@Param("actorId") UUID actorId, @Param("key") String key);
}
