package com.example.qlchgiay.config;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigTest {
    private final SecurityConfig.LegacyAwarePasswordEncoder encoder =
            new SecurityConfig.LegacyAwarePasswordEncoder();

    @Test
    void acceptsLegacyPasswordAndRequestsUpgrade() {
        assertTrue(encoder.matches("123456", "123456"));
        assertFalse(encoder.matches("wrong", "123456"));
        assertTrue(encoder.upgradeEncoding("123456"));
    }

    @Test
    void storesNewPasswordsAsBcrypt() {
        String encoded = encoder.encode("mat-khau-an-toan");

        assertTrue(encoded.startsWith("$2"));
        assertTrue(encoder.matches("mat-khau-an-toan", encoded));
        assertFalse(encoder.upgradeEncoding(encoded));
    }

    @Test
    void thirdFailedEmployeeLoginCreatesAdminRequest() {
        TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        employee.setSoLanDangNhapSai(2);
        when(accountRepo.findByTenDangNhap("nhanvien1")).thenReturn(employee);

        boolean locked = SecurityConfig.registerFailedLogin(accountRepo, " nhanvien1 ");

        assertFalse(locked);
        assertEquals(3, employee.getSoLanDangNhapSai());
        assertTrue(employee.getYeuCauDatLaiMatKhau());
        verify(accountRepo).save(employee);
    }

    @Test
    void fifthFailedEmployeeLoginDoesNotLockAccountYet() {
        TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        employee.setSoLanDangNhapSai(4);
        employee.setYeuCauDatLaiMatKhau(true);
        when(accountRepo.findByTenDangNhap("nhanvien1")).thenReturn(employee);

        boolean locked = SecurityConfig.registerFailedLogin(accountRepo, "nhanvien1");

        assertFalse(locked);
        assertEquals(5, employee.getSoLanDangNhapSai());
        assertFalse(employee.getTamKhoaDangNhap());
        verify(accountRepo).save(employee);
    }

    @Test
    void sixthFailedEmployeeLoginTemporarilyLocksAccount() {
        TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        employee.setSoLanDangNhapSai(5);
        employee.setYeuCauDatLaiMatKhau(true);
        when(accountRepo.findByTenDangNhap("nhanvien1")).thenReturn(employee);

        boolean locked = SecurityConfig.registerFailedLogin(accountRepo, "nhanvien1");

        assertTrue(locked);
        assertEquals(6, employee.getSoLanDangNhapSai());
        assertTrue(employee.getYeuCauDatLaiMatKhau());
        assertTrue(employee.getTamKhoaDangNhap());
        verify(accountRepo).save(employee);
    }

    @Test
    void failedAdminLoginDoesNotCreateAResetRequest() {
        TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        admin.setSoLanDangNhapSai(2);
        when(accountRepo.findByTenDangNhap("admin")).thenReturn(admin);

        boolean locked = SecurityConfig.registerFailedLogin(accountRepo, "admin");

        assertFalse(locked);
        assertEquals(2, admin.getSoLanDangNhapSai());
        assertFalse(admin.getYeuCauDatLaiMatKhau());
        verify(accountRepo, never()).save(admin);
    }
}
