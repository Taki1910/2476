package com.example.qlchgiay.service;

import com.example.qlchgiay.model.KhuyenMai;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.repo.KhuyenMaiRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KhuyenMaiServiceTest {
    @Mock private KhuyenMaiRepo promotionRepo;
    @Mock private SanPhamRepo productRepo;

    @Test
    void appliesPercentagePromotionAtServer() {
        SanPham product = product(1, "1000000");
        KhuyenMai promotion = promotion(KhuyenMaiService.PERCENT, "15", product);
        when(promotionRepo.findActiveAt(any())).thenReturn(List.of(promotion));

        KhuyenMaiService.PriceQuote quote = new KhuyenMaiService(promotionRepo, productRepo)
                .quoteProducts(List.of(product), LocalDateTime.now()).get(1);

        assertEquals(new BigDecimal("1000000"), quote.originalPrice());
        assertEquals(new BigDecimal("850000.00"), quote.finalPrice());
        assertEquals(promotion, quote.promotion());
    }

    @Test
    void fixedDiscountNeverMakesPriceNegative() {
        SanPham product = product(1, "100000");
        KhuyenMai promotion = promotion(KhuyenMaiService.FIXED, "150000", product);
        when(promotionRepo.findActiveAt(any())).thenReturn(List.of(promotion));

        KhuyenMaiService.PriceQuote quote = new KhuyenMaiService(promotionRepo, productRepo)
                .quoteProducts(List.of(product), LocalDateTime.now()).get(1);

        assertEquals(new BigDecimal("0.00"), quote.finalPrice());
    }

    @Test
    void rejectsOverlappingPromotionForSameProduct() {
        SanPham product = product(1, "100000");
        KhuyenMai promotion = promotion(KhuyenMaiService.PERCENT, "10", product);
        promotion.setTenKhuyenMai("Trùng lịch");
        promotion.setBatDau(LocalDateTime.now());
        promotion.setKetThuc(LocalDateTime.now().plusDays(1));
        promotion.setTrangThai(true);
        when(productRepo.findByIdForUpdate(1)).thenReturn(java.util.Optional.of(product));
        when(promotionRepo.existsOverlapping(any(), any(), any(), any())).thenReturn(true);

        KhuyenMaiService service = new KhuyenMaiService(promotionRepo, productRepo);

        assertThrows(IllegalArgumentException.class, () -> service.save(promotion, List.of(1)));
        verify(promotionRepo, never()).save(any());
    }

    @Test
    void acceptsIntegerPercentageWithinRange() {
        SanPham product = product(1, "1000000");
        KhuyenMai promotion = promotion(KhuyenMaiService.PERCENT, "25", product);
        when(productRepo.findByIdForUpdate(1)).thenReturn(java.util.Optional.of(product));
        when(promotionRepo.save(any(KhuyenMai.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KhuyenMai saved = new KhuyenMaiService(promotionRepo, productRepo)
                .save(promotion, List.of(1));

        assertEquals(new BigDecimal("25"), saved.getGiaTri());
        verify(promotionRepo).save(any(KhuyenMai.class));
    }

    @Test
    void rejectsDecimalPercentage() {
        KhuyenMai promotion = promotion(
                KhuyenMaiService.PERCENT, "12.5", product(1, "1000000")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KhuyenMaiService(promotionRepo, productRepo)
                        .save(promotion, List.of(1))
        );
        verify(promotionRepo, never()).save(any());
    }

    @Test
    void rejectsPercentageOutsideOneToOneHundred() {
        KhuyenMai zero = promotion(KhuyenMaiService.PERCENT, "0", product(1, "1000000"));
        KhuyenMai overOneHundred = promotion(
                KhuyenMaiService.PERCENT, "101", product(1, "1000000")
        );
        KhuyenMaiService service = new KhuyenMaiService(promotionRepo, productRepo);

        assertThrows(IllegalArgumentException.class, () -> service.save(zero, List.of(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(overOneHundred, List.of(1))
        );
        verify(promotionRepo, never()).save(any());
    }

    @Test
    void rejectsDecimalFixedDiscount() {
        KhuyenMai promotion = promotion(
                KhuyenMaiService.FIXED, "150000.5", product(1, "1000000")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KhuyenMaiService(promotionRepo, productRepo)
                        .save(promotion, List.of(1))
        );
        verify(promotionRepo, never()).save(any());
    }

    private SanPham product(Integer id, String price) {
        SanPham product = new SanPham();
        product.setId(id);
        product.setGia(new BigDecimal(price));
        return product;
    }

    private KhuyenMai promotion(String type, String value, SanPham product) {
        KhuyenMai promotion = new KhuyenMai();
        promotion.setLoaiGiam(type);
        promotion.setGiaTri(new BigDecimal(value));
        promotion.setTenKhuyenMai("Khuyến mại kiểm thử");
        promotion.setBatDau(LocalDateTime.now());
        promotion.setKetThuc(LocalDateTime.now().plusDays(1));
        promotion.setTrangThai(true);
        promotion.setSanPhams(new LinkedHashSet<>(List.of(product)));
        return promotion;
    }
}
