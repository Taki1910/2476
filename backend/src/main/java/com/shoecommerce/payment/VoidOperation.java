package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_void_operation", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_payment_void_operation_payment", columnNames = "payment_id"),
        @UniqueConstraint(name = "UQ_payment_void_operation_order", columnNames = "order_public_id"),
        @UniqueConstraint(name = "UQ_payment_void_operation_actor_key", columnNames = {"actor_account_public_id", "idempotency_key"})
})
public class VoidOperation {
    enum Status { PROCESSING, SUCCEEDED, FAILED_RETRYABLE, UNKNOWN, REVIEW_REQUIRED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(name = "order_public_id", nullable = false, unique = true) private UUID orderPublicId;
    @Column(name = "actor_account_public_id", nullable = false) private UUID actorAccountPublicId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 0) private BigDecimal requestedAmount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected VoidOperation() { }

    static VoidOperation create(Payment payment, CustomerOrder.PaymentFacts order, UUID actorId,
            String key, Instant now) {
        if (payment == null || order == null || actorId == null || key == null || key.isBlank()
                || order.totalAmount() <= 0 || !"VND".equals(order.currency())) {
            throw new IllegalArgumentException("Void operation is invalid");
        }
        VoidOperation operation = new VoidOperation();
        operation.publicId = UUID.randomUUID();
        operation.payment = payment;
        operation.orderPublicId = order.orderId();
        operation.actorAccountPublicId = actorId;
        operation.idempotencyKey = key;
        operation.requestedAmount = BigDecimal.valueOf(order.totalAmount());
        operation.currency = order.currency();
        operation.status = Status.PROCESSING;
        operation.createdAt = now;
        return operation;
    }

    void retry() {
        if (status != Status.FAILED_RETRYABLE) throw new IllegalStateException("Void operation is not retryable");
        status = Status.PROCESSING;
        resolvedAt = null;
    }
    void succeed(Instant now) { requireProcessing(); status = Status.SUCCEEDED; resolvedAt = now; }
    void fail(Instant now) { requireProcessing(); status = Status.FAILED_RETRYABLE; resolvedAt = now; }
    void unknown(Instant now) { requireProcessing(); status = Status.UNKNOWN; resolvedAt = now; }
    void requireReview(Instant now) { requireProcessing(); status = Status.REVIEW_REQUIRED; resolvedAt = now; }
    private void requireProcessing() { if (status != Status.PROCESSING) throw new IllegalStateException("Void operation is not processing"); }

    boolean matches(UUID actorId, String key, UUID orderId) { return actorAccountPublicId.equals(actorId) && idempotencyKey.equals(key) && orderPublicId.equals(orderId); }
    boolean processing() { return status == Status.PROCESSING; }
    boolean failedRetryable() { return status == Status.FAILED_RETRYABLE; }
    UUID publicId() { return publicId; }
    UUID orderPublicId() { return orderPublicId; }
    UUID actorAccountPublicId() { return actorAccountPublicId; }
    String idempotencyKey() { return idempotencyKey; }
    Payment payment() { return payment; }
    BigDecimal requestedAmount() { return requestedAmount; }
    String currency() { return currency; }
    String status() { return status.name(); }
    Instant createdAt() { return createdAt; }
    Instant resolvedAt() { return resolvedAt; }
}
