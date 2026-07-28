package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.service.WorkSessionService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Year;

@Controller
@RequestMapping("/khachhang")
public class KhachHangController {
    private final KhachHangRepo repo;
    private final WorkSessionService workSessionService;

    public KhachHangController(KhachHangRepo repo, WorkSessionService workSessionService) {
        this.repo = repo;
        this.workSessionService = workSessionService;
    }

    @InitBinder
    void trimTextFields(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/them")
    public String createForm(HttpSession s, Model m) {
        if (!loggedIn(s)) return "redirect:/login";
        m.addAttribute("item", new KhachHang());
        m.addAttribute("pageTitle", "Thêm khách hàng");
        return "khachhang-form";
    }

    @PostMapping("/them")
    public String create(
            HttpSession s,
            @Valid @ModelAttribute("item") KhachHang item,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra
    ) {
        if (!loggedIn(s)) return "redirect:/login";
        validateBusinessRules(item, null, bindingResult);
        if (bindingResult.hasErrors()) {
            return showForm(model, "Thêm khách hàng");
        }
        try {
            repo.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            bindingResult.reject(
                    "customer.save.conflict",
                    "Không thể lưu khách hàng vì thông tin bị trùng hoặc không hợp lệ."
            );
            return showForm(model, "Thêm khách hàng");
        }
        if (SessionUserControllerAdvice.isEmployee(s)) {
            workSessionService.recordCustomerCreated(s);
        }
        ra.addFlashAttribute("success", "Đã thêm khách hàng thành công.");
        return "redirect:/khachhang";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, HttpSession s, Model m, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";
        KhachHang item = repo.findById(id).orElse(null);
        if (item == null) return missing(ra);
        m.addAttribute("item", item);
        return "khachhang-detail";
    }

    @GetMapping("/sua/{id}")
    public String updateForm(@PathVariable Integer id, HttpSession s, Model m, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";
        KhachHang item = repo.findById(id).orElse(null);
        if (item == null) return missing(ra);
        m.addAttribute("item", item);
        m.addAttribute("pageTitle", "Cập nhật khách hàng");
        return "khachhang-form";
    }

    @PostMapping("/sua/{id}")
    public String update(
            @PathVariable Integer id,
            HttpSession s,
            @Valid @ModelAttribute("item") KhachHang form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra
    ) {
        if (!loggedIn(s)) return "redirect:/login";
        KhachHang item = repo.findById(id).orElse(null);
        if (item == null) return missing(ra);
        form.setId(id);
        validateBusinessRules(form, id, bindingResult);
        if (bindingResult.hasErrors()) {
            return showForm(model, "Cập nhật khách hàng");
        }

        item.setTenKH(form.getTenKH()); item.setGioiTinh(form.getGioiTinh()); item.setNamSinh(form.getNamSinh());
        item.setSoDienThoai(form.getSoDienThoai()); item.setDiaChi(form.getDiaChi());
        try {
            repo.saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            bindingResult.reject(
                    "customer.save.conflict",
                    "Không thể lưu khách hàng vì thông tin bị trùng hoặc không hợp lệ."
            );
            return showForm(model, "Cập nhật khách hàng");
        }
        ra.addFlashAttribute("success", "Đã cập nhật khách hàng thành công.");
        return "redirect:/khachhang";
    }

    @PostMapping("/xoa/{id}")
    public String delete(@PathVariable Integer id, HttpSession s, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";
        try { repo.deleteById(id); repo.flush(); ra.addFlashAttribute("success", "Đã xóa khách hàng."); }
        catch (DataIntegrityViolationException ex) { ra.addFlashAttribute("error", "Không thể xóa khách hàng đã phát sinh đơn hàng hoặc giỏ hàng."); }
        return "redirect:/khachhang";
    }

    private void validateBusinessRules(KhachHang item, Integer currentId, BindingResult bindingResult) {
        if (item.getNamSinh() != null
                && (item.getNamSinh() < 1900 || item.getNamSinh() > Year.now().getValue())) {
            bindingResult.rejectValue(
                    "namSinh",
                    "customer.birthYear.invalid",
                    "Năm sinh phải từ 1900 đến năm hiện tại."
            );
        }

        String phone = item.getSoDienThoai();
        if (phone != null && !bindingResult.hasFieldErrors("soDienThoai")) {
            boolean duplicate = currentId == null
                    ? repo.existsBySoDienThoai(phone)
                    : repo.existsBySoDienThoaiAndIdNot(phone, currentId);
            if (duplicate) {
                bindingResult.rejectValue(
                        "soDienThoai",
                        "customer.phone.duplicate",
                        "Số điện thoại đã thuộc về một khách hàng khác."
                );
            }
        }
    }

    private String showForm(Model model, String title) {
        model.addAttribute("pageTitle", title);
        return "khachhang-form";
    }

    private String missing(RedirectAttributes ra) { ra.addFlashAttribute("error", "Không tìm thấy khách hàng."); return "redirect:/khachhang"; }
    private boolean loggedIn(HttpSession s) { return s.getAttribute("user") instanceof TaiKhoan; }
}
