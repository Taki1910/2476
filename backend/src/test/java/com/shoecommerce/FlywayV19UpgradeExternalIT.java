package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FlywayV19UpgradeExternalIT {

    @Test
    void upgradesARealPopulatedV19DatabaseToV20() throws Exception {
        String sourceUrl = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        String databaseName = "shoe_commerce_phase17_v19_" + UUID.randomUUID().toString().replace("-", "");
        String masterUrl = withDatabase(sourceUrl, "master");
        String upgradeUrl = withDatabase(sourceUrl, databaseName);
        boolean created = false;

        try (Connection master = DriverManager.getConnection(masterUrl, username, password)) {
            try (Statement statement = master.createStatement()) {
                statement.execute("CREATE DATABASE [" + databaseName + "]");
            }
            created = true;

            Flyway.configure().dataSource(upgradeUrl, username, password).locations("classpath:db/migration")
                    .target("19").load().migrate();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version IS NOT NULL"))
                    .isEqualTo(19);
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM sys.tables WHERE name = 'catalog_shoe_fit_profile'"))
                    .isZero();

            UUID product = UUID.randomUUID();
            UUID variant = UUID.randomUUID();
            try (Connection upgrade = DriverManager.getConnection(upgradeUrl, username, password)) {
                try (PreparedStatement insertProduct = upgrade.prepareStatement(
                        "INSERT INTO catalog_product(public_id, name, entity_version, created_at) VALUES (?, 'V19 upgrade product', 0, SYSUTCDATETIME())")) {
                    insertProduct.setObject(1, product);
                    insertProduct.executeUpdate();
                }
                try (PreparedStatement insertVariant = upgrade.prepareStatement("""
                        INSERT INTO catalog_product_variant(public_id, product_id, sku, size, color, lifecycle_status, entity_version, created_at)
                        VALUES (?, (SELECT id FROM catalog_product WHERE public_id = ?), 'PHASE17-V19-UPGRADE', '40', 'Ink', 'PUBLISHED', 0, SYSUTCDATETIME())
                        """)) {
                    insertVariant.setObject(1, variant);
                    insertVariant.setObject(2, product);
                    insertVariant.executeUpdate();
                }
            }

            Flyway.configure().dataSource(upgradeUrl, username, password).locations("classpath:db/migration")
                    .load().migrate();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '20'"))
                    .isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM sys.tables WHERE name IN ('catalog_shoe_fit_profile', 'catalog_shoe_fit_size_range')"))
                    .isEqualTo(2);
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM catalog_product WHERE public_id = '" + product + "'"))
                    .isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM catalog_product_variant WHERE public_id = '" + variant + "'"))
                    .isOne();
        } finally {
            if (created) {
                try (Connection master = DriverManager.getConnection(masterUrl, username, password);
                        Statement statement = master.createStatement()) {
                    statement.execute("ALTER DATABASE [" + databaseName + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                    statement.execute("DROP DATABASE [" + databaseName + "]");
                }
            }
        }
    }

    private static String withDatabase(String sourceUrl, String databaseName) {
        if (sourceUrl == null || !sourceUrl.matches("(?is).*;databaseName=[^;]+.*")) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL must contain a SQL Server databaseName parameter.");
        }
        return sourceUrl.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + databaseName);
    }

    private static int count(String url, String username, String password, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
