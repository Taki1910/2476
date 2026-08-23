package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.service.AppNotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AppNotificationController {
    private final AppNotificationService notificationService;

    public AppNotificationController(AppNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        if (!(session.getAttribute("user") instanceof TaiKhoan account)) {
            return "redirect:/login";
        }
        if (!notificationService.markRead(id, account.getId())) {
            redirect.addFlashAttribute("error", "Không tìm thấy thông báo của tài khoản này.");
        }
        return "redirect:/dashboard#persistentNotifications";
    }
}
