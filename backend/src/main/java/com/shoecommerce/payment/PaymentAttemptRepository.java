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

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByPublicId(UUID publicId);
    Optional<PaymentAttempt> findByMerchantTransactionReference(String merchantTransactionReference);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT attempts.public_id FROM payment_attempt attempts WHERE attempts.merchant_transaction_reference = :merchantReference", nativeQuery = true)
    Optional<UUID> findPublicIdByMerchantReference(@Param("merchantReference") String merchantReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findLockedByPublicId(UUID publicId);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT orders.public_id FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE attempts.public_id = :attemptId", nativeQuery = true)
    Optional<UUID> findOrderPublicId(@Param("attemptId") UUID attemptId);

    @Query(value = "SELECT attempts.* FROM payment_attempt attempts WITH (UPDLOCK, HOLDLOCK) WHERE attempts.owner_account_public_id = :ownerId AND attempts.idempotency_key = :idempotencyKey", nativeQuery = true)
    Optional<PaymentAttempt> findScopedForUpdate(@Param("ownerId") UUID ownerId, @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByPaymentAndStatus(Payment payment, PaymentAttempt.Status status);

    boolean existsByProviderAndProviderTransactionNoAndPublicIdNot(String provider, String providerTransactionNo, UUID publicId);

    @Query(value = "SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = :orderId AND attempts.status = 'SUCCEEDED'", nativeQuery = true)
    int countSucceededForOrder(@Param("orderId") UUID orderId);

}
