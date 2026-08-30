package com.shoecommerce.branch;

import java.time.Instant;
import java.util.Locale;
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
@Table(name = "org_location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Location() {
    }

    static Location create(Branch branch, String code, String name, Instant now) {
        Location location = new Location();
        location.publicId = UUID.randomUUID();
        location.branch = branch;
        location.code = code.trim().toUpperCase(Locale.ROOT);
        location.name = name.trim();
        location.enabled = true;
        location.createdAt = now;
        return location;
    }

    public Long id() {
        return id;
    }

    public UUID publicId() {
        return publicId;
    }

    public Long branchId() {
        return branch.id();
    }

    public UUID branchPublicId() {
        return branch.publicId();
    }

    public boolean enabled() {
        return enabled;
    }

    public String code() { return code; }
    public String name() { return name; }
}
