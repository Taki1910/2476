package com.example.qlchgiay.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoleAwareUiTemplateTest {

    @Test
    void employeeReachableSidebarsOnlyRenderReportsForAdmins() throws IOException {
        for (String template : List.of(
                "sanpham.html",
                "khachhang.html",
                "hoadon.html",
                "chatbot.html",
                "caidat.html"
        )) {
            String html = readTemplate(template);
            assertTrue(
                    html.contains("<a th:if=\"${isAdmin}\" th:href=\"@{/baocao}\">"),
                    template + " must hide the Reports menu from employees"
            );
        }

        String dashboard = readTemplate("dashboard.html");
        assertTrue(dashboard.contains(
                "<li class=\"menu-item\" th:if=\"${isAdmin}\">\n"
                        + "            <a th:href=\"@{/baocao}\" class=\"menu-link\">"
        ));
    }

    @Test
    void dashboardStoreWideControlsAndWidgetsAreAdminOnly() throws IOException {
        String dashboard = readTemplate("dashboard.html");

        assertTrue(dashboard.contains(
                "<a th:if=\"${isAdmin}\" th:href=\"@{/sanpham}\" class=\"action-card\">"
        ));
        assertTrue(dashboard.contains(
                "<a th:if=\"${isAdmin}\" th:href=\"@{/baocao}\" class=\"action-card\">"
        ));
        assertTrue(dashboard.contains("<section class=\"kpi-grid\" th:if=\"${isAdmin}\">"));
        assertTrue(dashboard.contains("<section class=\"charts-grid\" th:if=\"${isAdmin}\">"));
        assertTrue(dashboard.contains("<section class=\"bottom-grid\" th:if=\"${isAdmin}\">"));
    }

    @Test
    void productFiltersMatchOneVariantAndExposeCompleteSortResetState() throws IOException {
        String products = readTemplate("sanpham.html");

        assertTrue(products.contains("class=\"variant-filter-data\""));
        assertTrue(products.contains("variants.some(variant =>"));
        assertTrue(products.contains("currentPriceSort === null ? \"asc\" : currentPriceSort === \"asc\" ? \"desc\" : null"));
        assertTrue(products.contains("id=\"clearAllFilters\""));
        assertTrue(products.contains("Bỏ sắp xếp theo giá"));
    }

    @Test
    void supplierDashboardOnlyShowsMetricsBackedBySupplierData() throws IOException {
        String suppliers = readTemplate("nhacungcap.html");

        assertFalse(suppliers.contains("Nhập hàng tháng này"));
        assertTrue(suppliers.contains("Tổng nhà cung cấp"));
        assertTrue(suppliers.contains("th:text=\"${activeCount}\""));
        assertTrue(suppliers.contains("th:text=\"${inactiveCount}\""));
    }

    @Test
    void promotionFormAndDisplayUseIntegerBusinessRule() throws IOException {
        String promotions = readTemplate("khuyenmai.html");

        assertTrue(promotions.contains("name=\"giaTri\" type=\"number\" min=\"1\" step=\"1\""));
        assertTrue(promotions.contains("formatDecimal(promotion.giaTri,0,'COMMA',0,'POINT') + '%'"));
    }

    private String readTemplate(String name) throws IOException {
        String resource = "/templates/" + name;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing template: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }
}
