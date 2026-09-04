package com.shoecommerce.order;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "commerce_order_item")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private CustomerOrder order;
    @Column(name = "variant_public_id", nullable = false) private UUID variantPublicId;
    @Column(name = "location_public_id", nullable = false) private UUID locationPublicId;
    @Column(nullable = false) private long quantity;
    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 0) private BigDecimal unitPriceAmount;
    @Column(name = "sku_snapshot", length = 64) private String skuSnapshot;
    @Column(name = "size_snapshot", length = 32) private String sizeSnapshot;
    @Column(name = "color_snapshot", length = 64) private String colorSnapshot;
    @Column(name = "reservation_public_id") private UUID reservationPublicId;
    @Column(name = "price_version_public_id") private UUID priceVersionPublicId;

    protected OrderItem() { }

    static OrderItem create(CustomerOrder order, UUID variantPublicId, UUID locationPublicId, long quantity, long unitPriceAmount) {
        if (order == null || variantPublicId == null || locationPublicId == null || quantity <= 0 || unitPriceAmount <= 0) throw new IllegalArgumentException("Order item is invalid");
        OrderItem item = new OrderItem();
        item.publicId = UUID.randomUUID();
        item.order = order;
        item.variantPublicId = variantPublicId;
        item.locationPublicId = locationPublicId;
        item.quantity = quantity;
        item.unitPriceAmount = BigDecimal.valueOf(unitPriceAmount);
        item.reservationPublicId = order.reservationPublicId();
        item.priceVersionPublicId = order.priceVersionPublicId();
        return item;
    }

    static OrderItem createCheckout(CustomerOrder order, UUID variantPublicId, UUID locationPublicId,
            String sku, String size, long quantity, long unitPriceAmount) {
        OrderItem item = create(order, variantPublicId, locationPublicId, quantity, unitPriceAmount);
        if (sku == null || sku.isBlank() || size == null || size.isBlank()) throw new IllegalArgumentException("Order catalog snapshot is invalid");
        item.skuSnapshot = sku;
        item.sizeSnapshot = size;
        return item;
    }

    UUID variantPublicId() { return variantPublicId; }
    UUID publicId() { return publicId; }
    UUID locationPublicId() { return locationPublicId; }
    long quantity() { return quantity; }
    long unitPriceAmount() { return unitPriceAmount.longValueExact(); }
    long totalAmount() { return Math.multiplyExact(unitPriceAmount(), quantity); }
    String skuSnapshot() { return skuSnapshot; }
    String sizeSnapshot() { return sizeSnapshot; }
    String colorSnapshot() { return colorSnapshot; }
    UUID reservationPublicId() { return reservationPublicId; }
    UUID priceVersionPublicId() { return priceVersionPublicId; }
    static OrderItem createCart(CustomerOrder order, CustomerOrder.ItemFacts line, UUID priceVersionId) {
        OrderItem item = createCheckout(order, line.variantId(), line.locationId(), line.sku(), line.size(),
                line.quantity(), line.unitPriceAmount());
        if (line.reservationId() == null || priceVersionId == null) throw new IllegalArgumentException("Cart item evidence is required");
        item.reservationPublicId = line.reservationId();
        item.priceVersionPublicId = priceVersionId;
        item.colorSnapshot = line.color();
        return item;
    }
    CustomerOrder.ItemFacts facts() {
        return new CustomerOrder.ItemFacts(publicId, reservationPublicId, variantPublicId, locationPublicId,
                quantity, skuSnapshot, sizeSnapshot, colorSnapshot, unitPriceAmount(), totalAmount());
    }
}
