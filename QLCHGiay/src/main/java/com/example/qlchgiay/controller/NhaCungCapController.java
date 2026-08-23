package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhaCungCap;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/nhacungcap")
public class NhaCungCapController {
    private static final String ACTIVE_STATUS = "Hoạt động";
    private static final String INACTIVE_STATUS = "Ngừng hợp tác";

    private final NhaCungCapRepo repo;

    public NhaCungCapController(NhaCungCapRepo repo) { this.repo = repo; }

    @InitBinder
    void trimTextFields(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/them")
    public String createForm(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        NhaCungCap item = new NhaCungCap();
        item.setTrangThai(ACTIVE_STATUS);
        model.addAttribute("item", item);
        return showForm(model, "Thêm nhà cung cấp");
    }

    @PostMapping("/them")
    public String create(
            HttpSession session,
            @Valid @ModelAttribute("item") NhaCungCap item,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        if (bindingResult.hasErrors()) return showForm(model, "Thêm nhà cung cấp");
        repo.save(item);
        redirect.addFlashAttribute("success", "Đã thêm nhà cung cấp.");
        return "redirect:/nhacungcap";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, HttpSession session, Model model, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        NhaCungCap item = repo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        model.addAttribute("item", item);
        return "nhacungcap-detail";
    }

    @GetMapping("/sua/{id}")
    public String updateForm(
            @PathVariable Integer id,
            HttpSession session,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        NhaCungCap item = repo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        model.addAttribute("item", item);
        return showForm(model, "Cập nhật nhà cung cấp");
    }

    @PostMapping("/sua/{id}")
    public String update(
            @PathVariable Integer id,
            HttpSession session,
            @Valid @ModelAttribute("item") NhaCungCap form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirect
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        NhaCungCap item = repo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        if (bindingResult.hasErrors()) {
            form.setId(id);
            return showForm(model, "Cập nhật nhà cung cấp");
        }

        item.setTenNCC(form.getTenNCC());
        item.setSoDienThoai(form.getSoDienThoai());
        item.setEmail(form.getEmail());
        item.setDiaChi(form.getDiaChi());
        item.setTrangThai(form.getTrangThai());
        repo.save(item);
        redirect.addFlashAttribute("success", "Đã cập nhật nhà cung cấp.");
        return "redirect:/nhacungcap";
    }

    @PostMapping("/ngung-hop-tac/{id}")
    public String deactivate(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirect.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền ngừng hợp tác với nhà cung cấp."
            );
            return "redirect:/nhacungcap";
        }
        NhaCungCap supplier = repo.findById(id).orElse(null);
        if (supplier == null) return missing(redirect);
        if (INACTIVE_STATUS.equals(supplier.getTrangThai())) {
            redirect.addFlashAttribute("success", "Nhà cung cấp đã ngừng hợp tác trước đó.");
            return "redirect:/nhacungcap";
        }
        supplier.setTrangThai(INACTIVE_STATUS);
        repo.save(supplier);
        redirect.addFlashAttribute("success", "Đã ngừng hợp tác với nhà cung cấp.");
        return "redirect:/nhacungcap";
    }

    private String showForm(Model model, String title) {
        model.addAttribute("pageTitle", title);
        return "nhacungcap-form";
    }

    private String missing(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Không tìm thấy nhà cung cấp.");
        return "redirect:/nhacungcap";
    }

    private boolean loggedIn(HttpSession session) {
        return SessionUserControllerAdvice.hasBusinessAccess(session);
    }
}
