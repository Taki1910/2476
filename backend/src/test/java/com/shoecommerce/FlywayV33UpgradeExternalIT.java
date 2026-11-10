package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class FlywayV33UpgradeExternalIT {
    @Test
    void migratesACleanInstallationThroughV33() throws Exception {
        try (Database database = Database.create("fresh")) {
            database.migrate();
            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='33'"))
                    .isOne();
        }
    }

    @Test
    void upgradesV32WithNullableTrimmedBinaryUniqueBarcodesAndPublicationHistory() throws Exception {
        try (Database database = Database.create("upgrade")) {
            database.migrateTo("32");
            database.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (NEWID(),N'P4A',0,SYSUTCDATETIME())");
            database.variant("P4A-DRAFT", "DRAFT");
            database.variant("P4A-PUBLISHED", "PUBLISHED");
            database.variant("P4A-RETIRED", "RETIRED");

            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('catalog_product_variant') AND name IN ('barcode','ever_published')"))
                    .isEqualTo(2);
            assertThat(database.count("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('catalog_product_variant') AND name='UX_catalog_product_variant_barcode' AND is_unique=1 AND has_filter=1"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM catalog_product_variant WHERE barcode IS NULL"))
                    .isEqualTo(3);
            assertThat(database.count("SELECT COUNT(*) FROM catalog_product_variant WHERE lifecycle_status IN ('PUBLISHED','RETIRED') AND ever_published=1"))
                    .isEqualTo(2);

            database.execute("UPDATE catalog_product_variant SET barcode=N'000Ab-9' WHERE sku='P4A-DRAFT'");
            assertThat(database.value("SELECT barcode FROM catalog_product_variant WHERE sku='P4A-DRAFT'"))
                    .isEqualTo("000Ab-9");
            database.execute("UPDATE catalog_product_variant SET barcode=N'000ab-9' WHERE sku='P4A-PUBLISHED'");
            assertThatThrownBy(() -> database.execute("UPDATE catalog_product_variant SET barcode=N'000Ab-9' WHERE sku='P4A-RETIRED'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> database.execute("UPDATE catalog_product_variant SET barcode=N' 000X ' WHERE sku='P4A-RETIRED'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;

        private Database(String masterUrl, String url, String username, String password, String name) {
            this.masterUrl = masterUrl; this.url = url; this.username = username; this.password = password; this.name = name;
        }

        static Database create(String suffix) throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_v33_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
            Database database = new Database(withDatabase(source, "master"), withDatabase(source, name), user, password, name);
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

        void variant(String sku, String status) throws SQLException {
            String size = switch (status) { case "PUBLISHED" -> "42"; case "RETIRED" -> "43"; default -> "41"; };
            execute("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) "
                    + "SELECT NEWID(),id,'" + sku + "','" + size + "',N'Black','" + status + "',0,SYSUTCDATETIME() FROM catalog_product WHERE name=N'P4A'");
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) { statement.execute(sql); }
        }

        int count(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next(); return result.getInt(1);
            }
        }

        String value(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next(); return result.getString(1);
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
            if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*"))
                throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
            return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + database);
        }
    }
}
