package com.shoecommerce.payment;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_reconciliation_command", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_payment_reconciliation_actor_key", columnNames = {"actor_account_public_id", "idempotency_key"}),
        @UniqueConstraint(name = "UQ_payment_reconciliation_provider_request", columnNames = "provider_request_id")
})
class PaymentReconciliationCommand {
    enum Status { QUERYING, PROVIDER_UNAVAILABLE, EVIDENCE_RECORDED, MISMATCH, COMPLETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "payment_attempt_public_id", nullable = false) private UUID paymentAttemptPublicId;
    @Column(name = "actor_account_public_id", nullable = false) private UUID actorAccountPublicId;
    @Nationalized @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "provider_request_id", nullable = false, length = 32) private String providerRequestId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "provider_outcome", length = 24) private PaymentQueryProvider.Outcome providerOutcome;
    @Column(name = "provider_response_id", length = 32) private String providerResponseId;
    @Column(name = "provider_response_code", length = 8) private String providerResponseCode;
    @Column(name = "provider_transaction_status", length = 8) private String providerTransactionStatus;
    @Column(name = "provider_transaction_no", length = 32) private String providerTransactionNo;
    @Column(name = "provider_paid_at") private Instant providerPaidAt;
    @Column(name = "provider_evidence_hash", length = 64) private String providerEvidenceHash;
    @Column(name = "previous_attempt_status", length = 24) private String previousAttemptStatus;
    @Column(name = "current_attempt_status", length = 24) private String currentAttemptStatus;
    @Column(name = "local_transition_applied") private Boolean localTransitionApplied;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected PaymentReconciliationCommand() { }

    static PaymentReconciliationCommand create(UUID attemptId, UUID actorId, String key,
            String providerRequestId, Instant now) {
        if (attemptId == null || actorId == null || key == null || key.isBlank() || key.length() > 128
                || providerRequestId == null || !providerRequestId.matches("[A-Za-z0-9]{1,32}") || now == null) {
            throw new IllegalArgumentException("Payment reconciliation command is invalid");
        }
        PaymentReconciliationCommand command = new PaymentReconciliationCommand();
        command.publicId = UUID.randomUUID(); command.paymentAttemptPublicId = attemptId;
        command.actorAccountPublicId = actorId; command.idempotencyKey = key;
        command.providerRequestId = providerRequestId; command.status = Status.QUERYING;
        command.createdAt = now;
        return command;
    }

    void record(PaymentQueryProvider.Result result, Instant now) {
        require(Status.QUERYING);
        if (result == null || result.outcome() == null || result.evidenceHash() == null
                || result.evidenceHash().length() != 64) throw new IllegalArgumentException("QueryDr evidence is invalid");
        providerOutcome = result.outcome(); providerResponseId = result.responseId();
        providerResponseCode = result.responseCode(); providerTransactionStatus = result.transactionStatus();
        providerTransactionNo = result.providerTransactionNo(); providerPaidAt = result.providerPaidAt();
        providerEvidenceHash = result.evidenceHash(); status = Status.EVIDENCE_RECORDED;
    }

    void complete(String previous, String current, boolean applied, Instant now) {
        require(Status.EVIDENCE_RECORDED);
        previousAttemptStatus = previous; currentAttemptStatus = current;
        localTransitionApplied = applied; status = Status.COMPLETED; resolvedAt = now;
    }

    void providerUnavailable(Instant now) { require(Status.QUERYING); status = Status.PROVIDER_UNAVAILABLE; resolvedAt = now; }
    void mismatch(Instant now) { require(Status.QUERYING); status = Status.MISMATCH; resolvedAt = now; }
    private void require(Status expected) { if (status != expected) throw new IllegalStateException("Payment reconciliation command is " + status); }

    PaymentQueryProvider.Result result() {
        if (providerOutcome == null) throw new IllegalStateException("Payment reconciliation has no verified evidence");
        return new PaymentQueryProvider.Result(providerOutcome, providerResponseId, providerResponseCode,
                providerTransactionStatus, providerTransactionNo, providerPaidAt, providerEvidenceHash);
    }
    boolean target(UUID attemptId) { return paymentAttemptPublicId.equals(attemptId); }
    boolean querying() { return status == Status.QUERYING; }
    boolean providerUnavailable() { return status == Status.PROVIDER_UNAVAILABLE; }
    boolean evidenceRecorded() { return status == Status.EVIDENCE_RECORDED; }
    boolean mismatch() { return status == Status.MISMATCH; }
    boolean completed() { return status == Status.COMPLETED; }
    UUID publicId() { return publicId; }
    UUID paymentAttemptPublicId() { return paymentAttemptPublicId; }
    UUID actorAccountPublicId() { return actorAccountPublicId; }
    String idempotencyKey() { return idempotencyKey; }
    String providerRequestId() { return providerRequestId; }
    String previousAttemptStatus() { return previousAttemptStatus; }
    String currentAttemptStatus() { return currentAttemptStatus; }
    boolean localTransitionApplied() { return Boolean.TRUE.equals(localTransitionApplied); }
}
