package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FlywayV35UpgradeExternalIT {
    @Test
    void migratesFreshAndExistingV34DatabasesForManualQueryDr() throws Exception {
        try (Database fresh = Database.create("fresh")) {
            fresh.migrate();
            assertContract(fresh);
        }
        try (Database upgrade = Database.create("upgrade")) {
            upgrade.migrateTo("34");
            upgrade.migrate();
            assertContract(upgrade);
        }
    }

    private static void assertContract(Database database) throws SQLException {
        assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='35'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name='payment_reconciliation_command'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM iam_permission WHERE code='PAYMENT_RECONCILE'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM iam_role_permission rp JOIN iam_role_bundle r ON r.id=rp.role_id JOIN iam_permission p ON p.id=rp.permission_id WHERE r.code='OPERATIONS' AND p.code='PAYMENT_RECONCILE'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID('payment_reconciliation_command') AND name IN ('UQ_payment_reconciliation_actor_key','UQ_payment_reconciliation_provider_request')"))
                .isEqualTo(2);
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;
        private Database(String masterUrl, String url, String username, String password, String name) {
            this.masterUrl = masterUrl; this.url = url; this.username = username; this.password = password; this.name = name;
        }
        static Database create(String suffix) throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String username = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_v35_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
            Database database = new Database(withDatabase(source, "master"), withDatabase(source, name), username, password, name);
            database.executeOnMaster("CREATE DATABASE [" + name + "]");
            return database;
        }
        void migrateTo(String target) { Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").target(target).load().migrate(); }
        void migrate() { Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate(); }
        int count(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next(); return result.getInt(1);
            }
        }
        private void executeOnMaster(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(masterUrl, username, password);
                    Statement statement = connection.createStatement()) { statement.execute(sql); }
        }
        @Override public void close() throws Exception {
            executeOnMaster("ALTER DATABASE [" + name + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [" + name + "]");
        }
        private static String withDatabase(String url, String database) {
            if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*"))
                throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
            return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + database);
        }
    }
}
