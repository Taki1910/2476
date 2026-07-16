package com.example.qlchgiay.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;

@Controller
public class AccountController {

    private final TaiKhoanRepo taiKhoanRepo;

    public AccountController(TaiKhoanRepo taiKhoanRepo) {
        this.taiKhoanRepo = taiKhoanRepo;
    }

    @GetMapping({"/", "/login"})
    public String loginPage(HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/dashboard";
        }
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
        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
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
}
