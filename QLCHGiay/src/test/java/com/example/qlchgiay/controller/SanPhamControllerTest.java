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
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        TaiKhoan admin = new TaiKhoan();
        admin.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(admin);
    }

    @Test
    void createProductCanCreateCustomColor() {
        Loai category = new Loai();
        category.setId(10);
        ChatLieu material = new ChatLieu();
        material.setId(20);
        Size size = new Size();
        size.setId(30);
        size.setTenSize("40");
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
                "/images/products/nike.svg",
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
    void createMatchingVariantIsRejectedWithoutChangingExistingData() {
        Loai category = new Loai();
        category.setId(1);
        Mau color = new Mau();
        color.setId(2);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Size size = new Size();
        size.setId(4);
        size.setTenSize("40");

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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
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
                        "/images/products/nike.svg",
                        null,
                        new RedirectAttributesModelMap()
                )
        );

        assertTrue(exception.getMessage().contains("#SP-99"));
        assertEquals(7, existing.getTonKho());
        assertEquals(BigDecimal.valueOf(1_900_000), existing.getGia());
        verify(sanPhamRepo, never()).save(any());
        verify(sanPhamRepo, never()).flush();
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
                        "/images/products/nike.svg",
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
    void createRejectsPriceThatIsNotDivisibleByOneThousand() {
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
                        BigDecimal.valueOf(2_690_500),
                        1,
                        null,
                        null,
                        new RedirectAttributesModelMap()
                )
        );

        assertEquals("Giá bán phải là số nguyên dương và chia hết cho 1.000 VNĐ.", exception.getMessage());
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void createAcceptsPositivePriceBelowOneMillionWhenItUsesTheRequiredStep() {
        Loai category = new Loai();
        category.setId(1);
        Mau color = new Mau();
        color.setId(2);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Size size = new Size();
        size.setId(4);
        size.setTenSize("40");
        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(mauRepo.findById(2)).thenReturn(Optional.of(color));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size));
        when(sanPhamRepo.findByTenSPIgnoreCase("Giày giá hợp lệ")).thenReturn(List.of());

        String view = controller.create(
                session, "Giày giá hợp lệ", 1, 2, 3, 4,
                null, null, null, null,
                BigDecimal.valueOf(500_000), 1,
                "/images/products/nike.svg", null,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/sanpham", view);
        verify(sanPhamRepo).save(any(SanPham.class));
        verify(sanPhamRepo).flush();
    }

    @Test
    void createRequiresAnImage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
                        session, "Giày thiếu ảnh", 1, 2, 3, 4,
                        null, null, null, null,
                        BigDecimal.valueOf(2_690_000), 1,
                        null, null, new RedirectAttributesModelMap()
                )
        );

        assertEquals("Sản phẩm đang kinh doanh bắt buộc phải có ảnh.", exception.getMessage());
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void createRejectsDecimalSizeEvenWhenSubmittedDirectly() {
        Loai category = new Loai();
        category.setId(1);
        Mau color = new Mau();
        color.setId(2);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Size size = new Size();
        size.setId(4);
        size.setTenSize("42.5");
        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(mauRepo.findById(2)).thenReturn(Optional.of(color));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(
                        session, "Giày size lỗi", 1, 2, 3, 4,
                        null, null, null, null,
                        BigDecimal.valueOf(2_690_000), 1,
                        "/images/products/nike.svg", null,
                        new RedirectAttributesModelMap()
                )
        );

        assertTrue(exception.getMessage().contains("số nguyên dương"));
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
                        "/images/products/nike.svg",
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

    @Test
    void employeeCannotOpenProductCreateForm() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);

        assertEquals("redirect:/sanpham", controller.showCreate(session, new ExtendedModelMap()));
    }

    @Test
    void employeeCannotCreateOrRestockProduct() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.create(
                session, "Giày trái phép", 1, 2, 3, 4,
                null, null, null, null,
                BigDecimal.valueOf(2_000_000), 100,
                null, null, redirect
        );

        assertEquals("redirect:/sanpham", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền thêm sản phẩm hoặc thay đổi tồn kho.",
                redirect.getFlashAttributes().get("error")
        );
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void employeeCannotOpenProductEditForm() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.showUpdate(
                7,
                session,
                new ExtendedModelMap(),
                redirect
        );

        assertEquals("redirect:/sanpham", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền chỉnh sửa sản phẩm.",
                redirect.getFlashAttributes().get("error")
        );
        verify(sanPhamRepo, never()).findById(7);
    }

    @Test
    void employeeCannotOpenInactiveProductByGuessingItsUrl() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        SanPham product = new SanPham();
        product.setId(7);
        product.setTrangThai("INACTIVE");
        when(sanPhamRepo.findById(7)).thenReturn(Optional.of(product));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.detail(7, session, new ExtendedModelMap(), redirect);

        assertEquals("redirect:/sanpham", view);
        assertEquals("Sản phẩm này đã ngừng bán.", redirect.getFlashAttributes().get("error"));
    }

    @Test
    void batchCreateBuildsEverySelectedColorAndSizeAtomically() {
        Loai category = new Loai();
        category.setId(1);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Mau black = new Mau();
        black.setId(2);
        black.setTenMau("Đen");
        Mau white = new Mau();
        white.setId(5);
        white.setTenMau("Trắng");
        Size size40 = new Size();
        size40.setId(4);
        size40.setTenSize("40");
        Size size41 = new Size();
        size41.setId(6);
        size41.setTenSize("41");
        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(mauRepo.findById(2)).thenReturn(Optional.of(black));
        when(mauRepo.findById(5)).thenReturn(Optional.of(white));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size40));
        when(sizeRepo.findById(6)).thenReturn(Optional.of(size41));
        when(sanPhamRepo.findByTenSPIgnoreCase("Giày lô"))
                .thenReturn(List.of());
        List<SanPham> saved = new ArrayList<>();
        when(sanPhamRepo.saveAll(anyList())).thenAnswer(invocation -> {
            List<SanPham> variants = invocation.getArgument(0);
            saved.addAll(variants);
            return variants;
        });

        String view = controller.createBatch(
                session, " Giày lô ", 1, List.of(2, 5), 3, List.of(4, 6),
                null, null, null, null,
                BigDecimal.valueOf(1_500_000), 8, "/images/products/nike.svg", null,
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/sanpham", view);
        assertEquals(4, saved.size());
        assertTrue(saved.stream().allMatch(item -> item.getTonKho() == 8));
        verify(sanPhamRepo).flush();
    }

    @Test
    void batchCreateRejectsExistingVariantBeforeSavingAnything() {
        Loai category = new Loai();
        category.setId(1);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Mau color = new Mau();
        color.setId(2);
        color.setTenMau("Đen");
        Size size = new Size();
        size.setId(4);
        size.setTenSize("40");
        SanPham existing = new SanPham();
        existing.setId(99);
        existing.setTenSP("Giày lô");
        existing.setMaLoai(category);
        existing.setMaMau(color);
        existing.setMaChatLieu(material);
        existing.setMaSize(size);
        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(mauRepo.findById(2)).thenReturn(Optional.of(color));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size));
        when(sanPhamRepo.findByTenSPIgnoreCase("Giày lô"))
                .thenReturn(List.of(existing));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.createBatch(
                        session, "Giày lô", 1, List.of(2), 3, List.of(4),
                        null, null, null, null,
                        BigDecimal.valueOf(1_500_000), 8, "/images/products/nike.svg", null,
                        new RedirectAttributesModelMap()
                )
        );

        assertTrue(exception.getMessage().contains("Đen / 40 (#SP-99)"));
        verify(sanPhamRepo, never()).saveAll(anyList());
        verify(sanPhamRepo, never()).flush();
    }

    @Test
    void staleProductEditCannotOverwriteNewerStock() {
        SanPham product = new SanPham();
        product.setId(7);
        product.setVersion(4L);
        when(sanPhamRepo.findByIdForUpdate(7)).thenReturn(Optional.of(product));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.update(
                        7, session, "Giày cũ", 1, 2, 3, 4,
                        null, null, null, null,
                        BigDecimal.valueOf(1_500_000), 999,
                        null, null, 3L,
                        new RedirectAttributesModelMap()
                )
        );

        assertTrue(exception.getMessage().contains("đã thay đổi ở phiên khác"));
        verify(sanPhamRepo, never()).save(any());
    }

    @Test
    void updateRejectsACombinationOwnedByAnotherVariant() {
        Loai category = new Loai();
        category.setId(1);
        Mau color = new Mau();
        color.setId(2);
        ChatLieu material = new ChatLieu();
        material.setId(3);
        Size size = new Size();
        size.setId(4);
        size.setTenSize("42");

        SanPham current = new SanPham();
        current.setId(7);
        current.setVersion(4L);
        SanPham duplicate = new SanPham();
        duplicate.setId(8);
        duplicate.setTenSP("Giày trùng");
        duplicate.setMaLoai(category);
        duplicate.setMaMau(color);
        duplicate.setMaChatLieu(material);
        duplicate.setMaSize(size);

        when(sanPhamRepo.findByIdForUpdate(7)).thenReturn(Optional.of(current));
        when(loaiRepo.findById(1)).thenReturn(Optional.of(category));
        when(mauRepo.findById(2)).thenReturn(Optional.of(color));
        when(chatLieuRepo.findById(3)).thenReturn(Optional.of(material));
        when(sizeRepo.findById(4)).thenReturn(Optional.of(size));
        when(sanPhamRepo.findByTenSPIgnoreCase("Giày trùng"))
                .thenReturn(List.of(current, duplicate));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.update(
                        7, session, "Giày trùng", 1, 2, 3, 4,
                        null, null, null, null,
                        BigDecimal.valueOf(2_690_000), 5,
                        "/images/products/nike.svg", null, 4L,
                        new RedirectAttributesModelMap()
                )
        );

        assertTrue(exception.getMessage().contains("#SP-8"));
        verify(sanPhamRepo, never()).save(any());
        verify(sanPhamRepo, never()).flush();
    }
}
