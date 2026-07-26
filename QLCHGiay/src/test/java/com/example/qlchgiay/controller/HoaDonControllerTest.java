package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChiTietSanPham;
import com.example.qlchgiay.model.DonHang;
import com.example.qlchgiay.model.HoaDon;
import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChiTietHoaDonRepo;
import com.example.qlchgiay.repo.ChiTietSanPhamRepo;
import com.example.qlchgiay.repo.DonHangRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private HoaDonController controller;

    @BeforeEach
    void setUp() {
        controller = new HoaDonController(
                hoaDonRepo,
                donHangRepo,
                nhanVienRepo,
                khachHangRepo,
                sanPhamRepo,
                chiTietSanPhamRepo,
                chiTietHoaDonRepo
        );
        when(session.getAttribute("user")).thenReturn(new TaiKhoan());
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
        form.ngayLap = LocalDate.now();
        form.trangThai = "Đã thanh toán";
        form.sanPhamIds = List.of(2);
        form.soLuongs = List.of(1);

        String view = controller.create(session, form, new RedirectAttributesModelMap());

        ArgumentCaptor<HoaDon> invoiceCaptor = ArgumentCaptor.forClass(HoaDon.class);
        verify(hoaDonRepo).save(invoiceCaptor.capture());
        assertEquals("redirect:/hoadon/99", view);
        assertEquals("Chưa thanh toán", invoiceCaptor.getValue().getTrangThai());
        assertEquals("Đang xử lý", invoiceCaptor.getValue().getMaDonHang().getTrangThai());
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
        assertEquals("Hoàn thành", order.getTrangThai());
        verify(hoaDonRepo).save(invoice);
        verify(donHangRepo).save(order);
    }
}
