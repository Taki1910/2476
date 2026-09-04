package com.shoecommerce.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
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
import com.shoecommerce.fulfillment.PickupFulfillment;
import com.shoecommerce.fulfillment.PickupFulfillmentService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.pricing.PriceQuoteService;
import com.shoecommerce.pricing.CartQuoteService;
import com.shoecommerce.pricing.VariantPrice;
import com.shoecommerce.pricing.VariantPriceRepository;

@Service
public class CustomerOrderService {
    private static final long MAX_CHECKOUT_QUANTITY = 10;
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
    private final CartQuoteService cartQuotes;
    private final PickupFulfillmentService fulfillments;

    public CustomerOrderService(CustomerOrderRepository orders, InventoryReservationService reservations, PaymentAttemptService payments,
            ProductVariantRepository variants, VariantPriceRepository prices, LocationRepository locations,
            AuthorizationPolicy authorization, OwnershipPolicy ownership, AuditWriter audit, Clock clock,
            PriceQuoteService priceQuotes, UserAccountRepository accounts, PickupPresentationService pickupPresentation,
            CartQuoteService cartQuotes, PickupFulfillmentService fulfillments) {
        this.orders = orders; this.reservations = reservations; this.payments = payments; this.variants = variants; this.prices = prices;
        this.locations = locations; this.authorization = authorization; this.ownership = ownership; this.audit = audit; this.clock = clock;
        this.priceQuotes = priceQuotes; this.accounts = accounts;
        this.pickupPresentation = pickupPresentation;
        this.cartQuotes = cartQuotes;
        this.fulfillments = fulfillments;
    }

    @Transactional
    public OrderView checkoutCart(SessionPrincipal actor, UUID quoteId, List<CartQuoteService.LineRequest> requested,
            String idempotencyKey) {
        return checkoutCart(actor, quoteId, requested,
                new FulfillmentRequest(PickupFulfillment.Type.PICKUP, null, null), idempotencyKey);
    }

