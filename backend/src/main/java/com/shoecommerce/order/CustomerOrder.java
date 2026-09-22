package com.shoecommerce.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.shoecommerce.pricing.VariantPrice;
import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity(name = "CustomerOrder")
@Table(name = "commerce_order")
public class CustomerOrder {
    enum Status { PENDING_PAYMENT, PAID, CANCELLED }
    enum Channel { ONLINE, POS }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "owner_account_public_id") private UUID ownerAccountPublicId;
    @Column(name = "responsible_branch_public_id", nullable = false) private UUID responsibleBranchPublicId;
    @Column(name = "reservation_public_id") private UUID reservationPublicId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Channel channel;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Status status;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "price_quote_public_id") private UUID priceQuotePublicId;
    @Column(name = "checkout_idempotency_key", length = 128) private String checkoutIdempotencyKey;
    @Column(name = "price_version_public_id") private UUID priceVersionPublicId;
    @Column(name = "cart_quote_public_id") private UUID cartQuotePublicId;
    @Column(name = "checkout_fingerprint", length = 64) private String checkoutFingerprint;
    @Column(name = "merchandise_amount", nullable = false, precision = 19, scale = 0) private BigDecimal merchandiseAmount;
    @Column(name = "calculation_policy", nullable = false, length = 16) private String calculationPolicy = "VND_V1";
    @Column(name = "merchandise_discount_amount", nullable = false, precision = 19, scale = 0) private BigDecimal merchandiseDiscountAmount = BigDecimal.ZERO;
    @Column(name = "shipping_fee_amount", nullable = false, precision = 19, scale = 0) private BigDecimal shippingFeeAmount;
    @Column(name = "shipping_discount_amount", nullable = false, precision = 19, scale = 0) private BigDecimal shippingDiscountAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0) private BigDecimal total;
    @Column(name = "shipping_rule_public_id") private UUID shippingRulePublicId;
    @Column(name = "shipping_zone_code", length = 32) private String shippingZoneCode;
    @Column(name = "shipping_origin_location_public_id") private UUID shippingOriginLocationId;
    @Column(name = "shipping_origin_branch_public_id") private UUID shippingOriginBranchId;
    @Column(name = "destination_province_code", length = 12) private String destinationProvinceCode;
    @Column(name = "destination_district_code", length = 12) private String destinationDistrictCode;
    @Column(name = "shipping_quoted_at") private Instant shippingQuotedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id ASC") private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() { }

    static CustomerOrder create(UUID ownerAccountPublicId, UUID branchPublicId, UUID reservationPublicId,
            UUID variantPublicId, UUID locationPublicId, long quantity, long unitPriceAmount, Instant now) {
        if (ownerAccountPublicId == null || branchPublicId == null || reservationPublicId == null) throw new IllegalArgumentException("Order ownership is invalid");
        CustomerOrder order = new CustomerOrder();
        order.publicId = UUID.randomUUID();
        order.ownerAccountPublicId = ownerAccountPublicId;
        order.responsibleBranchPublicId = branchPublicId;
        order.reservationPublicId = reservationPublicId;
        order.channel = Channel.ONLINE;
        order.currency = "VND";
        order.status = Status.PENDING_PAYMENT;
        order.createdAt = now;
        order.items.add(OrderItem.create(order, variantPublicId, locationPublicId, quantity, unitPriceAmount));
        order.setMoney(Math.multiplyExact(quantity, unitPriceAmount), 0);
        return order;
    }

    static CustomerOrder createCheckout(UUID ownerAccountPublicId, UUID branchPublicId, UUID reservationPublicId,
            UUID quoteId, String idempotencyKey, UUID priceVersionId, UUID variantPublicId, UUID locationPublicId,
            String sku, String size, long quantity, long unitPriceAmount, Instant now) {
        CustomerOrder order = new CustomerOrder();
        order.publicId = UUID.randomUUID();
        order.ownerAccountPublicId = ownerAccountPublicId;
        order.responsibleBranchPublicId = branchPublicId;
        order.reservationPublicId = reservationPublicId;
        order.priceQuotePublicId = quoteId;
        order.checkoutIdempotencyKey = idempotencyKey;
        order.priceVersionPublicId = priceVersionId;
        order.channel = Channel.ONLINE;
        order.currency = "VND";
        order.status = Status.PENDING_PAYMENT;
        order.createdAt = now;
        order.items.add(OrderItem.createCheckout(order, variantPublicId, locationPublicId, sku, size, quantity, unitPriceAmount));
        order.setMoney(Math.multiplyExact(quantity, unitPriceAmount), 0);
        return order;
    }

    public static CustomerOrder createPos(UUID branchPublicId, UUID priceVersionId, UUID variantPublicId,
            UUID locationPublicId, String sku, String size, long unitPriceAmount, Instant now) {
        if (branchPublicId == null || priceVersionId == null || now == null) {
            throw new IllegalArgumentException("POS Order evidence is invalid");
        }
        CustomerOrder order = new CustomerOrder();
        order.publicId = UUID.randomUUID();
        order.responsibleBranchPublicId = branchPublicId;
        order.priceVersionPublicId = priceVersionId;
        order.channel = Channel.POS;
        order.currency = "VND";
        order.status = Status.PAID;
        order.createdAt = now;
        order.paidAt = now;
        order.items.add(OrderItem.createCheckout(order, variantPublicId, locationPublicId,
                sku, size, 1, unitPriceAmount));
        order.setMoney(unitPriceAmount, 0);
        return order;
    }

    static CustomerOrder createCart(UUID ownerId, UUID branchId, UUID quoteId, String key, String fingerprint,
            List<ItemFacts> lines, List<UUID> priceVersionIds, long merchandise,long merchandiseDiscount, long shippingFee,long shippingDiscount,
            UUID shippingRuleId, String shippingZone, UUID shippingOriginLocation, UUID shippingOriginBranch,
            String destinationProvince, String destinationDistrict, Instant shippingQuotedAt, Instant now) {
        if (ownerId == null || branchId == null || quoteId == null || now == null || lines.isEmpty()
                || lines.size() != priceVersionIds.size()
                || lines.stream().map(ItemFacts::variantId).distinct().count() != lines.size()
                || lines.stream().map(ItemFacts::locationId).distinct().count() != 1) {
            throw new IllegalArgumentException("Cart order evidence is invalid");
        }
        CustomerOrder order = new CustomerOrder();
        order.publicId = UUID.randomUUID(); order.ownerAccountPublicId = ownerId;
        order.responsibleBranchPublicId = branchId; order.cartQuotePublicId = quoteId;
        order.checkoutIdempotencyKey = key; order.checkoutFingerprint = fingerprint;
        order.channel = Channel.ONLINE; order.currency = "VND";
        order.status = Status.PENDING_PAYMENT; order.createdAt = now;
        for (int index = 0; index < lines.size(); index++) {
            order.items.add(OrderItem.createCart(order, lines.get(index), priceVersionIds.get(index)));
        }
        order.shippingRulePublicId=shippingRuleId; order.shippingZoneCode=shippingZone;
        order.shippingOriginLocationId=shippingOriginLocation; order.shippingOriginBranchId=shippingOriginBranch;
        order.destinationProvinceCode=destinationProvince; order.destinationDistrictCode=destinationDistrict;
        order.shippingQuotedAt=shippingRuleId==null?null:shippingQuotedAt;
        order.setMoney(merchandise,merchandiseDiscount,shippingFee,shippingDiscount);
        order.totalAmount();
        return order;
    }

    private void setMoney(long merchandise,long shipping){
        setMoney(merchandise,0,shipping,0);
    }
    private void setMoney(long merchandise,long merchandiseDiscount,long shipping,long shippingDiscount){
        long payable=Math.addExact(merchandise-merchandiseDiscount,shipping-shippingDiscount);
        if(merchandise<=0||shipping<0||payable>VariantPrice.MAX_AMOUNT) throw new IllegalArgumentException("Order total exceeds supported range");
        merchandiseAmount=BigDecimal.valueOf(merchandise);merchandiseDiscountAmount=BigDecimal.valueOf(merchandiseDiscount); shippingFeeAmount=BigDecimal.valueOf(shipping);shippingDiscountAmount=BigDecimal.valueOf(shippingDiscount); total=BigDecimal.valueOf(payable);
    }

    boolean cancel(Instant now) { if (status == Status.CANCELLED) return false; if (status != Status.PENDING_PAYMENT) throw new IllegalStateException("Order is not cancellable"); status = Status.CANCELLED; cancelledAt = now; return true; }
    public void cancelPaid(Instant now) { if (status != Status.PAID) throw new IllegalStateException("Paid Order is not cancellable"); status = Status.CANCELLED; cancelledAt = now; }
    public void expire(Instant now) { if (status != Status.PENDING_PAYMENT) throw new IllegalStateException("Order is not pending payment"); status = Status.CANCELLED; cancelledAt = now; }
    public void markPaid(Instant now) { if (status != Status.PENDING_PAYMENT) throw new IllegalStateException("Order is not pending payment"); status = Status.PAID; paidAt = now; }
    public String paymentStatus() { return status.name(); }
    boolean cancelled() { return status == Status.CANCELLED; }
    boolean pendingPayment() { return status == Status.PENDING_PAYMENT; }
    public UUID publicId() { return publicId; }
    UUID ownerAccountPublicId() { return ownerAccountPublicId; }
    UUID responsibleBranchPublicId() { return responsibleBranchPublicId; }
    UUID reservationPublicId() { return reservationPublicId; }
    String currency() { return currency; }
    String status() { return status.name(); }
    Instant createdAt() { return createdAt; }
    Instant cancelledAt() { return cancelledAt; }
    Instant paidAt() { return paidAt; }
    OrderItem item() { if (items.size() != 1) throw new IllegalStateException("Single-item contract requires one line"); return items.getFirst(); }
    List<OrderItem> items() { return List.copyOf(items); }
    UUID cartQuotePublicId() { return cartQuotePublicId; }
    public boolean cartCheckout() { return cartQuotePublicId != null; }
    String checkoutFingerprint() { return checkoutFingerprint; }
    long totalAmount() {
        long itemsTotal = 0;
        for (OrderItem item : items) itemsTotal = Math.addExact(itemsTotal, item.totalAmount());
        if (merchandiseAmount.longValueExact()!=itemsTotal || total.longValueExact()!=Math.addExact(itemsTotal-merchandiseDiscountAmount.longValueExact(),shippingFeeAmount.longValueExact()-shippingDiscountAmount.longValueExact()))
            throw new IllegalStateException("Order monetary snapshot is inconsistent");
        return total.longValueExact();
    }
    public long merchandiseAmount(){return merchandiseAmount.longValueExact();}
    public long shippingFeeAmount(){return shippingFeeAmount.longValueExact();}
    public long merchandiseDiscountAmount(){return merchandiseDiscountAmount.longValueExact();}
    public long shippingDiscountAmount(){return shippingDiscountAmount.longValueExact();}
    UUID priceQuotePublicId() { return priceQuotePublicId; }
    String checkoutIdempotencyKey() { return checkoutIdempotencyKey; }
    UUID priceVersionPublicId() { return priceVersionPublicId; }
    public PaymentFacts paymentFacts() { return new PaymentFacts(publicId, ownerAccountPublicId, responsibleBranchPublicId,
            items.stream().map(OrderItem::facts).toList(), status == Status.PENDING_PAYMENT, status == Status.PAID,
            merchandiseAmount(), shippingFeeAmount(), totalAmount(), currency, paidAt); }
    public ReceiptFacts receiptFacts() { OrderItem item = item(); return new ReceiptFacts(publicId, responsibleBranchPublicId,
            priceVersionPublicId, channel.name(), status.name(), createdAt, paidAt, item.variantPublicId(),
            item.locationPublicId(), item.skuSnapshot(), item.sizeSnapshot(), item.quantity(),
            item.unitPriceAmount(), item.totalAmount(), currency); }
    public record ItemFacts(UUID orderItemId, UUID reservationId, UUID variantId, UUID locationId,
            long quantity, String sku, String size, String color, long unitPriceAmount, long totalAmount) { }
    public record PaymentFacts(UUID orderId, UUID ownerAccountId, UUID responsibleBranchId, List<ItemFacts> items,
            boolean pendingPayment, boolean paid, long merchandiseAmount, long shippingFeeAmount, long totalAmount,
            String currency, Instant paidAt) {
        public List<UUID> reservationIds() { return items.stream().map(ItemFacts::reservationId).toList(); }
        public UUID locationId() { return items.getFirst().locationId(); }
        private ItemFacts single() { if (items.size() != 1) throw new IllegalStateException("Single-item contract requires one line"); return items.getFirst(); }
        public UUID reservationId() { return single().reservationId(); }
        public UUID orderItemId() { return single().orderItemId(); }
        public UUID variantId() { return single().variantId(); }
        public long quantity() { return items.stream().mapToLong(ItemFacts::quantity).sum(); }
        public String sku() { return single().sku(); }
        public String size() { return single().size(); }
    }
    public record ReceiptFacts(UUID orderId, UUID responsibleBranchId, UUID priceVersionId, String channel,
            String status, Instant createdAt, Instant paidAt, UUID variantId, UUID locationId, String sku,
            String size, long quantity, long unitPrice, long total, String currency) { }
}
