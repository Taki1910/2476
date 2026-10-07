package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
class FlywayV27UpgradeExternalIT {
    @Test
    void upgradesLegacyAttemptsWithoutRewritingMoneyAndEnforcesTypedReferences() throws Exception {
        String source=System.getenv("SPRING_DATASOURCE_URL"), user=System.getenv("SPRING_DATASOURCE_USERNAME"), password=System.getenv("SPRING_DATASOURCE_PASSWORD");
        String name="shoe_remediation_v27_"+UUID.randomUUID().toString().replace("-", "");
        if(source==null || !source.matches("(?is).*;databaseName=[^;]+.*")) throw new IllegalStateException("Isolated databaseName required");
        String masterUrl=source.replaceFirst("(?i)(;databaseName=)[^;]+","$1master");
        String url=source.replaceFirst("(?i)(;databaseName=)[^;]+","$1"+name);
        try(Connection master=DriverManager.getConnection(masterUrl,user,password);Statement administration=master.createStatement()) {
            administration.execute("CREATE DATABASE ["+name+"]");
            try {
                Flyway.configure().dataSource(url,user,password).target("26").load().migrate();
                try(Connection connection=DriverManager.getConnection(url,user,password);Statement sql=connection.createStatement()) {
                    try(var resource=getClass().getResourceAsStream("/void-v26-fixture.sql")) {
                        sql.execute(new String(java.util.Objects.requireNonNull(resource).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                    }
                    String before=json(sql);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET component_type='SHIPPING' WHERE id=(SELECT MIN(id) FROM payment_void_allocation)"))
                            .isInstanceOf(SQLException.class);
                    Flyway.configure().dataSource(url,user,password).load().migrate();
                    assertThat(json(sql)).isEqualTo(before);
                    assertThat(scalar(sql,"SELECT COUNT(*) FROM payment_void_attempt WHERE calculation_version='LEGACY_V1'")).isEqualTo(5);
                    assertThat(scalar(sql,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='27' AND success=1")).isOne();
                    // Shipping has its own Order FK. Multiple distinct components in one attempt remain legal.
                    sql.execute("""
                        INSERT INTO payment_void_allocation(public_id,void_operation_id,void_attempt_id,component_type,shipping_order_public_id,amount,status,created_at)
                        SELECT NEWID(),a.void_operation_id,a.id,'SHIPPING',o.order_public_id,1,'ACTIVE',SYSUTCDATETIME()
                        FROM payment_void_attempt a JOIN payment_void_operation o ON o.id=a.void_operation_id WHERE a.status='CREATED'
                        """);
                    String shipping=" WHERE component_type='SHIPPING'";
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET shipping_order_public_id=NEWID()"+shipping)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET component_public_id=(SELECT TOP 1 public_id FROM commerce_order_item)"+shipping)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET shipping_order_public_id=NULL"+shipping)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET amount=0"+shipping)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_allocation SET amount=-1"+shipping)).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("UPDATE payment_void_attempt SET calculation_version='OTHER'" )).isInstanceOf(SQLException.class);
                    assertThatThrownBy(()->sql.execute("""
                        INSERT INTO payment_void_allocation(public_id,void_operation_id,void_attempt_id,component_type,shipping_order_public_id,amount,status,created_at)
                        SELECT NEWID(),void_operation_id,void_attempt_id,component_type,shipping_order_public_id,1,'ACTIVE',SYSUTCDATETIME()
                        FROM payment_void_allocation WHERE component_type='SHIPPING'
                        """)).isInstanceOf(SQLException.class);
                }
            } finally {
                administration.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                administration.execute("DROP DATABASE ["+name+"]");
            }
        }
    }
    private static int scalar(Statement sql,String query)throws SQLException {try(ResultSet rows=sql.executeQuery(query)){rows.next();return rows.getInt(1);}}
    private static String json(Statement sql)throws SQLException {
        try(ResultSet rows=sql.executeQuery("""
                SELECT a.public_id,a.generation,a.amount,a.status,a.created_at,a.resolved_at,
                       v.public_id AS allocation_id,v.component_type,v.component_public_id,v.amount AS allocated,
                       v.status AS allocation_status,v.created_at AS allocation_created,v.resolved_at AS allocation_resolved
                FROM payment_void_attempt a JOIN payment_void_allocation v ON v.void_attempt_id=a.id
                ORDER BY a.id FOR JSON PATH,INCLUDE_NULL_VALUES
                """)){StringBuilder result=new StringBuilder();while(rows.next())result.append(rows.getString(1));return result.toString();}
    }
}
