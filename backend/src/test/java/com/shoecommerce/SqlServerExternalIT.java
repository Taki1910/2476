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
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name IN ('catalog_shoe_fit_profile', 'catalog_shoe_fit_size_range')", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM commerce_order_item items JOIN commerce_order orders ON orders.id = items.order_id WHERE orders.reservation_public_id IS NOT NULL AND (items.reservation_public_id IS NULL OR items.reservation_public_id <> orders.reservation_public_id)", Integer.class)).isZero();
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
