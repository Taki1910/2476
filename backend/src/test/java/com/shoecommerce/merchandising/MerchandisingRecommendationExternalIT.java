package com.shoecommerce.merchandising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.CandidateType;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.PromotionState;
import com.shoecommerce.merchandising.MerchandisingRecommendationService.Sellability;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class MerchandisingRecommendationExternalIT {
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationPolicy authorization;
    @Autowired AccountUserDetailsService users;

    private MerchandisingRecommendationService service;
    private SessionPrincipal manager;
    private SessionPrincipal denied;
    private UUID branch;
    private UUID location;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM storefront_page_revision");
        manager = actor(true);
        denied = actor(false);
        branch = UUID.randomUUID();
        location = UUID.randomUUID();
        jdbc.update("INSERT INTO org_branch(public_id,code,name,enabled,created_at) VALUES (?,?,?,1,?)",
                branch, "B-" + branch.toString().substring(0, 8), "Global branch", Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at)
                SELECT ?,id,?,?,1,? FROM org_branch WHERE public_id=?
                """, location, "L-" + location.toString().substring(0, 8), "Global location", Timestamp.from(NOW), branch);
        service = new MerchandisingRecommendationService(jdbc, authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void protectsTheProjectionAndUsesGlobalEnabledLocationAvailability() {
        ProductFixture product = product("Global Court", true, true, 7);

        var candidate = service.candidates(manager, "Global Court", CandidateType.ALL, 0, 20)
                .candidates().stream().filter(item -> item.productId().equals(product.productId()))
                .findFirst().orElseThrow();

        assertThat(candidate.productId()).isEqualTo(product.productId());
        assertThat(candidate.aggregateAvailable()).isEqualTo(7);
        assertThat(candidate.sellability()).isEqualTo(Sellability.SELLABLE);
        assertThat(candidate.warnings()).extracting(warning -> warning.code())
                .containsExactlyInAnyOrder("MISSING_PRODUCT_MEDIA", "MISSING_PRODUCT_PRESENTATION");

        ProductFixture priceStockMismatch = product("Priced variant sold out", true, true, 0);
        addUnpricedPublishedStock(priceStockMismatch.productId(), 9);
        var mismatch = service.candidates(manager, "Priced variant sold out", CandidateType.ALL, 0, 20)
                .candidates().stream().filter(item -> item.productId().equals(priceStockMismatch.productId()))
                .findFirst().orElseThrow();
        assertThat(mismatch.aggregateAvailable()).isZero();
        assertThat(mismatch.sellability()).isEqualTo(Sellability.SOLD_OUT);
        assertThatThrownBy(() -> service.candidates(denied, null, CandidateType.ALL, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void countsOnlineAndPosSalesWithinTheThirtyDayWindowButRejectsPartialVoidEvidence() {
        ProductFixture exactBoundary = product("Boundary", true, true, 10);
        onlineSale(exactBoundary, 2, NOW.minusSeconds(30L * 86_400), false);
        posSale(exactBoundary, NOW.minusSeconds(86_400));
        onlineSale(exactBoundary, 5, NOW.minusSeconds(30L * 86_400 + 1), false);
        onlineSale(exactBoundary, 9, NOW.plusSeconds(1), false);

        ProductFixture partialVoid = product("Partial void", true, true, 10);
        onlineSale(partialVoid, 4, NOW.minusSeconds(3_600), true);

        var boundary = service.candidates(manager, "Boundary", CandidateType.ALL, 0, 20).candidates().stream()
                .filter(candidate -> candidate.productId().equals(exactBoundary.productId()))
                .findFirst().orElseThrow();
        var incomplete = service.candidates(manager, "Partial void", CandidateType.ALL, 0, 20).candidates().stream()
                .filter(candidate -> candidate.productId().equals(partialVoid.productId()))
                .findFirst().orElseThrow();

        assertThat(boundary.sales().units30d()).isEqualTo(3);
        assertThat(boundary.sales().periodStart()).isEqualTo(NOW.minusSeconds(30L * 86_400));
        assertThat(boundary.sales().periodEnd()).isEqualTo(NOW);
        assertThat(incomplete.sales().complete()).isFalse();
        assertThat(incomplete.warnings()).extracting(warning -> warning.code())
                .contains("PARTIAL_VOID_SALES_UNRESOLVED");
        assertThat(service.candidates(manager, "Boundary", CandidateType.BEST_SELLER, 0, 20).candidates())
                .extracting(candidate -> candidate.productId()).contains(exactBoundary.productId());
        assertThat(service.candidates(manager, "Partial void", CandidateType.BEST_SELLER, 0, 20).candidates())
                .extracting(candidate -> candidate.productId()).doesNotContain(partialVoid.productId());
    }

    @Test
    void exposesOnlyProductScopedActivePromotionRecommendationsAndCurrentPlacement() {
        ProductFixture product = product("Promoted Court", true, true, 3);
        promotion(product.productId(), "Active item", "ITEM", "ITEM_PERCENT", NOW.minusSeconds(60),
                NOW.plusSeconds(3_600));
        promotion(product.productId(), "Scheduled item", "ITEM", "ITEM_FIXED", NOW.plusSeconds(3_600),
                NOW.plusSeconds(7_200));
        promotion(product.productId(), "Order campaign", "ORDER_AUTOMATIC", "ORDER_FIXED",
                NOW.minusSeconds(60), NOW.plusSeconds(3_600));
        place(product.productId(), "PUBLISHED", "Đang hiển thị");
        place(product.productId(), "DRAFT", "Bản nháp");

        var candidate = service.candidates(manager, "Promoted Court", CandidateType.PROMOTED, 0, 20)
                .candidates().stream().filter(item -> item.productId().equals(product.productId()))
                .findFirst().orElseThrow();

        assertThat(candidate.promotions()).extracting(promotion -> promotion.name())
                .containsExactly("Active item", "Scheduled item");
        assertThat(candidate.promotions()).extracting(promotion -> promotion.state())
                .containsExactly(PromotionState.ACTIVE, PromotionState.SCHEDULED);
        assertThat(candidate.publishedPlacements()).extracting(placement -> placement.titleVi())
                .containsExactly("Đang hiển thị");
        assertThat(candidate.draftPlacements()).extracting(placement -> placement.titleVi())
                .containsExactly("Bản nháp");
    }

    private SessionPrincipal actor(boolean permitted) {
        UUID id = UUID.randomUUID();
        String login = "p5a-" + id + "@example.test";
        jdbc.update("""
                INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,
                    entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)
                """, id, login, "fixture", Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO iam_account_role(account_id,role_id)
                SELECT a.id,r.id FROM iam_user_account a CROSS JOIN iam_role_bundle r
                WHERE a.public_id=? AND r.code='OPERATIONS'
                """, id);
        if (permitted) jdbc.update("""
                INSERT INTO iam_account_permission(account_id,permission_id,granted_at)
                SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p
                WHERE a.public_id=? AND p.code='STOREFRONT_MANAGE'
                """, Timestamp.from(NOW), id);
        return (SessionPrincipal) users.loadUserByUsername(login);
    }

    private ProductFixture product(String name, boolean published, boolean priced, long available) {
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID(), price = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_product(public_id,name,category,collection,hero_image,primary_image,
                    entity_version,created_at) VALUES (?,?,N'Sneaker',N'Everyday',NULL,NULL,0,?)
                """, product, name, Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,
                    entity_version,created_at,ever_published)
                SELECT ?,id,?,'42',N'Black',?,0,?,? FROM catalog_product WHERE public_id=?
                """, variant, "P5A-" + variant, published ? "PUBLISHED" : "DRAFT", Timestamp.from(NOW),
                published, product);
        if (priced) jdbc.update("""
                INSERT INTO pricing_variant_price(public_id,variant_id,amount,entity_version,updated_at,valid_from)
                SELECT ?,id,500000,0,?,? FROM catalog_product_variant WHERE public_id=?
                """, price, Timestamp.from(NOW), Timestamp.from(NOW.minusSeconds(60)), variant);
        jdbc.update("""
                INSERT INTO inventory_balance(variant_id,location_id,on_hand,reserved,entity_version,updated_at)
                SELECT v.id,l.id,?,0,0,? FROM catalog_product_variant v CROSS JOIN org_location l
                WHERE v.public_id=? AND l.public_id=?
                """, available, Timestamp.from(NOW), variant, location);
        return new ProductFixture(product, variant, price);
    }

    private void onlineSale(ProductFixture product, long quantity, Instant soldAt, boolean partialVoid) {
        UUID reservation = UUID.randomUUID(), order = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_reservation(public_id,owner_account_public_id,variant_id,location_id,quantity,
                    status,entity_version,created_at,adopted_at,committed_at)
                SELECT ?,?,v.id,l.id,?,'COMMITTED',0,?,?,? FROM catalog_product_variant v CROSS JOIN org_location l
                WHERE v.public_id=? AND l.public_id=?
                """, reservation, manager.publicId(), quantity, Timestamp.from(soldAt.minusSeconds(60)),
                Timestamp.from(soldAt.minusSeconds(30)), Timestamp.from(soldAt), product.variantId(), location);
        insertOrder(order, "ONLINE", reservation, null, manager.publicId(), quantity, soldAt);
        insertItem(item, order, product, reservation, quantity);
        UUID payment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment(public_id,order_id,currency,entity_version,created_at)
                SELECT ?,id,'VND',0,? FROM commerce_order WHERE public_id=?
                """, payment, Timestamp.from(soldAt.minusSeconds(60)), order);
        jdbc.update("""
                INSERT INTO payment_attempt(public_id,payment_id,owner_account_public_id,idempotency_key,provider,
                    merchant_transaction_reference,client_ip,status,amount,currency,entity_version,created_at,
                    expires_at,resolved_at,provider_response_code,provider_transaction_status,provider_evidence_hash)
                SELECT NEWID(),id,?,?, 'LEGACY',?,'127.0.0.1','SUCCEEDED',?,'VND',0,?,?,?,?,?,REPLICATE('0',64)
                FROM payment WHERE public_id=?
                """, manager.publicId(), "pay-" + order, "merchant-" + order, 500000L * quantity,
                Timestamp.from(soldAt.minusSeconds(60)), Timestamp.from(soldAt.plusSeconds(600)),
                Timestamp.from(soldAt), "00", "00", payment);
        if (partialVoid) partialVoid(payment, order, item, quantity * 500000L / 2, soldAt.plusSeconds(60));
    }

    private void addUnpricedPublishedStock(UUID product, long available) {
        UUID variant = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,
                    entity_version,created_at,ever_published)
                SELECT ?,id,?,'43',N'White','PUBLISHED',0,?,1 FROM catalog_product WHERE public_id=?
                """, variant, "P5A-UNPRICED-" + variant, Timestamp.from(NOW), product);
        jdbc.update("""
                INSERT INTO inventory_balance(variant_id,location_id,on_hand,reserved,entity_version,updated_at)
                SELECT v.id,l.id,?,0,0,? FROM catalog_product_variant v CROSS JOIN org_location l
                WHERE v.public_id=? AND l.public_id=?
                """, available, Timestamp.from(NOW), variant, location);
    }

    private void posSale(ProductFixture product, Instant soldAt) {
        UUID order = UUID.randomUUID(), item = UUID.randomUUID(), register = UUID.randomUUID(), shift = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pos_register(public_id,code,location_id,enabled,created_at)
                SELECT ?,?,id,1,? FROM org_location WHERE public_id=?
                """, register, "R-" + register.toString().substring(0, 8),
                Timestamp.from(soldAt.minusSeconds(120)), location);
        jdbc.update("""
                INSERT INTO cashier_shift(public_id,register_id,location_id,cashier_account_id,status,entity_version,opened_at)
                SELECT ?,r.id,r.location_id,a.id,'OPEN',0,? FROM pos_register r CROSS JOIN iam_user_account a
                WHERE r.public_id=? AND a.public_id=?
                """, shift, Timestamp.from(soldAt.minusSeconds(120)), register, manager.publicId());
        insertOrder(order, "POS", null, product.priceId(), null, 1, soldAt);
        insertItem(item, order, product, null, 1);
        jdbc.update("""
                INSERT INTO cash_tender(public_id,order_id,shift_id,register_id,cashier_account_id,amount,currency,created_at)
                SELECT NEWID(),o.id,s.id,r.id,a.id,500000,'VND',? FROM commerce_order o
                CROSS JOIN cashier_shift s CROSS JOIN pos_register r CROSS JOIN iam_user_account a
                WHERE o.public_id=? AND s.public_id=? AND r.public_id=? AND a.public_id=?
                """, Timestamp.from(soldAt), order, shift, register, manager.publicId());
    }

    private void insertOrder(UUID order, String channel, UUID reservation, UUID price, UUID owner,
            long quantity, Instant at) {
        jdbc.update("""
                INSERT INTO commerce_order(public_id,owner_account_public_id,responsible_branch_public_id,
                    reservation_public_id,price_version_public_id,channel,currency,status,entity_version,
                    created_at,paid_at,merchandise_amount,shipping_fee_amount,total_amount)
                VALUES (?,?,?,?,?,?,'VND','PAID',0,?,?,?,0,?)
                """, order, owner, branch, reservation, price, channel, Timestamp.from(at.minusSeconds(60)),
                Timestamp.from(at), 500000L * quantity, 500000L * quantity);
    }

    private void insertItem(UUID item, UUID order, ProductFixture product, UUID reservation, long quantity) {
        jdbc.update("""
                INSERT INTO commerce_order_item(public_id,order_id,variant_public_id,location_public_id,quantity,
                    unit_price_amount,sku_snapshot,size_snapshot,color_snapshot,reservation_public_id,
                    price_version_public_id)
                SELECT ?,o.id,?, ?,?,500000,'P5A-SKU','42',N'Black',?,?
                FROM commerce_order o WHERE o.public_id=?
                """, item, product.variantId(), location, quantity, reservation, product.priceId(), order);
    }

    private void partialVoid(UUID payment, UUID order, UUID item, long amount, Instant at) {
        UUID operation = UUID.randomUUID(), attempt = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_void_operation(public_id,payment_id,order_public_id,actor_account_public_id,
                    idempotency_key,requested_amount,currency,status,entity_version,created_at,resolved_at)
                SELECT ?,p.id,?,?,?,?,'VND','SUCCEEDED',0,?,? FROM payment p WHERE p.public_id=?
                """, operation, order, manager.publicId(), "void-" + order, amount,
                Timestamp.from(at.minusSeconds(30)), Timestamp.from(at), payment);
        jdbc.update("""
                INSERT INTO payment_void_attempt(public_id,void_operation_id,generation,actor_account_public_id,
                    idempotency_key,merchant_request_reference,amount,status,created_at,resolved_at,calculation_version)
                SELECT ?,id,1,?,?,?,?,'SUCCEEDED',?,?,'SNAPSHOT_V2' FROM payment_void_operation WHERE public_id=?
                """, attempt, manager.publicId(), "void-attempt-" + order,
                "vr-" + order.toString().substring(0, 8), amount,
                Timestamp.from(at.minusSeconds(30)), Timestamp.from(at), operation);
        jdbc.update("""
                INSERT INTO payment_void_allocation(public_id,void_operation_id,void_attempt_id,component_type,
                    component_public_id,amount,status,created_at,resolved_at)
                SELECT NEWID(),o.id,a.id,'ORDER_ITEM',?,?,'SUCCEEDED',?,? FROM payment_void_operation o
                CROSS JOIN payment_void_attempt a WHERE o.public_id=? AND a.public_id=?
                """, item, amount, Timestamp.from(at.minusSeconds(30)), Timestamp.from(at), operation, attempt);
    }

    private void promotion(UUID product, String name, String layer, String effect, Instant from, Instant to) {
        UUID family = UUID.randomUUID(), revision = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at)
                VALUES (?,'AUTOMATIC',NULL,0,?)
                """, family, Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,
                    percentage_value,fixed_amount,priority,valid_from,valid_to,created_by_account_public_id,
                    created_at,published_at)
                VALUES (?,?,1,?,'PUBLISHED',?,?,?, ?,1,?,?,?,?,?)
                """, family, revision, name, layer, effect, effect.endsWith("PERCENT") ? 10 : null,
                effect.endsWith("FIXED") ? 10000 : null, Timestamp.from(from), Timestamp.from(to),
                manager.publicId(), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO promotion_product_scope(promotion_id,product_public_id)
                SELECT id,? FROM promotion WHERE public_id=?
                """, product, revision);
    }

    private void place(UUID product, String status, String title) {
        UUID revision = UUID.randomUUID(), section = UUID.randomUUID();
        int number = "PUBLISHED".equals(status) ? 1 : 2;
        jdbc.update("""
                INSERT INTO storefront_page_revision(public_id,page_key,revision_number,status,entity_version,
                    created_by_account_public_id,created_at,published_at)
                VALUES (?,'HOME',?,?,0,?,?,?)
                """, revision, number, status, manager.publicId(), Timestamp.from(NOW),
                "PUBLISHED".equals(status) ? Timestamp.from(NOW) : null);
        jdbc.update("""
                INSERT INTO storefront_section(public_id,page_revision_id,position,section_type,is_enabled,
                    title_vi,title_en,cta_label_vi,cta_label_en,cta_kind,cta_search_term)
                SELECT ?,id,0,'PRODUCT_COLLECTION',1,?,?,N'Xem',N'View','CATALOG_SEARCH',N'court'
                FROM storefront_page_revision WHERE public_id=?
                """, section, title, title, revision);
        jdbc.update("""
                INSERT INTO storefront_collection_product(section_id,product_public_id,position)
                SELECT id,?,0 FROM storefront_section WHERE public_id=?
                """, product, section);
    }

    private record ProductFixture(UUID productId, UUID variantId, UUID priceId) { }
}
