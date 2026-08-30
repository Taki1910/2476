package com.shoecommerce.pricing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class PriceQuoteService {
    private static final Duration VALIDITY = Duration.ofMinutes(15);

    private final PriceQuoteRepository quotes;
    private final VariantPriceRepository prices;
    private final InventoryBalanceRepository balances;
    private final AuthorizationPolicy authorization;
    private final Clock clock;
    private final CheckoutHoldExpiryService checkoutExpiry;

    public PriceQuoteService(PriceQuoteRepository quotes, VariantPriceRepository prices,
            InventoryBalanceRepository balances, AuthorizationPolicy authorization, Clock clock,
            CheckoutHoldExpiryService checkoutExpiry) {
        this.quotes = quotes;
        this.prices = prices;
        this.balances = balances;
        this.authorization = authorization;
        this.clock = clock;
        this.checkoutExpiry = checkoutExpiry;
    }

    @Transactional
    public QuoteView quote(SessionPrincipal actor, UUID variantId) {
        authorization.requirePermission(actor, PermissionCode.CATALOG_BROWSE);
        checkoutExpiry.expireForVariant(variantId);
        Instant quotedAt = clock.instant();
        VariantPrice price = prices.findEffectivePublished(variantId, quotedAt)
                .orElseThrow(() -> new ResourceNotFoundException("STOREFRONT_VARIANT_NOT_FOUND", "Variant not found."));
        if (balances.countCustomerAvailable(variantId) == 0) {
            throw new BusinessConflictException("VARIANT_UNAVAILABLE", "This variant is currently unavailable.");
        }
        PriceQuote quote = quotes.save(PriceQuote.create(actor.accountId(), price, quotedAt, quotedAt.plus(VALIDITY)));
        return new QuoteView(quote.publicId(), price.variantPublicId(), price.publicId(), quote.amount(),
                quote.currency(), quote.quotedAt(), quote.expiresAt());
    }

    @Transactional
    public CheckoutQuote checkoutQuote(SessionPrincipal actor, UUID quoteId) {
        authorization.requirePermission(actor, PermissionCode.ORDER_PLACE);
        PriceQuote quote = quotes.findByPublicId(quoteId)
                .filter(candidate -> candidate.ownerAccountId() == actor.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("PRICE_QUOTE_NOT_FOUND", "Price quote not found."));
        Instant now = clock.instant();
        if (!now.isBefore(quote.expiresAt())) {
            throw new BusinessConflictException("PRICE_QUOTE_EXPIRED", "This price quote has expired. Request a fresh price.");
        }
        ProductVariant variant = quote.priceVersion().variant();
        if (!variant.published()) {
            throw new BusinessConflictException("VARIANT_NOT_SELLABLE", "This variant is no longer available for checkout.");
        }
        checkoutExpiry.expireForVariant(variant.publicId());
        return new CheckoutQuote(quote.publicId(), quote.priceVersion().publicId(), variant,
                quote.amount(), quote.currency());
    }

    @Transactional(readOnly = true)
    public CurrentPrice currentForPos(SessionPrincipal actor, UUID variantId) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        VariantPrice price = prices.findEffectivePublished(variantId, clock.instant())
                .orElseThrow(() -> new ResourceNotFoundException("POS_VARIANT_NOT_FOUND", "Sellable variant not found."));
        return new CurrentPrice(price.variant(), price.publicId(), price.amount(), "VND");
    }

    public record QuoteView(UUID id, UUID variantId, UUID priceVersionId, long amount,
            String currency, Instant quotedAt, Instant expiresAt) { }
    public record CheckoutQuote(UUID quoteId, UUID priceVersionId, ProductVariant variant,
            long amount, String currency) { }
    public record CurrentPrice(ProductVariant variant, UUID priceVersionId, long amount, String currency) { }
}
