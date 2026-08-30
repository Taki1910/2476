package com.shoecommerce.payment;

import java.time.Instant;
import java.util.UUID;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.*;

@Entity
@Table(name = "payment")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true) private CustomerOrder order;
    @Column(nullable = false, length = 3) private String currency;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Payment() { }

    static Payment create(CustomerOrder order, String currency, Instant now) {
        if (order == null || !"VND".equals(currency)) throw new IllegalArgumentException("Payment identity is invalid");
        Payment payment = new Payment();
        payment.publicId = UUID.randomUUID();
        payment.order = order;
        payment.currency = currency;
        payment.createdAt = now;
        return payment;
    }

    UUID orderPublicId() { return order.paymentFacts().orderId(); }
    UUID ownerAccountPublicId() { return order.paymentFacts().ownerAccountId(); }
    UUID locationPublicId() { return order.paymentFacts().locationId(); }
    String currency() { return currency; }
}
