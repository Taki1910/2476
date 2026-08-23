package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChiTietSanPham;
import com.example.qlchgiay.model.NhaCungCap;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NhaCungCapControllerTest {
    @Mock private NhaCungCapRepo repo;
    @Mock private HttpSession session;

    private NhaCungCapController controller;

    @BeforeEach
    void setUp() {
        controller = new NhaCungCapController(repo);
    }

    @Test
    void employeeCannotDeactivateSupplier() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.deactivate(7, session, redirect);

        assertEquals("redirect:/nhacungcap", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền ngừng hợp tác với nhà cung cấp.",
                redirect.getFlashAttributes().get("error")
        );
        verify(repo, never()).findById(7);
        verify(repo, never()).deleteById(7);
    }

    @Test
    void adminDeactivatesSupplierWithoutBreakingHistoricalReference() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Quản lý");
        when(session.getAttribute("user")).thenReturn(admin);
        NhaCungCap supplier = supplier("Hoạt động");
        ChiTietSanPham historicalDetail = new ChiTietSanPham();
        historicalDetail.setMaNCC(supplier);
        when(repo.findById(7)).thenReturn(Optional.of(supplier));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/nhacungcap",
                controller.deactivate(7, session, redirect)
        );

        assertEquals("Ngừng hợp tác", supplier.getTrangThai());
        assertSame(supplier, historicalDetail.getMaNCC());
        assertEquals(
                "Đã ngừng hợp tác với nhà cung cấp.",
                redirect.getFlashAttributes().get("success")
        );
        verify(repo).save(supplier);
        verify(repo, never()).deleteById(7);

        ExtendedModelMap detailModel = new ExtendedModelMap();
        assertEquals(
                "nhacungcap-detail",
                controller.detail(7, session, detailModel, new RedirectAttributesModelMap())
        );
        assertSame(supplier, detailModel.get("item"));
    }

    @Test
    void repeatedDeactivationIsAnIdempotentNoOp() {
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(admin);
        NhaCungCap supplier = supplier("Ngừng hợp tác");
        when(repo.findById(7)).thenReturn(Optional.of(supplier));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals(
                "redirect:/nhacungcap",
                controller.deactivate(7, session, redirect)
        );

        assertEquals("Ngừng hợp tác", supplier.getTrangThai());
        assertEquals(
                "Nhà cung cấp đã ngừng hợp tác trước đó.",
                redirect.getFlashAttributes().get("success")
        );
        verify(repo, never()).save(supplier);
        verify(repo, never()).deleteById(7);
    }

    private NhaCungCap supplier(String status) {
        NhaCungCap supplier = new NhaCungCap();
        supplier.setId(7);
        supplier.setTenNCC("Nhà cung cấp lịch sử");
        supplier.setTrangThai(status);
        return supplier;
    }
}
