package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChiTietSanPham;
import com.example.qlchgiay.model.ChiTietHoaDon;
import com.example.qlchgiay.model.DonHang;
import com.example.qlchgiay.model.HoaDon;
import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.KhuyenMai;
import com.example.qlchgiay.model.LichSuChinhSuaHoaDon;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.PhienLamViec;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.model.ThanhToan;
import com.example.qlchgiay.repo.ChiTietHoaDonRepo;
import com.example.qlchgiay.repo.ChiTietSanPhamRepo;
import com.example.qlchgiay.repo.DonHangRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.repo.LichSuChinhSuaHoaDonRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.ThanhToanRepo;
import com.example.qlchgiay.service.WorkSessionService;
import com.example.qlchgiay.service.KhuyenMaiService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HoaDonControllerTest {
    @Mock private HoaDonRepo hoaDonRepo;
    @Mock private DonHangRepo donHangRepo;
    @Mock private NhanVienRepo nhanVienRepo;
    @Mock private KhachHangRepo khachHangRepo;
    @Mock private SanPhamRepo sanPhamRepo;
    @Mock private ChiTietSanPhamRepo chiTietSanPhamRepo;
    @Mock private ChiTietHoaDonRepo chiTietHoaDonRepo;
    @Mock private ThanhToanRepo thanhToanRepo;
    @Mock private LichSuChinhSuaHoaDonRepo invoiceHistoryRepo;
    @Mock private HttpSession session;
    @Mock private WorkSessionService workSessionService;
    @Mock private KhuyenMaiService promotionService;

    private HoaDonController controller;
    private TaiKhoan loggedInAccount;

    @BeforeEach
    void setUp() {
        controller = new HoaDonController(
                hoaDonRepo,
                donHangRepo,
                nhanVienRepo,
                khachHangRepo,
                sanPhamRepo,
                chiTietSanPhamRepo,
                chiTietHoaDonRepo,
                thanhToanRepo,
                invoiceHistoryRepo,
                workSessionService,
                promotionService
        );
        loggedInAccount = new TaiKhoan();
        loggedInAccount.setVaiTro("Admin");
        loggedInAccount.setTenDangNhap("admin");
        when(session.getAttribute("user")).thenReturn(loggedInAccount);
        lenient().when(promotionService.quoteProducts(any(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    List<SanPham> products = invocation.getArgument(0);
                    Map<Integer, KhuyenMaiService.PriceQuote> quotes = new LinkedHashMap<>();
                    for (SanPham product : products) {
                        BigDecimal price = product.getGia() == null ? BigDecimal.ZERO : product.getGia();
                        quotes.put(product.getId(), new KhuyenMaiService.PriceQuote(price, price, null));
                    }
                    return quotes;
                });
    }

    @Test
    void createAlwaysStartsAsUnpaid() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày kiểm thử");
        product.setGia(BigDecimal.valueOf(1_000_000));
        product.setTonKho(5);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);

        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findById(2)).thenReturn(Optional.of(product));
        when(chiTietSanPhamRepo.findFirstByMaSPId(2)).thenReturn(Optional.of(detailProduct));
        when(hoaDonRepo.save(any(HoaDon.class))).thenAnswer(invocation -> {
            HoaDon saved = invocation.getArgument(0);
            saved.setId(99);
            return saved;
        });

        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        LocalDate today = LocalDate.now();
        form.ngayLap = today.plusYears(5);
        form.trangThai = "Đã thanh toán";
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        String view = controller.create(session, form, new RedirectAttributesModelMap());

        ArgumentCaptor<HoaDon> invoiceCaptor = ArgumentCaptor.forClass(HoaDon.class);
        verify(hoaDonRepo).save(invoiceCaptor.capture());
        assertEquals("redirect:/hoadon/99", view);
        assertEquals("Chưa thanh toán", invoiceCaptor.getValue().getTrangThai());
        assertEquals("Chưa thanh toán", invoiceCaptor.getValue().getMaDonHang().getTrangThai());
        assertEquals(today, invoiceCaptor.getValue().getNgayLap());
        assertEquals(today, invoiceCaptor.getValue().getMaDonHang().getNgayDatHang());
    }

    @Test
    void createStoresOriginalAndPromotionalPriceFromServerQuote() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày khuyến mại");
        product.setGia(new BigDecimal("1000000"));
        product.setTonKho(5);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setMaSP(product);
        KhuyenMai promotion = new KhuyenMai();
        promotion.setId(7);
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findById(2)).thenReturn(Optional.of(product));
        when(chiTietSanPhamRepo.findFirstByMaSPId(2)).thenReturn(Optional.of(detailProduct));
        when(promotionService.quoteProducts(any(), any(LocalDateTime.class))).thenReturn(Map.of(
                2, new KhuyenMaiService.PriceQuote(
                        new BigDecimal("1000000"), new BigDecimal("850000.00"), promotion
                )
        ));
        when(hoaDonRepo.save(any(HoaDon.class))).thenAnswer(invocation -> {
            HoaDon saved = invocation.getArgument(0);
            saved.setId(99);
            return saved;
        });
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(2);

        controller.create(session, form, new RedirectAttributesModelMap());

        ArgumentCaptor<ChiTietHoaDon> line = ArgumentCaptor.forClass(ChiTietHoaDon.class);
        verify(chiTietHoaDonRepo).save(line.capture());
        assertEquals(new BigDecimal("1000000"), line.getValue().getGiaGoc());
        assertEquals(new BigDecimal("850000.00"), line.getValue().getDonGia());
        assertSame(promotion, line.getValue().getMaKhuyenMai());
    }

    @Test
    void paymentMarksInvoicePaidAndDecrementsStock() {
        DonHang order = new DonHang();
        order.setTrangThai("Đang xử lý");
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setTrangThai("Chưa thanh toán");
        invoice.setTongTien(BigDecimal.valueOf(2_000_000));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày kiểm thử");
        product.setTonKho(5);
        ChiTietSanPham productDetail = new ChiTietSanPham();
        productDetail.setMaSP(product);
        com.example.qlchgiay.model.ChiTietHoaDon line =
                new com.example.qlchgiay.model.ChiTietHoaDon();
        line.setMaChiTietSP(productDetail);
        line.setSoLuong(2);

        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(chiTietHoaDonRepo.findByMaHoaDonId(7)).thenReturn(List.of(line));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
        when(thanhToanRepo.existsByMaHoaDonId(7)).thenReturn(false);

        String view = controller.pay(
                7,
                session,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/hoadon/7", view);
        assertEquals("Đã thanh toán", invoice.getTrangThai());
        assertEquals("Đã thanh toán", order.getTrangThai());
        assertEquals(3, product.getTonKho());
        verify(hoaDonRepo).save(invoice);
        verify(donHangRepo).save(order);
        ArgumentCaptor<ThanhToan> paymentCaptor = ArgumentCaptor.forClass(ThanhToan.class);
        verify(thanhToanRepo).save(paymentCaptor.capture());
        assertSame(invoice, paymentCaptor.getValue().getMaHoaDon());
        assertEquals(BigDecimal.valueOf(2_000_000), paymentCaptor.getValue().getSoTien());
        assertEquals("Thành công", paymentCaptor.getValue().getTrangThai());
    }

    @Test
    void paymentRejectsCancelledInvoiceWithoutChangingStock() {
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setTrangThai("Đã hủy");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        assertEquals("Không thể thanh toán hóa đơn đã hủy.", exception.getMessage());
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
        verify(thanhToanRepo, never()).save(any());
    }

    @Test
    void payingAnAlreadyPaidInvoiceIsIdempotent() {
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setTrangThai("Đã thanh toán");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));

        String view = controller.pay(7, session, new RedirectAttributesModelMap());

        assertEquals("redirect:/hoadon/7", view);
        verify(chiTietHoaDonRepo, never()).findByMaHoaDonId(7);
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
        verify(thanhToanRepo, never()).save(any());
    }

    @Test
    void employeeCreatesInvoiceUnderTheirOwnIdentity() {
        NhanVien currentEmployee = new NhanVien();
        currentEmployee.setId(44);
        currentEmployee.setTenNhanVien("Nhân viên hiện tại");
        loggedInAccount.setVaiTro("Nhân viên");
        loggedInAccount.setMaNhanVien(currentEmployee);
        PhienLamViec currentSession = new PhienLamViec();
        currentSession.setId(12);
        currentSession.setMaNhanVien(currentEmployee);

        KhachHang customer = new KhachHang();
        customer.setId(1);
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày nhân viên bán");
        product.setGia(BigDecimal.valueOf(900_000));
        product.setTonKho(4);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);

        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findById(2)).thenReturn(Optional.of(product));
        when(chiTietSanPhamRepo.findFirstByMaSPId(2)).thenReturn(Optional.of(detailProduct));
        when(workSessionService.currentSession(session)).thenReturn(Optional.of(currentSession));
        when(hoaDonRepo.save(any(HoaDon.class))).thenAnswer(invocation -> {
            HoaDon saved = invocation.getArgument(0);
            saved.setId(101);
            return saved;
        });

        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.maNhanVien = 999;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        String view = controller.create(session, form, new RedirectAttributesModelMap());

        ArgumentCaptor<HoaDon> invoiceCaptor = ArgumentCaptor.forClass(HoaDon.class);
        ArgumentCaptor<DonHang> orderCaptor = ArgumentCaptor.forClass(DonHang.class);
        verify(hoaDonRepo).save(invoiceCaptor.capture());
        verify(donHangRepo).save(orderCaptor.capture());
        verify(nhanVienRepo, never()).findById(anyInt());

        assertEquals("redirect:/hoadon/101", view);
        assertSame(currentEmployee, invoiceCaptor.getValue().getMaNhanVien());
        assertSame(currentEmployee, orderCaptor.getValue().getMaNhanVien());
        assertSame(currentSession, invoiceCaptor.getValue().getMaPhien());
    }

    @Test
    void employeeCanOpenOwnInvoiceFromCurrentSession() {
        NhanVien currentEmployee = new NhanVien();
        currentEmployee.setId(44);
        loggedInAccount.setVaiTro("Nhân viên");
        loggedInAccount.setMaNhanVien(currentEmployee);
        PhienLamViec currentSession = new PhienLamViec();
        currentSession.setId(12);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaNhanVien(currentEmployee);
        invoice.setMaPhien(currentSession);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findById(7)).thenReturn(Optional.of(invoice));
        when(workSessionService.currentSession(session)).thenReturn(Optional.of(currentSession));

        String view = controller.updateForm(
                7,
                session,
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("hoadon-form", view);
    }

    @Test
    void employeeCannotOpenInvoiceFromPreviousSession() {
        NhanVien currentEmployee = new NhanVien();
        currentEmployee.setId(44);
        loggedInAccount.setVaiTro("Nhân viên");
        loggedInAccount.setMaNhanVien(currentEmployee);
        PhienLamViec previousSession = new PhienLamViec();
        previousSession.setId(11);
        PhienLamViec currentSession = new PhienLamViec();
        currentSession.setId(12);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaNhanVien(currentEmployee);
        invoice.setMaPhien(previousSession);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findById(7)).thenReturn(Optional.of(invoice));
        when(workSessionService.currentSession(session)).thenReturn(Optional.of(currentSession));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.updateForm(
                7,
                session,
                new ExtendedModelMap(),
                redirect
        );

        assertEquals("redirect:/hoadon", view);
        assertEquals(
                "Nhân viên chỉ được chỉnh sửa hóa đơn do mình tạo trong phiên làm việc hiện tại.",
                redirect.getFlashAttributes().get("error")
        );
    }

    @Test
    void updatingInvoiceStoresBeforeAndAfterHistory() {
        KhachHang oldCustomer = new KhachHang();
        oldCustomer.setTenKH("Khách cũ");
        KhachHang newCustomer = new KhachHang();
        newCustomer.setId(1);
        newCustomer.setTenKH("Khách mới");
        DonHang order = new DonHang();
        order.setMaKH(oldCustomer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setNgayLap(LocalDate.now());
        invoice.setTrangThai("Chưa thanh toán");
        invoice.setTongTien(BigDecimal.valueOf(500_000));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày kiểm thử");
        product.setGia(BigDecimal.valueOf(900_000));
        product.setTonKho(4);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);
        com.example.qlchgiay.model.ChiTietHoaDon updatedLine =
                new com.example.qlchgiay.model.ChiTietHoaDon();
        updatedLine.setMaChiTietSP(detailProduct);
        updatedLine.setSoLuong(1);

        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(newCustomer));
        when(sanPhamRepo.findById(2)).thenReturn(Optional.of(product));
        when(chiTietSanPhamRepo.findFirstByMaSPId(2)).thenReturn(Optional.of(detailProduct));
        when(chiTietHoaDonRepo.findByMaHoaDonId(7))
                .thenReturn(List.of())
                .thenReturn(List.of(updatedLine));

        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        String view = controller.update(
                7,
                session,
                form,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/hoadon/7", view);
        ArgumentCaptor<LichSuChinhSuaHoaDon> historyCaptor =
                ArgumentCaptor.forClass(LichSuChinhSuaHoaDon.class);
        verify(invoiceHistoryRepo).save(historyCaptor.capture());
        assertEquals("admin", historyCaptor.getValue().getNguoiChinhSua());
        assertTrue(historyCaptor.getValue().getDuLieuTruoc().contains("Khách cũ"));
        assertTrue(historyCaptor.getValue().getDuLieuSau().contains("Khách mới"));
        assertTrue(historyCaptor.getValue().getDuLieuSau().contains("Giày kiểm thử"));
    }

    @Test
    void employeeCannotDeleteInvoice() {
        loggedInAccount.setVaiTro("Nhân viên");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.delete(7, session, redirect);

        assertEquals("redirect:/hoadon", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền xóa hóa đơn.",
                redirect.getFlashAttributes().get("error")
        );
        verify(chiTietHoaDonRepo, never()).deleteByMaHoaDonId(7);
        verify(hoaDonRepo, never()).deleteById(7);
    }
}
