package com.shoecommerce.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.shoecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

@Entity
@Table(name = "pricing_variant_price")
public class VariantPrice {
    public static final long MAX_AMOUNT = 9_007_199_254_740_991L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(nullable = false, precision = 19, scale = 0) private BigDecimal amount;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected VariantPrice() { }
    public static VariantPrice create(ProductVariant variant, long amount, Instant now) { if (amount <= 0 || amount > MAX_AMOUNT) throw new IllegalArgumentException("Price must be between 1 and " + MAX_AMOUNT); VariantPrice price = new VariantPrice(); price.publicId = UUID.randomUUID(); price.variant = variant; price.amount = BigDecimal.valueOf(amount); price.validFrom = now; price.updatedAt = now; return price; }
    public void close(Instant now) { if (validTo != null || !now.isAfter(validFrom)) throw new IllegalStateException("Price version cannot be closed at this instant"); validTo = now; updatedAt = now; }
    public Long id() { return id; }
    public UUID publicId() { return publicId; }
    public ProductVariant variant() { return variant; }
    public UUID variantPublicId() { return variant.publicId(); }
    public Instant validFrom() { return validFrom; }
    public long amount() { return amount.longValueExact(); }
}
