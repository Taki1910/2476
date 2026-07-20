package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.HoaDon;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.DonHangRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/hoadon")
public class HoaDonController {
    private final HoaDonRepo repo;
    private final DonHangRepo donHangRepo;
    private final NhanVienRepo nhanVienRepo;
    public HoaDonController(HoaDonRepo repo, DonHangRepo donHangRepo, NhanVienRepo nhanVienRepo) {
        this.repo=repo; this.donHangRepo=donHangRepo; this.nhanVienRepo=nhanVienRepo;
    }
    @GetMapping("/them") public String createForm(HttpSession s,Model m){if(!loggedIn(s))return "redirect:/login";HoaDon x=new HoaDon();x.setNgayLap(LocalDate.now());x.setTrangThai("Chờ thanh toán");m.addAttribute("item",x);load(m,"Tạo hóa đơn");return "hoadon-form";}
    @PostMapping("/them") public String create(HttpSession s,@RequestParam(required=false)Integer maDonHang,@RequestParam(required=false)Integer maNhanVien,@RequestParam LocalDate ngayLap,@RequestParam BigDecimal tongTien,@RequestParam String trangThai,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";HoaDon x=new HoaDon();apply(x,maDonHang,maNhanVien,ngayLap,tongTien,trangThai);repo.save(x);ra.addFlashAttribute("success","Đã tạo hóa đơn.");return "redirect:/hoadon";}
    @GetMapping("/{id}") public String detail(@PathVariable Integer id,HttpSession s,Model m,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";HoaDon x=repo.findById(id).orElse(null);if(x==null)return missing(ra);m.addAttribute("item",x);return "hoadon-detail";}
    @GetMapping("/in/{id}") public String print(@PathVariable Integer id,HttpSession s,Model m,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";HoaDon x=repo.findById(id).orElse(null);if(x==null)return missing(ra);m.addAttribute("item",x);m.addAttribute("printMode",true);return "hoadon-detail";}
    @GetMapping("/sua/{id}") public String updateForm(@PathVariable Integer id,HttpSession s,Model m,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";HoaDon x=repo.findById(id).orElse(null);if(x==null)return missing(ra);m.addAttribute("item",x);load(m,"Cập nhật hóa đơn");return "hoadon-form";}
    @PostMapping("/sua/{id}") public String update(@PathVariable Integer id,HttpSession s,@RequestParam(required=false)Integer maDonHang,@RequestParam(required=false)Integer maNhanVien,@RequestParam LocalDate ngayLap,@RequestParam BigDecimal tongTien,@RequestParam String trangThai,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";HoaDon x=repo.findById(id).orElse(null);if(x==null)return missing(ra);apply(x,maDonHang,maNhanVien,ngayLap,tongTien,trangThai);repo.save(x);ra.addFlashAttribute("success","Đã cập nhật hóa đơn.");return "redirect:/hoadon";}
    @PostMapping("/xoa/{id}") public String delete(@PathVariable Integer id,HttpSession s,RedirectAttributes ra){if(!loggedIn(s))return "redirect:/login";try{repo.deleteById(id);repo.flush();ra.addFlashAttribute("success","Đã xóa hóa đơn.");}catch(DataIntegrityViolationException ex){ra.addFlashAttribute("error","Không thể xóa hóa đơn đã có chi tiết hoặc thanh toán.");}return "redirect:/hoadon";}
    private void apply(HoaDon x,Integer dh,Integer nv,LocalDate date,BigDecimal total,String status){if(date==null||total==null||total.signum()<0)throw new IllegalArgumentException("Ngày lập và tổng tiền không hợp lệ.");x.setMaDonHang(dh==null?null:donHangRepo.findById(dh).orElseThrow());x.setMaNhanVien(nv==null?null:nhanVienRepo.findById(nv).orElseThrow());x.setNgayLap(date);x.setTongTien(total);x.setTrangThai(status==null||status.isBlank()?"Chờ thanh toán":status.trim());}
    private void load(Model m,String title){m.addAttribute("pageTitle",title);m.addAttribute("donHangList",donHangRepo.findAll());m.addAttribute("nhanVienList",nhanVienRepo.findAll());}
    private String missing(RedirectAttributes ra){ra.addFlashAttribute("error","Không tìm thấy hóa đơn.");return "redirect:/hoadon";}
    private boolean loggedIn(HttpSession s){return s.getAttribute("user") instanceof TaiKhoan;}
}
