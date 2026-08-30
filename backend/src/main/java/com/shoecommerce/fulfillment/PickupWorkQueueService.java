package com.shoecommerce.fulfillment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class PickupWorkQueueService {
    private static final String SELECT = """
            SELECT orders.public_id order_id, fulfillments.public_id fulfillment_id,
                   branches.public_id branch_id, branches.code branch_code, branches.name branch_name,
                   locations.public_id location_id, locations.code location_code, locations.name location_name,
                   items.sku_snapshot, items.size_snapshot, items.quantity,
                   orders.status order_status, COALESCE(fulfillments.status, 'NOT_CREATED') fulfillment_status,
                   fulfillments.created_at, fulfillments.prepared_at, fulfillments.handed_over_at,
                   fulfillments.cancelled_at, voids.status void_status
            FROM commerce_order orders
            JOIN commerce_order_item items ON items.order_id = orders.id
            JOIN org_location locations ON locations.public_id = items.location_public_id
            JOIN org_branch branches ON branches.public_id = orders.responsible_branch_public_id
            JOIN iam_staff_assignment assignments ON assignments.location_id = locations.id
                AND assignments.branch_id = branches.id AND assignments.account_id = ? AND assignments.active = 1
            LEFT JOIN pickup_fulfillment fulfillments ON fulfillments.order_id = orders.id
            LEFT JOIN payment_void_operation voids ON voids.order_public_id = orders.public_id
            WHERE orders.status IN ('PAID', 'CANCELLED')
            """;

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;

    PickupWorkQueueService(JdbcTemplate jdbc, AuthorizationPolicy authorization) {
        this.jdbc = jdbc; this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<PickupTask> queue(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_PICKUP);
        return jdbc.query(SELECT + " ORDER BY CASE COALESCE(fulfillments.status, 'NOT_CREATED') WHEN 'PREPARED' THEN 0 WHEN 'PICKING' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'NOT_CREATED' THEN 3 ELSE 4 END, orders.created_at",
                PickupWorkQueueService::map, actor.accountId());
    }

    @Transactional(readOnly = true)
    public PickupTask detail(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_PICKUP);
        return jdbc.query(SELECT + " AND orders.public_id = ?", PickupWorkQueueService::map,
                actor.accountId(), orderId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PICKUP_NOT_FOUND", "Pickup task was not found in the active Location scope."));
    }

    private static PickupTask map(ResultSet rs, int row) throws SQLException {
        return new PickupTask(rs.getObject("order_id", UUID.class), rs.getObject("fulfillment_id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getString("branch_code"), rs.getString("branch_name"),
                rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getString("location_name"),
                rs.getString("sku_snapshot"), rs.getString("size_snapshot"), rs.getLong("quantity"),
                rs.getString("order_status"), rs.getString("fulfillment_status"), instant(rs, "created_at"),
                instant(rs, "prepared_at"), instant(rs, "handed_over_at"), instant(rs, "cancelled_at"),
                rs.getString("void_status"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column); return timestamp == null ? null : timestamp.toInstant();
    }

    public record PickupTask(UUID orderId, UUID fulfillmentId, UUID branchId, String branchCode, String branchName,
            UUID locationId, String locationCode, String locationName, String sku, String size, long quantity,
            String orderStatus, String fulfillmentStatus, Instant createdAt, Instant preparedAt,
            Instant handedOverAt, Instant cancelledAt, String financialVoidStatus) { }
}
