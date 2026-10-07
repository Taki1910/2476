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
class FlywayV30UpgradeExternalIT {
    @Test
    void upgradesV29WithDemoWardsAndAppendOnlyDeliverySnapshotColumns() throws Exception {
        try (Database database = Database.create()) {
            database.migrateTo("29");
            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='30'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM geo_ward WHERE dataset_version='DEMO-2026'"))
                    .isEqualTo(6);
            assertThat(database.count("""
                    SELECT COUNT(*) FROM sys.columns
                    WHERE object_id=OBJECT_ID('pickup_fulfillment')
                      AND name IN ('delivery_province_code','delivery_province_label','delivery_district_code',
                        'delivery_district_label','delivery_ward_code','delivery_ward_label',
                        'delivery_address_line','address_resolution_version')
                    """)).isEqualTo(8);
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('pricing_cart_quote') AND name='destination_ward_code'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('commerce_order') AND name='destination_ward_code'"))
                    .isOne();
            assertThatThrownBy(() -> database.execute("""
                    INSERT INTO geo_ward(code,province_code,district_code,display_label,dataset_version)
                    VALUES ('BAD-HIER','79','001',N'Invalid hierarchy','DEMO-2026')
                    """)).isInstanceOf(SQLException.class);
        }
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;

        private Database(String masterUrl, String url, String username, String password, String name) {
            this.masterUrl = masterUrl; this.url = url; this.username = username; this.password = password; this.name = name;
        }

        static Database create() throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_v30_" + UUID.randomUUID().toString().replace("-", "");
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

        void execute(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        int count(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next(); return result.getInt(1);
            }
        }

        @Override
        public void close() throws Exception {
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
