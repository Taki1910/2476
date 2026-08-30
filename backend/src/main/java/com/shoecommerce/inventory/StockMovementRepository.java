package com.shoecommerce.inventory;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    long countByTypeAndOrderId(StockMovement.Type type, UUID orderId);
}
