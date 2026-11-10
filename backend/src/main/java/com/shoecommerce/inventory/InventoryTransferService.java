package com.shoecommerce.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
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
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class InventoryTransferService {
    private final InventoryTransferRepository transfers;
    private final InventoryTransferCommandRepository commands;
    private final InventoryBalanceRepository balances;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;
    private final LocationRepository locations;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    InventoryTransferService(InventoryTransferRepository transfers, InventoryTransferCommandRepository commands,
            InventoryBalanceRepository balances, StockMovementRepository movements,
            ProductVariantRepository variants, LocationRepository locations, UserAccountRepository accounts,
            AuthorizationPolicy authorization, AuditWriter audit, JdbcTemplate jdbc, Clock clock) {
        this.transfers = transfers;
        this.commands = commands;
        this.balances = balances;
        this.movements = movements;
        this.variants = variants;
        this.locations = locations;
        this.accounts = accounts;
        this.authorization = authorization;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public TransferView create(SessionPrincipal actor, CreateCommand request, String rawKey) {
        lockActor(actor);
        authorization.requirePermission(actor, PermissionCode.INVENTORY_TRANSFER);
        ValidCreate valid = validate(request);
        requireScopes(actor, valid.source(), valid.destination());
        String key = key(rawKey);
        String canonicalLines = valid.lines().stream()
                .sorted(Comparator.comparing(line -> line.variantId().toString()))
                .map(line -> line.variantId() + ":" + line.quantity()).collect(Collectors.joining(","));
        String fingerprint = fingerprint("CREATE", valid.source().publicId(), valid.destination().publicId(),
                canonicalLines);
        InventoryTransferCommand known = commands.findByActorIdAndKey(actor.publicId(), key).orElse(null);
        if (known != null) return replay(known, InventoryTransferCommand.Action.CREATE, fingerprint, actor);

        Instant now = clock.instant();
        InventoryTransfer transfer = transfers.saveAndFlush(InventoryTransfer.create(valid.source().publicId(),
                valid.destination().publicId(), valid.lines().stream()
                        .map(line -> new InventoryTransfer.LineDraft(line.variantId(), line.quantity())).toList(),
                actor.publicId(), now));
        commands.saveAndFlush(InventoryTransferCommand.create(transfer.publicId(), actor.publicId(), key,
                InventoryTransferCommand.Action.CREATE, fingerprint, now));
        audit.append(actor, "INVENTORY_TRANSFER_CREATED", "INVENTORY_TRANSFER", transfer.publicId(),
                valid.source().branchId(), valid.source().id(), Map.of("sourceLocationId", valid.source().publicId(),
                        "destinationLocationId", valid.destination().publicId(), "lineCount", valid.lines().size()));
        return view(transfer, valid.source(), valid.destination(), false);
    }

    @Transactional
    public TransferView dispatch(SessionPrincipal actor, UUID transferId, String rawKey) {
        lockActor(actor);
        authorization.requirePermission(actor, PermissionCode.INVENTORY_TRANSFER);
        String key = key(rawKey);
        String fingerprint = fingerprint("DISPATCH", transferId);
        InventoryTransferCommand known = commands.findByActorIdAndKey(actor.publicId(), key).orElse(null);
        if (known != null) return replay(known, InventoryTransferCommand.Action.DISPATCH, fingerprint, actor);
        InventoryTransfer transfer = locked(transferId);
        Locations scope = locations(transfer);
        requireScopes(actor, scope.source(), scope.destination());
        requireStatus(transfer, InventoryTransfer.Status.DRAFT);
        LockedBalances locked = lockBalances(transfer, scope, true);
        for (InventoryTransferLine line : transfer.lines()) {
            InventoryBalance source = locked.get(line.variantId(), transfer.sourceLocationId());
            if (source == null || line.requestedQuantity() > source.available()) {
                throw new BusinessConflictException("INSUFFICIENT_STOCK",
                        "Source inventory is not available in the requested quantity.", line.variantId());
            }
        }
        Instant now = clock.instant();
        for (InventoryTransferLine line : transfer.lines()) {
            InventoryBalance source = locked.get(line.variantId(), transfer.sourceLocationId());
            long before = source.onHand();
            source.issueAvailable(line.requestedQuantity(), now);
            movements.save(StockMovement.createTransfer(StockMovement.Type.TRANSFER_OUT,
                    operationKey(transfer.publicId(), "OUT", line.variantId()), transfer.publicId(), actor.publicId(),
                    line.variantId(), transfer.sourceLocationId(), line.requestedQuantity(), before, source.onHand(),
                    source.reserved(), now));
        }
        transfer.dispatch(actor.publicId(), now);
        transfers.flush();
        commands.saveAndFlush(InventoryTransferCommand.create(transfer.publicId(), actor.publicId(), key,
                InventoryTransferCommand.Action.DISPATCH, fingerprint, now));
        audit.append(actor, "INVENTORY_TRANSFER_DISPATCHED", "INVENTORY_TRANSFER", transfer.publicId(),
                scope.source().branchId(), scope.source().id(), Map.of("sourceLocationId", transfer.sourceLocationId(),
                        "destinationLocationId", transfer.destinationLocationId(), "quantity", transfer.inTransitQuantity()));
        return view(transfer, scope.source(), scope.destination(), false);
    }

    @Transactional
    public TransferView receive(SessionPrincipal actor, UUID transferId, String rawKey) {
        lockActor(actor);
        authorization.requirePermission(actor, PermissionCode.INVENTORY_TRANSFER);
        String key = key(rawKey);
        String fingerprint = fingerprint("RECEIVE", transferId);
        InventoryTransferCommand known = commands.findByActorIdAndKey(actor.publicId(), key).orElse(null);
        if (known != null) return replay(known, InventoryTransferCommand.Action.RECEIVE, fingerprint, actor);
        InventoryTransfer transfer = locked(transferId);
        Locations scope = locations(transfer);
        requireScopes(actor, scope.source(), scope.destination());
        requireStatus(transfer, InventoryTransfer.Status.DISPATCHED);
        LockedBalances locked = lockBalances(transfer, scope, true);
        Instant now = clock.instant();
        for (InventoryTransferLine line : transfer.lines()) {
            InventoryBalance destination = locked.get(line.variantId(), transfer.destinationLocationId());
            long before = destination.onHand();
            try {
                destination.receive(line.dispatchedQuantity(), now);
            } catch (ArithmeticException overflow) {
                throw new InvalidRequestException("INVENTORY_QUANTITY_OVERFLOW", "Transfer quantity is too large.");
            }
            movements.save(StockMovement.createTransfer(StockMovement.Type.TRANSFER_IN,
                    operationKey(transfer.publicId(), "IN", line.variantId()), transfer.publicId(), actor.publicId(),
                    line.variantId(), transfer.destinationLocationId(), line.dispatchedQuantity(), before,
                    destination.onHand(), destination.reserved(), now));
        }
        transfer.receive(actor.publicId(), now);
        transfers.flush();
        commands.saveAndFlush(InventoryTransferCommand.create(transfer.publicId(), actor.publicId(), key,
                InventoryTransferCommand.Action.RECEIVE, fingerprint, now));
        audit.append(actor, "INVENTORY_TRANSFER_RECEIVED", "INVENTORY_TRANSFER", transfer.publicId(),
                scope.destination().branchId(), scope.destination().id(), Map.of(
                        "sourceLocationId", transfer.sourceLocationId(),
                        "destinationLocationId", transfer.destinationLocationId(),
                        "quantity", transfer.lines().stream().mapToLong(InventoryTransferLine::receivedQuantity).sum()));
        return view(transfer, scope.source(), scope.destination(), false);
    }

    @Transactional(readOnly = true)
    public List<TransferView> list(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.INVENTORY_TRANSFER);
        List<UUID> ids = jdbc.queryForList("""
                SELECT TOP (100) transfers.public_id FROM inventory_transfer transfers
                WHERE EXISTS (SELECT 1 FROM iam_staff_assignment source_scope
                    JOIN org_location source_location ON source_location.id=source_scope.location_id
                    JOIN org_branch source_branch ON source_branch.id=source_location.branch_id
                    WHERE source_scope.account_id=? AND source_scope.active=1 AND source_location.enabled=1
                      AND source_branch.enabled=1 AND source_location.public_id=transfers.source_location_public_id)
                  AND EXISTS (SELECT 1 FROM iam_staff_assignment destination_scope
                    JOIN org_location destination_location ON destination_location.id=destination_scope.location_id
                    JOIN org_branch destination_branch ON destination_branch.id=destination_location.branch_id
                    WHERE destination_scope.account_id=? AND destination_scope.active=1 AND destination_location.enabled=1
                      AND destination_branch.enabled=1 AND destination_location.public_id=transfers.destination_location_public_id)
                ORDER BY transfers.created_at DESC, transfers.public_id
                """, UUID.class, actor.accountId(), actor.accountId());
        return ids.stream().map(id -> {
            InventoryTransfer transfer = transfers.findByPublicId(id).orElseThrow();
            Locations scope = locations(transfer);
            return view(transfer, scope.source(), scope.destination(), false);
        }).toList();
    }

    @Transactional(readOnly = true)
    public TransferOptions options(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.INVENTORY_TRANSFER);
        List<LocationOption> locationOptions = jdbc.query("""
                SELECT DISTINCT branches.public_id branch_id, branches.code branch_code,
                       locations.public_id location_id, locations.code location_code, locations.name location_name
                FROM iam_staff_assignment assignments
                JOIN org_location locations ON locations.id=assignments.location_id AND locations.enabled=1
                JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                WHERE assignments.account_id=? AND assignments.active=1
                ORDER BY branches.code, locations.code
                """, (rs, row) -> new LocationOption(rs.getObject("branch_id", UUID.class),
                        rs.getString("branch_code"), rs.getObject("location_id", UUID.class),
                        rs.getString("location_code"), rs.getString("location_name")), actor.accountId());
        List<StockOption> stock = jdbc.query("""
                SELECT locations.public_id location_id, variants.public_id variant_id, variants.sku,
                       products.name product_name, balances.on_hand, balances.reserved,
                       balances.on_hand-balances.reserved available
                FROM iam_staff_assignment assignments
                JOIN org_location locations ON locations.id=assignments.location_id AND locations.enabled=1
                JOIN org_branch branches ON branches.id=locations.branch_id AND branches.enabled=1
                JOIN inventory_balance balances ON balances.location_id=locations.id
                JOIN catalog_product_variant variants ON variants.id=balances.variant_id
                JOIN catalog_product products ON products.id=variants.product_id
                WHERE assignments.account_id=? AND assignments.active=1
                ORDER BY locations.code, variants.sku, variants.public_id
                """, (rs, row) -> new StockOption(rs.getObject("location_id", UUID.class),
                        rs.getObject("variant_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                        rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("available")), actor.accountId());
        return new TransferOptions(locationOptions, stock);
    }

    private TransferView replay(InventoryTransferCommand known, InventoryTransferCommand.Action action,
            String fingerprint, SessionPrincipal actor) {
        if (!known.matches(action, fingerprint)) throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT",
                "This idempotency key was already used for another inventory transfer command.");
        InventoryTransfer transfer = transfers.findByPublicId(known.transferId()).orElseThrow();
        Locations scope = locations(transfer);
        requireScopes(actor, scope.source(), scope.destination());
        return view(transfer, scope.source(), scope.destination(), true);
    }

    private ValidCreate validate(CreateCommand request) {
        if (request == null || request.sourceLocationId() == null || request.destinationLocationId() == null
                || request.lines() == null || request.lines().isEmpty() || request.lines().size() > 100) {
            throw new InvalidRequestException("INVALID_INVENTORY_TRANSFER", "Transfer locations and 1 to 100 lines are required.");
        }
        Location source = activeLocation(request.sourceLocationId());
        Location destination = activeLocation(request.destinationLocationId());
        if (source.publicId().equals(destination.publicId())) throw new BusinessConflictException(
                "INVENTORY_TRANSFER_SAME_LOCATION", "Source and destination locations must be different.");
        List<InventoryTransfer.LineDraft> lines = request.lines().stream()
                .map(line -> line == null ? null : new InventoryTransfer.LineDraft(line.variantId(), line.quantity()))
                .toList();
        try {
            InventoryTransfer.create(source.publicId(), destination.publicId(), lines, UUID.randomUUID(), clock.instant());
        } catch (BusinessConflictException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("INVALID_INVENTORY_TRANSFER", exception.getMessage());
        }
        List<UUID> variantIds = request.lines().stream().map(LineCommand::variantId).toList();
        if (variants.findAllByPublicId(variantIds).size() != variantIds.size()) {
            throw new ResourceNotFoundException("VARIANT_NOT_FOUND", "One or more variants were not found.");
        }
        return new ValidCreate(source, destination, request.lines());
    }

    private LockedBalances lockBalances(InventoryTransfer transfer, Locations scope, boolean createDestination) {
        List<UUID> ids = transfer.lines().stream().map(InventoryTransferLine::variantId).toList();
        List<ProductVariant> lockedVariants = variants.findAllLockedByPublicId(ids);
        if (lockedVariants.size() != ids.size()) throw new ResourceNotFoundException("VARIANT_NOT_FOUND", "Variant not found.");
        List<Location> orderedLocations = List.of(scope.source(), scope.destination()).stream()
                .sorted(Comparator.comparing(Location::id)).toList();
        Map<BalanceKey, InventoryBalance> locked = new HashMap<>();
        for (Location location : orderedLocations) {
            for (ProductVariant variant : lockedVariants) {
                InventoryBalance balance = balances.findLockedByVariantAndLocation(variant, location).orElse(null);
                if (balance == null && createDestination && location.publicId().equals(transfer.destinationLocationId())) {
                    balance = balances.saveAndFlush(InventoryBalance.create(variant, location, 0, clock.instant()));
                }
                if (balance != null) locked.put(new BalanceKey(variant.publicId(), location.publicId()), balance);
            }
        }
        return new LockedBalances(locked);
    }

    private TransferView view(InventoryTransfer transfer, Location source, Location destination, boolean replayed) {
        Map<UUID, ProductVariant> found = new HashMap<>();
        variants.findAllByPublicId(transfer.lines().stream().map(InventoryTransferLine::variantId).toList())
                .forEach(variant -> found.put(variant.publicId(), variant));
        List<LineView> lines = transfer.lines().stream().map(line -> {
            ProductVariant variant = found.get(line.variantId());
            if (variant == null) throw new IllegalStateException("Transfer variant is unavailable");
            return new LineView(line.variantId(), variant.sku(), line.requestedQuantity(),
                    line.dispatchedQuantity(), line.receivedQuantity());
        }).toList();
        return new TransferView(transfer.publicId(), source.publicId(), source.code(), source.name(),
                destination.publicId(), destination.code(), destination.name(), transfer.status().name(),
                transfer.createdAt(), transfer.dispatchedAt(), transfer.receivedAt(), lines, replayed);
    }

    private InventoryTransfer locked(UUID id) {
        if (id == null) throw new ResourceNotFoundException("INVENTORY_TRANSFER_NOT_FOUND", "Inventory transfer not found.");
        return transfers.findLockedByPublicId(id).orElseThrow(() ->
                new ResourceNotFoundException("INVENTORY_TRANSFER_NOT_FOUND", "Inventory transfer not found."));
    }
    private Locations locations(InventoryTransfer transfer) {
        return new Locations(activeLocation(transfer.sourceLocationId()), activeLocation(transfer.destinationLocationId()));
    }
    private Location activeLocation(UUID id) {
        return locations.findByPublicId(id).filter(Location::enabled).orElseThrow(() ->
                new ResourceNotFoundException("LOCATION_NOT_FOUND", "Location not found or disabled."));
    }
    private void requireScopes(SessionPrincipal actor, Location source, Location destination) {
        authorization.requireLocationAccess(actor, source.publicId());
        authorization.requireLocationAccess(actor, destination.publicId());
    }
    private static void requireStatus(InventoryTransfer transfer, InventoryTransfer.Status expected) {
        if (transfer.status() != expected) throw new BusinessConflictException("INVENTORY_TRANSFER_INVALID_STATE",
                "Inventory transfer cannot perform this action from state " + transfer.status() + ".");
    }
    private void lockActor(SessionPrincipal actor) {
        accounts.findByPublicIdForUpdate(actor.publicId())
                .orElseThrow(() -> new IllegalStateException("Inventory transfer account not found"));
    }
    private static String key(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.isEmpty() || key.length() > 128) throw new InvalidRequestException("INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must contain 1 to 128 characters.");
        return key;
    }
    private static String operationKey(UUID transferId, String direction, UUID variantId) {
        return "T:" + transferId + ":" + direction + ":" + variantId;
    }
    private static String fingerprint(Object... parts) {
        StringBuilder canonical = new StringBuilder();
        for (Object part : parts) canonical.append(part).append('|');
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record LineCommand(UUID variantId, long quantity) { }
    public record CreateCommand(UUID sourceLocationId, UUID destinationLocationId, List<LineCommand> lines) { }
    public record LineView(UUID variantId, String sku, long requestedQuantity, long dispatchedQuantity,
            long receivedQuantity) { }
    public record TransferView(UUID transferId, UUID sourceLocationId, String sourceLocationCode,
            String sourceLocationName, UUID destinationLocationId, String destinationLocationCode,
            String destinationLocationName, String status, Instant createdAt, Instant dispatchedAt,
            Instant receivedAt, List<LineView> items, boolean replayed) { }
    public record LocationOption(UUID branchId, String branchCode, UUID locationId, String locationCode,
            String locationName) { }
    public record StockOption(UUID locationId, UUID variantId, String sku, String productName,
            long onHand, long reserved, long available) { }
    public record TransferOptions(List<LocationOption> locations, List<StockOption> stock) { }
    private record ValidCreate(Location source, Location destination, List<LineCommand> lines) { }
    private record Locations(Location source, Location destination) { }
    private record BalanceKey(UUID variantId, UUID locationId) { }
    private record LockedBalances(Map<BalanceKey, InventoryBalance> values) {
        InventoryBalance get(UUID variantId, UUID locationId) { return values.get(new BalanceKey(variantId, locationId)); }
    }
}
