package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FlywayV32UpgradeExternalIT {
    @Test
    void migratesACleanInstallationThroughV32() throws Exception {
        try (Database database = Database.create("fresh")) {
            database.migrate();
            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='32'"))
                    .isOne();
        }
    }

    @Test
    void upgradesV30AndEnforcesTheApprovedLifecycleAndActiveOptionIdentity() throws Exception {
        try (Database database = Database.create("clean")) {
            database.migrateTo("30");
            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('31','32')"))
                    .isEqualTo(2);
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('catalog_product_variant') AND name IN ('normalized_color','normalized_size')"))
                    .isEqualTo(2);
            assertThat(database.count("SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('catalog_product_variant') AND name='UX_catalog_variant_active_option' AND is_unique=1"))
                    .isOne();

            database.seedProductAndVariant("P2B-A", "P2B-A-41", "41", "Black", "RETIRED");
            database.seedVariant("P2B-A", "P2B-A-NEW", "041.00", "black", "DRAFT");
            assertThatThrownBy(() -> database.seedVariant("P2B-A", "P2B-A-DUP", "41.0", " BLACK ", "PUBLISHED"))
                    .isInstanceOf(SQLException.class);
            database.seedVariant("P2B-A", "P2B-A-DEN", "42", "Den", "DRAFT");
            database.seedVariant("P2B-A", "P2B-A-DDEN", "42", "Đen", "DRAFT");
        }
    }

    @Test
    void concurrentActiveOptionCreationHasOneDatabaseWinner() throws Exception {
        try (Database database = Database.create("concurrent")) {
            database.migrate();
            database.seedProductAndVariant("P2B-C", "P2B-C-OLD", "40", "Grey", "RETIRED");
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> database.tryConcurrentVariant("P2B-C", "P2B-C-A", "42", "Blue", ready, start));
                var second = executor.submit(() -> database.tryConcurrentVariant("P2B-C", "P2B-C-B", "042.00", " blue ", ready, start));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            }
            assertThat(database.count("SELECT COUNT(*) FROM catalog_product_variant variants JOIN catalog_product products ON products.id=variants.product_id WHERE products.name=N'P2B-C' AND variants.lifecycle_status<>'RETIRED'"))
                    .isOne();
        }
    }

    @Test
    void duplicateActiveOptionsRefuseV32BeforeAnyV32SchemaChange() throws Exception {
        try (Database database = Database.create("duplicate")) {
            database.migrateTo("31");
            database.seedProductAndVariant("P2B-D", "P2B-D-1", "41", "Black", "DRAFT");
            database.seedVariant("P2B-D", "P2B-D-2", "041.00", "black", "PUBLISHED");

            assertThatThrownBy(database::migrate).isInstanceOf(FlywayException.class)
                    .hasMessageContaining("V32 blocked by active duplicate");
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('catalog_product_variant') AND name IN ('normalized_color','normalized_size')"))
                    .isZero();
        }
    }

    @Test
    void invalidEuSizeRefusesV32BeforeAnyV32SchemaChange() throws Exception {
        try (Database database = Database.create("invalid")) {
            database.migrateTo("31");
            database.seedProductAndVariant("P2B-I", "P2B-I-1", "EU 41", "Đen", "DRAFT");

            assertThatThrownBy(database::migrate).isInstanceOf(FlywayException.class)
                    .hasMessageContaining("V32 blocked by invalid EU sizes");
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('catalog_product_variant') AND name IN ('normalized_color','normalized_size')"))
                    .isZero();
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
            String name = "shoe_commerce_v32_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
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

        void seedProductAndVariant(String productKey, String sku, String size, String color, String status) throws SQLException {
            execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (NEWID(),N'" + productKey + "',0,SYSUTCDATETIME())");
            seedVariant(productKey, sku, size, color, status);
        }

        void seedVariant(String productKey, String sku, String size, String color, String status) throws SQLException {
            execute("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) "
                    + "SELECT NEWID(),id,'" + sku + "','" + size + "',N'" + color + "','" + status + "',0,SYSUTCDATETIME() "
                    + "FROM catalog_product WHERE name=N'" + productKey + "'");
        }

        void execute(String sql) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) { statement.execute(sql); }
        }

        boolean tryConcurrentVariant(String productKey, String sku, String size, String color,
                CountDownLatch ready, CountDownLatch start) throws Exception {
            try (Connection connection = DriverManager.getConnection(url, username, password);
                    Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent insert did not start");
                try {
                    statement.execute("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) "
                            + "SELECT NEWID(),id,'" + sku + "','" + size + "',N'" + color + "','DRAFT',0,SYSUTCDATETIME() FROM catalog_product WHERE name=N'" + productKey + "'");
                    connection.commit();
                    return true;
                } catch (SQLException conflict) {
                    connection.rollback();
                    return false;
                }
            }
        }

        int count(String sql) throws SQLException {
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
            if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*"))
                throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
            return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + database);
        }
    }
}
