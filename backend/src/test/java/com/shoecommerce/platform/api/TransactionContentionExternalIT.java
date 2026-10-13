package com.shoecommerce.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class TransactionContentionExternalIT {
    private static final String ENDPOINT = "/aud01/transaction";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApiExceptionHandler exceptionHandler;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private ContentionProbe probe;

    @BeforeAll
    void createProbe() {
        jdbc.execute("IF OBJECT_ID('aud01_effect','U') IS NOT NULL DROP TABLE aud01_effect");
        jdbc.execute("IF OBJECT_ID('aud01_lock_probe','U') IS NOT NULL DROP TABLE aud01_lock_probe");
        jdbc.execute("CREATE TABLE aud01_lock_probe (id int NOT NULL PRIMARY KEY, value int NOT NULL)");
        jdbc.update("INSERT INTO aud01_lock_probe(id,value) VALUES (1,0),(2,0)");
        jdbc.execute("CREATE TABLE aud01_effect (operation_key nvarchar(128) NOT NULL PRIMARY KEY, payload nvarchar(128) NOT NULL UNIQUE)");
        probe = new ContentionProbe(jdbc, transactionManager);
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController(probe))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @BeforeEach
    void resetProbe() {
        jdbc.update("DELETE FROM aud01_effect");
        jdbc.update("UPDATE aud01_lock_probe SET value=0");
        probe.reset();
    }

    @AfterAll
    void dropProbe() {
        jdbc.execute("IF OBJECT_ID('aud01_effect','U') IS NOT NULL DROP TABLE aud01_effect");
        jdbc.execute("IF OBJECT_ID('aud01_lock_probe','U') IS NOT NULL DROP TABLE aud01_lock_probe");
    }

    @Test
    void deadlockVictimRollsBackAndSameKeyRetryDoesNotDuplicate() throws Exception {
        probe.armDeadlock();
        MvcResult victim;
        MvcResult winner;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var victimCall = executor.submit(() -> execute("deadlock-victim", "payload-victim", 1, 2, true, -1));
            var winnerCall = executor.submit(() -> execute("deadlock-winner", "payload-winner", 2, 1, false, -1));
            victim = victimCall.get(20, TimeUnit.SECONDS);
            winner = winnerCall.get(20, TimeUnit.SECONDS);
        }

        assertRetryableContention(victim, 503);
        assertThat(winner.getResponse().getStatus()).isEqualTo(200);
        assertThat(probe.lastSqlError()).isEqualTo(1205);
        assertThat(effectCount("deadlock-victim")).isZero();
        assertThat(effectCount("deadlock-winner")).isOne();

        MvcResult retry = execute("deadlock-victim", "payload-victim", 1, 2, true, -1);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(retry).get("created").asBoolean()).isTrue();
        assertThat(effectCount("deadlock-victim")).isOne();

        MvcResult replay = execute("deadlock-victim", "payload-victim", 1, 2, true, -1);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(replay).get("created").asBoolean()).isFalse();
        assertThat(effectCount("deadlock-victim")).isOne();

        MvcResult mismatch = execute("deadlock-victim", "changed-payload", 1, 2, true, -1);
        assertThat(mismatch.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(mismatch).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(effectCount("deadlock-victim")).isOne();
    }

    @Test
    void lockTimeoutRollsBackAndSameKeyRetrySucceedsOnce() throws Exception {
        try (Connection holder = dataSource.getConnection();
                PreparedStatement lock = holder.prepareStatement("UPDATE aud01_lock_probe SET value=value+1 WHERE id=1")) {
            holder.setAutoCommit(false);
            lock.executeUpdate();

            MvcResult timedOut = execute("timeout-key", "timeout-payload", 2, 1, false, 1000);
            assertRetryableContention(timedOut, 503);
            assertThat(probe.lastSqlError()).isEqualTo(1222);
            assertThat(effectCount("timeout-key")).isZero();
            holder.rollback();
        }

        MvcResult retry = execute("timeout-key", "timeout-payload", 2, 1, false, 1000);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(retry).get("created").asBoolean()).isTrue();
        MvcResult replay = execute("timeout-key", "timeout-payload", 2, 1, false, 1000);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(replay).get("created").asBoolean()).isFalse();
        assertThat(effectCount("timeout-key")).isOne();
    }

    @Test
    void nonContentionDatabaseFailureIsNotRetryableServiceUnavailable() throws Exception {
        assertThat(execute("unique-a", "same-payload", 1, 1, false, -1).getResponse().getStatus()).isEqualTo(200);

        MvcResult duplicate = execute("unique-b", "same-payload", 2, 2, false, -1);

        JsonNode problem = body(duplicate);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(500);
        assertThat(probe.lastSqlError()).isIn(2601, 2627);
        assertThat(problem.get("code").asText()).isEqualTo("HTTP_500");
        assertThat(problem.has("retryable")).isFalse();
        assertThat(problem.has("reuseSameIdempotencyKey")).isFalse();
        assertThat(effectCount("unique-b")).isZero();
    }

    private MvcResult execute(String key, String payload, int firstRow, int secondRow,
            boolean lowDeadlockPriority, int lockTimeoutMillis) throws Exception {
        String content = json.writeValueAsString(new ProbeRequest(
                payload, firstRow, secondRow, lowDeadlockPriority, lockTimeoutMillis));
        return mvc.perform(post(ENDPOINT)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
                .andReturn();
    }

    private void assertRetryableContention(MvcResult result, int status) throws Exception {
        JsonNode problem = body(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
        assertThat(problem.get("code").asText()).isEqualTo("CONCURRENCY_RETRY_REQUIRED");
        assertThat(problem.get("retryable").asBoolean()).isTrue();
        assertThat(problem.get("reuseSameIdempotencyKey").asBoolean()).isTrue();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("aud01_")
                .doesNotContain("1205")
                .doesNotContain("1222");
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int effectCount(String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM aud01_effect WHERE operation_key=?", Integer.class, key);
    }

    @RestController
    static final class ProbeController {
        private final ContentionProbe probe;

        ProbeController(ContentionProbe probe) {
            this.probe = probe;
        }

        @PostMapping(ENDPOINT)
        ProbeResult execute(@RequestHeader("Idempotency-Key") String key, @RequestBody ProbeRequest request) {
            return probe.execute(key, request.payload(), request.firstRow(), request.secondRow(),
                    request.lowDeadlockPriority(), request.lockTimeoutMillis());
        }
    }

    record ProbeRequest(String payload, int firstRow, int secondRow,
            boolean lowDeadlockPriority, int lockTimeoutMillis) { }

    record ProbeResult(String key, String payload, boolean created) { }

    static final class ContentionProbe {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;
        private final AtomicInteger lastSqlError = new AtomicInteger();
        private volatile CountDownLatch firstLocks = new CountDownLatch(0);

        ContentionProbe(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
            this.jdbc = jdbc;
            this.transactions = new TransactionTemplate(transactionManager);
        }

        void armDeadlock() {
            firstLocks = new CountDownLatch(2);
        }

        void reset() {
            firstLocks = new CountDownLatch(0);
            lastSqlError.set(0);
        }

        int lastSqlError() {
            return lastSqlError.get();
        }

        ProbeResult execute(String key, String payload, int firstRow, int secondRow,
                boolean lowDeadlockPriority, int lockTimeoutMillis) {
            return transactions.execute(status -> jdbc.execute((ConnectionCallback<ProbeResult>) connection ->
                    execute(connection, key, payload, firstRow, secondRow, lowDeadlockPriority, lockTimeoutMillis)));
        }

        private ProbeResult execute(Connection connection, String key, String payload, int firstRow, int secondRow,
                boolean lowDeadlockPriority, int lockTimeoutMillis) throws SQLException {
            SQLException failure = null;
            try {
                setSession(connection, lowDeadlockPriority, lockTimeoutMillis);
                ProbeResult replay = replay(connection, key, payload);
                if (replay != null) return replay;
                updateRow(connection, firstRow);
                awaitFirstLocks();
                insertEffect(connection, key, payload);
                updateRow(connection, secondRow);
                return new ProbeResult(key, payload, true);
            } catch (SQLException exception) {
                lastSqlError.set(exception.getErrorCode());
                failure = exception;
                throw exception;
            } finally {
                try (Statement reset = connection.createStatement()) {
                    reset.execute("SET DEADLOCK_PRIORITY NORMAL; SET LOCK_TIMEOUT -1");
                } catch (SQLException resetFailure) {
                    if (failure == null) throw resetFailure;
                    failure.addSuppressed(resetFailure);
                }
            }
        }

        private static void setSession(Connection connection, boolean lowPriority, int lockTimeoutMillis)
                throws SQLException {
            if (lockTimeoutMillis != -1 && lockTimeoutMillis != 1000) {
                throw new IllegalArgumentException("Test lock timeout must be -1 or 1000");
            }
            try (Statement settings = connection.createStatement()) {
                settings.execute("SET DEADLOCK_PRIORITY " + (lowPriority ? "LOW" : "HIGH")
                        + "; SET LOCK_TIMEOUT " + lockTimeoutMillis);
            }
        }

        private static ProbeResult replay(Connection connection, String key, String payload) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM aud01_effect WHERE operation_key=?")) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return null;
                    if (!Objects.equals(result.getString(1), payload)) {
                        throw new BusinessConflictException("IDEMPOTENCY_KEY_CONFLICT",
                                "This idempotency key was already used for a different request.");
                    }
                    return new ProbeResult(key, payload, false);
                }
            }
        }

        private void awaitFirstLocks() throws SQLException {
            firstLocks.countDown();
            try {
                if (!firstLocks.await(10, TimeUnit.SECONDS)) {
                    throw new SQLException("Deadlock probe barrier timed out", "HYT00", 0);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException("Deadlock probe interrupted", "HY008", 0, exception);
            }
        }

        private static void updateRow(Connection connection, int row) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE aud01_lock_probe SET value=value+1 WHERE id=?")) {
                statement.setInt(1, row);
                if (statement.executeUpdate() != 1) throw new SQLException("Probe row missing", "02000", 0);
            }
        }

        private static void insertEffect(Connection connection, String key, String payload) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO aud01_effect(operation_key,payload) VALUES (?,?)")) {
                statement.setString(1, key);
                statement.setString(2, payload);
                statement.executeUpdate();
            }
        }
    }
}
