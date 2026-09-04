package com.shoecommerce.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.*;

@Entity
@Table(name = "pricing_cart_quote")
public class CartQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "public_id", nullable = false, unique = true) UUID publicId;
    @Column(name = "owner_account_id", nullable = false) long ownerAccountId;
    @Column(name = "quoted_at", nullable = false) Instant quotedAt;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL) @OrderBy("id ASC")
    List<CartQuoteLine> items = new ArrayList<>();
    protected CartQuote() { }
}

@Entity
@Table(name = "pricing_cart_quote_line")
class CartQuoteLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cart_quote_id") CartQuote quote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "price_version_id") VariantPrice priceVersion;
    @Column(name = "variant_public_id", nullable = false) UUID variantId;
    @Column(nullable = false) long quantity;
    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 0) BigDecimal unitPrice;
    @org.hibernate.annotations.Nationalized @Column(name = "product_name", nullable = false, length = 200) String productName;
    @Column(nullable = false, length = 64) String sku;
    @Column(nullable = false, length = 32) String size;
    @Column(nullable = false, length = 64) String color;
    protected CartQuoteLine() { }
}

interface CartQuoteRepository extends JpaRepository<CartQuote, Long> {
    Optional<CartQuote> findByPublicId(UUID publicId);
}
