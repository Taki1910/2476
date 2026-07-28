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
        customer.setNamSinh(2000);
        customer.setSoDienThoai("0901234567");
        customer.setDiaChi("Hà Nội");
        return customer;
    }
}
