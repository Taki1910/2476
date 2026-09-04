package com.shoecommerce.identity;

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
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.shoecommerce.branch.ScopeAdministrationService;
import com.shoecommerce.platform.api.CorrelationIdFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class Foundation1SecurityExternalIT {

    private static final String PASSWORD = "Correct-Horse-42";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountUserDetailsService userDetailsService;

    @Autowired
    private IdentityAdministrationService identityAdministration;

    @Autowired
    private ScopeAdministrationService scopeAdministration;

    @Autowired
    private AuthorizationPolicy authorization;

    @Autowired
    private OwnershipPolicy ownership;

    @Test
    void provesIdentitySessionCsrfScopeRevocationAndSqlConstraints() throws Exception {
        UUID adminId = bootstrapAdministrator();
        SessionPrincipal admin = principal("admin@example.com");

        UUID operationsId = identityAdministration.createAccount(
                admin, "operations@example.com", PASSWORD, RoleCode.OPERATIONS);
        UUID cashierId = identityAdministration.createAccount(
                admin, "cashier@example.com", PASSWORD, RoleCode.CASHIER);
        UUID disabledId = identityAdministration.createAccount(
                admin, "disabled@example.com", PASSWORD, RoleCode.CUSTOMER);
        identityAdministration.setAccountEnabled(admin, disabledId, false);

        UUID branchA = scopeAdministration.createBranch(admin, "HCM", "Ho Chi Minh");
        UUID branchB = scopeAdministration.createBranch(admin, "HN", "Ha Noi");
        UUID locationA = scopeAdministration.createLocation(
                admin, branchA, "HCM-FLOOR", "HCM Sales Floor");
        UUID locationB = scopeAdministration.createLocation(
                admin, branchB, "HN-FLOOR", "HN Sales Floor");
        scopeAdministration.setAssignment(admin, operationsId, branchA, locationA, true);

        SessionPrincipal operations = principal("operations@example.com");
        authorization.requirePermission(operations, PermissionCode.CATALOG_MANAGE);
        authorization.requireBranchAccess(operations, branchA);
        authorization.requireLocationAccess(operations, locationA);
        ownership.requireOwnership(operations, operationsId);
        assertThatThrownBy(() -> authorization.requirePermission(admin, PermissionCode.CATALOG_MANAGE))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireBranchAccess(operations, branchB))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireLocationAccess(operations, locationB))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ownership.requireOwnership(operations, adminId))
                .isInstanceOf(AccessDeniedException.class);

        BrowserSession adminBrowser = new BrowserSession();
        assertThat(login(adminBrowser, "admin@example.com", PASSWORD).statusCode()).isEqualTo(200);
        HttpResponse<String> me = get(adminBrowser, "/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("IDENTITY_MANAGE").doesNotContain("CATALOG_MANAGE");
        assertThat(post(adminBrowser, "/api/v1/auth/logout", null, "").statusCode()).isEqualTo(403);
        assertThat(post(adminBrowser, "/api/v1/auth/logout", csrf(adminBrowser), "").statusCode())
                .isEqualTo(204);
        assertThat(get(adminBrowser, "/api/v1/auth/me").statusCode()).isEqualTo(401);

        BrowserSession wrongPassword = new BrowserSession();
        HttpResponse<String> invalid = login(wrongPassword, "admin@example.com", "Wrong-Password-42");
        BrowserSession disabled = new BrowserSession();
        HttpResponse<String> disabledLogin = login(disabled, "disabled@example.com", PASSWORD);
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(disabledLogin.statusCode()).isEqualTo(401);
        assertThat(invalid.body()).isEqualTo(disabledLogin.body()).contains("AUTHENTICATION_FAILED");

        BrowserSession operationsBrowser = new BrowserSession();
        assertThat(login(operationsBrowser, "operations@example.com", PASSWORD).statusCode()).isEqualTo(200);
        assertAuditFailureRollsBackGrant(admin, operationsId);
        identityAdministration.setDirectPermission(
                admin, operationsId, PermissionCode.IDENTITY_MANAGE, true);
        HttpResponse<String> stale = get(operationsBrowser, "/api/v1/auth/me");
        assertThat(stale.statusCode()).isEqualTo(401);
        assertThat(stale.body()).contains("SESSION_AUTHORITY_STALE");
        BrowserSession refreshedOperations = new BrowserSession();
        assertThat(login(refreshedOperations, "operations@example.com", PASSWORD).body())
                .contains("IDENTITY_MANAGE");

        BrowserSession cashierBrowser = new BrowserSession();
        assertThat(login(cashierBrowser, "cashier@example.com", PASSWORD).statusCode()).isEqualTo(200);
        identityAdministration.setAccountEnabled(admin, cashierId, false);
        assertThat(get(cashierBrowser, "/api/v1/auth/me").statusCode()).isEqualTo(401);

        assertSqlConstraints(operationsId, branchA, locationB);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE action IN ('ACCOUNT_DISABLED', 'PERMISSION_GRANTED', 'ASSIGNMENT_GRANTED')
                """, Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '2' AND success = 1
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT password_hash FROM iam_user_account WHERE public_id = ?
                """, String.class, adminId)).startsWith("{bcrypt}");
    }

    @Test
    void selfRegistrationCreatesOnlyCustomerAuthorityAndRejectsDuplicates() throws Exception {
        BrowserSession browser = new BrowserSession();
        String login = "new-customer-" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(java.util.Map.of("login", login, "password", PASSWORD));

        HttpResponse<String> created = postJson(browser, "/api/v1/auth/register", csrf(browser), body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains(login);
        assertThat(login(browser, login, PASSWORD).body())
                .contains("CUSTOMER", "CATALOG_BROWSE", "ORDER_PLACE")
                .doesNotContain("POS_SELL", "FULFILL_PICKUP", "REPORT_VIEW");

        BrowserSession duplicate = new BrowserSession();
        HttpResponse<String> conflict = postJson(duplicate, "/api/v1/auth/register", csrf(duplicate), body);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("IDENTITY_ALREADY_EXISTS");
    }

    private UUID bootstrapAdministrator() {
        UUID publicId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO iam_user_account(
                    public_id, login_normalized, password_hash, status,
                    auth_version, entity_version, created_at, updated_at)
                VALUES (?, 'admin@example.com', ?, 'ENABLED', 1, 0, ?, ?)
                """, publicId, passwordEncoder.encode(PASSWORD), now, now);
        jdbcTemplate.update("""
                INSERT INTO iam_account_role(account_id, role_id)
                SELECT accounts.id, roles.id
                FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles
                WHERE accounts.public_id = ? AND roles.code = 'ADMINISTRATOR'
                """, publicId);
        return publicId;
    }

    private SessionPrincipal principal(String login) {
        SessionPrincipal principal = (SessionPrincipal) userDetailsService.loadUserByUsername(login);
        principal.eraseCredentials();
        return principal;
    }

    private void assertSqlConstraints(UUID operationsId, UUID branchA, UUID locationB) {
        Timestamp now = Timestamp.from(Instant.now());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO iam_user_account(
                    public_id, login_normalized, password_hash, status,
                    auth_version, entity_version, created_at, updated_at)
                VALUES (?, 'ADMIN@example.com', ?, 'ENABLED', 1, 0, ?, ?)
                """, UUID.randomUUID(), passwordEncoder.encode(PASSWORD), now, now))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam_user_account WHERE public_id = ?", Long.class, operationsId);
        Long branchId = jdbcTemplate.queryForObject(
                "SELECT id FROM org_branch WHERE public_id = ?", Long.class, branchA);
        Long locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM org_location WHERE public_id = ?", Long.class, locationB);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO iam_staff_assignment(
                    public_id, account_id, branch_id, location_id, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """, UUID.randomUUID(), accountId, branchId, locationId, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertAuditFailureRollsBackGrant(SessionPrincipal admin, UUID operationsId) {
        Long versionBefore = jdbcTemplate.queryForObject(
                "SELECT auth_version FROM iam_user_account WHERE public_id = ?",
                Long.class,
                operationsId);
        MDC.put(CorrelationIdFilter.MDC_KEY, "x".repeat(65));
        try {
            assertThatThrownBy(() -> identityAdministration.setDirectPermission(
                    admin, operationsId, PermissionCode.IDENTITY_MANAGE, true))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auth_version FROM iam_user_account WHERE public_id = ?",
                Long.class,
                operationsId)).isEqualTo(versionBefore);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam_account_permission account_permissions
                JOIN iam_permission permissions ON permissions.id = account_permissions.permission_id
                JOIN iam_user_account accounts ON accounts.id = account_permissions.account_id
                WHERE accounts.public_id = ? AND permissions.code = 'IDENTITY_MANAGE'
                """, Integer.class, operationsId)).isZero();
    }

    private HttpResponse<String> login(
            BrowserSession session,
            String username,
            String password) throws Exception {
        String body = "username=" + encode(username) + "&password=" + encode(password);
        return post(session, "/api/v1/auth/login", csrf(session), body);
    }

    private Csrf csrf(BrowserSession session) throws Exception {
        HttpResponse<String> response = get(session, "/api/v1/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = objectMapper.readTree(response.body());
        return new Csrf(json.get("headerName").asString(), json.get("token").asString());
    }

    private HttpResponse<String> get(BrowserSession session, String path) throws Exception {
        return session.client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            BrowserSession session,
            String path,
            Csrf csrf,
            String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrf != null) {
            request.header(csrf.headerName(), csrf.token());
        }
        return session.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(BrowserSession session, String path, Csrf csrf, String body) throws Exception {
        return session.client.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(csrf.headerName(), csrf.token())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class BrowserSession {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    private record Csrf(String headerName, String token) {
    }
}
