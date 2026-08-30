package com.shoecommerce.branch;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScopeStore {

    private final JdbcTemplate jdbcTemplate;

    public ScopeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasBranchAccess(long accountId, UUID branchPublicId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam_staff_assignment assignments
                JOIN org_branch branches ON branches.id = assignments.branch_id
                WHERE assignments.account_id = ?
                  AND assignments.active = 1
                  AND branches.enabled = 1
                  AND branches.public_id = ?
                """, Integer.class, accountId, branchPublicId);
        return count != null && count > 0;
    }

    public boolean hasLocationAccess(long accountId, UUID locationPublicId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam_staff_assignment assignments
                JOIN org_branch branches ON branches.id = assignments.branch_id
                JOIN org_location locations
                  ON locations.id = assignments.location_id
                 AND locations.branch_id = assignments.branch_id
                WHERE assignments.account_id = ?
                  AND assignments.active = 1
                  AND branches.enabled = 1
                  AND locations.enabled = 1
                  AND locations.public_id = ?
                """, Integer.class, accountId, locationPublicId);
        return count != null && count > 0;
    }

    boolean setAssignment(
            long accountId,
            long branchId,
            Long locationId,
            boolean active,
            Instant now) {
        List<AssignmentState> existing = jdbcTemplate.query("""
                SELECT id, active
                FROM iam_staff_assignment
                WHERE account_id = ? AND branch_id = ?
                  AND ((? IS NULL AND location_id IS NULL) OR location_id = ?)
                """,
                (resultSet, row) -> new AssignmentState(
                        resultSet.getLong("id"),
                        resultSet.getBoolean("active")),
                accountId, branchId, locationId, locationId);
        if (!existing.isEmpty()) {
            AssignmentState assignment = existing.getFirst();
            if (assignment.active() == active) {
                return false;
            }
            jdbcTemplate.update("""
                    UPDATE iam_staff_assignment SET active = ?, updated_at = ? WHERE id = ?
                    """, active, Timestamp.from(now), assignment.id());
            return true;
        }
        if (!active) {
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO iam_staff_assignment(
                    public_id, account_id, branch_id, location_id, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), accountId, branchId, locationId, active,
                Timestamp.from(now), Timestamp.from(now));
        return true;
    }

    private record AssignmentState(long id, boolean active) {
    }
}
