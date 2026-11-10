package com.shoecommerce.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.platform.api.BusinessConflictException;

/** Immutable Order evidence only. Financial callers own authorization and Order/Payment locks. */
@Service
public class OrderPaidComponents {
    public enum PaidComponentType { ORDER_ITEM, SHIPPING }
    public record PaidComponentKey(PaidComponentType type, UUID publicId) { }
    public record PaidComponent(PaidComponentKey key, BigDecimal amount) { }
    private record Totals(BigDecimal gross, BigDecimal discount, BigDecimal shipping,
            BigDecimal shippingDiscount, BigDecimal total) { }
    private record Line(UUID id, BigDecimal gross, BigDecimal discount) { }
    private final JdbcTemplate jdbc;

    public OrderPaidComponents(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<PaidComponent> read(UUID orderId) {
        var orders = jdbc.query("""
                SELECT merchandise_amount, merchandise_discount_amount, shipping_fee_amount,
                       shipping_discount_amount, total_amount FROM commerce_order WHERE public_id=?
                """, (rs, n) -> new Totals(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5)), orderId);
        if (orders.size() != 1) throw invalid();
        var totals = orders.getFirst();
        var lines = jdbc.query("""
                SELECT i.public_id, i.unit_price_amount*i.quantity,
                       COALESCE(SUM(a.applied_amount),0)
                FROM commerce_order_item i JOIN commerce_order o ON o.id=i.order_id
                LEFT JOIN commerce_order_item_adjustment a ON a.order_item_public_id=i.public_id
                WHERE o.public_id=? GROUP BY i.public_id,i.unit_price_amount,i.quantity ORDER BY i.public_id
                """, (rs, n) -> new Line(rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getBigDecimal(3)), orderId);
        if (lines.isEmpty()
                || lines.stream().map(Line::gross).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(totals.gross()) != 0
                || lines.stream().map(Line::discount).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(totals.discount()) != 0) {
            throw invalid();
        }
        // Validate child evidence against each parent, without subtracting parent discounts twice.
        Integer brokenParents = jdbc.queryForObject("""
                SELECT COUNT(*) FROM commerce_order_adjustment p
                WHERE p.order_public_id=? AND p.layer IN ('ORDER_AUTOMATIC','VOUCHER_SELECTED')
                AND p.applied_amount<>(SELECT COALESCE(SUM(a.applied_amount),0)
                    FROM commerce_order_item_adjustment a JOIN commerce_order_item i ON i.public_id=a.order_item_public_id
                    JOIN commerce_order o ON o.id=i.order_id
                    WHERE a.parent_order_adjustment_public_id=p.public_id AND o.public_id=p.order_public_id
                    AND a.layer=CASE p.layer WHEN 'ORDER_AUTOMATIC' THEN 'ORDER_ALLOCATION' ELSE 'VOUCHER_ALLOCATION' END)
                """, Integer.class, orderId);
        Integer brokenChildren = jdbc.queryForObject("""
                SELECT COUNT(*) FROM commerce_order_item_adjustment a
                JOIN commerce_order_item i ON i.public_id=a.order_item_public_id
                JOIN commerce_order o ON o.id=i.order_id
                LEFT JOIN commerce_order_adjustment p ON p.public_id=a.parent_order_adjustment_public_id
                WHERE o.public_id=? AND ((a.layer='ITEM' AND a.parent_order_adjustment_public_id IS NOT NULL)
                    OR (a.layer<>'ITEM' AND (p.public_id IS NULL OR p.order_public_id<>o.public_id
                        OR p.layer<>CASE a.layer WHEN 'ORDER_ALLOCATION' THEN 'ORDER_AUTOMATIC' ELSE 'VOUCHER_SELECTED' END)))
                """, Integer.class, orderId);
        BigDecimal shippingDiscount = jdbc.queryForObject("""
                SELECT COALESCE(SUM(applied_amount),0) FROM commerce_order_adjustment
                WHERE order_public_id=? AND layer='SHIPPING'
                """, BigDecimal.class, orderId);
        if (brokenParents != 0 || brokenChildren != 0 || shippingDiscount.compareTo(totals.shippingDiscount()) != 0) throw invalid();

        List<PaidComponent> components = new ArrayList<>();
        for (var line : lines) add(components, PaidComponentType.ORDER_ITEM, line.id(), line.gross().subtract(line.discount()));
        add(components, PaidComponentType.SHIPPING, orderId, totals.shipping().subtract(totals.shippingDiscount()));
        if (totals.total().signum() <= 0 || components.stream().map(PaidComponent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(totals.total()) != 0) throw invalid();
        return List.copyOf(components);
    }

    private static void add(List<PaidComponent> components, PaidComponentType type, UUID id, BigDecimal amount) {
        if (amount.signum() < 0) throw invalid();
        if (amount.signum() > 0) components.add(new PaidComponent(new PaidComponentKey(type, id), amount));
    }

    private static BusinessConflictException invalid() {
        return new BusinessConflictException("PAID_COMPONENT_SNAPSHOT_INVALID", "Immutable Order paid components are inconsistent.");
    }
}
