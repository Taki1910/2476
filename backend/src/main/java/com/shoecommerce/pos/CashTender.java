package com.shoecommerce.pos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

@Entity
@Table(name = "cash_tender")
public class CashTender {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true) private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shift_id", nullable = false) private CashierShift shift;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "register_id", nullable = false) private PosRegister register;
    @Column(name = "cashier_account_id", nullable = false) private long cashierAccountId;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected CashTender() { }

    static CashTender accept(CustomerOrder order, CashierShift shift, long amount, Instant now) {
        if (amount <= 0) throw new IllegalArgumentException("Cash tender must be positive");
        CashTender tender = new CashTender();
        tender.publicId = UUID.randomUUID();
        tender.order = order;
        tender.shift = shift;
        tender.register = shift.register();
        tender.cashierAccountId = shift.cashierAccountId();
        tender.amount = BigDecimal.valueOf(amount);
        tender.currency = "VND";
        tender.createdAt = now;
        return tender;
    }

    UUID publicId() { return publicId; }
    long amount() { return amount.longValueExact(); }
}
