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
import com.example.qlchgiay.service.AppNotificationService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock private AppNotificationService notificationService;

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
                promotionService,
                notificationService
        );
        loggedInAccount = new TaiKhoan();
        loggedInAccount.setVaiTro("Admin");
        loggedInAccount.setTenDangNhap("admin");
        NhanVien adminEmployee = new NhanVien();
        adminEmployee.setId(1);
        adminEmployee.setTenNhanVien("Quản trị viên");
        adminEmployee.setTrangThai("Đang làm");
        loggedInAccount.setMaNhanVien(adminEmployee);
        when(session.getAttribute("user")).thenReturn(loggedInAccount);
        lenient().when(promotionService.quoteProducts(any(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    Collection<SanPham> products = invocation.getArgument(0);
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
        customer.setNgaySinh(LocalDate.now().minusYears(15));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày kiểm thử");
        product.setGia(BigDecimal.valueOf(1_000_000));
        product.setTonKho(5);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);

        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
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
    void createWalkInInvoiceWithoutCreatingFakeCustomer() {
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày bán khách lẻ");
        product.setGia(BigDecimal.valueOf(1_200_000));
        product.setTonKho(5);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
        when(chiTietSanPhamRepo.findFirstByMaSPId(2)).thenReturn(Optional.of(detailProduct));
        when(hoaDonRepo.save(any(HoaDon.class))).thenAnswer(invocation -> {
            HoaDon saved = invocation.getArgument(0);
            saved.setId(100);
            return saved;
        });

        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.khachLe = true;
        form.taoKhachMoi = true;
        form.tenKhachMoi = "Dữ liệu phải bị bỏ qua";
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        String view = controller.create(session, form, new RedirectAttributesModelMap());

        ArgumentCaptor<DonHang> order = ArgumentCaptor.forClass(DonHang.class);
        ArgumentCaptor<HoaDon> invoice = ArgumentCaptor.forClass(HoaDon.class);
        verify(donHangRepo).save(order.capture());
        verify(hoaDonRepo).save(invoice.capture());
        assertEquals("redirect:/hoadon/100", view);
        assertNull(order.getValue().getMaKH());
        assertEquals("Khách lẻ", invoice.getValue().getTenKhachHangSnapshot());
        assertNull(invoice.getValue().getSoDienThoaiKhachHangSnapshot());
        assertEquals(BigDecimal.valueOf(1_200_000), invoice.getValue().getTongTien());
        verify(khachHangRepo, never()).save(any());
        verify(khachHangRepo, never()).findById(anyInt());
        verify(chiTietHoaDonRepo).sumCommittedQuantity(2, "Chưa thanh toán", null);
    }

    @Test
    void createStoresOriginalAndPromotionalPriceFromServerQuote() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setNgaySinh(LocalDate.now().minusYears(20));
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
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
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
    void createRejectsInactiveProductBeforeCreatingOrder() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày ngừng bán");
        product.setTrangThai("INACTIVE");
        product.setGia(BigDecimal.valueOf(1_000_000));
        product.setTonKho(5);
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertTrue(exception.getMessage().contains("đã ngừng bán"));
        verify(donHangRepo, never()).save(any());
        verify(hoaDonRepo, never()).save(any());
    }

    @Test
    void createRejectsArchivedCustomerBeforeCreatingOrder() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setTrangThai("ARCHIVED");
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertEquals("Khách hàng đã được lưu trữ.", exception.getMessage());
        verify(donHangRepo, never()).save(any());
        verify(hoaDonRepo, never()).save(any());
    }

    @Test
    void createRejectsCustomerOneDayBeforeFifteenthBirthday() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setNgaySinh(LocalDate.now().minusYears(15).plusDays(1));
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertEquals("Khách hàng phải đủ 15 tuổi trở lên để mua hàng.", exception.getMessage());
        verify(donHangRepo, never()).save(any());
        verify(hoaDonRepo, never()).save(any());
    }

    @Test
    void quickCustomerRejectsInvalidVietnameseMobileNumber() {
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.taoKhachMoi = true;
        form.tenKhachMoi = "Khách mới";
        form.soDienThoaiMoi = "0412345678";
        form.ngaySinhKhachMoi = LocalDate.now().minusYears(20);
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertEquals(
                "Số điện thoại không đúng định dạng số di động Việt Nam.",
                exception.getMessage()
        );
        verify(khachHangRepo, never()).save(any());
    }

    @Test
    void createRejectsQuantityCommittedToAnotherUnpaidInvoice() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày đã được giữ");
        product.setGia(BigDecimal.valueOf(1_000_000));
        product.setTonKho(10);
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
        when(chiTietHoaDonRepo.sumCommittedQuantity(2, "Chưa thanh toán", null))
                .thenReturn(7L);
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(5);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertTrue(exception.getMessage().contains("chỉ còn 3 sản phẩm khả dụng"));
        verify(donHangRepo, never()).save(any());
        verify(hoaDonRepo, never()).save(any());
    }

    @Test
    void createRejectsNonPositiveLineInsteadOfSilentlyDroppingIt() {
        KhachHang customer = new KhachHang();
        customer.setId(1);
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        HoaDonController.InvoiceForm form = new HoaDonController.InvoiceForm();
        form.maKhachHang = 1;
        form.sanPhamIds = List.of(2, 3);
        form.soLuongs = List.of(1, 0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(session, form, new RedirectAttributesModelMap())
        );

        assertEquals("Số lượng sản phẩm phải lớn hơn 0.", exception.getMessage());
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
        verify(hoaDonRepo, never()).save(any());
    }

    @Test
    void paymentMarksInvoicePaidAndDecrementsStock() {
        DonHang order = new DonHang();
        order.setTrangThai("Đang xử lý");
        KhachHang customer = new KhachHang();
        customer.setNgaySinh(LocalDate.now().minusYears(15));
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(loggedInAccount.getMaNhanVien());
        invoice.setTrangThai("Chưa thanh toán");
        invoice.setTongTien(BigDecimal.valueOf(2_000_000));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày kiểm thử");
        product.setGia(BigDecimal.valueOf(1_000_000));
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
    void paymentRejectsExistingPaymentBeforeDeductingStock() {
        KhachHang customer = new KhachHang();
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        DonHang order = new DonHang();
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(loggedInAccount.getMaNhanVien());
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(thanhToanRepo.existsByMaHoaDonId(7)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        assertEquals("Hóa đơn đã có giao dịch thanh toán.", exception.getMessage());
        verify(chiTietHoaDonRepo, never()).findByMaHoaDonId(7);
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void paymentRecalculatesTotalFromCurrentServerPromotion() {
        KhachHang customer = new KhachHang();
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        DonHang order = new DonHang();
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(loggedInAccount.getMaNhanVien());
        invoice.setTrangThai("Chưa thanh toán");
        invoice.setTongTien(BigDecimal.ONE);
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày khuyến mại");
        product.setGia(new BigDecimal("1000000"));
        product.setTonKho(6);
        ChiTietSanPham productDetail = new ChiTietSanPham();
        productDetail.setMaSP(product);
        ChiTietHoaDon line = new ChiTietHoaDon();
        line.setMaChiTietSP(productDetail);
        line.setSoLuong(2);
        line.setDonGia(BigDecimal.ONE);
        KhuyenMai promotion = new KhuyenMai();
        promotion.setTenKhuyenMai("Giảm hiện tại");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(chiTietHoaDonRepo.findByMaHoaDonId(7)).thenReturn(List.of(line));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
        when(promotionService.quoteProducts(any(), any(LocalDateTime.class))).thenReturn(Map.of(
                2, new KhuyenMaiService.PriceQuote(
                        new BigDecimal("1000000"), new BigDecimal("850000.00"), promotion
                )
        ));

        controller.pay(7, session, new RedirectAttributesModelMap());

        assertEquals(new BigDecimal("1700000.00"), invoice.getTongTien());
        assertEquals(new BigDecimal("1700000.00"), order.getTongTien());
        assertEquals(new BigDecimal("850000.00"), line.getDonGia());
        assertSame(promotion, line.getMaKhuyenMai());
        verify(notificationService).notifyStockThreshold(product, 6, 7);
        ArgumentCaptor<ThanhToan> payment = ArgumentCaptor.forClass(ThanhToan.class);
        verify(thanhToanRepo).save(payment.capture());
        assertEquals(new BigDecimal("1700000.00"), payment.getValue().getSoTien());
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
    void paymentRechecksCustomerPurchaseAge() {
        KhachHang customer = new KhachHang();
        customer.setNgaySinh(LocalDate.now().minusYears(15).plusDays(1));
        DonHang order = new DonHang();
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        assertEquals("Khách hàng phải đủ 15 tuổi trở lên để mua hàng.", exception.getMessage());
        verify(chiTietHoaDonRepo, never()).findByMaHoaDonId(7);
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
        verify(thanhToanRepo, never()).save(any());
    }

    @Test
    void paymentRejectsArchivedCustomer() {
        KhachHang customer = new KhachHang();
        customer.setTrangThai("ARCHIVED");
        DonHang order = new DonHang();
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        assertEquals("Khách hàng không còn đủ điều kiện thanh toán.", exception.getMessage());
        verify(chiTietHoaDonRepo, never()).findByMaHoaDonId(7);
        verify(thanhToanRepo, never()).save(any());
    }

    @Test
    void paymentRejectsInactiveProductDetail() {
        KhachHang customer = new KhachHang();
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        DonHang order = new DonHang();
        order.setMaKH(customer);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(loggedInAccount.getMaNhanVien());
        invoice.setTrangThai("Chưa thanh toán");
        SanPham product = new SanPham();
        product.setId(2);
        ChiTietSanPham productDetail = new ChiTietSanPham();
        productDetail.setMaSP(product);
        productDetail.setTrangThai("INACTIVE");
        ChiTietHoaDon line = new ChiTietHoaDon();
        line.setMaChiTietSP(productDetail);
        line.setSoLuong(1);
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(chiTietHoaDonRepo.findByMaHoaDonId(7)).thenReturn(List.of(line));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        assertEquals("Chi tiết sản phẩm đã ngừng hoạt động.", exception.getMessage());
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
        verify(thanhToanRepo, never()).save(any());
    }

    @Test
    void employeeCannotPayAnotherEmployeesInvoice() {
        NhanVien actor = new NhanVien();
        actor.setId(44);
        NhanVien owner = new NhanVien();
        owner.setId(45);
        loggedInAccount.setVaiTro("Nhân viên");
        loggedInAccount.setMaNhanVien(actor);
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaNhanVien(owner);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));

        assertThrows(
                AccessDeniedException.class,
                () -> controller.pay(7, session, new RedirectAttributesModelMap())
        );

        verify(chiTietHoaDonRepo, never()).findByMaHoaDonId(7);
        verify(sanPhamRepo, never()).findByIdForUpdate(anyInt());
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
        customer.setNgaySinh(LocalDate.now().minusYears(20));
        SanPham product = new SanPham();
        product.setId(2);
        product.setTenSP("Giày nhân viên bán");
        product.setGia(BigDecimal.valueOf(900_000));
        product.setTonKho(4);
        ChiTietSanPham detailProduct = new ChiTietSanPham();
        detailProduct.setId(3);
        detailProduct.setMaSP(product);

        when(khachHangRepo.findById(1)).thenReturn(Optional.of(customer));
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
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
        newCustomer.setNgaySinh(LocalDate.now().minusYears(20));
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
        when(sanPhamRepo.findByIdForUpdate(2)).thenReturn(Optional.of(product));
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
        assertEquals("Quản trị viên", historyCaptor.getValue().getNguoiChinhSua());
        assertTrue(historyCaptor.getValue().getDuLieuTruoc().contains("Khách cũ"));
        assertTrue(historyCaptor.getValue().getDuLieuSau().contains("Khách mới"));
        assertTrue(historyCaptor.getValue().getDuLieuSau().contains("Giày kiểm thử"));
        verify(chiTietHoaDonRepo).sumCommittedQuantity(2, "Chưa thanh toán", 7);
    }

    @Test
    void cancellingInvoiceIsTerminalAndAuditedWithReason() {
        DonHang order = new DonHang();
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setTrangThai("Chưa thanh toán");
        invoice.setTongTien(BigDecimal.valueOf(1_000_000));
        when(hoaDonRepo.findByIdForUpdate(7)).thenReturn(Optional.of(invoice));
        when(chiTietHoaDonRepo.findByMaHoaDonId(7)).thenReturn(List.of());

        String view = controller.cancel(
                7,
                "Khách đổi ý",
                session,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/hoadon/7", view);
        assertEquals("Đã hủy", invoice.getTrangThai());
        assertEquals("Đã hủy", order.getTrangThai());
        ArgumentCaptor<LichSuChinhSuaHoaDon> history =
                ArgumentCaptor.forClass(LichSuChinhSuaHoaDon.class);
        verify(invoiceHistoryRepo).save(history.capture());
        assertTrue(history.getValue().getDuLieuSau().contains("Lý do hủy: Khách đổi ý"));
    }

    @Test
    void cancelledInvoiceCannotBeEditedOrReopened() {
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setTrangThai("Đã hủy");
        when(hoaDonRepo.findById(7)).thenReturn(Optional.of(invoice));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.updateForm(7, session, new ExtendedModelMap(), redirect);

        assertEquals("redirect:/hoadon/7", view);
        assertEquals(
                "Không thể chỉnh sửa hóa đơn đã thanh toán hoặc đã hủy.",
                redirect.getFlashAttributes().get("error")
        );
    }

    @Test
    void employeeCannotDeleteInvoice() {
        loggedInAccount.setVaiTro("Nhân viên");
        when(hoaDonRepo.existsById(7)).thenReturn(true);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.delete(7, session, redirect);

        assertEquals("redirect:/hoadon/7", view);
        assertEquals(
                "Hóa đơn đã phát hành không thể xóa. Hãy hủy hóa đơn chưa thanh toán nếu cần.",
                redirect.getFlashAttributes().get("error")
        );
        verify(chiTietHoaDonRepo, never()).deleteByMaHoaDonId(7);
        verify(hoaDonRepo, never()).delete(any(HoaDon.class));
    }
}
