package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_payment_attempt_owner_key", columnNames = {"owner_account_public_id", "idempotency_key"}),
        @UniqueConstraint(name = "UQ_payment_attempt_merchant_reference", columnNames = "merchant_transaction_reference")
})
public class PaymentAttempt {
    enum Status { PENDING, SUCCEEDED, FAILED, CANCELLED, EXPIRED, REVIEW_REQUIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(name = "owner_account_public_id", nullable = false) private UUID ownerAccountPublicId;
    @Nationalized @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(nullable = false, length = 16) private String provider;
    @Column(name = "merchant_transaction_reference", nullable = false, length = 100) private String merchantTransactionReference;
    @Column(name = "client_ip", nullable = false, length = 45) private String clientIp;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "provider_transaction_no", length = 32) private String providerTransactionNo;
    @Column(name = "provider_response_code", length = 8) private String providerResponseCode;
    @Column(name = "provider_transaction_status", length = 8) private String providerTransactionStatus;
    @Column(name = "provider_paid_at") private Instant providerPaidAt;
    @Column(name = "provider_evidence_hash", length = 64) private String providerEvidenceHash;

    protected PaymentAttempt() { }

    static PaymentAttempt create(Payment payment, UUID ownerId, String key, BigDecimal amount,
            String merchantReference, String clientIp, Instant now, Instant expiresAt) {
        if (payment == null || ownerId == null || key == null || amount == null || amount.signum() <= 0
                || merchantReference == null || clientIp == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Payment attempt is invalid");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.publicId = UUID.randomUUID();
        attempt.payment = payment;
        attempt.ownerAccountPublicId = ownerId;
        attempt.idempotencyKey = key;
        attempt.provider = "VNPAY";
        attempt.merchantTransactionReference = merchantReference;
        attempt.clientIp = clientIp;
        attempt.status = Status.PENDING;
        attempt.amount = amount;
        attempt.currency = payment.currency();
        attempt.createdAt = now;
        attempt.expiresAt = expiresAt;
        return attempt;
    }

    void cancel(Instant now) { if (status == Status.PENDING) { status = Status.CANCELLED; cancelledAt = now; } }
    void expire(Instant now) { if (status == Status.PENDING) { status = Status.EXPIRED; resolvedAt = now; } }
    void applyFailure(PaymentProvider.VerifiedResult result, Instant now) {
        if (status == Status.SUCCEEDED || status == Status.REVIEW_REQUIRED) {
            throw new IllegalStateException("Resolved PaymentAttempt cannot fail");
        }
        record(result, now);
        status = Status.FAILED;
        cancelledAt = null;
    }
    void applySuccess(PaymentProvider.VerifiedResult result, Instant now) {
        requirePending();
        record(result, now);
        status = Status.SUCCEEDED;
    }
    // Compatibility for pre-v10 integration fixtures; no production HTTP path invokes these methods.
    void succeed(Instant now) { applySuccess(legacyResult("00"), now); }
    void fail(Instant now) { applyFailure(legacyResult("01"), now); }
    void requireReview(PaymentProvider.VerifiedResult result, Instant now) {
        if (status == Status.SUCCEEDED) throw new IllegalStateException("Successful PaymentAttempt cannot require review");
        record(result, now);
        status = Status.REVIEW_REQUIRED;
        cancelledAt = null;
    }
    boolean sameProviderResult(PaymentProvider.VerifiedResult result) {
        return providerResponseCode != null
                && equalsNullable(providerTransactionNo, result.providerTransactionNo())
                && providerResponseCode.equals(result.responseCode())
                && providerTransactionStatus.equals(result.transactionStatus())
                && providerEvidenceHash.equals(result.evidenceHash());
    }
    private void record(PaymentProvider.VerifiedResult result, Instant now) {
        providerTransactionNo = result.providerTransactionNo();
        providerResponseCode = result.responseCode();
        providerTransactionStatus = result.transactionStatus();
        providerPaidAt = result.providerPaidAt();
        providerEvidenceHash = result.evidenceHash();
        resolvedAt = now;
    }
    private void requirePending() {
        if (status != Status.PENDING) throw new IllegalStateException("Payment attempt is not pending");
    }
    private static boolean equalsNullable(Object left, Object right) { return left == null ? right == null : left.equals(right); }
    private PaymentProvider.VerifiedResult legacyResult(String code) {
        return new PaymentProvider.VerifiedResult(merchantTransactionReference, amount.longValueExact(), null,
                code, code, null, "0".repeat(64));
    }

    boolean pending() { return status == Status.PENDING; }
    boolean succeeded() { return status == Status.SUCCEEDED; }
    boolean failed() { return status == Status.FAILED; }
    boolean reviewRequired() { return status == Status.REVIEW_REQUIRED; }
    UUID publicId() { return publicId; }
    UUID orderPublicId() { return payment.orderPublicId(); }
    UUID ownerAccountPublicId() { return ownerAccountPublicId; }
    String provider() { return provider; }
    String merchantTransactionReference() { return merchantTransactionReference; }
    String clientIp() { return clientIp; }
    String status() { return status.name(); }
    BigDecimal amount() { return amount; }
    String currency() { return currency; }
    Instant createdAt() { return createdAt; }
    Instant expiresAt() { return expiresAt; }
    Instant cancelledAt() { return cancelledAt; }
    Instant resolvedAt() { return resolvedAt; }
    String providerTransactionNo() { return providerTransactionNo; }
    String providerResponseCode() { return providerResponseCode; }
    String providerTransactionStatus() { return providerTransactionStatus; }
    Instant providerPaidAt() { return providerPaidAt; }
}
