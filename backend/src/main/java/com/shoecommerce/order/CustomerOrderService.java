package com.shoecommerce.order;

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
import com.shoecommerce.identity.OwnershipPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.payment.PaymentAttemptService;
import com.shoecommerce.fulfillment.PickupPresentationService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.VariantPrice;
import com.shoecommerce.pricing.VariantPriceRepository;

@Service
public class CustomerOrderService {
    private final CustomerOrderRepository orders;
    private final InventoryReservationService reservations;
    private final PaymentAttemptService payments;
    private final ProductVariantRepository variants;
    private final VariantPriceRepository prices;
    private final LocationRepository locations;
    private final AuthorizationPolicy authorization;
    private final OwnershipPolicy ownership;
    private final AuditWriter audit;
    private final Clock clock;
    private final PriceQuoteService priceQuotes;
    private final UserAccountRepository accounts;
    private final PickupPresentationService pickupPresentation;

    public CustomerOrderService(CustomerOrderRepository orders, InventoryReservationService reservations, PaymentAttemptService payments,
            ProductVariantRepository variants, VariantPriceRepository prices, LocationRepository locations,
            AuthorizationPolicy authorization, OwnershipPolicy ownership, AuditWriter audit, Clock clock,
            PriceQuoteService priceQuotes, UserAccountRepository accounts, PickupPresentationService pickupPresentation) {
        this.orders = orders; this.reservations = reservations; this.payments = payments; this.variants = variants; this.prices = prices;
        this.locations = locations; this.authorization = authorization; this.ownership = ownership; this.audit = audit; this.clock = clock;
        this.priceQuotes = priceQuotes; this.accounts = accounts;
        this.pickupPresentation = pickupPresentation;
    }

    @Transactional
    public OrderView checkout(SessionPrincipal actor, UUID quoteId, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        if (quoteId == null) throw new InvalidRequestException("INVALID_CHECKOUT_REQUEST", "A price quote is required.");
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 characters.");
        }

