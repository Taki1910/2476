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

        assertTrue(SessionUserControllerAdvice.isAdmin(managerWithY));
        assertTrue(SessionUserControllerAdvice.isAdmin(managerWithI));
        assertFalse(SessionUserControllerAdvice.isEmployee(managerWithY));
        assertFalse(SessionUserControllerAdvice.isEmployee(managerWithI));
    }

    @Test
    void everyNonAdminRoleUsesEmployeePermissions() {
        TaiKhoan employee = account("Nhân viên bán hàng");
        TaiKhoan unspecifiedRole = account(null);

        assertTrue(SessionUserControllerAdvice.isEmployee(employee));
        assertTrue(SessionUserControllerAdvice.isEmployee(unspecifiedRole));
        assertFalse(SessionUserControllerAdvice.isAdmin(employee));
        assertFalse(SessionUserControllerAdvice.isAdmin(unspecifiedRole));
    }

    private TaiKhoan account(String role) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        return account;
    }
}
