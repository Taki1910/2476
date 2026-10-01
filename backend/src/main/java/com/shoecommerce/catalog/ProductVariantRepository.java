package com.shoecommerce.catalog;
import java.util.List; import java.util.Optional; import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    interface ProductColor { UUID getProductId(); String getColor(); }
    Optional<ProductVariant> findByPublicId(UUID publicId);
    Optional<ProductVariant> findBySku(String sku);
    Optional<ProductVariant> findByBarcode(String barcode);
    @Query("select distinct variant.color from ProductVariant variant where variant.product = :product order by variant.color")
    List<String> findDistinctColorsByProduct(@Param("product") Product product);
    @Query("select distinct variant.product.publicId as productId, variant.color as color from ProductVariant variant order by variant.product.publicId, variant.color")
    List<ProductColor> findDistinctProductColors();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select variant from ProductVariant variant where variant.publicId = :publicId")
    Optional<ProductVariant> findLockedByPublicId(@Param("publicId") UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select variant from ProductVariant variant where variant.publicId in :publicIds order by variant.id")
    List<ProductVariant> findAllLockedByPublicId(@Param("publicIds") List<UUID> publicIds);
}
