package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.Normalizer;
import java.util.Locale;

@ControllerAdvice
public class SessionUserControllerAdvice {

    @ModelAttribute
    public void exposeCurrentUser(HttpSession session, Model model) {
        if (!(session.getAttribute("user") instanceof TaiKhoan account)) {
            return;
        }

        model.addAttribute("userName", displayName(account, session));
        model.addAttribute("userRole", displayRole(account, session));
        model.addAttribute("isEmployee", isEmployee(account));
        model.addAttribute("isAdmin", isAdmin(account));
        model.addAttribute("currentEmployee", account.getMaNhanVien());
    }

    public static boolean isEmployee(HttpSession session) {
        return session.getAttribute("user") instanceof TaiKhoan account && isEmployee(account);
    }

    public static boolean isEmployee(TaiKhoan account) {
        return account != null && !isAdmin(account);
    }

    public static boolean isAdmin(HttpSession session) {
        return session.getAttribute("user") instanceof TaiKhoan account && isAdmin(account);
    }

    public static boolean isAdmin(TaiKhoan account) {
        if (account == null) {
            return false;
        }
        String normalizedRole = normalizeRole(displayRole(account, null));
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

    private static String normalizeRole(String role) {
        String value = role == null ? "" : role;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
