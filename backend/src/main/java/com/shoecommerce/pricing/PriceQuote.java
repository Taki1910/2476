package com.shoecommerce.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pricing_price_quote")
public class PriceQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "owner_account_id", nullable = false) private long ownerAccountId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "price_version_id") private VariantPrice priceVersion;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Column(nullable = false, length = 3, columnDefinition = "char(3)") private String currency;
    @Column(name = "quoted_at", nullable = false) private Instant quotedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected PriceQuote() { }

    static PriceQuote create(long ownerAccountId, VariantPrice priceVersion, Instant quotedAt, Instant expiresAt) {
        PriceQuote quote = new PriceQuote();
        quote.publicId = UUID.randomUUID();
        quote.ownerAccountId = ownerAccountId;
        quote.priceVersion = priceVersion;
        quote.amount = BigDecimal.valueOf(priceVersion.amount());
        quote.currency = "VND";
        quote.quotedAt = quotedAt;
        quote.expiresAt = expiresAt;
        return quote;
    }

    UUID publicId() { return publicId; }
    long ownerAccountId() { return ownerAccountId; }
    VariantPrice priceVersion() { return priceVersion; }
    long amount() { return amount.longValueExact(); }
    String currency() { return currency; }
    Instant quotedAt() { return quotedAt; }
    Instant expiresAt() { return expiresAt; }
}
