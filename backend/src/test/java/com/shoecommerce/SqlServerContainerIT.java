package com.shoecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SqlServerContainerIT {

    @Container
    static final MSSQLServerContainer SQL_SERVER = new MSSQLServerContainer(
            "mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
            .acceptLicense();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
    }

    @Test
    void connectsMigratesAndUsesUtcClock(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired Clock clock) {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15') AND success = 1",
                Integer.class);

        assertThat(jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class)).isNotBlank();
        assertThat(migrationCount).isEqualTo(15);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
