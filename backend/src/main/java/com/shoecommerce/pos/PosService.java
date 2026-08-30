package com.shoecommerce.pos;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final Clock clock;

    public PosService(PosRegisterRepository registers, CashierShiftRepository shifts,
            PosCashSaleRepository sales, CashTenderRepository tenders, ProductVariantRepository variants,
            PriceQuoteService pricing, InventoryBalanceRepository balances, CustomerOrderRepository orders,
            PickupFulfillmentRepository fulfillments, StockMovementRepository movements,
            BranchRepository branches, UserAccountRepository accounts, AuthorizationPolicy authorization,
            AuditWriter audit, Clock clock) {
        this.registers = registers; this.shifts = shifts; this.sales = sales; this.tenders = tenders;
        this.variants = variants; this.pricing = pricing; this.balances = balances; this.orders = orders;
        this.fulfillments = fulfillments; this.movements = movements; this.branches = branches;
        this.accounts = accounts; this.authorization = authorization; this.audit = audit; this.clock = clock;
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

    @Transactional(readOnly = true)
    public VariantView lookup(SessionPrincipal actor, UUID shiftId, String sku) {
        authorization.requirePermission(actor, PermissionCode.POS_SELL);
        CashierShift shift = ownedShift(actor, shiftId);
        requireOpen(shift);
        String normalized = sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new InvalidRequestException("INVALID_SKU", "SKU must contain 1 to 64 characters.");
        }
        ProductVariant variant = variants.findBySku(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("POS_VARIANT_NOT_FOUND", "Sellable variant not found."));
        var price = pricing.currentForPos(actor, variant.publicId());
        Location location = shift.register().location();
        long available = balances.findByVariantAndLocation(variant, location).map(InventoryBalance::available).orElse(0L);
        return new VariantView(variant.publicId(), variant.product().name(), variant.sku(), variant.size(),
                variant.color(), price.priceVersionId(), price.amount(), price.currency(), available,
                shift.register().publicId(), location.publicId());
    }

    @Transactional
    public SaleResult sell(SessionPrincipal actor, UUID shiftId, UUID variantId, String idempotencyKey) {
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

        var price = pricing.currentForPos(actor, variantId);
        ProductVariant variant = price.variant();
        PosRegister register = shift.register();
        Location location = register.location();
        requireUsable(actor, register);
        InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location)
                .orElseThrow(() -> new BusinessConflictException("INSUFFICIENT_INVENTORY", "This variant is not available at the Register Location."));
        Instant now = clock.instant();
        try { balance.issueAvailable(1, now); }
        catch (IllegalStateException exception) {
            throw new BusinessConflictException("INSUFFICIENT_INVENTORY", "The final unit was sold by another channel.");
        }

        Branch branch = branches.findByPublicId(location.branchPublicId()).filter(Branch::enabled)
                .orElseThrow(() -> new BusinessConflictException("REGISTER_UNAVAILABLE", "The Register Branch is unavailable."));
        CustomerOrder order = orders.save(CustomerOrder.createPos(branch.publicId(), price.priceVersionId(),
                variant.publicId(), location.publicId(), variant.sku(), variant.size(), price.amount(), now));
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
                order.createdAt(), order.sku(), order.size(), order.quantity(), order.unitPrice(), order.total(),
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

    public record RegisterView(UUID id, String code, UUID locationId, String locationCode,
            String locationName, UUID branchId) { }
    public record ShiftView(UUID id, RegisterView register, String status, Instant openedAt,
            Instant closedAt, long expectedCash, String currency) { }
    public record VariantView(UUID id, String productName, String sku, String size, String color,
            UUID priceVersionId, long amount, String currency, long available, UUID registerId, UUID locationId) { }
    public record ReceiptView(UUID orderId, UUID saleId, UUID tenderId, UUID shiftId, UUID registerId,
            String registerCode, UUID locationId, String locationCode, String locationName, Instant soldAt,
            String sku, String size, long quantity, long unitPrice, long total, String currency,
            String tender, String fulfillmentStatus) { }
    public record SaleResult(ReceiptView receipt, boolean created) { }
}
