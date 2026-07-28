package com.example.qlchgiay.service;

import com.example.qlchgiay.controller.SessionUserControllerAdvice;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.PhienLamViec;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.PhienLamViecRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkSessionService {
    public static final String SESSION_ID_ATTRIBUTE = "workSessionId";
    public static final String NOTIFICATIONS_ATTRIBUTE = "workNotifications";

    private final PhienLamViecRepo workSessionRepo;

    public WorkSessionService(PhienLamViecRepo workSessionRepo) {
        this.workSessionRepo = workSessionRepo;
    }

    @Transactional
    public List<WorkSummary> handleSuccessfulLogin(TaiKhoan account, HttpSession httpSession) {
        if (SessionUserControllerAdvice.isEmployee(account)) {
            NhanVien employee = account.getMaNhanVien();
            if (employee == null || employee.getId() == null) {
                return List.of();
            }

            LocalDateTime now = LocalDateTime.now();
            List<PhienLamViec> abandonedSessions =
                    workSessionRepo.findByMaNhanVienIdAndKetThucIsNull(employee.getId());
            abandonedSessions.forEach(workSession -> workSession.setKetThuc(now));
            workSessionRepo.saveAll(abandonedSessions);

            List<PhienLamViec> unseenSessions = workSessionRepo
                    .findByMaNhanVienIdAndKetThucIsNotNullAndNhanVienDaXemFalseOrderByKetThucDesc(
                            employee.getId()
                    );
            List<WorkSummary> notifications = summarizeActiveSessions(unseenSessions);
            unseenSessions.forEach(workSession -> workSession.setNhanVienDaXem(true));
            workSessionRepo.saveAll(unseenSessions);

            PhienLamViec currentSession = new PhienLamViec();
            currentSession.setMaNhanVien(employee);
            currentSession.setBatDau(now);
            currentSession = workSessionRepo.save(currentSession);
            httpSession.setAttribute(SESSION_ID_ATTRIBUTE, currentSession.getId());
            return notifications;
        }

        if (SessionUserControllerAdvice.isAdmin(account)) {
            List<PhienLamViec> unseenSessions = workSessionRepo
                    .findByKetThucIsNotNullAndAdminDaXemFalseOrderByKetThucDesc();
            List<WorkSummary> notifications = summarizeActiveSessions(unseenSessions);
            unseenSessions.forEach(workSession -> workSession.setAdminDaXem(true));
            workSessionRepo.saveAll(unseenSessions);
            return notifications;
        }

        return List.of();
    }

    @Transactional
    public void finishSession(HttpSession httpSession) {
        Object workSessionId = httpSession.getAttribute(SESSION_ID_ATTRIBUTE);
        if (!(workSessionId instanceof Integer id)) {
            return;
        }
        workSessionRepo.findById(id).ifPresent(workSession -> {
            if (workSession.getKetThuc() == null) {
                workSession.setKetThuc(LocalDateTime.now());
                workSessionRepo.save(workSession);
            }
        });
        httpSession.removeAttribute(SESSION_ID_ATTRIBUTE);
    }

    @Transactional
    public void recordCustomerCreated(HttpSession httpSession) {
        currentSession(httpSession).ifPresent(workSession -> {
            workSession.setSoKhachHangMoi(safeCount(workSession.getSoKhachHangMoi()) + 1);
            workSessionRepo.save(workSession);
        });
    }

    @Transactional
    public void recordPaidSale(
            HttpSession httpSession,
            int productQuantity,
            BigDecimal revenue
    ) {
        if (productQuantity <= 0 && (revenue == null || revenue.signum() <= 0)) {
            return;
        }
        currentSession(httpSession).ifPresent(workSession -> {
            workSession.setSoSanPhamBan(
                    safeCount(workSession.getSoSanPhamBan()) + Math.max(0, productQuantity)
            );
            workSession.setDoanhThu(
                    safeMoney(workSession.getDoanhThu()).add(safeMoney(revenue))
            );
            workSessionRepo.save(workSession);
        });
    }

    private java.util.Optional<PhienLamViec> currentSession(HttpSession httpSession) {
        Object workSessionId = httpSession.getAttribute(SESSION_ID_ATTRIBUTE);
        if (!(workSessionId instanceof Integer id)) {
            return java.util.Optional.empty();
        }
        return workSessionRepo.findById(id)
                .filter(workSession -> workSession.getKetThuc() == null);
    }

    private List<WorkSummary> summarizeActiveSessions(List<PhienLamViec> sessions) {
        List<WorkSummary> summaries = new ArrayList<>();
        for (PhienLamViec workSession : sessions) {
            int productCount = safeCount(workSession.getSoSanPhamBan());
            int customerCount = safeCount(workSession.getSoKhachHangMoi());
            BigDecimal revenue = safeMoney(workSession.getDoanhThu());
            if (productCount == 0 && customerCount == 0 && revenue.signum() == 0) {
                continue;
            }
            String employeeName = workSession.getMaNhanVien() == null
                    || workSession.getMaNhanVien().getTenNhanVien() == null
                    ? "Nhân viên"
                    : workSession.getMaNhanVien().getTenNhanVien();
            summaries.add(new WorkSummary(
                    employeeName,
                    workSession.getBatDau(),
                    workSession.getKetThuc(),
                    productCount,
                    customerCount,
                    revenue
            ));
        }
        return summaries;
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record WorkSummary(
            String employeeName,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int productCount,
            int customerCount,
            BigDecimal revenue
    ) {
    }
}
