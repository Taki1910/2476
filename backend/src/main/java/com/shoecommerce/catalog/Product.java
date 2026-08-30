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
}
