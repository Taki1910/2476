package com.shoecommerce.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.shoecommerce.platform.api.BusinessConflictException;

import jakarta.persistence.*;

@Entity
@Table(name = "catalog_product_variant")
public class ProductVariant {
    private static final BigDecimal MAX_EU_SIZE = new BigDecimal("999.99");
    public enum Status { DRAFT, PUBLISHED, RETIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(length = 128) private String barcode;
    @Column(nullable = false, length = 32) private String size;
    @Column(nullable = false, length = 64) private String color;
    @Column(name = "normalized_color", insertable = false, updatable = false, length = 64) private String normalizedColor;
    @Column(name = "normalized_size", insertable = false, updatable = false, precision = 5, scale = 2) private BigDecimal normalizedSize;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false) private Status status;
    @Column(name = "ever_published", nullable = false) private boolean everPublished;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ProductVariant() { }
    static ProductVariant create(Product product, String sku, String size, String color, Instant now) {
        return create(product, sku, size, color, null, now);
    }
    static ProductVariant create(Product product, String sku, String size, String color, String barcode, Instant now) {
        if (product == null || sku == null || !sku.trim().matches("[A-Za-z0-9_-]{2,64}") || now == null) {
            throw new IllegalArgumentException("Variant identity is invalid");
        }
        String displaySize = size == null ? "" : size.trim();
        String displayColor = color == null ? "" : color.trim();
        normalizeSize(displaySize);
        normalizeColor(displayColor);
        ProductVariant variant = new ProductVariant(); variant.publicId = UUID.randomUUID(); variant.product = product;
        variant.sku = sku.trim().toUpperCase(Locale.ROOT); variant.barcode = normalizeBarcode(barcode);
        variant.size = displaySize; variant.color = displayColor;
        variant.status = Status.DRAFT; variant.createdAt = now; return variant;
    }
    static String normalizeBarcode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 128) throw new IllegalArgumentException("Barcode is invalid");
        return normalized.isEmpty() ? null : normalized;
    }
    static BigDecimal normalizeSize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 32
                || !normalized.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) {
            throw new IllegalArgumentException("EU size is invalid");
        }
        try {
            BigDecimal size = new BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY);
            if (size.signum() <= 0 || size.compareTo(MAX_EU_SIZE) > 0) throw new IllegalArgumentException("EU size is invalid");
            return size;
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException("EU size is invalid", invalid);
        }
    }
    static String normalizeColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) throw new IllegalArgumentException("Color is invalid");
        return normalized.toUpperCase(Locale.ROOT);
    }
    void publish(long expectedVersion) {
        requirePublishable(expectedVersion);
        status = Status.PUBLISHED;
        everPublished = true;
    }
    void requirePublishable(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.DRAFT) throw new IllegalStateException("Variant is not publishable");
    }
    void retire(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status == Status.RETIRED) throw new IllegalStateException("Variant is already retired");
        status = Status.RETIRED;
    }
    void restore(long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.RETIRED) throw new IllegalStateException("Variant is not restorable");
        status = Status.DRAFT;
    }
    void updateBarcode(String value, long expectedVersion) {
        requireVersion(expectedVersion);
        if (status != Status.DRAFT || everPublished) {
            throw new BusinessConflictException("CATALOG_BARCODE_IMMUTABLE",
                    "Barcode is immutable after a Variant has been published.");
        }
        barcode = normalizeBarcode(value);
    }
    private void requireVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new BusinessConflictException("CATALOG_VARIANT_CHANGED",
                    "The Variant changed. Reload the latest state and retry.");
        }
    }
    public Long id() { return id; } public UUID publicId() { return publicId; } public Product product() { return product; }
    public String sku() { return sku; } public String barcode() { return barcode; }
    public String size() { return size; } public String color() { return color; }
    public String normalizedColor() { return normalizedColor == null ? normalizeColor(color) : normalizedColor; }
    public BigDecimal normalizedSize() { return normalizedSize == null ? normalizeSize(size) : normalizedSize; }
    public String status() { return status.name(); } public long version() { return version; }
    public boolean published() { return status == Status.PUBLISHED; }
    public boolean everPublished() { return everPublished; }
}
