package com.shoecommerce.branch;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "org_branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Branch() {
    }

    static Branch create(String code, String name, Instant now) {
        Branch branch = new Branch();
        branch.publicId = UUID.randomUUID();
        branch.code = code.trim().toUpperCase(Locale.ROOT);
        branch.name = name.trim();
        branch.enabled = true;
        branch.createdAt = now;
        return branch;
    }

    public Long id() {
        return id;
    }

    public UUID publicId() {
        return publicId;
    }

    public boolean enabled() {
        return enabled;
    }

    public String code() { return code; }
    public String name() { return name; }
}
