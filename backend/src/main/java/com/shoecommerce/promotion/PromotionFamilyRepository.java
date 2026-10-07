package com.shoecommerce.promotion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionFamilyRepository extends JpaRepository<PromotionFamily, UUID> { }
