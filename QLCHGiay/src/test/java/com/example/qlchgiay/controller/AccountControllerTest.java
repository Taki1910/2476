package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {
    @Mock
    private TaiKhoanRepo taiKhoanRepo;
    @Mock
    private HttpSession session;

    private AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(taiKhoanRepo);
    }

    @Test
    void resetPasswordWithMatchingEmployeePhone() {
        NhanVien employee = new NhanVien();
        employee.setSoDienThoai("0911 111 111");
        TaiKhoan account = new TaiKhoan();
        account.setTenDangNhap("admin");
        account.setMatKhau("123456");
        account.setTrangThai("Hoạt động");
        account.setMaNhanVien(employee);
        when(taiKhoanRepo.findByTenDangNhap("admin")).thenReturn(account);

        String view = controller.resetPassword(
                " admin ",
                "0911-111-111",
                "matkhau-moi",
                "matkhau-moi",
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/login", view);
        assertEquals("matkhau-moi", account.getMatKhau());
        verify(taiKhoanRepo).save(account);
    }

    @Test
    void resetPasswordRejectsWrongPhone() {
        NhanVien employee = new NhanVien();
        employee.setSoDienThoai("0911111111");
        TaiKhoan account = new TaiKhoan();
        account.setMaNhanVien(employee);
        when(taiKhoanRepo.findByTenDangNhap("admin")).thenReturn(account);

        String view = controller.resetPassword(
                "admin",
                "0999999999",
                "matkhau-moi",
                "matkhau-moi",
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("quen-mat-khau", view);
        verify(taiKhoanRepo, never()).save(account);
    }

    @Test
    void openingLoginClearsAnExistingAuthenticatedSession() {
        when(session.getAttribute("user")).thenReturn(new TaiKhoan());

        String view = controller.loginPage(session);

        assertEquals("login", view);
        verify(session).invalidate();
    }

    @Test
    void openingForgotPasswordClearsAnExistingAuthenticatedSession() {
        when(session.getAttribute("user")).thenReturn(new TaiKhoan());

        String view = controller.forgotPasswordPage(session);

        assertEquals("quen-mat-khau", view);
        verify(session).invalidate();
    }
}
