package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_void_allocation", uniqueConstraints = @UniqueConstraint(
        name = "UQ_payment_void_allocation_component", columnNames = {"void_attempt_id", "component_type", "component_public_id"}))
public class VoidAllocation {
    enum Status { ACTIVE, SUCCEEDED, RELEASED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "void_operation_id", nullable = false) private VoidOperation operation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "void_attempt_id", nullable = false) private VoidAttempt attempt;
    @Column(name = "component_type", nullable = false, length = 24) private String componentType;
    @Column(name = "component_public_id", nullable = false) private UUID componentPublicId;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected VoidAllocation() { }

    static VoidAllocation create(VoidOperation operation, VoidAttempt attempt, UUID componentId,
            BigDecimal amount, Instant now) {
        if (operation == null || attempt == null || componentId == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Void allocation is invalid");
        }
        VoidAllocation allocation = new VoidAllocation();
        allocation.publicId = UUID.randomUUID();
        allocation.operation = operation;
        allocation.attempt = attempt;
        allocation.componentType = "ORDER_ITEM";
        allocation.componentPublicId = componentId;
        allocation.amount = amount;
        allocation.status = Status.ACTIVE;
        allocation.createdAt = now;
        return allocation;
    }

    void succeed(Instant now) { if (status != Status.ACTIVE) throw new IllegalStateException("Void allocation is not active"); status = Status.SUCCEEDED; resolvedAt = now; }
    void release(Instant now) { if (status != Status.ACTIVE) throw new IllegalStateException("Void allocation is not active"); status = Status.RELEASED; resolvedAt = now; }
    BigDecimal amount() { return amount; }
    UUID componentPublicId() { return componentPublicId; }
    String status() { return status.name(); }
}
