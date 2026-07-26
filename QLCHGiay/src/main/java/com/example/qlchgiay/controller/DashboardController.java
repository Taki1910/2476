package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Controller
public class DashboardController {
    private final SanPhamRepo sanPhamRepo;
    private final KhachHangRepo khachHangRepo;
    private final HoaDonRepo hoaDonRepo;
    private final NhaCungCapRepo nhaCungCapRepo;
    private final LoaiRepo loaiRepo;
    private final MauRepo mauRepo;
    private final SizeRepo sizeRepo;
    private final ChiTietHoaDonRepo chiTietHoaDonRepo;

    public DashboardController(SanPhamRepo sanPhamRepo, KhachHangRepo khachHangRepo,
                               HoaDonRepo hoaDonRepo, NhaCungCapRepo nhaCungCapRepo,
                               LoaiRepo loaiRepo, MauRepo mauRepo, SizeRepo sizeRepo,
                               ChiTietHoaDonRepo chiTietHoaDonRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.khachHangRepo = khachHangRepo;
        this.hoaDonRepo = hoaDonRepo;
        this.nhaCungCapRepo = nhaCungCapRepo;
        this.loaiRepo = loaiRepo;
        this.mauRepo = mauRepo;
        this.sizeRepo = sizeRepo;
        this.chiTietHoaDonRepo = chiTietHoaDonRepo;
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
        loadAnalytics(model);
        return "dashboard";
    }

