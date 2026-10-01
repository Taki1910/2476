package com.shoecommerce.pos;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.branch.Branch;
import com.shoecommerce.branch.BranchRepository;
import com.shoecommerce.branch.Location;
import com.shoecommerce.catalog.ProductVariant;
import com.shoecommerce.catalog.ProductVariantRepository;
import com.shoecommerce.fulfillment.PickupFulfillment;
import com.shoecommerce.fulfillment.PickupFulfillmentRepository;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.identity.UserAccountRepository;
import com.shoecommerce.inventory.InventoryBalance;
import com.shoecommerce.inventory.InventoryBalanceRepository;
import com.shoecommerce.inventory.StockMovement;
import com.shoecommerce.inventory.StockMovementRepository;
import com.shoecommerce.order.CustomerOrder;
import com.shoecommerce.order.CustomerOrderRepository;
import com.shoecommerce.order.CheckoutHoldExpiryService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.pricing.PriceQuoteService;

@Service
public class PosService {
    private final PosRegisterRepository registers;
    private final CashierShiftRepository shifts;
    private final PosCashSaleRepository sales;
    private final CashTenderRepository tenders;
    private final ProductVariantRepository variants;
    private final PriceQuoteService pricing;
    private final InventoryBalanceRepository balances;
    private final CustomerOrderRepository orders;
    private final PickupFulfillmentRepository fulfillments;
    private final StockMovementRepository movements;
    private final BranchRepository branches;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final CheckoutHoldExpiryService checkoutExpiry;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PosService(PosRegisterRepository registers, CashierShiftRepository shifts,
            PosCashSaleRepository sales, CashTenderRepository tenders, ProductVariantRepository variants,
            PriceQuoteService pricing, InventoryBalanceRepository balances, CustomerOrderRepository orders,
            PickupFulfillmentRepository fulfillments, StockMovementRepository movements,
            BranchRepository branches, UserAccountRepository accounts, AuthorizationPolicy authorization,
            AuditWriter audit, CheckoutHoldExpiryService checkoutExpiry, JdbcTemplate jdbc, Clock clock) {
        this.registers = registers; this.shifts = shifts; this.sales = sales; this.tenders = tenders;
        this.variants = variants; this.pricing = pricing; this.balances = balances; this.orders = orders;
        this.fulfillments = fulfillments; this.movements = movements; this.branches = branches;
        this.accounts = accounts; this.authorization = authorization; this.audit = audit;
        this.checkoutExpiry = checkoutExpiry; this.jdbc = jdbc; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RegisterView> registers(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        return registers.findAccessible(actor.accountId()).stream().map(PosService::view).toList();
    }

    @Transactional
    public ShiftView openShift(SessionPrincipal actor, UUID registerId) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        if (registerId == null) throw new InvalidRequestException("INVALID_REGISTER", "A Register is required.");
        accounts.findByPublicIdForUpdate(actor.publicId()).orElseThrow();
        PosRegister register = registers.findLockedByPublicId(registerId)
                .orElseThrow(() -> new ResourceNotFoundException("REGISTER_NOT_FOUND", "Register not found."));
        requireUsable(actor, register);
        if (shifts.findByCashierAccountIdAndStatus(actor.accountId(), CashierShift.Status.OPEN).isPresent()
                || shifts.existsByRegisterAndStatus(register, CashierShift.Status.OPEN)) {
            throw new BusinessConflictException("SHIFT_ALREADY_OPEN", "The cashier or Register already has an open Shift.");
        }
        CashierShift shift;
        try { shift = shifts.saveAndFlush(CashierShift.open(register, actor.accountId(), clock.instant())); }
        catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException("SHIFT_ALREADY_OPEN", "The cashier or Register already has an open Shift.");
        }
        Location location = register.location();
        audit.append(actor, "POS_SHIFT_OPENED", "CASHIER_SHIFT", shift.publicId(), location.branchId(), location.id(),
                Map.of("registerId", register.publicId(), "registerCode", register.code()));
        return view(shift, 0);
    }

    @Transactional
    public ShiftView closeShift(SessionPrincipal actor, UUID shiftId) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        CashierShift shift = ownedLockedShift(actor, shiftId);
        long expectedCash = tenders.expectedCash(shift).longValueExact();
        if (shift.close(clock.instant())) {
            Location location = shift.register().location();
            audit.append(actor, "POS_SHIFT_CLOSED", "CASHIER_SHIFT", shift.publicId(),
                    location.branchId(), location.id(), Map.of("expectedCash", expectedCash, "currency", "VND"));
        }
        return view(shift, expectedCash);
    }

    @Transactional(readOnly = true)
    public ShiftView currentShift(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        return shifts.findByCashierAccountIdAndStatus(actor.accountId(), CashierShift.Status.OPEN)
                .map(shift -> { requireUsable(actor, shift.register()); return view(shift, tenders.expectedCash(shift).longValueExact()); })
                .orElse(null);
    }

    @Transactional
    public VariantView lookup(SessionPrincipal actor, UUID shiftId, String sku) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        CashierShift shift = ownedShift(actor, shiftId);
        requireOpen(shift);
        String normalized = sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new InvalidRequestException("INVALID_SKU", "SKU must contain 1 to 64 characters.");
        }
        ProductVariant variant = variants.findBySku(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("POS_SKU_NOT_FOUND", "SKU not found."));
        checkoutExpiry.expireForVariant(variant.publicId());
        return candidate(shift, variant.publicId());
    }

    @Transactional
    public VariantView lookupBarcode(SessionPrincipal actor, UUID shiftId, String barcode) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        CashierShift shift = ownedShift(actor, shiftId);
        requireOpen(shift);
        String normalized = barcode == null ? "" : barcode.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new InvalidRequestException("INVALID_BARCODE", "Barcode must contain 1 to 128 characters.");
        }
        ProductVariant variant = variants.findByBarcode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("POS_BARCODE_NOT_FOUND", "Barcode not found."));
        checkoutExpiry.expireForVariant(variant.publicId());
        return candidate(shift, variant.publicId());
    }

    @Transactional
    public List<VariantView> search(SessionPrincipal actor, UUID shiftId, String query) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        CashierShift shift = ownedShift(actor, shiftId);
        requireOpen(shift);
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (search.isEmpty() || search.length() > 80) {
            throw new InvalidRequestException("INVALID_POS_SEARCH", "Search must contain 1 to 80 characters.");
        }
        List<String> terms = Arrays.stream(search.split("\\s+")).distinct().toList();
        String haystack = "LOWER(CONCAT(products.name,N' ',variants.sku,N' ',variants.color,N' ',variants.size))";
        String termFilter = terms.stream().map(ignored -> "CHARINDEX(?, " + haystack + ") > 0")
                .collect(Collectors.joining(" AND "));
        List<Object> parameters = new ArrayList<>();
        parameters.add(search);
        parameters.addAll(terms);
        parameters.add(search);
        parameters.add(search);
        List<UUID> ids = jdbc.query("""
                SELECT TOP (20) variants.public_id
                FROM catalog_product_variant variants
                JOIN catalog_product products ON products.id=variants.product_id
                WHERE LOWER(variants.sku)=?
                   OR (variants.lifecycle_status='PUBLISHED' AND %s)
                ORDER BY CASE WHEN LOWER(variants.sku)=? THEN 0 ELSE 1 END,
                         CASE WHEN CHARINDEX(?, LOWER(products.name)) > 0 THEN 0 ELSE 1 END,
                         products.name,variants.color,variants.normalized_size,variants.sku
                """.formatted(termFilter), (row, index) -> row.getObject(1, UUID.class), parameters.toArray());
        ids.forEach(checkoutExpiry::expireForVariant);
        return ids.stream().map(id -> candidate(shift, id)).toList();
    }

    @Transactional
    public SaleResult sell(SessionPrincipal actor, UUID shiftId, UUID variantId, String idempotencyKey) {
        return sell(actor, shiftId, variantId, null, idempotencyKey);
    }

    @Transactional
    public SaleResult sell(SessionPrincipal actor, UUID shiftId, UUID variantId, UUID expectedPriceVersionId,
            String idempotencyKey) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        String key = validateKey(idempotencyKey);
        CashierShift shift = ownedLockedShift(actor, shiftId);
        PosCashSale replay = sales.findByShiftAndIdempotencyKey(shift, key).orElse(null);
        if (replay != null) {
            if (!replay.variantId().equals(variantId)) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT", "This sale key belongs to a different request.");
            }
            return new SaleResult(receipt(replay), false);
        }
        requireOpen(shift);
        if (variantId == null) throw new InvalidRequestException("INVALID_POS_SALE", "A ProductVariant is required.");

        ProductVariant variant = variants.findLockedByPublicId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("POS_VARIANT_NOT_FOUND", "Sellable variant not found."));
        checkoutExpiry.expireForVariant(variant.publicId());
        if ("RETIRED".equals(variant.status())) {
            throw new BusinessConflictException("POS_VARIANT_RETIRED",
                    "This variant has been retired.", variant.publicId());
        }
        if (!variant.published()) {
            throw new BusinessConflictException("POS_VARIANT_NOT_PUBLISHED",
                    "This variant is not published for sale.", variant.publicId());
        }
        PriceQuoteService.CurrentPrice price;
        try {
            price = pricing.currentForPos(actor, variantId);
        } catch (ResourceNotFoundException missingPrice) {
            if (!"POS_VARIANT_NOT_FOUND".equals(missingPrice.code())) throw missingPrice;
            throw new BusinessConflictException("POS_PRICE_UNAVAILABLE",
                    "This variant has no current sale price.", variant.publicId());
        }
        if (expectedPriceVersionId != null && !expectedPriceVersionId.equals(price.priceVersionId())) {
            throw new BusinessConflictException("POS_PRICE_CHANGED",
                    "The price changed. Review the current price before completing the sale.", variant.publicId());
        }
        PosRegister register = shift.register();
        Location location = register.location();
        requireUsable(actor, register);
        InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location)
                .orElseThrow(() -> new BusinessConflictException("POS_SOLD_OUT_HERE", "This variant is sold out at the Register Location."));
        Instant now = clock.instant();
        try { balance.issueAvailable(1, now); }
        catch (IllegalStateException exception) {
            throw new BusinessConflictException("POS_SOLD_OUT_HERE", "The final unit was sold by another channel.");
        }

        Branch branch = branches.findByPublicId(location.branchPublicId()).filter(Branch::enabled)
                .orElseThrow(() -> new BusinessConflictException("REGISTER_UNAVAILABLE", "The Register Branch is unavailable."));
        CustomerOrder order = orders.save(CustomerOrder.createPos(branch.publicId(), price.priceVersionId(),
                variant.publicId(), location.publicId(), variant.sku(), variant.size(), variant.color(),
                price.amount(), now));
        PosCashSale sale = sales.save(PosCashSale.create(order, shift, variant.publicId(), key, now));
        CashTender tender = tenders.save(CashTender.accept(order, shift, price.amount(), now));
        String operationKey = sale.publicId().toString();
        fulfillments.save(PickupFulfillment.createPosHandedOver(order, branch, location,
                actor.publicId(), operationKey, now));
        movements.save(StockMovement.createPos(operationKey, order.publicId(), register.publicId(),
                shift.publicId(), actor.publicId(), variant.publicId(), location.publicId(), 1, now));

        Map<String, ?> facts = Map.of("orderId", order.publicId(), "shiftId", shift.publicId(),
                "registerId", register.publicId(), "variantId", variant.publicId(), "amount", price.amount(),
                "currency", price.currency());
        audit.append(actor, "POS_CASH_SALE", "ORDER", order.publicId(), branch.id(), location.id(), facts);
        audit.append(actor, "POS_CASH_TENDER_ACCEPTED", "CASH_TENDER", tender.publicId(), branch.id(), location.id(), facts);
        audit.append(actor, "POS_HANDOVER", "PICKUP_FULFILLMENT", order.publicId(), branch.id(), location.id(), facts);
        return new SaleResult(receipt(sale), true);
    }

    @Transactional(readOnly = true)
    public ReceiptView receipt(SessionPrincipal actor, UUID orderId) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        PosCashSale sale = sales.findOwnedReceipt(orderId, actor.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("POS_RECEIPT_NOT_FOUND", "Receipt not found."));
        authorization.requireLocationAccess(actor, sale.shift().register().location().publicId());
        return receipt(sale);
    }

    private CashierShift ownedShift(SessionPrincipal actor, UUID shiftId) {
        CashierShift shift = shifts.findByPublicId(shiftId)
                .filter(candidate -> candidate.ownedBy(actor.accountId()))
                .orElseThrow(() -> new ResourceNotFoundException("SHIFT_NOT_FOUND", "Shift not found."));
        requireUsable(actor, shift.register());
        return shift;
    }

    private CashierShift ownedLockedShift(SessionPrincipal actor, UUID shiftId) {
        CashierShift shift = shifts.findLockedByPublicId(shiftId)
                .filter(candidate -> candidate.ownedBy(actor.accountId()))
                .orElseThrow(() -> new ResourceNotFoundException("SHIFT_NOT_FOUND", "Shift not found."));
        requireUsable(actor, shift.register());
        return shift;
    }

    private void requireUsable(SessionPrincipal actor, PosRegister register) {
        Location location = register.location();
        authorization.requireLocationAccess(actor, location.publicId());
        if (!register.enabled() || !location.enabled()) {
            throw new BusinessConflictException("REGISTER_UNAVAILABLE", "The Register or Location is unavailable.");
        }
    }

    private static void requireOpen(CashierShift shift) {
        if (!shift.open()) throw new BusinessConflictException("SHIFT_CLOSED", "The Shift is closed.");
    }

    private ReceiptView receipt(PosCashSale sale) {
        CustomerOrder.ReceiptFacts order = sale.order().receiptFacts();
        CashTender tender = tenders.findByOrder(sale.order()).orElseThrow();
        PosRegister register = sale.shift().register();
        Location location = register.location();
        return new ReceiptView(order.orderId(), sale.publicId(), tender.publicId(), sale.shift().publicId(),
                register.publicId(), register.code(), location.publicId(), location.code(), location.name(),
                order.createdAt(), order.sku(), order.size(), order.color(), order.quantity(), order.unitPrice(), order.total(),
                order.currency(), "CASH", "HANDED_OVER");
    }

    private static RegisterView view(PosRegister register) {
        Location location = register.location();
        return new RegisterView(register.publicId(), register.code(), location.publicId(), location.code(),
                location.name(), location.branchPublicId());
    }

    private ShiftView view(CashierShift shift, long expectedCash) {
        return new ShiftView(shift.publicId(), view(shift.register()), shift.status(), shift.openedAt(),
                shift.closedAt(), expectedCash, "VND");
    }

    private static String validateKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty() || value.length() > 128) {
            throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 characters.");
        }
        return value;
    }

    private VariantView candidate(CashierShift shift, UUID variantId) {
        Location location = shift.register().location();
        Instant now = clock.instant();
        List<VariantView> rows = jdbc.query("""
                SELECT variants.public_id,products.name product_name,
                       COALESCE(products.primary_image,products.hero_image) thumbnail,
                       variants.sku,variants.barcode,variants.size,variants.color,variants.lifecycle_status,
                       prices.public_id price_version_id,prices.amount,
                       COALESCE(balances.on_hand,0) on_hand,COALESCE(balances.reserved,0) reserved
                FROM catalog_product_variant variants
                JOIN catalog_product products ON products.id=variants.product_id
                LEFT JOIN pricing_variant_price prices ON prices.variant_id=variants.id
                     AND prices.valid_from<=? AND (prices.valid_to IS NULL OR prices.valid_to>?)
                LEFT JOIN inventory_balance balances ON balances.variant_id=variants.id AND balances.location_id=?
                WHERE variants.public_id=?
                """, (row, index) -> {
            Long amount = row.getBigDecimal("amount") == null ? null : row.getBigDecimal("amount").longValueExact();
            long onHand = row.getLong("on_hand");
            long reserved = row.getLong("reserved");
            long available = onHand - reserved;
            return new VariantView(row.getObject("public_id", UUID.class), row.getString("product_name"),
                    row.getString("thumbnail"), row.getString("sku"), row.getString("barcode"),
                    row.getString("size"), row.getString("color"), row.getString("lifecycle_status"),
                    saleState(row.getString("lifecycle_status"), amount, available),
                    row.getObject("price_version_id", UUID.class), amount, "VND", onHand, reserved, available,
                    shift.register().publicId(), shift.register().code(), location.publicId(), location.code(),
                    location.name());
        }, Timestamp.from(now), Timestamp.from(now), location.id(), variantId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("POS_VARIANT_NOT_FOUND", "Variant not found.");
        return rows.getFirst();
    }

    static SaleState saleState(String lifecycle, Long amount, long available) {
        if ("RETIRED".equals(lifecycle)) return SaleState.RETIRED;
        if (!"PUBLISHED".equals(lifecycle)) return SaleState.NOT_PUBLISHED;
        if (amount == null) return SaleState.PRICE_UNAVAILABLE;
        return available > 0 ? SaleState.SELLABLE : SaleState.SOLD_OUT_HERE;
    }

    public record RegisterView(UUID id, String code, UUID locationId, String locationCode,
            String locationName, UUID branchId) { }
    public record ShiftView(UUID id, RegisterView register, String status, Instant openedAt,
            Instant closedAt, long expectedCash, String currency) { }
    public enum SaleState { SELLABLE, SOLD_OUT_HERE, RETIRED, NOT_PUBLISHED, PRICE_UNAVAILABLE }
    public record VariantView(UUID id, String productName, String thumbnail, String sku, String barcode,
            String size, String color, String lifecycleState, SaleState saleState, UUID priceVersionId,
            Long amount, String currency, long onHand, long reserved, long available,
            UUID registerId, String registerCode, UUID locationId, String locationCode, String locationName) { }
    public record ReceiptView(UUID orderId, UUID saleId, UUID tenderId, UUID shiftId, UUID registerId,
            String registerCode, UUID locationId, String locationCode, String locationName, Instant soldAt,
            String sku, String size, String color, long quantity, long unitPrice, long total, String currency,
            String tender, String fulfillmentStatus) { }
    public record SaleResult(ReceiptView receipt, boolean created) { }
}
