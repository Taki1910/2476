package com.shoecommerce.catalog;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "catalog_product_variant")
public class ProductVariant {
    public enum Status { DRAFT, PUBLISHED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(nullable = false, length = 32) private String size;
    @Column(nullable = false, length = 64) private String color;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false) private Status status;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ProductVariant() { }
    static ProductVariant create(Product product, String sku, String size, String color, Instant now) {
        if (product == null || sku == null || !sku.trim().matches("[A-Za-z0-9_-]{2,64}") || size == null || size.isBlank() || size.trim().length() > 32 || color == null || color.isBlank() || color.trim().length() > 64) throw new IllegalArgumentException("Variant identity is invalid");
        ProductVariant variant = new ProductVariant(); variant.publicId = UUID.randomUUID(); variant.product = product; variant.sku = sku.trim().toUpperCase(Locale.ROOT); variant.size = size.trim(); variant.color = color.trim(); variant.status = Status.DRAFT; variant.createdAt = now; return variant;
    }
    void publish() { if (status != Status.DRAFT) throw new IllegalStateException("Variant is not publishable"); status = Status.PUBLISHED; }
    public Long id() { return id; } public UUID publicId() { return publicId; } public Product product() { return product; } public String sku() { return sku; } public String size() { return size; } public String color() { return color; } public boolean published() { return status == Status.PUBLISHED; }
}
