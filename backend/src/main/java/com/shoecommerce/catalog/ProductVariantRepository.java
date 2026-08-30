package com.shoecommerce.catalog;
import java.util.Optional; import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByPublicId(UUID publicId);
    Optional<ProductVariant> findBySku(String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select variant from ProductVariant variant where variant.publicId = :publicId")
    Optional<ProductVariant> findLockedByPublicId(@Param("publicId") UUID publicId);
}
