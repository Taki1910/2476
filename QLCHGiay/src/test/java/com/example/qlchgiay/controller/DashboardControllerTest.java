package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChiTietHoaDonRepo;
import com.example.qlchgiay.repo.HoaDonRepo;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.repo.LoaiRepo;
import com.example.qlchgiay.repo.MauRepo;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.SizeRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
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

        verify(sanPhamRepo).findAll(expectedSort);
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

    private ExtendedModelMap renderProducts(String role, Sort expectedSort) {
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro(role);
        when(session.getAttribute("user")).thenReturn(account);
        when(sanPhamRepo.findAll(expectedSort)).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("sanpham", controller.sanPham(session, model));
        return model;
    }
}
