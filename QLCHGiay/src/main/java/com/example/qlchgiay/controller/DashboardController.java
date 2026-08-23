package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.*;
import com.example.qlchgiay.service.WorkSessionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.text.NumberFormat;
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
    private final TaiKhoanRepo taiKhoanRepo;

    public DashboardController(SanPhamRepo sanPhamRepo, KhachHangRepo khachHangRepo,
                               HoaDonRepo hoaDonRepo, NhaCungCapRepo nhaCungCapRepo,
                               LoaiRepo loaiRepo, MauRepo mauRepo, SizeRepo sizeRepo,
                               ChiTietHoaDonRepo chiTietHoaDonRepo,
                               TaiKhoanRepo taiKhoanRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.khachHangRepo = khachHangRepo;
        this.hoaDonRepo = hoaDonRepo;
        this.nhaCungCapRepo = nhaCungCapRepo;
        this.loaiRepo = loaiRepo;
        this.mauRepo = mauRepo;
        this.sizeRepo = sizeRepo;
        this.chiTietHoaDonRepo = chiTietHoaDonRepo;
        this.taiKhoanRepo = taiKhoanRepo;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("userRole", session.getAttribute("userRole"));
        model.addAttribute("productCount", sanPhamRepo.count());
        model.addAttribute("customerCount", khachHangRepo.count());
        var currentEmployee = SessionUserControllerAdvice.currentEmployee(session);
        model.addAttribute(
                "invoiceCount",
                SessionUserControllerAdvice.isAdmin(session)
                        ? hoaDonRepo.count()
                        : currentEmployee == null
                                ? 0
                                : hoaDonRepo.findByMaNhanVienIdOrderByIdDesc(currentEmployee.getId()).size()
        );
        Object workNotifications = session.getAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE);
        if (workNotifications != null) {
            model.addAttribute("workNotifications", workNotifications);
            session.removeAttribute(WorkSessionService.NOTIFICATIONS_ATTRIBUTE);
        }
        Object workSessionTransition = session.getAttribute(
                WorkSessionService.TRANSITION_NOTICE_ATTRIBUTE
        );
        if (workSessionTransition != null) {
            model.addAttribute("workSessionTransition", workSessionTransition);
            session.removeAttribute(WorkSessionService.TRANSITION_NOTICE_ATTRIBUTE);
        }
        if (SessionUserControllerAdvice.isAdmin(session)) {
            model.addAttribute("supplierCount", nhaCungCapRepo.count());
            model.addAttribute(
                    "passwordResetRequests",
                    taiKhoanRepo.findByYeuCauDatLaiMatKhauTrueOrderByTenDangNhapAsc()
            );
        }
        if (SessionUserControllerAdvice.isAdmin(session)) loadAnalytics(model, "6");
        return "dashboard";
    }

    @GetMapping("/sanpham")
    public String sanPham(HttpSession s, Model m) {
        if (!loggedIn(s)) return "redirect:/login";
        boolean employeeView = SessionUserControllerAdvice.isEmployee(s);
        Sort productSort = employeeView
                ? Sort.by(Sort.Order.desc("tonKho"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("id"));
        var items = employeeView
                ? sanPhamRepo.findByTrangThai("ACTIVE", productSort)
                : sanPhamRepo.findAll(productSort);
        List<ProductGroupView> groupedItems = groupProducts(items);
        Comparator<ProductGroupView> groupOrder = employeeView
                ? Comparator.comparingInt(ProductGroupView::getTotalStock).reversed()
                    .thenComparing(ProductGroupView::getRepresentativeId, Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(ProductGroupView::getNewestVariantId, Comparator.nullsLast(Comparator.reverseOrder()));
        groupedItems.sort(groupOrder);
        m.addAttribute("items", groupedItems);
        m.addAttribute(
                "productOrderLabel",
                employeeView
                        ? "Sắp xếp mặc định: tồn kho cao nhất trước."
                        : "Sắp xếp mặc định: sản phẩm mới thêm trước."
        );
        m.addAttribute("availableCount", groupedItems.stream().filter(x -> x.getTotalStock() > 5).count());
        m.addAttribute("lowStockCount", groupedItems.stream().filter(x -> x.getTotalStock() > 0 && x.getTotalStock() <= 5).count());
        m.addAttribute("outOfStockCount", groupedItems.stream().filter(x -> x.getTotalStock() == 0).count());
        m.addAttribute("loaiList", loaiRepo.findAll());
        m.addAttribute("mauList", mauRepo.findAll());
        m.addAttribute("sizeList", sizeRepo.findAll());
        return "sanpham";
    }

    private List<ProductGroupView> groupProducts(List<com.example.qlchgiay.model.SanPham> variants) {
        Map<String, ProductGroupView> groups = new LinkedHashMap<>();
        for (var variant : variants) {
            String key = variant.getTenSP() == null ? "" : variant.getTenSP().trim().toLowerCase(Locale.ROOT);
            groups.computeIfAbsent(key, ignored -> new ProductGroupView()).add(variant);
        }
        return new ArrayList<>(groups.values());
    }

    public static final class ProductGroupView {
        private final List<com.example.qlchgiay.model.SanPham> variants = new ArrayList<>();
        private final Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        private final Set<String> colors = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        private final Set<String> sizes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        private com.example.qlchgiay.model.SanPham representative;
        private BigDecimal minPrice = BigDecimal.ZERO;
        private BigDecimal maxPrice = BigDecimal.ZERO;
        private int totalStock;
        private boolean anyActive;
        private boolean anyInactive;

        private void add(com.example.qlchgiay.model.SanPham variant) {
            if (isBetterRepresentative(variant, representative)) representative = variant;
            variants.add(variant);
            if (variant.getMaLoai() != null && variant.getMaLoai().getTenLoai() != null) {
                categories.add(variant.getMaLoai().getTenLoai());
            }
            if (variant.getMaMau() != null && variant.getMaMau().getTenMau() != null) {
                colors.add(variant.getMaMau().getTenMau());
            }
            if (variant.getMaSize() != null && variant.getMaSize().getTenSize() != null) {
                sizes.add(variant.getMaSize().getTenSize());
            }
            totalStock += variant.getTonKho() == null ? 0 : variant.getTonKho();
            anyActive |= variant.isActive();
            anyInactive |= !variant.isActive();
            if (variant.getGia() != null && (minPrice.signum() == 0 || variant.getGia().compareTo(minPrice) < 0)) {
                minPrice = variant.getGia();
            }
            if (variant.getGia() != null && variant.getGia().compareTo(maxPrice) > 0) {
                maxPrice = variant.getGia();
            }
        }

        public String getName() { return representative == null ? "" : representative.getTenSP(); }
        public Integer getRepresentativeId() { return representative == null ? null : representative.getId(); }
        public Integer getNewestVariantId() {
            return variants.stream().map(com.example.qlchgiay.model.SanPham::getId)
                    .filter(Objects::nonNull).max(Integer::compareTo).orElse(null);
        }
        public String getImage() {
            return variants.stream()
                    .sorted(ProductGroupView::compareRepresentativePriority)
                    .map(com.example.qlchgiay.model.SanPham::getHinhAnh)
                    .filter(image -> image != null && !image.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        public List<com.example.qlchgiay.model.SanPham> getVariants() { return variants; }
        public int getVariantCount() { return variants.size(); }
        public String getCategorySummary() { return String.join(", ", categories); }
        public String getColorSummary() { return String.join(", ", colors); }
        public String getSizeSummary() { return String.join(", ", sizes); }
        public String getSearchText() { return getName() + " " + getCategorySummary() + " " + getColorSummary() + " " + getSizeSummary(); }
        public BigDecimal getSortPrice() { return minPrice; }
        public String getPriceRange() {
            if (minPrice.compareTo(maxPrice) == 0) return money(minPrice);
            return money(minPrice) + " – " + money(maxPrice);
        }
        public int getTotalStock() { return totalStock; }
        public String getStockStatus() { return totalStock == 0 ? "out" : totalStock <= 5 ? "low" : "available"; }
        public String getBusinessStatus() { return anyActive && anyInactive ? "mixed" : anyActive ? "active" : "inactive"; }
        public String getBusinessStatusLabel() { return switch (getBusinessStatus()) {
            case "active" -> "Đang kinh doanh";
            case "mixed" -> "Có biến thể ngừng bán";
            default -> "Ngừng kinh doanh";
        }; }
        private static String money(BigDecimal value) {
            if (value == null) return "0 đ";
            NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
            return format.format(value) + " đ";
        }

        private static boolean isBetterRepresentative(
                com.example.qlchgiay.model.SanPham candidate,
                com.example.qlchgiay.model.SanPham current
        ) {
            return current == null || compareRepresentativePriority(candidate, current) < 0;
        }

        private static int compareRepresentativePriority(
                com.example.qlchgiay.model.SanPham left,
                com.example.qlchgiay.model.SanPham right
        ) {
            int activeComparison = Boolean.compare(right.isActive(), left.isActive());
            if (activeComparison != 0) return activeComparison;
            return Comparator.nullsLast(Integer::compareTo).compare(left.getId(), right.getId());
        }
    }
    @GetMapping("/khachhang") public String khachHang(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; var items=khachHangRepo.findAllByOrderByIdDesc();m.addAttribute("items",items);m.addAttribute("maleCount",items.stream().filter(x->Boolean.TRUE.equals(x.getGioiTinh())).count());m.addAttribute("femaleCount",items.stream().filter(x->Boolean.FALSE.equals(x.getGioiTinh())).count());return "khachhang"; }
    @GetMapping("/hoadon")
    public String hoaDon(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        var employee = SessionUserControllerAdvice.currentEmployee(session);
        var items = SessionUserControllerAdvice.isAdmin(session)
                ? hoaDonRepo.findAllByOrderByIdDesc()
                : employee == null
                        ? List.<com.example.qlchgiay.model.HoaDon>of()
                        : hoaDonRepo.findByMaNhanVienIdOrderByIdDesc(employee.getId());
        model.addAttribute("items", items);
        Set<Integer> editableInvoiceIds = new HashSet<>();
        Object workSessionId = session.getAttribute(WorkSessionService.SESSION_ID_ATTRIBUTE);
        var currentEmployee = employee;
        if (SessionUserControllerAdvice.isEmployee(session)
                && workSessionId instanceof Integer currentSessionId
                && currentEmployee != null
                && currentEmployee.getId() != null) {
            items.stream()
                    .filter(item -> "Chưa thanh toán".equals(item.getTrangThaiHienThi()))
                    .filter(item -> item.getMaPhien() != null
                            && Objects.equals(item.getMaPhien().getId(), currentSessionId))
                    .filter(item -> item.getMaNhanVien() != null
                            && Objects.equals(
                                    item.getMaNhanVien().getId(),
                                    currentEmployee.getId()
                            ))
                    .map(com.example.qlchgiay.model.HoaDon::getId)
                    .filter(Objects::nonNull)
                    .forEach(editableInvoiceIds::add);
        }
        model.addAttribute("editableInvoiceIds", editableInvoiceIds);
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
    @GetMapping("/nhacungcap") public String nhaCungCap(HttpSession s, Model m) { if (!loggedIn(s)) return "redirect:/login"; if (!SessionUserControllerAdvice.isAdmin(s)) throw new org.springframework.security.access.AccessDeniedException("Nhân viên không được xem nhà cung cấp");var items=nhaCungCapRepo.findAllByOrderByIdDesc();m.addAttribute("items",items);m.addAttribute("activeCount",items.stream().filter(x->"Hoạt động".equals(x.getTrangThai())).count());m.addAttribute("inactiveCount",items.stream().filter(x->!"Hoạt động".equals(x.getTrangThai())).count());return "nhacungcap"; }
    @GetMapping("/baocao") public String baoCao(HttpSession s, Model m,
                                                   @RequestParam(defaultValue = "6") String period) {
        if (!SessionUserControllerAdvice.isAdmin(s)) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ quản lý được xem báo cáo");
        }
        loadAnalytics(m, normalizeReportPeriod(period));
        return "baocao";
    }
    @GetMapping("/chatbot") public String chatbot(HttpSession s) { return loggedIn(s) ? "chatbot" : "redirect:/login"; }
    private boolean loggedIn(HttpSession s) {
        return SessionUserControllerAdvice.hasBusinessAccess(s);
    }

    private boolean isCompleted(String status) {
        if (status == null) {
            return false;
        }
        String value = status.toLowerCase();
        return value.contains("đã thanh toán") || value.contains("hoàn thành");
    }

    private void loadAnalytics(Model model, String reportPeriod) {
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

        List<YearMonth> selectedMonths = reportMonths(reportPeriod, currentMonth);
        Set<YearMonth> selectedMonthSet = new HashSet<>(selectedMonths);
        List<com.example.qlchgiay.model.HoaDon> periodInvoices = completedInvoices.stream()
                .filter(invoice -> invoice.getNgayLap() != null
                        && selectedMonthSet.contains(YearMonth.from(invoice.getNgayLap())))
                .toList();
        Set<Integer> periodInvoiceIds = periodInvoices.stream()
                .map(com.example.qlchgiay.model.HoaDon::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        int periodSoldCount = details.stream()
                .filter(detail -> detail.getMaHoaDon() != null
                        && periodInvoiceIds.contains(detail.getMaHoaDon().getId()))
                .mapToInt(detail -> detail.getSoLuong() == null ? 0 : detail.getSoLuong())
                .sum();
        model.addAttribute("periodRevenue", sumRevenue(periodInvoices));
        model.addAttribute("periodSoldCount", periodSoldCount);
        model.addAttribute("periodInvoiceCount", periodInvoices.size());
        for (YearMonth month : selectedMonths) {
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
        model.addAttribute("reportPeriod", reportPeriod);
        model.addAttribute("reportPeriodLabel", switch (reportPeriod) {
            case "12" -> "12 tháng gần nhất";
            case "year" -> "Năm nay (01/01 đến hiện tại)";
            default -> "6 tháng gần nhất";
        });

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
            String productName = detail.getTenSanPhamHienThi();
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

    List<YearMonth> reportMonths(String period, YearMonth currentMonth) {
        if ("year".equals(period)) {
            YearMonth start = YearMonth.of(currentMonth.getYear(), 1);
            return java.util.stream.Stream.iterate(start, month -> month.plusMonths(1))
                    .limit(currentMonth.getMonthValue())
                    .toList();
        }
        int count = "12".equals(period) ? 12 : 6;
        YearMonth start = currentMonth.minusMonths(count - 1L);
        return java.util.stream.Stream.iterate(start, month -> month.plusMonths(1))
                .limit(count)
                .toList();
    }

    private String normalizeReportPeriod(String period) {
        return Set.of("6", "12", "year").contains(period) ? period : "6";
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
