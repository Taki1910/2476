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
import com.shoecommerce.shipping.ShippingService;
import com.shoecommerce.promotion.PromotionService;
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
    private final ShippingService shipping;
    private final PromotionService promotions;

    public CartQuoteService(CartQuoteRepository quotes, VariantPriceRepository prices,
            InventoryReservationService reservations, CheckoutHoldExpiryService expiry,
            AuthorizationPolicy authorization, Clock clock, ShippingService shipping, PromotionService promotions) {
        this.quotes = quotes; this.prices = prices; this.reservations = reservations;
        this.expiry = expiry; this.authorization = authorization; this.clock = clock; this.shipping = shipping; this.promotions=promotions;
    }

    @Transactional
    public QuoteView quote(SessionPrincipal actor, List<LineRequest> requested) {
        return quote(actor, requested, new FulfillmentQuote("PICKUP", null, null));
    }

    @Transactional
    public QuoteView quote(SessionPrincipal actor, List<LineRequest> requested, FulfillmentQuote fulfillment) {
        return quote(actor,requested,fulfillment,PromotionService.VoucherSelection.none());
    }

    @Transactional
    public QuoteView quote(SessionPrincipal actor, List<LineRequest> requested, FulfillmentQuote fulfillment,
            PromotionService.VoucherSelection voucherSelection) {
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
        long merchandise = lineTotal(quote);
        quote.fulfillmentType = fulfillment == null || fulfillment.type() == null ? "PICKUP" : fulfillment.type();
        quote.shippingFeeAmount = BigDecimal.ZERO;
        if ("DELIVERY".equals(quote.fulfillmentType)) {
            var origin = reservations.deliveryOrigin(stock);
            var shippingQuote = shipping.quote(origin.id(), origin.branchId(), fulfillment.destinationProvinceCode(),
                    fulfillment.destinationDistrictCode(), quote.quotedAt);
            quote.originLocationId=origin.id(); quote.originBranchId=origin.branchId();
            quote.destinationProvinceCode=fulfillment.destinationProvinceCode(); quote.destinationDistrictCode=fulfillment.destinationDistrictCode();
            quote.shippingRuleId=shippingQuote.revisionId(); quote.shippingZoneCode=shippingQuote.zoneCode();
            quote.shippingFeeAmount=BigDecimal.valueOf(shippingQuote.feeAmount());
        } else if (!"PICKUP".equals(quote.fulfillmentType)) {
            throw new InvalidRequestException("INVALID_FULFILLMENT", "Choose pickup or delivery.");
        }
        var evaluation=promotions.evaluate(quote.items.stream().map(line->new PromotionService.Line(line.variantId,
                line.priceVersion.variant().product().publicId(),line.quantity,line.unitPrice.longValueExact())).toList(),
                quote.shippingFeeAmount.longValueExact(),quote.quotedAt,java.util.Set.of(),false,actor.publicId(),voucherSelection,
                "DELIVERY".equals(quote.fulfillmentType));
        quote.merchandiseAmount=BigDecimal.valueOf(evaluation.merchandise());
        quote.merchandiseDiscountAmount=BigDecimal.valueOf(evaluation.merchandiseDiscount());
        quote.shippingDiscountAmount=BigDecimal.valueOf(evaluation.shippingDiscount());
        quote.totalAmount=BigDecimal.valueOf(evaluation.total());
        quotes.saveAndFlush(quote);
        promotions.storeQuote(quote.publicId,evaluation);
        QuoteView result = view(quote, reservations.checkoutLocations(stock),evaluation.adjustments());
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
        if (quote.shippingRuleId != null) shipping.requireQuoteUsable(quote.shippingRuleId);
        List<PromotionService.Adjustment> accepted=promotions.quoteAdjustments(quoteId);
        var current=promotions.revalidate(quote.items.stream().map(line->new PromotionService.Line(line.variantId,
                 line.priceVersion.variant().product().publicId(),line.quantity,line.unitPrice.longValueExact())).toList(),
                quote.shippingFeeAmount.longValueExact(),clock.instant(),accepted,actor.publicId(),"DELIVERY".equals(quote.fulfillmentType));
        if(!promotions.quoteSignature(quoteId).equals(current.signature())) {
            boolean selected=accepted.stream().anyMatch(a->!"AUTOMATIC".equals(a.acquisitionMode()));
            throw new BusinessConflictException(selected?"VOUCHER_STALE":"PROMOTION_QUOTE_STALE",selected?"The selected voucher changed. Review a fresh quote before checkout.":"Automatic offers changed. Review a fresh quote before checkout.");
        }
        // Checkout revalidates and locks the chosen location in reserveCartForCheckout.
        // Avoid a stale non-locking balance read in the same transaction before that lock upgrade.
        QuoteView evidence = view(quote, List.of(),accepted);
        return new CheckoutQuote(evidence, quote.items.stream().map(line -> line.priceVersion.variant()).toList(), quote.shippingRuleId,current);
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
        return fingerprint(quoteId,lines,fulfillmentEvidence,"");
    }

    public static String fingerprint(UUID quoteId, List<LineRequest> lines, String fulfillmentEvidence, String voucherEvidence) {
        if (quoteId == null) throw new InvalidRequestException("INVALID_CHECKOUT_REQUEST", "A cart quote is required.");
        StringBuilder canonical = new StringBuilder(quoteId.toString());
        for (LineRequest line : normalize(lines)) canonical.append('|').append(line.variantId()).append(':').append(line.quantity());
        canonical.append('|').append(fulfillmentEvidence == null ? "" : fulfillmentEvidence);
        canonical.append('|').append(voucherEvidence == null ? "" : voucherEvidence);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static long lineTotal(CartQuote quote) {
        long total=0; try { for(CartQuoteLine line:quote.items) total=Math.addExact(total,Math.multiplyExact(line.unitPrice.longValueExact(),line.quantity)); }
        catch(ArithmeticException overflow){throw amountLimit();} return total;
    }
    private static InvalidRequestException amountLimit(){return new InvalidRequestException("CART_AMOUNT_LIMIT", "Cart total exceeds the supported payment limit.");}
    private static QuoteView view(CartQuote quote, List<InventoryReservationService.CheckoutLocation> pickupLocations,List<PromotionService.Adjustment> adjustments) {
        List<LineView> lines = quote.items.stream().map(line -> new LineView(line.variantId, line.productName, line.sku,
                line.size, line.color, line.priceVersion.publicId(), line.quantity, line.unitPrice.longValueExact(),
                Math.multiplyExact(line.unitPrice.longValueExact(), line.quantity))).toList();
        long total = quote.totalAmount.longValueExact();
        if (total <= 0 || total > VariantPrice.MAX_AMOUNT || total > MAX_TOTAL_AMOUNT) {
            throw new InvalidRequestException("CART_AMOUNT_LIMIT", "Cart total exceeds the supported payment limit.");
        }
        return new QuoteView(quote.publicId, quote.quotedAt, quote.expiresAt, "VND", quote.fulfillmentType,
                quote.originLocationId, quote.originBranchId, quote.destinationProvinceCode, quote.destinationDistrictCode,
                quote.shippingZoneCode, quote.merchandiseAmount.longValueExact(),quote.merchandiseDiscountAmount.longValueExact(), quote.shippingFeeAmount.longValueExact(),quote.shippingDiscountAmount.longValueExact(),
                total, lines,adjustments.stream().map(a->new AdjustmentView(a.name(),a.layer(),a.amount(),a.acquisitionMode(),a.maskedCode(),a.voucherClaimId())).toList(), pickupLocations);
    }

    public record LineRequest(@NotNull UUID variantId, @Positive @Max(10) long quantity) { }
    public record LineView(UUID variantId, String productName, String sku, String size, String color,
            UUID priceVersionId, long quantity, long unitPriceAmount, long totalAmount) { }
    public record FulfillmentQuote(String type, String destinationProvinceCode, String destinationDistrictCode) { }
    public record QuoteView(UUID id, Instant quotedAt, Instant expiresAt, String currency, String fulfillmentType,
            UUID originLocationId, UUID originBranchId, String destinationProvinceCode, String destinationDistrictCode,
            String shippingZoneCode, long merchandiseAmount,long merchandiseDiscountAmount, long shippingFeeAmount,long shippingDiscountAmount, long totalAmount,
            List<LineView> items,List<AdjustmentView> adjustments, List<InventoryReservationService.CheckoutLocation> pickupLocations) { }
    public record AdjustmentView(String name,String layer,long amount,String acquisitionMode,String maskedCode,UUID claimId){}
    public record CheckoutQuote(QuoteView quote, List<ProductVariant> variants, UUID shippingRuleId,PromotionService.Evaluation promotionEvaluation) { }
}
