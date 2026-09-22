package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class SqlServerExternalIT {

    @Test
    void connectsMigratesAndUsesUtcClock(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired Clock clock) {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15') AND success = 1",
                Integer.class);

        assertThat(jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class)).isNotBlank();
        assertThat(migrationCount).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version IN ('16','17','18')", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '19'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '20'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '21'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '22'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '23'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success = 1 AND version = '24'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_permission WHERE code = 'PROMOTION_MANAGE'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name IN ('promotion','promotion_product_scope','pricing_cart_quote_adjustment','commerce_order_adjustment','commerce_order_item_adjustment','promotion_redemption')", Integer.class)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name IN ('promotion_family','voucher_claim')", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_permission WHERE code = 'SHIPPING_RATE_MANAGE'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('pickup_fulfillment') AND name='delivery_fee_amount'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam_permission WHERE code = 'STAFF_MANAGE_SCOPED'", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name IN ('catalog_shoe_fit_profile', 'catalog_shoe_fit_size_range')", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_order_item items JOIN commerce_order orders ON orders.id = items.order_id WHERE orders.reservation_public_id IS NOT NULL AND (items.reservation_public_id IS NULL OR items.reservation_public_id <> orders.reservation_public_id)", Integer.class)).isZero();
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