    @GetMapping("/sanpham")
    public String sanPham(HttpSession s, Model m) {
        if (!loggedIn(s)) return "redirect:/login";
        var items = sanPhamRepo.findAll();
        m.addAttribute("items", items);
        m.addAttribute("availableCount", items.stream().filter(x -> x.getTonKho() != null && x.getTonKho() > 5).count());
        m.addAttribute("lowStockCount", items.stream().filter(x -> x.getTonKho() != null && x.getTonKho() > 0 && x.getTonKho() <= 5).count());
        m.addAttribute("outOfStockCount", items.stream().filter(x -> x.getTonKho() == null || x.getTonKho() == 0).count());
        m.addAttribute("loaiList", loaiRepo.findAll());
        m.addAttribute("mauList", mauRepo.findAll());
        m.addAttribute("sizeList", sizeRepo.findAll());
        return "sanpham";
    }
    @GetMapping("/khachhang") public String khachHang(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; var items=khachHangRepo.findAll();m.addAttribute("items",items);m.addAttribute("maleCount",items.stream().filter(x->Boolean.TRUE.equals(x.getGioiTinh())).count());m.addAttribute("femaleCount",items.stream().filter(x->Boolean.FALSE.equals(x.getGioiTinh())).count());return "khachhang"; }
    @GetMapping("/hoadon") public String hoaDon(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login";var items=hoaDonRepo.findAll();m.addAttribute("items",items);m.addAttribute("completedCount",items.stream().filter(x->isCompleted(x.getTrangThai())).count());m.addAttribute("pendingCount",items.stream().filter(x->!isCompleted(x.getTrangThai())).count());m.addAttribute("invoiceRevenue",items.stream().filter(x->isCompleted(x.getTrangThai())).map(x->x.getTongTien()==null?BigDecimal.ZERO:x.getTongTien()).reduce(BigDecimal.ZERO,BigDecimal::add));return "hoadon"; }
    @GetMapping("/nhacungcap") public String nhaCungCap(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login";var items=nhaCungCapRepo.findAll();m.addAttribute("items",items);m.addAttribute("activeCount",items.stream().filter(x->x.getTrangThai()==null||x.getTrangThai().toLowerCase().contains("hoạt")).count());m.addAttribute("inactiveCount",items.stream().filter(x->x.getTrangThai()!=null&&!x.getTrangThai().toLowerCase().contains("hoạt")).count());return "nhacungcap"; }
    @GetMapping("/baocao") public String baoCao(HttpSession s,Model m) {if(!loggedIn(s))return "redirect:/login";loadAnalytics(m);return "baocao";}
    @GetMapping("/chatbot") public String chatbot(HttpSession s) { return loggedIn(s) ? "chatbot" : "redirect:/login"; }
    private boolean loggedIn(HttpSession s) { return s.getAttribute("user") instanceof TaiKhoan; }
    private boolean isCompleted(String s){if(s==null)return false;String v=s.toLowerCase();return v.contains("đã thanh toán")||v.contains("hoàn thành");}
    private void loadAnalytics(Model model){
        var invoices=hoaDonRepo.findAll();var details=chiTietHoaDonRepo.findAll();LocalDate today=LocalDate.now();
        BigDecimal total=invoices.stream().filter(x->isCompleted(x.getTrangThai())).map(x->x.getTongTien()==null?BigDecimal.ZERO:x.getTongTien()).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal todayRevenue=invoices.stream().filter(x->today.equals(x.getNgayLap())&&isCompleted(x.getTrangThai())).map(x->x.getTongTien()==null?BigDecimal.ZERO:x.getTongTien()).reduce(BigDecimal.ZERO,BigDecimal::add);
        int sold=details.stream().mapToInt(x->x.getSoLuong()==null?0:x.getSoLuong()).sum();
        model.addAttribute("totalRevenue",total);model.addAttribute("todayRevenue",todayRevenue);model.addAttribute("soldCount",sold);model.addAttribute("invoiceCount",invoices.size());model.addAttribute("customerCount",khachHangRepo.count());
        model.addAttribute("recentInvoices",invoices.stream().sorted(Comparator.comparing(com.example.qlchgiay.model.HoaDon::getNgayLap,Comparator.nullsLast(Comparator.reverseOrder()))).limit(5).toList());
        model.addAttribute("lowStockProducts",sanPhamRepo.findTop5ByTonKhoLessThanEqualOrderByTonKhoAsc(5));
        List<String> labels=new ArrayList<>();List<BigDecimal> revenues=new ArrayList<>();List<Map<String,Object>> reports=new ArrayList<>();
        for(int i=5;i>=0;i--){YearMonth ym=YearMonth.now().minusMonths(i);labels.add("T"+ym.getMonthValue()+"/"+ym.getYear());var monthly=invoices.stream().filter(x->x.getNgayLap()!=null&&YearMonth.from(x.getNgayLap()).equals(ym)&&isCompleted(x.getTrangThai())).toList();BigDecimal rev=monthly.stream().map(x->x.getTongTien()==null?BigDecimal.ZERO:x.getTongTien()).reduce(BigDecimal.ZERO,BigDecimal::add);revenues.add(rev);int qty=details.stream().filter(d->d.getMaHoaDon()!=null&&monthly.stream().anyMatch(h->h.getId().equals(d.getMaHoaDon().getId()))).mapToInt(d->d.getSoLuong()==null?0:d.getSoLuong()).sum();Map<String,Object> row=new LinkedHashMap<>();row.put("period",labels.get(labels.size()-1));row.put("orders",monthly.size());row.put("quantity",qty);row.put("revenue",rev);reports.add(0,row);}
        model.addAttribute("monthLabels",labels);model.addAttribute("monthRevenues",revenues);model.addAttribute("monthlyReports",reports);
        BigDecimal maxRevenue=revenues.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);List<Map<String,Object>> chartRows=new ArrayList<>();for(int i=0;i<labels.size();i++){Map<String,Object> row=new LinkedHashMap<>();row.put("label",labels.get(i));row.put("revenue",revenues.get(i));row.put("height",maxRevenue.signum()==0?0:revenues.get(i).multiply(BigDecimal.valueOf(100)).divide(maxRevenue,0,java.math.RoundingMode.HALF_UP));chartRows.add(row);}model.addAttribute("chartRows",chartRows);
        Map<String,Integer> sales=new HashMap<>();for(var d:details){String name=d.getMaChiTietSP()!=null&&d.getMaChiTietSP().getMaSP()!=null?d.getMaChiTietSP().getMaSP().getTenSP():"Khác";sales.merge(name,d.getSoLuong()==null?0:d.getSoLuong(),Integer::sum);}model.addAttribute("topProducts",sales.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).toList());
        Map<String,Long> categories=new LinkedHashMap<>();for(var p:sanPhamRepo.findAll()){String name=p.getMaLoai()==null?"Chưa phân loại":p.getMaLoai().getTenLoai();categories.merge(name,1L,Long::sum);}model.addAttribute("categoryLabels",categories.keySet());model.addAttribute("categoryValues",categories.values());
    }
}
