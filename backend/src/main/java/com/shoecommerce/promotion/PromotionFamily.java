package com.shoecommerce.promotion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_family")
public class PromotionFamily {
    public enum AcquisitionMode { AUTOMATIC, CODE, CLAIMABLE }

    @Id
    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_mode", nullable = false, length = 16)
    private AcquisitionMode acquisitionMode;

    @Column(name = "normalized_code", length = 32)
    private String normalizedCode;

    @Column(name = "is_publicly_discoverable", nullable = false)
    private boolean publiclyDiscoverable;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromotionFamily() { }
}
