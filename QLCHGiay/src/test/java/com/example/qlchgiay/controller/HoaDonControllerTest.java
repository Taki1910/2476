package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChiTietSanPham;
import com.example.qlchgiay.model.DonHang;
import com.example.qlchgiay.model.HoaDon;
import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChiTietHoaDonRepo;
import com.example.qlchgiay.repo.ChiTietSanPhamRepo;
import com.example.qlchgiay.repo.DonHangRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.service.WorkSessionService;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoaDonControllerTest {
    @Mock private HoaDonRepo hoaDonRepo;
    @Mock private DonHangRepo donHangRepo;
    @Mock private NhanVienRepo nhanVienRepo;
    @Mock private KhachHangRepo khachHangRepo;
    @Mock private SanPhamRepo sanPhamRepo;
    @Mock private ChiTietSanPhamRepo chiTietSanPhamRepo;
    @Mock private ChiTietHoaDonRepo chiTietHoaDonRepo;
    @Mock private HttpSession session;
    @Mock private WorkSessionService workSessionService;

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
                workSessionService
        );
        loggedInAccount = new TaiKhoan();
        loggedInAccount.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(loggedInAccount);
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
    void printMarksInvoiceAndOrderAsPaid() {
        DonHang order = new DonHang();
        order.setTrangThai("Đang xử lý");
        HoaDon invoice = new HoaDon();
        invoice.setId(7);
        invoice.setMaDonHang(order);
        invoice.setTrangThai("Chưa thanh toán");
        when(hoaDonRepo.findById(7)).thenReturn(Optional.of(invoice));
        String view = controller.print(
                7,
                session,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/hoadon/7?print=true", view);
        assertEquals("Đã thanh toán", invoice.getTrangThai());
        assertEquals("Đã thanh toán", order.getTrangThai());
        verify(hoaDonRepo).save(invoice);
        verify(donHangRepo).save(order);
    }

    @Test
    void employeeCreatesInvoiceUnderTheirOwnIdentity() {
        NhanVien currentEmployee = new NhanVien();
        currentEmployee.setId(44);
        currentEmployee.setTenNhanVien("Nhân viên hiện tại");
        loggedInAccount.setVaiTro("Nhân viên");
        loggedInAccount.setMaNhanVien(currentEmployee);

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
    }

    @Test
    void employeeCannotOpenInvoiceEditForm() {
        loggedInAccount.setVaiTro("Nhân viên");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.updateForm(
                7,
                session,
                new ExtendedModelMap(),
                redirect
        );

        assertEquals("redirect:/hoadon", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền chỉnh sửa hóa đơn.",
                redirect.getFlashAttributes().get("error")
        );
        verify(hoaDonRepo, never()).findById(anyInt());
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
