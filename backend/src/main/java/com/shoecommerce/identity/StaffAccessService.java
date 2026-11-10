package com.shoecommerce.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.ScopeStore;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class StaffAccessService {
    private static final Set<PermissionCode> ALLOWLIST = Set.of(PermissionCode.POS_SELL, PermissionCode.FULFILL_ORDER);

    private final JdbcTemplate jdbc;
    private final UserAccountRepository accounts;
    private final AuthorityStore authorities;
    private final ScopeStore scopes;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    StaffAccessService(JdbcTemplate jdbc, UserAccountRepository accounts, AuthorityStore authorities,
            ScopeStore scopes, AuthorizationPolicy authorization, AuditWriter audit, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.authorities = authorities;
        this.scopes = scopes;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CapabilityView> allowedCapabilities(SessionPrincipal actor) {
        requireActor(actor);
        return ALLOWLIST.stream().filter(permission -> authorities.hasPermission(actor.accountId(), permission))
                .sorted().map(permission -> new CapabilityView(permission.name())).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationView> manageableLocations(SessionPrincipal actor) {
        requireActor(actor);
        return jdbc.query("""
                SELECT branches.public_id branch_id, branches.code branch_code, branches.name branch_name,
                       locations.public_id location_id, locations.code location_code, locations.name location_name
                FROM iam_staff_assignment assignments
                JOIN org_branch branches ON branches.id = assignments.branch_id AND branches.enabled = 1
                JOIN org_location locations ON locations.id = assignments.location_id
                    AND locations.branch_id = assignments.branch_id AND locations.enabled = 1
                WHERE assignments.account_id = ? AND assignments.active = 1
                ORDER BY branches.code, locations.code
                """, (rs, row) -> new LocationView(rs.getObject("branch_id", UUID.class), rs.getString("branch_code"),
                        rs.getString("branch_name"), rs.getObject("location_id", UUID.class),
                        rs.getString("location_code"), rs.getString("location_name")), actor.accountId());
    }

    @Transactional(readOnly = true)
    public List<StaffAccessRecord> manageableStaff(SessionPrincipal actor) {
        requireActor(actor);
        return jdbc.queryForList("""
                SELECT DISTINCT accounts.id
                FROM iam_user_account accounts
                JOIN iam_account_role account_roles ON account_roles.account_id = accounts.id
                JOIN iam_role_bundle roles ON roles.id = account_roles.role_id AND roles.code IN ('CASHIER', 'OPERATIONS')
                JOIN iam_staff_assignment target_scope ON target_scope.account_id = accounts.id AND target_scope.active = 1
                JOIN iam_staff_assignment actor_scope ON actor_scope.location_id = target_scope.location_id
                    AND actor_scope.account_id = ? AND actor_scope.active = 1
                WHERE accounts.id <> ?
                  AND NOT EXISTS (
                    SELECT 1 FROM iam_account_role blocked_roles
                    JOIN iam_role_bundle blocked ON blocked.id = blocked_roles.role_id
                    WHERE blocked_roles.account_id = accounts.id
                      AND blocked.code IN ('CUSTOMER', 'PROVIDER', 'ADMINISTRATOR'))
                ORDER BY accounts.id
                """, Long.class, actor.accountId(), actor.accountId()).stream().map(this::record).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffAccessRecord> allStaff(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.IDENTITY_MANAGE);
        return jdbc.queryForList("""
                SELECT DISTINCT accounts.id FROM iam_user_account accounts
                JOIN iam_account_role account_roles ON account_roles.account_id = accounts.id
                JOIN iam_role_bundle roles ON roles.id = account_roles.role_id
                WHERE roles.code IN ('CASHIER', 'OPERATIONS') ORDER BY accounts.id
                """, Long.class).stream().map(this::record).toList();
    }

    @Transactional(readOnly = true)
    public StaffAccessRecord manageableStaff(SessionPrincipal actor, UUID accountId) {
        requireActor(actor);
        long targetId = target(actor, accountId).id();
        return record(targetId);
    }

    @Transactional
    public boolean setCapability(SessionPrincipal actor, UUID accountId, PermissionCode permission, boolean granted) {
        requireActor(actor);
        if (!ALLOWLIST.contains(permission) || !authorities.hasPermission(actor.accountId(), permission)) {
            throw new AccessDeniedException("Capability cannot be delegated");
        }
        UserAccount target = target(actor, accountId);
        if (granted && authorities.inheritedPermissionCodes(target.id()).contains(permission.name())) return false;
        Instant now = clock.instant();
        if (!authorities.setDirectPermission(target.id(), permission, granted, now)) return false;
        target.invalidateAuthority(now);
        audit.append(actor, granted ? "STAFF_CAPABILITY_GRANTED" : "STAFF_CAPABILITY_REVOKED",
                "USER_ACCOUNT", target.publicId(), null, null, Map.of("permission", permission.name()));
        return true;
    }

    @Transactional
    public boolean setAssignment(SessionPrincipal actor, UUID accountId, UUID branchId, UUID locationId, boolean active) {
        requireActor(actor);
        if (!scopes.hasLocationAccess(actor.accountId(), locationId)) throw new AccessDeniedException("Location access denied");
        UserAccount target = staffTarget(accountId);
        if (target.id() == actor.accountId()) throw new AccessDeniedException("Self privilege changes are forbidden");
        List<long[]> matches = jdbc.query("""
                SELECT branches.id branch_id, locations.id location_id
                FROM org_branch branches JOIN org_location locations ON locations.branch_id = branches.id
                WHERE branches.public_id = ? AND locations.public_id = ? AND branches.enabled = 1 AND locations.enabled = 1
                """, (rs, row) -> new long[] { rs.getLong("branch_id"), rs.getLong("location_id") }, branchId, locationId);
        if (matches.isEmpty()) throw new IllegalArgumentException("Location not found");
        long[] ids = matches.getFirst();
        Instant now = clock.instant();
        if (!scopes.setAssignment(target.id(), ids[0], ids[1], active, now)) return false;
        target.invalidateAuthority(now);
        audit.append(actor, active ? "STAFF_ASSIGNMENT_GRANTED" : "STAFF_ASSIGNMENT_REVOKED",
                "USER_ACCOUNT", target.publicId(), ids[0], ids[1], Map.of("locationPublicId", locationId));
        return true;
    }

    private void requireActor(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.STAFF_MANAGE_SCOPED);
        if (manageableLocationsUnchecked(actor.accountId()) == 0) throw new AccessDeniedException("Active Location assignment required");
    }

    private UserAccount target(SessionPrincipal actor, UUID publicId) {
        UserAccount target = staffTarget(publicId);
        if (target.id() == actor.accountId()) throw new AccessDeniedException("Self privilege changes are forbidden");
        Integer shared = jdbc.queryForObject("""
                SELECT COUNT(*) FROM iam_staff_assignment target_scope
                JOIN iam_staff_assignment actor_scope ON actor_scope.location_id = target_scope.location_id
                WHERE target_scope.account_id = ? AND target_scope.active = 1
                  AND actor_scope.account_id = ? AND actor_scope.active = 1
                """, Integer.class, target.id(), actor.accountId());
        if (shared == null || shared == 0) throw new AccessDeniedException("Target is outside the actor Location scope");
        return target;
    }

    private UserAccount staffTarget(UUID publicId) {
        UserAccount target = accounts.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("STAFF_NOT_FOUND", "Staff account was not found."));
        Set<String> roles = authorities.roleCodes(target.id());
        if (roles.stream().noneMatch(Set.of("CASHIER", "OPERATIONS")::contains)
                || roles.stream().anyMatch(Set.of("CUSTOMER", "PROVIDER", "ADMINISTRATOR")::contains)) {
            throw new AccessDeniedException("Target is not a manageable staff account");
        }
        return target;
    }

    private int manageableLocationsUnchecked(long accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM iam_staff_assignment assignments
                JOIN org_location locations ON locations.id = assignments.location_id AND locations.enabled = 1
                JOIN org_branch branches ON branches.id = assignments.branch_id AND branches.enabled = 1
                WHERE assignments.account_id = ? AND assignments.active = 1
                """, Integer.class, accountId);
        return count == null ? 0 : count;
    }

    private StaffAccessRecord record(long accountId) {
        UserAccount account = accounts.findById(accountId).orElseThrow();
        List<LocationView> assignments = jdbc.query("""
                SELECT branches.public_id branch_id, branches.code branch_code, branches.name branch_name,
                       locations.public_id location_id, locations.code location_code, locations.name location_name
                FROM iam_staff_assignment assignments
                JOIN org_branch branches ON branches.id = assignments.branch_id
                JOIN org_location locations ON locations.id = assignments.location_id
                WHERE assignments.account_id = ? AND assignments.active = 1 ORDER BY branches.code, locations.code
                """, (rs, row) -> new LocationView(rs.getObject("branch_id", UUID.class), rs.getString("branch_code"),
                        rs.getString("branch_name"), rs.getObject("location_id", UUID.class),
                        rs.getString("location_code"), rs.getString("location_name")), accountId);
        Set<String> roles = authorities.roleCodes(accountId);
        Set<String> direct = authorities.directPermissionCodes(accountId);
        return new StaffAccessRecord(account.publicId(), account.loginNormalized(), account.status().name(),
                roles.stream().sorted().findFirst().orElse(""), authorities.inheritedPermissionCodes(accountId).stream().sorted().toList(),
                direct.stream().sorted().toList(), assignments);
    }

    public record CapabilityView(String code) { }
    public record LocationView(UUID branchId, String branchCode, String branchName, UUID locationId, String locationCode, String locationName) { }
    public record StaffAccessRecord(UUID accountId, String login, String status, String baseRole,
            List<String> inheritedPermissions, List<String> directCapabilities, List<LocationView> assignments) { }
}
