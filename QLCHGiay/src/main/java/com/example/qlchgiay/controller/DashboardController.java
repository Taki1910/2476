package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final SanPhamRepo sanPhamRepo;
    private final KhachHangRepo khachHangRepo;
    private final HoaDonRepo hoaDonRepo;
    private final NhaCungCapRepo nhaCungCapRepo;

    public DashboardController(SanPhamRepo sanPhamRepo, KhachHangRepo khachHangRepo,
                               HoaDonRepo hoaDonRepo, NhaCungCapRepo nhaCungCapRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.khachHangRepo = khachHangRepo;
        this.hoaDonRepo = hoaDonRepo;
        this.nhaCungCapRepo = nhaCungCapRepo;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("userRole", session.getAttribute("userRole"));
        model.addAttribute("productCount", sanPhamRepo.count());
        model.addAttribute("customerCount", khachHangRepo.count());
        model.addAttribute("invoiceCount", hoaDonRepo.count());
        model.addAttribute("supplierCount", nhaCungCapRepo.count());
        return "dashboard";
    }

    @GetMapping("/sanpham")
    public String sanPham(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("items", sanPhamRepo.findAll()); return "sanpham"; }
    @GetMapping("/kho")
    public String kho(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("items", sanPhamRepo.findAll()); return "kho"; }
    @GetMapping("/khachhang")
    public String khachHang(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("items", khachHangRepo.findAll()); return "khachhang"; }
    @GetMapping("/hoadon")
    public String hoaDon(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("items", hoaDonRepo.findAll()); return "hoadon"; }
    @GetMapping("/nhacungcap")
    public String nhaCungCap(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; m.addAttribute("items", nhaCungCapRepo.findAll()); return "nhacungcap"; }
    @GetMapping("/baocao") public String baoCao(HttpSession s) { return loggedIn(s) ? "baocao" : "redirect:/login"; }
    @GetMapping("/chatbot") public String chatbot(HttpSession s) { return loggedIn(s) ? "chatbot" : "redirect:/login"; }
    private boolean loggedIn(HttpSession s) { return s.getAttribute("user") instanceof TaiKhoan; }
}
