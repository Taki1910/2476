package com.example.qlchgiay.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KhachHangValidationTest {
    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void customerNameIsRequired() {
        KhachHang customer = new KhachHang();

        Set<String> invalidFields = invalidFields(customer);

        assertEquals(Set.of("tenKH"), invalidFields);
    }

    @Test
    void customerContactInformationMustBeValid() {
        KhachHang customer = validCustomer();
        customer.setSoDienThoai("12345");
        customer.setDiaChi("x".repeat(201));

        Set<String> invalidFields = invalidFields(customer);

        assertTrue(invalidFields.contains("soDienThoai"));
        assertTrue(invalidFields.contains("diaChi"));
    }

    @Test
    void customerPhoneMustUseAVietnameseMobilePrefix() {
        KhachHang customer = validCustomer();
        for (String phone : Set.of(
                "0412345678", "0112345678", "090123456", "09012345678",
                "09012 3456", "09012a3456", "09012-3456"
        )) {
            customer.setSoDienThoai(phone);
            var violation = validator.validate(customer).stream()
                    .filter(item -> item.getPropertyPath().toString().equals("soDienThoai"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    "Số điện thoại không đúng định dạng số di động Việt Nam.",
                    violation.getMessage(),
                    phone
            );
        }

        for (String phone : Set.of(
                "0321234567", "0521234567", "0701234567",
                "0811234567", "0901234567"
        )) {
            customer.setSoDienThoai(phone);
            assertTrue(validator.validate(customer).isEmpty(), phone);
        }
    }

    @Test
    void customerPhoneAndAddressMayBeEmpty() {
        KhachHang customer = validCustomer();
        customer.setSoDienThoai(null);
        customer.setDiaChi(null);

        assertTrue(validator.validate(customer).isEmpty());
    }

    private Set<String> invalidFields(KhachHang customer) {
        return validator.validate(customer).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private KhachHang validCustomer() {
        KhachHang customer = new KhachHang();
        customer.setTenKH("Khách hàng kiểm thử");
        customer.setGioiTinh(true);
        customer.setNgaySinh(java.time.LocalDate.of(2000, 6, 15));
        customer.setSoDienThoai("0901234567");
        customer.setDiaChi("Hà Nội");
        return customer;
    }
}
