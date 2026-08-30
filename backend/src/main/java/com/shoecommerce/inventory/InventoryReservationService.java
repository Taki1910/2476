package com.shoecommerce.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Location;
import com.shoecommerce.branch.LocationRepository;
import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.catalog.ProductVariantRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.OwnershipPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.pricing.VariantPriceRepository;

@Service
public class InventoryReservationService {
    private final InventoryReservationRepository reservations;
    private final InventoryBalanceRepository balances;
    private final ProductVariantRepository variants;
    private final VariantPriceRepository prices;
    private final LocationRepository locations;
    private final AuthorizationPolicy authorization;
    private final OwnershipPolicy ownership;
    private final AuditWriter audit;
    private final Clock clock;
    private final Duration checkoutReservationTtl;

    public InventoryReservationService(InventoryReservationRepository reservations, InventoryBalanceRepository balances,
            ProductVariantRepository variants, VariantPriceRepository prices, LocationRepository locations,
            AuthorizationPolicy authorization, OwnershipPolicy ownership, AuditWriter audit, Clock clock,
            @Value("${commerce.checkout.reservation-ttl}") Duration checkoutReservationTtl) {
        if (checkoutReservationTtl.isZero() || checkoutReservationTtl.isNegative()) {
            throw new IllegalArgumentException("Checkout reservation TTL must be positive");
        }
        this.reservations = reservations; this.balances = balances; this.variants = variants; this.prices = prices;
        this.locations = locations; this.authorization = authorization; this.ownership = ownership; this.audit = audit; this.clock = clock;
        this.checkoutReservationTtl = checkoutReservationTtl;
    }

