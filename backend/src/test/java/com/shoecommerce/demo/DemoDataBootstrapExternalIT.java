package com.shoecommerce.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private static final String COURT_VI = "Court Classic thuộc danh mục Court và bộ sưu tập Court Originals. Hồ sơ kích cỡ hiện được ghi nhận là chuẩn kích cỡ, với độ rộng tiêu chuẩn.";
    private static final String COURT_EN = "Court Classic is in the Court category and the Court Originals collection. Its current sizing profile is recorded as true to size with a regular width profile.";
    private static final String METRO_VI = "Metro Runner thuộc danh mục Running và bộ sưu tập Metro Motion. Hồ sơ kích cỡ hiện được ghi nhận là chuẩn kích cỡ, với độ rộng được ghi nhận là rộng.";
    private static final String METRO_EN = "Metro Runner is in the Running category and the Metro Motion collection. Its current sizing profile is recorded as true to size with a wide width profile.";
    private static final String TRAIL_VI = "Trail Form thuộc danh mục Trail và bộ sưu tập Trail Series. Hồ sơ kích cỡ hiện được ghi nhận là chuẩn kích cỡ, với độ rộng tiêu chuẩn.";
    private static final String TRAIL_EN = "Trail Form is in the Trail category and the Trail Series collection. Its current sizing profile is recorded as true to size with a regular width profile.";

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void restartingOnAnotherDayPreservesDemoSalesAndTheirFinancialSnapshots() {
        seedAt("2026-08-31T00:00:00Z");
        var initial = historyEvidence();
        assertThat(initial).hasSize(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pickup_fulfillment WHERE fulfillment_type = 'DELIVERY' AND status IN ('OUT_FOR_DELIVERY', 'DELIVERED')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_shoe_fit_profile profiles
                JOIN catalog_product products ON products.id=profiles.product_id
                WHERE products.name IN ('Court Classic','Metro Runner','After Dark','City Loafer','Trail Form')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM catalog_shoe_fit_size_range ranges
                JOIN catalog_shoe_fit_profile profiles ON profiles.id=ranges.profile_id
                JOIN catalog_product products ON products.id=profiles.product_id
                WHERE products.name IN ('Court Classic','Metro Runner','After Dark','City Loafer','Trail Form')
                """, Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT fit_tendency FROM catalog_shoe_fit_profile profiles JOIN catalog_product products ON products.id = profiles.product_id WHERE products.name = 'After Dark'", String.class)).isEqualTo("RUNS_SMALL");
        assertThat(jdbc.queryForObject("SELECT width_profile FROM catalog_shoe_fit_profile profiles JOIN catalog_product products ON products.id = profiles.product_id WHERE products.name = 'City Loafer'", String.class)).isEqualTo("WIDE");
        var media = s14Media();
        assertThat(media).containsExactly(
                Map.of("name", "Basket Retro", "hero_image", "/products/basket-retro.png", "primary_image", "/products/basket-retro.png"),
                Map.of("name", "City Loafer", "hero_image", "/products/city-loafer.png", "primary_image", "/products/city-loafer.png"),
                Map.of("name", "Trail Edge", "hero_image", "/products/trail-edge.png", "primary_image", "/products/trail-edge.png"),
                Map.of("name", "Urban Hiker", "hero_image", "/products/urban-hiker.png", "primary_image", "/products/urban-hiker.png"));

        seedAt("2026-08-31T12:00:00Z");
        assertThat(historyEvidence()).isEqualTo(initial);
        seedAt("2026-09-01T00:00:00Z");
        assertThat(historyEvidence()).isEqualTo(initial);
        assertThat(s14Media()).isEqualTo(media);
    }

    @Test
    void seedsExactlyThreeApprovedPresentationsOnceAndPreservesOperatorContent() {
        seedAt("2026-09-20T00:00:00Z");
        List<PresentationRow> first = pilotPresentations();
        assertThat(first).hasSize(3);
        assertThat(first).containsExactly(
                new PresentationRow(first.get(0).id(), "Court Classic", "PUBLISHED", COURT_VI, COURT_EN),
                new PresentationRow(first.get(1).id(), "Metro Runner", "PUBLISHED", METRO_VI, METRO_EN),
                new PresentationRow(first.get(2).id(), "Trail Form", "PUBLISHED", TRAIL_VI, TRAIL_EN));
        assertThat(presentationCountForControls()).isZero();

        seedAt("2026-09-21T00:00:00Z");
        assertThat(pilotPresentations()).isEqualTo(first);
        jdbc.update("""
                UPDATE product_presentation_revision SET summary_en=N'Operator-owned summary'
                WHERE product_id=(SELECT id FROM catalog_product WHERE name='Court Classic')
                """);

        seedAt("2026-09-22T00:00:00Z");
        assertThat(pilotPresentations()).filteredOn(row -> row.name().equals("Court Classic"))
                .singleElement().extracting(PresentationRow::summaryEn).isEqualTo("Operator-owned summary");
        assertThat(presentationCountForControls()).isZero();
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

    private List<Map<String, Object>> s14Media() {
        return jdbc.queryForList("""
                SELECT name, hero_image, primary_image
                FROM catalog_product
                WHERE name IN ('Basket Retro','City Loafer','Trail Edge','Urban Hiker')
                ORDER BY name
                """);
    }

    private List<PresentationRow> pilotPresentations() {
        return jdbc.query("""
                SELECT revisions.public_id, products.name, revisions.status,
                       revisions.summary_vi, revisions.summary_en
                FROM product_presentation_revision revisions
                JOIN catalog_product products ON products.id=revisions.product_id
                WHERE products.name IN ('Court Classic','Metro Runner','Trail Form')
                ORDER BY products.name
                """, (row, index) -> new PresentationRow(row.getObject("public_id", UUID.class),
                        row.getString("name"), row.getString("status"), row.getString("summary_vi"),
                        row.getString("summary_en")));
    }

    private int presentationCountForControls() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_presentation_revision revisions
                JOIN catalog_product products ON products.id=revisions.product_id
                WHERE products.name IN ('Court High','Pace Knit','Trail Edge')
                """, Integer.class);
    }

    private record PresentationRow(UUID id, String name, String status, String summaryVi, String summaryEn) { }
}
