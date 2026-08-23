package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.*;
import com.example.qlchgiay.repo.*;
import com.example.qlchgiay.service.WorkSessionService;
import com.example.qlchgiay.service.KhuyenMaiService;
import com.example.qlchgiay.service.AppNotificationService;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.Normalizer;
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
    private final AppNotificationService notificationService;

    public HoaDonController(HoaDonRepo hoaDonRepo, DonHangRepo donHangRepo, NhanVienRepo nhanVienRepo,
                            KhachHangRepo khachHangRepo, SanPhamRepo sanPhamRepo,
                            ChiTietSanPhamRepo chiTietSanPhamRepo, ChiTietHoaDonRepo chiTietHoaDonRepo,
                            ThanhToanRepo thanhToanRepo,
                            LichSuChinhSuaHoaDonRepo invoiceHistoryRepo,
                            WorkSessionService workSessionService,
                            KhuyenMaiService promotionService,
                            AppNotificationService notificationService) {
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
        this.notificationService = notificationService;
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
        if (!canViewInvoice(item, session)) {
            throw new AccessDeniedException("Bạn không có quyền xem hóa đơn này.");
        }
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
                UNPAID_STATUS.equals(item.getTrangThaiHienThi())
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
        requireInvoiceAccess(item, session);
        if (PAID_STATUS.equals(item.getTrangThaiHienThi())) {
            redirect.addFlashAttribute("success", "Hóa đơn đã được thanh toán trước đó.");
            return "redirect:/hoadon/" + id;
        }
        if (CANCELLED_STATUS.equals(item.getTrangThaiHienThi())) {
            throw new IllegalArgumentException("Không thể thanh toán hóa đơn đã hủy.");
        }
        if (thanhToanRepo.existsByMaHoaDonId(id)) {
            throw new IllegalArgumentException("Hóa đơn đã có giao dịch thanh toán.");
        }
        KhachHang customer = item.getMaDonHang() == null ? null : item.getMaDonHang().getMaKH();
        if (customer != null && !customer.isActive()) {
            throw new IllegalArgumentException("Khách hàng không còn đủ điều kiện thanh toán.");
        }
        if (customer != null) requirePurchaseAge(customer);
        if (!isActiveEmployee(item.getMaNhanVien())) {
            throw new IllegalArgumentException("Nhân viên phụ trách đã ngừng hoạt động.");
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
            if (!isActiveProductDetail(detail.getMaChiTietSP())) {
                throw new IllegalArgumentException("Chi tiết sản phẩm đã ngừng hoạt động.");
            }
            int quantity = detail.getSoLuong() == null ? 0 : detail.getSoLuong();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm trong hóa đơn không hợp lệ.");
            }
            try {
                quantities.merge(detail.getMaChiTietSP().getMaSP().getId(), quantity, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Tổng số lượng sản phẩm trong hóa đơn không hợp lệ.");
            }
        }

        Map<Integer, SanPham> products = new LinkedHashMap<>();
        for (Integer productId : quantities.keySet().stream().sorted().toList()) {
            Map.Entry<Integer, Integer> entry = Map.entry(productId, quantities.get(productId));
            SanPham product = sanPhamRepo.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại."));
            if (!product.isActive()) {
                throw new IllegalArgumentException("Sản phẩm " + product.getTenSP() + " đã ngừng bán.");
            }
            if (product.getGia() == null || product.getGia().signum() < 0) {
                throw new IllegalArgumentException("Sản phẩm chưa có giá bán hợp lệ.");
            }
            int stock = product.getTonKho() == null ? 0 : product.getTonKho();
            if (entry.getValue() > stock) {
                throw new IllegalArgumentException(
                        "Sản phẩm " + product.getTenSP() + " không đủ tồn kho."
                );
            }
            products.put(productId, product);
        }

        Map<Integer, KhuyenMaiService.PriceQuote> quotes = promotionService.quoteProducts(
                products.values(), LocalDateTime.now()
        );
        BigDecimal total = BigDecimal.ZERO;
        for (ChiTietHoaDon detail : details) {
            SanPham product = products.get(detail.getMaChiTietSP().getMaSP().getId());
            KhuyenMaiService.PriceQuote quote = quotes.get(product.getId());
            if (quote == null) throw new IllegalArgumentException("Không thể xác định giá bán hiện tại.");
            detail.setGiaGoc(quote.originalPrice());
            detail.setDonGia(quote.finalPrice());
            detail.setMaKhuyenMai(quote.promotion());
            detail.setTenSanPhamSnapshot(product.getTenSP());
            detail.setMaSanPhamSnapshot("SP-" + product.getId());
            detail.setMoTaBienTheSnapshot(variantDescription(product));
            detail.setTenKhuyenMaiSnapshot(
                    quote.promotion() == null ? null : quote.promotion().getTenKhuyenMai()
            );
            total = total.add(quote.finalPrice().multiply(BigDecimal.valueOf(detail.getSoLuong())));
        }
        chiTietHoaDonRepo.saveAll(details);

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            SanPham product = products.get(entry.getKey());
            int previousStock = product.getTonKho();
            product.setTonKho(previousStock - entry.getValue());
            sanPhamRepo.save(product);
            notificationService.notifyStockThreshold(product, previousStock, item.getId());
        }

        item.setTongTien(total);
        item.setTrangThai(PAID_STATUS);
        item.setTenKhachHangSnapshot(customer == null ? "Khách lẻ" : customer.getTenKH());
        item.setSoDienThoaiKhachHangSnapshot(customer == null ? null : customer.getSoDienThoai());
        item.setTenNhanVienSnapshot(item.getMaNhanVien().getTenNhanVien());
        hoaDonRepo.save(item);
        if (item.getMaDonHang() != null) {
            item.getMaDonHang().setTongTien(total);
            item.getMaDonHang().setTrangThai(PAID_STATUS);
            donHangRepo.save(item.getMaDonHang());
        }

        ThanhToan payment = new ThanhToan();
        payment.setMaHoaDon(item);
        payment.setPhuongThuc("Tiền mặt");
        payment.setNgayThanhToan(LocalDate.now());
        payment.setSoTien(total);
        payment.setTrangThai("Thành công");
        thanhToanRepo.save(payment);

        if (SessionUserControllerAdvice.isEmployee(session)) {
            int productQuantity = quantities.values().stream().mapToInt(Integer::intValue).sum();
            workSessionService.recordPaidSale(session, productQuantity, total);
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
        if (!UNPAID_STATUS.equals(item.getTrangThaiHienThi())) {
            return immutableInvoiceDenied(redirect, id);
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
        if (!UNPAID_STATUS.equals(invoice.getTrangThaiHienThi())) {
            return immutableInvoiceDenied(redirect, id);
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
    public String delete(@PathVariable Integer id, HttpSession session, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!hoaDonRepo.existsById(id)) return missing(redirect);
        redirect.addFlashAttribute(
                "error",
                "Hóa đơn đã phát hành không thể xóa. Hãy hủy hóa đơn chưa thanh toán nếu cần."
        );
        return "redirect:/hoadon/" + id;
    }

    @PostMapping("/huy/{id}")
    @Transactional
    public String cancel(
            @PathVariable Integer id,
            @RequestParam("lyDo") String reason,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon invoice = hoaDonRepo.findByIdForUpdate(id).orElse(null);
        if (invoice == null) return missing(redirect);
        requireInvoiceAccess(invoice, session);
        if (!UNPAID_STATUS.equals(invoice.getTrangThaiHienThi())) {
            throw new IllegalArgumentException("Chỉ hóa đơn chưa thanh toán mới có thể hủy.");
        }
        String cancellationReason = clean(reason);
        if (cancellationReason.length() < 3 || cancellationReason.length() > 500) {
            throw new IllegalArgumentException("Lý do hủy phải có từ 3 đến 500 ký tự.");
        }

        List<ChiTietHoaDon> details = chiTietHoaDonRepo.findByMaHoaDonId(id);
        String before = invoiceSnapshot(invoice, details);
        invoice.setTrangThai(CANCELLED_STATUS);
        hoaDonRepo.save(invoice);
        if (invoice.getMaDonHang() != null) {
            invoice.getMaDonHang().setTrangThai(CANCELLED_STATUS);
            donHangRepo.save(invoice.getMaDonHang());
        }
        recordEdit(
                invoice,
                session,
                before,
                invoiceSnapshot(invoice, details) + " | Lý do hủy: " + cancellationReason
        );
        redirect.addFlashAttribute("success", "Đã hủy hóa đơn và lưu lý do.");
        return "redirect:/hoadon/" + id;
    }

    private void saveInvoice(HoaDon invoice, InvoiceForm form, HttpSession session) {
        validateForm(form);
        String normalizedStatus = invoice.getId() == null
                ? UNPAID_STATUS
                : invoice.getTrangThaiHienThi();
        LocalDate invoiceDate = invoice.getId() == null
                ? LocalDate.now()
                : Optional.ofNullable(invoice.getNgayLap()).orElse(LocalDate.now());
        KhachHang customer = resolveCustomer(form, invoice);
        if (customer != null) requirePurchaseAge(customer);
        NhanVien employee = resolveEmployee(session, form.maNhanVien);

        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (int i = 0; i < form.sanPhamIds.size(); i++) {
            Integer productId = form.sanPhamIds.get(i);
            Integer quantity = i < form.soLuongs.size() ? form.soLuongs.get(i) : null;
            if (productId == null) continue;
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Số lượng sản phẩm phải lớn hơn 0.");
            }
            try {
                quantities.merge(productId, quantity, Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Tổng số lượng sản phẩm không hợp lệ.");
            }
        }
        if (quantities.isEmpty()) throw new IllegalArgumentException("Hóa đơn phải có ít nhất một sản phẩm.");

        List<SanPham> products = quantities.keySet().stream().sorted()
                .map(id -> sanPhamRepo.findByIdForUpdate(id)
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại.")))
                .toList();
        Map<Integer, KhuyenMaiService.PriceQuote> quotes = promotionService.quoteProducts(
                products, LocalDateTime.now()
        );
        BigDecimal total = BigDecimal.ZERO;
        for (SanPham product : products) {
            int quantity = quantities.get(product.getId());
            if (!product.isActive()) {
                throw new IllegalArgumentException("Sản phẩm " + product.getTenSP() + " đã ngừng bán.");
            }
            if (product.getGia() == null) throw new IllegalArgumentException("Sản phẩm chưa có giá bán.");
            int stock = product.getTonKho() == null ? 0 : product.getTonKho();
            long committed = Optional.ofNullable(chiTietHoaDonRepo.sumCommittedQuantity(
                    product.getId(), UNPAID_STATUS, invoice.getId()
            )).orElse(0L);
            long available = Math.max(0L, (long) stock - committed);
            if (quantity > available) {
                throw new IllegalArgumentException(
                        "Sản phẩm " + product.getTenSP() + " chỉ còn " + available
                                + " sản phẩm khả dụng do các hóa đơn chưa thanh toán khác."
                );
            }
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
        invoice.setTenKhachHangSnapshot(customer == null ? "Khách lẻ" : customer.getTenKH());
        invoice.setSoDienThoaiKhachHangSnapshot(customer == null ? null : customer.getSoDienThoai());
        invoice.setTenNhanVienSnapshot(
                employee == null ? "Chưa phân công" : employee.getTenNhanVien()
        );
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
            line.setTenSanPhamSnapshot(product.getTenSP());
            line.setMaSanPhamSnapshot("SP-" + product.getId());
            line.setMoTaBienTheSnapshot(variantDescription(product));
            line.setTenKhuyenMaiSnapshot(
                    quote.promotion() == null ? null : quote.promotion().getTenKhuyenMai()
            );
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
        if (form.khachLe) return null;
        if (!form.taoKhachMoi) {
            if (form.maKhachHang == null) throw new IllegalArgumentException("Vui lòng chọn khách hàng hoặc tạo nhanh khách mới.");
            KhachHang customer = khachHangRepo.findById(form.maKhachHang)
                    .orElseThrow(() -> new IllegalArgumentException("Khách hàng không tồn tại."));
            if (!customer.isActive()) throw new IllegalArgumentException("Khách hàng đã được lưu trữ.");
            return customer;
        }
        String name = clean(form.tenKhachMoi);
        String phone = form.soDienThoaiMoi == null ? "" : form.soDienThoaiMoi;
        if (name.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập tên khách hàng mới.");
        if (!phone.matches("^(03|05|07|08|09)\\d{8}$")) {
            throw new IllegalArgumentException("Số điện thoại không đúng định dạng số di động Việt Nam.");
        }
        if (form.ngaySinhKhachMoi == null) {
            throw new IllegalArgumentException("Khách hàng phải đủ 15 tuổi trở lên để mua hàng.");
        }
        if (khachHangRepo.existsBySoDienThoai(phone)) throw new IllegalArgumentException("Số điện thoại này đã có trong danh sách khách hàng.");
        KhachHang customer = new KhachHang();
        customer.setTenKH(name);
        customer.setSoDienThoai(phone);
        customer.setNgaySinh(form.ngaySinhKhachMoi);
        customer.setDiaChi(clean(form.diaChiMoi));
        return khachHangRepo.save(customer);
    }

    private void requirePurchaseAge(KhachHang customer) {
        if (customer == null || customer.getNgaySinhHieuLuc() == null
                || customer.getNgaySinhHieuLuc().isAfter(LocalDate.now().minusYears(15))) {
            throw new IllegalArgumentException("Khách hàng phải đủ 15 tuổi trở lên để mua hàng.");
        }
    }

    private void validateForm(InvoiceForm form) {
        if (form.sanPhamIds == null) form.sanPhamIds = new ArrayList<>();
        if (form.soLuongs == null) form.soLuongs = new ArrayList<>();
        if (form.khachLe) {
            form.taoKhachMoi = false;
            form.maKhachHang = null;
        }
    }

    private NhanVien resolveEmployee(HttpSession session, Integer requestedEmployeeId) {
        if (SessionUserControllerAdvice.isEmployee(session)) {
            NhanVien currentEmployee = SessionUserControllerAdvice.currentEmployee(session);
            if (currentEmployee == null || currentEmployee.getId() == null) {
                throw new IllegalArgumentException(
                        "Tài khoản nhân viên chưa được liên kết với hồ sơ nhân viên."
                );
            }
            if (!isActiveEmployee(currentEmployee)) {
                throw new IllegalArgumentException("Nhân viên đã ngừng hoạt động.");
            }
            return currentEmployee;
        }
        if (requestedEmployeeId == null) {
            NhanVien current = SessionUserControllerAdvice.currentEmployee(session);
            if (current == null || !isActiveEmployee(current)) {
                throw new IllegalArgumentException("Hóa đơn phải có nhân viên phụ trách đang hoạt động.");
            }
            return current;
        }
        NhanVien employee = nhanVienRepo.findById(requestedEmployeeId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));
        if (!isActiveEmployee(employee)) {
            throw new IllegalArgumentException("Nhân viên đã ngừng hoạt động.");
        }
        return employee;
    }

    private boolean canEditInvoice(HoaDon invoice, HttpSession session) {
        if (!(session.getAttribute("user") instanceof TaiKhoan account)
                || !SessionUserControllerAdvice.isActive(account)) {
            return false;
        }
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

    private boolean canViewInvoice(HoaDon invoice, HttpSession session) {
        if (SessionUserControllerAdvice.isAdmin(session)) return true;
        NhanVien employee = SessionUserControllerAdvice.currentEmployee(session);
        return SessionUserControllerAdvice.isEmployee(session)
                && employee != null && invoice.getMaNhanVien() != null
                && Objects.equals(employee.getId(), invoice.getMaNhanVien().getId());
    }

    private void requireInvoiceAccess(HoaDon invoice, HttpSession session) {
        if (!canEditInvoice(invoice, session)) {
            throw new AccessDeniedException(
                    "Bạn không có quyền thực hiện thao tác với hóa đơn này."
            );
        }
    }

    private String invoiceSnapshot(HoaDon invoice, List<ChiTietHoaDon> details) {
        String customer = invoice.getTenKhachHangHienThi();
        StringJoiner products = new StringJoiner(", ");
        for (ChiTietHoaDon detail : details) {
            String productName = detail.getTenSanPhamHienThi();
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
        List<SanPham> products = sanPhamRepo.findByTrangThaiOrderByTenSPAsc("ACTIVE");
        model.addAttribute("pageTitle", title);
        model.addAttribute(
                "khachHangList",
                khachHangRepo.findByTrangThaiOrderByIdDesc("ACTIVE").stream()
                        .filter(customer -> customer.getNgaySinhHieuLuc() != null
                                && !customer.getNgaySinhHieuLuc().isAfter(LocalDate.now().minusYears(15)))
                        .toList()
        );
        model.addAttribute("nhanVienList", nhanVienRepo.findActiveWithPositionOrderByIdDesc());
        model.addAttribute("sanPhamList", products);
        model.addAttribute("priceQuotes", promotionService.quoteProducts(products, LocalDateTime.now()));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isEmployee", SessionUserControllerAdvice.isEmployee(session));
        model.addAttribute("currentEmployee", SessionUserControllerAdvice.currentEmployee(session));
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private boolean isActiveEmployee(NhanVien employee) {
        if (employee == null || employee.getTrangThai() == null) return employee != null;
        String status = Normalizer.normalize(employee.getTrangThai(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return !status.contains("ngung") && !status.contains("inactive")
                && !status.contains("disable") && !status.contains("khoa");
    }
    private boolean isActiveProductDetail(ChiTietSanPham detail) {
        if (detail == null || detail.getTrangThai() == null || detail.getTrangThai().isBlank()) return detail != null;
        String status = Normalizer.normalize(detail.getTrangThai(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return status.equals("active") || status.contains("con hang");
    }
    private String variantDescription(SanPham product) {
        List<String> parts = new ArrayList<>();
        if (product.getMaLoai() != null) parts.add(product.getMaLoai().getTenLoai());
        if (product.getMaMau() != null) parts.add(product.getMaMau().getTenMau());
        if (product.getMaSize() != null) parts.add("Size " + product.getMaSize().getTenSize());
        return String.join(" / ", parts);
    }
    private String missing(RedirectAttributes redirect) { redirect.addFlashAttribute("error", "Không tìm thấy hóa đơn."); return "redirect:/hoadon"; }
    private String editDenied(RedirectAttributes redirect) {
        redirect.addFlashAttribute(
                "error",
                "Nhân viên chỉ được chỉnh sửa hóa đơn do mình tạo trong phiên làm việc hiện tại."
        );
        return "redirect:/hoadon";
    }
    private String immutableInvoiceDenied(RedirectAttributes redirect, Integer id) {
        redirect.addFlashAttribute("error", "Không thể chỉnh sửa hóa đơn đã thanh toán hoặc đã hủy.");
        return "redirect:/hoadon/" + id;
    }
    private boolean loggedIn(HttpSession session) { return SessionUserControllerAdvice.hasBusinessAccess(session); }

    @Getter
    @Setter
    public static class InvoiceForm {
        public Integer maKhachHang;
        public boolean khachLe;
        public boolean taoKhachMoi;
        public String tenKhachMoi;
        public String soDienThoaiMoi;
        public LocalDate ngaySinhKhachMoi;
        public String diaChiMoi;
        public Integer maNhanVien;
        public LocalDate ngayLap;
        public String trangThai;
        public List<Integer> sanPhamIds = new ArrayList<>();
        public List<Integer> soLuongs = new ArrayList<>();
    }
}
