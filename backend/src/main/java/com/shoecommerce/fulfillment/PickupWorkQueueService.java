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
                   items.sku_snapshot, items.size_snapshot, items.color_snapshot, items.quantity,
                   orders.status order_status, COALESCE(fulfillments.status, 'NOT_CREATED') fulfillment_status,
                   COALESCE(fulfillments.fulfillment_type, 'PICKUP') fulfillment_type,
                   fulfillments.created_at, fulfillments.picking_started_at, fulfillments.prepared_at,
                   fulfillments.handed_over_at, fulfillments.dispatched_at, fulfillments.delivered_at,
                   fulfillments.cancelled_at, fulfillments.receiver_name, fulfillments.receiver_phone,
                   fulfillments.delivery_address, fulfillments.delivery_note,
                   fulfillments.delivery_fee_amount, voids.status void_status
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
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        return group(jdbc.query(SELECT + " ORDER BY CASE COALESCE(fulfillments.status, 'NOT_CREATED') WHEN 'PREPARED' THEN 0 WHEN 'PICKING' THEN 1 WHEN 'PENDING' THEN 2 WHEN 'NOT_CREATED' THEN 3 ELSE 4 END, orders.created_at, items.id",
                PickupWorkQueueService::map, actor.accountId()));
    }

    @Transactional(readOnly = true)
    public PickupTask detail(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.FULFILL_ORDER);
        return group(jdbc.query(SELECT + " AND orders.public_id = ? ORDER BY items.id", PickupWorkQueueService::map,
                actor.accountId(), orderId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("FULFILLMENT_NOT_FOUND", "Fulfillment task was not found in the active Location scope."));
    }

    private static PickupTask map(ResultSet rs, int row) throws SQLException {
        return new PickupTask(rs.getObject("order_id", UUID.class), rs.getObject("fulfillment_id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getString("branch_code"), rs.getString("branch_name"),
                rs.getObject("location_id", UUID.class), rs.getString("location_code"), rs.getString("location_name"),
                rs.getString("sku_snapshot"), rs.getString("size_snapshot"), rs.getLong("quantity"),
                rs.getString("order_status"), rs.getString("fulfillment_type"), rs.getString("fulfillment_status"),
                instant(rs, "created_at"), instant(rs, "picking_started_at"), instant(rs, "prepared_at"),
                instant(rs, "handed_over_at"), instant(rs, "dispatched_at"), instant(rs, "delivered_at"),
                instant(rs, "cancelled_at"), rs.getString("receiver_name"), rs.getString("receiver_phone"),
                rs.getString("delivery_address"), rs.getString("delivery_note"), rs.getLong("delivery_fee_amount"),
                rs.getString("void_status"), 1, List.of(new PickupItem(rs.getString("sku_snapshot"),
                    rs.getString("size_snapshot"), rs.getString("color_snapshot"), rs.getLong("quantity"))));
    }

    private static List<PickupTask> group(List<PickupTask> rows) {
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(PickupTask::orderId,
                java.util.LinkedHashMap::new, java.util.stream.Collectors.toList())).values().stream().map(group -> {
            PickupTask task = group.getFirst();
            List<PickupItem> items = group.stream().flatMap(row -> row.items().stream()).toList();
            return new PickupTask(task.orderId(), task.fulfillmentId(), task.branchId(), task.branchCode(), task.branchName(),
                    task.locationId(), task.locationCode(), task.locationName(), items.size() == 1 ? task.sku() : null,
                    items.size() == 1 ? task.size() : null, items.stream().mapToLong(PickupItem::quantity).sum(),
                    task.orderStatus(), task.fulfillmentType(), task.fulfillmentStatus(), task.createdAt(),
                    task.pickingStartedAt(), task.preparedAt(), task.handedOverAt(), task.dispatchedAt(),
                    task.deliveredAt(), task.cancelledAt(), task.receiverName(), task.receiverPhone(),
                    task.deliveryAddress(), task.deliveryNote(), task.deliveryFeeAmount(),
                    task.financialVoidStatus(), items.size(), items);
        }).toList();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, java.time.LocalDateTime.class);
        return value == null ? null : value.toInstant(java.time.ZoneOffset.UTC);
    }

    public record PickupTask(UUID orderId, UUID fulfillmentId, UUID branchId, String branchCode, String branchName,
            UUID locationId, String locationCode, String locationName, String sku, String size, long quantity,
            String orderStatus, String fulfillmentType, String fulfillmentStatus, Instant createdAt,
            Instant pickingStartedAt, Instant preparedAt, Instant handedOverAt, Instant dispatchedAt,
            Instant deliveredAt, Instant cancelledAt, String receiverName, String receiverPhone,
            String deliveryAddress, String deliveryNote, long deliveryFeeAmount, String financialVoidStatus,
            int itemCount, List<PickupItem> items) { }
    public record PickupItem(String sku, String size, String color, long quantity) { }
}
