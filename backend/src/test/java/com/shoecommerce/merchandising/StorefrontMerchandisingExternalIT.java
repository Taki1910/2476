package com.shoecommerce.merchandising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.CtaKind;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.ClonePublished;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.MediaKey;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.PublishDraft;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SaveDraft;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SectionInput;
import com.shoecommerce.merchandising.StorefrontMerchandisingService.SectionType;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;
import com.shoecommerce.platform.api.InvalidRequestException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class StorefrontMerchandisingExternalIT {
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Autowired StorefrontMerchandisingService merchandising;
    @Autowired StorefrontManagementController controller;
    @Autowired AccountUserDetailsService users;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @BeforeEach
    void clearHomepage() {
        jdbc.update("DELETE FROM storefront_page_revision");
    }

    @Test
    void publishesAtomicallyAndDegradesStaleRuntimeReferences() throws Exception {
        SessionPrincipal manager = actor("OPERATIONS", true);
        UUID publicProduct = product(true);
        UUID futureProduct = product(false);
        UUID currentOffer = promotion(manager.publicId(), true);
        UUID expiredOffer = promotion(manager.publicId(), false);

        assertThat(merchandising.homepage().sections()).isEmpty();
        HttpResponse<String> empty = get("/api/v1/storefront/homepage");
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).contains("\"publishedRevisionId\":null", "\"sections\":[]");

        var invalidReferenceDraft = controller.save(manager, new SaveDraft(null, null, List.of(
                hero(), collection(List.of(publicProduct, futureProduct)), spotlight(expiredOffer))));
        assertThat(invalidReferenceDraft.sections()).extracting(section -> section.position())
                .containsExactly(0, 1, 2);
        assertThat(merchandising.homepage().sections()).isEmpty();
        assertCode(() -> controller.publish(manager, publish(invalidReferenceDraft, null)),
                "STOREFRONT_REFERENCE_INVALID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_page_revision WHERE status='PUBLISHED'",
                Integer.class)).isZero();
        publishProduct(futureProduct);
        assertCode(() -> controller.publish(manager, publish(invalidReferenceDraft, null)),
                "STOREFRONT_REFERENCE_INVALID");

        assertThatThrownBy(() -> controller.save(manager,
                new SaveDraft(invalidReferenceDraft.revisionId(), 99L, List.of(hero()))))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("STOREFRONT_DRAFT_CONFLICT");
        assertCode(() -> controller.save(manager, new SaveDraft(invalidReferenceDraft.revisionId(),
                invalidReferenceDraft.entityVersion(), List.of(invalidCta()))), "STOREFRONT_CTA_INVALID");

        var firstDraft = controller.save(manager, new SaveDraft(invalidReferenceDraft.revisionId(),
                invalidReferenceDraft.entityVersion(), List.of(hero(), collection(List.of(publicProduct)),
                        spotlight(currentOffer))));
        var firstPublished = controller.publish(manager, publish(firstDraft, null));
        assertThat(merchandising.homepage().sections()).extracting(section -> section.position())
                .containsExactly(0, 1, 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_page_revision WHERE status='PUBLISHED'",
                Integer.class)).isOne();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO storefront_page_revision(public_id,page_key,revision_number,status,entity_version,
                    created_by_account_public_id,created_at,published_at)
                VALUES (?,'HOME',999,'PUBLISHED',0,?,?,?)
                """, UUID.randomUUID(), manager.publicId(), Timestamp.from(NOW), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        var secondDraft = controller.save(manager, new SaveDraft(null, null,
                List.of(hero(), collection(List.of(publicProduct, futureProduct)),
                        productCtaCollection(List.of(publicProduct), futureProduct), spotlight(currentOffer))));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO storefront_page_revision(public_id,page_key,revision_number,status,entity_version,
                    created_by_account_public_id,created_at)
                VALUES (?,'HOME',999,'DRAFT',0,?,?)
                """, UUID.randomUUID(), manager.publicId(), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> controller.publish(manager, publish(secondDraft, firstPublished.revisionId())))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(jdbc.queryForObject("SELECT public_id FROM storefront_page_revision WHERE status='PUBLISHED'",
                UUID.class)).isEqualTo(firstPublished.revisionId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_page_revision WHERE status='DRAFT'",
                Integer.class)).isOne();

        var secondPublished = controller.publish(manager, publish(secondDraft, firstPublished.revisionId()));
        assertThat(secondPublished.revisionNumber()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_page_revision WHERE status='PUBLISHED'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storefront_page_revision WHERE status='RETIRED'",
                Integer.class)).isOne();

        unpublishProduct(futureProduct);
        retirePromotion(currentOffer);
        var degraded = merchandising.homepage();
        assertThat(degraded.sections()).extracting(section -> section.type())
                .containsExactly(SectionType.HERO_BANNER, SectionType.PRODUCT_COLLECTION);
        assertThat(degraded.sections().get(1).products()).extracting(product -> product.id())
                .containsExactly(publicProduct);
        assertThat(degraded.sections().get(0).media().url()).startsWith("/products/");
    }

    @Test
    void enforcesPermissionMatrixAndDatabaseShape() throws Exception {
        SessionPrincipal manager = actor("OPERATIONS", true);
        SessionPrincipal operations = actor("OPERATIONS", false);
        SessionPrincipal cashier = actor("CASHIER", false);
        SessionPrincipal customer = actor("CUSTOMER", false);

        assertThat(controller.state(manager)).isNotNull();
        for (SessionPrincipal denied : List.of(operations, cashier, customer)) {
            assertThatThrownBy(() -> controller.state(denied)).isInstanceOf(AccessDeniedException.class);
        }
        assertThat(get("/api/v1/operations/storefront/homepage").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/storefront/homepage").statusCode()).isEqualTo(200);

        assertCode(() -> controller.save(manager, new SaveDraft(null, null,
                List.of(collection(java.util.Collections.singletonList(null))))),
                "STOREFRONT_COLLECTION_DUPLICATE");

        var draft = controller.save(manager, new SaveDraft(null, null, List.of(hero())));
        jdbc.update("DELETE FROM storefront_section WHERE page_revision_id=(SELECT id FROM storefront_page_revision WHERE public_id=?)",
                draft.revisionId());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO storefront_section(public_id,page_revision_id,position,section_type,is_enabled,
                    title_vi,title_en,cta_label_vi,cta_label_en,cta_kind,media_key)
                SELECT NEWID(),id,1,'HERO_BANNER',1,N'VI',N'EN',N'VI',N'EN','OFFERS','REMOTE_URL'
                FROM storefront_page_revision WHERE public_id=?
                """, draft.revisionId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns
                WHERE object_id IN (OBJECT_ID('storefront_page_revision'),OBJECT_ID('storefront_section'),
                    OBJECT_ID('storefront_collection_product'))
                  AND name IN ('product_name','price_amount','promotion_name','promotion_terms')
                """, Integer.class)).isZero();
    }

    @Test
    void clonesTheCurrentPublicationIntoANewImmutableDraft() {
        SessionPrincipal manager = actor("OPERATIONS", true);
        UUID product = product(true);
        UUID offer = promotion(manager.publicId(), true);
        var draft = controller.save(manager, new SaveDraft(null, null,
                List.of(hero(), collection(List.of(product)), spotlight(offer))));
        var published = controller.publish(manager, publish(draft, null));

        var cloned = controller.clonePublished(manager, new ClonePublished(published.revisionId()));

        assertThat(cloned.status()).isEqualTo("DRAFT");
        assertThat(cloned.revisionNumber()).isEqualTo(published.revisionNumber() + 1);
        assertThat(cloned.sections()).extracting(section -> section.type())
                .containsExactly(SectionType.HERO_BANNER, SectionType.PRODUCT_COLLECTION,
                        SectionType.PROMOTION_SPOTLIGHT);
        assertThat(cloned.sections().get(1).productIds()).containsExactly(product);
        assertThat(cloned.sections().get(0).titleVi()).isEqualTo(published.sections().get(0).titleVi());
        assertThat(controller.state(manager).published().revisionId()).isEqualTo(published.revisionId());
        assertThat(jdbc.queryForObject("SELECT status FROM storefront_page_revision WHERE public_id=?",
                String.class, published.revisionId())).isEqualTo("PUBLISHED");
    }

    @Test
    void rejectsUnsafeCloneAttemptsWithoutChangingPublication() {
        SessionPrincipal manager = actor("OPERATIONS", true);
        SessionPrincipal denied = actor("OPERATIONS", false);

        assertCode(() -> controller.clonePublished(manager, new ClonePublished(UUID.randomUUID())),
                "STOREFRONT_PUBLISHED_REQUIRED");
        assertThatThrownBy(() -> controller.clonePublished(denied, new ClonePublished(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);

        var firstDraft = controller.save(manager, new SaveDraft(null, null, List.of(hero())));
        var published = controller.publish(manager, publish(firstDraft, null));
        assertThatThrownBy(() -> controller.clonePublished(manager, new ClonePublished(UUID.randomUUID())))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("STOREFRONT_DRAFT_CONFLICT");

        var clone = controller.clonePublished(manager, new ClonePublished(published.revisionId()));
        assertThatThrownBy(() -> controller.clonePublished(manager, new ClonePublished(published.revisionId())))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("STOREFRONT_DRAFT_CONFLICT");
        assertThat(controller.state(manager).draft().revisionId()).isEqualTo(clone.revisionId());
        assertThat(controller.state(manager).published().revisionId()).isEqualTo(published.revisionId());
    }

    private SessionPrincipal actor(String role, boolean storefrontPermission) {
        UUID id = UUID.randomUUID();
        String login = "s11-" + role.toLowerCase() + "-" + id + "@example.test";
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("""
                INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,
                    entity_version,created_at,updated_at)
                VALUES (?,?,?,'ENABLED',1,0,?,?)
                """, id, login, "fixture", now, now);
        jdbc.update("""
                INSERT INTO iam_account_role(account_id,role_id)
                SELECT a.id,r.id FROM iam_user_account a CROSS JOIN iam_role_bundle r
                WHERE a.public_id=? AND r.code=?
                """, id, role);
        if (storefrontPermission) jdbc.update("""
                INSERT INTO iam_account_permission(account_id,permission_id,granted_at)
                SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p
                WHERE a.public_id=? AND p.code='STOREFRONT_MANAGE'
                """, now, id);
        return (SessionPrincipal) users.loadUserByUsername(login);
    }

    private UUID product(boolean published) {
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID(), price = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (?,?,0,?)",
                product, "S1.1 " + product, now);
        jdbc.update("""
                INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,
                    entity_version,created_at)
                SELECT ?,id,?,'42',N'Black',?,0,? FROM catalog_product WHERE public_id=?
                """, variant, "S11-" + variant, published ? "PUBLISHED" : "DRAFT", now, product);
        jdbc.update("""
                INSERT INTO pricing_variant_price(public_id,variant_id,amount,entity_version,updated_at,valid_from)
                SELECT ?,id,1000000,0,?,? FROM catalog_product_variant WHERE public_id=?
                """, price, now, Timestamp.from(NOW.minusSeconds(60)), variant);
        return product;
    }

    private UUID promotion(UUID creator, boolean current) {
        UUID family = UUID.randomUUID(), revision = UUID.randomUUID();
        Instant at = Instant.now();
        Instant from = current ? at.minusSeconds(60) : at.minusSeconds(7200);
        Instant to = current ? at.plusSeconds(7200) : at.minusSeconds(3600);
        jdbc.update("""
                INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at)
                VALUES (?,'CLAIMABLE',NULL,1,?)
                """, family, Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,
                    priority,valid_from,valid_to,created_by_account_public_id,created_at,published_at,
                    customer_summary,customer_terms)
                VALUES (?,?,1,N'S1.1 offer','PUBLISHED','SHIPPING','FREE_SHIPPING',1,?,?,?,?,?,N'Summary',N'Terms')
                """, family, revision, Timestamp.from(from), Timestamp.from(to), creator, Timestamp.from(NOW),
                Timestamp.from(NOW));
        return family;
    }

    private void publishProduct(UUID product) {
        jdbc.update("UPDATE catalog_product_variant SET lifecycle_status='PUBLISHED' WHERE product_id=(SELECT id FROM catalog_product WHERE public_id=?)",
                product);
    }

    private void unpublishProduct(UUID product) {
        jdbc.update("UPDATE catalog_product_variant SET lifecycle_status='DRAFT' WHERE product_id=(SELECT id FROM catalog_product WHERE public_id=?)",
                product);
    }

    private void retirePromotion(UUID family) {
        jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE family_public_id=? AND status='PUBLISHED'",
                Timestamp.from(NOW), family);
    }

    private static PublishDraft publish(StorefrontMerchandisingService.RevisionState draft, UUID published) {
        return new PublishDraft(draft.revisionId(), draft.entityVersion(), published);
    }

    private static SectionInput hero() {
        return new SectionInput(SectionType.HERO_BANNER, true, "Giày cho hôm nay", "Shoes for today",
                "Khám phá bộ sưu tập", "Explore the collection", "Xem ưu đãi", "View offers",
                CtaKind.OFFERS, null, null, null, MediaKey.COURT_CLASSIC, List.of());
    }

    private static SectionInput collection(List<UUID> products) {
        return new SectionInput(SectionType.PRODUCT_COLLECTION, true, "Sản phẩm chọn lọc", "Curated products",
                null, null, "Xem giày", "Browse shoes", CtaKind.CATALOG_SEARCH, null, "court", null, null,
                products);
    }

    private static SectionInput spotlight(UUID family) {
        return new SectionInput(SectionType.PROMOTION_SPOTLIGHT, true, "Ưu đãi", "Offer", null, null,
                "Xem chi tiết", "View details", CtaKind.PROMOTION_DETAIL, family, null, family, null, List.of());
    }

    private static SectionInput productCtaCollection(List<UUID> products, UUID target) {
        return new SectionInput(SectionType.PRODUCT_COLLECTION, true, "Bộ sưu tập", "Collection", null, null,
                "Xem sản phẩm", "View product", CtaKind.PRODUCT_DETAIL, target, null, null, null, products);
    }

    private static SectionInput invalidCta() {
        return new SectionInput(SectionType.HERO_BANNER, true, "VI", "EN", null, null, "VI", "EN",
                CtaKind.OFFERS, UUID.randomUUID(), null, null, MediaKey.COURT_CLASSIC, List.of());
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(InvalidRequestException.class)
                .extracting(error -> ((InvalidRequestException) error).code()).isEqualTo(code);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
