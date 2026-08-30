package com.shoecommerce.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shoecommerce.order.CustomerOrder;

import jakarta.persistence.LockModeType;

interface PosRegisterRepository extends JpaRepository<PosRegister, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosRegister> findLockedByPublicId(UUID publicId);

    @Query(value = """
            SELECT DISTINCT registers.*
            FROM pos_register registers
            JOIN org_location locations ON locations.id = registers.location_id
            JOIN org_branch branches ON branches.id = locations.branch_id
            JOIN iam_staff_assignment assignments
              ON assignments.location_id = locations.id AND assignments.branch_id = branches.id
            WHERE assignments.account_id = :accountId AND assignments.active = 1
              AND registers.enabled = 1 AND locations.enabled = 1 AND branches.enabled = 1
            ORDER BY registers.code
            """, nativeQuery = true)
    List<PosRegister> findAccessible(@Param("accountId") long accountId);
}

interface CashierShiftRepository extends JpaRepository<CashierShift, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CashierShift> findLockedByPublicId(UUID publicId);
    Optional<CashierShift> findByPublicId(UUID publicId);
    Optional<CashierShift> findByCashierAccountIdAndStatus(long cashierAccountId, CashierShift.Status status);
    boolean existsByRegisterAndStatus(PosRegister register, CashierShift.Status status);
}

interface PosCashSaleRepository extends JpaRepository<PosCashSale, Long> {
    Optional<PosCashSale> findByShiftAndIdempotencyKey(CashierShift shift, String idempotencyKey);
    @Query("select sale from PosCashSale sale where sale.order.publicId = :orderId and sale.cashierAccountId = :cashierId")
    Optional<PosCashSale> findOwnedReceipt(@Param("orderId") UUID orderId, @Param("cashierId") long cashierId);
}

interface CashTenderRepository extends JpaRepository<CashTender, Long> {
    Optional<CashTender> findByOrder(CustomerOrder order);
    @Query("select coalesce(sum(tender.amount), 0) from CashTender tender where tender.shift = :shift")
    BigDecimal expectedCash(@Param("shift") CashierShift shift);
}