        // ponytail: a customer-row lock serializes that customer's checkout keys; split to a dedicated claim table only if per-customer checkout throughput demands it.
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow(() -> new IllegalStateException("Checkout account not found"));
        var replay = orders.findByOwnerAccountPublicIdAndCheckoutIdempotencyKey(actor.publicId(), key);
        if (replay.isPresent()) {
            if (!quoteId.equals(replay.get().priceQuotePublicId())) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key was already used for a different checkout request.");
            }
            return view(replay.get());
        }

        PriceQuoteService.CheckoutQuote quote = priceQuotes.checkoutQuote(actor, quoteId);
        if (orders.existsByPriceQuotePublicId(quoteId)) {
            throw new BusinessConflictException("PRICE_QUOTE_CONSUMED", "This price quote has already created an order.");
        }
        ProductVariant variant = quote.variant();
        checkedTotal(quote.amount(), 1);
        InventoryReservationService.Adoption adoption = reservations.reserveForCheckout(actor, variant, 1);
        CustomerOrder order = orders.save(CustomerOrder.createCheckout(actor.publicId(), adoption.branchId(),
                adoption.reservationId(), quote.quoteId(), key, quote.priceVersionId(), variant.publicId(),
                adoption.locationId(), variant.sku(), variant.size(), 1, quote.amount(), clock.instant()));
        Location location = locations.findByPublicId(adoption.locationId()).orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "ORDER_CREATED", "ORDER", order.publicId(), location.branchId(), location.id(),
                Map.of("reservationId", adoption.reservationId(), "quoteId", quote.quoteId(),
                        "priceVersionId", quote.priceVersionId(), "unitPrice", quote.amount(), "quantity", 1, "currency", quote.currency()));
        return view(order);
    }

    @Transactional
    public OrderView create(SessionPrincipal actor, UUID reservationId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        InventoryReservationService.Adoption adoption = reservations.adoptForOrder(actor, reservationId);
        ProductVariant variant = variants.findByPublicId(adoption.variantId()).orElseThrow(() -> new IllegalArgumentException("Variant not found"));
        if (!variant.published()) throw new IllegalStateException("Variant is not published");
        VariantPrice price = prices.findByVariant(variant).filter(candidate -> candidate.amount() > 0).orElseThrow(() -> new IllegalStateException("Variant has no valid current price"));
        checkedTotal(price.amount(), adoption.quantity());
        Location location = locations.findByPublicId(adoption.locationId()).filter(Location::enabled).orElseThrow(() -> new IllegalStateException("Location is not enabled"));
        CustomerOrder order = orders.save(CustomerOrder.create(actor.publicId(), adoption.branchId(), adoption.reservationId(), adoption.variantId(), adoption.locationId(), adoption.quantity(), price.amount(), clock.instant()));
        audit.append(actor, "ORDER_CREATED", "ORDER", order.publicId(), location.branchId(), location.id(), Map.of("reservationId", reservationId, "unitPrice", price.amount(), "quantity", adoption.quantity(), "currency", "VND"));
        return view(order);
    }

    @Transactional(readOnly = true)
    public OrderView readOwn(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        CustomerOrder order = order(orderId);
        ownership.requireOwnership(actor, order.ownerAccountPublicId());
        return view(order);
    }

    @Transactional
    public OrderView cancelOwn(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        ownership.requireOwnership(actor, order.ownerAccountPublicId());
        if (order.cancelled()) return view(order);
        if (!order.pendingPayment()) throw new BusinessConflictException("Paid Order cannot be cancelled without a refund policy");
        payments.cancelPendingForOwnedOrder(actor, order.publicId());
        reservations.releaseAdoptedForCancelledOrder(actor, order.reservationPublicId());
        order.cancel(clock.instant());
        Location location = locations.findByPublicId(order.item().locationPublicId()).orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "ORDER_CANCELLED", "ORDER", order.publicId(), location.branchId(), location.id(), Map.of("reservationId", order.reservationPublicId()));
        return view(order);
    }

    private CustomerOrder order(UUID id) { return orders.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Order not found")); }
    private static void checkedTotal(long unitPrice, long quantity) { try { Math.multiplyExact(unitPrice, quantity); } catch (ArithmeticException exception) { throw new IllegalArgumentException("Order total exceeds supported range", exception); } }
    private OrderView view(CustomerOrder order) {
        OrderItem item = order.item();
        Location location = locations.findByPublicId(item.locationPublicId())
                .orElseThrow(() -> new IllegalStateException("Order location not found"));
        var pickup = pickupPresentation.forOrder(order);
        return new OrderView(order.publicId(), order.reservationPublicId(), reservations.expiryForOrder(order.reservationPublicId()),
                order.priceQuotePublicId(), order.priceVersionPublicId(), order.ownerAccountPublicId(),
                order.responsibleBranchPublicId(), order.status(), order.createdAt(), order.cancelledAt(), order.paidAt(),
                item.variantPublicId(), item.skuSnapshot(), item.sizeSnapshot(), item.locationPublicId(), location.code(),
                location.name(), item.quantity(), item.unitPriceAmount(), order.currency(), item.totalAmount(),
                pickup.customerStatus(), pickup.fulfillmentStatus(), pickup.financialVoidStatus(), pickup.cancellationEligible());
    }
    public record OrderView(UUID id, UUID reservationId, Instant reservationExpiresAt, UUID priceQuoteId, UUID priceVersionId,
            UUID ownerAccountId, UUID responsibleBranchId, String status, Instant createdAt, Instant cancelledAt,
            Instant paidAt, UUID variantId, String sku, String size, UUID locationId, String locationCode,
            String locationName, long quantity,
            long unitPriceAmount, String currency, long totalAmount, String pickupStatus, String fulfillmentStatus,
            String financialVoidStatus, boolean cancellationEligible) { }
}
