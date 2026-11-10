package com.shoecommerce.inventory;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_transfer_command", uniqueConstraints =
        @UniqueConstraint(name = "UQ_inventory_transfer_command_actor_key",
                columnNames = {"actor_account_public_id", "idempotency_key"}))
class InventoryTransferCommand {
    enum Action { CREATE, DISPATCH, RECEIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @Column(name = "transfer_public_id", nullable = false) private UUID transferId;
    @Column(name = "actor_account_public_id", nullable = false) private UUID actorId;
    @Nationalized @Column(name = "idempotency_key", nullable = false, length = 128) private String key;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Action action;
    @Nationalized @Column(name = "request_fingerprint", nullable = false, length = 512) private String fingerprint;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected InventoryTransferCommand() { }

    static InventoryTransferCommand create(UUID transferId, UUID actorId, String key, Action action,
            String fingerprint, Instant now) {
        if (transferId == null || actorId == null || key == null || key.isBlank() || key.length() > 128
                || action == null || fingerprint == null || fingerprint.isBlank() || fingerprint.length() > 512
                || now == null) throw new IllegalArgumentException("Inventory transfer command is invalid");
        InventoryTransferCommand command = new InventoryTransferCommand();
        command.publicId = UUID.randomUUID();
        command.transferId = transferId;
        command.actorId = actorId;
        command.key = key;
        command.action = action;
        command.fingerprint = fingerprint;
        command.createdAt = now;
        return command;
    }

    boolean matches(Action expectedAction, String expectedFingerprint) {
        return action == expectedAction && fingerprint.equals(expectedFingerprint);
    }
    UUID transferId() { return transferId; }
}
