package com.example.qlchgiay.controller;

import jakarta.servlet.http.HttpSession;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

@Controller
public class AccountController {
    private final TaiKhoanRepo accountRepo;
    private final PasswordEncoder passwordEncoder;

    public AccountController(TaiKhoanRepo accountRepo, PasswordEncoder passwordEncoder) {
        this.accountRepo = accountRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "locked", required = false) String locked,
            @RequestParam(name = "logout", required = false) String logout,
            HttpSession session,
            Model model
    ) {
        if (session.getAttribute("user") instanceof TaiKhoan) {
            return "redirect:/dashboard";
        }
        if (locked != null) {
            model.addAttribute(
                    "error",
                    "Tài khoản đã tạm khóa. Vui lòng liên hệ quản lý"
            );
        } else if (error != null) {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu");
        }
        if (logout != null) {
            model.addAttribute("success", "Bạn đã đăng xuất an toàn");
        }
        return "login";
    }

    @GetMapping("/quen-mat-khau")
    public String forgotPasswordPage() {
        return "quen-mat-khau";
    }

    @GetMapping("/caidat")
    public String settings(
            @RequestParam(name = "accountId", required = false) Integer accountId,
            HttpSession session,
            Model model
    ) {
        if (!(session.getAttribute("user") instanceof TaiKhoan user)) return "redirect:/login";
        model.addAttribute("user", user);
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("userRole", session.getAttribute("userRole"));
        if (SessionUserControllerAdvice.isAdmin(user)) {
            model.addAttribute("accounts", accountRepo.findAllByOrderByTenDangNhapAsc());
            model.addAttribute("selectedAccountId", accountId);
        }
        return "caidat";
    }

    @PostMapping("/admin/dat-lai-mat-khau")
    public String adminResetPassword(
            @RequestParam("accountId") Integer accountId,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            throw new AccessDeniedException("Chỉ quản lý được đặt lại mật khẩu");
        }
        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            redirectAttributes.addFlashAttribute(
                    "passwordError",
                    "Mật khẩu phải có ít nhất 8 ký tự và không quá 72 byte"
            );
            redirectAttributes.addAttribute("accountId", accountId);
            return "redirect:/caidat";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute(
                    "passwordError",
                    "Mật khẩu xác nhận không khớp"
            );
            redirectAttributes.addAttribute("accountId", accountId);
            return "redirect:/caidat";
        }

        TaiKhoan account = accountRepo.findById(accountId).orElse(null);
        if (account == null) {
            redirectAttributes.addFlashAttribute(
                    "passwordError",
                    "Tài khoản cần đặt lại không còn tồn tại"
            );
            redirectAttributes.addAttribute("accountId", accountId);
            return "redirect:/caidat";
        }

        account.setMatKhau(passwordEncoder.encode(newPassword));
        account.setSoLanDangNhapSai(0);
        account.setYeuCauDatLaiMatKhau(false);
        account.setTamKhoaDangNhap(false);
        accountRepo.save(account);
        redirectAttributes.addFlashAttribute(
                "passwordSuccess",
                "Đã đặt lại mật khẩu cho tài khoản @" + account.getTenDangNhap()
        );
        return "redirect:/caidat";
    }

    @PostMapping("/admin/mo-khoa-tai-khoan")
    public String adminUnlockAccount(
            @RequestParam(name = "accountId", required = false) Integer accountId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            throw new AccessDeniedException("Chỉ quản lý được mở khóa tài khoản");
        }
        if (accountId == null) {
            redirectAttributes.addFlashAttribute(
                    "passwordError",
                    "Vui lòng chọn tài khoản cần mở khóa"
            );
            return "redirect:/caidat";
        }

        TaiKhoan account = accountRepo.findById(accountId).orElse(null);
        if (account == null) {
            redirectAttributes.addFlashAttribute(
                    "passwordError",
                    "Tài khoản cần mở khóa không còn tồn tại"
            );
            redirectAttributes.addAttribute("accountId", accountId);
            return "redirect:/caidat";
        }

        account.setSoLanDangNhapSai(0);
        account.setYeuCauDatLaiMatKhau(false);
        account.setTamKhoaDangNhap(false);
        accountRepo.save(account);
        redirectAttributes.addFlashAttribute(
                "passwordSuccess",
                "Đã mở khóa đăng nhập cho tài khoản @" + account.getTenDangNhap()
        );
        return "redirect:/caidat";
    }

}
