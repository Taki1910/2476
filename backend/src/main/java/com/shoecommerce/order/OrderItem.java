package com.shoecommerce.order;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "commerce_order_item")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true) private CustomerOrder order;
    @Column(name = "variant_public_id", nullable = false) private UUID variantPublicId;
    @Column(name = "location_public_id", nullable = false) private UUID locationPublicId;
    @Column(nullable = false) private long quantity;
    @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 0) private BigDecimal unitPriceAmount;
    @Column(name = "sku_snapshot", length = 64) private String skuSnapshot;
    @Column(name = "size_snapshot", length = 32) private String sizeSnapshot;

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
}
