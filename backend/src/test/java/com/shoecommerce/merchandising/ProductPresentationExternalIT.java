package com.shoecommerce.merchandising;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.merchandising.ProductPresentationService.PublishDraft;
import com.shoecommerce.merchandising.ProductPresentationService.RevisionView;
import com.shoecommerce.merchandising.ProductPresentationService.SaveDraft;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.CorrelationIdFilter;
import com.shoecommerce.platform.api.InvalidRequestException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class ProductPresentationExternalIT {
    private static final String PASSWORD = "ChangeMe-42!";
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Autowired ProductPresentationService service;
    @Autowired ProductPresentationController controller;
    @Autowired AccountUserDetailsService users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    @BeforeEach
    void clearPresentationState() {
        jdbc.update("DELETE FROM audit_event WHERE action LIKE 'PRODUCT_PRESENTATION_%'");
        jdbc.update("DELETE FROM product_presentation_revision");
        MDC.clear();
    }

    @Test
    void createsAnEmptyFirstDraftAndPersistsCanonicalIncompleteText() {
        SessionPrincipal manager = actor(true).principal();
        UUID product = product("first-draft");

        RevisionView created = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        assertThat(created.revisionNumber()).isOne();
        assertThat(created.entityVersion()).isZero();
        assertThat(created.summaryVi()).isNull();
        assertThat(created.summaryEn()).isNull();

        RevisionView saved = service.saveDraft(manager, product,
                new SaveDraft(created.id(), 0L, "  Hồ sơ  kích cỡ  ", "   "));
        assertThat(saved.summaryVi()).isEqualTo("Hồ sơ  kích cỡ");
        assertThat(saved.summaryEn()).isNull();
        assertThat(saved.entityVersion()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT summary_vi FROM product_presentation_revision WHERE public_id=?",
                String.class, created.id())).isEqualTo("Hồ sơ  kích cỡ");
    }

    @Test
    void validatesLocalesVersionsPermissionAndPublishedImmutability() {
        SessionPrincipal manager = actor(true).principal();
        SessionPrincipal denied = actor(false).principal();
        UUID product = product("validation");

        assertThatThrownBy(() -> service.managementState(denied, product))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.saveDraft(denied, product, new SaveDraft(null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        RevisionView draft = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        assertThatThrownBy(() -> service.saveDraft(manager, product,
                new SaveDraft(draft.id(), 0L, "x".repeat(501), null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.publish(manager, product,
                new PublishDraft(draft.id(), 0L, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Vietnamese and English");
        assertThatThrownBy(() -> service.saveDraft(manager, product,
                new SaveDraft(UUID.randomUUID(), 0L, "VI", "EN")))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("PRODUCT_PRESENTATION_DRAFT_CONFLICT");

        RevisionView complete = service.saveDraft(manager, product,
                new SaveDraft(draft.id(), draft.entityVersion(), "VI one", "EN one"));
        RevisionView published = service.publish(manager, product,
                new PublishDraft(complete.id(), complete.entityVersion(), null));
        StoredIdentity before = stored(published.id());

        RevisionView copied = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        assertThat(copied.summaryVi()).isEqualTo("VI one");
        assertThat(copied.summaryEn()).isEqualTo("EN one");
        service.saveDraft(manager, product,
                new SaveDraft(copied.id(), copied.entityVersion(), "VI two", "EN two"));

        assertThat(stored(published.id())).isEqualTo(before);
    }

    @Test
    void atomicallyReplacesPublicationRollsBackAndBoundsReplay() {
        SessionPrincipal manager = actor(true).principal();
        UUID product = product("replace");
        RevisionView firstDraft = completeDraft(manager, product, "VI one", "EN one");
        RevisionView first = service.publish(manager, product,
                new PublishDraft(firstDraft.id(), firstDraft.entityVersion(), null));

        RevisionView second = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        second = service.saveDraft(manager, product,
                new SaveDraft(second.id(), second.entityVersion(), "VI two", "EN two"));
        PublishDraft replace = new PublishDraft(second.id(), second.entityVersion(), first.id());
        int auditsBeforeFailure = auditCount(product);

        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> service.publish(manager, product, replace))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(status(first.id())).isEqualTo("PUBLISHED");
        assertThat(status(second.id())).isEqualTo("DRAFT");
        assertThat(auditCount(product)).isEqualTo(auditsBeforeFailure);

        RevisionView secondPublished = service.publish(manager, product, replace);
        assertThat(status(first.id())).isEqualTo("RETIRED");
        assertThat(status(secondPublished.id())).isEqualTo("PUBLISHED");
        int audits = auditCount(product);
        assertThat(service.publish(manager, product, replace).id()).isEqualTo(secondPublished.id());
        assertThat(auditCount(product)).isEqualTo(audits);

        RevisionView third = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        third = service.saveDraft(manager, product,
                new SaveDraft(third.id(), third.entityVersion(), "VI three", "EN three"));
        RevisionView finalThird = third;
        assertThatThrownBy(() -> service.publish(manager, product,
                new PublishDraft(finalThird.id(), finalThird.entityVersion(), UUID.randomUUID())))
                .isInstanceOf(BusinessConflictException.class);
        service.publish(manager, product,
                new PublishDraft(third.id(), third.entityVersion(), secondPublished.id()));
        assertThatThrownBy(() -> service.publish(manager, product, replace))
                .isInstanceOf(BusinessConflictException.class)
                .extracting(error -> ((BusinessConflictException) error).code())
                .isEqualTo("PRODUCT_PRESENTATION_PUBLISH_CONFLICT");
    }

    @Test
    void serializesOneProductWithoutBlockingAnotherProduct() throws Exception {
        SessionPrincipal manager = actor(true).principal();
        UUID lockedProduct = product("locked");
        UUID independentProduct = product("independent");
        UUID racedProduct = product("raced");

        try (Connection lock = dataSource.getConnection();
                PreparedStatement statement = lock.prepareStatement(
                        "SELECT id FROM catalog_product WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?")) {
            lock.setAutoCommit(false);
            lock.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            statement.setObject(1, lockedProduct);
            statement.executeQuery().next();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var blocked = executor.submit(() -> service.saveDraft(manager, lockedProduct,
                        new SaveDraft(null, null, null, null)));
                var independent = executor.submit(() -> service.saveDraft(manager, independentProduct,
                        new SaveDraft(null, null, null, null)));
                assertThat(independent.get(3, TimeUnit.SECONDS).revisionNumber()).isOne();
                assertThat(blocked.isDone()).isFalse();
                lock.commit();
                assertThat(blocked.get(3, TimeUnit.SECONDS).revisionNumber()).isOne();
            }
        }

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> createOutcome(start, manager, racedProduct));
            var two = executor.submit(() -> createOutcome(start, manager, racedProduct));
            start.countDown();
            assertThat(List.of(one.get(), two.get())).filteredOn(RevisionView.class::isInstance).hasSize(1);
            assertThat(List.of(one.get(), two.get())).filteredOn(BusinessConflictException.class::isInstance)
                    .singleElement().satisfies(error -> assertThat(((BusinessConflictException) error).code())
                            .isEqualTo("PRODUCT_PRESENTATION_DRAFT_EXISTS"));
        }

        assertThat(draftCount(lockedProduct)).isOne();
        assertThat(draftCount(independentProduct)).isOne();
        assertThat(draftCount(racedProduct)).isOne();
    }

    @Test
    void exposesAuthenticatedHttpContractAndProtectsOperationsRoute() throws Exception {
        Actor manager = actor(true);
        UUID product = product("http");
        assertThat(send(new Browser(), "GET", "/api/v1/operations/product-presentations/" + product, null).statusCode())
                .isEqualTo(401);

        Browser browser = new Browser();
        login(browser, manager.login());
        HttpResponse<String> state = send(browser, "GET",
                "/api/v1/operations/product-presentations/" + product, null);
        assertThat(state.statusCode()).isEqualTo(200);
        assertThat(state.body()).contains("\"productId\":\"" + product + "\"", "\"draft\":null", "\"published\":null");

        HttpResponse<String> created = send(browser, "PUT",
                "/api/v1/operations/product-presentations/" + product + "/draft",
                "{\"expectedDraftRevisionId\":null,\"expectedEntityVersion\":null,\"summaryVi\":null,\"summaryEn\":null}");
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("\"revisionNumber\":1", "\"status\":\"DRAFT\"");
    }

    private Object createOutcome(CountDownLatch start, SessionPrincipal manager, UUID product) {
        try {
            start.await();
            return service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        } catch (RuntimeException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        }
    }

    private RevisionView completeDraft(SessionPrincipal manager, UUID product, String vi, String en) {
        RevisionView created = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
        return service.saveDraft(manager, product,
                new SaveDraft(created.id(), created.entityVersion(), vi, en));
    }

    private Actor actor(boolean permission) {
        UUID id = UUID.randomUUID();
        String login = "presentation-" + id + "@example.test";
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("""
                INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,
                    entity_version,created_at,updated_at)
                VALUES (?,?,?,'ENABLED',1,0,?,?)
                """, id, login, encoder.encode(PASSWORD), now, now);
        jdbc.update("""
                INSERT INTO iam_account_role(account_id,role_id)
                SELECT a.id,r.id FROM iam_user_account a CROSS JOIN iam_role_bundle r
                WHERE a.public_id=? AND r.code='OPERATIONS'
                """, id);
        if (permission) jdbc.update("""
                INSERT INTO iam_account_permission(account_id,permission_id,granted_at)
                SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p
                WHERE a.public_id=? AND p.code='STOREFRONT_MANAGE'
                """, now, id);
        SessionPrincipal principal = (SessionPrincipal) users.loadUserByUsername(login);
        principal.eraseCredentials();
        return new Actor(principal, login);
    }

    private UUID product(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (?,?,0,?)",
                id, "Presentation " + label, Timestamp.from(NOW));
        return id;
    }

    private StoredIdentity stored(UUID revisionId) {
        return jdbc.queryForObject("""
                SELECT product_id,revision_number,summary_vi,summary_en,created_by_account_public_id,created_at
                FROM product_presentation_revision WHERE public_id=?
                """, (row, index) -> new StoredIdentity(row.getLong("product_id"), row.getInt("revision_number"),
                        row.getString("summary_vi"), row.getString("summary_en"),
                        row.getObject("created_by_account_public_id", UUID.class), row.getTimestamp("created_at").toInstant()),
                revisionId);
    }

    private String status(UUID revisionId) {
        return jdbc.queryForObject("SELECT status FROM product_presentation_revision WHERE public_id=?",
                String.class, revisionId);
    }

    private int draftCount(UUID productId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_presentation_revision r
                JOIN catalog_product p ON p.id=r.product_id
                WHERE p.public_id=? AND r.status='DRAFT'
                """, Integer.class, productId);
    }

    private int auditCount(UUID productId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE action LIKE 'PRODUCT_PRESENTATION_%' AND details_json LIKE ?
                """, Integer.class, "%" + productId + "%");
    }

    private void login(Browser browser, String username) throws Exception {
        Csrf csrf = csrf(browser);
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
        HttpResponse<String> response = browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrf.header(), csrf.token()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> send(Browser browser, String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path));
        if (body == null) return browser.client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        Csrf csrf = csrf(browser);
        return browser.client.send(request.header("Content-Type", "application/json")
                .header(csrf.header(), csrf.token()).method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Csrf csrf(Browser browser) throws Exception {
        JsonNode node = json.readTree(browser.client.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        return new Csrf(node.get("headerName").asString(), node.get("token").asString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private record Actor(SessionPrincipal principal, String login) { }
    private record StoredIdentity(long productId, int revisionNumber, String summaryVi, String summaryEn,
            UUID creator, Instant createdAt) { }
    private record Csrf(String header, String token) { }
    private static final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
}
