package com.shoecommerce.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class DemoDataBootstrapExternalIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void restartingOnAnotherDayPreservesDemoSalesAndTheirFinancialSnapshots() {
        seedAt("2026-08-31T00:00:00Z");
        var initial = historyEvidence();
        assertThat(initial).hasSize(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pickup_fulfillment WHERE fulfillment_type = 'DELIVERY' AND status IN ('OUT_FOR_DELIVERY', 'DELIVERED')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_shoe_fit_profile", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_shoe_fit_size_range", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT fit_tendency FROM catalog_shoe_fit_profile profiles JOIN catalog_product products ON products.id = profiles.product_id WHERE products.name = 'After Dark'", String.class)).isEqualTo("RUNS_SMALL");
        assertThat(jdbc.queryForObject("SELECT width_profile FROM catalog_shoe_fit_profile profiles JOIN catalog_product products ON products.id = profiles.product_id WHERE products.name = 'City Loafer'", String.class)).isEqualTo("WIDE");

        seedAt("2026-08-31T12:00:00Z");
        assertThat(historyEvidence()).isEqualTo(initial);
        seedAt("2026-09-01T00:00:00Z");
        assertThat(historyEvidence()).isEqualTo(initial);
    }

    private void seedAt(String instant) {
        new DemoDataBootstrap(jdbc, passwords, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)).run();
    }

    private List<Map<String, Object>> historyEvidence() {
        return jdbc.queryForList("""
                SELECT sales.public_id, sales.created_at, tender.amount, item.unit_price_amount
                FROM pos_cash_sale sales
                JOIN cash_tender tender ON tender.order_id = sales.order_id
                JOIN commerce_order_item item ON item.order_id = sales.order_id
                WHERE sales.idempotency_key LIKE 'demo-history-%'
                ORDER BY sales.public_id
                """);
    }
}