    @Transactional
    public OrderView checkoutCart(SessionPrincipal actor, UUID quoteId, List<CartQuoteService.LineRequest> requested,
            FulfillmentRequest fulfillmentRequest, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        var demand = CartQuoteService.normalize(requested);
        FulfillmentIntent intent = fulfillmentIntent(fulfillmentRequest);
        String fingerprint = CartQuoteService.fingerprint(quoteId, demand, intent.fingerprint());
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 128) throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 characters.");
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow(() -> new IllegalStateException("Checkout account not found"));
        var replay = orders.findByOwnerAccountPublicIdAndCheckoutIdempotencyKey(actor.publicId(), key);
        if (replay.isPresent()) {
            if (!fingerprint.equals(replay.get().checkoutFingerprint())) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key was already used for a different checkout request.");
            }
            return view(replay.get());
        }
        var checkout = cartQuotes.checkoutQuote(actor, quoteId, demand);
        if (orders.existsByCartQuotePublicId(quoteId)) throw new BusinessConflictException("PRICE_QUOTE_CONSUMED", "This price quote has already created an order.");
        var quote = checkout.quote();
        List<InventoryReservationService.Demand> stockDemand = new java.util.ArrayList<>();
        for (int i = 0; i < quote.items().size(); i++) {
            stockDemand.add(new InventoryReservationService.Demand(checkout.variants().get(i), quote.items().get(i).quantity()));
        }
        Instant now = clock.instant();
        var adoptions = reservations.reserveCartForCheckout(actor, stockDemand, now, intent.pickupLocationId());
        List<CustomerOrder.ItemFacts> lines = new java.util.ArrayList<>();
        for (var line : quote.items()) {
            var adoption = adoptions.stream().filter(value -> value.variantId().equals(line.variantId())).findFirst().orElseThrow();
            lines.add(new CustomerOrder.ItemFacts(null, adoption.reservationId(), line.variantId(), adoption.locationId(),
                    line.quantity(), line.sku(), line.size(), line.color(), line.unitPriceAmount(), line.totalAmount()));
        }
        CustomerOrder order = orders.save(CustomerOrder.createCart(actor.publicId(), adoptions.getFirst().branchId(), quoteId,
                key, fingerprint, lines, quote.items().stream().map(CartQuoteService.LineView::priceVersionId).toList(), now));
        Location location = locations.findByPublicId(adoptions.getFirst().locationId()).orElseThrow();
        fulfillments.createIntent(actor, order, location, intent.type(), intent.delivery(), now);
        audit.append(actor, "ORDER_CREATED", "ORDER", order.publicId(), location.branchId(), location.id(),
                Map.of("cartQuoteId", quoteId, "itemCount", lines.size(), "totalAmount", order.totalAmount(), "currency", "VND",
                        "reservationIds", order.paymentFacts().reservationIds(), "fulfillmentType", intent.type().name()));
        return view(order);
    }

    @Transactional
    public OrderView checkout(SessionPrincipal actor, UUID quoteId, String idempotencyKey) {
        return checkout(actor, quoteId, 1, idempotencyKey);
    }

    @Transactional
    public OrderView checkout(SessionPrincipal actor, UUID quoteId, long quantity, String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        authorization.requirePermission(actor, PermissionCode.CHECKOUT_RESERVE);
        if (quoteId == null) throw new InvalidRequestException("INVALID_CHECKOUT_REQUEST", "A price quote is required.");
        if (quantity <= 0 || quantity > MAX_CHECKOUT_QUANTITY) {
            throw new InvalidRequestException("INVALID_CHECKOUT_QUANTITY", "Quantity must be between 1 and 10.");
        }
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 characters.");
        }

        // ponytail: a customer-row lock serializes that customer's checkout keys; split to a dedicated claim table only if per-customer checkout throughput demands it.
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow(() -> new IllegalStateException("Checkout account not found"));
        var replay = orders.findByOwnerAccountPublicIdAndCheckoutIdempotencyKey(actor.publicId(), key);
        if (replay.isPresent()) {
            if (!quoteId.equals(replay.get().priceQuotePublicId()) || replay.get().item().quantity() != quantity) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This idempotency key was already used for a different checkout request.");
            }
            return view(replay.get());
        }

        PriceQuoteService.CheckoutQuote quote = priceQuotes.checkoutQuote(actor, quoteId);
        if (orders.existsByPriceQuotePublicId(quoteId)) {
            throw new BusinessConflictException("PRICE_QUOTE_CONSUMED", "This price quote has already created an order.");
        }
        ProductVariant variant = quote.variant();
        checkedTotal(quote.amount(), quantity);
        InventoryReservationService.Adoption adoption = reservations.reserveForCheckout(actor, variant, quantity);
        CustomerOrder order = orders.save(CustomerOrder.createCheckout(actor.publicId(), adoption.branchId(),
                adoption.reservationId(), quote.quoteId(), key, quote.priceVersionId(), variant.publicId(),
                adoption.locationId(), variant.sku(), variant.size(), quantity, quote.amount(), clock.instant()));
        Location location = locations.findByPublicId(adoption.locationId()).orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "ORDER_CREATED", "ORDER", order.publicId(), location.branchId(), location.id(),
                Map.of("reservationId", adoption.reservationId(), "quoteId", quote.quoteId(),
                        "priceVersionId", quote.priceVersionId(), "unitPrice", quote.amount(), "quantity", quantity, "currency", quote.currency()));
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

    @Transactional(readOnly = true)
    public OrderPage readOwnOrders(SessionPrincipal actor, int page, int size) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidRequestException("INVALID_ORDER_PAGE", "Page must be non-negative and size must be between 1 and 50.");
        }
        var results = orders.findByOwnerAccountPublicIdOrderByCreatedAtDesc(actor.publicId(), PageRequest.of(page, size));
        return new OrderPage(results.getContent().stream().map(this::view).toList(), page, size, results.hasNext());
    }

    @Transactional
    public OrderView cancelOwn(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        CustomerOrder order = orders.findLockedByPublicId(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        ownership.requireOwnership(actor, order.ownerAccountPublicId());
        if (order.cancelled()) return view(order);
        if (!order.pendingPayment()) throw new BusinessConflictException("Paid Order cannot be cancelled without a refund policy");
        payments.cancelPendingForOwnedOrder(actor, order.publicId());
        reservations.releaseAdoptedForCancelledOrder(actor, order.paymentFacts().reservationIds());
        order.cancel(clock.instant());
        Location location = locations.findByPublicId(order.paymentFacts().locationId()).orElseThrow(() -> new IllegalStateException("Order location not found"));
        audit.append(actor, "ORDER_CANCELLED", "ORDER", order.publicId(), location.branchId(), location.id(), Map.of("reservationIds", order.paymentFacts().reservationIds()));
        return view(order);
    }

    private CustomerOrder order(UUID id) { return orders.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Order not found")); }
    private static void checkedTotal(long unitPrice, long quantity) {
        try {
            long total = Math.multiplyExact(unitPrice, quantity);
            if (total > VariantPrice.MAX_AMOUNT) throw new IllegalArgumentException("Order total exceeds supported range");
        } catch (ArithmeticException exception) { throw new IllegalArgumentException("Order total exceeds supported range", exception); }
    }
    private OrderView view(CustomerOrder order) {
        var items = order.items();
        OrderItem single = items.size() == 1 ? items.getFirst() : null;
        Location location = locations.findByPublicId(items.getFirst().locationPublicId())
                .orElseThrow(() -> new IllegalStateException("Order location not found"));
        var pickup = pickupPresentation.forOrder(order);
        return new OrderView(order.publicId(), orderReference(order.publicId()), single == null ? null : single.reservationPublicId(), reservations.expiryForOrder(items.getFirst().reservationPublicId()),
                order.priceQuotePublicId(), single == null ? null : single.priceVersionPublicId(), order.ownerAccountPublicId(),
                order.responsibleBranchPublicId(), order.status(), order.createdAt(), order.cancelledAt(), order.paidAt(),
                single == null ? null : single.variantPublicId(), single == null ? null : single.skuSnapshot(),
                single == null ? null : single.sizeSnapshot(), location.publicId(), location.code(), location.name(),
                items.stream().mapToLong(OrderItem::quantity).sum(), single == null ? null : single.unitPriceAmount(), order.currency(), order.totalAmount(),
                pickup.customerStatus(), pickup.fulfillmentStatus(), pickup.financialVoidStatus(), pickup.cancellationEligible(),
                order.cartQuotePublicId(), items.size(), items.stream().map(item -> new OrderLine(item.publicId(), item.reservationPublicId(),
                    item.priceVersionPublicId(), item.variantPublicId(), item.skuSnapshot(), item.sizeSnapshot(), item.colorSnapshot(),
                    item.locationPublicId(), item.quantity(), item.unitPriceAmount(), item.totalAmount())).toList(),
                pickup.fulfillmentType(), pickup.acceptedAt(), pickup.readyAt(), pickup.handedOverAt(),
                pickup.dispatchedAt(), pickup.deliveredAt(), pickup.fulfillmentCancelledAt(), pickup.receiverName(),
                pickup.receiverPhone(), pickup.deliveryAddress(), pickup.deliveryNote(), pickup.deliveryFeeAmount());
    }
    public record OrderView(UUID id, String orderReference, UUID reservationId, Instant reservationExpiresAt, UUID priceQuoteId, UUID priceVersionId,
            UUID ownerAccountId, UUID responsibleBranchId, String status, Instant createdAt, Instant cancelledAt,
            Instant paidAt, UUID variantId, String sku, String size, UUID locationId, String locationCode,
            String locationName, long quantity,
            Long unitPriceAmount, String currency, long totalAmount, String pickupStatus, String fulfillmentStatus,
            String financialVoidStatus, boolean cancellationEligible, UUID cartQuoteId, int itemCount,
            List<OrderLine> items, String fulfillmentType, Instant acceptedAt, Instant readyAt,
            Instant handedOverAt, Instant dispatchedAt, Instant deliveredAt, Instant fulfillmentCancelledAt,
            String receiverName, String receiverPhone, String deliveryAddress, String deliveryNote,
            long deliveryFeeAmount) { }

    public record OrderLine(UUID id, UUID reservationId, UUID priceVersionId, UUID variantId, String sku,
            String size, String color, UUID locationId, long quantity, long unitPriceAmount, long totalAmount) { }

    public record OrderPage(List<OrderView> items, int page, int size, boolean hasNext) { }

    public record FulfillmentRequest(PickupFulfillment.Type type, UUID pickupLocationId, DeliveryRequest delivery) { }
    public record DeliveryRequest(String receiverName, String receiverPhone, String address, String note) { }
    private record FulfillmentIntent(PickupFulfillment.Type type, UUID pickupLocationId,
            PickupFulfillment.DeliveryDetails delivery) {
        String fingerprint() {
            if (type == PickupFulfillment.Type.PICKUP) return "PICKUP|" + pickupLocationId;
            return "DELIVERY|" + delivery.receiverName() + "|" + delivery.receiverPhone() + "|"
                    + delivery.address() + "|" + (delivery.note() == null ? "" : delivery.note());
        }
    }

    private static FulfillmentIntent fulfillmentIntent(FulfillmentRequest request) {
        if (request == null || request.type() == null) {
            throw new InvalidRequestException("INVALID_FULFILLMENT", "Choose pickup or delivery.");
        }
        try {
            if (request.type() == PickupFulfillment.Type.PICKUP) {
                if (request.delivery() != null) {
                    throw new IllegalArgumentException("Pickup cannot include a delivery address");
                }
                return new FulfillmentIntent(request.type(), request.pickupLocationId(), null);
            }
            if (request.pickupLocationId() != null || request.delivery() == null) {
                throw new IllegalArgumentException("Delivery requires receiver details and no pickup location");
            }
            DeliveryRequest delivery = request.delivery();
            return new FulfillmentIntent(request.type(), null, new PickupFulfillment.DeliveryDetails(
                    delivery.receiverName(), delivery.receiverPhone(), delivery.address(), delivery.note()));
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException("INVALID_FULFILLMENT", invalid.getMessage());
        }
    }

    private static String orderReference(UUID id) {
        return "SC-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
