package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import com.example.qlchgiay.service.AppNotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.Normalizer;
import java.util.Locale;

@ControllerAdvice
public class SessionUserControllerAdvice {
    private final TaiKhoanRepo accountRepo;
    private final AppNotificationService notificationService;

    public SessionUserControllerAdvice(
            TaiKhoanRepo accountRepo,
            AppNotificationService notificationService
    ) {
        this.accountRepo = accountRepo;
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void exposeCurrentUser(HttpSession session, Model model) {
        if (!(session.getAttribute("user") instanceof TaiKhoan account)) {
            return;
        }
        account = accountRepo.findWithEmployeeByTenDangNhap(account.getTenDangNhap())
                .orElseThrow(() -> inactiveSession(session));
        if (!isActive(account) || (!isAdmin(account) && !isEmployee(account))) {
            throw inactiveSession(session);
        }
        session.setAttribute("user", account);
        session.setAttribute("userName", displayName(account, null));
        session.setAttribute("userRole", displayRole(account, null));

        model.addAttribute("userName", displayName(account, session));
        model.addAttribute("userRole", displayRole(account, session));
        model.addAttribute("isEmployee", isEmployee(account));
        model.addAttribute("isAdmin", isAdmin(account));
        model.addAttribute("currentEmployee", account.getMaNhanVien());
        model.addAttribute("persistentNotifications", notificationService.latestFor(account.getId()));
        model.addAttribute("persistentUnreadCount", notificationService.unreadCount(account.getId()));
    }

    @ExceptionHandler(InactiveSessionException.class)
    public String handleInactiveSession() {
        return "redirect:/login?inactive";
    }

    public static boolean isEmployee(HttpSession session) {
        return session.getAttribute("user") instanceof TaiKhoan account && isEmployee(account);
    }

    public static boolean isEmployee(TaiKhoan account) {
        if (account == null) return false;
        return switch (normalizeRole(storedRole(account))) {
            case "nhan vien", "nhan vien ban hang" -> true;
            default -> false;
        };
    }

    public static boolean isAdmin(HttpSession session) {
        return session.getAttribute("user") instanceof TaiKhoan account && isAdmin(account);
    }

    public static boolean isAdmin(TaiKhoan account) {
        if (account == null) {
            return false;
        }
        String normalizedRole = normalizeRole(storedRole(account));
        return switch (normalizedRole) {
            case "admin", "quan ly", "quan li", "quan ly cua hang", "quan li cua hang" -> true;
            default -> false;
        };
    }

    public static NhanVien currentEmployee(HttpSession session) {
        if (session.getAttribute("user") instanceof TaiKhoan account) {
            return account.getMaNhanVien();
        }
        return null;
    }

    public static boolean isActive(TaiKhoan account) {
        if (account == null || isInactive(account.getTrangThai())) return false;
        return account.getMaNhanVien() == null
                || !isInactive(account.getMaNhanVien().getTrangThai());
    }

    public static boolean hasBusinessAccess(HttpSession session) {
        if (!(session.getAttribute("user") instanceof TaiKhoan account)) return false;
        return isActive(account) && (isAdmin(account) || isEmployee(account));
    }

    public static String displayName(TaiKhoan account, HttpSession session) {
        if (session != null && session.getAttribute("userName") instanceof String userName
                && !userName.isBlank()) {
            return userName;
        }
        NhanVien employee = account.getMaNhanVien();
        if (employee != null && employee.getTenNhanVien() != null
                && !employee.getTenNhanVien().isBlank()) {
            return employee.getTenNhanVien();
        }
        return account.getTenDangNhap() == null ? "Người dùng" : account.getTenDangNhap();
    }

    public static String displayRole(TaiKhoan account, HttpSession session) {
        if (session != null && session.getAttribute("userRole") instanceof String userRole
                && !userRole.isBlank()) {
            return userRole;
        }
        if (account.getVaiTro() != null && !account.getVaiTro().isBlank()) {
            return account.getVaiTro();
        }
        NhanVien employee = account.getMaNhanVien();
        if (employee != null && employee.getMaChucVu() != null
                && employee.getMaChucVu().getTenChucVu() != null
                && !employee.getMaChucVu().getTenChucVu().isBlank()) {
            return employee.getMaChucVu().getTenChucVu();
        }
        return "Nhân viên";
    }

    private static String storedRole(TaiKhoan account) {
        if (account.getVaiTro() != null && !account.getVaiTro().isBlank()) {
            return account.getVaiTro();
        }
        NhanVien employee = account.getMaNhanVien();
        return employee != null && employee.getMaChucVu() != null
                ? employee.getMaChucVu().getTenChucVu()
                : "";
    }

    private AccessDeniedException inactiveSession(HttpSession session) {
        session.invalidate();
        return new InactiveSessionException("Tài khoản không còn quyền truy cập hệ thống.");
    }

    private static final class InactiveSessionException extends AccessDeniedException {
        private InactiveSessionException(String message) {
            super(message);
        }
    }

    private static boolean isInactive(String status) {
        if (status == null || status.isBlank()) return false;
        String value = normalizeRole(status);
        return value.contains("ngung") || value.contains("khoa")
                || value.contains("inactive") || value.contains("disable");
    }

    private static String normalizeRole(String role) {
        String value = role == null ? "" : role;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
