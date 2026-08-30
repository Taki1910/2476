package com.shoecommerce.demo;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
    private static final UUID BRANCH = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID VARIANT_40 = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID VARIANT_41 = UUID.fromString("00000000-0000-0000-0000-000000000105");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public DemoDataBootstrap(JdbcTemplate jdbc, PasswordEncoder passwords, Clock clock) {
        this.jdbc = jdbc; this.passwords = passwords; this.clock = clock;
    }

    @Override @Transactional
    public void run(String... args) {
        Instant now = clock.instant(); Timestamp timestamp = Timestamp.from(now);
        account("customer.demo", "CUSTOMER", timestamp);
        account("cashier.demo", "CASHIER", timestamp);
        account("operations.demo", "OPERATIONS", timestamp);
        account("manager.demo", "OPERATIONS", timestamp);
        insert("IF NOT EXISTS (SELECT 1 FROM org_branch WHERE code = 'DEMO-A') INSERT INTO org_branch(public_id, code, name, enabled, created_at) VALUES (?, 'DEMO-A', N'Demo Branch A', 1, ?)", BRANCH, timestamp);
        long branchId = id("SELECT id FROM org_branch WHERE code = 'DEMO-A'");
        insert("IF NOT EXISTS (SELECT 1 FROM org_location WHERE branch_id = ? AND code = 'DEMO-FLOOR') INSERT INTO org_location(public_id, branch_id, code, name, enabled, created_at) VALUES (?, ?, 'DEMO-FLOOR', N'Demo Sales Floor', 1, ?)", branchId, LOCATION, branchId, timestamp);
        long locationId = id("SELECT id FROM org_location WHERE public_id = ?", LOCATION);
        assign("cashier.demo", branchId, locationId, timestamp); assign("operations.demo", branchId, locationId, timestamp); assign("manager.demo", branchId, locationId, timestamp);
        insert("IF NOT EXISTS (SELECT 1 FROM pos_register WHERE code = 'DEMO-01') INSERT INTO pos_register(public_id, code, location_id, enabled, created_at) VALUES (NEWID(), 'DEMO-01', ?, 1, ?)", locationId, timestamp);
        insert("IF NOT EXISTS (SELECT 1 FROM catalog_product WHERE public_id = ?) INSERT INTO catalog_product(public_id, name, entity_version, created_at) VALUES (?, N'Court Classic', 0, ?)", PRODUCT, PRODUCT, timestamp);
        long productId = id("SELECT id FROM catalog_product WHERE public_id = ?", PRODUCT);
        variant(productId, VARIANT_40, "DEMO-CC-BLK-40", "40", timestamp); variant(productId, VARIANT_41, "DEMO-CC-BLK-41", "41", timestamp);
        baseline(VARIANT_40, locationId, timestamp); baseline(VARIANT_41, locationId, timestamp);
    }

    private void account(String login, String role, Timestamp now) {
        insert("IF NOT EXISTS (SELECT 1 FROM iam_user_account WHERE login_normalized = ?) INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (NEWID(), ?, ?, 'ENABLED', 1, 0, ?, ?)", login, login, passwords.encode(PASSWORD), now, now);
        long accountId = id("SELECT id FROM iam_user_account WHERE login_normalized = ?", login);
        insert("IF NOT EXISTS (SELECT 1 FROM iam_account_role ar JOIN iam_role_bundle r ON r.id = ar.role_id WHERE ar.account_id = ? AND r.code = ?) INSERT INTO iam_account_role(account_id, role_id) SELECT ?, id FROM iam_role_bundle WHERE code = ?", accountId, role, accountId, role);
    }
    private void assign(String login, long branchId, long locationId, Timestamp now) {
        long accountId = id("SELECT id FROM iam_user_account WHERE login_normalized = ?", login);
        insert("IF NOT EXISTS (SELECT 1 FROM iam_staff_assignment WHERE account_id = ? AND branch_id = ? AND location_id = ?) INSERT INTO iam_staff_assignment(public_id, account_id, branch_id, location_id, active, created_at, updated_at) VALUES (NEWID(), ?, ?, ?, 1, ?, ?)", accountId, branchId, locationId, accountId, branchId, locationId, now, now);
    }
    private void variant(long productId, UUID publicId, String sku, String size, Timestamp now) {
        insert("IF NOT EXISTS (SELECT 1 FROM catalog_product_variant WHERE sku = ?) INSERT INTO catalog_product_variant(public_id, product_id, sku, size, color, lifecycle_status, entity_version, created_at) VALUES (?, ?, ?, ?, N'Black', 'PUBLISHED', 0, ?)", sku, publicId, productId, sku, size, now);
        long variantId = id("SELECT id FROM catalog_product_variant WHERE sku = ?", sku);
        insert("IF NOT EXISTS (SELECT 1 FROM pricing_variant_price WHERE variant_id = ? AND valid_to IS NULL) INSERT INTO pricing_variant_price(public_id, variant_id, amount, entity_version, valid_from, valid_to, updated_at) VALUES (NEWID(), ?, 125000, 0, ?, NULL, ?)", variantId, variantId, now, now);
    }
    private void baseline(UUID variant, long locationId, Timestamp now) {
        long variantId = id("SELECT id FROM catalog_product_variant WHERE public_id = ?", variant);
        insert("IF NOT EXISTS (SELECT 1 FROM inventory_balance WHERE variant_id = ? AND location_id = ?) INSERT INTO inventory_balance(variant_id, location_id, on_hand, entity_version, updated_at, reserved) VALUES (?, ?, 3, 0, ?, 0)", variantId, locationId, variantId, locationId, now);
    }
    private long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void insert(String sql, Object... args) { jdbc.update(sql, args); }
}
