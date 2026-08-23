package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import com.example.qlchgiay.service.AppNotificationService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void onlyKnownEmployeeRolesUseEmployeePermissions() {
        TaiKhoan standardEmployee = account("Nhân viên");
        TaiKhoan employee = account("Nhân viên bán hàng");
        TaiKhoan explicitlyNotAdmin = account("Không phải admin");
        TaiKhoan unspecifiedRole = account(null);

        assertTrue(SessionUserControllerAdvice.isEmployee(standardEmployee));
        assertTrue(SessionUserControllerAdvice.isEmployee(employee));
        assertFalse(SessionUserControllerAdvice.isEmployee(explicitlyNotAdmin));
        assertFalse(SessionUserControllerAdvice.isEmployee(unspecifiedRole));
        assertFalse(SessionUserControllerAdvice.isAdmin(employee));
        assertFalse(SessionUserControllerAdvice.isAdmin(explicitlyNotAdmin));
        assertFalse(SessionUserControllerAdvice.isAdmin(unspecifiedRole));
    }

    @Test
    void staleAuthenticatedSessionIsInvalidatedWhenAccountBecomesInactive() {
        TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
        HttpSession session = mock(HttpSession.class);
        TaiKhoan stale = account("Nhân viên");
        stale.setTenDangNhap("seller");
        TaiKhoan reloaded = account("Nhân viên");
        reloaded.setTenDangNhap("seller");
        reloaded.setTrangThai("INACTIVE");
        when(session.getAttribute("user")).thenReturn(stale);
        when(accountRepo.findWithEmployeeByTenDangNhap("seller"))
                .thenReturn(Optional.of(reloaded));

        SessionUserControllerAdvice advice = new SessionUserControllerAdvice(
                accountRepo,
                mock(AppNotificationService.class)
        );

        assertThrows(
                AccessDeniedException.class,
                () -> advice.exposeCurrentUser(session, new ExtendedModelMap())
        );
        verify(session).invalidate();
    }

    private TaiKhoan account(String role) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        return account;
    }
}