    @Transactional
    public ReservationView reserve(SessionPrincipal actor, UUID variantId, UUID locationId, long quantity) {
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive");
        ProductVariant variant = variants.findByPublicId(variantId).orElseThrow(() -> new IllegalArgumentException("Variant not found"));
        if (!variant.published()) throw new IllegalStateException("Variant is not published");
        if (prices.findByVariant(variant).filter(price -> price.amount() > 0).isEmpty()) throw new IllegalStateException("Variant has no valid current price");
        Location location = locations.findByPublicId(locationId).filter(Location::enabled).orElseThrow(() -> new IllegalArgumentException("Location not found or disabled"));
        InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location).orElseThrow(() -> new IllegalStateException("Insufficient available stock"));
        balance.reserve(quantity, clock.instant());
        InventoryReservation reservation = reservations.save(InventoryReservation.create(actor.publicId(), variant, location, quantity, clock.instant()));
        audit.append(actor, "INVENTORY_RESERVED", "INVENTORY_RESERVATION", reservation.publicId(), location.branchId(), location.id(), Map.of("variantId", variantId, "quantity", quantity));
        return view(reservation);
    }

    @Transactional
    public Adoption reserveForCheckout(SessionPrincipal actor, ProductVariant variant, long quantity) {
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive");
        for (Location location : balances.findCheckoutLocations(variant)) {
            InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location).orElseThrow();
            if (balance.available() < quantity) continue;
            Instant now = clock.instant();
            balance.reserve(quantity, now);
            InventoryReservation reservation = InventoryReservation.createCheckout(actor.publicId(), variant, location,
                    quantity, now, now.plus(checkoutReservationTtl));
            reservation.adopt(now);
            reservations.save(reservation);
            audit.append(actor, "INVENTORY_RESERVED", "INVENTORY_RESERVATION", reservation.publicId(),
                    location.branchId(), location.id(), Map.of("variantId", variant.publicId(), "quantity", quantity));
            return new Adoption(reservation.publicId(), actor.publicId(), variant.publicId(), location.publicId(),
                    location.branchPublicId(), quantity);
        }
        throw new BusinessConflictException("INSUFFICIENT_STOCK", "This variant is no longer available in the requested quantity.");
    }

    @Transactional
    public void expireAdoptedForOrder(UUID reservationId, Instant now) {
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location())
                .orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
        reservation.expire(now);
        balance.release(reservation.quantity(), now);
    }

    @Transactional(readOnly = true)
    public ReservationView readOwn(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        InventoryReservation reservation = reservation(reservationId);
        ownership.requireOwnership(actor, reservation.ownerAccountPublicId());
        return view(reservation);
    }

    @Transactional
    public ReservationView releaseOwn(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId).orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        ownership.requireOwnership(actor, reservation.ownerAccountPublicId());
        if (reservation.adopted()) throw new BusinessConflictException("Adopted reservation cannot be released independently");
        if (reservation.active()) {
            InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location()).orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
            balance.release(reservation.quantity(), clock.instant());
            reservation.release(clock.instant());
            audit.append(actor, "INVENTORY_RESERVATION_RELEASED", "INVENTORY_RESERVATION", reservation.publicId(), reservation.location().branchId(), reservation.location().id(), Map.of("quantity", reservation.quantity()));
        }
        return view(reservation);
    }

    @Transactional
    public Adoption adoptForOrder(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId).orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        ownership.requireOwnership(actor, reservation.ownerAccountPublicId());
        if (!reservation.active()) throw new BusinessConflictException("Reservation is not adoptable");
        reservation.adopt(clock.instant());
        return new Adoption(reservation.publicId(), reservation.ownerAccountPublicId(), reservation.variant().publicId(), reservation.location().publicId(), reservation.location().branchPublicId(), reservation.quantity());
    }

    @Transactional
    public void releaseAdoptedForCancelledOrder(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId).orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        ownership.requireOwnership(actor, reservation.ownerAccountPublicId());
        if (!reservation.adopted()) throw new BusinessConflictException("Reservation is not adopted by a pending order");
        InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location()).orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
        balance.release(reservation.quantity(), clock.instant());
        reservation.releaseAdopted(clock.instant());
    }

    @Transactional(readOnly = true)
    public Instant requireAdoptedForPayment(SessionPrincipal actor, UUID reservationId, Instant now) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_INITIATE);
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        ownership.requireOwnership(actor, reservation.ownerAccountPublicId());
        if (!reservation.adopted()) throw new BusinessConflictException("Order reservation is not adopted");
        if (reservation.expiresAt() != null && !now.isBefore(reservation.expiresAt())) {
            throw new BusinessConflictException("RESERVATION_EXPIRED", "Order reservation has expired.");
        }
        return reservation.expiresAt() == null ? now.plus(checkoutReservationTtl) : reservation.expiresAt();
    }

    @Transactional
    public PaymentCommit commitForSuccessfulPayment(UUID reservationId, Instant now) {
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.adopted()) return PaymentCommit.INELIGIBLE;
        if (reservation.expiresAt() != null && !now.isBefore(reservation.expiresAt())) {
            InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location())
                    .orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
            reservation.expire(now);
            balance.release(reservation.quantity(), now);
            return PaymentCommit.EXPIRED_RELEASED;
        }
        reservation.commit(now);
        return PaymentCommit.COMMITTED;
    }

    @Transactional
    public Consumption consumeForSuccessfulPayment(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_EVENT_APPLY);
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId).orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.adopted()) throw new BusinessConflictException("Order reservation is not adopted");
        InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location()).orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
        long beforeOnHand = balance.onHand();
        long beforeReserved = balance.reserved();
        balance.consume(reservation.quantity(), clock.instant());
        reservation.consume(clock.instant());
        return new Consumption(reservation.quantity(), beforeOnHand, beforeReserved, balance.onHand(), balance.reserved());
    }

    @Transactional(readOnly = true)
    public void requireConsumedForFulfillment(UUID reservationId, UUID locationId) {
        InventoryReservation reservation = reservation(reservationId);
        if ((!reservation.consumed() && !reservation.committed()) || !reservation.location().publicId().equals(locationId)) {
            throw new BusinessConflictException("Order reservation is not committed at its fulfillment Location");
        }
    }

    @Transactional
    public FulfillmentStock handoverCommitted(UUID reservationId, Instant now) {
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.committed()) throw new BusinessConflictException("Order reservation is not committed for handover");
        InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location())
                .orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
        long beforeOnHand = balance.onHand();
        long beforeReserved = balance.reserved();
        balance.consume(reservation.quantity(), now);
        reservation.consumeCommitted(now);
        return new FulfillmentStock(reservation.publicId(), reservation.variant().publicId(),
                reservation.location().publicId(), reservation.quantity(), beforeOnHand, beforeReserved,
                balance.onHand(), balance.reserved());
    }

    @Transactional
    public FulfillmentStock restoreCommittedCancellation(UUID reservationId, Instant now) {
        InventoryReservation reservation = reservations.findLockedByPublicId(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        if (!reservation.committed()) throw new BusinessConflictException("Order reservation is not committed for cancellation");
        InventoryBalance balance = balances.findLockedByVariantAndLocation(reservation.variant(), reservation.location())
                .orElseThrow(() -> new IllegalStateException("Inventory balance not found"));
        long beforeOnHand = balance.onHand();
        long beforeReserved = balance.reserved();
        balance.release(reservation.quantity(), now);
        reservation.restoreCancelled(now);
        return new FulfillmentStock(reservation.publicId(), reservation.variant().publicId(),
                reservation.location().publicId(), reservation.quantity(), beforeOnHand, beforeReserved,
                balance.onHand(), balance.reserved());
    }

    @Transactional(readOnly = true)
    public Instant expiryForOrder(UUID reservationId) {
        return reservation(reservationId).expiresAt();
    }

    private InventoryReservation reservation(UUID id) { return reservations.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Reservation not found")); }
    private static ReservationView view(InventoryReservation reservation) { return new ReservationView(reservation.publicId(), reservation.ownerAccountPublicId(), reservation.variant().publicId(), reservation.location().publicId(), reservation.quantity(), reservation.status(), reservation.createdAt(), reservation.adoptedAt(), reservation.releasedAt(), reservation.consumedAt(), reservation.expiresAt(), reservation.expiredAt(), reservation.committedAt(), reservation.cancelledRestoredAt()); }
    public record ReservationView(UUID id, UUID ownerAccountId, UUID variantId, UUID locationId, long quantity, String status, Instant createdAt, Instant adoptedAt, Instant releasedAt, Instant consumedAt, Instant expiresAt, Instant expiredAt, Instant committedAt, Instant cancelledRestoredAt) { }
    public record Adoption(UUID reservationId, UUID ownerAccountId, UUID variantId, UUID locationId, UUID branchId, long quantity) { }
    public record Consumption(long quantity, long beforeOnHand, long beforeReserved, long afterOnHand, long afterReserved) { }
    public record FulfillmentStock(UUID reservationId, UUID variantId, UUID locationId, long quantity,
            long beforeOnHand, long beforeReserved, long afterOnHand, long afterReserved) { }
    public enum PaymentCommit { COMMITTED, EXPIRED_RELEASED, INELIGIBLE }
}
