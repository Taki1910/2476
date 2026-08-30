package com.shoecommerce.pos;

import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

@Entity
@Table(name = "pos_cash_sale", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_pos_cash_sale_order", columnNames = "order_id"),
        @UniqueConstraint(name = "UQ_pos_cash_sale_shift_key", columnNames = {"shift_id", "idempotency_key"})
})
public class PosCashSale {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shift_id", nullable = false) private CashierShift shift;
    @Column(name = "cashier_account_id", nullable = false) private long cashierAccountId;
    @Column(name = "variant_public_id", nullable = false) private UUID variantId;
    @Column(name = "idempotency_key", nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PosCashSale() { }

    static PosCashSale create(CustomerOrder order, CashierShift shift, UUID variantId, String key, Instant now) {
        PosCashSale sale = new PosCashSale();
        sale.publicId = UUID.randomUUID();
        sale.order = order;
        sale.shift = shift;
        sale.cashierAccountId = shift.cashierAccountId();
        sale.variantId = variantId;
        sale.idempotencyKey = key;
        sale.createdAt = now;
        return sale;
    }

    UUID publicId() { return publicId; }
    CustomerOrder order() { return order; }
    CashierShift shift() { return shift; }
    UUID variantId() { return variantId; }
    Instant createdAt() { return createdAt; }
}
