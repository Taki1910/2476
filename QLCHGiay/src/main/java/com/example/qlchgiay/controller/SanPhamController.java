package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChatLieuRepo;
import com.example.qlchgiay.repo.LoaiRepo;
import com.example.qlchgiay.repo.MauRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.SizeRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class SanPhamController {
    private final SanPhamRepo sanPhamRepo;
    private final LoaiRepo loaiRepo;
    private final MauRepo mauRepo;
    private final ChatLieuRepo chatLieuRepo;
    private final SizeRepo sizeRepo;

    public SanPhamController(SanPhamRepo sanPhamRepo, LoaiRepo loaiRepo, MauRepo mauRepo,
                             ChatLieuRepo chatLieuRepo, SizeRepo sizeRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.loaiRepo = loaiRepo;
        this.mauRepo = mauRepo;
        this.chatLieuRepo = chatLieuRepo;
        this.sizeRepo = sizeRepo;
    }

    @GetMapping("/sanpham/them")
    public String showCreate(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        model.addAttribute("sanPham", new SanPham());
        model.addAttribute("pageTitle", "Thêm sản phẩm");
        loadOptions(model);
        return "sanpham-form";
    }

    @PostMapping("/sanpham/them")
    public String create(HttpSession session, @RequestParam String tenSP,
                         @RequestParam(required = false) Integer maLoai,
                         @RequestParam(required = false) Integer maMau,
                         @RequestParam(required = false) Integer maChatLieu,
                         @RequestParam(required = false) Integer maSize,
                         @RequestParam BigDecimal gia, @RequestParam Integer tonKho,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        SanPham sanPham = new SanPham();
        applyForm(sanPham, tenSP, maLoai, maMau, maChatLieu, maSize, gia, tonKho);
        sanPhamRepo.save(sanPham);
        redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm thành công.");
        return "redirect:/sanpham";
    }

    @GetMapping("/sanpham/{id}")
    public String detail(@PathVariable Integer id, HttpSession session, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        model.addAttribute("sanPham", sanPham);
        return "sanpham-detail";
    }

    @GetMapping("/sanpham/sua/{id}")
    public String showUpdate(@PathVariable Integer id, HttpSession session, Model model,
                             RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        model.addAttribute("sanPham", sanPham);
        model.addAttribute("pageTitle", "Cập nhật sản phẩm");
        loadOptions(model);
        return "sanpham-form";
    }

    @PostMapping("/sanpham/sua/{id}")
    public String update(@PathVariable Integer id, HttpSession session,
                         @RequestParam String tenSP,
                         @RequestParam(required = false) Integer maLoai,
                         @RequestParam(required = false) Integer maMau,
                         @RequestParam(required = false) Integer maChatLieu,
                         @RequestParam(required = false) Integer maSize,
                         @RequestParam BigDecimal gia, @RequestParam Integer tonKho,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        applyForm(sanPham, tenSP, maLoai, maMau, maChatLieu, maSize, gia, tonKho);
        sanPhamRepo.save(sanPham);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật sản phẩm thành công.");
        return "redirect:/sanpham";
    }

    @PostMapping("/sanpham/xoa/{id}")
    public String delete(@PathVariable Integer id, HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!sanPhamRepo.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        try {
            sanPhamRepo.deleteById(id);
            sanPhamRepo.flush();
            redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm thành công.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa sản phẩm đã phát sinh hóa đơn hoặc giỏ hàng.");
        }
        return "redirect:/sanpham";
    }

    private void applyForm(SanPham sanPham, String tenSP, Integer maLoai, Integer maMau,
                           Integer maChatLieu, Integer maSize, BigDecimal gia, Integer tonKho) {
        String normalizedName = tenSP == null ? "" : tenSP.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Tên sản phẩm phải có từ 1 đến 100 ký tự.");
        }
        if (gia == null || gia.signum() < 0 || tonKho == null || tonKho < 0) {
            throw new IllegalArgumentException("Giá và số lượng tồn không được âm.");
        }
        sanPham.setTenSP(normalizedName);
        sanPham.setGia(gia);
        sanPham.setTonKho(tonKho);
        sanPham.setMaLoai(maLoai == null ? null : loaiRepo.findById(maLoai).orElseThrow());
        sanPham.setMaMau(maMau == null ? null : mauRepo.findById(maMau).orElseThrow());
        sanPham.setMaChatLieu(maChatLieu == null ? null : chatLieuRepo.findById(maChatLieu).orElseThrow());
        sanPham.setMaSize(maSize == null ? null : sizeRepo.findById(maSize).orElseThrow());
    }

    private void loadOptions(Model model) {
        model.addAttribute("loaiList", loaiRepo.findAll());
        model.addAttribute("mauList", mauRepo.findAll());
        model.addAttribute("chatLieuList", chatLieuRepo.findAll());
        model.addAttribute("sizeList", sizeRepo.findAll());
    }

    private boolean loggedIn(HttpSession session) {
        return session.getAttribute("user") instanceof TaiKhoan;
    }
}
