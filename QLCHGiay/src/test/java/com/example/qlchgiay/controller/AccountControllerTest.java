package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {
    private final TaiKhoanRepo accountRepo = mock(TaiKhoanRepo.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AccountController controller =
            new AccountController(accountRepo, passwordEncoder);

    @Test
    void loginPageShowsGenericAuthenticationError() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.loginPage(
                "true",
                null,
                null,
                null,
                null,
                new MockHttpSession(),
                model
        );

        assertEquals("login", view);
        assertEquals("Sai tên đăng nhập hoặc mật khẩu", model.get("error"));
    }

    @Test
    void loginPageExplainsWhenAccountIsTemporarilyLocked() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.loginPage(
                null,
                "true",
                null,
                null,
                null,
                new MockHttpSession(),
                model
        );

        assertEquals("login", view);
        assertEquals(
                "Tài khoản đã tạm khóa. Vui lòng liên hệ quản lý",
                model.get("error")
        );
    }

    @Test
    void authenticatedUserCannotReturnToLoginPage() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new TaiKhoan());

        String view = controller.loginPage(
                null,
                null,
                null,
                null,
                null,
                session,
                new ExtendedModelMap()
        );

        assertEquals("redirect:/dashboard", view);
    }

    @Test
    void forgotPasswordOnlyShowsSafeRecoveryInstructions() {
        assertEquals("quen-mat-khau", controller.forgotPasswordPage());
    }

    @Test
    void adminCanResetAnAccountPassword() {
        MockHttpSession session = new MockHttpSession();
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        session.setAttribute("user", admin);

        TaiKhoan account = new TaiKhoan();
        account.setId(2);
        account.setTenDangNhap("nhanvien1");
        account.setMatKhau("old-password");
        account.setSoLanDangNhapSai(3);
        account.setYeuCauDatLaiMatKhau(true);
        account.setTamKhoaDangNhap(true);
        when(accountRepo.findById(2)).thenReturn(java.util.Optional.of(account));

        String view = controller.adminResetPassword(
                2,
                "mat-khau-moi",
                "mat-khau-moi",
                session,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/nhanvien", view);
        assertTrue(passwordEncoder.matches("mat-khau-moi", account.getMatKhau()));
        assertEquals(0, account.getSoLanDangNhapSai());
        assertFalse(account.getYeuCauDatLaiMatKhau());
        assertFalse(account.getTamKhoaDangNhap());
        verify(accountRepo).save(account);
    }

    @Test
    void adminCanUnlockAnAccountWithoutChangingItsPassword() {
        MockHttpSession session = new MockHttpSession();
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        session.setAttribute("user", admin);

        TaiKhoan account = new TaiKhoan();
        account.setId(2);
        account.setTenDangNhap("nhanvien1");
        account.setMatKhau("unchanged-password");
        account.setSoLanDangNhapSai(6);
        account.setYeuCauDatLaiMatKhau(true);
        account.setTamKhoaDangNhap(true);
        when(accountRepo.findById(2)).thenReturn(java.util.Optional.of(account));

        String view = controller.adminUnlockAccount(
                2,
                session,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/nhanvien", view);
        assertEquals("unchanged-password", account.getMatKhau());
        assertEquals(0, account.getSoLanDangNhapSai());
        assertFalse(account.getYeuCauDatLaiMatKhau());
        assertFalse(account.getTamKhoaDangNhap());
        verify(accountRepo).save(account);
    }

    @Test
    void adminUnlockRequiresAnAccountSelection() {
        MockHttpSession session = new MockHttpSession();
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        session.setAttribute("user", admin);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.adminUnlockAccount(null, session, redirectAttributes);

        assertEquals("redirect:/nhanvien", view);
        assertEquals(
                "Vui lòng chọn tài khoản cần mở khóa",
                redirectAttributes.getFlashAttributes().get("passwordError")
        );
        verify(accountRepo, never()).findById(2);
    }

    @Test
    void employeeCannotResetAnotherAccountPassword() {
        MockHttpSession session = new MockHttpSession();
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        session.setAttribute("user", employee);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.adminResetPassword(
                        2,
                        "mat-khau-moi",
                        "mat-khau-moi",
                        session,
                        new RedirectAttributesModelMap()
                )
        );
    }

    @Test
    void adminResetRejectsMismatchedPasswords() {
        MockHttpSession session = new MockHttpSession();
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Quản lý");
        session.setAttribute("user", admin);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.adminResetPassword(
                2,
                "mat-khau-moi",
                "mat-khau-khac",
                session,
                redirectAttributes
        );

        assertEquals("redirect:/nhanvien", view);
        assertEquals(
                "Mật khẩu xác nhận không khớp",
                redirectAttributes.getFlashAttributes().get("passwordError")
        );
        verify(accountRepo, never()).findById(2);
    }
}
