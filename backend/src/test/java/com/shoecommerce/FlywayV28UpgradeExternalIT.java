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
class FlywayV28UpgradeExternalIT {
    @Test
    void migratesFreshDatabaseThroughV28WithProductPresentationConstraints() throws Exception {
        try (Database database = Database.create("fresh")) {
            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='28'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name='product_presentation_revision'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM sys.indexes WHERE name IN ('UX_product_presentation_one_draft','UX_product_presentation_one_published')"))
                    .isEqualTo(2);

            UUID creator = UUID.randomUUID();
            database.execute("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES ('"
                    + creator + "','v28-" + creator + "@example.test','fixture','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME())");
            long product = database.insertProduct("Draft product");
            long incompletePublishedProduct = database.insertProduct("Incomplete published product");
            long incompleteRetiredProduct = database.insertProduct("Incomplete retired product");

            database.execute(revision(product, creator, 1, "DRAFT", "NULL", "NULL", "NULL", "NULL"));
            database.execute(revision(product, creator, 2, "PUBLISHED", "N'Bản tóm tắt'", "N'Summary'", "SYSUTCDATETIME()", "NULL"));

            assertThatThrownBy(() -> database.execute(revision(product, creator, 3, "DRAFT", "NULL", "NULL", "NULL", "NULL")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> database.execute(revision(product, creator, 4, "PUBLISHED", "N'Khác'", "N'Other'", "SYSUTCDATETIME()", "NULL")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> database.execute(revision(product, creator, 2, "RETIRED", "N'Cũ'", "N'Old'", "SYSUTCDATETIME()", "SYSUTCDATETIME()")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> database.execute(revision(incompletePublishedProduct, creator, 1, "PUBLISHED", "NULL", "N'Summary'", "SYSUTCDATETIME()", "NULL")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> database.execute(revision(incompleteRetiredProduct, creator, 1, "RETIRED", "N'Bản tóm tắt'", "NULL", "SYSUTCDATETIME()", "SYSUTCDATETIME()")))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void upgradesPopulatedV27WithoutCreatingPresentationRows() throws Exception {
        try (Database database = Database.create("upgrade")) {
            database.migrateTo("27");
            UUID product = UUID.randomUUID();
            database.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('"
                    + product + "',N'Preserved product',0,SYSUTCDATETIME())");

            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM catalog_product WHERE public_id='" + product + "'"))
                    .isOne();
            assertThat(database.count("SELECT COUNT(*) FROM product_presentation_revision")).isZero();
        }
    }

    private static String revision(long product, UUID creator, int number, String status, String vi, String en,
            String publishedAt, String retiredAt) {
        return "INSERT INTO product_presentation_revision(public_id,product_id,revision_number,status,summary_vi,summary_en,created_by_account_public_id,created_at,published_at,retired_at) VALUES (NEWID(),"
                + product + "," + number + ",'" + status + "'," + vi + "," + en + ",'" + creator
                + "',SYSUTCDATETIME()," + publishedAt + "," + retiredAt + ")";
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

        static Database create(String label) throws Exception {
            String source = System.getenv("SPRING_DATASOURCE_URL");
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name = "shoe_commerce_v28_" + label + "_" + UUID.randomUUID().toString().replace("-", "");
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
            if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*")) {
                throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
            }
            return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + database);
        }
    }
}
