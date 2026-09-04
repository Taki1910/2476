package com.shoecommerce.pricing;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Service
public class CartQuoteService {
    // Current checkout has only the full-order VNPAY payment route; its amount ceiling is narrower than JS-safe VND.
    public static final long MAX_TOTAL_AMOUNT = 9_999_999_999L;
    private final CartQuoteRepository quotes;
    private final VariantPriceRepository prices;
    private final InventoryReservationService reservations;
    private final CheckoutHoldExpiryService expiry;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public CartQuoteService(CartQuoteRepository quotes, VariantPriceRepository prices,
            InventoryReservationService reservations, CheckoutHoldExpiryService expiry,
            AuthorizationPolicy authorization, Clock clock) {
        this.quotes = quotes; this.prices = prices; this.reservations = reservations;
        this.expiry = expiry; this.authorization = authorization; this.clock = clock;
    }

    @Transactional
    public QuoteView quote(SessionPrincipal actor, List<LineRequest> requested) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_BROWSE);
        List<LineRequest> demand = normalize(requested);
        demand.forEach(line -> expiry.expireForVariant(line.variantId()));
        CartQuote quote = new CartQuote();
        quote.publicId = UUID.randomUUID(); quote.ownerAccountId = actor.accountId();
        quote.quotedAt = clock.instant(); quote.expiresAt = quote.quotedAt.plus(Duration.ofMinutes(15));
        for (LineRequest request : demand) {
            VariantPrice price = prices.findEffectivePublished(request.variantId(), quote.quotedAt)
                    .orElseThrow(() -> new BusinessConflictException("VARIANT_NOT_SELLABLE", "This cart variant is no longer available.", request.variantId()));
            ProductVariant variant = price.variant();
            CartQuoteLine line = new CartQuoteLine();
            line.quote = quote; line.priceVersion = price; line.variantId = variant.publicId();
            line.quantity = request.quantity(); line.unitPrice = BigDecimal.valueOf(price.amount());
            line.productName = variant.product().name(); line.sku = variant.sku(); line.size = variant.size(); line.color = variant.color();
            quote.items.add(line);
        }
        List<InventoryReservationService.Demand> stock = quote.items.stream().map(line ->
                new InventoryReservationService.Demand(line.priceVersion.variant(), line.quantity)).toList();
        QuoteView result = view(quote, reservations.checkoutLocations(stock));
        quotes.save(quote);
        return result;
    }

    @Transactional
    public CheckoutQuote checkoutQuote(SessionPrincipal actor, UUID quoteId, List<LineRequest> requested) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        List<LineRequest> demand = normalize(requested);
        CartQuote quote = quotes.findByPublicId(quoteId).filter(value -> value.ownerAccountId == actor.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("PRICE_QUOTE_NOT_FOUND", "Price quote not found."));
        if (!clock.instant().isBefore(quote.expiresAt)) {
            throw new BusinessConflictException("PRICE_QUOTE_EXPIRED", "This price quote has expired. Request a fresh price.");
        }
        if (!demand.equals(normalize(quote.items.stream().map(line -> new LineRequest(line.variantId, line.quantity)).toList()))) {
            throw new BusinessConflictException("CART_QUOTE_MISMATCH", "The cart changed. Review a new quote before checkout.");
        }
        demand.forEach(line -> expiry.expireForVariant(line.variantId()));
        for (CartQuoteLine line : quote.items) {
            if (!line.priceVersion.variant().published()) {
                throw new BusinessConflictException("VARIANT_NOT_SELLABLE", "This cart variant is no longer available.", line.variantId);
            }
        }
        // Checkout revalidates and locks the chosen location in reserveCartForCheckout.
        // Avoid a stale non-locking balance read in the same transaction before that lock upgrade.
        QuoteView evidence = view(quote, List.of());
        return new CheckoutQuote(evidence, quote.items.stream().map(line -> line.priceVersion.variant()).toList());
    }

    public static List<LineRequest> normalize(List<LineRequest> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 50) {
            throw new InvalidRequestException("INVALID_CART", "Cart must contain between 1 and 50 lines.");
        }
        TreeMap<String, Long> quantities = new TreeMap<>();
        for (LineRequest line : requested) {
            if (line == null || line.variantId() == null || line.quantity() < 1 || line.quantity() > 10) {
                throw new InvalidRequestException("INVALID_CHECKOUT_QUANTITY", "Quantity must be between 1 and 10.");
            }
            long quantity = quantities.merge(line.variantId().toString(), line.quantity(), Long::sum);
            if (quantity > 10) throw new InvalidRequestException("INVALID_CHECKOUT_QUANTITY", "Combined variant quantity must be between 1 and 10.");
        }
        return quantities.entrySet().stream().map(entry -> new LineRequest(UUID.fromString(entry.getKey()), entry.getValue())).toList();
    }

    public static String fingerprint(UUID quoteId, List<LineRequest> lines) {
        return fingerprint(quoteId, lines, "");
    }

    public static String fingerprint(UUID quoteId, List<LineRequest> lines, String fulfillmentEvidence) {
        if (quoteId == null) throw new InvalidRequestException("INVALID_CHECKOUT_REQUEST", "A cart quote is required.");
        StringBuilder canonical = new StringBuilder(quoteId.toString());
        for (LineRequest line : normalize(lines)) canonical.append('|').append(line.variantId()).append(':').append(line.quantity());
        canonical.append('|').append(fulfillmentEvidence == null ? "" : fulfillmentEvidence);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static QuoteView view(CartQuote quote, List<InventoryReservationService.CheckoutLocation> pickupLocations) {
        List<LineView> lines = quote.items.stream().map(line -> new LineView(line.variantId, line.productName, line.sku,
                line.size, line.color, line.priceVersion.publicId(), line.quantity, line.unitPrice.longValueExact(),
                Math.multiplyExact(line.unitPrice.longValueExact(), line.quantity))).toList();
        long total = 0;
        try { for (LineView line : lines) total = Math.addExact(total, line.totalAmount()); }
        catch (ArithmeticException overflow) { throw new InvalidRequestException("CART_AMOUNT_LIMIT", "Cart total exceeds the supported payment limit."); }
        if (total <= 0 || total > VariantPrice.MAX_AMOUNT || total > MAX_TOTAL_AMOUNT) {
            throw new InvalidRequestException("CART_AMOUNT_LIMIT", "Cart total exceeds the supported payment limit.");
        }
        return new QuoteView(quote.publicId, quote.quotedAt, quote.expiresAt, "VND", total, lines, pickupLocations);
    }

    public record LineRequest(@NotNull UUID variantId, @Positive @Max(10) long quantity) { }
    public record LineView(UUID variantId, String productName, String sku, String size, String color,
            UUID priceVersionId, long quantity, long unitPriceAmount, long totalAmount) { }
    public record QuoteView(UUID id, Instant quotedAt, Instant expiresAt, String currency, long totalAmount,
            List<LineView> items, List<InventoryReservationService.CheckoutLocation> pickupLocations) { }
    public record CheckoutQuote(QuoteView quote, List<ProductVariant> variants) { }
}
