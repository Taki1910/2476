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
class FlywayV23UpgradeExternalIT {

    @Test
    void upgradesPopulatedV22DatabaseAndPreservesExistingFinancialData() throws Exception {
        String sourceUrl = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        String databaseName = "shoe_commerce_c2_v22_" + UUID.randomUUID().toString().replace("-", "");
        String masterUrl = withDatabase(sourceUrl, "master");
        String upgradeUrl = withDatabase(sourceUrl, databaseName);
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID price = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID fulfillment = UUID.randomUUID();
        boolean created = false;

        try (Connection master = DriverManager.getConnection(masterUrl, username, password);
                Statement statement = master.createStatement()) {
            statement.execute("CREATE DATABASE [" + databaseName + "]");
            created = true;
            Flyway.configure().dataSource(upgradeUrl, username, password).locations("classpath:db/migration")
                    .target("22").load().migrate();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL")).isEqualTo(22);
            try (Connection upgrade = DriverManager.getConnection(upgradeUrl, username, password);
                    Statement insert = upgrade.createStatement()) {
                insert.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('"
                        + product + "','C1 upgrade evidence',0,SYSUTCDATETIME())");
                insert.execute("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) SELECT '"+variant+"',id,'C1-UPGRADE-SKU','40','Black','PUBLISHED',0,SYSUTCDATETIME() FROM catalog_product WHERE public_id='"+product+"'");
                insert.execute("INSERT INTO pricing_variant_price(public_id,variant_id,amount,entity_version,valid_from,updated_at) SELECT '"+price+"',id,450000,0,SYSUTCDATETIME(),SYSUTCDATETIME() FROM catalog_product_variant WHERE public_id='"+variant+"'");
                insert.execute("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES ('"+account+"','c1-upgrade@example.com','fixture-only','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME())");
                insert.execute("INSERT INTO org_branch(public_id,code,name,enabled,created_at) VALUES ('"+branch+"','C1-UPGRADE-BRANCH','Upgrade branch',1,SYSUTCDATETIME())");
                insert.execute("INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at) SELECT '"+location+"',id,'C1-UPGRADE-LOCATION','Upgrade location',1,SYSUTCDATETIME() FROM org_branch WHERE public_id='"+branch+"'");
                insert.execute("INSERT INTO inventory_reservation(public_id,owner_account_public_id,variant_id,location_id,quantity,status,entity_version,created_at,adopted_at,expires_at) SELECT '"+reservation+"','"+account+"',v.id,l.id,2,'ADOPTED',0,DATEADD(minute,-5,SYSUTCDATETIME()),DATEADD(minute,-4,SYSUTCDATETIME()),DATEADD(hour,1,SYSUTCDATETIME()) FROM catalog_product_variant v CROSS JOIN org_location l WHERE v.public_id='"+variant+"' AND l.public_id='"+location+"'");
                insert.execute("INSERT INTO commerce_order(public_id,owner_account_public_id,responsible_branch_public_id,reservation_public_id,currency,status,entity_version,created_at,channel,merchandise_amount,shipping_fee_amount,total_amount) VALUES ('"+order+"','"+account+"','"+branch+"','"+reservation+"','VND','PENDING_PAYMENT',0,SYSUTCDATETIME(),'ONLINE',900000,0,900000)");
                insert.execute("INSERT INTO commerce_order_item(public_id,order_id,variant_public_id,location_public_id,quantity,unit_price_amount,sku_snapshot,size_snapshot,reservation_public_id,price_version_public_id,color_snapshot) SELECT NEWID(),o.id,'"+variant+"','"+location+"',2,450000,'C1-UPGRADE-SKU','40','"+reservation+"','"+price+"','Black' FROM commerce_order o WHERE o.public_id='"+order+"'");
                insert.execute("INSERT INTO pickup_fulfillment(public_id,order_id,branch_id,location_id,status,entity_version,created_at,channel,fulfillment_type) SELECT '"+fulfillment+"',o.id,b.id,l.id,'PENDING',0,SYSUTCDATETIME(),'ONLINE','PICKUP' FROM commerce_order o CROSS JOIN org_branch b CROSS JOIN org_location l WHERE o.public_id='"+order+"' AND b.public_id='"+branch+"' AND l.public_id='"+location+"'");
            }

            Flyway.configure().dataSource(upgradeUrl, username, password).locations("classpath:db/migration").load().migrate();

            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version='23' AND success=1")).isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM catalog_product WHERE public_id='" + product + "'")).isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM commerce_order WHERE public_id='"+order+"' AND merchandise_amount=900000 AND shipping_fee_amount=0 AND total_amount=900000 AND merchandise_discount_amount=0 AND shipping_discount_amount=0 AND calculation_policy='VND_V1'")).isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM commerce_order_adjustment")).isZero();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM commerce_order_item_adjustment")).isZero();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM promotion_redemption")).isZero();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM pickup_fulfillment WHERE public_id='"+fulfillment+"' AND status='PENDING' AND fulfillment_type='PICKUP'")).isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM sys.tables WHERE name IN ('geo_province','geo_district','shipping_rate_rule')")).isEqualTo(3);
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM iam_permission WHERE code='SHIPPING_RATE_MANAGE'")).isOne();
            assertThat(count(upgradeUrl, username, password,
                    "SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('pickup_fulfillment') AND name='delivery_fee_amount'")).isZero();
        } finally {
            if (created) try (Connection master = DriverManager.getConnection(masterUrl, username, password);
                    Statement statement = master.createStatement()) {
                statement.execute("ALTER DATABASE [" + databaseName + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                statement.execute("DROP DATABASE [" + databaseName + "]");
            }
        }
    }

    private static String withDatabase(String url, String databaseName) {
        if (url == null || !url.matches("(?is).*;databaseName=[^;]+.*"))
            throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");
        return url.replaceFirst("(?i)(;databaseName=)[^;]+", "$1" + databaseName);
    }

    private static int count(String url, String username, String password, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}

