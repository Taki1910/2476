package com.shoecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.IdentityAdministrationService;
import com.shoecommerce.identity.RoleCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.inventory.InventoryReservationService;
import com.shoecommerce.inventory.InventoryAdjustmentService;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.pricing.PriceQuoteService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class VerticalSlice2CustomerCatalogExternalIT {
    private static final String PASSWORD = "Correct-Horse-42";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;
    @Autowired IdentityAdministrationService identities;
    @Autowired ScopeAdministrationService scopes;
    @Autowired CatalogService catalog;
    @Autowired StorefrontCatalogService storefront;
    @Autowired PriceQuoteService pricing;
    @Autowired InventoryReservationService reservations;
    @Autowired InventoryAdjustmentService adjustments;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @Test
    void browsesPublishedCatalogFromAvailabilityWithoutMutatingInventory() {
        Fixture fixture = fixture("browse");
        InventoryState before = inventory(fixture.availableVariant());

        var products = storefront.browse();
        var detail = storefront.detail(fixture.product());

        assertThat(products).filteredOn(product -> product.id().equals(fixture.product())).singleElement().satisfies(product -> {
            assertThat(product.name()).startsWith("Court Runner");
            assertThat(product.variantCount()).isEqualTo(2);
            assertThat(product.availableVariantCount()).isEqualTo(1);
        });
        assertThat(detail.variants()).extracting(StorefrontCatalogService.VariantView::size)
                .containsExactly("41", "42");
        assertThat(detail.variants()).extracting(StorefrontCatalogService.VariantView::availability)
                .containsExactly("AVAILABLE", "UNAVAILABLE");
        assertThat(storefront.browse()).isNotEmpty();
        assertThat(inventory(fixture.availableVariant())).isEqualTo(before);
        assertThatThrownBy(() -> storefront.detail(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void projectsOnlyPublishedProductPresentationAcrossBrowseDetailAndCustomerJson() throws Exception {
        Fixture fixture = fixture("presentation");
        UUID publishedProduct = fixture.product();
        UUID draftProduct = visibleProduct(fixture, "draft-only");
        UUID retiredProduct = visibleProduct(fixture, "retired-only");
        insertPresentation(publishedProduct, fixture.operations().publicId(), "PUBLISHED", "VI", "EN");
        insertPresentation(draftProduct, fixture.operations().publicId(), "DRAFT", "Draft VI", "Draft EN");
        insertPresentation(retiredProduct, fixture.operations().publicId(), "RETIRED", "Old VI", "Old EN");

        var products = storefront.browse();
        var expected = new StorefrontCatalogService.ProductPresentation(
                new StorefrontCatalogService.LocalizedSummary("VI", "EN"));
        assertThat(products).filteredOn(product -> product.id().equals(publishedProduct)).singleElement()
                .extracting(StorefrontCatalogService.ProductSummary::presentation).isEqualTo(expected);
        assertThat(products).filteredOn(product -> product.id().equals(draftProduct)).singleElement()
                .extracting(StorefrontCatalogService.ProductSummary::presentation).isNull();
        assertThat(products).filteredOn(product -> product.id().equals(retiredProduct)).singleElement()
                .extracting(StorefrontCatalogService.ProductSummary::presentation).isNull();
        assertThat(storefront.detail(publishedProduct).presentation()).isEqualTo(expected);
        assertThat(storefront.detail(draftProduct).presentation()).isNull();
        assertThat(storefront.detail(retiredProduct).presentation()).isNull();

        Browser browser = new Browser();
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(
                uri("/api/v1/storefront/products/" + publishedProduct)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.at("/presentation/summary/vi").asText()).isEqualTo("VI");
        assertThat(body.at("/presentation/summary/en").asText()).isEqualTo("EN");
        assertThat(body.at("/presentation").has("entityVersion")).isFalse();
        assertThat(body.at("/presentation").has("revisionNumber")).isFalse();
        assertThat(body.at("/presentation").has("status")).isFalse();
    }

    @Test
    void createsExpiringImmutableQuoteFromEffectivePriceVersion() {
        Fixture fixture = fixture("quote");
        InventoryState before = inventory(fixture.availableVariant());

        PriceQuoteService.QuoteView first = pricing.quote(fixture.customer(), fixture.availableVariant());
        catalog.setPrice(fixture.operations(), fixture.availableVariant(), 135_000);
        PriceQuoteService.QuoteView second = pricing.quote(fixture.customer(), fixture.availableVariant());

        assertThat(first.amount()).isEqualTo(125_000);
        assertThat(first.currency()).isEqualTo("VND");
        assertThat(Duration.between(first.quotedAt(), first.expiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(second.amount()).isEqualTo(135_000);
        assertThat(second.priceVersionId()).isNotEqualTo(first.priceVersionId());
        assertThat(jdbc.queryForObject("SELECT amount FROM pricing_price_quote WHERE public_id = ?", Long.class, first.id()))
                .isEqualTo(125_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pricing_variant_price prices JOIN catalog_product_variant variants ON variants.id = prices.variant_id WHERE variants.public_id = ?", Integer.class, fixture.availableVariant()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pricing_variant_price prices JOIN catalog_product_variant variants ON variants.id = prices.variant_id WHERE variants.public_id = ? AND prices.valid_to IS NULL", Integer.class, fixture.availableVariant()))
                .isEqualTo(1);
        assertThat(inventory(fixture.availableVariant())).isEqualTo(before);
        assertThatThrownBy(() -> pricing.quote(fixture.customer(), fixture.unavailableVariant()))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("This variant is currently unavailable.");
        assertThatThrownBy(() -> pricing.quote(fixture.customer(), fixture.draftVariant()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fromPriceExcludesDraftAndClosedPricesAndMatchesTheCurrentQuote() {
        Fixture fixture = fixture("from-price");
        catalog.setPrice(fixture.operations(), fixture.unavailableVariant(), 175_000);
        catalog.setPrice(fixture.operations(), fixture.draftVariant(), 1_000);
        var historicalQuote = pricing.quote(fixture.customer(), fixture.availableVariant());
        catalog.setPrice(fixture.operations(), fixture.availableVariant(), 149_000);

        assertThat(storefront.browse()).filteredOn(product -> product.id().equals(fixture.product()))
                .singleElement().satisfies(product -> assertThat(product.fromAmount()).isEqualTo(149_000));
        assertThat(pricing.quote(fixture.customer(), fixture.availableVariant()).amount()).isEqualTo(149_000);
        assertThat(historicalQuote.amount()).isEqualTo(125_000);
        assertThat(storefront.detail(fixture.product()).variants()).hasSize(2);
    }

    @Test
    void derivesOneConsistentPriceSummaryFromReturnedVariants() {
        Fixture fixture = fixture("evidence-price");

        var single = storefront.detail(fixture.product());
        assertThat(single.pricing()).isEqualTo(
                new StorefrontCatalogService.PriceSummary("SINGLE", 125_000, 125_000, "VND"));
        assertThat(single.variants()).allSatisfy(variant ->
                assertThat(variant.amount()).isEqualTo(single.pricing().minimumAmount()));

        catalog.setPrice(fixture.operations(), fixture.unavailableVariant(), 175_000);
        var range = storefront.detail(fixture.product());
        List<Long> amounts = range.variants().stream().map(StorefrontCatalogService.VariantView::amount).toList();
        assertThat(range.pricing()).isEqualTo(
                new StorefrontCatalogService.PriceSummary("RANGE", 125_000, 175_000, "VND"));
        assertThat(amounts).containsExactly(125_000L, 175_000L);
        assertThat(range.pricing().minimumAmount()).isEqualTo(amounts.stream().mapToLong(Long::longValue).min().orElseThrow());
        assertThat(range.pricing().maximumAmount()).isEqualTo(amounts.stream().mapToLong(Long::longValue).max().orElseThrow());
    }

    @Test
    void projectsTrimmedOrderedMediaWithoutInventingEntries() {
        Fixture fixture = fixture("evidence-media");
        String name = "Court Runner evidence-media";

        jdbc.update("UPDATE catalog_product SET primary_image = ?, hero_image = ? WHERE public_id = ?",
                " /products/pair.png ", "/products/pair.png", fixture.product());
        assertThat(storefront.detail(fixture.product()).media()).containsExactly(
                new StorefrontCatalogService.MediaView("/products/pair.png", 0, name));

        jdbc.update("UPDATE catalog_product SET hero_image = ? WHERE public_id = ?",
                "/products/PAIR.png", fixture.product());
        assertThat(storefront.detail(fixture.product()).media())
                .extracting(StorefrontCatalogService.MediaView::url)
                .containsExactly("/products/pair.png", "/products/PAIR.png");

        jdbc.update("UPDATE catalog_product SET primary_image = NULL, hero_image = '   ' WHERE public_id = ?",
                fixture.product());
        assertThat(storefront.detail(fixture.product()).media()).isEmpty();
    }

    @Test
    void returnsOnlyCompleteVisibleSizeGuidanceAndIgnoresExtraRanges() {
        Fixture fixture = fixture("evidence-fit");
        long profileId = insertFitProfile(fixture.product(), "TRUE_TO_SIZE", "REGULAR");
        insertFitRange(profileId, "41", 250, 257, 88, 99);
        insertFitRange(profileId, "42", 257, 264, 90, 101);
        insertFitRange(profileId, "99", 300, 307, 110, 120);

        var detail = storefront.detail(fixture.product());
        assertThat(detail.fitSupported()).isTrue();
        assertThat(detail.fitGuidance()).isNotNull();
        assertThat(detail.fitGuidance().fitAssistantSupported()).isTrue();
        assertThat(detail.fitGuidance().ranges())
                .extracting(StorefrontCatalogService.FitRangeView::size)
                .containsExactly("41", "42");
        assertThatThrownBy(() -> insertFitRange(profileId, "41", 250, 257, 88, 99))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void omitsFitGuidanceWhenAVisibleSizeHasNoRange() {
        Fixture fixture = fixture("fit-miss");
        long profileId = insertFitProfile(fixture.product(), "TRUE_TO_SIZE", "REGULAR");
        insertFitRange(profileId, "41", 250, 257, 88, 99);

        assertThat(storefront.detail(fixture.product()).fitGuidance()).isNull();
    }

    @Test
    void ordersGenuinelyMixedVisibleSizesNumericFirstWithoutInventingASelection() {
        Fixture fixture = fixture("mixed");
        UUID product = catalog.createProduct(fixture.operations(), "Mixed Size Runner");
        for (String size : List.of("XS", "10", "M", "9")) {
            UUID variant = catalog.createVariant(fixture.operations(), product,
                    "VS2-MIX-" + size + "-" + shortId(), size, "Ink");
            catalog.setPrice(fixture.operations(), variant, 125_000);
            adjustments.adjust(fixture.operations(), variant, fixture.location(), 1,
                    "Mixed-size fixture", UUID.randomUUID().toString());
            catalog.publish(fixture.operations(), variant);
        }
        long profileId = insertFitProfile(product, "TRUE_TO_SIZE", "REGULAR");
        insertFitRange(profileId, "XS", 220, 227, 80, 90);
        insertFitRange(profileId, "10", 270, 277, 95, 105);
        insertFitRange(profileId, "M", 240, 247, 85, 95);
        insertFitRange(profileId, "9", 260, 267, 92, 102);

        var detail = storefront.detail(product);
        assertThat(detail.variants()).extracting(StorefrontCatalogService.VariantView::size)
                .containsExactly("9", "10", "M", "XS");
        assertThat(detail.fitGuidance()).isNotNull();
        assertThat(detail.fitGuidance().ranges()).extracting(StorefrontCatalogService.FitRangeView::size)
                .containsExactly("9", "10", "M", "XS");
    }

    @Test
    void exposesBrowsingButKeepsQuotesAndStaffActionsProtected() throws Exception {
        Fixture fixture = fixture("api");
        Browser anonymous = new Browser();
        HttpResponse<String> publicProducts = anonymous.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(publicProducts.statusCode()).isEqualTo(200);
        assertThat(publicProducts.body()).contains("Court Runner").contains("125000");
        assertThat(request(anonymous, "POST", "/api/v1/storefront/price-quotes", "{\"variantId\":\"" + fixture.availableVariant() + "\"}").statusCode()).isEqualTo(401);

        Browser operations = new Browser();
        login(operations, fixture.operationsLogin());
        HttpResponse<String> forbidden = operations.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(forbidden.statusCode()).isEqualTo(200);

        Browser customer = new Browser();
        login(customer, fixture.customerLogin());
        HttpResponse<String> products = customer.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(products.statusCode()).isEqualTo(200);
        assertThat(products.body()).contains("Court Runner").contains("125000");
        HttpResponse<String> detail = customer.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products/" + fixture.product())).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("AVAILABLE").contains("UNAVAILABLE").doesNotContain("onHand").doesNotContain("reserved");
        JsonNode detailJson = json.readTree(detail.body());
        assertThat(detailJson.path("pricing").path("state").asString()).isEqualTo("SINGLE");
        assertThat(detailJson.path("pricing").path("minimumAmount").asLong()).isEqualTo(125_000);
        assertThat(detailJson.path("media").isArray()).isTrue();
        assertThat(detailJson.has("fitGuidance")).isTrue();
        assertThat(detailJson.get("fitGuidance").isNull()).isTrue();
        assertThat(detailJson.has("variants")).isTrue();
        assertThat(detailJson.has("primaryImage")).isTrue();
        assertThat(detailJson.has("fitSupported")).isTrue();
        HttpResponse<String> quote = request(customer, "POST", "/api/v1/storefront/price-quotes", "{\"variantId\":\"" + fixture.availableVariant() + "\"}");
        assertThat(quote.statusCode()).isEqualTo(201);
        assertThat(quote.body()).contains("125000").contains("VND").contains("priceVersionId").contains("expiresAt");
        HttpResponse<String> invalidQuote = request(customer, "POST", "/api/v1/storefront/price-quotes", "{}");
        assertThat(invalidQuote.statusCode()).isEqualTo(400);
        assertThat(invalidQuote.body()).contains("VALIDATION_FAILED");
        HttpResponse<String> missing = customer.client.send(HttpRequest.newBuilder(uri("/api/v1/storefront/products/" + UUID.randomUUID())).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("STOREFRONT_PRODUCT_NOT_FOUND");
        HttpResponse<String> staffEndpoint = customer.client.send(HttpRequest.newBuilder(uri("/api/v1/catalog/sellable/variants/" + fixture.availableVariant())).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(staffEndpoint.statusCode()).isEqualTo(403);
    }

    @Test
    void exposesDataDrivenHeroCandidatesWithoutFixingAProduct() throws Exception {
        Fixture fixture = fixture("hero");
        var hero = storefront.hero();

        assertThat(hero.candidates()).filteredOn(product -> product.id().equals(fixture.product())).singleElement()
                .satisfies(product -> {
                    assertThat(product.featured()).isFalse();
                    assertThat(product.newArrival()).isFalse();
                    assertThat(product.campaignEligible()).isTrue();
                    assertThat(product.merchandisingRank()).isEqualTo(100);
                });
        Browser anonymous = new Browser();
        HttpResponse<String> response = anonymous.client.send(
                HttpRequest.newBuilder(uri("/api/v1/storefront/hero")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("candidates").contains("topSeller");
    }

    private Fixture fixture(String suffix) {
        SessionPrincipal admin = bootstrapAdmin();
        String operationsLogin = "vs2-" + suffix + "-ops-" + UUID.randomUUID() + "@example.com";
        String customerLogin = "vs2-" + suffix + "-customer-" + UUID.randomUUID() + "@example.com";
        UUID operationsId = identities.createAccount(admin, operationsLogin, PASSWORD, RoleCode.OPERATIONS);
        identities.createAccount(admin, customerLogin, PASSWORD, RoleCode.CUSTOMER);
        UUID branch = scopes.createBranch(admin, "VS2-" + suffix + "-" + shortId(), "Customer branch");
        UUID location = scopes.createLocation(admin, branch, "FLOOR-" + shortId(), "Sales floor");
        scopes.setAssignment(admin, operationsId, branch, location, true);
        SessionPrincipal operations = principal(operationsLogin);
        SessionPrincipal customer = principal(customerLogin);
        UUID product = catalog.createProduct(operations, "Court Runner " + suffix);
        UUID available = catalog.createVariant(operations, product, "VS2-A-" + shortId(), "41", "Ink");
        catalog.setPrice(operations, available, 125_000);
        adjustments.adjust(operations, available, location, 2, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(operations, available);
        reservations.reserve(customer, available, location, 1);
        UUID unavailable = catalog.createVariant(operations, product, "VS2-U-" + shortId(), "42", "Chalk");
        catalog.setPrice(operations, unavailable, 125_000);
        adjustments.adjust(operations, unavailable, location, 1, "Test fixture", UUID.randomUUID().toString());
        catalog.publish(operations, unavailable);
        reservations.reserve(customer, unavailable, location, 1);
        UUID draft = catalog.createVariant(operations, product, "VS2-D-" + shortId(), "43", "Clay");
        catalog.setPrice(operations, draft, 125_000);
        adjustments.adjust(operations, draft, location, 1, "Test fixture", UUID.randomUUID().toString());
        return new Fixture(product, available, unavailable, draft, location, operations, customer,
                operationsLogin, customerLogin);
    }

    private UUID visibleProduct(Fixture fixture, String label) {
        UUID product = catalog.createProduct(fixture.operations(), "Presentation " + label + " " + shortId());
        UUID variant = catalog.createVariant(fixture.operations(), product,
                "VS2-PRESENTATION-" + shortId(), "40", "Ink");
        catalog.setPrice(fixture.operations(), variant, 125_000);
        adjustments.adjust(fixture.operations(), variant, fixture.location(), 1,
                "Presentation fixture", UUID.randomUUID().toString());
        catalog.publish(fixture.operations(), variant);
        return product;
    }

    private void insertPresentation(UUID productId, UUID creator, String status, String vi, String en) {
        Timestamp publishedAt = status.equals("DRAFT") ? null : Timestamp.from(Instant.now());
        Timestamp retiredAt = status.equals("RETIRED") ? Timestamp.from(Instant.now()) : null;
        jdbc.update("""
                INSERT INTO product_presentation_revision(public_id,product_id,revision_number,status,
                    summary_vi,summary_en,entity_version,created_by_account_public_id,created_at,published_at,retired_at)
                SELECT NEWID(),id,1,?,?,?,?,?,?,?,? FROM catalog_product WHERE public_id=?
                """, status, vi, en, status.equals("DRAFT") ? 0 : 1, creator,
                Timestamp.from(Instant.now()), publishedAt, retiredAt, productId);
    }

    private long insertFitProfile(UUID productId, String tendency, String width) {
        UUID profileId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_shoe_fit_profile(public_id, product_id, size_system,
                    fit_tendency, width_profile, created_at)
                SELECT ?, id, 'EU', ?, ?, ? FROM catalog_product WHERE public_id = ?
                """, profileId, tendency, width, Timestamp.from(Instant.now()), productId);
        return jdbc.queryForObject("SELECT id FROM catalog_shoe_fit_profile WHERE public_id = ?",
                Long.class, profileId);
    }

    private void insertFitRange(long profileId, String size, double minLength, double maxLength,
            double minWidth, double maxWidth) {
        jdbc.update("""
                INSERT INTO catalog_shoe_fit_size_range(profile_id, size_label,
                    min_foot_length_mm, max_foot_length_mm, min_foot_width_mm, max_foot_width_mm)
                VALUES (?, ?, ?, ?, ?, ?)
                """, profileId, size, minLength, maxLength, minWidth, maxWidth);
    }

    private InventoryState inventory(UUID variantId) {
        return jdbc.queryForObject("SELECT SUM(balances.on_hand), SUM(balances.reserved) FROM inventory_balance balances JOIN catalog_product_variant variants ON variants.id = balances.variant_id WHERE variants.public_id = ?", (rs, row) -> new InventoryState(rs.getLong(1), rs.getLong(2)), variantId);
    }

    private SessionPrincipal bootstrapAdmin() {
        UUID id = UUID.randomUUID(); String login = "vs2-admin-" + id + "@example.com"; Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id, login_normalized, password_hash, status, auth_version, entity_version, created_at, updated_at) VALUES (?, ?, ?, 'ENABLED', 1, 0, ?, ?)", id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("INSERT INTO iam_account_role(account_id, role_id) SELECT accounts.id, roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'", id);
        return principal(login);
    }

    private SessionPrincipal principal(String login) { SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login); principal.eraseCredentials(); return principal; }
    private static String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
    private void login(Browser browser, String username) throws Exception {
        Csrf csrf = csrf(browser);
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type", "application/x-www-form-urlencoded").header(csrf.header(), csrf.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
    private HttpResponse<String> request(Browser browser, String method, String path, String body) throws Exception {
        Csrf csrf = csrf(browser);
        return browser.client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Csrf csrf(Browser browser) throws Exception { JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()); return new Csrf(node.get("headerName").asString(), node.get("token").asString()); }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private static final class Browser { private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Csrf(String header, String token) { }
    private record InventoryState(long onHand, long reserved) { }
    private record Fixture(UUID product, UUID availableVariant, UUID unavailableVariant, UUID draftVariant,
            UUID location, SessionPrincipal operations, SessionPrincipal customer, String operationsLogin,
            String customerLogin) { }
}
