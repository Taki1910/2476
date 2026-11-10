package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class FlywayV24UpgradeExternalIT {

    @Test
    void migratesFreshDatabaseThroughV24() throws Exception {
        try (Database database = Database.create("v24_fresh")) {
            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='24'")).isOne();
            assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name IN ('promotion_family','voucher_claim')")).isEqualTo(2);
        }
    }

    @Test
    void upgradesPopulatedV23AndPreservesPromotionAndAdjustmentEvidence() throws Exception {
        try (Database database = Database.create("v24_upgrade")) {
            database.migrateTo("23");
            UUID account=UUID.randomUUID(),family=UUID.randomUUID(),retired=UUID.randomUUID(),published=UUID.randomUUID(),quote=UUID.randomUUID();
            database.execute("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES ('"+account+"','v24-upgrade-"+account+"@example.test','fixture','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME())");
            database.execute("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,priority,valid_from,created_by_account_public_id,created_at,published_at,retired_at) VALUES ('"+family+"','"+retired+"',1,'R1','RETIRED','ORDER_AUTOMATIC','ORDER_FIXED',100,1,DATEADD(day,-2,SYSUTCDATETIME()),'"+account+"',DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(day,-1,SYSUTCDATETIME()))");
            database.execute("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES ('"+family+"','"+published+"',2,'R2','PUBLISHED','ORDER_AUTOMATIC','ORDER_FIXED',200,2,DATEADD(day,-1,SYSUTCDATETIME()),'"+account+"',DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,-1,SYSUTCDATETIME()))");
            database.execute("INSERT INTO pricing_cart_quote(public_id,owner_account_id,quoted_at,expires_at,merchandise_amount,merchandise_discount_amount,total_amount) SELECT '"+quote+"',id,SYSUTCDATETIME(),DATEADD(minute,15,SYSUTCDATETIME()),1000,200,800 FROM iam_user_account WHERE public_id='"+account+"'");
            database.execute("INSERT INTO pricing_cart_quote_adjustment(public_id,quote_public_id,promotion_family_public_id,promotion_revision_public_id,revision_number,name_snapshot,effect_type,layer,qualifying_base_amount,fixed_amount,applied_amount,priority,applied_at) VALUES (NEWID(),'"+quote+"','"+family+"','"+published+"',2,'R2','ORDER_FIXED','ORDER_AUTOMATIC',1000,200,200,2,SYSUTCDATETIME())");

            database.migrate();

            assertThat(database.count("SELECT COUNT(*) FROM promotion_family WHERE public_id='"+family+"' AND acquisition_mode='AUTOMATIC' AND normalized_code IS NULL AND is_publicly_discoverable=0")).isOne();
            assertThat(database.count("SELECT COUNT(*) FROM promotion WHERE family_public_id='"+family+"' AND ((public_id='"+retired+"' AND status='RETIRED' AND fixed_amount=100) OR (public_id='"+published+"' AND status='PUBLISHED' AND fixed_amount=200))")).isEqualTo(2);
            assertThat(database.count("SELECT COUNT(*) FROM pricing_cart_quote_adjustment WHERE quote_public_id='"+quote+"' AND applied_amount=200 AND acquisition_mode='AUTOMATIC' AND masked_code_snapshot IS NULL AND voucher_claim_public_id IS NULL")).isOne();
            assertThat(database.count("SELECT COUNT(*) FROM promotion_redemption")).isZero();
        }
    }

    @Test
    void duplicatePublishedFamilyFailsBeforeAnyV24ObjectIsCreated() throws Exception {
        try (Database database = Database.create("v24_duplicate")) {
            database.migrateTo("23");
            UUID account=UUID.randomUUID(),family=UUID.randomUUID();
            database.execute("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES ('"+account+"','v24-duplicate-"+account+"@example.test','fixture','ENABLED',1,0,SYSUTCDATETIME(),SYSUTCDATETIME())");
            for(int revision=1;revision<=2;revision++) database.execute("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES ('"+family+"',NEWID(),"+revision+",'Duplicate','PUBLISHED','ORDER_AUTOMATIC','ORDER_FIXED',100,1,DATEADD(day,-1,SYSUTCDATETIME()),'"+account+"',SYSUTCDATETIME(),SYSUTCDATETIME())");

            assertThatThrownBy(database::migrate).hasMessageContaining("V24 requires at most one PUBLISHED");
            assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name IN ('promotion_family','voucher_claim')")).isZero();
            assertThat(database.count("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('promotion') AND name IN ('customer_summary','customer_terms')")).isZero();
        }
    }

    private static final class Database implements AutoCloseable {
        private final String masterUrl, url, username, password, name;
        private Database(String masterUrl,String url,String username,String password,String name){this.masterUrl=masterUrl;this.url=url;this.username=username;this.password=password;this.name=name;}
        static Database create(String label) throws Exception {
            String source=System.getenv("SPRING_DATASOURCE_URL"),user=System.getenv("SPRING_DATASOURCE_USERNAME"),password=System.getenv("SPRING_DATASOURCE_PASSWORD");
            String name="shoe_commerce_c3_"+label+"_"+UUID.randomUUID().toString().replace("-","");
            Database database=new Database(withDatabase(source,"master"),withDatabase(source,name),user,password,name);
            try(Connection connection=DriverManager.getConnection(database.masterUrl,user,password);Statement statement=connection.createStatement()){statement.execute("CREATE DATABASE ["+name+"]");}
            return database;
        }
        void migrateTo(String target){Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").target(target).load().migrate();}
        void migrate(){Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").load().migrate();}
        void execute(String sql)throws Exception{try(Connection connection=DriverManager.getConnection(url,username,password);Statement statement=connection.createStatement()){statement.execute(sql);}}
        int count(String sql)throws Exception{try(Connection connection=DriverManager.getConnection(url,username,password);Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql)){result.next();return result.getInt(1);}}
        public void close()throws Exception{try(Connection connection=DriverManager.getConnection(masterUrl,username,password);Statement statement=connection.createStatement()){statement.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");statement.execute("DROP DATABASE ["+name+"]");}}
        private static String withDatabase(String url,String database){if(url==null||!url.matches("(?is).*;databaseName=[^;]+.*"))throw new IllegalStateException("SPRING_DATASOURCE_URL must contain databaseName");return url.replaceFirst("(?i)(;databaseName=)[^;]+","$1"+database);}
    }
}
