package com.example.qlchgiay.service;

import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.PhienLamViec;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.PhienLamViecRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkSessionServiceTest {
    @Mock private PhienLamViecRepo workSessionRepo;
    @Mock private HttpSession httpSession;

    private WorkSessionService service;

    @BeforeEach
    void setUp() {
        service = new WorkSessionService(workSessionRepo);
    }

    @Test
    void employeeReceivesPreviousSessionSummaryAndStartsANewSession() {
        NhanVien employee = employee(2, "Trần Nhân Viên");
        TaiKhoan account = account("Nhân viên", employee);
        PhienLamViec previousSession = completedSession(employee);
        previousSession.setSoSanPhamBan(4);
        previousSession.setSoKhachHangMoi(2);
        previousSession.setDoanhThu(BigDecimal.valueOf(3_600_000));

        when(workSessionRepo.findByMaNhanVienIdAndKetThucIsNull(2)).thenReturn(List.of());
        when(workSessionRepo
                .findByMaNhanVienIdAndKetThucIsNotNullAndNhanVienDaXemFalseOrderByKetThucDesc(2))
                .thenReturn(List.of(previousSession));
        when(workSessionRepo.save(any(PhienLamViec.class))).thenAnswer(invocation -> {
            PhienLamViec saved = invocation.getArgument(0);
            saved.setId(99);
            return saved;
        });

        List<WorkSessionService.WorkSummary> notifications =
                service.handleSuccessfulLogin(account, httpSession);

        assertEquals(1, notifications.size());
        assertEquals("Trần Nhân Viên", notifications.get(0).employeeName());
        assertEquals(4, notifications.get(0).productCount());
        assertEquals(2, notifications.get(0).customerCount());
        assertEquals(BigDecimal.valueOf(3_600_000), notifications.get(0).revenue());
        assertTrue(previousSession.getNhanVienDaXem());
        verify(httpSession).setAttribute(WorkSessionService.SESSION_ID_ATTRIBUTE, 99);
    }

    @Test
    void adminReceivesCompletedEmployeeSessionSummary() {
        NhanVien employee = employee(3, "Lê Nhân Viên");
        TaiKhoan account = account("Admin", employee(1, "Quản trị viên"));
        PhienLamViec previousSession = completedSession(employee);
        previousSession.setSoSanPhamBan(3);
        previousSession.setDoanhThu(BigDecimal.valueOf(2_400_000));

        when(workSessionRepo.findByKetThucIsNotNullAndAdminDaXemFalseOrderByKetThucDesc())
                .thenReturn(List.of(previousSession));

        List<WorkSessionService.WorkSummary> notifications =
                service.handleSuccessfulLogin(account, httpSession);

        assertEquals(1, notifications.size());
        assertEquals("Lê Nhân Viên", notifications.get(0).employeeName());
        assertTrue(previousSession.getAdminDaXem());
    }

    @Test
    void emptySessionIsMarkedSeenButNotShown() {
        NhanVien employee = employee(2, "Trần Nhân Viên");
        TaiKhoan account = account("Admin", employee(1, "Quản trị viên"));
        PhienLamViec emptySession = completedSession(employee);

        when(workSessionRepo.findByKetThucIsNotNullAndAdminDaXemFalseOrderByKetThucDesc())
                .thenReturn(List.of(emptySession));

        List<WorkSessionService.WorkSummary> notifications =
                service.handleSuccessfulLogin(account, httpSession);

        assertTrue(notifications.isEmpty());
        assertTrue(emptySession.getAdminDaXem());
    }

    private TaiKhoan account(String role, NhanVien employee) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        account.setMaNhanVien(employee);
        return account;
    }

    private NhanVien employee(int id, String name) {
        NhanVien employee = new NhanVien();
        employee.setId(id);
        employee.setTenNhanVien(name);
        return employee;
    }

    private PhienLamViec completedSession(NhanVien employee) {
        PhienLamViec workSession = new PhienLamViec();
        workSession.setMaNhanVien(employee);
        workSession.setBatDau(LocalDateTime.now().minusHours(4));
        workSession.setKetThuc(LocalDateTime.now().minusHours(1));
        return workSession;
    }
}
