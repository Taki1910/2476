package com.shoecommerce.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface PaymentReconciliationCommandRepository extends JpaRepository<PaymentReconciliationCommand, Long> {
    Optional<PaymentReconciliationCommand> findByActorAccountPublicIdAndIdempotencyKey(UUID actorId, String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentReconciliationCommand> findLockedByPublicId(UUID publicId);
}
