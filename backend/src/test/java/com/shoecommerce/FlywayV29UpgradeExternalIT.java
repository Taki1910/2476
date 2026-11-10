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
class FlywayV29UpgradeExternalIT {
    @Test
    void upgradesPopulatedV28WithNullEvidenceAndEnforcesEveryControlledCode() throws Exception {
        try (Database database = Database.create()) {
            database.migrateTo("28");
            UUID existing = UUID.randomUUID();
            database.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('"
                    + existing + "',N'Existing product',0,SYSUTCDATETIME())");

            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='29'"))
                    .isOne();
            assertThat(database.count("""
                    SELECT COUNT(*) FROM catalog_product WHERE public_id='%s'
                      AND intended_use IS NULL AND primary_surface IS NULL AND upper_construction IS NULL
                      AND cut_profile IS NULL AND primary_sole_profile IS NULL
                    """.formatted(existing))).isOne();

            long product = database.insertProduct("Allowed codes");
            for (String value : new String[] { "CASUAL_DAILY", "ROAD_RUNNING", "TRAIL_WALKING", "TRAIL_RUNNING" })
                database.update(product, "intended_use", value);
            for (String value : new String[] { "SMOOTH_HARD_SURFACE", "ROAD_TRACK", "MIXED_TRAIL" })
                database.update(product, "primary_surface", value);
            for (String value : new String[] { "SYNTHETIC_LEATHER", "CANVAS_TEXTILE", "MESH_TEXTILE",
                    "KNIT_TEXTILE", "WOVEN_TEXTILE", "MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS" })
                database.update(product, "upper_construction", value);
            for (String value : new String[] { "LOW_TOP", "HIGH_TOP" })
                database.update(product, "cut_profile", value);
            for (String value : new String[] { "CUPSOLE", "FLEX_GROOVED", "LUGGED" })
                database.update(product, "primary_sole_profile", value);

            for (String column : new String[] { "intended_use", "primary_surface", "upper_construction",
                    "cut_profile", "primary_sole_profile" }) {
                assertThatThrownBy(() -> database.update(product, column, "UNSUPPORTED"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;

        private Database(String masterUrl, String url, String username, String password, String name) {
            this.masterUrl = masterUrl;
            this.url = url;
            this.username = username;
            this.password = password;
            this.name = name;
        }

        static Database create() throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_v29_" + UUID.randomUUID().toString().replace("-", "");
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

        void execute(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        long insertProduct(String name) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (NEWID(),N'"
                        + name.replace("'", "''") + "',0,SYSUTCDATETIME())", Statement.RETURN_GENERATED_KEYS);
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        }

        void update(long product, String column, String value) throws SQLException {
            execute("UPDATE catalog_product SET " + column + "='" + value + "' WHERE id=" + product);
        }

        int count(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getInt(1);
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
