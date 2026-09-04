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

    // Each proxy-intercepted hop commits before reading its parent: a JOIN can retain child S locks
    // while waiting for Order, reversing the Order -> Payment -> Attempt write-lock hierarchy.
    default Optional<UUID> findOrderPublicId(UUID attemptId) {
        return findPaymentIdByAttemptId(attemptId)
                .flatMap(this::findOrderIdByPaymentId).flatMap(this::findOrderPublicIdByOrderId);
    }

    default Optional<UUID> findScopedOrderId(UUID ownerId, String idempotencyKey) {
        return findPaymentIdByOwnerAndKey(ownerId, idempotencyKey)
                .flatMap(this::findOrderIdByPaymentId).flatMap(this::findOrderPublicIdByOrderId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT payment_id FROM payment_attempt WHERE public_id = :attemptId", nativeQuery = true)
    Optional<Long> findPaymentIdByAttemptId(@Param("attemptId") UUID attemptId);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT payment_id FROM payment_attempt WHERE owner_account_public_id = :ownerId AND idempotency_key = :idempotencyKey", nativeQuery = true)
    Optional<Long> findPaymentIdByOwnerAndKey(@Param("ownerId") UUID ownerId, @Param("idempotencyKey") String idempotencyKey);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT order_id FROM payment WHERE id = :paymentId", nativeQuery = true)
    Optional<Long> findOrderIdByPaymentId(@Param("paymentId") Long paymentId);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @Query(value = "SELECT public_id FROM commerce_order WHERE id = :orderId", nativeQuery = true)
    Optional<UUID> findOrderPublicIdByOrderId(@Param("orderId") Long orderId);

    Optional<PaymentAttempt> findByOwnerAccountPublicIdAndIdempotencyKey(UUID ownerAccountPublicId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByPaymentAndStatus(Payment payment, PaymentAttempt.Status status);

    boolean existsByProviderAndProviderTransactionNoAndPublicIdNot(String provider, String providerTransactionNo, UUID publicId);

    @Query(value = "SELECT COUNT(*) FROM payment_attempt attempts JOIN payment payments ON payments.id = attempts.payment_id JOIN commerce_order orders ON orders.id = payments.order_id WHERE orders.public_id = :orderId AND attempts.status = 'SUCCEEDED'", nativeQuery = true)
    int countSucceededForOrder(@Param("orderId") UUID orderId);

}
