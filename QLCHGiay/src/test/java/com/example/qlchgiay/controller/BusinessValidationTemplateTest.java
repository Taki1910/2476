package com.example.qlchgiay.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessValidationTemplateTest {

    @Test
    void customerFormsUseVietnameseMobilePattern() throws IOException {
        String customerForm = readTemplate("khachhang-form.html");
        String invoiceForm = readTemplate("hoadon-form.html");

        assertFalse(customerForm.contains("novalidate"));
        assertTrue(customerForm.contains("pattern=\"(03|05|07|08|09)[0-9]{8}\""));
        assertTrue(invoiceForm.contains("pattern=\"(03|05|07|08|09)[0-9]{8}\""));
        assertTrue(customerForm.contains("Số điện thoại không đúng định dạng số di động Việt Nam."));
        assertTrue(invoiceForm.contains("Số điện thoại không đúng định dạng số di động Việt Nam."));
    }

    @Test
    void purchaseAndEmployeeFormsUseExactBirthdayLimits() throws IOException {
        String invoiceForm = readTemplate("hoadon-form.html");
        String employeeForm = readTemplate("nhanvien-form.html");

        assertTrue(invoiceForm.contains("now().minusYears(15)"));
        assertTrue(invoiceForm.contains("Khách hàng phải đủ 15 tuổi trở lên để mua hàng."));
        assertTrue(employeeForm.contains("now().minusYears(18)"));
        assertTrue(employeeForm.contains("Nhân viên phải đủ 18 tuổi trở lên."));
    }

    @Test
    void selectingProductImageClearsStalePathValidation() throws IOException {
        String productForm = readTemplate("sanpham-form.html");
        int fileChange = productForm.indexOf("imageFileInput.addEventListener('change'");
        int clearValidation = productForm.indexOf("imageUrlInput.setCustomValidity('');", fileChange);

        assertTrue(fileChange >= 0);
        assertTrue(clearValidation > fileChange);
    }

    @Test
    void productDetailVariantsUseModeAwareColors() throws IOException {
        String productDetail = readTemplate("sanpham-detail.html");

        assertTrue(productDetail.contains(":root.light-mode{"));
        assertTrue(productDetail.contains("background:var(--detail-row)"));
        assertTrue(productDetail.contains("class=\"variant-row\""));
    }

    private String readTemplate(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/templates/" + name)) {
            if (input == null) throw new IOException("Missing template: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
