package com.shoecommerce.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import com.shoecommerce.catalog.ProductEvidence.CutProfile;
import com.shoecommerce.catalog.ProductEvidence.IntendedUse;
import com.shoecommerce.catalog.ProductEvidence.PrimarySoleProfile;
import com.shoecommerce.catalog.ProductEvidence.PrimarySurface;
import com.shoecommerce.catalog.ProductEvidence.UpperConstruction;
import com.shoecommerce.platform.api.BusinessConflictException;

@Entity(name = "Product")
@jakarta.persistence.Table(name = "catalog_product")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 32) private String category;
    @Column(name = "collection", length = 64) private String collection;
    @Column(nullable = false) private boolean featured;
    @Column(name = "new_arrival", nullable = false) private boolean newArrival;
    @Column(name = "campaign_eligible", nullable = false) private boolean campaignEligible = true;
    @Column(name = "merchandising_rank", nullable = false) private int merchandisingRank = 100;
    @Column(name = "hero_image", length = 255) private String heroImage;
    @Column(name = "primary_image", length = 255) private String primaryImage;
    @Enumerated(EnumType.STRING) @Column(name = "intended_use", length = 24) private IntendedUse intendedUse;
    @Enumerated(EnumType.STRING) @Column(name = "primary_surface", length = 24) private PrimarySurface primarySurface;
    @Enumerated(EnumType.STRING) @Column(name = "upper_construction", length = 48) private UpperConstruction upperConstruction;
    @Enumerated(EnumType.STRING) @Column(name = "cut_profile", length = 16) private CutProfile cutProfile;
    @Enumerated(EnumType.STRING) @Column(name = "primary_sole_profile", length = 24) private PrimarySoleProfile primarySoleProfile;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Product() { }
    static Product create(String name, Instant now) {
        return create(name, null, null, null, null, now);
    }
    static Product create(String name, String category, String collection, String heroImage, String primaryImage,
            Instant now) {
        if (now == null) throw new IllegalArgumentException("Product creation time is required");
        Product product = new Product(); product.publicId = UUID.randomUUID(); product.createdAt = now;
        product.applyIdentity(name, category, collection, heroImage, primaryImage); return product;
    }
    public Long id() { return id; }
    public UUID publicId() { return publicId; }
    public String name() { return name; }
    public String category() { return category; }
    public String collection() { return collection; }
    public boolean featured() { return featured; }
    public boolean newArrival() { return newArrival; }
    public boolean campaignEligible() { return campaignEligible; }
    public int merchandisingRank() { return merchandisingRank; }
    public String heroImage() { return heroImage; }
    public String primaryImage() { return primaryImage; }
    public long version() { return version; }
    Identity identity() { return new Identity(name, category, collection, heroImage, primaryImage); }
    Identity updateIdentity(String name, String category, String collection, String heroImage, String primaryImage,
            long expectedEntityVersion) {
        if (version != expectedEntityVersion) {
            throw new BusinessConflictException("CATALOG_PRODUCT_CHANGED",
                    "The Product changed. Reload the latest state and retry.");
        }
        Identity before = identity();
        applyIdentity(name, category, collection, heroImage, primaryImage);
        return before;
    }
    private void applyIdentity(String name, String category, String collection, String heroImage,
            String primaryImage) {
        this.name = required(name, 160, "Product name");
        this.category = optional(category, 32, "Product category");
        this.collection = optional(collection, 64, "Product collection");
        this.heroImage = optional(heroImage, 255, "Product hero image");
        this.primaryImage = optional(primaryImage, 255, "Product primary image");
    }
    private static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > max) throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    public ProductEvidence evidence() {
        return new ProductEvidence(intendedUse, primarySurface, upperConstruction, cutProfile, primarySoleProfile);
    }
    ProductEvidence replaceEvidence(ProductEvidence evidence, long expectedEntityVersion) {
        if (version != expectedEntityVersion) {
            throw new BusinessConflictException("PRODUCT_EVIDENCE_CONFLICT",
                    "The Product evidence changed. Reload the latest state and retry.");
        }
        ProductEvidence before = evidence();
        ProductEvidence replacement = evidence == null ? ProductEvidence.empty() : evidence;
        intendedUse = replacement.intendedUse();
        primarySurface = replacement.primarySurface();
        upperConstruction = replacement.upperConstruction();
        cutProfile = replacement.cutProfile();
        primarySoleProfile = replacement.primarySoleProfile();
        return before;
    }
    record Identity(String name, String category, String collection, String heroImage, String primaryImage) { }
}
