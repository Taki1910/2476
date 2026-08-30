package com.shoecommerce.identity;

import java.time.Instant;
import java.util.Locale;
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
@Table(name = "iam_user_account")
public class UserAccount {

    public enum Status {
        ENABLED,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "login_normalized", nullable = false, unique = true, length = 254)
    private String loginNormalized;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    static UserAccount create(String login, String passwordHash, Instant now) {
        UserAccount account = new UserAccount();
        account.publicId = UUID.randomUUID();
        account.loginNormalized = normalizeLogin(login);
        account.passwordHash = passwordHash;
        account.status = Status.ENABLED;
        account.authVersion = 1;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public static String normalizeLogin(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    void setEnabled(boolean enabled, Instant now) {
        status = enabled ? Status.ENABLED : Status.DISABLED;
        invalidateAuthority(now);
    }

    public void invalidateAuthority(Instant now) {
        authVersion++;
        updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public UUID publicId() {
        return publicId;
    }

    public String loginNormalized() {
        return loginNormalized;
    }

    String passwordHash() {
        return passwordHash;
    }

    public Status status() {
        return status;
    }

    public long authVersion() {
        return authVersion;
    }
}
