package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChatLieu;
import com.example.qlchgiay.model.Loai;
import com.example.qlchgiay.model.Mau;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.Size;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChatLieuRepo;
import com.example.qlchgiay.repo.ChiTietSanPhamRepo;
import com.example.qlchgiay.repo.LoaiRepo;
import com.example.qlchgiay.repo.MauRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.SizeRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanPhamControllerTest {
    @Mock private SanPhamRepo sanPhamRepo;
    @Mock private LoaiRepo loaiRepo;
    @Mock private MauRepo mauRepo;
    @Mock private ChatLieuRepo chatLieuRepo;
    @Mock private SizeRepo sizeRepo;
    @Mock private ChiTietSanPhamRepo chiTietSanPhamRepo;
    @Mock private HttpSession session;

    private SanPhamController controller;

    @BeforeEach
    void setUp() {
        controller = new SanPhamController(
                sanPhamRepo,
                loaiRepo,
                mauRepo,
                chatLieuRepo,
                sizeRepo,
                chiTietSanPhamRepo
        );
        when(session.getAttribute("user")).thenReturn(new TaiKhoan());
    }

    @Test
    void createProductCanCreateCustomColor() {
        Loai category = new Loai();
        category.setId(10);
        ChatLieu material = new ChatLieu();
        material.setId(20);
        Size size = new Size();
        size.setId(30);
        when(loaiRepo.findById(10)).thenReturn(Optional.of(category));
        when(chatLieuRepo.findById(20)).thenReturn(Optional.of(material));
        when(sizeRepo.findById(30)).thenReturn(Optional.of(size));
        when(mauRepo.findFirstByTenMauIgnoreCase("Xanh rêu")).thenReturn(Optional.empty());
        when(mauRepo.save(any(Mau.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sanPhamRepo.findByTenSPIgnoreCase("Giày thử nghiệm")).thenReturn(List.of());

        String view = controller.create(
                session,
                "Giày thử nghiệm",
                10,
                null,
                20,
                30,
                null,
                "  Xanh   rêu ",
                null,
                null,
                BigDecimal.valueOf(1_500_000),
                8,
                null,
                null,
                new RedirectAttributesModelMap()
        );

        ArgumentCaptor<Mau> colorCaptor = ArgumentCaptor.forClass(Mau.class);
        ArgumentCaptor<SanPham> productCaptor = ArgumentCaptor.forClass(SanPham.class);
        verify(mauRepo).save(colorCaptor.capture());
        verify(sanPhamRepo).save(productCaptor.capture());

        assertEquals("redirect:/sanpham", view);
        assertEquals("Xanh rêu", colorCaptor.getValue().getTenMau());
        assertEquals("Xanh rêu", productCaptor.getValue().getMaMau().getTenMau());
    }

    @Test
    void createMatchingVariantAddsToExistingStock() {
        Loai category = new Loai();
        category.setId(1);
        Mau color = new Mau();
        color.setId(2);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Size size = new Size();
        size.setId(4);

        SanPham existing = new SanPham();
        existing.setId(99);
        existing.setTenSP("Nike Air Test");
        existing.setMaLoai(category);
        existing.setMaMau(color);
        existing.setMaChatLieu(material);
        existing.setMaSize(size);
        existing.setGia(BigDecimal.valueOf(1_900_000));
        existing.setTonKho(7);

        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(mauRepo.findById(2)).thenReturn(Optional.of(color));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size));
        when(sanPhamRepo.findByTenSPIgnoreCase("Nike Air Test")).thenReturn(List.of(existing));

        String view = controller.create(
                session,
                "  Nike Air Test ",
                1,
                2,
                3,
                4,
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(2_100_000),
                5,
                null,
                null,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/sanpham", view);
        assertEquals(12, existing.getTonKho());
        assertEquals(BigDecimal.valueOf(2_100_000), existing.getGia());
        verify(sanPhamRepo).save(existing);
    }

    @Test
    void createRequiresAllVariantComponents() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
                        session,
                        "Giày thiếu thuộc tính",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(1_000_000),
                        1,
                        null,
                        null,
                        new RedirectAttributesModelMap()
                )
        );

        assertEquals(
                "Vui lòng chọn hoặc thêm loại sản phẩm.",
                exception.getMessage()
        );
    }

    @Test
    void createRejectsPriceBelowOneMillion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
                        session,
                        "Giày giá thấp",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(999_999),
                        1,
                        null,
                        null,
                        new RedirectAttributesModelMap()
                )
        );

        assertEquals("Giá bán phải từ 1.000.000 VNĐ trở lên.", exception.getMessage());
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void createRejectsZeroStock() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
                        session,
                        "Giày hết tồn",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(1_000_000),
                        0,
                        null,
                        null,
                        new RedirectAttributesModelMap()
                )
        );

        assertEquals("Số lượng tồn phải lớn hơn 0.", exception.getMessage());
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void employeeCannotDeleteProduct() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.delete(7, session, redirect);

        assertEquals("redirect:/sanpham", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền xóa sản phẩm.",
                redirect.getFlashAttributes().get("error")
        );
        verify(sanPhamRepo, never()).existsById(7);
        verify(sanPhamRepo, never()).deleteById(7);
    }
}
