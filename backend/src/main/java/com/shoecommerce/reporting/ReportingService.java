package com.shoecommerce.reporting;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.InvalidRequestException;

@Service
public class ReportingService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public ReportingService(JdbcTemplate jdbc, AuthorizationPolicy authorization, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScopeReport scope(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.REPORT_VIEW);
        Instant asOf = clock.instant();
        LocalDate today = asOf.atZone(BUSINESS_ZONE).toLocalDate();
        List<LocationScope> locations = jdbc.query("""
                SELECT DISTINCT branches.public_id AS branch_id, branches.code AS branch_code,
                       branches.name AS branch_name, locations.public_id AS location_id,
                       locations.code AS location_code, locations.name AS location_name
                FROM iam_staff_assignment assignments
                JOIN org_branch branches ON branches.id = assignments.branch_id AND branches.enabled = 1
                JOIN org_location locations ON locations.id = assignments.location_id
                    AND locations.branch_id = assignments.branch_id AND locations.enabled = 1
                WHERE assignments.account_id = ? AND assignments.active = 1
                ORDER BY branches.code, locations.code
                """, (rs, row) -> locationScope(rs), actor.accountId());
        return new ScopeReport(asOf, BUSINESS_ZONE.getId(), today, today.plusDays(1), locations);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NetSalesReport netSales(SessionPrincipal actor, LocalDate fromDate, LocalDate toDate, UUID locationId) {
        Context context = context(actor, fromDate, toDate, locationId);
        Object[] arguments = intervalArguments(context);
        return jdbc.queryForObject("""
                WITH online AS (
                    SELECT COALESCE(SUM(attempts.amount), 0) AS amount
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'SUCCEEDED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                ), pos AS (
                    SELECT COALESCE(SUM(tenders.amount), 0) AS amount
                    FROM cash_tender tenders
                    JOIN commerce_order orders ON orders.id = tenders.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE tenders.created_at >= ? AND tenders.created_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                ), voids AS (
                    SELECT COALESCE(SUM(allocations.amount), 0) AS amount
                    FROM payment_void_allocation allocations
                    JOIN commerce_order_item items ON items.public_id = allocations.component_public_id
                    JOIN commerce_order orders ON orders.id = items.order_id
                    WHERE allocations.status = 'SUCCEEDED' AND allocations.component_type = 'ORDER_ITEM'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                ), exceptions AS (
                    SELECT attempts.amount
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'REVIEW_REQUIRED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT operations.requested_amount
                    FROM payment_void_operation operations
                    JOIN commerce_order orders ON orders.public_id = operations.order_public_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE operations.status IN ('UNKNOWN', 'REVIEW_REQUIRED')
                      AND operations.resolved_at >= ? AND operations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT allocations.amount
                    FROM payment_void_allocation allocations
                    JOIN commerce_order_item items ON items.public_id = allocations.component_public_id
                    JOIN commerce_order orders ON orders.id = items.order_id
                    WHERE allocations.status = 'RELEASED' AND allocations.component_type = 'ORDER_ITEM'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                )
                SELECT online.amount AS online_gross, pos.amount AS pos_gross,
                       online.amount + pos.amount AS gross_sales, voids.amount AS successful_voids,
                       online.amount + pos.amount - voids.amount AS net_sales,
                       COALESCE((SELECT SUM(amount) FROM exceptions), 0) AS exception_amount,
                       (SELECT COUNT(*) FROM exceptions) AS exception_count
                FROM online CROSS JOIN pos CROSS JOIN voids
                """, (rs, row) -> new NetSalesReport(context, money(rs, "online_gross"), money(rs, "pos_gross"),
                        money(rs, "gross_sales"), money(rs, "successful_voids"), money(rs, "net_sales"),
                        money(rs, "exception_amount"), rs.getLong("exception_count"), "VND"),
                expand(arguments, 6));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductSalesReport productSales(SessionPrincipal actor, LocalDate fromDate, LocalDate toDate, UUID locationId) {
        Context context = context(actor, fromDate, toDate, locationId);
        List<ProductSalesRow> rows = jdbc.query("""
                WITH entries AS (
                    SELECT items.variant_public_id, items.sku_snapshot, items.size_snapshot,
                           attempts.amount AS online_gross, CAST(0 AS DECIMAL(38,0)) AS pos_gross,
                           CAST(0 AS DECIMAL(38,0)) AS reversal
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'SUCCEEDED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT items.variant_public_id, items.sku_snapshot, items.size_snapshot,
                           CAST(0 AS DECIMAL(38,0)), tenders.amount, CAST(0 AS DECIMAL(38,0))
                    FROM cash_tender tenders
                    JOIN commerce_order orders ON orders.id = tenders.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE tenders.created_at >= ? AND tenders.created_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT items.variant_public_id, items.sku_snapshot, items.size_snapshot,
                           CAST(0 AS DECIMAL(38,0)), CAST(0 AS DECIMAL(38,0)), allocations.amount
                    FROM payment_void_allocation allocations
                    JOIN commerce_order_item items ON items.public_id = allocations.component_public_id
                    JOIN commerce_order orders ON orders.id = items.order_id
                    WHERE allocations.status = 'SUCCEEDED' AND allocations.component_type = 'ORDER_ITEM'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                )
                SELECT variant_public_id, sku_snapshot, size_snapshot,
                       SUM(online_gross) AS online_gross, SUM(pos_gross) AS pos_gross,
                       SUM(online_gross + pos_gross) AS gross_sales, SUM(reversal) AS successful_voids,
                       SUM(online_gross + pos_gross - reversal) AS net_sales
                FROM entries
                GROUP BY variant_public_id, sku_snapshot, size_snapshot
                ORDER BY net_sales DESC, sku_snapshot, size_snapshot, variant_public_id
                """, (rs, row) -> new ProductSalesRow(rs.getObject("variant_public_id", UUID.class),
                        rs.getString("sku_snapshot"), rs.getString("size_snapshot"),
                        money(rs, "online_gross"), money(rs, "pos_gross"), money(rs, "gross_sales"),
                        money(rs, "successful_voids"), money(rs, "net_sales")),
                expand(intervalArguments(context), 3));
        BigDecimal online = sum(rows, ProductSalesRow::onlineGross);
        BigDecimal pos = sum(rows, ProductSalesRow::posGross);
        BigDecimal gross = sum(rows, ProductSalesRow::grossSales);
        BigDecimal reversals = sum(rows, ProductSalesRow::successfulVoids);
        BigDecimal net = sum(rows, ProductSalesRow::netSales);
        return new ProductSalesReport(context, rows, online.toPlainString(), pos.toPlainString(),
                gross.toPlainString(), reversals.toPlainString(), net.toPlainString(), "VND");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryReport inventory(SessionPrincipal actor, UUID locationId, String sku) {
        Context context = context(actor, null, null, locationId);
        String normalizedSku = normalizeSku(sku);
        String filter = normalizedSku == null ? "" : " AND variants.sku = ?";
        Object[] locationArguments = normalizedSku == null ? new Object[] { locationId } : new Object[] { locationId, normalizedSku };
        List<InventoryRow> rows = jdbc.query("""
                SELECT variants.public_id AS variant_id, products.name AS product_name, variants.sku,
                       variants.size, balances.on_hand, balances.reserved,
                       balances.on_hand - balances.reserved AS available, balances.updated_at
                FROM inventory_balance balances
                JOIN catalog_product_variant variants ON variants.id = balances.variant_id
                JOIN catalog_product products ON products.id = variants.product_id
                JOIN org_location locations ON locations.id = balances.location_id
                WHERE locations.public_id = ? """ + filter + """
                ORDER BY variants.sku, variants.size, variants.public_id
                """, (rs, row) -> new InventoryRow(rs.getObject("variant_id", UUID.class),
                        rs.getString("product_name"), rs.getString("sku"), rs.getString("size"),
                        rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("available"),
                        rs.getTimestamp("updated_at").toInstant()), locationArguments);
        List<MovementRow> movements = jdbc.query("""
                SELECT TOP (100) movements.public_id, movements.order_public_id, movements.variant_public_id,
                       variants.sku, movements.operation_type, movements.on_hand_delta,
                       movements.reserved_delta, movements.occurred_at
                FROM inventory_stock_movement movements
                JOIN catalog_product_variant variants ON variants.public_id = movements.variant_public_id
                WHERE movements.location_public_id = ? """ + filter + """
                ORDER BY movements.occurred_at DESC, movements.public_id
                """, (rs, row) -> new MovementRow(rs.getObject("public_id", UUID.class),
                        rs.getObject("order_public_id", UUID.class), rs.getObject("variant_public_id", UUID.class),
                        rs.getString("sku"), rs.getString("operation_type"), rs.getLong("on_hand_delta"),
                        rs.getLong("reserved_delta"), rs.getTimestamp("occurred_at").toInstant()), locationArguments);
        List<ReservationRow> reservations = jdbc.query("""
                SELECT reservations.public_id, variants.public_id AS variant_id, variants.sku,
                       reservations.quantity, reservations.status, reservations.created_at,
                       reservations.expires_at
                FROM inventory_reservation reservations
                JOIN catalog_product_variant variants ON variants.id = reservations.variant_id
                JOIN org_location locations ON locations.id = reservations.location_id
                WHERE locations.public_id = ? AND reservations.status IN ('ACTIVE', 'ADOPTED', 'COMMITTED') """ + filter + """
                ORDER BY variants.sku, reservations.created_at, reservations.public_id
                """, (rs, row) -> new ReservationRow(rs.getObject("public_id", UUID.class),
                        rs.getObject("variant_id", UUID.class), rs.getString("sku"), rs.getLong("quantity"),
                        rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                        nullableInstant(rs, "expires_at")), locationArguments);
        return new InventoryReport(context, normalizedSku, rows, movements, reservations);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport reconciliation(SessionPrincipal actor, LocalDate fromDate, LocalDate toDate, UUID locationId) {
        Context context = context(actor, fromDate, toDate, locationId);
        List<ReconciliationEntry> entries = jdbc.query("""
                WITH entries AS (
                    SELECT 'ONLINE_CAPTURE' AS category, attempts.public_id AS reference_id,
                           orders.public_id AS order_id, 'SUCCEEDED' AS status, attempts.amount,
                           attempts.amount AS net_effect, attempts.resolved_at AS occurred_at, CAST(0 AS BIT) AS exception
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'SUCCEEDED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT 'POS_CASH', tenders.public_id, orders.public_id, 'ACCEPTED', tenders.amount,
                           tenders.amount, tenders.created_at, CAST(0 AS BIT)
                    FROM cash_tender tenders
                    JOIN commerce_order orders ON orders.id = tenders.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE tenders.created_at >= ? AND tenders.created_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT 'VOID', allocations.public_id, orders.public_id, 'SUCCEEDED', allocations.amount,
                           -allocations.amount, allocations.resolved_at, CAST(0 AS BIT)
                    FROM payment_void_allocation allocations
                    JOIN commerce_order_item items ON items.public_id = allocations.component_public_id
                    JOIN commerce_order orders ON orders.id = items.order_id
                    WHERE allocations.status = 'SUCCEEDED' AND allocations.component_type = 'ORDER_ITEM'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT 'PAYMENT_REVIEW', attempts.public_id, orders.public_id, 'REVIEW_REQUIRED', attempts.amount,
                           CAST(0 AS DECIMAL(38,0)), attempts.resolved_at, CAST(1 AS BIT)
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE attempts.status = 'REVIEW_REQUIRED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT 'VOID_RECONCILIATION', operations.public_id, orders.public_id, operations.status,
                            operations.requested_amount, CAST(0 AS DECIMAL(38,0)), operations.resolved_at, CAST(1 AS BIT)
                    FROM payment_void_operation operations
                    JOIN commerce_order orders ON orders.public_id = operations.order_public_id
                    JOIN commerce_order_item items ON items.order_id = orders.id
                    WHERE operations.status IN ('UNKNOWN', 'REVIEW_REQUIRED')
                      AND operations.resolved_at >= ? AND operations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                    UNION ALL
                    SELECT 'VOID_RECONCILIATION', allocations.public_id, orders.public_id, 'RELEASED',
                           allocations.amount, CAST(0 AS DECIMAL(38,0)), allocations.resolved_at, CAST(1 AS BIT)
                    FROM payment_void_allocation allocations
                    JOIN commerce_order_item items ON items.public_id = allocations.component_public_id
                    JOIN commerce_order orders ON orders.id = items.order_id
                    WHERE allocations.status = 'RELEASED' AND allocations.component_type = 'ORDER_ITEM'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND items.location_public_id = ?
                )
                SELECT category, reference_id, order_id, status, amount, net_effect, occurred_at, exception
                FROM entries ORDER BY occurred_at DESC, reference_id
                """, (rs, row) -> new ReconciliationEntry(rs.getString("category"),
                        rs.getObject("reference_id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getString("status"), money(rs, "amount"), money(rs, "net_effect"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getBoolean("exception")),
                expand(intervalArguments(context), 6));
        BigDecimal exceptionAmount = entries.stream().filter(ReconciliationEntry::exception)
                .map(entry -> new BigDecimal(entry.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long exceptionCount = entries.stream().filter(ReconciliationEntry::exception).count();
        return new ReconciliationReport(context, entries, exceptionAmount.toPlainString(), exceptionCount, "VND");
    }

    private Context context(SessionPrincipal actor, LocalDate fromDate, LocalDate toDate, UUID locationId) {
        authorization.requirePermission(actor, PermissionCode.REPORT_VIEW);
        if (locationId == null) throw new InvalidRequestException("REPORT_LOCATION_REQUIRED", "Location is required.");
        authorization.requireLocationAccess(actor, locationId);
        LocationScope scope = jdbc.queryForObject("""
                SELECT branches.public_id AS branch_id, branches.code AS branch_code, branches.name AS branch_name,
                       locations.public_id AS location_id, locations.code AS location_code, locations.name AS location_name
                FROM org_location locations JOIN org_branch branches ON branches.id = locations.branch_id
                WHERE locations.public_id = ? AND locations.enabled = 1 AND branches.enabled = 1
                """, (rs, row) -> locationScope(rs), locationId);
        if (scope == null) throw new InvalidRequestException("REPORT_SCOPE_UNAVAILABLE", "Reporting scope is unavailable.");
        Instant from = null;
        Instant to = null;
        if (fromDate != null || toDate != null) {
            if (fromDate == null || toDate == null || !fromDate.isBefore(toDate)) {
                throw new InvalidRequestException("INVALID_REPORT_RANGE", "From date must be before the exclusive to date.");
            }
            from = fromDate.atStartOfDay(BUSINESS_ZONE).toInstant();
            to = toDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        }
        return new Context(from, to, clock.instant(), BUSINESS_ZONE.getId(), scope);
    }

    private static LocationScope locationScope(ResultSet rs) throws SQLException {
        return new LocationScope(rs.getObject("branch_id", UUID.class), rs.getString("branch_code"),
                rs.getString("branch_name"), rs.getObject("location_id", UUID.class),
                rs.getString("location_code"), rs.getString("location_name"));
    }

    private static Object[] intervalArguments(Context context) {
        return new Object[] { LocalDateTime.ofInstant(context.from(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(context.to(), ZoneOffset.UTC),
                context.scope().branchId(), context.scope().locationId() };
    }

    private static Object[] expand(Object[] arguments, int repetitions) {
        Object[] expanded = new Object[arguments.length * repetitions];
        for (int index = 0; index < repetitions; index++) {
            System.arraycopy(arguments, 0, expanded, index * arguments.length, arguments.length);
        }
        return expanded;
    }

    private static String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        String normalized = sku.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > 64) throw new InvalidRequestException("INVALID_REPORT_FILTER", "SKU is too long.");
        return normalized;
    }

    private static String money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return (value == null ? BigDecimal.ZERO : value).toPlainString();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static BigDecimal sum(List<ProductSalesRow> rows,
            java.util.function.Function<ProductSalesRow, String> getter) {
        return rows.stream().map(getter).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record LocationScope(UUID branchId, String branchCode, String branchName, UUID locationId,
            String locationCode, String locationName) { }
    public record ScopeReport(Instant asOf, String businessTimezone, LocalDate defaultFromDate,
            LocalDate defaultToDate, List<LocationScope> locations) { }
    public record Context(Instant from, Instant to, Instant asOf, String businessTimezone, LocationScope scope) { }
    public record NetSalesReport(Context context, String onlineGross, String posGross, String grossSales,
            String successfulVoids, String netSales, String exceptionAmount, long exceptionCount, String currency) { }
    public record ProductSalesRow(UUID variantId, String sku, String size, String onlineGross, String posGross,
            String grossSales, String successfulVoids, String netSales) { }
    public record ProductSalesReport(Context context, List<ProductSalesRow> rows, String onlineGross,
            String posGross, String grossSales, String successfulVoids, String netSales, String currency) { }
    public record InventoryRow(UUID variantId, String productName, String sku, String size, long onHand,
            long reserved, long available, Instant updatedAt) { }
    public record MovementRow(UUID id, UUID orderId, UUID variantId, String sku, String type,
            long onHandDelta, long reservedDelta, Instant occurredAt) { }
    public record ReservationRow(UUID id, UUID variantId, String sku, long quantity, String status,
            Instant createdAt, Instant expiresAt) { }
    public record InventoryReport(Context context, String sku, List<InventoryRow> rows,
            List<MovementRow> movements, List<ReservationRow> reservations) { }
    public record ReconciliationEntry(String category, UUID referenceId, UUID orderId, String status,
            String amount, String netEffect, Instant occurredAt, boolean exception) { }
    public record ReconciliationReport(Context context, List<ReconciliationEntry> entries,
            String exceptionAmount, long exceptionCount, String currency) { }
}
