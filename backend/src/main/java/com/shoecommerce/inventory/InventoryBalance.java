package com.shoecommerce.inventory;

import java.time.Instant;
import com.shoecommerce.branch.Location;
import com.shoecommerce.catalog.ProductVariant;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory_balance")
public class InventoryBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "variant_id") private ProductVariant variant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id") private Location location;
    @Column(name = "on_hand", nullable = false) private long onHand;
    @Column(nullable = false) private long reserved;
    @Version @Column(name = "entity_version", nullable = false) private long version;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected InventoryBalance() { }
    public static InventoryBalance create(ProductVariant variant, Location location, long onHand, Instant now) { InventoryBalance balance = new InventoryBalance(); balance.variant = variant; balance.location = location; balance.setOnHand(onHand, now); return balance; }
    public void setOnHand(long onHand, Instant now) { if (onHand < reserved) throw new IllegalArgumentException("Stock cannot be below reserved quantity"); this.onHand = onHand; this.updatedAt = now; }
    public void reserve(long quantity, Instant now) { if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive"); if (quantity > available()) throw new IllegalStateException("Insufficient available stock"); reserved += quantity; updatedAt = now; }
    public void release(long quantity, Instant now) { if (quantity <= 0 || quantity > reserved) throw new IllegalStateException("Reserved quantity cannot be released"); reserved -= quantity; updatedAt = now; }
    public void consume(long quantity, Instant now) { if (quantity <= 0 || quantity > reserved || quantity > onHand) throw new IllegalStateException("Reserved inventory cannot be consumed"); onHand -= quantity; reserved -= quantity; updatedAt = now; }
    public void issueAvailable(long quantity, Instant now) { if (quantity <= 0 || quantity > available()) throw new IllegalStateException("Insufficient available stock"); onHand -= quantity; updatedAt = now; }
    public long onHand() { return onHand; }
    public long reserved() { return reserved; }
    public long available() { return onHand - reserved; }
}
