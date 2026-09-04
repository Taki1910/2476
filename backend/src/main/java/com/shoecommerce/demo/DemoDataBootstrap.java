package com.shoecommerce.demo;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoDataBootstrap implements CommandLineRunner {
    private static final String PASSWORD = "DemoPass!2026";
    private static final UUID BRANCH = uuid("branch-a");
    private static final UUID FLOOR = uuid("location-floor");
    private static final UUID STOCKROOM = uuid("location-stockroom");
    private static final List<ProductSeed> PRODUCTS = List.of(
            new ProductSeed("court-classic", "Court Classic", "CC", "White / Black", 1_490_000, List.of("39", "40", "41", "42"), List.of(8, 5, 2, 0), "Court", "Court Originals", true, false, true, 1, 120, "/products/court-classic.png", "/products/court-classic.png"),
            new ProductSeed("metro-runner", "Metro Runner", "MR", "Silver / Ink", 1_790_000, List.of("40", "41", "42", "43"), List.of(6, 4, 1, 0), "Running", "Metro Motion", true, false, true, 3, 120, "/products/metro-runner.png", "/products/metro-runner.png"),
            new ProductSeed("studio-low", "Studio Low", "SL", "Ivory / Gum", 1_350_000, List.of("38", "39", "40", "41"), List.of(7, 3, 2, 0), "Smart Casual", "Everyday Form", false, false, true, 8, 120, "/products/studio-low.png", "/products/studio-low.png"),
            new ProductSeed("trail-form", "Trail Form", "TF", "Moss / Sand", 2_090_000, List.of("40", "41", "42", "43"), List.of(5, 3, 1, 0), "Trail", "Trail Series", false, false, true, 9, 120, "/products/trail-form.png", "/products/trail-form.png"),
            new ProductSeed("after-dark", "After Dark", "AD", "Black / Pink", 1_890_000, List.of("39", "40", "41", "42"), List.of(4, 3, 2, 0), "Lifestyle", "After Hours", true, false, true, 2, 120, "/products/after-dark.png", "/products/after-dark.png"),
            new ProductSeed("daily-canvas", "Daily Canvas", "DC", "Natural / Navy", 990_000, List.of("38", "39", "40", "41"), List.of(9, 6, 2, 0), "Canvas", "Daily Essentials", true, false, true, 5, 120, "/products/daily-canvas.png", "/products/daily-canvas.png"),
            new ProductSeed("court-high", "Court High", "CH", "Chalk / Red", 1_690_000, List.of("40", "41", "42", "43"), List.of(6, 4, 1, 0), "Court", "Court Originals", false, false, true, 4, 120, "/products/court-high.png", "/products/court-high.png"),
            new ProductSeed("pace-knit", "Pace Knit", "PK", "Stone / Lime", 1_590_000, List.of("39", "40", "41", "42"), List.of(7, 5, 2, 0), "Running", "Metro Motion", false, false, true, 10, 120, "/products/pace-knit.png", "/products/pace-knit.png"),
            new ProductSeed("urban-hiker", "Urban Hiker", "UH", "Black / Olive", 1_890_000, List.of("40", "41", "42", "43", "44"), List.of(4, 4, 3, 2, 1), "Trail", "Trail Series", false, true, true, 11, 14, "/products/trail-form.png", "/products/trail-form.png"),
            new ProductSeed("city-loafer", "City Loafer", "CL", "Brown Leather", 2_190_000, List.of("39", "40", "41", "42"), List.of(3, 4, 3, 1), "Slip-on", "City Edit", false, true, true, 12, 10, "/products/studio-low.png", "/products/studio-low.png"),
            new ProductSeed("lite-runner", "Lite Runner", "LR", "Grey / White", 1_290_000, List.of("39", "40", "41", "42"), List.of(5, 5, 3, 2), "Running", "Metro Motion", false, true, true, 13, 9, "/products/pace-knit.png", "/products/pace-knit.png"),
            new ProductSeed("street-high", "Street High", "SH", "All Black", 1_690_000, List.of("40", "41", "42", "43"), List.of(3, 4, 2, 1), "Lifestyle", "After Hours", false, true, true, 14, 8, "/products/after-dark.png", "/products/after-dark.png"),
            new ProductSeed("canvas-low", "Canvas Low", "CLOW", "Beige", 890_000, List.of("38", "39", "40", "41"), List.of(5, 6, 4, 2), "Canvas", "Daily Essentials", false, true, true, 15, 7, "/products/daily-canvas.png", "/products/daily-canvas.png"),
            new ProductSeed("basket-retro", "Basket Retro", "BR", "White / Green", 1_390_000, List.of("39", "40", "41", "42"), List.of(3, 5, 3, 1), "Court", "Court Originals", false, true, true, 16, 6, "/products/court-classic.png", "/products/court-classic.png"),
            new ProductSeed("skate-pro", "Skate Pro", "SP", "Black / Gum", 1_190_000, List.of("39", "40", "41", "42", "43"), List.of(4, 5, 3, 2, 1), "Lifestyle", "Street Utility", false, true, true, 17, 5, "/products/studio-low.png", "/products/studio-low.png"),
            new ProductSeed("trail-edge", "Trail Edge", "TE", "Navy / Orange", 1_790_000, List.of("40", "41", "42", "43"), List.of(4, 3, 2, 1), "Trail", "Trail Series", false, true, true, 18, 4, "/products/trail-form.png", "/products/trail-form.png"),
            new ProductSeed("form-trainer", "Form Trainer", "FT", "Black / Sand", 1_490_000, List.of("39", "40", "41", "42"), List.of(4, 3, 2, 1), "Training", "Motion Lab", false, true, true, 19, 3, "/products/pace-knit.png", "/products/pace-knit.png"),
            new ProductSeed("archive-one", "Archive One", "AO", "Black / Silver", 2_490_000, List.of("40", "41", "42"), List.of(2, 2, 1), "Limited", "Archive 01", true, false, false, 6, 120, "/products/after-dark.png", "/products/after-dark.png"));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private UUID branchPublicId = BRANCH;
    private UUID floorPublicId = FLOOR;
    private UUID stockroomPublicId = STOCKROOM;

    public DemoDataBootstrap(JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        seedAccounts(now);
        seedLocations(now);
        seedCatalog(now);
        seedFitProfiles(now);
        seedOnlineScenarios(now);
        seedPosScenarios(now);
        seedSalesHistory(now);
        synchronizeReserved(now);
    }

    private void seedAccounts(LocalDateTime now) {
        account("customer.demo", "CUSTOMER", now);
        account("customer.second", "CUSTOMER", now);
        account("cashier.demo", "CASHIER", now);
        account("operations.demo", "OPERATIONS", now);
        account("manager.demo", "OPERATIONS", now);
    }

    private void seedLocations(LocalDateTime now) {
        insert("IF NOT EXISTS (SELECT 1 FROM org_branch WHERE code = 'DEMO-A') INSERT INTO org_branch(public_id, code, name, enabled, created_at) VALUES (?, 'DEMO-A', N'Demo Branch A', 1, ?)", BRANCH, now);
        long branchId = id("SELECT id FROM org_branch WHERE code = 'DEMO-A'");
        branchPublicId = jdbc.queryForObject("SELECT public_id FROM org_branch WHERE code = 'DEMO-A'", UUID.class);
        floorPublicId = location(branchId, FLOOR, "DEMO-FLOOR", "Demo Sales Floor", now);
        stockroomPublicId = location(branchId, STOCKROOM, "DEMO-STOCK", "Demo Stockroom", now);
        long floorId = id("SELECT id FROM org_location WHERE public_id = ?", floorPublicId);
        long stockroomId = id("SELECT id FROM org_location WHERE public_id = ?", stockroomPublicId);
        assign("cashier.demo", branchId, floorId, now);
        for (String login : List.of("operations.demo", "manager.demo")) {
            assign(login, branchId, floorId, now);
            assign(login, branchId, stockroomId, now);
        }
        register("DEMO-01", floorId, now);
        register("DEMO-02", floorId, now);
        register("DEMO-STOCK-01", stockroomId, now);
    }

    private void seedCatalog(LocalDateTime now) {
        long floorId = id("SELECT id FROM org_location WHERE public_id = ?", floorPublicId);
        long stockroomId = id("SELECT id FROM org_location WHERE public_id = ?", stockroomPublicId);
        for (ProductSeed product : PRODUCTS) {
            UUID productPublicId = uuid("product-" + product.key());
            insert("IF NOT EXISTS (SELECT 1 FROM catalog_product WHERE public_id = ?) INSERT INTO catalog_product(public_id, name, category, collection, featured, new_arrival, campaign_eligible, merchandising_rank, hero_image, primary_image, entity_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
                    productPublicId, productPublicId, product.name(), product.category(), product.collection(), product.featured(),
                    product.newArrival(), product.campaignEligible(), product.merchandisingRank(), product.heroImage(),
                    product.primaryImage(), now.minusDays(product.createdDaysAgo()));
            insert("UPDATE catalog_product SET category = ?, collection = ?, featured = ?, new_arrival = ?, campaign_eligible = ?, merchandising_rank = ?, hero_image = ?, primary_image = ? WHERE public_id = ?",
                    product.category(), product.collection(), product.featured(), product.newArrival(), product.campaignEligible(),
                    product.merchandisingRank(), product.heroImage(), product.primaryImage(), productPublicId);
            long productId = id("SELECT id FROM catalog_product WHERE public_id = ?", productPublicId);
            for (int index = 0; index < product.sizes().size(); index++) {
                String size = product.sizes().get(index);
                String sku = "DEMO-" + product.code() + "-" + size;
                UUID variantPublicId = uuid("variant-" + sku);
                variant(productId, variantPublicId, sku, size, product.color(), product.price(), now);
                balance(variantPublicId, floorId, product.floorStock().get(index), now);
                balance(variantPublicId, stockroomId, Math.max(0, 5 - index), now);
                if (index == 0) priceHistory(variantPublicId, product.price() - 100_000, now);
            }
        }
    }

    private void seedFitProfiles(LocalDateTime now) {
        fitProfile(now, "court-classic", "TRUE_TO_SIZE", "REGULAR", List.of(
                new FitRange("39", 242, 249, 86, 96), new FitRange("40", 249, 256, 88, 99),
                new FitRange("41", 256, 263, 90, 101), new FitRange("42", 263, 270, 92, 103)));
        fitProfile(now, "metro-runner", "TRUE_TO_SIZE", "WIDE", List.of(
                new FitRange("40", 246, 253, 90, 108), new FitRange("41", 253, 260, 92, 110),
                new FitRange("42", 260, 267, 94, 112), new FitRange("43", 267, 274, 96, 114)));
        fitProfile(now, "after-dark", "RUNS_SMALL", "NARROW", List.of(
                new FitRange("39", 236, 244, 82, 91), new FitRange("40", 244, 251, 84, 93),
                new FitRange("41", 251, 258, 86, 95), new FitRange("42", 258, 265, 88, 97)));
        fitProfile(now, "city-loafer", "RUNS_LARGE", "WIDE", List.of(
                new FitRange("39", 248, 255, 90, 106), new FitRange("40", 255, 262, 92, 108),
                new FitRange("41", 262, 269, 94, 110), new FitRange("42", 269, 276, 96, 112)));
        fitProfile(now, "trail-form", "TRUE_TO_SIZE", "REGULAR", List.of(
                new FitRange("40", 246, 253, 88, 99), new FitRange("41", 253, 260, 90, 101),
                new FitRange("42", 260, 267, 92, 103), new FitRange("43", 267, 274, 94, 105)));
    }

    private void fitProfile(LocalDateTime now, String productKey, String tendency, String width,
            List<FitRange> ranges) {
        ProductSeed product = PRODUCTS.stream().filter(candidate -> candidate.key().equals(productKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fit product: " + productKey));
        UUID productPublicId = uuid("product-" + product.key());
        UUID profilePublicId = uuid("fit-profile-" + product.key());
        insert("IF NOT EXISTS (SELECT 1 FROM catalog_shoe_fit_profile WHERE product_id = (SELECT id FROM catalog_product WHERE public_id = ?)) INSERT INTO catalog_shoe_fit_profile(public_id, product_id, size_system, fit_tendency, width_profile, created_at) VALUES (?, (SELECT id FROM catalog_product WHERE public_id = ?), 'EU', ?, ?, ?)",
                productPublicId, profilePublicId, productPublicId, tendency, width, now);
        long profileId = id("SELECT id FROM catalog_shoe_fit_profile WHERE product_id = (SELECT id FROM catalog_product WHERE public_id = ?)", productPublicId);
        for (FitRange range : ranges) {
            insert("IF NOT EXISTS (SELECT 1 FROM catalog_shoe_fit_size_range WHERE profile_id = ? AND size_label = ?) INSERT INTO catalog_shoe_fit_size_range(profile_id, size_label, min_foot_length_mm, max_foot_length_mm, min_foot_width_mm, max_foot_width_mm) VALUES (?, ?, ?, ?, ?, ?)",
                    profileId, range.size(), profileId, range.size(), range.minLength(), range.maxLength(), range.minWidth(), range.maxWidth());
        }
    }

    private void seedOnlineScenarios(LocalDateTime now) {
        int[] reservableVariants = {0, 1, 2, 4, 5, 8, 9, 12, 13, 16, 17, 21, 22};
        for (int index = 0; index < 13; index++) {
            String pickup = index < 3 ? "NOT_CREATED" : index < 6 ? "PENDING" : index < 8 ? "PICKING"
                    : index < 11 ? "PREPARED" : index == 11 ? "OUT_FOR_DELIVERY" : "DELIVERED";
            onlineOrder("paid-" + index, reservableVariants[index], "PAID", "SUCCEEDED", pickup, now.minusHours(2 + index * 3L), false);
        }
        for (int index = 0; index < 3; index++) onlineOrder("cancelled-unpaid-" + index, 13 + index, "CANCELLED", null, "NOT_CREATED", now.minusDays(index + 2L), false);
        for (int index = 0; index < 2; index++) onlineOrder("cancelled-void-" + index, 16 + index, "CANCELLED", "SUCCEEDED", "CANCELLED", now.minusDays(index + 1L), true);
        onlineOrder("failed-0", 24, "PENDING_PAYMENT", "FAILED", "NOT_CREATED", now.minusHours(3), false);
        onlineOrder("failed-1", 25, "PENDING_PAYMENT", "FAILED", "NOT_CREATED", now.minusHours(2), false);
        onlineOrder("review-0", 28, "PENDING_PAYMENT", "REVIEW_REQUIRED", "NOT_CREATED", now.minusHours(1), false);
    }

    private void onlineOrder(String key, int variantIndex, String orderStatus, String paymentStatus,
            String pickupStatus, LocalDateTime createdAt, boolean successfulVoid) {
        UUID orderPublicId = uuid("order-" + key);
        if (exists("SELECT COUNT(*) FROM commerce_order WHERE public_id = ?", orderPublicId)) return;
        VariantSeed variant = variantAt(variantIndex);
        long variantId = id("SELECT id FROM catalog_product_variant WHERE public_id = ?", variant.publicId());
        String login = variantIndex % 2 == 0 ? "customer.demo" : "customer.second";
        long customerId = id("SELECT id FROM iam_user_account WHERE login_normalized = ?", login);
        UUID customerPublicId = accountPublicId(login);
        long priceId = id("SELECT id FROM pricing_variant_price WHERE variant_id = ? AND valid_to IS NULL", variantId);
        UUID pricePublicId = jdbc.queryForObject("SELECT public_id FROM pricing_variant_price WHERE id = ?", UUID.class, priceId);
        long amount = jdbc.queryForObject("SELECT amount FROM pricing_variant_price WHERE id = ?", Long.class, priceId);
        UUID quotePublicId = uuid("quote-" + key);
        UUID reservationPublicId = uuid("reservation-" + key);
        UUID itemPublicId = uuid("item-" + key);
        LocalDateTime paidAt = "SUCCEEDED".equals(paymentStatus) ? createdAt.plusMinutes(4) : null;
        LocalDateTime cancelledAt = orderStatus.equals("CANCELLED") ? createdAt.plusMinutes(successfulVoid ? 90 : 12) : null;
        String reservationStatus = successfulVoid ? "CANCELLED_RESTORED"
                : List.of("HANDED_OVER", "OUT_FOR_DELIVERY", "DELIVERED").contains(pickupStatus) ? "CONSUMED"
                : orderStatus.equals("CANCELLED") ? "RELEASED" : "SUCCEEDED".equals(paymentStatus) ? "COMMITTED" : "ADOPTED";

        insert("INSERT INTO pricing_price_quote(public_id, owner_account_id, price_version_id, amount, currency, quoted_at, expires_at) VALUES (?, ?, ?, ?, 'VND', ?, ?)", quotePublicId, customerId, priceId, amount, createdAt.minusMinutes(2), createdAt.plusHours(8));
        insert("INSERT INTO inventory_reservation(public_id, owner_account_public_id, variant_id, location_id, quantity, status, entity_version, created_at, released_at, adopted_at, consumed_at, expires_at, expired_at, committed_at, cancelled_restored_at) VALUES (?, ?, ?, (SELECT id FROM org_location WHERE public_id = ?), 1, ?, 0, ?, ?, ?, ?, ?, NULL, ?, ?)",
                reservationPublicId, customerPublicId, variantId, floorPublicId, reservationStatus, createdAt,
                reservationStatus.equals("RELEASED") ? cancelledAt : null,
                reservationStatus.equals("RELEASED") ? null : createdAt.plusMinutes(1),
                reservationStatus.equals("CONSUMED") ? createdAt.plusHours(2) : null,
                createdAt.plusHours(8),
                List.of("COMMITTED", "CANCELLED_RESTORED").contains(reservationStatus) ? paidAt : null,
                reservationStatus.equals("CANCELLED_RESTORED") ? cancelledAt : null);
        insert("INSERT INTO commerce_order(public_id, owner_account_public_id, responsible_branch_public_id, reservation_public_id, currency, status, entity_version, created_at, cancelled_at, paid_at, price_quote_public_id, checkout_idempotency_key, price_version_public_id, channel) VALUES (?, ?, ?, ?, 'VND', ?, 0, ?, ?, ?, ?, ?, ?, 'ONLINE')",
                orderPublicId, customerPublicId, branchPublicId, reservationPublicId, orderStatus, createdAt, cancelledAt, paidAt,
                quotePublicId, "demo-checkout-" + key, pricePublicId);
        long orderId = id("SELECT id FROM commerce_order WHERE public_id = ?", orderPublicId);
        insert("INSERT INTO commerce_order_item(public_id, order_id, variant_public_id, location_public_id, quantity, unit_price_amount, sku_snapshot, size_snapshot, reservation_public_id, price_version_public_id) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)", itemPublicId, orderId, variant.publicId(), floorPublicId, amount, variant.sku(), variant.size(), reservationPublicId, pricePublicId);

        if (paymentStatus != null) seedPayment(key, orderId, customerPublicId, amount, paymentStatus, createdAt.plusMinutes(2));
        if (!pickupStatus.equals("NOT_CREATED")) seedPickup(key, orderId, orderPublicId, reservationPublicId, variant.publicId(), pickupStatus, createdAt.plusMinutes(8));
        if (successfulVoid) seedVoid(key, orderId, orderPublicId, itemPublicId, reservationPublicId, variant.publicId(), amount, cancelledAt);
    }

    private void seedPayment(String key, long orderId, UUID owner, long amount, String status, LocalDateTime createdAt) {
        UUID paymentPublicId = uuid("payment-" + key);
        UUID attemptPublicId = uuid("attempt-" + key);
        LocalDateTime resolvedAt = createdAt.plusMinutes(2);
        insert("INSERT INTO payment(public_id, order_id, currency, entity_version, created_at) VALUES (?, ?, 'VND', 0, ?)", paymentPublicId, orderId, createdAt);
        long paymentId = id("SELECT id FROM payment WHERE public_id = ?", paymentPublicId);
        insert("INSERT INTO payment_attempt(public_id, payment_id, owner_account_public_id, idempotency_key, status, amount, currency, entity_version, created_at, cancelled_at, resolved_at, provider, merchant_transaction_reference, client_ip, expires_at, provider_transaction_no, provider_response_code, provider_transaction_status, provider_paid_at, provider_evidence_hash) VALUES (?, ?, ?, ?, ?, ?, 'VND', 0, ?, NULL, ?, 'VNPAY', ?, '127.0.0.1', ?, ?, ?, ?, ?, ?)",
                attemptPublicId, paymentId, owner, "demo-payment-" + key, status, amount, createdAt, resolvedAt,
                "DEMO-" + key.toUpperCase(), createdAt.plusHours(1), "SUCCEEDED".equals(status) ? "TXN-" + key : null,
                "SUCCEEDED".equals(status) || "REVIEW_REQUIRED".equals(status) ? "00" : "24",
                "SUCCEEDED".equals(status) || "REVIEW_REQUIRED".equals(status) ? "00" : "02",
                "SUCCEEDED".equals(status) || "REVIEW_REQUIRED".equals(status) ? resolvedAt : null, "0".repeat(64));
    }

    private void seedPickup(String key, long orderId, UUID orderPublicId, UUID reservationPublicId,
            UUID variantPublicId, String status, LocalDateTime createdAt) {
        UUID operator = accountPublicId("operations.demo");
        boolean delivery = List.of("OUT_FOR_DELIVERY", "DELIVERED").contains(status);
        LocalDateTime picking = List.of("PICKING", "PREPARED", "HANDED_OVER", "OUT_FOR_DELIVERY", "DELIVERED").contains(status) ? createdAt.plusMinutes(10) : null;
        LocalDateTime prepared = List.of("PREPARED", "HANDED_OVER", "OUT_FOR_DELIVERY", "DELIVERED").contains(status) ? createdAt.plusMinutes(25) : null;
        LocalDateTime handed = status.equals("HANDED_OVER") ? createdAt.plusMinutes(50) : null;
        LocalDateTime dispatched = delivery ? createdAt.plusMinutes(50) : null;
        LocalDateTime delivered = status.equals("DELIVERED") ? createdAt.plusHours(2) : null;
        LocalDateTime cancelled = status.equals("CANCELLED") ? createdAt.plusMinutes(40) : null;
        insert("INSERT INTO pickup_fulfillment(public_id, order_id, branch_id, location_id, status, entity_version, created_at, picking_started_at, prepared_at, prepared_by_account_public_id, handed_over_at, handed_over_by_account_public_id, handover_idempotency_key, cancelled_at, cancelled_by_account_public_id, channel, fulfillment_type, receiver_name, receiver_phone, delivery_address, delivery_note, delivery_fee_amount, dispatched_at, dispatched_by_account_public_id, dispatch_idempotency_key, delivered_at, delivered_by_account_public_id, delivery_idempotency_key) VALUES (?, ?, (SELECT id FROM org_branch WHERE public_id = ?), (SELECT id FROM org_location WHERE public_id = ?), ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                uuid("pickup-" + key), orderId, branchPublicId, floorPublicId, status, createdAt, picking, prepared,
                prepared == null ? null : operator, handed, handed == null ? null : operator,
                handed == null ? null : "demo-handover-" + key, cancelled, cancelled == null ? null : operator,
                delivery ? "DELIVERY" : "PICKUP", delivery ? "Nguyen Van A" : null,
                delivery ? "+84 912 345 678" : null, delivery ? "12 Nguyen Hue, Quan 1" : null,
                delivery ? "Demo delivery" : null, dispatched, delivery ? operator : null,
                delivery ? "demo-dispatch-" + key : null, delivered, delivered == null ? null : operator,
                delivered == null ? null : "demo-delivered-" + key);
        if (status.equals("HANDED_OVER") || delivery) {
            String movementType = delivery ? "DELIVERY_DISPATCH" : "PICKUP_HANDOVER";
            insert("INSERT INTO inventory_stock_movement(public_id, operation_type, operation_key, order_public_id, reservation_public_id, variant_public_id, location_public_id, actor_account_public_id, quantity, on_hand_delta, reserved_delta, occurred_at, pos_register_public_id, cashier_shift_public_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, -1, -1, ?, NULL, NULL)", uuid("movement-handover-" + key), movementType, "demo-handover-movement-" + key, orderPublicId, reservationPublicId, variantPublicId, floorPublicId, operator, delivery ? dispatched : handed);
        }
    }

    private void seedVoid(String key, long orderId, UUID orderPublicId, UUID itemPublicId,
            UUID reservationPublicId, UUID variantPublicId, long amount, LocalDateTime resolvedAt) {
        UUID customer = jdbc.queryForObject("SELECT owner_account_public_id FROM commerce_order WHERE id = ?", UUID.class, orderId);
        UUID operationPublicId = uuid("void-operation-" + key);
        UUID attemptPublicId = uuid("void-attempt-" + key);
        insert("INSERT INTO payment_void_operation(public_id, payment_id, order_public_id, actor_account_public_id, idempotency_key, requested_amount, currency, status, entity_version, created_at, resolved_at) VALUES (?, (SELECT id FROM payment WHERE order_id = ?), ?, ?, ?, ?, 'VND', 'SUCCEEDED', 0, ?, ?)", operationPublicId, orderId, orderPublicId, customer, "demo-void-" + key, amount, resolvedAt.minusMinutes(2), resolvedAt);
        long operationId = id("SELECT id FROM payment_void_operation WHERE public_id = ?", operationPublicId);
        insert("INSERT INTO payment_void_attempt(public_id, void_operation_id, generation, actor_account_public_id, idempotency_key, merchant_request_reference, amount, status, provider_response_id, provider_response_code, provider_transaction_status, provider_transaction_no, provider_evidence_hash, created_at, resolved_at) VALUES (?, ?, 1, ?, ?, ?, ?, 'SUCCEEDED', ?, '00', '00', ?, ?, ?, ?)", attemptPublicId, operationId, customer, "demo-void-attempt-" + key, "DV" + Math.abs(key.hashCode()), amount, "RESP-" + key, "VOID-TXN-" + key, "1".repeat(64), resolvedAt.minusMinutes(1), resolvedAt);
        long attemptId = id("SELECT id FROM payment_void_attempt WHERE public_id = ?", attemptPublicId);
        insert("INSERT INTO payment_void_allocation(public_id, void_operation_id, void_attempt_id, component_type, component_public_id, amount, status, created_at, resolved_at) VALUES (?, ?, ?, 'ORDER_ITEM', ?, ?, 'SUCCEEDED', ?, ?)", uuid("void-allocation-" + key), operationId, attemptId, itemPublicId, amount, resolvedAt.minusMinutes(1), resolvedAt);
        insert("INSERT INTO inventory_stock_movement(public_id, operation_type, operation_key, order_public_id, reservation_public_id, variant_public_id, location_public_id, actor_account_public_id, quantity, on_hand_delta, reserved_delta, occurred_at, pos_register_public_id, cashier_shift_public_id) VALUES (?, 'CANCELLATION_RESTORE', ?, ?, ?, ?, ?, ?, 1, 0, -1, ?, NULL, NULL)", uuid("movement-cancel-" + key), "demo-cancel-movement-" + key, orderPublicId, reservationPublicId, variantPublicId, floorPublicId, customer, resolvedAt);
    }

    private void seedPosScenarios(LocalDateTime now) {
        UUID shiftPublicId = uuid("pos-history-shift");
        if (exists("SELECT COUNT(*) FROM cashier_shift WHERE public_id = ?", shiftPublicId)) return;
        long registerId = id("SELECT id FROM pos_register WHERE code = 'DEMO-01'");
        UUID registerPublicId = jdbc.queryForObject("SELECT public_id FROM pos_register WHERE id = ?", UUID.class, registerId);
        long cashierId = id("SELECT id FROM iam_user_account WHERE login_normalized = 'cashier.demo'");
        UUID cashierPublicId = accountPublicId("cashier.demo");
        LocalDateTime openedAt = now.minusDays(1).withHour(2).withMinute(0).withSecond(0).withNano(0);
        insert("INSERT INTO cashier_shift(public_id, register_id, location_id, cashier_account_id, status, entity_version, opened_at, closed_at) VALUES (?, ?, (SELECT id FROM org_location WHERE public_id = ?), ?, 'CLOSED', 0, ?, ?)", shiftPublicId, registerId, floorPublicId, cashierId, openedAt, openedAt.plusHours(8));
        long shiftId = id("SELECT id FROM cashier_shift WHERE public_id = ?", shiftPublicId);
        for (int index = 0; index < 12; index++) {
            VariantSeed variant = variantAt(20 + index);
            String key = "pos-" + index;
            LocalDateTime soldAt = openedAt.plusMinutes(20 + index * 25L);
            posSale(key, variant, shiftId, shiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId, soldAt);
        }
    }

    private void seedSalesHistory(LocalDateTime now) {
        long registerId = id("SELECT id FROM pos_register WHERE code = 'DEMO-01'");
        UUID registerPublicId = jdbc.queryForObject("SELECT public_id FROM pos_register WHERE id = ?", UUID.class, registerId);
        long cashierId = id("SELECT id FROM iam_user_account WHERE login_normalized = 'cashier.demo'");
        UUID cashierPublicId = accountPublicId("cashier.demo");
        LocalDateTime older = now.minusDays(20).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime baseline = now.minusDays(10).withHour(11).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime recent = now.minusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        long olderShift = historyShift("older", registerId, cashierId, older);
        long baselineShift = historyShift("baseline", registerId, cashierId, baseline);
        long recentShift = historyShift("recent", registerId, cashierId, recent);
        UUID olderShiftPublicId = uuid("merch-shift-older");
        UUID baselineShiftPublicId = uuid("merch-shift-baseline");
        UUID recentShiftPublicId = uuid("merch-shift-recent");
        seedSales("court-classic", 28, olderShift, olderShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("after-dark", 20, olderShift, olderShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("daily-canvas", 24, olderShift, olderShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("metro-runner", 3, baselineShift, baselineShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("court-classic", 6, recentShift, recentShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("after-dark", 4, recentShift, recentShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("daily-canvas", 4, recentShift, recentShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
        seedSales("metro-runner", 12, recentShift, recentShiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId);
    }

    private void seedSales(String productKey, int count, long shiftId, UUID shiftPublicId,
            long registerId, UUID registerPublicId, long cashierId, UUID cashierPublicId) {
        // Anchor seed identities to the original shift, not the date of a later restart.
        LocalDateTime soldAt = jdbc.queryForObject("SELECT opened_at FROM cashier_shift WHERE id = ?", LocalDateTime.class, shiftId);
        VariantSeed variant = variant(productKey);
        for (int index = 0; index < count; index++) {
            posSale("history-" + productKey + "-" + soldAt.toLocalDate() + "-" + index, variant, shiftId,
                    shiftPublicId, registerId, registerPublicId, cashierId, cashierPublicId, soldAt.plusMinutes(index));
        }
    }

    private long historyShift(String key, long registerId, long cashierId, LocalDateTime openedAt) {
        UUID shiftPublicId = uuid("merch-shift-" + key);
        insert("IF NOT EXISTS (SELECT 1 FROM cashier_shift WHERE public_id = ?) INSERT INTO cashier_shift(public_id, register_id, location_id, cashier_account_id, status, entity_version, opened_at, closed_at) VALUES (?, ?, (SELECT id FROM org_location WHERE public_id = ?), ?, 'CLOSED', 0, ?, ?)",
                shiftPublicId, shiftPublicId, registerId, floorPublicId, cashierId, openedAt, openedAt.plusHours(8));
        return id("SELECT id FROM cashier_shift WHERE public_id = ?", shiftPublicId);
    }

    private void posSale(String key, VariantSeed variant, long shiftId, UUID shiftPublicId, long registerId,
            UUID registerPublicId, long cashierId, UUID cashierPublicId, LocalDateTime soldAt) {
        UUID salePublicId = uuid("sale-" + key);
        if (exists("SELECT COUNT(*) FROM pos_cash_sale WHERE public_id = ?", salePublicId)) return;
        long variantId = id("SELECT id FROM catalog_product_variant WHERE public_id = ?", variant.publicId());
        UUID priceVersion = jdbc.queryForObject("SELECT public_id FROM pricing_variant_price WHERE variant_id = ? AND valid_to IS NULL", UUID.class, variantId);
        long amount = jdbc.queryForObject("SELECT amount FROM pricing_variant_price WHERE variant_id = ? AND valid_to IS NULL", Long.class, variantId);
        UUID orderPublicId = uuid("order-" + key);
        insert("INSERT INTO commerce_order(public_id, owner_account_public_id, responsible_branch_public_id, reservation_public_id, currency, status, entity_version, created_at, cancelled_at, paid_at, price_quote_public_id, checkout_idempotency_key, price_version_public_id, channel) VALUES (?, NULL, ?, NULL, 'VND', 'PAID', 0, ?, NULL, ?, NULL, NULL, ?, 'POS')", orderPublicId, branchPublicId, soldAt, soldAt, priceVersion);
        long orderId = id("SELECT id FROM commerce_order WHERE public_id = ?", orderPublicId);
        insert("INSERT INTO commerce_order_item(public_id, order_id, variant_public_id, location_public_id, quantity, unit_price_amount, sku_snapshot, size_snapshot, price_version_public_id) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?)", uuid("item-" + key), orderId, variant.publicId(), floorPublicId, amount, variant.sku(), variant.size(), priceVersion);
        insert("INSERT INTO pos_cash_sale(public_id, order_id, shift_id, cashier_account_id, variant_public_id, idempotency_key, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", salePublicId, orderId, shiftId, cashierId, variant.publicId(), "demo-" + key, soldAt);
        insert("INSERT INTO cash_tender(public_id, order_id, shift_id, register_id, cashier_account_id, amount, currency, created_at) VALUES (?, ?, ?, ?, ?, ?, 'VND', ?)", uuid("tender-" + key), orderId, shiftId, registerId, cashierId, amount, soldAt);
        insert("INSERT INTO pickup_fulfillment(public_id, order_id, branch_id, location_id, status, entity_version, created_at, picking_started_at, prepared_at, prepared_by_account_public_id, handed_over_at, handed_over_by_account_public_id, handover_idempotency_key, cancelled_at, cancelled_by_account_public_id, channel) VALUES (?, ?, (SELECT id FROM org_branch WHERE public_id = ?), (SELECT id FROM org_location WHERE public_id = ?), 'HANDED_OVER', 0, ?, NULL, NULL, NULL, ?, ?, ?, NULL, NULL, 'POS')", uuid("pickup-" + key), orderId, branchPublicId, floorPublicId, soldAt, soldAt, cashierPublicId, salePublicId.toString());
        insert("INSERT INTO inventory_stock_movement(public_id, operation_type, operation_key, order_public_id, reservation_public_id, variant_public_id, location_public_id, actor_account_public_id, quantity, on_hand_delta, reserved_delta, occurred_at, pos_register_public_id, cashier_shift_public_id) VALUES (?, 'POS_CASH_SALE', ?, ?, NULL, ?, ?, ?, 1, -1, 0, ?, ?, ?)", uuid("movement-" + key), salePublicId.toString(), orderPublicId, variant.publicId(), floorPublicId, cashierPublicId, soldAt, registerPublicId, shiftPublicId);
    }

    private void synchronizeReserved(LocalDateTime now) {
        insert("UPDATE balances SET reserved = active.reserved, updated_at = ? FROM inventory_balance balances CROSS APPLY (SELECT COUNT_BIG(*) reserved FROM inventory_reservation reservations WHERE reservations.variant_id = balances.variant_id AND reservations.location_id = balances.location_id AND reservations.status IN ('ACTIVE', 'ADOPTED', 'COMMITTED')) active", now);
    }

    private void account(String login, String role, LocalDateTime now) {
        insert("IF NOT EXISTS (SELECT 1 FROM iam_user_account WHERE login_normalized = ?) INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", login, uuid("account-" + login), login, passwords.encode(PASSWORD), now, now);
        long accountId = id("SELECT id FROM iam_user_account WHERE login_normalized = ?", login);
        insert("IF NOT EXISTS (SELECT 1 FROM iam_account_role ar JOIN iam_role_bundle r ON r.id = ar.role_id WHERE ar.account_id = ? AND r.code = ?) INSERT INTO iam_account_role(account_id, role_id) SELECT ?, id FROM iam_role_bundle WHERE code = ?", accountId, role, accountId, role);
    }

    private void assign(String login, long branchId, long locationId, LocalDateTime now) {
        long accountId = id("SELECT id FROM iam_user_account WHERE login_normalized = ?", login);
        insert("IF NOT EXISTS (SELECT 1 FROM iam_staff_assignment WHERE account_id = ? AND branch_id = ? AND location_id = ?) INSERT INTO iam_staff_assignment(public_id, account_id, branch_id, location_id, active, created_at, updated_at) VALUES (?, ?, ?, ?, 1, ?, ?)", accountId, branchId, locationId, uuid("assignment-" + login + "-" + locationId), accountId, branchId, locationId, now, now);
    }

    private UUID location(long branchId, UUID publicId, String code, String name, LocalDateTime now) {
        insert("INSERT INTO org_location(public_id, branch_id, code, name, enabled, created_at) SELECT ?, ?, ?, ?, 1, ? WHERE NOT EXISTS (SELECT 1 FROM org_location WITH (UPDLOCK, HOLDLOCK) WHERE branch_id = ? AND code = ?)", publicId, branchId, code, name, now, branchId, code);
        return jdbc.queryForObject("SELECT public_id FROM org_location WHERE branch_id = ? AND code = ?", UUID.class, branchId, code);
    }

    private void register(String code, long locationId, LocalDateTime now) {
        insert("IF NOT EXISTS (SELECT 1 FROM pos_register WHERE code = ?) INSERT INTO pos_register(public_id, code, location_id, enabled, created_at) VALUES (?, ?, ?, 1, ?)", code, uuid("register-" + code), code, locationId, now);
    }

    private void variant(long productId, UUID publicId, String sku, String size, String color, long amount, LocalDateTime now) {
        insert("IF NOT EXISTS (SELECT 1 FROM catalog_product_variant WHERE sku = ?) INSERT INTO catalog_product_variant(public_id, product_id, sku, size, color, lifecycle_status, entity_version, created_at) VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 0, ?)", sku, publicId, productId, sku, size, color, now.minusDays(120));
        long variantId = id("SELECT id FROM catalog_product_variant WHERE sku = ?", sku);
        insert("IF NOT EXISTS (SELECT 1 FROM pricing_variant_price WHERE variant_id = ? AND valid_to IS NULL) INSERT INTO pricing_variant_price(public_id, variant_id, amount, entity_version, valid_from, valid_to, updated_at) VALUES (?, ?, ?, 0, ?, NULL, ?)", variantId, uuid("price-current-" + sku), variantId, amount, now.minusDays(60), now);
    }

    private void priceHistory(UUID variant, long amount, LocalDateTime now) {
        long variantId = id("SELECT id FROM catalog_product_variant WHERE public_id = ?", variant);
        UUID history = uuid("price-history-" + variant);
        insert("IF NOT EXISTS (SELECT 1 FROM pricing_variant_price WHERE public_id = ?) INSERT INTO pricing_variant_price(public_id, variant_id, amount, entity_version, valid_from, valid_to, updated_at) VALUES (?, ?, ?, 0, ?, ?, ?)", history, history, variantId, amount, now.minusDays(120), now.minusDays(60), now.minusDays(60));
    }

    private void balance(UUID variant, long locationId, long onHand, LocalDateTime now) {
        long variantId = id("SELECT id FROM catalog_product_variant WHERE public_id = ?", variant);
        insert("IF NOT EXISTS (SELECT 1 FROM inventory_balance WHERE variant_id = ? AND location_id = ?) INSERT INTO inventory_balance(variant_id, location_id, on_hand, entity_version, updated_at, reserved) VALUES (?, ?, ?, 0, ?, 0)", variantId, locationId, variantId, locationId, onHand, now);
    }

    private VariantSeed variantAt(int index) {
        int offset = index;
        for (ProductSeed product : PRODUCTS) {
            if (offset < product.sizes().size()) {
                String size = product.sizes().get(offset);
                String sku = "DEMO-" + product.code() + "-" + size;
                return new VariantSeed(uuid("variant-" + sku), sku, size);
            }
            offset -= product.sizes().size();
        }
        throw new IllegalArgumentException("Unknown demo variant index: " + index);
    }

    private VariantSeed variant(String productKey) {
        ProductSeed product = PRODUCTS.stream().filter(candidate -> candidate.key().equals(productKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown demo product: " + productKey));
        String size = product.sizes().get(0);
        String sku = "DEMO-" + product.code() + "-" + size;
        return new VariantSeed(uuid("variant-" + sku), sku, size);
    }

    private UUID accountPublicId(String login) { return jdbc.queryForObject("SELECT public_id FROM iam_user_account WHERE login_normalized = ?", UUID.class, login); }
    private boolean exists(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args) > 0; }
    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void insert(String sql, Object... args) { jdbc.update(sql, args); }
    private static UUID uuid(String key) { return UUID.nameUUIDFromBytes(("shoe-commerce-demo:" + key).getBytes(StandardCharsets.UTF_8)); }

    private record ProductSeed(String key, String name, String code, String color, long price, List<String> sizes,
            List<Integer> floorStock, String category, String collection, boolean featured, boolean newArrival,
            boolean campaignEligible, int merchandisingRank, int createdDaysAgo, String heroImage, String primaryImage) { }
    private record VariantSeed(UUID publicId, String sku, String size) { }
    private record FitRange(String size, double minLength, double maxLength, double minWidth, double maxWidth) { }
}
