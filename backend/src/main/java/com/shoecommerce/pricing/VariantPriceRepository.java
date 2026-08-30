package com.shoecommerce.pricing;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.shoecommerce.catalog.ProductVariant;
public interface VariantPriceRepository extends JpaRepository<VariantPrice, Long> {
    @Query("select price from VariantPrice price where price.variant = :variant and price.validTo is null")
    Optional<VariantPrice> findByVariant(@Param("variant") ProductVariant variant);
    @Query("""
            select price from VariantPrice price
            where price.variant.publicId = :variantId
              and price.variant.status = com.shoecommerce.catalog.ProductVariant$Status.PUBLISHED
              and price.validFrom <= :at
              and (price.validTo is null or price.validTo > :at)
            """)
    Optional<VariantPrice> findEffectivePublished(@Param("variantId") UUID variantId, @Param("at") Instant at);
}
