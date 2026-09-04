package com.shoecommerce.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        return reserveCartForCheckout(actor, List.of(new Demand(variant, quantity)), clock.instant()).getFirst();
    }

    @Transactional
    public List<Adoption> reserveCartForCheckout(SessionPrincipal actor, List<Demand> demands, Instant now) {
        return reserveCartForCheckout(actor, demands, now, null);
    }

    @Transactional
    public List<Adoption> reserveCartForCheckout(SessionPrincipal actor, List<Demand> demands, Instant now,
            UUID preferredLocationId) {
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        Objects.requireNonNull(now, "Checkout time is required");
        List<CheckoutStock> selected = cartStock(demands, true, preferredLocationId);
        Instant expiresAt = now.plus(checkoutReservationTtl);
        List<Adoption> result = new ArrayList<>();
        for (CheckoutStock stock : selected) {
            ProductVariant variant = stock.demand().variant();
            long quantity = stock.demand().quantity();
            Location location = stock.location();
            stock.balance().reserve(quantity, now);
            InventoryReservation reservation = InventoryReservation.createCheckout(actor.publicId(), variant,
                    location, quantity, now, expiresAt);
            reservation.adopt(now);
            reservations.save(reservation);
            audit.append(actor, "INVENTORY_RESERVED", "INVENTORY_RESERVATION", reservation.publicId(),
                    location.branchId(), location.id(), Map.of("variantId", variant.publicId(), "quantity", quantity));
            result.add(new Adoption(reservation.publicId(), actor.publicId(), variant.publicId(), location.publicId(),
                    location.branchPublicId(), quantity));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public void checkCartAvailability(List<Demand> demands) {
        cartStock(demands, false, null);
    }

    @Transactional(readOnly = true)
    public List<CheckoutLocation> checkoutLocations(List<Demand> demands) {
        List<CheckoutStock> candidates = candidates(demands, false);
        Set<Long> common = commonLocationIds(demands, candidates);
        return candidates.stream().map(CheckoutStock::location)
                .filter(location -> common.contains(location.id())).distinct()
                .sorted(Comparator.comparing(Location::id))
                .map(location -> new CheckoutLocation(location.publicId(), location.code(), location.name()))
                .toList();
    }

    private List<CheckoutStock> cartStock(List<Demand> demands, boolean lock, UUID preferredLocationId) {
        List<CheckoutStock> candidates = candidates(demands, lock);
        Set<Long> common = commonLocationIds(demands, candidates);
        long locationId;
        if (preferredLocationId == null) {
            locationId = common.stream().min(Long::compareTo).orElseThrow(() -> new BusinessConflictException(
                    "NO_COMMON_PICKUP_LOCATION", "These items cannot currently be collected together at one pickup location."));
        } else {
            locationId = candidates.stream().map(CheckoutStock::location)
                    .filter(location -> preferredLocationId.equals(location.publicId()) && common.contains(location.id()))
                    .map(Location::id).findFirst().orElseThrow(() -> new BusinessConflictException(
                            "PICKUP_LOCATION_UNAVAILABLE", "The selected pickup location cannot currently fulfill this cart."));
        }
        return demands.stream().map(demand -> candidates.stream().filter(candidate ->
                candidate.demand().variant().publicId().equals(demand.variant().publicId())
                        && candidate.location().id() == locationId).findFirst().orElseThrow()).toList();
    }

    private List<CheckoutStock> candidates(List<Demand> demands, boolean lock) {
        if (demands == null || demands.isEmpty()) throw new IllegalArgumentException("Cart must contain a variant");
        Set<UUID> variantsSeen = new HashSet<>();
        for (Demand demand : demands) {
            if (demand == null || demand.variant() == null || demand.variant().id() == null
                    || demand.variant().publicId() == null || demand.quantity() <= 0
                    || !variantsSeen.add(demand.variant().publicId())) {
                throw new IllegalArgumentException("Cart demands must contain distinct persisted variants and positive quantities");
            }
        }

        List<CheckoutStock> candidates = new ArrayList<>();
        // Acquire every existing candidate balance before checking or mutating any line.
        for (Demand demand : demands.stream().sorted(Comparator.comparing(d -> d.variant().id())).toList()) {
            for (Location location : balances.findCheckoutLocations(demand.variant()).stream()
                    .sorted(Comparator.comparing(Location::id)).toList()) {
                var balance = lock ? balances.findLockedByVariantAndLocation(demand.variant(), location)
                        : balances.findByVariantAndLocation(demand.variant(), location);
                balance.ifPresent(value -> candidates.add(new CheckoutStock(demand, location, value)));
            }
        }
        return candidates;
    }

    private static Set<Long> commonLocationIds(List<Demand> demands, List<CheckoutStock> candidates) {
        Set<Long> common = null;
        for (Demand demand : demands) {
            Set<Long> eligible = new HashSet<>();
            for (CheckoutStock candidate : candidates) {
                if (candidate.demand().variant().publicId().equals(demand.variant().publicId())
                        && candidate.balance().available() >= demand.quantity()) eligible.add(candidate.location().id());
            }
            if (eligible.isEmpty()) {
                throw new BusinessConflictException("INSUFFICIENT_STOCK",
                        "This variant is no longer available in the requested quantity.", demand.variant().publicId());
            }
            if (common == null) common = eligible;
            else common.retainAll(eligible);
        }
        if (common == null || common.isEmpty()) throw new BusinessConflictException(
                "NO_COMMON_PICKUP_LOCATION", "These items cannot currently be collected together at one pickup location.");
        return common;
    }

    @Transactional
    public void expireAdoptedForOrder(UUID reservationId, Instant now) {
        expireAdoptedForOrder(List.of(reservationId), now);
    }

    @Transactional
    public void expireAdoptedForOrder(List<UUID> reservationIds, Instant now) {
        List<InventoryReservation> holds = lockReservations(reservationIds);
        if (holds.stream().anyMatch(hold -> !hold.adopted() || !hold.dueForExpiry(now))) {
            throw new BusinessConflictException("Order reservations are not all eligible for expiry");
        }
        Map<UUID, InventoryBalance> locked = lockBalances(holds);
        for (InventoryReservation hold : holds) {
            locked.get(hold.publicId()).release(hold.quantity(), now);
            hold.expire(now);
        }
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
        releaseAdoptedForCancelledOrder(actor, List.of(reservationId));
    }

    @Transactional
    public void releaseAdoptedForCancelledOrder(SessionPrincipal actor, List<UUID> reservationIds) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        List<InventoryReservation> holds = lockReservations(reservationIds);
        for (InventoryReservation hold : holds) ownership.requireOwnership(actor, hold.ownerAccountPublicId());
        if (holds.stream().anyMatch(hold -> !hold.adopted())) {
            throw new BusinessConflictException("Reservation is not adopted by a pending order");
        }
        Map<UUID, InventoryBalance> locked = lockBalances(holds);
        Instant now = clock.instant();
        for (InventoryReservation hold : holds) {
            locked.get(hold.publicId()).release(hold.quantity(), now);
            hold.releaseAdopted(now);
        }
    }

    @Transactional(readOnly = true)
    public Instant requireAdoptedForPayment(SessionPrincipal actor, UUID reservationId, Instant now) {
        return requireAdoptedForPayment(actor, List.of(reservationId), now);
    }

    @Transactional(readOnly = true)
    public Instant requireAdoptedForPayment(SessionPrincipal actor, List<UUID> reservationIds, Instant now) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_INITIATE);
        List<InventoryReservation> holds = lockReservations(reservationIds);
        for (InventoryReservation hold : holds) ownership.requireOwnership(actor, hold.ownerAccountPublicId());
        if (holds.stream().anyMatch(hold -> !hold.adopted())) {
            throw new BusinessConflictException("Order reservation is not adopted");
        }
        if (holds.stream().anyMatch(hold -> hold.dueForExpiry(now))) {
            throw new BusinessConflictException("RESERVATION_EXPIRED", "Order reservation has expired.");
        }
        return holds.stream().map(InventoryReservation::expiresAt).filter(Objects::nonNull)
                .min(Instant::compareTo).orElseGet(() -> now.plus(checkoutReservationTtl));
    }

    @Transactional
    public PaymentCommit commitForSuccessfulPayment(UUID reservationId, Instant now) {
        return commitForSuccessfulPayment(List.of(reservationId), now);
    }

    @Transactional
    public PaymentCommit commitForSuccessfulPayment(List<UUID> reservationIds, Instant now) {
        List<InventoryReservation> holds = lockReservations(reservationIds);
        if (holds.stream().anyMatch(hold -> !hold.adopted())) return PaymentCommit.INELIGIBLE;
        Map<UUID, InventoryBalance> locked = lockBalances(holds);
        // The coherent set has one deadline; an expiry discovered through any line releases all lines.
        if (holds.getFirst().dueForExpiry(now)) {
            for (InventoryReservation hold : holds) {
                locked.get(hold.publicId()).release(hold.quantity(), now);
                hold.expire(now);
            }
            return PaymentCommit.EXPIRED_RELEASED;
        }
        for (InventoryReservation hold : holds) hold.commit(now);
        return PaymentCommit.COMMITTED;
    }

    @Transactional
    public Consumption consumeForSuccessfulPayment(SessionPrincipal actor, UUID reservationId) {
        FulfillmentStock stock = consumeForSuccessfulPayment(actor, List.of(reservationId)).getFirst();
        return new Consumption(stock.quantity(), stock.beforeOnHand(), stock.beforeReserved(),
                stock.afterOnHand(), stock.afterReserved());
    }

    @Transactional
    public List<FulfillmentStock> consumeForSuccessfulPayment(SessionPrincipal actor, List<UUID> reservationIds) {
        authorization.requirePermission(actor, PermissionCode.PAYMENT_EVENT_APPLY);
        List<InventoryReservation> holds = lockReservations(reservationIds);
        if (holds.stream().anyMatch(hold -> !hold.adopted())) {
            throw new BusinessConflictException("Order reservation is not adopted");
        }
        Map<UUID, InventoryBalance> locked = lockBalances(holds);
        Instant now = clock.instant();
        List<FulfillmentStock> result = new ArrayList<>();
        // Preserve the historical test-provider path: consume at payment, not at pickup.
        for (InventoryReservation hold : holds) {
            InventoryBalance balance = locked.get(hold.publicId());
            long beforeOnHand = balance.onHand();
            long beforeReserved = balance.reserved();
            balance.consume(hold.quantity(), now);
            hold.consume(now);
            result.add(stock(hold, balance, beforeOnHand, beforeReserved));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public void requireConsumedForFulfillment(UUID reservationId, UUID locationId) {
        requireConsumedForFulfillment(List.of(reservationId), locationId);
    }

    @Transactional(readOnly = true)
    public void requireConsumedForFulfillment(List<UUID> reservationIds, UUID locationId) {
        List<InventoryReservation> holds = lockReservations(reservationIds);
        if ((!holds.stream().allMatch(InventoryReservation::consumed)
                && !holds.stream().allMatch(InventoryReservation::committed))
                || !holds.getFirst().location().publicId().equals(locationId)) {
            throw new BusinessConflictException("Order reservation is not committed at its fulfillment Location");
        }
    }

    @Transactional
    public FulfillmentStock handoverCommitted(UUID reservationId, Instant now) {
        return handoverCommitted(List.of(reservationId), now).getFirst();
    }

    @Transactional
    public List<FulfillmentStock> handoverCommitted(List<UUID> reservationIds, Instant now) {
        return finishCommitted(reservationIds, now, true);
    }

    @Transactional
    public FulfillmentStock restoreCommittedCancellation(UUID reservationId, Instant now) {
        return restoreCommittedCancellation(List.of(reservationId), now).getFirst();
    }

    @Transactional
    public List<FulfillmentStock> restoreCommittedCancellation(List<UUID> reservationIds, Instant now) {
        return finishCommitted(reservationIds, now, false);
    }

    private List<FulfillmentStock> finishCommitted(List<UUID> reservationIds, Instant now, boolean handover) {
        List<InventoryReservation> holds = lockReservations(reservationIds);
        if (holds.stream().anyMatch(hold -> !hold.committed())) {
            throw new BusinessConflictException("Order reservation is not committed for " + (handover ? "handover" : "cancellation"));
        }
        Map<UUID, InventoryBalance> locked = lockBalances(holds);
        List<FulfillmentStock> result = new ArrayList<>();
        for (InventoryReservation hold : holds) {
            InventoryBalance balance = locked.get(hold.publicId());
            long beforeOnHand = balance.onHand();
            long beforeReserved = balance.reserved();
            if (handover) {
                balance.consume(hold.quantity(), now);
                hold.consumeCommitted(now);
            } else {
                balance.release(hold.quantity(), now);
                hold.restoreCancelled(now);
            }
            result.add(stock(hold, balance, beforeOnHand, beforeReserved));
        }
        return List.copyOf(result);
    }

    private List<InventoryReservation> lockReservations(List<UUID> reservationIds) {
        if (reservationIds == null || reservationIds.isEmpty() || reservationIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(reservationIds).size() != reservationIds.size()) {
            throw new IllegalArgumentException("Distinct order reservation IDs are required");
        }
        List<InventoryReservation> holds = reservationIds.stream().sorted(Comparator.comparing(UUID::toString))
                .map(id -> reservations.findLockedByPublicId(id)
                        .orElseThrow(() -> new IllegalArgumentException("Reservation not found"))).toList();
        InventoryReservation first = holds.getFirst();
        Set<UUID> variantsSeen = new HashSet<>();
        for (InventoryReservation hold : holds) {
            if (!first.ownerAccountPublicId().equals(hold.ownerAccountPublicId())
                    || !first.location().publicId().equals(hold.location().publicId())
                    || !Objects.equals(first.expiresAt(), hold.expiresAt())
                    || !variantsSeen.add(hold.variant().publicId())) {
                throw new BusinessConflictException("Order reservations do not form a coherent hold set");
            }
        }
        return holds;
    }

    private Map<UUID, InventoryBalance> lockBalances(List<InventoryReservation> holds) {
        Map<UUID, InventoryBalance> locked = new LinkedHashMap<>();
        // Every Reservation is already locked. Never return to Reservation rank after this point.
        for (InventoryReservation hold : holds.stream().sorted(Comparator
                .comparing((InventoryReservation hold) -> hold.variant().id())
                .thenComparing(hold -> hold.location().id())).toList()) {
            locked.put(hold.publicId(), balances.findLockedByVariantAndLocation(hold.variant(), hold.location())
                    .orElseThrow(() -> new IllegalStateException("Inventory balance not found")));
        }
        // Validate the complete set before a mutation, including paths that return a PaymentCommit enum.
        for (InventoryReservation hold : holds) {
            InventoryBalance balance = locked.get(hold.publicId());
            if (hold.quantity() <= 0 || balance.reserved() < hold.quantity() || balance.onHand() < hold.quantity()) {
                throw new IllegalStateException("Order reserved inventory is inconsistent");
            }
        }
        return locked;
    }

    private static FulfillmentStock stock(InventoryReservation hold, InventoryBalance balance,
            long beforeOnHand, long beforeReserved) {
        return new FulfillmentStock(hold.publicId(), hold.variant().publicId(), hold.location().publicId(),
                hold.quantity(), beforeOnHand, beforeReserved, balance.onHand(), balance.reserved());
    }

    @Transactional(readOnly = true)
    public Instant expiryForOrder(UUID reservationId) {
        return reservation(reservationId).expiresAt();
    }

    private InventoryReservation reservation(UUID id) { return reservations.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Reservation not found")); }
    private static ReservationView view(InventoryReservation reservation) { return new ReservationView(reservation.publicId(), reservation.ownerAccountPublicId(), reservation.variant().publicId(), reservation.location().publicId(), reservation.quantity(), reservation.status(), reservation.createdAt(), reservation.adoptedAt(), reservation.releasedAt(), reservation.consumedAt(), reservation.expiresAt(), reservation.expiredAt(), reservation.committedAt(), reservation.cancelledRestoredAt()); }
    public record ReservationView(UUID id, UUID ownerAccountId, UUID variantId, UUID locationId, long quantity, String status, Instant createdAt, Instant adoptedAt, Instant releasedAt, Instant consumedAt, Instant expiresAt, Instant expiredAt, Instant committedAt, Instant cancelledRestoredAt) { }
    public record Adoption(UUID reservationId, UUID ownerAccountId, UUID variantId, UUID locationId, UUID branchId, long quantity) { }
    public record Demand(ProductVariant variant, long quantity) { }
    public record CheckoutLocation(UUID id, String code, String name) { }
    private record CheckoutStock(Demand demand, Location location, InventoryBalance balance) { }
    public record Consumption(long quantity, long beforeOnHand, long beforeReserved, long afterOnHand, long afterReserved) { }
    public record FulfillmentStock(UUID reservationId, UUID variantId, UUID locationId, long quantity,
            long beforeOnHand, long beforeReserved, long afterOnHand, long afterReserved) { }
    public enum PaymentCommit { COMMITTED, EXPIRED_RELEASED, INELIGIBLE }
}
