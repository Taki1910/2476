package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChucVuRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NhanVienControllerTest {
    @Mock private NhanVienRepo employeeRepo;
    @Mock private TaiKhoanRepo accountRepo;
    @Mock private ChucVuRepo positionRepo;
    @Mock private HttpSession session;

    private BCryptPasswordEncoder passwordEncoder;
    private NhanVienController controller;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        controller = new NhanVienController(
                employeeRepo,
                accountRepo,
                positionRepo,
                passwordEncoder
        );
    }

    @Test
    void adminCanCreateEmployeeAndLoginAccountTogether() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(admin);
        when(positionRepo.findFirstByTenChucVuIgnoreCase("Nhân viên"))
                .thenReturn(Optional.empty());

        NhanVien employee = validEmployee();
        String view = controller.create(
                session,
                employee,
                "nhanvien2",
                "mat-khau-moi",
                "mat-khau-moi",
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        ArgumentCaptor<TaiKhoan> account = ArgumentCaptor.forClass(TaiKhoan.class);
        verify(employeeRepo).save(employee);
        verify(accountRepo).save(account.capture());
        assertEquals("redirect:/nhanvien", view);
        assertSame(employee, account.getValue().getMaNhanVien());
        assertEquals("Nhân viên", account.getValue().getVaiTro());
        assertTrue(passwordEncoder.matches("mat-khau-moi", account.getValue().getMatKhau()));
    }

    @Test
    void createRejectsEmployeeOneDayBeforeEighteenthBirthday() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(admin);
        NhanVien employee = validEmployee();
        employee.setNgaySinh(LocalDate.now().minusYears(18).plusDays(1));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.create(
                session,
                employee,
                "nhanvien2",
                "mat-khau-moi",
                "mat-khau-moi",
                model,
                new RedirectAttributesModelMap()
        );

        assertEquals("nhanvien-form", view);
        assertEquals("Nhân viên phải đủ 18 tuổi trở lên.", model.get("error"));
        verify(employeeRepo, never()).save(employee);
        verify(accountRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsEmployeeUnderEighteen() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(admin);
        NhanVien existing = validEmployee();
        existing.setId(7);
        NhanVien form = validEmployee();
        form.setNgaySinh(LocalDate.now().minusYears(18).plusDays(1));
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro("Nhân viên");
        when(employeeRepo.findById(7)).thenReturn(Optional.of(existing));
        when(accountRepo.findFirstByMaNhanVienId(7)).thenReturn(Optional.of(account));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.update(
                7,
                session,
                form,
                true,
                model,
                new RedirectAttributesModelMap()
        );

        assertEquals("nhanvien-form", view);
        assertEquals("Nhân viên phải đủ 18 tuổi trở lên.", model.get("error"));
        verify(employeeRepo, never()).save(existing);
        verify(accountRepo, never()).save(account);
    }

    @Test
    void employeeCannotOpenEmployeeManagement() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.list(session, null, new ExtendedModelMap())
        );
    }

    private NhanVien validEmployee() {
        NhanVien employee = new NhanVien();
        employee.setTenNhanVien("Nhân viên kiểm thử");
        employee.setGioiTinh(true);
        employee.setNgaySinh(LocalDate.now().minusYears(18));
        employee.setSoDienThoai("0901234567");
        employee.setQueQuan("Hà Nội");
        return employee;
    }
}
