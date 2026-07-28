package com.example.qlchgiay.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NhaCungCapValidationTest {
    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void allSupplierFieldsAreRequired() {
        NhaCungCap supplier = new NhaCungCap();

        Set<String> invalidFields = validator.validate(supplier).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("tenNCC", "soDienThoai", "email", "diaChi", "trangThai"),
                invalidFields
        );
    }

    @Test
    void supplierContactInformationMustBeValid() {
        NhaCungCap supplier = validSupplier();
        supplier.setSoDienThoai("12345");
        supplier.setEmail("email-khong-hop-le");

        Set<String> invalidFields = validator.validate(supplier).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.contains("soDienThoai"));
        assertTrue(invalidFields.contains("email"));
    }

    @Test
    void completeSupplierPassesValidation() {
        assertTrue(validator.validate(validSupplier()).isEmpty());
    }

    private NhaCungCap validSupplier() {
        NhaCungCap supplier = new NhaCungCap();
        supplier.setTenNCC("Nhà cung cấp kiểm thử");
        supplier.setSoDienThoai("0901234567");
        supplier.setEmail("lienhe@nhacungcap.vn");
        supplier.setDiaChi("123 Nguyễn Trãi, Hà Nội");
        supplier.setTrangThai("Hoạt động");
        return supplier;
    }
}
