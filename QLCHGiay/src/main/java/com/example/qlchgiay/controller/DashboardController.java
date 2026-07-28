package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.*;
import com.example.qlchgiay.service.WorkSessionService;
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
        Object workNotifications = session.getAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE);
        if (workNotifications != null) {
            model.addAttribute("workNotifications", workNotifications);
            session.removeAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE);
        }
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
    @GetMapping("/hoadon")
    public String hoaDon(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        var items = hoaDonRepo.findAll();
        model.addAttribute("items", items);
        model.addAttribute(
                "paidCount",
                items.stream().filter(item -> "Đã thanh toán".equals(item.getTrangThaiHienThi())).count()
        );
        model.addAttribute(
                "unpaidCount",
                items.stream().filter(item -> "Chưa thanh toán".equals(item.getTrangThaiHienThi())).count()
        );
        model.addAttribute(
                "invoiceRevenue",
                items.stream()
                        .filter(item -> "Đã thanh toán".equals(item.getTrangThaiHienThi()))
                        .map(item -> item.getTongTien() == null ? BigDecimal.ZERO : item.getTongTien())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
        return "hoadon";
    }
    @GetMapping("/nhacungcap") public String nhaCungCap(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login";var items=nhaCungCapRepo.findAll();m.addAttribute("items",items);m.addAttribute("activeCount",items.stream().filter(x->x.getTrangThai()==null||x.getTrangThai().toLowerCase().contains("hoạt")).count());m.addAttribute("inactiveCount",items.stream().filter(x->x.getTrangThai()!=null&&!x.getTrangThai().toLowerCase().contains("hoạt")).count());return "nhacungcap"; }
    @GetMapping("/baocao") public String baoCao(HttpSession s,Model m) {if(!loggedIn(s))return "redirect:/login";loadAnalytics(m);return "baocao";}
    @GetMapping("/chatbot") public String chatbot(HttpSession s) { return loggedIn(s) ? "chatbot" : "redirect:/login"; }
    private boolean loggedIn(HttpSession s) {
        return s.getAttribute("user") instanceof TaiKhoan;
    }

    private boolean isCompleted(String status) {
        if (status == null) {
            return false;
        }
        String value = status.toLowerCase();
        return value.contains("đã thanh toán") || value.contains("hoàn thành");
    }

    private void loadAnalytics(Model model) {
        var invoices = hoaDonRepo.findAll();
        var details = chiTietHoaDonRepo.findAll();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        var completedInvoices = invoices.stream()
                .filter(invoice -> isCompleted(invoice.getTrangThai()))
                .toList();
        BigDecimal totalRevenue = completedInvoices.stream()
                .map(invoice -> safeMoney(invoice.getTongTien()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayRevenue = completedInvoices.stream()
                .filter(invoice -> today.equals(invoice.getNgayLap()))
                .map(invoice -> safeMoney(invoice.getTongTien()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int soldCount = details.stream()
                .filter(detail -> detail.getMaHoaDon() != null
                        && isCompleted(detail.getMaHoaDon().getTrangThai()))
                .mapToInt(detail -> detail.getSoLuong() == null ? 0 : detail.getSoLuong())
                .sum();

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("todayRevenue", todayRevenue);
        model.addAttribute("soldCount", soldCount);
        model.addAttribute("invoiceCount", invoices.size());
        model.addAttribute("customerCount", khachHangRepo.count());
        model.addAttribute("currentYear", today.getYear());
        model.addAttribute(
                "recentInvoices",
                invoices.stream()
                        .sorted(Comparator.comparing(
                                com.example.qlchgiay.model.HoaDon::getNgayLap,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                        .limit(5)
                        .toList()
        );
        model.addAttribute(
                "lowStockProducts",
                sanPhamRepo.findTop5ByTonKhoLessThanEqualOrderByTonKhoAsc(5)
        );

        List<String> monthLabels = new ArrayList<>();
        List<BigDecimal> monthRevenues = new ArrayList<>();
        List<Map<String, Object>> monthlyReports = new ArrayList<>();

        for (int offset = 5; offset >= 0; offset--) {
            YearMonth month = currentMonth.minusMonths(offset);
            List<com.example.qlchgiay.model.HoaDon> monthlyInvoices =
                    invoicesForMonth(completedInvoices, month);
            BigDecimal revenue = sumRevenue(monthlyInvoices);
            String label = "T" + month.getMonthValue() + "/" + month.getYear();

            monthLabels.add(label);
            monthRevenues.add(revenue);

            Set<Integer> invoiceIds = monthlyInvoices.stream()
                    .map(com.example.qlchgiay.model.HoaDon::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            int quantity = details.stream()
                    .filter(detail -> detail.getMaHoaDon() != null
                            && invoiceIds.contains(detail.getMaHoaDon().getId()))
                    .mapToInt(detail -> detail.getSoLuong() == null ? 0 : detail.getSoLuong())
                    .sum();

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("period", label);
            report.put("orders", monthlyInvoices.size());
            report.put("quantity", quantity);
            report.put("revenue", revenue);
            monthlyReports.add(0, report);
        }

        List<String> yearLabels = new ArrayList<>();
        List<BigDecimal> yearRevenues = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            YearMonth month = YearMonth.of(today.getYear(), monthNumber);
            yearLabels.add("T" + monthNumber);
            yearRevenues.add(sumRevenue(invoicesForMonth(completedInvoices, month)));
        }

        model.addAttribute("monthLabels", monthLabels);
        model.addAttribute("monthRevenues", monthRevenues);
        model.addAttribute("yearLabels", yearLabels);
        model.addAttribute("yearRevenues", yearRevenues);
        model.addAttribute("monthlyReports", monthlyReports);

        BigDecimal maxRevenue = monthRevenues.stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        List<Map<String, Object>> chartRows = new ArrayList<>();
        for (int index = 0; index < monthLabels.size(); index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", monthLabels.get(index));
            row.put("revenue", monthRevenues.get(index));
            row.put(
                    "height",
                    maxRevenue.signum() == 0
                            ? 0
                            : monthRevenues.get(index)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(maxRevenue, 0, java.math.RoundingMode.HALF_UP)
            );
            chartRows.add(row);
        }
        model.addAttribute("chartRows", chartRows);

        Map<String, Integer> productSales = new HashMap<>();
        for (var detail : details) {
            if (detail.getMaHoaDon() == null
                    || !isCompleted(detail.getMaHoaDon().getTrangThai())) {
                continue;
            }
            String productName = detail.getMaChiTietSP() != null
                    && detail.getMaChiTietSP().getMaSP() != null
                    ? detail.getMaChiTietSP().getMaSP().getTenSP()
                    : "Khác";
            productSales.merge(
                    productName,
                    detail.getSoLuong() == null ? 0 : detail.getSoLuong(),
                    Integer::sum
            );
        }
        List<Map.Entry<String, Integer>> rankedProducts = productSales.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .toList();
        int productSalesTotal = productSales.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int topProductQuantity = rankedProducts.isEmpty() ? 0 : rankedProducts.get(0).getValue();
        List<Map<String, Object>> productComparison = new ArrayList<>();
        for (int index = 0; index < rankedProducts.size(); index++) {
            Map.Entry<String, Integer> product = rankedProducts.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", index + 1);
            row.put("name", product.getKey());
            row.put("quantity", product.getValue());
            row.put(
                    "share",
                    productSalesTotal == 0
                            ? 0
                            : Math.round(product.getValue() * 100.0 / productSalesTotal)
            );
            row.put(
                    "relative",
                    topProductQuantity == 0
                            ? 0
                            : Math.round(product.getValue() * 100.0 / topProductQuantity)
            );
            productComparison.add(row);
        }
        model.addAttribute("productSalesTotal", productSalesTotal);
        model.addAttribute("productComparison", productComparison);

        Map<String, Long> categories = new LinkedHashMap<>();
        for (var product : sanPhamRepo.findAll()) {
            String categoryName = product.getMaLoai() == null
                    ? "Chưa phân loại"
                    : product.getMaLoai().getTenLoai();
            categories.merge(categoryName, 1L, Long::sum);
        }
        model.addAttribute("categoryLabels", categories.keySet());
        model.addAttribute("categoryValues", categories.values());
    }

    private List<com.example.qlchgiay.model.HoaDon> invoicesForMonth(
            List<com.example.qlchgiay.model.HoaDon> invoices,
            YearMonth month
    ) {
        return invoices.stream()
                .filter(invoice -> invoice.getNgayLap() != null
                        && YearMonth.from(invoice.getNgayLap()).equals(month))
                .toList();
    }

    private BigDecimal sumRevenue(List<com.example.qlchgiay.model.HoaDon> invoices) {
        return invoices.stream()
                .map(invoice -> safeMoney(invoice.getTongTien()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
