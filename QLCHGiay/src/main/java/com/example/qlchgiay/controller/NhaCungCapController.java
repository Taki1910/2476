package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.NhaCungCap;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/nhacungcap")
public class NhaCungCapController {
    private final NhaCungCapRepo repo;
    public NhaCungCapController(NhaCungCapRepo repo) { this.repo = repo; }

    @GetMapping("/them") public String createForm(HttpSession s, Model m) {
        if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("item", new NhaCungCap()); m.addAttribute("pageTitle", "Thêm nhà cung cấp"); return "nhacungcap-form";
    }
    @PostMapping("/them") public String create(HttpSession s, @ModelAttribute NhaCungCap item, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login"; normalize(item); repo.save(item); ra.addFlashAttribute("success", "Đã thêm nhà cung cấp."); return "redirect:/nhacungcap";
    }
    @GetMapping("/{id}") public String detail(@PathVariable Integer id, HttpSession s, Model m, RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login"; NhaCungCap x=repo.findById(id).orElse(null); if(x==null)return missing(ra); m.addAttribute("item",x); return "nhacungcap-detail";
    }
    @GetMapping("/sua/{id}") public String updateForm(@PathVariable Integer id,HttpSession s,Model m,RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login"; NhaCungCap x=repo.findById(id).orElse(null); if(x==null)return missing(ra); m.addAttribute("item",x);m.addAttribute("pageTitle","Cập nhật nhà cung cấp");return "nhacungcap-form";
    }
    @PostMapping("/sua/{id}") public String update(@PathVariable Integer id,HttpSession s,@ModelAttribute NhaCungCap f,RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login"; NhaCungCap x=repo.findById(id).orElse(null);if(x==null)return missing(ra);
        x.setTenNCC(f.getTenNCC());x.setSoDienThoai(f.getSoDienThoai());x.setEmail(f.getEmail());x.setDiaChi(f.getDiaChi());x.setTrangThai(f.getTrangThai());normalize(x);repo.save(x);ra.addFlashAttribute("success","Đã cập nhật nhà cung cấp.");return "redirect:/nhacungcap";
    }
    @PostMapping("/xoa/{id}") public String delete(@PathVariable Integer id,HttpSession s,RedirectAttributes ra) {
        if (!loggedIn(s)) return "redirect:/login";try{repo.deleteById(id);repo.flush();ra.addFlashAttribute("success","Đã xóa nhà cung cấp.");}catch(DataIntegrityViolationException ex){ra.addFlashAttribute("error","Không thể xóa nhà cung cấp đang gắn với sản phẩm.");}return "redirect:/nhacungcap";
    }
    private void normalize(NhaCungCap x){String n=x.getTenNCC()==null?"":x.getTenNCC().trim();if(n.isEmpty()||n.length()>100)throw new IllegalArgumentException("Tên nhà cung cấp không hợp lệ.");x.setTenNCC(n);if(x.getTrangThai()==null||x.getTrangThai().isBlank())x.setTrangThai("Hoạt động");}
    private String missing(RedirectAttributes ra){ra.addFlashAttribute("error","Không tìm thấy nhà cung cấp.");return "redirect:/nhacungcap";}
    private boolean loggedIn(HttpSession s){return s.getAttribute("user") instanceof TaiKhoan;}
}
