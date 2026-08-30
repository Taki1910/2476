package com.shoecommerce.inventory;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.shoecommerce.branch.Location;
import com.shoecommerce.catalog.ProductVariant;
import jakarta.persistence.LockModeType;
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    Optional<InventoryBalance> findByVariantAndLocation(ProductVariant variant, Location location);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from InventoryBalance balance where balance.variant = :variant and balance.location = :location")
    Optional<InventoryBalance> findLockedByVariantAndLocation(@Param("variant") ProductVariant variant, @Param("location") Location location);
    boolean existsByVariantAndOnHandGreaterThan(ProductVariant variant, long onHand);
    @Query("""
            select count(balance) from InventoryBalance balance
            where balance.variant.publicId = :variantId
              and balance.location.enabled = true
              and balance.location.branch.enabled = true
              and balance.onHand > balance.reserved
            """)
    long countCustomerAvailable(@Param("variantId") UUID variantId);
    @Query("""
            select balance.location from InventoryBalance balance
            where balance.variant = :variant
              and balance.location.enabled = true
              and balance.location.branch.enabled = true
            order by balance.location.id
            """)
    List<Location> findCheckoutLocations(@Param("variant") ProductVariant variant);
}
