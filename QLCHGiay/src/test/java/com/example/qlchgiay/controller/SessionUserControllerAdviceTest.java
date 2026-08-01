package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionUserControllerAdviceTest {

    @Test
    void managerSpellingsShareAdminPermissions() {
        TaiKhoan managerWithY = account("Quản lý");
        TaiKhoan managerWithI = account("Quản lí");
        TaiKhoan storeManager = account("Quản lý cửa hàng");

        assertTrue(SessionUserControllerAdvice.isAdmin(managerWithY));
        assertTrue(SessionUserControllerAdvice.isAdmin(managerWithI));
        assertTrue(SessionUserControllerAdvice.isAdmin(storeManager));
        assertFalse(SessionUserControllerAdvice.isEmployee(managerWithY));
        assertFalse(SessionUserControllerAdvice.isEmployee(managerWithI));
        assertFalse(SessionUserControllerAdvice.isEmployee(storeManager));
    }

    @Test
    void everyNonAdminRoleUsesEmployeePermissions() {
        TaiKhoan employee = account("Nhân viên bán hàng");
        TaiKhoan inventoryEmployee = account("Nhân viên quản lý kho");
        TaiKhoan explicitlyNotAdmin = account("Không phải admin");
        TaiKhoan unspecifiedRole = account(null);

        assertTrue(SessionUserControllerAdvice.isEmployee(employee));
        assertTrue(SessionUserControllerAdvice.isEmployee(inventoryEmployee));
        assertTrue(SessionUserControllerAdvice.isEmployee(explicitlyNotAdmin));
        assertTrue(SessionUserControllerAdvice.isEmployee(unspecifiedRole));
        assertFalse(SessionUserControllerAdvice.isAdmin(employee));
        assertFalse(SessionUserControllerAdvice.isAdmin(inventoryEmployee));
        assertFalse(SessionUserControllerAdvice.isAdmin(explicitlyNotAdmin));
        assertFalse(SessionUserControllerAdvice.isAdmin(unspecifiedRole));
    }

    private TaiKhoan account(String role) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        return account;
    }
}
