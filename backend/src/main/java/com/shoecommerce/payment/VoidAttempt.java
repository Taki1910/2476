package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_void_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_payment_void_attempt_generation", columnNames = {"void_operation_id", "generation"}),
        @UniqueConstraint(name = "UQ_payment_void_attempt_key", columnNames = {"void_operation_id", "idempotency_key"}),
        @UniqueConstraint(name = "UQ_payment_void_attempt_actor_key", columnNames = {"actor_account_public_id", "idempotency_key"})
})
public class VoidAttempt {
    enum Status { CREATED, SUCCEEDED, DEFINITIVE_FAILED, UNKNOWN, REVIEW_REQUIRED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "void_operation_id", nullable = false) private VoidOperation operation;
    @Column(nullable = false) private int generation;
    @Column(name = "actor_account_public_id", nullable = false) private UUID actorAccountPublicId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "merchant_request_reference", nullable = false, unique = true, length = 32) private String merchantRequestReference;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Column(name = "provider_response_id", length = 32) private String providerResponseId;
    @Column(name = "provider_response_code", length = 8) private String providerResponseCode;
    @Column(name = "provider_transaction_status", length = 8) private String providerTransactionStatus;
    @Column(name = "provider_transaction_no", length = 32) private String providerTransactionNo;
    @Column(name = "provider_evidence_hash", length = 64) private String providerEvidenceHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected VoidAttempt() { }

    static VoidAttempt create(VoidOperation operation, int generation, UUID actorId, String key, Instant now) {
        if (operation == null || generation <= 0 || actorId == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Void attempt is invalid");
        }
        VoidAttempt attempt = new VoidAttempt();
        attempt.publicId = UUID.randomUUID();
        attempt.operation = operation;
        attempt.generation = generation;
        attempt.actorAccountPublicId = actorId;
        attempt.idempotencyKey = key;
        attempt.merchantRequestReference = UUID.randomUUID().toString().replace("-", "");
        attempt.amount = operation.requestedAmount();
        attempt.status = Status.CREATED;
        attempt.createdAt = now;
        return attempt;
    }

    void apply(VoidProvider.Result result, Instant now) {
        if (status != Status.CREATED) throw new IllegalStateException("Void attempt is already resolved");
        providerResponseId = result.responseId();
        providerResponseCode = result.responseCode();
        providerTransactionStatus = result.transactionStatus();
        providerTransactionNo = result.providerTransactionNo();
        providerEvidenceHash = result.evidenceHash();
        resolvedAt = now;
        status = switch (result.outcome()) {
            case SUCCEEDED -> Status.SUCCEEDED;
            case DEFINITIVE_FAILED -> Status.DEFINITIVE_FAILED;
            case UNKNOWN -> Status.UNKNOWN;
            case REVIEW_REQUIRED -> Status.REVIEW_REQUIRED;
        };
    }

    void markUnknown(Instant now) {
        if (status != Status.CREATED) throw new IllegalStateException("Void attempt is already resolved");
        status = Status.UNKNOWN;
        resolvedAt = now;
    }

    UUID publicId() { return publicId; }
    VoidOperation operation() { return operation; }
    int generation() { return generation; }
    UUID actorAccountPublicId() { return actorAccountPublicId; }
    String idempotencyKey() { return idempotencyKey; }
    String merchantRequestReference() { return merchantRequestReference; }
    BigDecimal amount() { return amount; }
    String status() { return status.name(); }
    Instant createdAt() { return createdAt; }
    Instant resolvedAt() { return resolvedAt; }
}
