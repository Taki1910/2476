package com.shoecommerce.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

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
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Product() { }
    static Product create(String name, Instant now) {
        if (name == null || name.isBlank() || name.trim().length() > 160) throw new IllegalArgumentException("Product name is invalid");
        Product product = new Product(); product.publicId = UUID.randomUUID(); product.name = name.trim(); product.createdAt = now; return product;
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
}
