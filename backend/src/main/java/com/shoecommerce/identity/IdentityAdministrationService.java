package com.shoecommerce.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.platform.api.BusinessConflictException;

@Service
public class IdentityAdministrationService {

    private static final Pattern LOGIN_PATTERN = Pattern.compile("[a-z0-9._@+-]{3,254}");

    private final UserAccountRepository accounts;
    private final AuthorityStore authorities;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public IdentityAdministrationService(
            UserAccountRepository accounts,
            AuthorityStore authorities,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder,
            AuditWriter auditWriter,
            Clock clock) {
        this.accounts = accounts;
        this.authorities = authorities;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public UUID createAccount(
            SessionPrincipal actor,
            String login,
            String plaintextPassword,
            RoleCode role) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        String normalizedLogin = UserAccount.normalizeLogin(login);
        validateLogin(normalizedLogin);
        validatePassword(plaintextPassword);
        Instant now = clock.instant();
        UserAccount account = accounts.saveAndFlush(
                UserAccount.create(normalizedLogin, passwordEncoder.encode(plaintextPassword), now));
        authorities.addRole(account.id(), role);
        auditWriter.append(actor, "ACCOUNT_CREATED", "USER_ACCOUNT", account.publicId(), null, null,
                Map.of("role", role.name()));
        return account.publicId();
    }

    @Transactional
    public AuthController.RegisteredAccountResponse registerCustomer(String login, String plaintextPassword) {
        String normalizedLogin = UserAccount.normalizeLogin(login);
        validateLogin(normalizedLogin);
        validatePassword(plaintextPassword);
        try {
            UserAccount account = accounts.saveAndFlush(
                    UserAccount.create(normalizedLogin, passwordEncoder.encode(plaintextPassword), clock.instant()));
            authorities.addRole(account.id(), RoleCode.CUSTOMER);
            auditWriter.appendSystem("CUSTOMER_SELF_REGISTERED", "USER_ACCOUNT", account.publicId(),
                    Map.of("role", RoleCode.CUSTOMER.name()));
            return new AuthController.RegisteredAccountResponse(account.publicId(), account.loginNormalized());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException("IDENTITY_ALREADY_EXISTS", "An account with this login already exists.");
        }
    }

    @Transactional
    public boolean setAccountEnabled(SessionPrincipal actor, UUID accountPublicId, boolean enabled) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        UserAccount account = lockedAccount(accountPublicId);
        if ((account.status() == UserAccount.Status.ENABLED) == enabled) {
            return false;
        }
        account.setEnabled(enabled, clock.instant());
        auditWriter.append(actor, enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED",
                "USER_ACCOUNT", account.publicId(), null, null, Map.of("enabled", enabled));
        return true;
    }

    @Transactional
    public boolean setDirectPermission(
            SessionPrincipal actor,
            UUID accountPublicId,
            PermissionCode permission,
            boolean granted) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        UserAccount account = lockedAccount(accountPublicId);
        Instant now = clock.instant();
        if (!authorities.setDirectPermission(account.id(), permission, granted, now)) {
            return false;
        }
        account.invalidateAuthority(now);
        // Grant, authVersion, and audit must commit together so no session can keep stale authority.
        auditWriter.append(actor, granted ? "PERMISSION_GRANTED" : "PERMISSION_REVOKED",
                "USER_ACCOUNT", account.publicId(), null, null,
                Map.of("permission", permission.name()));
        return true;
    }

    private UserAccount lockedAccount(UUID accountPublicId) {
        return accounts.findByPublicIdForUpdate(accountPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private static void validateLogin(String login) {
        if (!LOGIN_PATTERN.matcher(login).matches()) {
            throw new IllegalArgumentException("Login identifier is invalid");
        }
    }

    private static void validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be 12-72 UTF-8 bytes");
        }
    }
}
