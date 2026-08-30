package com.shoecommerce.identity;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthorityStore {

    private final JdbcTemplate jdbcTemplate;

    public AuthorityStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Set<String> roleCodes(long accountId) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT roles.code
                FROM iam_account_role account_roles
                JOIN iam_role_bundle roles ON roles.id = account_roles.role_id
                WHERE account_roles.account_id = ?
                """, String.class, accountId));
    }

    Set<String> permissionCodes(long accountId) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT permissions.code
                FROM iam_account_role account_roles
                JOIN iam_role_permission role_permissions ON role_permissions.role_id = account_roles.role_id
                JOIN iam_permission permissions ON permissions.id = role_permissions.permission_id
                WHERE account_roles.account_id = ?
                UNION
                SELECT permissions.code
                FROM iam_account_permission account_permissions
                JOIN iam_permission permissions ON permissions.id = account_permissions.permission_id
                WHERE account_permissions.account_id = ?
                """, String.class, accountId, accountId));
    }

    boolean hasPermission(long accountId, PermissionCode permission) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT permissions.code
                    FROM iam_account_role account_roles
                    JOIN iam_role_permission role_permissions ON role_permissions.role_id = account_roles.role_id
                    JOIN iam_permission permissions ON permissions.id = role_permissions.permission_id
                    WHERE account_roles.account_id = ?
                    UNION
                    SELECT permissions.code
                    FROM iam_account_permission account_permissions
                    JOIN iam_permission permissions ON permissions.id = account_permissions.permission_id
                    WHERE account_permissions.account_id = ?
                ) granted
                WHERE granted.code = ?
                """, Integer.class, accountId, accountId, permission.name());
        return count != null && count > 0;
    }

    void addRole(long accountId, RoleCode role) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO iam_account_role(account_id, role_id)
                SELECT ?, id FROM iam_role_bundle WHERE code = ?
                """, accountId, role.name());
        if (inserted != 1) {
            throw new IllegalArgumentException("Unknown role bundle");
        }
    }

    boolean setDirectPermission(long accountId, PermissionCode permission, boolean granted, Instant now) {
        if (granted) {
            if (hasDirectPermission(accountId, permission)) {
                return false;
            }
            jdbcTemplate.update("""
                    INSERT INTO iam_account_permission(account_id, permission_id, granted_at)
                    SELECT ?, id, ? FROM iam_permission WHERE code = ?
                    """, accountId, Timestamp.from(now), permission.name());
            return true;
        }
        return jdbcTemplate.update("""
                DELETE account_permissions
                FROM iam_account_permission account_permissions
                JOIN iam_permission permissions ON permissions.id = account_permissions.permission_id
                WHERE account_permissions.account_id = ? AND permissions.code = ?
                """, accountId, permission.name()) == 1;
    }

    public boolean hasStaffRole(long accountId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam_account_role account_roles
                JOIN iam_role_bundle roles ON roles.id = account_roles.role_id
                WHERE account_roles.account_id = ?
                  AND roles.code IN ('CASHIER', 'OPERATIONS', 'ADMINISTRATOR')
                """, Integer.class, accountId);
        return count != null && count > 0;
    }

    private boolean hasDirectPermission(long accountId, PermissionCode permission) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam_account_permission account_permissions
                JOIN iam_permission permissions ON permissions.id = account_permissions.permission_id
                WHERE account_permissions.account_id = ? AND permissions.code = ?
                """, Integer.class, accountId, permission.name());
        return count != null && count > 0;
    }
}
