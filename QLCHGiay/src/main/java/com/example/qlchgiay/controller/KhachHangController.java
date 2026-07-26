package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.KhachHangRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/khachhang")
public class KhachHangController {
    private final KhachHangRepo repo;

    public KhachHangController(KhachHangRepo repo) { this.repo = repo; }

    @GetMapping("/them")
    public String createForm(HttpSession s, Model m) {
        if (!loggedIn(s)) return "redirect:/login";
        m.addAttribute("item", new KhachHang());
        m.addAttribute("pageTitle", "Thêm khách hàng");
        return "khachhang-form";
    }

    @PostMapping("/them")
    public String create(HttpSession s, @ModelAttribute KhachHang item, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";
        normalize(item);
        repo.save(item);
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
    public String update(@PathVariable Integer id, HttpSession s, @ModelAttribute KhachHang form, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";
        KhachHang item = repo.findById(id).orElse(null);
        if (item == null) return missing(ra);
        item.setTenKH(form.getTenKH()); item.setGioiTinh(form.getGioiTinh()); item.setNamSinh(form.getNamSinh());
        item.setSoDienThoai(form.getSoDienThoai()); item.setDiaChi(form.getDiaChi());
        normalize(item); repo.save(item);
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

    private void normalize(KhachHang x) {
        String name = x.getTenKH() == null ? "" : x.getTenKH().trim();
        if (name.isEmpty() || name.length() > 100) throw new IllegalArgumentException("Tên khách hàng không hợp lệ.");
        int year = java.time.Year.now().getValue();
        if (x.getNamSinh() != null && (x.getNamSinh() < 1900 || x.getNamSinh() > year)) throw new IllegalArgumentException("Năm sinh không hợp lệ.");
        String phone = x.getSoDienThoai() == null ? "" : x.getSoDienThoai().trim();
        if (!phone.isEmpty() && !phone.matches("^0\\d{9}$"))
            throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0.");
        if (!phone.isEmpty() && (x.getId() == null ? repo.existsBySoDienThoai(phone) : repo.existsBySoDienThoaiAndIdNot(phone, x.getId())))
            throw new IllegalArgumentException("Số điện thoại đã thuộc về một khách hàng khác.");
        x.setTenKH(name);
        x.setSoDienThoai(phone.isEmpty() ? null : phone);
    }
    private String missing(RedirectAttributes ra) { ra.addFlashAttribute("error", "Không tìm thấy khách hàng."); return "redirect:/khachhang"; }
    private boolean loggedIn(HttpSession s) { return s.getAttribute("user") instanceof TaiKhoan; }
}
