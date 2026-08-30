package com.shoecommerce.pos;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "cashier_shift")
public class CashierShift {
    enum Status { OPEN, CLOSED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "register_id", nullable = false) private PosRegister register;
    @Column(name = "location_id", nullable = false, updatable = false) private Long locationId;
    @Column(name = "cashier_account_id", nullable = false) private long cashierAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Status status;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "closed_at") private Instant closedAt;

    protected CashierShift() { }

    static CashierShift open(PosRegister register, long cashierAccountId, Instant now) {
        CashierShift shift = new CashierShift();
        shift.publicId = UUID.randomUUID();
        shift.register = register;
        shift.locationId = register.location().id();
        shift.cashierAccountId = cashierAccountId;
        shift.status = Status.OPEN;
        shift.openedAt = now;
        return shift;
    }

    boolean close(Instant now) { if (status == Status.CLOSED) return false; status = Status.CLOSED; closedAt = now; return true; }
    boolean open() { return status == Status.OPEN; }
    boolean ownedBy(long accountId) { return cashierAccountId == accountId; }
    Long id() { return id; }
    UUID publicId() { return publicId; }
    PosRegister register() { return register; }
    long cashierAccountId() { return cashierAccountId; }
    String status() { return status.name(); }
    Instant openedAt() { return openedAt; }
    Instant closedAt() { return closedAt; }
}
