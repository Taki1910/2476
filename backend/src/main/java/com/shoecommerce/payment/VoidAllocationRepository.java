package com.shoecommerce.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface VoidAllocationRepository extends JpaRepository<VoidAllocation, Long> {
    List<VoidAllocation> findAllByAttempt(VoidAttempt attempt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from VoidAllocation allocation where allocation.attempt = :attempt order by allocation.id")
    List<VoidAllocation> findLockedByAttempt(@Param("attempt") VoidAttempt attempt);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payment_void_allocation WHERE component_public_id = :componentId AND status IN ('ACTIVE', 'SUCCEEDED')", nativeQuery = true)
    BigDecimal usedCapacity(@Param("componentId") UUID componentId);
}
