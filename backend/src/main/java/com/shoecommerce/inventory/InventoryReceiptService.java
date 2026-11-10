package com.shoecommerce.inventory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.catalog.ProductVariantRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class InventoryReceiptService {
    private final InventoryBalanceRepository balances;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;
    private final LocationRepository locations;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    public InventoryReceiptService(InventoryBalanceRepository balances, StockMovementRepository movements,
            ProductVariantRepository variants, LocationRepository locations, UserAccountRepository accounts,
            AuthorizationPolicy authorization, AuditWriter audit, Clock clock) {
        this.balances = balances;
        this.movements = movements;
        this.variants = variants;
        this.locations = locations;
        this.accounts = accounts;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReceiptResult receive(SessionPrincipal actor, UUID variantId, UUID locationId, long quantity,
            String note, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.INVENTORY_ADJUST);
        if (variantId == null || locationId == null || quantity <= 0) {
            throw new InvalidRequestException("INVALID_INVENTORY_RECEIPT",
                    "Variant, Location and a positive receipt quantity are required.");
        }
        authorization.requireLocationAccess(actor, locationId);
        String key = required(idempotencyKey, 128, "Idempotency-Key");
        String normalizedNote = optional(note, 256, "Receipt note");
        String fingerprint = variantId + "|" + locationId + "|" + quantity + "|"
                + (normalizedNote == null ? "" : normalizedNote);
        String operationKey = "R:" + UUID.nameUUIDFromBytes(
                (actor.publicId() + ":" + key).getBytes(StandardCharsets.UTF_8));

        accounts.findByPublicIdForUpdate(actor.publicId())
                .orElseThrow(() -> new IllegalStateException("Inventory receipt account not found"));
        StockMovement replay = movements.findByOperationKey(operationKey).orElse(null);
        if (replay != null) {
            if (!replay.matchesReceipt(fingerprint)) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT",
                        "This idempotency key was already used for a different inventory receipt.");
            }
            return view(replay, false);
        }

        ProductVariant variant = variants.findLockedByPublicId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("VARIANT_NOT_FOUND", "Variant not found."));
        Location location = locations.findByPublicId(locationId).filter(Location::enabled)
                .orElseThrow(() -> new ResourceNotFoundException("LOCATION_NOT_FOUND", "Location not found or disabled."));
        Instant now = clock.instant();
        InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location)
                .orElseGet(() -> InventoryBalance.create(variant, location, 0, now));
        long beforeOnHand = balance.onHand();
        long reserved = balance.reserved();
        try {
            balance.receive(quantity, now);
        } catch (ArithmeticException overflow) {
            throw new InvalidRequestException("INVENTORY_QUANTITY_OVERFLOW", "Receipt quantity is too large.");
        }
        balances.saveAndFlush(balance);

        StockMovement movement = movements.saveAndFlush(StockMovement.createReceipt(operationKey, fingerprint,
                normalizedNote, actor.publicId(), variantId, locationId, quantity, beforeOnHand, balance.onHand(),
                reserved, now));
        Map<String, Object> details = new HashMap<>();
        details.put("variantId", variantId);
        details.put("quantity", quantity);
        details.put("beforeOnHand", beforeOnHand);
        details.put("afterOnHand", balance.onHand());
        details.put("reserved", reserved);
        details.put("available", balance.available());
        if (normalizedNote != null) details.put("note", normalizedNote);
        audit.append(actor, "INVENTORY_RECEIVED", "STOCK_MOVEMENT", movement.publicId(), location.branchId(),
                location.id(), details);
        return view(movement, true);
    }

    private static ReceiptResult view(StockMovement movement, boolean created) {
        return new ReceiptResult(movement.publicId(), movement.variantId(), movement.locationId(), movement.quantity(),
                movement.beforeOnHand(), movement.afterOnHand(), movement.reservedAtOperation(),
                movement.afterOnHand() - movement.reservedAtOperation(), movement.onHandDelta(),
                movement.reservedDelta(), movement.reason(), created);
    }

    private static String required(String value, int max, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new InvalidRequestException("INVALID_INVENTORY_RECEIPT", name + " must contain 1 to " + max + " characters.");
        }
        return normalized;
    }

    private static String optional(String value, int max, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new InvalidRequestException("INVALID_INVENTORY_RECEIPT", name + " must contain at most " + max + " characters.");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    public record ReceiptResult(UUID movementId, UUID variantId, UUID locationId, long quantity,
            long beforeOnHand, long afterOnHand, long reserved, long available, long onHandDelta,
            long reservedDelta, String note, boolean created) { }
}
