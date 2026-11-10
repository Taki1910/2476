package com.shoecommerce.promotion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "voucher_claim")
public class VoucherClaim {
    public enum Status { CLAIMED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "promotion_family_public_id", nullable = false)
    private UUID promotionFamilyPublicId;

    @Column(name = "claimed_revision_public_id", nullable = false)
    private UUID claimedRevisionPublicId;

    @Column(name = "customer_account_public_id", nullable = false)
    private UUID customerAccountPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by_account_public_id")
    private UUID revokedByAccountPublicId;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    protected VoucherClaim() { }
}
