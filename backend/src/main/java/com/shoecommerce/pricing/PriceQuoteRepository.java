package com.shoecommerce.pricing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PriceQuoteRepository extends JpaRepository<PriceQuote, Long> {
    Optional<PriceQuote> findByPublicId(UUID publicId);
}
