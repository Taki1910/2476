package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.*;
import com.example.qlchgiay.repo.*;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/hoadon")
public class HoaDonController {
    private static final String UNPAID_STATUS = "Chưa thanh toán";
    private static final String PAID_STATUS = "Đã thanh toán";

    private final HoaDonRepo hoaDonRepo;
    private final DonHangRepo donHangRepo;
    private final NhanVienRepo nhanVienRepo;
    private final KhachHangRepo khachHangRepo;
    private final SanPhamRepo sanPhamRepo;
    private final ChiTietSanPhamRepo chiTietSanPhamRepo;
    private final ChiTietHoaDonRepo chiTietHoaDonRepo;

    public HoaDonController(HoaDonRepo hoaDonRepo, DonHangRepo donHangRepo, NhanVienRepo nhanVienRepo,
                            KhachHangRepo khachHangRepo, SanPhamRepo sanPhamRepo,
                            ChiTietSanPhamRepo chiTietSanPhamRepo, ChiTietHoaDonRepo chiTietHoaDonRepo) {
        this.hoaDonRepo = hoaDonRepo;
        this.donHangRepo = donHangRepo;
        this.nhanVienRepo = nhanVienRepo;
        this.khachHangRepo = khachHangRepo;
        this.sanPhamRepo = sanPhamRepo;
        this.chiTietSanPhamRepo = chiTietSanPhamRepo;
        this.chiTietHoaDonRepo = chiTietHoaDonRepo;
    }

    @GetMapping("/them")
    public String createForm(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = new HoaDon();
        item.setNgayLap(LocalDate.now());
        item.setTrangThai(UNPAID_STATUS);
        model.addAttribute("item", item);
        model.addAttribute("details", List.of());
        loadForm(model, "Tạo hóa đơn bán hàng");
        return "hoadon-form";
    }

    @PostMapping("/them")
    @Transactional
    public String create(HttpSession session, InvoiceForm form, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon invoice = new HoaDon();
        form.trangThai = UNPAID_STATUS;
        saveInvoice(invoice, form);
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
        model.addAttribute("printMode", printMode);
        return "hoadon-detail";
    }

    @PostMapping("/in/{id}")
    @Transactional
    public String print(@PathVariable Integer id, HttpSession session, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = hoaDonRepo.findById(id).orElse(null);
        if (item == null) return missing(redirect);

        item.setTrangThai(PAID_STATUS);
        hoaDonRepo.save(item);
        if (item.getMaDonHang() != null) {
            item.getMaDonHang().setTrangThai("Hoàn thành");
            donHangRepo.save(item.getMaDonHang());
        }

        return "redirect:/hoadon/" + id + "?print=true";
    }

    @GetMapping("/sua/{id}")
    public String updateForm(@PathVariable Integer id, HttpSession session, Model model, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon item = hoaDonRepo.findById(id).orElse(null);
        if (item == null) return missing(redirect);
        model.addAttribute("item", item);
        model.addAttribute("details", chiTietHoaDonRepo.findByMaHoaDonId(id));
        loadForm(model, "Cập nhật hóa đơn");
        return "hoadon-form";
    }

    @PostMapping("/sua/{id}")
    @Transactional
    public String update(@PathVariable Integer id, HttpSession session, InvoiceForm form, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        HoaDon invoice = hoaDonRepo.findById(id).orElse(null);
        if (invoice == null) return missing(redirect);
        saveInvoice(invoice, form);
        redirect.addFlashAttribute("success", "Đã cập nhật hóa đơn.");
        return "redirect:/hoadon/" + id;
    }

    @PostMapping("/xoa/{id}")
    @Transactional
    public String delete(@PathVariable Integer id, HttpSession session, RedirectAttributes redirect) {
        if (!loggedIn(session)) return "redirect:/login";
        try {
            chiTietHoaDonRepo.deleteByMaHoaDonId(id);
            hoaDonRepo.deleteById(id);
            hoaDonRepo.flush();
            redirect.addFlashAttribute("success", "Đã xóa hóa đơn.");
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("error", "Không thể xóa hóa đơn đã phát sinh thanh toán.");
        }
        return "redirect:/hoadon";
    }

