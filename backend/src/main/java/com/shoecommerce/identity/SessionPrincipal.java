package com.shoecommerce.identity;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class SessionPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final UUID publicId;
    private final String login;
    private final boolean enabled;
    private final long authVersion;
    private final Instant authenticatedAt;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;
    private String passwordHash;

    SessionPrincipal(
            UserAccount account,
            String passwordHash,
            Instant authenticatedAt,
            Set<String> roles,
            Set<String> permissions) {
        this.accountId = account.id();
        this.publicId = account.publicId();
        this.login = account.loginNormalized();
        this.passwordHash = passwordHash;
        this.enabled = account.status() == UserAccount.Status.ENABLED;
        this.authVersion = account.authVersion();
        this.authenticatedAt = authenticatedAt;
        this.roles = Set.copyOf(roles);
        this.permissions = Set.copyOf(permissions);
        this.authorities = permissions.stream()
                .map(code -> new SimpleGrantedAuthority("PERM_" + code))
                .toList();
    }

    public long accountId() {
        return accountId;
    }

    public UUID publicId() {
        return publicId;
    }

    public long authVersion() {
        return authVersion;
    }

    public Instant authenticatedAt() {
        return authenticatedAt;
    }

    public Set<String> roles() {
        return roles;
    }

    public Set<String> permissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
