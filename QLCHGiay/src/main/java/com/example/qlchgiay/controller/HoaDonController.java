package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.*;
import com.example.qlchgiay.repo.*;
import com.example.qlchgiay.service.WorkSessionService;
import com.example.qlchgiay.service.KhuyenMaiService;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/hoadon")
public class HoaDonController {
    private static final String UNPAID_STATUS = "Chưa thanh toán";
    private static final String PAID_STATUS = "Đã thanh toán";
    private static final String CANCELLED_STATUS = "Đã hủy";

    private final HoaDonRepo hoaDonRepo;
    private final DonHangRepo donHangRepo;
    private final NhanVienRepo nhanVienRepo;
    private final KhachHangRepo khachHangRepo;
    private final SanPhamRepo sanPhamRepo;
    private final ChiTietSanPhamRepo chiTietSanPhamRepo;
    private final ChiTietHoaDonRepo chiTietHoaDonRepo;
    private final ThanhToanRepo thanhToanRepo;
    private final LichSuChinhSuaHoaDonRepo invoiceHistoryRepo;
    private final WorkSessionService workSessionService;
    private final KhuyenMaiService promotionService;

    public HoaDonController(HoaDonRepo hoaDonRepo, DonHangRepo donHangRepo, NhanVienRepo nhanVienRepo,
                            KhachHangRepo khachHangRepo, SanPhamRepo sanPhamRepo,
                            ChiTietSanPhamRepo chiTietSanPhamRepo, ChiTietHoaDonRepo chiTietHoaDonRepo,
                            ThanhToanRepo thanhToanRepo,
                            LichSuChinhSuaHoaDonRepo invoiceHistoryRepo,
                            WorkSessionService workSessionService,
                            KhuyenMaiService promotionService) {
        this.hoaDonRepo = hoaDonRepo;
        this.donHangRepo = donHangRepo;
        this.nhanVienRepo = nhanVienRepo;
        this.khachHangRepo = khachHangRepo;
        this.sanPhamRepo = sanPhamRepo;
        this.chiTietSanPhamRepo = chiTietSanPhamRepo;
        this.chiTietHoaDonRepo = chiTietHoaDonRepo;
        this.thanhToanRepo = thanhToanRepo;
        this.invoiceHistoryRepo = invoiceHistoryRepo;
        this.workSessionService = workSessionService;
        this.promotionService = promotionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleInvalidInput(
            IllegalArgumentException exception,
            RedirectAttributes redirect
    ) {
        redirect.addFlashAttribute("error", exception.getMessage());
        return "redirect:/hoadon";
    }

    @GetMapping("/them")
    public String createForm(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = new HoaDon();
        item.setNgayLap(LocalDate.now());
        item.setTrangThai(UNPAID_STATUS);
        if (SessionUserControllerAdvice.isEmployee(session)) {
            item.setMaNhanVien(SessionUserControllerAdvice.currentEmployee(session));
        }
        model.addAttribute("item", item);
        model.addAttribute("details", List.of());
        loadForm(model, "Tạo hóa đơn bán hàng", session);
        return "hoadon-form";
    }

    @PostMapping("/them")
    @Transactional
    public String create(HttpSession session, InvoiceForm form, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon invoice = new HoaDon();
        if (SessionUserControllerAdvice.isEmployee(session)) {
            invoice.setMaPhien(workSessionService.currentSession(session).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Không tìm thấy phiên làm việc hiện tại. Vui lòng đăng nhập lại."
                    )
            ));
        }
        form.trangThai = UNPAID_STATUS;
        saveInvoice(invoice, form, session);
        if (form.taoKhachMoi && SessionUserControllerAdvice.isEmployee(session)) {
            workSessionService.recordCustomerCreated(session);
        }
        redirect.addFlashAttribute("success", "Đã tạo hóa đơn với trạng thái Chưa thanh toán.");
        return "redirect:/hoadon/" + invoice.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id,
                         @RequestParam(name = "print", defaultValue = "false") boolean printMode,
                         HttpSession session, Model model, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = hoaDonRepo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        model.addAttribute("item", item);
        model.addAttribute("details", chiTietHoaDonRepo.findByMaHoaDonId(id));
        model.addAttribute(
                "history",
                printMode
                        ? List.of()
                        : invoiceHistoryRepo.findByMaHoaDonIdOrderByThoiGianDesc(id)
        );
        model.addAttribute(
                "canEdit",
                !PAID_STATUS.equals(item.getTrangThaiHienThi())
                        && canEditInvoice(item, session)
        );
        model.addAttribute("printMode", printMode);
        return "hoadon-detail";
    }

    @PostMapping("/thanh-toan/{id}")
    @Transactional
    public String pay(@PathVariable Integer id, HttpSession session, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = hoaDonRepo.findByIdForUpdate(id).orElse(null);
        if (item == null) return missing(redirect);
        if (PAID_STATUS.equals(item.getTrangThaiHienThi())) {
            redirect.addFlashAttribute("success", "Hóa đơn đã được thanh toán trước đó.");
            return "redirect:/hoadon/" + id;
        }
        if (CANCELLED_STATUS.equals(item.getTrangThaiHienThi())) {
            throw new IllegalArgumentException("Không thể thanh toán hóa đơn đã hủy.");
        }

        List<ChiTietHoaDon> details = chiTietHoaDonRepo.findByMaHoaDonId(id);
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn không có sản phẩm để thanh toán.");
        }
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (ChiTietHoaDon detail : details) {
            if (detail.getMaChiTietSP() == null || detail.getMaChiTietSP().getMaSP() == null) {
                throw new IllegalArgumentException("Hóa đơn có sản phẩm không hợp lệ.");
            }
            int quantity = detail.getSoLuong() == null ? 0 : detail.getSoLuong();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm trong hóa đơn không hợp lệ.");
            }
            quantities.merge(detail.getMaChiTietSP().getMaSP().getId(), quantity, Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            SanPham product = sanPhamRepo.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại."));
            int stock = product.getTonKho() == null ? 0 : product.getTonKho();
            if (entry.getValue() > stock) {
                throw new IllegalArgumentException(
                        "Sản phẩm " + product.getTenSP() + " không đủ tồn kho."
                );
            }
            product.setTonKho(stock - entry.getValue());
            sanPhamRepo.save(product);
        }

        item.setTrangThai(PAID_STATUS);
        hoaDonRepo.save(item);
        if (item.getMaDonHang() != null) {
            item.getMaDonHang().setTrangThai(PAID_STATUS);
            donHangRepo.save(item.getMaDonHang());
        }

        if (!thanhToanRepo.existsByMaHoaDonId(id)) {
            ThanhToan payment = new ThanhToan();
            payment.setMaHoaDon(item);
            payment.setPhuongThuc("Tiền mặt");
            payment.setNgayThanhToan(LocalDate.now());
            payment.setSoTien(item.getTongTien());
            payment.setTrangThai("Thành công");
            thanhToanRepo.save(payment);
        }

        if (SessionUserControllerAdvice.isEmployee(session)) {
            int productQuantity = quantities.values().stream().mapToInt(Integer::intValue).sum();
            workSessionService.recordPaidSale(session, productQuantity, item.getTongTien());
        }
        redirect.addFlashAttribute("success", "Đã thanh toán và cập nhật tồn kho.");
        return "redirect:/hoadon/" + id;
    }

    @GetMapping("/sua/{id}")
    public String updateForm(@PathVariable Integer id, HttpSession session, Model model, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = hoaDonRepo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        if (!canEditInvoice(item, session)) return editDenied(redirect);
        if (PAID_STATUS.equals(item.getTrangThaiHienThi())) {
            return paidEditDenied(redirect, id);
        }
        model.addAttribute("item", item);
        model.addAttribute("details", chiTietHoaDonRepo.findByMaHoaDonId(id));
        loadForm(model, "Cập nhật hóa đơn", session);
        return "hoadon-form";
    }

    @PostMapping("/sua/{id}")
    @Transactional
    public String update(@PathVariable Integer id, HttpSession session, InvoiceForm form, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon invoice = hoaDonRepo.findByIdForUpdate(id).orElse(null);
        if (invoice == null) return missing(redirect);
        if (!canEditInvoice(invoice, session)) return editDenied(redirect);
        if (PAID_STATUS.equals(invoice.getTrangThaiHienThi())) {
            return paidEditDenied(redirect, id);
        }
        String before = invoiceSnapshot(
                invoice,
                chiTietHoaDonRepo.findByMaHoaDonId(id)
        );
        saveInvoice(invoice, form, session);
        String after = invoiceSnapshot(
                invoice,
                chiTietHoaDonRepo.findByMaHoaDonId(id)
        );
        recordEdit(invoice, session, before, after);
        redirect.addFlashAttribute("success", "Đã cập nhật hóa đơn.");
        return "redirect:/hoadon/" + id;
    }

    @PostMapping("/xoa/{id}")
    @Transactional
    public String delete(@PathVariable Integer id, HttpSession session, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        if (SessionUserControllerAdvice.isEmployee(session)
                && workSessionService.currentSession(session).isEmpty()) {
            redirect.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền xóa hóa đơn."
            );
            return "redirect:/hoadon";
        }
        HoaDon invoice = hoaDonRepo.findByIdForUpdate(id).orElse(null);
        if (invoice == null) {
            return missing(redirect);
        }
        if (!canEditInvoice(invoice, session)) {
            redirect.addFlashAttribute(
                    "error",
                    "Nhân viên chỉ được xóa hóa đơn do mình tạo trong phiên làm việc hiện tại."
            );
            return "redirect:/hoadon";
        }
        if (PAID_STATUS.equals(invoice.getTrangThaiHienThi())
                || thanhToanRepo.existsByMaHoaDonId(id)) {
            redirect.addFlashAttribute("error", "Không thể xóa hóa đơn đã thanh toán.");
            return "redirect:/hoadon/" + id;
        }
        chiTietHoaDonRepo.deleteByMaHoaDonId(id);
        hoaDonRepo.delete(invoice);
        hoaDonRepo.flush();
        redirect.addFlashAttribute("success", "Đã xóa hóa đơn.");
        return "redirect:/hoadon";
    }

    private void saveInvoice(HoaDon invoice, InvoiceForm form, HttpSession session) {
        validateForm(form);
        String normalizedStatus = normalizeInvoiceStatus(form.trangThai);
        LocalDate invoiceDate = invoice.getId() == null
                ? LocalDate.now()
                : Optional.ofNullable(invoice.getNgayLap()).orElse(LocalDate.now());
        KhachHang customer = resolveCustomer(form, invoice);
        NhanVien employee = resolveEmployee(session, form.maNhanVien);

        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (int i = 0; i < form.sanPhamIds.size(); i++) {
            Integer productId = form.sanPhamIds.get(i);
            Integer quantity = i < form.soLuongs.size() ? form.soLuongs.get(i) : null;
            if (productId != null && quantity != null && quantity > 0) quantities.merge(productId, quantity, Integer::sum);
        }
        if (quantities.isEmpty()) throw new IllegalArgumentException("Hóa đơn phải có ít nhất một sản phẩm.");

        List<SanPham> products = quantities.keySet().stream().map(id -> sanPhamRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại."))).toList();
        Map<Integer, KhuyenMaiService.PriceQuote> quotes = promotionService.quoteProducts(
                products, LocalDateTime.now()
        );
        BigDecimal total = BigDecimal.ZERO;
        for (SanPham product : products) {
            int quantity = quantities.get(product.getId());
            if (product.getGia() == null) throw new IllegalArgumentException("Sản phẩm chưa có giá bán.");
            int stock = product.getTonKho() == null ? 0 : product.getTonKho();
            if (quantity > stock)
                throw new IllegalArgumentException("Sản phẩm " + product.getTenSP() + " không đủ tồn kho.");
            KhuyenMaiService.PriceQuote quote = quotes.get(product.getId());
            total = total.add(quote.finalPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        DonHang order = invoice.getMaDonHang();
        if (order == null) order = new DonHang();
        order.setMaKH(customer);
        order.setMaNhanVien(employee);
        order.setNgayDatHang(invoiceDate);
        order.setTongTien(total);
        order.setTrangThai(normalizedStatus);
        donHangRepo.save(order);

        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(employee);
        invoice.setNgayLap(invoiceDate);
        invoice.setTongTien(total);
        invoice.setTrangThai(normalizedStatus);
        hoaDonRepo.save(invoice);

        if (invoice.getId() != null) chiTietHoaDonRepo.deleteByMaHoaDonId(invoice.getId());
        for (SanPham product : products) {
            ChiTietSanPham detailProduct = resolveProductDetail(product);
            ChiTietHoaDon line = new ChiTietHoaDon();
            line.setMaHoaDon(invoice);
            line.setMaChiTietSP(detailProduct);
            line.setSoLuong(quantities.get(product.getId()));
            KhuyenMaiService.PriceQuote quote = quotes.get(product.getId());
            line.setGiaGoc(quote.originalPrice());
            line.setDonGia(quote.finalPrice());
            line.setMaKhuyenMai(quote.promotion());
            line.setThanhTien(line.getDonGia().multiply(BigDecimal.valueOf(line.getSoLuong())));
            chiTietHoaDonRepo.save(line);
        }
    }

    private ChiTietSanPham resolveProductDetail(SanPham product) {
        return chiTietSanPhamRepo.findFirstByMaSPId(product.getId()).orElseGet(() -> {
            ChiTietSanPham detail = new ChiTietSanPham();
            detail.setMaSP(product);
            detail.setTrangThai(product.getTonKho() != null && product.getTonKho() > 0 ? "Còn hàng" : "Hết hàng");
            return chiTietSanPhamRepo.save(detail);
        });
    }

    private KhachHang resolveCustomer(InvoiceForm form, HoaDon invoice) {
        if (!form.taoKhachMoi) {
            if (form.maKhachHang == null) throw new IllegalArgumentException("Vui lòng chọn khách hàng hoặc tạo nhanh khách mới.");
            return khachHangRepo.findById(form.maKhachHang).orElseThrow(() -> new IllegalArgumentException("Khách hàng không tồn tại."));
        }
        String name = clean(form.tenKhachMoi);
        String phone = clean(form.soDienThoaiMoi);
        if (name.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập tên khách hàng mới.");
        if (!phone.matches("^0\\d{9}$")) throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0.");
        if (khachHangRepo.existsBySoDienThoai(phone)) throw new IllegalArgumentException("Số điện thoại này đã có trong danh sách khách hàng.");
        KhachHang customer = new KhachHang();
        customer.setTenKH(name);
        customer.setSoDienThoai(phone);
        customer.setDiaChi(clean(form.diaChiMoi));
        return khachHangRepo.save(customer);
    }

    private void validateForm(InvoiceForm form) {
        if (form.sanPhamIds == null) form.sanPhamIds = new ArrayList<>();
        if (form.soLuongs == null) form.soLuongs = new ArrayList<>();
    }

    private String normalizeInvoiceStatus(String status) {
        if (status == null || status.isBlank()) return UNPAID_STATUS;
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (value.contains("hủy")) return CANCELLED_STATUS;
        return UNPAID_STATUS;
    }

    private NhanVien resolveEmployee(HttpSession session, Integer requestedEmployeeId) {
        if (SessionUserControllerAdvice.isEmployee(session)) {
            NhanVien currentEmployee = SessionUserControllerAdvice.currentEmployee(session);
            if (currentEmployee == null || currentEmployee.getId() == null) {
                throw new IllegalArgumentException(
                        "Tài khoản nhân viên chưa được liên kết với hồ sơ nhân viên."
                );
            }
            return currentEmployee;
        }
        return requestedEmployeeId == null
                ? null
                : nhanVienRepo.findById(requestedEmployeeId)
                        .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
    }

    private boolean canEditInvoice(HoaDon invoice, HttpSession session) {
        if (SessionUserControllerAdvice.isAdmin(session)) {
            return true;
        }
        if (!SessionUserControllerAdvice.isEmployee(session)
                || invoice.getMaNhanVien() == null
                || invoice.getMaPhien() == null) {
            return false;
        }
        NhanVien employee = SessionUserControllerAdvice.currentEmployee(session);
        if (employee == null || employee.getId() == null
                || !Objects.equals(employee.getId(), invoice.getMaNhanVien().getId())) {
            return false;
        }
        return workSessionService.currentSession(session)
                .map(current -> Objects.equals(current.getId(), invoice.getMaPhien().getId()))
                .orElse(false);
    }

    private String invoiceSnapshot(HoaDon invoice, List<ChiTietHoaDon> details) {
        String customer = invoice.getMaDonHang() != null
                && invoice.getMaDonHang().getMaKH() != null
                && invoice.getMaDonHang().getMaKH().getTenKH() != null
                ? invoice.getMaDonHang().getMaKH().getTenKH()
                : "Khách lẻ";
        StringJoiner products = new StringJoiner(", ");
        for (ChiTietHoaDon detail : details) {
            String productName = detail.getMaChiTietSP() != null
                    && detail.getMaChiTietSP().getMaSP() != null
                    && detail.getMaChiTietSP().getMaSP().getTenSP() != null
                    ? detail.getMaChiTietSP().getMaSP().getTenSP()
                    : "Sản phẩm";
            products.add(productName + " × " + Optional.ofNullable(detail.getSoLuong()).orElse(0));
        }
        String total = Optional.ofNullable(invoice.getTongTien())
                .orElse(BigDecimal.ZERO)
                .stripTrailingZeros()
                .toPlainString();
        return "Khách hàng: " + customer
                + " | Trạng thái: " + invoice.getTrangThaiHienThi()
                + " | Sản phẩm: " + (products.length() == 0 ? "Không có" : products)
                + " | Tổng tiền: " + total + " đ";
    }

    private void recordEdit(
            HoaDon invoice,
            HttpSession session,
            String before,
            String after
    ) {
        TaiKhoan account = (TaiKhoan) session.getAttribute("user");
        LichSuChinhSuaHoaDon history = new LichSuChinhSuaHoaDon();
        history.setMaHoaDon(invoice);
        Object workSessionId = session.getAttribute(WorkSessionService.SESSION_ID_ATTRIBUTE);
        if (workSessionId instanceof Integer id) {
            history.setMaPhien(id);
        }
        history.setNguoiChinhSua(
                SessionUserControllerAdvice.displayName(account, session)
        );
        history.setThoiGian(java.time.LocalDateTime.now());
        history.setDuLieuTruoc(before);
        history.setDuLieuSau(after);
        invoiceHistoryRepo.save(history);
    }

    private void loadForm(Model model, String title, HttpSession session) {
        List<SanPham> products = sanPhamRepo.findAllByOrderByTenSPAsc();
        model.addAttribute("pageTitle", title);
        model.addAttribute("khachHangList", khachHangRepo.findAll());
        model.addAttribute("nhanVienList", nhanVienRepo.findAll());
        model.addAttribute("sanPhamList", products);
        model.addAttribute("priceQuotes", promotionService.quoteProducts(products, LocalDateTime.now()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isEmployee", SessionUserControllerAdvice.isEmployee(session));
        model.addAttribute("currentEmployee", SessionUserControllerAdvice.currentEmployee(session));
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String missing(RedirectAttributes redirect) { redirect.addFlashAttribute("error", "Không tìm thấy hóa đơn."); return "redirect:/hoadon"; }
    private String editDenied(RedirectAttributes redirect) {
        redirect.addFlashAttribute(
                "error",
                "Nhân viên chỉ được chỉnh sửa hóa đơn do mình tạo trong phiên làm việc hiện tại."
        );
        return "redirect:/hoadon";
    }
    private String paidEditDenied(RedirectAttributes redirect, Integer id) {
        redirect.addFlashAttribute("error", "Không thể chỉnh sửa hóa đơn đã thanh toán.");
        return "redirect:/hoadon/" + id;
    }
    private boolean loggedIn(HttpSession session) { return session.getAttribute("user") instanceof TaiKhoan; }

    @Getter
    @Setter
    public static class InvoiceForm {
        public Integer maKhachHang;
        public boolean taoKhachMoi;
        public String tenKhachMoi;
        public String soDienThoaiMoi;
        public String diaChiMoi;
        public Integer maNhanVien;
        public LocalDate ngayLap;
        public String trangThai;
        public List<Integer> sanPhamIds = new ArrayList<>();
        public List<Integer> soLuongs = new ArrayList<>();
    }
}
