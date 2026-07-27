package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.Mau;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChatLieuRepo;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanPhamControllerTest {
    @Mock private SanPhamRepo sanPhamRepo;
    @Mock private LoaiRepo loaiRepo;
    @Mock private MauRepo mauRepo;
    @Mock private ChatLieuRepo chatLieuRepo;
    @Mock private SizeRepo sizeRepo;
    @Mock private HttpSession session;

    private SanPhamController controller;

    @BeforeEach
    void setUp() {
        controller = new SanPhamController(
                sanPhamRepo,
                loaiRepo,
                mauRepo,
                chatLieuRepo,
                sizeRepo
        );
        when(session.getAttribute("user")).thenReturn(new TaiKhoan());
    }

    @Test
    void createProductCanCreateCustomColor() {
        when(mauRepo.findFirstByTenMauIgnoreCase("Xanh rêu")).thenReturn(Optional.empty());
        when(mauRepo.save(any(Mau.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String view = controller.create(
                session,
                "Giày thử nghiệm",
                null,
                null,
                null,
                null,
                null,
                "  Xanh   rêu ",
                null,
                null,
                BigDecimal.valueOf(1_500_000),
                8,
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
}
