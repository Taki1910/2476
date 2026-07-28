package com.example.qlchgiay.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import com.example.qlchgiay.service.WorkSessionService;

@Controller
public class AccountController {

    private final TaiKhoanRepo taiKhoanRepo;
    private final WorkSessionService workSessionService;

    public AccountController(TaiKhoanRepo taiKhoanRepo, WorkSessionService workSessionService) {
        this.taiKhoanRepo = taiKhoanRepo;
        this.workSessionService = workSessionService;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        clearAuthenticatedSession(session);
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            Model model
    ) {
        TaiKhoan taiKhoan = taiKhoanRepo.findByTenDangNhapAndMatKhau(username.trim(), password);

        if (taiKhoan == null || isInactive(taiKhoan.getTrangThai())) {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu");
            model.addAttribute("username", username);
            return "login";
        }

        NhanVien nhanVien = taiKhoan.getMaNhanVien();
        String userName = nhanVien != null && nhanVien.getTenNhanVien() != null
                ? nhanVien.getTenNhanVien()
                : taiKhoan.getTenDangNhap();

        String userRole = taiKhoan.getVaiTro();
        if (userRole == null || userRole.isBlank()) {
            if (nhanVien != null && nhanVien.getMaChucVu() != null
                    && nhanVien.getMaChucVu().getTenChucVu() != null) {
                userRole = nhanVien.getMaChucVu().getTenChucVu();
            } else {
                userRole = "Nhân viên";
            }
        }

        session.setAttribute("user", taiKhoan);
        session.setAttribute("userName", userName);
        session.setAttribute("userRole", userRole);
        var workNotifications = workSessionService.handleSuccessfulLogin(taiKhoan, session);
        if (workNotifications.isEmpty()) {
            session.removeAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE);
        } else {
            session.setAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE, workNotifications);
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        workSessionService.finishSession(session);
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/quen-mat-khau")
    public String forgotPasswordPage(HttpSession session) {
        clearAuthenticatedSession(session);
        return "quen-mat-khau";
    }

    @PostMapping("/quen-mat-khau")
    public String resetPassword(
            @RequestParam("username") String username,
            @RequestParam("phone") String phone,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPhone = normalizePhone(phone);

        model.addAttribute("username", normalizedUsername);
        model.addAttribute("phone", phone);

        if (normalizedUsername.isBlank() || normalizedPhone.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập đầy đủ tên đăng nhập và số điện thoại");
            return "quen-mat-khau";
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 255) {
            model.addAttribute("error", "Mật khẩu mới phải có từ 6 đến 255 ký tự");
            return "quen-mat-khau";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp");
            return "quen-mat-khau";
        }

        TaiKhoan taiKhoan = taiKhoanRepo.findByTenDangNhap(normalizedUsername);
        NhanVien nhanVien = taiKhoan == null ? null : taiKhoan.getMaNhanVien();
        String accountPhone = nhanVien == null ? "" : normalizePhone(nhanVien.getSoDienThoai());

        if (taiKhoan == null || accountPhone.isBlank() || !accountPhone.equals(normalizedPhone)) {
            model.addAttribute("error", "Thông tin tài khoản hoặc số điện thoại không chính xác");
            return "quen-mat-khau";
        }
        if (isInactive(taiKhoan.getTrangThai())) {
            model.addAttribute("error", "Tài khoản đang bị khóa. Vui lòng liên hệ quản lý");
            return "quen-mat-khau";
        }

        taiKhoan.setMatKhau(newPassword);
        taiKhoanRepo.save(taiKhoan);
        redirectAttributes.addFlashAttribute(
                "success",
                "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới"
        );
        return "redirect:/login";
    }

    @GetMapping("/caidat")
    public String settings(HttpSession session, Model model) {
        if (!(session.getAttribute("user") instanceof TaiKhoan user)) return "redirect:/login";
        model.addAttribute("user", user);
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("userRole", session.getAttribute("userRole"));
        return "caidat";
    }

    private boolean isInactive(String trangThai) {
        if (trangThai == null || trangThai.isBlank()) {
            return false;
        }
        String status = trangThai.trim().toLowerCase();
        return status.contains("ngừng")
                || status.contains("ngung")
                || status.contains("khóa")
                || status.contains("khoa")
                || status.contains("inactive")
                || status.contains("disable");
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private void clearAuthenticatedSession(HttpSession session) {
        if (session.getAttribute("user") != null) {
            workSessionService.finishSession(session);
            session.invalidate();
        }
    }
}
