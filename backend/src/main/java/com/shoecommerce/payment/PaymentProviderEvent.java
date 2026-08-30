package com.shoecommerce.payment;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_provider_event", uniqueConstraints = @UniqueConstraint(name = "UQ_payment_provider_event_scope", columnNames = {"provider_account_public_id", "provider_event_id"}))
public class PaymentProviderEvent {
    public enum Outcome { SUCCESS, FAILURE }
    enum Disposition { APPLIED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "provider_account_public_id", nullable = false) private UUID providerAccountPublicId;
    @Nationalized @Column(name = "provider_event_id", nullable = false, length = 128) private String providerEventId;
    @Column(name = "payment_attempt_public_id", nullable = false) private UUID paymentAttemptPublicId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Outcome outcome;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Disposition disposition;
    @Column(name = "attempt_status", nullable = false, length = 16) private String attemptStatus;
    @Column(name = "order_status", nullable = false, length = 24) private String orderStatus;
    @Column(name = "rejection_reason", length = 32) private String rejectionReason;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "applied_at") private Instant appliedAt;

    protected PaymentProviderEvent() { }

    static PaymentProviderEvent applied(UUID providerId, String eventId, UUID attemptId, Outcome outcome,
            String attemptStatus, String orderStatus, Instant now) {
        return create(providerId, eventId, attemptId, outcome, Disposition.APPLIED, attemptStatus, orderStatus, null, now);
    }

    static PaymentProviderEvent rejected(UUID providerId, String eventId, UUID attemptId, Outcome outcome,
            String attemptStatus, String orderStatus, String reason, Instant now) {
        return create(providerId, eventId, attemptId, outcome, Disposition.REJECTED, attemptStatus, orderStatus, reason, now);
    }

    private static PaymentProviderEvent create(UUID providerId, String eventId, UUID attemptId, Outcome outcome,
            Disposition disposition, String attemptStatus, String orderStatus, String reason, Instant now) {
        PaymentProviderEvent event = new PaymentProviderEvent();
        event.publicId = UUID.randomUUID();
        event.providerAccountPublicId = providerId;
        event.providerEventId = eventId;
        event.paymentAttemptPublicId = attemptId;
        event.outcome = outcome;
        event.disposition = disposition;
        event.attemptStatus = attemptStatus;
        event.orderStatus = orderStatus;
        event.rejectionReason = reason;
        event.receivedAt = now;
        event.appliedAt = disposition == Disposition.APPLIED ? now : null;
        return event;
    }

    boolean matches(UUID attemptId, Outcome candidateOutcome) { return paymentAttemptPublicId.equals(attemptId) && outcome == candidateOutcome; }
    boolean applied() { return disposition == Disposition.APPLIED; }
    String providerEventId() { return providerEventId; }
    UUID paymentAttemptPublicId() { return paymentAttemptPublicId; }
    String attemptStatus() { return attemptStatus; }
    String orderStatus() { return orderStatus; }
    String rejectionReason() { return rejectionReason; }
    Instant appliedAt() { return appliedAt; }
}
