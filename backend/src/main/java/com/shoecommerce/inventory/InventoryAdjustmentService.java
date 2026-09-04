package com.shoecommerce.inventory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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

@Service
public class InventoryAdjustmentService {
    private final InventoryBalanceRepository balances;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;
    private final LocationRepository locations;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    public InventoryAdjustmentService(InventoryBalanceRepository balances, StockMovementRepository movements,
            ProductVariantRepository variants, LocationRepository locations, UserAccountRepository accounts,
            AuthorizationPolicy authorization, AuditWriter audit, Clock clock) {
        this.balances = balances; this.movements = movements; this.variants = variants;
        this.locations = locations; this.accounts = accounts; this.authorization = authorization;
        this.audit = audit; this.clock = clock;
    }

    @Transactional
    public AdjustmentResult adjust(SessionPrincipal actor, UUID variantId, UUID locationId, long targetOnHand,
            String reason, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.INVENTORY_ADJUST);
        if (variantId == null || locationId == null || targetOnHand < 0) {
            throw new InvalidRequestException("INVALID_INVENTORY_ADJUSTMENT", "Variant, Location and a non-negative target on-hand quantity are required.");
        }
        authorization.requireLocationAccess(actor, locationId);
        String key = normalize(idempotencyKey, 128, "Idempotency-Key");
        String normalizedReason = normalize(reason, 256, "Adjustment reason");
        String fingerprint = variantId + "|" + locationId + "|" + targetOnHand + "|" + normalizedReason;
        String operationKey = "A:" + UUID.nameUUIDFromBytes((actor.publicId() + ":" + key).getBytes(StandardCharsets.UTF_8));

        accounts.findByPublicIdForUpdate(actor.publicId())
                .orElseThrow(() -> new IllegalStateException("Inventory adjustment account not found"));
        StockMovement replay = movements.findByOperationKey(operationKey).orElse(null);
        if (replay != null) {
            if (!replay.matchesAdjustment(fingerprint)) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key was already used for a different inventory adjustment.");
            }
            return view(replay, false);
        }

        ProductVariant variant = variants.findLockedByPublicId(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found"));
        Location location = locations.findByPublicId(locationId).filter(Location::enabled)
                .orElseThrow(() -> new IllegalArgumentException("Location not found or disabled"));
        Instant now = clock.instant();
        InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location)
                .orElseGet(() -> InventoryBalance.create(variant, location, 0, now));
        long before = balance.onHand();
        balance.setOnHand(targetOnHand, now);
        balances.save(balance);

        StockMovement movement = movements.saveAndFlush(StockMovement.createAdjustment(operationKey, fingerprint,
                normalizedReason, actor.publicId(), variantId, locationId, before, targetOnHand, now));
        audit.append(actor, "INVENTORY_ADJUSTED", "STOCK_MOVEMENT", movement.publicId(),
                location.branchId(), location.id(), Map.of("variantId", variantId, "beforeOnHand", before,
                        "afterOnHand", targetOnHand, "onHandDelta", movement.onHandDelta(),
                        "reservedDelta", 0, "reason", normalizedReason));
        return view(movement, true);
    }

    private static String normalize(String value, int max, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new InvalidRequestException("INVALID_INVENTORY_ADJUSTMENT", name + " must contain 1 to " + max + " characters.");
        }
        return normalized;
    }

    private static AdjustmentResult view(StockMovement movement, boolean created) {
        return new AdjustmentResult(movement.publicId(), movement.variantId(), movement.locationId(),
                movement.beforeOnHand(), movement.afterOnHand(), movement.onHandDelta(),
                movement.reservedDelta(), movement.reason(), created);
    }

    public record AdjustmentResult(UUID movementId, UUID variantId, UUID locationId, long beforeOnHand,
            long afterOnHand, long onHandDelta, long reservedDelta, String reason, boolean created) { }
}
