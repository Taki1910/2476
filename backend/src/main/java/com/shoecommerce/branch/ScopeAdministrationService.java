package com.shoecommerce.branch;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorityStore;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccount;
import com.shoecommerce.identity.UserAccountRepository;

@Service
public class ScopeAdministrationService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_-]{2,32}");

    private final BranchRepository branches;
    private final LocationRepository locations;
    private final UserAccountRepository accounts;
    private final AuthorityStore authorities;
    private final ScopeStore scopes;
    private final AuthorizationPolicy authorization;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public ScopeAdministrationService(
            BranchRepository branches,
            LocationRepository locations,
            UserAccountRepository accounts,
            AuthorityStore authorities,
            ScopeStore scopes,
            AuthorizationPolicy authorization,
            AuditWriter auditWriter,
            Clock clock) {
        this.branches = branches;
        this.locations = locations;
        this.accounts = accounts;
        this.authorities = authorities;
        this.scopes = scopes;
        this.authorization = authorization;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public UUID createBranch(SessionPrincipal actor, String code, String name) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        validateCodeAndName(code, name);
        Branch branch = branches.save(Branch.create(code, name, clock.instant()));
        auditWriter.append(actor, "BRANCH_CREATED", "BRANCH", branch.publicId(), branch.id(), null,
                Map.of("code", code.trim().toUpperCase(Locale.ROOT)));
        return branch.publicId();
    }

    @Transactional
    public UUID createLocation(
            SessionPrincipal actor,
            UUID branchPublicId,
            String code,
            String name) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        validateCodeAndName(code, name);
        Branch branch = branches.findByPublicId(branchPublicId)
                .filter(Branch::enabled)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found or disabled"));
        Location location = locations.save(Location.create(branch, code, name, clock.instant()));
        auditWriter.append(actor, "LOCATION_CREATED", "LOCATION", location.publicId(),
                branch.id(), location.id(), Map.of("code", code.trim().toUpperCase(Locale.ROOT)));
        return location.publicId();
    }

    @Transactional
    public boolean setAssignment(
            SessionPrincipal actor,
            UUID accountPublicId,
            UUID branchPublicId,
            UUID locationPublicId,
            boolean active) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        UserAccount account = accounts.findByPublicIdForUpdate(accountPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!authorities.hasStaffRole(account.id())) {
            throw new IllegalArgumentException("Only staff accounts may receive assignments");
        }
        Branch branch = branches.findByPublicId(branchPublicId)
                .filter(Branch::enabled)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found or disabled"));
        Location location = locationPublicId == null ? null : locations.findByPublicId(locationPublicId)
                .filter(Location::enabled)
                .filter(candidate -> candidate.branchId().equals(branch.id()))
                .orElseThrow(() -> new IllegalArgumentException("Location is not active in the branch"));
        Instant now = clock.instant();
        if (!scopes.setAssignment(
                account.id(), branch.id(), location == null ? null : location.id(), active, now)) {
            return false;
        }
        account.invalidateAuthority(now);
        // Assignment, authVersion, and audit share the transaction to prevent stale cross-scope access.
        auditWriter.append(actor, active ? "ASSIGNMENT_GRANTED" : "ASSIGNMENT_REVOKED",
                "USER_ACCOUNT", account.publicId(), branch.id(), location == null ? null : location.id(),
                location == null
                        ? Map.of("scope", "BRANCH")
                        : Map.of("scope", "LOCATION", "locationPublicId", location.publicId()));
        return true;
    }

    private static void validateCodeAndName(String code, String name) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalizedCode).matches()
                || name == null
                || name.isBlank()
                || name.length() > 120) {
            throw new IllegalArgumentException("Scope code or name is invalid");
        }
    }
}
