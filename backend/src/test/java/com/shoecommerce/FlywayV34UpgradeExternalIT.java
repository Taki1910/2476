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
class FlywayV34UpgradeExternalIT {
    @Test
    void migratesCleanAndExistingV33DatabasesForReceiveMovements() throws Exception {
        try (Database fresh = Database.create("fresh")) {
            fresh.migrate();
            assertThat(fresh.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='34'"))
                    .isOne();
        }

        try (Database upgrade = Database.create("upgrade")) {
            upgrade.migrateTo("33");
            upgrade.migrate();
            assertThat(upgrade.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('inventory_stock_movement') AND name='reserved_at_operation'"))
                    .isOne();

            upgrade.execute(fixtureSql());
            upgrade.execute(receiptSql("receipt-ok", 0));
            assertThat(upgrade.count("SELECT COUNT(*) FROM inventory_stock_movement WHERE operation_type='RECEIVE'"))
                    .isOne();
            assertThatThrownBy(() -> upgrade.execute(receiptSql("receipt-invalid", 1)))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static String fixtureSql() {
        return "INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,created_at,updated_at) VALUES ('00000000-0000-0000-0000-000000000001',N'receipt-test',N'x','ENABLED',SYSUTCDATETIME(),SYSUTCDATETIME());"
                + "INSERT INTO org_branch(public_id,code,name,created_at) VALUES ('00000000-0000-0000-0000-000000000002','REC',N'Receipt',SYSUTCDATETIME());"
                + "INSERT INTO org_location(public_id,branch_id,code,name,created_at) SELECT '00000000-0000-0000-0000-000000000003',id,'REC',N'Receipt' ,SYSUTCDATETIME() FROM org_branch WHERE code='REC';"
                + "INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('00000000-0000-0000-0000-000000000004',N'Receipt product',0,SYSUTCDATETIME());"
                + "INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) SELECT '00000000-0000-0000-0000-000000000005',id,'REC-42','42',N'Black','DRAFT',0,SYSUTCDATETIME() FROM catalog_product WHERE name=N'Receipt product';";
    }

    private static String receiptSql(String key, int reservedDelta) {
        return "INSERT INTO inventory_stock_movement(public_id,operation_type,operation_key,variant_public_id,location_public_id,actor_account_public_id,quantity,on_hand_delta,reserved_delta,occurred_at,request_fingerprint,reason,before_on_hand,after_on_hand,reserved_at_operation) VALUES (NEWID(),'RECEIVE',N'"
                + key + "','00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001',4,4,"
                + reservedDelta + ",SYSUTCDATETIME(),N'fingerprint',N'PO-1',5,9,2)";
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
            String name = "shoe_commerce_v34_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
            Database database = new Database(withDatabase(source, "master"), withDatabase(source, name), username, password, name);
            database.executeOnMaster("CREATE DATABASE [" + name + "]");
            return database;
        }

        void migrateTo(String target) {
            Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").target(target).load().migrate();
        }

        void migrate() {
            Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate();
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
