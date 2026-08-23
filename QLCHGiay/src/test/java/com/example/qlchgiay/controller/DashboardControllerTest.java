package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.model.HoaDon;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.PhienLamViec;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.ChiTietSanPham;
import com.example.qlchgiay.repo.ChiTietHoaDonRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.repo.LoaiRepo;
import com.example.qlchgiay.repo.MauRepo;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.SizeRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import com.example.qlchgiay.service.WorkSessionService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {
    @Mock private SanPhamRepo sanPhamRepo;
    @Mock private KhachHangRepo khachHangRepo;
    @Mock private HoaDonRepo hoaDonRepo;
    @Mock private NhaCungCapRepo nhaCungCapRepo;
    @Mock private LoaiRepo loaiRepo;
    @Mock private MauRepo mauRepo;
    @Mock private SizeRepo sizeRepo;
    @Mock private ChiTietHoaDonRepo chiTietHoaDonRepo;
    @Mock private TaiKhoanRepo taiKhoanRepo;
    @Mock private HttpSession session;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(
                sanPhamRepo,
                khachHangRepo,
                hoaDonRepo,
                nhaCungCapRepo,
                loaiRepo,
                mauRepo,
                sizeRepo,
                chiTietHoaDonRepo,
                taiKhoanRepo
        );
    }

    @Test
    void employeeSeesHighestStockFirst() {
        Sort expectedSort = Sort.by(
                Sort.Order.desc("tonKho"),
                Sort.Order.desc("id")
        );

        ExtendedModelMap model = renderProducts("Nhân viên", expectedSort);

        verify(sanPhamRepo).findByTrangThai("ACTIVE", expectedSort);
        assertEquals(
                "Sắp xếp mặc định: tồn kho cao nhất trước.",
                model.get("productOrderLabel")
        );
    }

    @Test
    void managerSeesNewestProductsFirst() {
        Sort expectedSort = Sort.by(Sort.Order.desc("id"));

        ExtendedModelMap model = renderProducts("Quản lý", expectedSort);

        verify(sanPhamRepo).findAll(expectedSort);
        assertEquals(
                "Sắp xếp mặc định: sản phẩm mới thêm trước.",
                model.get("productOrderLabel")
        );
    }

    @Test
    void productVariantsAreGroupedBeforePresentationWithStableAggregateSemantics() {
        TaiKhoan manager = new TaiKhoan();
        manager.setVaiTro("Quản lý");
        when(session.getAttribute("user")).thenReturn(manager);
        Sort expectedSort = Sort.by(Sort.Order.desc("id"));

        SanPham inactive = product(9, " Nike Air Test ", 3_000_000, 2, "INACTIVE", "/images/inactive.svg");
        SanPham active = product(5, "nike air test", 2_000_000, 7, "ACTIVE", "/images/active.svg");
        when(sanPhamRepo.findAll(expectedSort)).thenReturn(List.of(inactive, active));

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("sanpham", controller.sanPham(session, model));

        @SuppressWarnings("unchecked")
        List<DashboardController.ProductGroupView> groups =
                (List<DashboardController.ProductGroupView>) model.get("items");
        assertEquals(1, groups.size());
        DashboardController.ProductGroupView group = groups.get(0);
        assertEquals(2, group.getVariantCount());
        assertEquals(5, group.getRepresentativeId());
        assertEquals(9, group.getTotalStock());
        assertEquals("mixed", group.getBusinessStatus());
        assertEquals("2.000.000 đ – 3.000.000 đ", group.getPriceRange());
        assertEquals("/images/active.svg", group.getImage());
    }

    @Test
    void employeeGroupsAreOrderedByTotalStockNotHighestSingleVariant() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        Sort expectedSort = Sort.by(Sort.Order.desc("tonKho"), Sort.Order.desc("id"));
        SanPham groupA1 = product(1, "Nhóm A", 1_000_000, 6, "ACTIVE", null);
        SanPham groupB = product(2, "Nhóm B", 1_000_000, 9, "ACTIVE", null);
        SanPham groupA2 = product(3, "Nhóm A", 1_000_000, 6, "ACTIVE", null);
        when(sanPhamRepo.findByTrangThai("ACTIVE", expectedSort))
                .thenReturn(List.of(groupB, groupA2, groupA1));

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("sanpham", controller.sanPham(session, model));

        @SuppressWarnings("unchecked")
        List<DashboardController.ProductGroupView> groups =
                (List<DashboardController.ProductGroupView>) model.get("items");
        assertEquals("Nhóm A", groups.get(0).getName());
        assertEquals(12, groups.get(0).getTotalStock());
    }

    @Test
    void employeeOnlyGetsEditActionForOwnInvoiceInCurrentSession() {
        NhanVien employee = new NhanVien();
        employee.setId(2);
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro("Nhân viên");
        account.setMaNhanVien(employee);
        PhienLamViec currentSession = new PhienLamViec();
        currentSession.setId(7);
        PhienLamViec previousSession = new PhienLamViec();
        previousSession.setId(6);
        HoaDon currentInvoice = invoice(11, employee, currentSession);
        HoaDon previousInvoice = invoice(12, employee, previousSession);
        when(session.getAttribute("user")).thenReturn(account);
        when(session.getAttribute(WorkSessionService.SESSION_ID_ATTRIBUTE)).thenReturn(7);
        when(hoaDonRepo.findByMaNhanVienIdOrderByIdDesc(2))
                .thenReturn(List.of(currentInvoice, previousInvoice));

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("hoadon", controller.hoaDon(session, model));

        @SuppressWarnings("unchecked")
        Set<Integer> editableIds = (Set<Integer>) model.get("editableInvoiceIds");
        assertTrue(editableIds.contains(11));
        assertFalse(editableIds.contains(12));
    }

    @Test
    void employeeCannotViewSuppliers() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.nhaCungCap(session, new ExtendedModelMap())
        );
    }

    @Test
    void employeeCannotViewReports() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);

        assertThrows(
                AccessDeniedException.class,
                () -> controller.baoCao(session, new ExtendedModelMap(), "6")
        );
        verify(hoaDonRepo, never()).findAll();
        verify(chiTietHoaDonRepo, never()).findAll();
    }

    @Test
    void adminCanViewReports() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Quản lý");
        when(session.getAttribute("user")).thenReturn(admin);
        when(hoaDonRepo.findAll()).thenReturn(List.of());
        when(chiTietHoaDonRepo.findAll()).thenReturn(List.of());
        when(sanPhamRepo.findAll()).thenReturn(List.of());

        assertEquals("baocao", controller.baoCao(session, new ExtendedModelMap(), "6"));
    }

    @Test
    void rollingTwelveMonthsAndCurrentYearHaveDifferentBoundaries() {
        YearMonth august2026 = YearMonth.of(2026, 8);

        List<YearMonth> rolling = controller.reportMonths("12", august2026);
        List<YearMonth> year = controller.reportMonths("year", august2026);

        assertEquals(YearMonth.of(2025, 9), rolling.get(0));
        assertEquals(august2026, rolling.get(11));
        assertEquals(YearMonth.of(2026, 1), year.get(0));
        assertEquals(august2026, year.get(7));
        assertEquals(12, rolling.size());
        assertEquals(8, year.size());
    }

    @Test
    void reportPeriodTotalsExcludeUnpaidAndOutOfRangeInvoices() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Quản lý");
        when(session.getAttribute("user")).thenReturn(admin);
        YearMonth current = YearMonth.now();
        HoaDon paidCurrent = reportInvoice(1, current.atDay(1), "Đã thanh toán", 1_000_000);
        HoaDon paidFifthPrevious = reportInvoice(
                2, current.minusMonths(5).atDay(1), "Hoàn thành", 2_000_000
        );
        HoaDon paidOutsideSixMonths = reportInvoice(
                3, current.minusMonths(6).atDay(1), "Đã thanh toán", 4_000_000
        );
        HoaDon unpaidCurrent = reportInvoice(4, current.atDay(1), "Chưa thanh toán", 9_000_000);
        when(hoaDonRepo.findAll()).thenReturn(List.of(
                paidCurrent, paidFifthPrevious, paidOutsideSixMonths, unpaidCurrent
        ));
        when(chiTietHoaDonRepo.findAll()).thenReturn(List.of());
        when(sanPhamRepo.findAll()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("baocao", controller.baoCao(session, model, "6"));

        assertEquals(BigDecimal.valueOf(3_000_000), model.get("periodRevenue"));
        assertEquals(2, model.get("periodInvoiceCount"));
        @SuppressWarnings("unchecked")
        List<BigDecimal> revenues = (List<BigDecimal>) model.get("monthRevenues");
        assertEquals(6, revenues.size());
        assertEquals(BigDecimal.valueOf(3_000_000),
                revenues.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(revenues.stream().anyMatch(value -> value.signum() == 0));
    }

    private ExtendedModelMap renderProducts(String role, Sort expectedSort) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        when(session.getAttribute("user")).thenReturn(account);
        if (role.equals("Nhân viên")) {
            when(sanPhamRepo.findByTrangThai("ACTIVE", expectedSort)).thenReturn(List.of());
        } else {
            when(sanPhamRepo.findAll(expectedSort)).thenReturn(List.of());
        }

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("sanpham", controller.sanPham(session, model));
        return model;
    }

    private HoaDon invoice(int id, NhanVien employee, PhienLamViec workSession) {
        HoaDon invoice = new HoaDon();
        invoice.setId(id);
        invoice.setMaNhanVien(employee);
        invoice.setMaPhien(workSession);
        invoice.setTrangThai("Chưa thanh toán");
        return invoice;
    }

    private SanPham product(int id, String name, long price, int stock, String status, String image) {
        SanPham product = new SanPham();
        product.setId(id);
        product.setTenSP(name);
        product.setGia(BigDecimal.valueOf(price));
        product.setTonKho(stock);
        product.setTrangThai(status);
        if (image != null) {
            ChiTietSanPham detail = new ChiTietSanPham();
            detail.setId(id);
            detail.setHinhAnh(image);
            product.getChiTietSanPhams().add(detail);
        }
        return product;
    }

    private HoaDon reportInvoice(int id, LocalDate date, String status, long total) {
        HoaDon invoice = new HoaDon();
        invoice.setId(id);
        invoice.setNgayLap(date);
        invoice.setTrangThai(status);
        invoice.setTongTien(BigDecimal.valueOf(total));
        return invoice;
    }
}
