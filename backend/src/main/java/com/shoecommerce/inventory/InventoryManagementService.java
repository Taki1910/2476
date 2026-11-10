package com.shoecommerce.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class InventoryManagementService {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final CheckoutHoldExpiryService checkoutExpiry;

    public InventoryManagementService(JdbcTemplate jdbc, AuthorizationPolicy authorization,
            CheckoutHoldExpiryService checkoutExpiry) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.checkoutExpiry = checkoutExpiry;
    }

    @Transactional(readOnly = true)
    public ProductInventory product(SessionPrincipal actor, UUID productId) {
        authorization.requirePermission(actor, PermissionCode.INVENTORY_VIEW);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM catalog_product WHERE public_id=?",
                Integer.class, productId);
        if (exists == null || exists == 0) {
            throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found.");
        }
        checkoutExpiry.expireRelevant(productId);
        List<BalanceView> balances = jdbc.query("""
                WITH scoped_locations AS (
                    SELECT DISTINCT locations.id,locations.public_id,locations.code,locations.name,
                           branches.public_id branch_public_id,branches.code branch_code,branches.name branch_name
                    FROM iam_staff_assignment assignments
                    JOIN org_location locations ON locations.id=assignments.location_id
                         AND locations.branch_id=assignments.branch_id AND locations.enabled=1
                    JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                    WHERE assignments.account_id=? AND assignments.active=1
                )
                SELECT variants.public_id variant_id,variants.sku,variants.size,variants.color,
                       variants.lifecycle_status,locations.public_id location_id,locations.code location_code,
                       locations.name location_name,locations.branch_public_id,locations.branch_code,
                       locations.branch_name,balances.on_hand,balances.reserved,balances.entity_version,
                       balances.updated_at
                FROM catalog_product products
                JOIN catalog_product_variant variants ON variants.product_id=products.id
                CROSS JOIN scoped_locations locations
                LEFT JOIN inventory_balance balances ON balances.variant_id=variants.id
                     AND balances.location_id=locations.id
                WHERE products.public_id=?
                ORDER BY variants.color,variants.size,variants.sku,locations.branch_code,locations.code
                """, InventoryManagementService::balance, actor.accountId(), productId);
        return new ProductInventory(productId, balances);
    }

    private static BalanceView balance(ResultSet row, int index) throws SQLException {
        Long onHand = nullableLong(row, "on_hand");
        Long reserved = nullableLong(row, "reserved");
        Long version = nullableLong(row, "entity_version");
        return new BalanceView(row.getObject("variant_id", UUID.class), row.getString("sku"),
                row.getString("size"), row.getString("color"), row.getString("lifecycle_status"),
                row.getObject("location_id", UUID.class), row.getString("location_code"),
                row.getString("location_name"), row.getObject("branch_public_id", UUID.class),
                row.getString("branch_code"), row.getString("branch_name"), onHand != null,
                onHand == null ? 0 : onHand, reserved == null ? 0 : reserved,
                onHand == null ? 0 : onHand - (reserved == null ? 0 : reserved), version,
                instant(row, "updated_at"));
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record ProductInventory(UUID productId, List<BalanceView> balances) { }
    public record BalanceView(UUID variantId, String sku, String size, String color, String status,
            UUID locationId, String locationCode, String locationName, UUID branchId, String branchCode,
            String branchName, boolean hasBalance, long onHand, long reserved, long available,
            Long balanceVersion, Instant updatedAt) { }
}
