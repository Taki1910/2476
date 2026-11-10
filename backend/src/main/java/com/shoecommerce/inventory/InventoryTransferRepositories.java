package com.shoecommerce.inventory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long> {
    @EntityGraph(attributePaths = "lines")
    Optional<InventoryTransfer> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select transfer from InventoryTransfer transfer where transfer.publicId = :publicId")
    Optional<InventoryTransfer> findLockedByPublicId(@Param("publicId") UUID publicId);
}

interface InventoryTransferCommandRepository extends JpaRepository<InventoryTransferCommand, Long> {
    Optional<InventoryTransferCommand> findByActorIdAndKey(UUID actorId, String key);
}
