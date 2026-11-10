package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FlywayV26UpgradeExternalIT {
    @Test
    void migratesFreshDatabaseThroughV26() throws Exception {
        try (Database database = Database.create("fresh")) {
            database.migrate();
            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='26'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name IN ('storefront_page_revision','storefront_section','storefront_collection_product')"))
                    .isEqualTo(3);
            assertThat(database.count("SELECT COUNT(*) FROM iam_permission WHERE code='STOREFRONT_MANAGE'"))
                    .isOne();
        }
    }

    @Test
    void upgradesPopulatedV25WithoutChangingExistingCommerceData() throws Exception {
        try (Database database = Database.create("upgrade")) {
            database.migrateTo("25");
            UUID account = UUID.randomUUID(), product = UUID.randomUUID(), family = UUID.randomUUID();
            database.execute("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES ('"
                    + account + "','s11-upgrade-" + account + "@example.test','fixture','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME())");
            database.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('"
                    + product + "',N'Preserved product',0,SYSUTCDATETIME())");
            database.execute("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES ('"
                    + family + "','CLAIMABLE',NULL,1,SYSUTCDATETIME())");

            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM iam_user_account WHERE public_id='" + account + "'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM catalog_product WHERE public_id='" + product + "'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM promotion_family WHERE public_id='" + family + "'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM storefront_page_revision")).isZero();
        }
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;
        private Database(String masterUrl, String url, String username, String password, String name) {
            this.masterUrl = masterUrl; this.url = url; this.username = username; this.password = password;
            this.name = name;
        }
        static Database create(String label) throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_s11_" + label + "_" + UUID.randomUUID().toString().replace("-", "");
            Database database = new Database(withDatabase(source, "master"), withDatabase(source, name), user,
                    password, name);
            try (Connection connection = DriverManager.getConnection(database.masterUrl, user, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE [" + name + "]");
            }
            return database;
        }
        void migrateTo(String target) {
            Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration")
                    .target(target).load().migrate();
        }
        void migrate() {
            Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate();
        }
        void execute(String sql) throws Exception {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) { statement.execute(sql); }
        }
        int count(String sql) throws Exception {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next(); return result.getInt(1);
            }
        }
        @Override public void close() throws Exception {
            try (Connection connection = DriverManager.getConnection(masterUrl, username, password);
                    Statement statement = connection.createStatement()) {
                statement.execute("ALTER DATABASE [" + name + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                statement.execute("DROP DATABASE [" + name + "]");
            }
        }
        private static String withDatabase(String url, String database) {
            if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*")) {
                throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
            }
            return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + database);
        }
    }
}
