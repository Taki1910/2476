package com.shoecommerce.pos;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.shoecommerce.branch.Location;

import jakarta.persistence.*;

@Entity
@Table(name = "pos_register")
public class PosRegister {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(nullable = false, unique = true, length = 32) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id", nullable = false) private Location location;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PosRegister() { }

    public static PosRegister create(String code, Location location, Instant now) {
        if (code == null || !code.trim().matches("[A-Za-z0-9_-]{2,32}") || location == null || now == null) {
            throw new IllegalArgumentException("Register identity is invalid");
        }
        PosRegister register = new PosRegister();
        register.publicId = UUID.randomUUID();
        register.code = code.trim().toUpperCase(Locale.ROOT);
        register.location = location;
        register.enabled = true;
        register.createdAt = now;
        return register;
    }

    Long id() { return id; }
    UUID publicId() { return publicId; }
    String code() { return code; }
    Location location() { return location; }
    boolean enabled() { return enabled; }
}
