package com.shoecommerce.reporting;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        return jdbc.queryForObject("""
                WITH recognized AS (
                    SELECT orders.id, orders.merchandise_amount, orders.merchandise_discount_amount,
                           orders.shipping_fee_amount, orders.shipping_discount_amount, attempts.amount, 'ONLINE' AS source
                    FROM payment_attempt attempts JOIN payment payments ON payments.id=attempts.payment_id
                    JOIN commerce_order orders ON orders.id=payments.order_id
                    WHERE attempts.status='SUCCEEDED' AND attempts.resolved_at>=? AND attempts.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                    UNION ALL
                    SELECT orders.id,orders.merchandise_amount,orders.merchandise_discount_amount,
                           orders.shipping_fee_amount,orders.shipping_discount_amount,tenders.amount,'POS'
                    FROM cash_tender tenders JOIN commerce_order orders ON orders.id=tenders.order_id
                    WHERE tenders.created_at>=? AND tenders.created_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                ), captured AS (
                    SELECT COALESCE(SUM(CASE source WHEN 'ONLINE' THEN amount ELSE 0 END),0) AS online_gross,
                           COALESCE(SUM(CASE source WHEN 'POS' THEN amount ELSE 0 END),0) AS pos_gross,
                           COALESCE(SUM(amount),0) AS gross_sales,
                           COALESCE(SUM(merchandise_amount),0) AS merchandise_gross,
                           COALESCE(SUM(merchandise_amount-merchandise_discount_amount),0) AS merchandise_net,
                           COALESCE(SUM(shipping_fee_amount),0) AS shipping_gross,
                           COALESCE(SUM(shipping_discount_amount),0) AS shipping_discount,
                           COALESCE(SUM(shipping_fee_amount-shipping_discount_amount),0) AS shipping_net
                    FROM recognized
                ), discounts AS (
                    SELECT COALESCE(SUM(CASE a.layer WHEN 'ITEM' THEN a.applied_amount ELSE 0 END),0) AS item_discount,
                           COALESCE(SUM(CASE a.layer WHEN 'ORDER_ALLOCATION' THEN a.applied_amount ELSE 0 END),0) AS order_discount,
                           COALESCE(SUM(CASE a.layer WHEN 'VOUCHER_ALLOCATION' THEN a.applied_amount ELSE 0 END),0) AS voucher_discount
                    FROM recognized r JOIN commerce_order_item i ON i.order_id=r.id
                    JOIN commerce_order_item_adjustment a ON a.order_item_public_id=i.public_id
                ), voids AS (
                    SELECT COALESCE(SUM(a.amount),0) AS amount,
                           COALESCE(SUM(CASE WHEN v.calculation_version='SNAPSHOT_V2' AND a.component_type='ORDER_ITEM' THEN a.amount ELSE 0 END),0) AS merchandise_voids,
                           COALESCE(SUM(CASE WHEN v.calculation_version='SNAPSHOT_V2' AND a.component_type='SHIPPING' THEN a.amount ELSE 0 END),0) AS shipping_voids,
                           COALESCE(SUM(CASE WHEN v.calculation_version='LEGACY_V1' THEN a.amount ELSE 0 END),0) AS legacy_voids
                    FROM payment_void_allocation a JOIN payment_void_attempt v ON v.id=a.void_attempt_id
                    JOIN payment_void_operation op ON op.id=a.void_operation_id
                    JOIN commerce_order orders ON orders.public_id=op.order_public_id
                    WHERE a.status='SUCCEEDED' AND a.resolved_at>=? AND a.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                ), exceptions AS (
                    SELECT a.amount FROM payment_attempt a JOIN payment p ON p.id=a.payment_id
                    JOIN commerce_order orders ON orders.id=p.order_id
                    WHERE a.status='REVIEW_REQUIRED' AND a.resolved_at>=? AND a.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                    UNION ALL
                    SELECT op.requested_amount FROM payment_void_operation op JOIN commerce_order orders ON orders.public_id=op.order_public_id
                    WHERE op.status IN ('UNKNOWN','REVIEW_REQUIRED') AND op.resolved_at>=? AND op.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                    UNION ALL
                    SELECT a.amount FROM payment_void_allocation a JOIN payment_void_operation op ON op.id=a.void_operation_id
                    JOIN commerce_order orders ON orders.public_id=op.order_public_id
                    WHERE a.status='RELEASED' AND a.resolved_at>=? AND a.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                )
                SELECT c.*,d.*,v.amount AS successful_voids,v.merchandise_voids,v.shipping_voids,v.legacy_voids,
                       c.gross_sales-v.amount AS net_sales,
                       COALESCE((SELECT SUM(amount) FROM exceptions),0) AS exception_amount,
                       (SELECT COUNT(*) FROM exceptions) AS exception_count
                FROM captured c CROSS JOIN discounts d CROSS JOIN voids v
                """, (rs,row) -> new NetSalesReport(context,money(rs,"online_gross"),money(rs,"pos_gross"),
                        money(rs,"gross_sales"),money(rs,"successful_voids"),money(rs,"net_sales"),
                        money(rs,"exception_amount"),rs.getLong("exception_count"),"VND",
                        money(rs,"merchandise_gross"),money(rs,"item_discount"),money(rs,"order_discount"),
                        money(rs,"voucher_discount"),money(rs,"merchandise_net"),money(rs,"shipping_gross"),
                        money(rs,"shipping_discount"),money(rs,"shipping_net"),money(rs,"merchandise_voids"),
                        money(rs,"shipping_voids"),money(rs,"legacy_voids")),expand(intervalArguments(context),6));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductSalesReport productSales(SessionPrincipal actor, LocalDate fromDate, LocalDate toDate, UUID locationId) {
        Context context = context(actor, fromDate, toDate, locationId);
        List<ProductSalesRow> rows = jdbc.query("""
                WITH discounts AS (
                    SELECT order_item_public_id,
                           SUM(CASE layer WHEN 'ITEM' THEN applied_amount ELSE 0 END) AS item_discount,
                           SUM(CASE layer WHEN 'ORDER_ALLOCATION' THEN applied_amount ELSE 0 END) AS order_discount,
                           SUM(CASE layer WHEN 'VOUCHER_ALLOCATION' THEN applied_amount ELSE 0 END) AS voucher_discount
                    FROM commerce_order_item_adjustment GROUP BY order_item_public_id
                ), entries AS (
                    SELECT items.variant_public_id,items.sku_snapshot,items.size_snapshot,
                           items.unit_price_amount*items.quantity AS online_gross,CAST(0 AS DECIMAL(38,0)) AS pos_gross,
                           CAST(0 AS DECIMAL(38,0)) AS reversal,
                           COALESCE(d.item_discount,0) AS item_discount,COALESCE(d.order_discount,0) AS order_discount,
                           COALESCE(d.voucher_discount,0) AS voucher_discount
                    FROM payment_attempt attempts JOIN payment payments ON payments.id=attempts.payment_id
                    JOIN commerce_order orders ON orders.id=payments.order_id
                    JOIN commerce_order_item items ON items.order_id=orders.id
                    LEFT JOIN discounts d ON d.order_item_public_id=items.public_id
                    WHERE attempts.status='SUCCEEDED' AND attempts.resolved_at>=? AND attempts.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND items.location_public_id=?
                    UNION ALL
                    SELECT items.variant_public_id,items.sku_snapshot,items.size_snapshot,
                           0,items.unit_price_amount*items.quantity,0,
                           COALESCE(d.item_discount,0),COALESCE(d.order_discount,0),COALESCE(d.voucher_discount,0)
                    FROM cash_tender tenders JOIN commerce_order orders ON orders.id=tenders.order_id
                    JOIN commerce_order_item items ON items.order_id=orders.id
                    LEFT JOIN discounts d ON d.order_item_public_id=items.public_id
                    WHERE tenders.created_at>=? AND tenders.created_at<?
                      AND orders.responsible_branch_public_id=? AND items.location_public_id=?
                    UNION ALL
                    SELECT items.variant_public_id,items.sku_snapshot,items.size_snapshot,0,0,a.amount,0,0,0
                    FROM payment_void_allocation a JOIN payment_void_attempt v ON v.id=a.void_attempt_id
                    JOIN commerce_order_item items ON items.public_id=a.component_public_id
                    JOIN commerce_order orders ON orders.id=items.order_id
                    WHERE a.status='SUCCEEDED' AND a.component_type='ORDER_ITEM' AND v.calculation_version='SNAPSHOT_V2'
                      AND a.resolved_at>=? AND a.resolved_at<?
                      AND orders.responsible_branch_public_id=? AND items.location_public_id=?
                )
                SELECT variant_public_id,sku_snapshot,size_snapshot,
                       SUM(online_gross) AS online_gross,SUM(pos_gross) AS pos_gross,
                       SUM(online_gross+pos_gross) AS gross_sales,SUM(reversal) AS successful_voids,
                       SUM(item_discount) AS item_discount,SUM(order_discount) AS order_discount,
                       SUM(voucher_discount) AS voucher_discount,
                       SUM(online_gross+pos_gross-item_discount-order_discount-voucher_discount) AS merchandise_net,
                       SUM(online_gross+pos_gross-item_discount-order_discount-voucher_discount-reversal) AS net_sales
                FROM entries GROUP BY variant_public_id,sku_snapshot,size_snapshot
                ORDER BY net_sales DESC,sku_snapshot,size_snapshot,variant_public_id
                """, (rs,row) -> new ProductSalesRow(rs.getObject("variant_public_id",UUID.class),
                        rs.getString("sku_snapshot"),rs.getString("size_snapshot"),money(rs,"online_gross"),
                        money(rs,"pos_gross"),money(rs,"gross_sales"),money(rs,"successful_voids"),money(rs,"net_sales"),
                        money(rs,"item_discount"),money(rs,"order_discount"),money(rs,"voucher_discount"),money(rs,"merchandise_net")),
                expand(intervalArguments(context),3));
        String legacy = jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.amount),0) FROM payment_void_allocation a
                JOIN payment_void_attempt v ON v.id=a.void_attempt_id
                JOIN payment_void_operation op ON op.id=a.void_operation_id
                JOIN commerce_order orders ON orders.public_id=op.order_public_id
                WHERE a.status='SUCCEEDED' AND v.calculation_version='LEGACY_V1'
                  AND a.resolved_at>=? AND a.resolved_at<?
                  AND orders.responsible_branch_public_id=? AND EXISTS(SELECT 1 FROM commerce_order_item i WHERE i.order_id=orders.id AND i.location_public_id=?)
                """, (rs,n)->rs.getBigDecimal(1).toPlainString(),intervalArguments(context));
        return new ProductSalesReport(context,rows,sum(rows,ProductSalesRow::onlineGross).toPlainString(),
                sum(rows,ProductSalesRow::posGross).toPlainString(),sum(rows,ProductSalesRow::grossSales).toPlainString(),
                sum(rows,ProductSalesRow::successfulVoids).toPlainString(),sum(rows,ProductSalesRow::netSales).toPlainString(),"VND",
                sum(rows,ProductSalesRow::itemDiscount).toPlainString(),sum(rows,ProductSalesRow::orderDiscount).toPlainString(),
                sum(rows,ProductSalesRow::voucherDiscount).toPlainString(),sum(rows,ProductSalesRow::merchandiseNetBeforeReversal).toPlainString(),legacy);
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
                        instant(rs, "updated_at")), locationArguments);
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
                        rs.getLong("reserved_delta"), instant(rs, "occurred_at")), locationArguments);
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
                        rs.getString("status"), instant(rs, "created_at"),
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
                    WHERE attempts.status = 'SUCCEEDED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS (SELECT 1 FROM commerce_order_item scope_items
                          WHERE scope_items.order_id = orders.id AND scope_items.location_public_id = ?)
                    UNION ALL
                    SELECT 'POS_CASH', tenders.public_id, orders.public_id, 'ACCEPTED', tenders.amount,
                           tenders.amount, tenders.created_at, CAST(0 AS BIT)
                    FROM cash_tender tenders
                    JOIN commerce_order orders ON orders.id = tenders.order_id
                    WHERE tenders.created_at >= ? AND tenders.created_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS (SELECT 1 FROM commerce_order_item scope_items
                          WHERE scope_items.order_id = orders.id AND scope_items.location_public_id = ?)
                    UNION ALL
                    SELECT CASE WHEN attempts.calculation_version='LEGACY_V1' THEN 'VOID_LEGACY'
                                WHEN allocations.component_type='SHIPPING' THEN 'VOID_SHIPPING' ELSE 'VOID' END,
                           allocations.public_id, orders.public_id, 'SUCCEEDED', allocations.amount,
                           -allocations.amount, allocations.resolved_at, CAST(0 AS BIT)
                    FROM payment_void_allocation allocations
                    JOIN payment_void_attempt attempts ON attempts.id=allocations.void_attempt_id
                    JOIN payment_void_operation operations ON operations.id=allocations.void_operation_id
                    JOIN commerce_order orders ON orders.public_id=operations.order_public_id
                    WHERE allocations.status = 'SUCCEEDED'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS(SELECT 1 FROM commerce_order_item items
                          WHERE items.order_id=orders.id AND items.location_public_id=?)
                    UNION ALL
                    SELECT 'PAYMENT_REVIEW', attempts.public_id, orders.public_id, 'REVIEW_REQUIRED', attempts.amount,
                           CAST(0 AS DECIMAL(38,0)), attempts.resolved_at, CAST(1 AS BIT)
                    FROM payment_attempt attempts
                    JOIN payment payments ON payments.id = attempts.payment_id
                    JOIN commerce_order orders ON orders.id = payments.order_id
                    WHERE attempts.status = 'REVIEW_REQUIRED' AND attempts.resolved_at >= ? AND attempts.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS (SELECT 1 FROM commerce_order_item scope_items
                          WHERE scope_items.order_id = orders.id AND scope_items.location_public_id = ?)
                    UNION ALL
                    SELECT 'VOID_RECONCILIATION', operations.public_id, orders.public_id, operations.status,
                            operations.requested_amount, CAST(0 AS DECIMAL(38,0)), operations.resolved_at, CAST(1 AS BIT)
                    FROM payment_void_operation operations
                    JOIN commerce_order orders ON orders.public_id = operations.order_public_id
                    WHERE operations.status IN ('UNKNOWN', 'REVIEW_REQUIRED')
                      AND operations.resolved_at >= ? AND operations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS (SELECT 1 FROM commerce_order_item scope_items
                          WHERE scope_items.order_id = orders.id AND scope_items.location_public_id = ?)
                    UNION ALL
                    SELECT 'VOID_RECONCILIATION', allocations.public_id, orders.public_id, 'RELEASED',
                           allocations.amount, CAST(0 AS DECIMAL(38,0)), allocations.resolved_at, CAST(1 AS BIT)
                    FROM payment_void_allocation allocations
                    JOIN payment_void_operation operations ON operations.id=allocations.void_operation_id
                    JOIN commerce_order orders ON orders.public_id=operations.order_public_id
                    WHERE allocations.status = 'RELEASED'
                      AND allocations.resolved_at >= ? AND allocations.resolved_at < ?
                      AND orders.responsible_branch_public_id = ? AND EXISTS(SELECT 1 FROM commerce_order_item items
                          WHERE items.order_id=orders.id AND items.location_public_id=?)
                )
                SELECT category, reference_id, order_id, status, amount, net_effect, occurred_at, exception
                FROM entries ORDER BY occurred_at DESC, reference_id
                """, (rs, row) -> new ReconciliationEntry(rs.getString("category"),
                        rs.getObject("reference_id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getString("status"), money(rs, "amount"), money(rs, "net_effect"),
                        instant(rs, "occurred_at"), rs.getBoolean("exception")),
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
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
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
            String successfulVoids, String netSales, String exceptionAmount, long exceptionCount, String currency,
            String merchandiseGross, String itemDiscount, String orderDiscount, String voucherDiscount,
            String merchandiseNetBeforeReversal, String shippingGross, String shippingDiscount,
            String shippingNetBeforeReversal, String merchandiseVoids, String shippingVoids, String unallocatedLegacyVoids) { }
    public record ProductSalesRow(UUID variantId, String sku, String size, String onlineGross, String posGross,
            String grossSales, String successfulVoids, String netSales, String itemDiscount, String orderDiscount,
            String voucherDiscount, String merchandiseNetBeforeReversal) { }
    public record ProductSalesReport(Context context, List<ProductSalesRow> rows, String onlineGross,
            String posGross, String grossSales, String successfulVoids, String netSales, String currency,
            String itemDiscount, String orderDiscount, String voucherDiscount, String merchandiseNetBeforeReversal,
            String unallocatedLegacyVoids) { }
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