    private void saveInvoice(HoaDon invoice, InvoiceForm form) {
        validateForm(form);
        KhachHang customer = resolveCustomer(form, invoice);
        NhanVien employee = form.maNhanVien == null ? null : nhanVienRepo.findById(form.maNhanVien)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên không tồn tại."));

        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (int i = 0; i < form.sanPhamIds.size(); i++) {
            Integer productId = form.sanPhamIds.get(i);
            Integer quantity = i < form.soLuongs.size() ? form.soLuongs.get(i) : null;
            if (productId != null && quantity != null && quantity > 0) quantities.merge(productId, quantity, Integer::sum);
        }
        if (quantities.isEmpty()) throw new IllegalArgumentException("Hóa đơn phải có ít nhất một sản phẩm.");

        List<SanPham> products = quantities.keySet().stream().map(id -> sanPhamRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại."))).toList();
        BigDecimal total = BigDecimal.ZERO;
        for (SanPham product : products) {
            int quantity = quantities.get(product.getId());
            if (product.getGia() == null) throw new IllegalArgumentException("Sản phẩm chưa có giá bán.");
            int stock = product.getTonKho() == null ? 0 : product.getTonKho();
            if (quantity > stock)
                throw new IllegalArgumentException("Sản phẩm " + product.getTenSP() + " không đủ tồn kho.");
            total = total.add(product.getGia().multiply(BigDecimal.valueOf(quantity)));
        }

        DonHang order = invoice.getMaDonHang();
        if (order == null) order = new DonHang();
        order.setMaKH(customer);
        order.setMaNhanVien(employee);
        order.setNgayDatHang(form.ngayLap);
        order.setTongTien(total);
        order.setTrangThai(orderStatus(form.trangThai));
        donHangRepo.save(order);

        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(employee);
        invoice.setNgayLap(form.ngayLap);
        invoice.setTongTien(total);
        invoice.setTrangThai(form.trangThai.trim());
        hoaDonRepo.save(invoice);

        if (invoice.getId() != null) chiTietHoaDonRepo.deleteByMaHoaDonId(invoice.getId());
        for (SanPham product : products) {
            ChiTietSanPham detailProduct = resolveProductDetail(product);
            ChiTietHoaDon line = new ChiTietHoaDon();
            line.setMaHoaDon(invoice);
            line.setMaChiTietSP(detailProduct);
            line.setSoLuong(quantities.get(product.getId()));
            line.setDonGia(product.getGia());
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
        if (form.ngayLap == null) throw new IllegalArgumentException("Vui lòng chọn ngày lập hóa đơn.");
        if (form.ngayLap.isAfter(LocalDate.now())) throw new IllegalArgumentException("Ngày lập không được ở tương lai.");
        if (form.trangThai == null || form.trangThai.isBlank()) throw new IllegalArgumentException("Vui lòng chọn trạng thái.");
        if (form.sanPhamIds == null) form.sanPhamIds = new ArrayList<>();
        if (form.soLuongs == null) form.soLuongs = new ArrayList<>();
    }

    private String orderStatus(String invoiceStatus) {
        String value = invoiceStatus.toLowerCase(Locale.ROOT);
        if (value.contains("hủy")) return "Đã hủy";
        if (value.contains("hoàn thành") || value.contains("đã thanh toán")) return "Hoàn thành";
        return "Đang xử lý";
    }

    private void loadForm(Model model, String title) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("khachHangList", khachHangRepo.findAll());
        model.addAttribute("nhanVienList", nhanVienRepo.findAll());
        model.addAttribute("sanPhamList", sanPhamRepo.findAllByOrderByTenSPAsc());
        model.addAttribute("today", LocalDate.now());
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String missing(RedirectAttributes redirect) { redirect.addFlashAttribute("error", "Không tìm thấy hóa đơn."); return "redirect:/hoadon"; }
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
