package com.example.qlchgiay.service;

import com.example.qlchgiay.model.KhuyenMai;
import com.example.qlchgiay.model.SanPham;
import com.example.qlchgiay.repo.KhuyenMaiRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KhuyenMaiService {
    public static final String PERCENT = "PHAN_TRAM";
    public static final String FIXED = "SO_TIEN";

    private final KhuyenMaiRepo promotionRepo;
    private final SanPhamRepo productRepo;

    public KhuyenMaiService(KhuyenMaiRepo promotionRepo, SanPhamRepo productRepo) {
        this.promotionRepo = promotionRepo;
        this.productRepo = productRepo;
    }

    @Transactional(readOnly = true)
    public List<KhuyenMai> findAll() {
        return promotionRepo.findAll().stream()
                .sorted(Comparator.comparing(KhuyenMai::getBatDau).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<KhuyenMai> findById(Integer id) {
        return id == null ? Optional.empty() : promotionRepo.findById(id);
    }

    @Transactional
    public KhuyenMai save(KhuyenMai submitted, Collection<Integer> productIds) {
        validate(submitted, productIds);
        Set<Integer> ids = new LinkedHashSet<>(productIds);
        List<SanPham> products = ids.stream().sorted()
                .map(id -> productRepo.findByIdForUpdate(id).orElseThrow(
                        () -> new IllegalArgumentException(
                                "Có sản phẩm khuyến mại không còn tồn tại."
                        )
                ))
                .toList();
        if (products.stream().anyMatch(product -> !product.isActive())) {
            throw new IllegalArgumentException("Không thể áp dụng khuyến mại cho sản phẩm ngừng bán.");
        }
        if (Boolean.TRUE.equals(submitted.getTrangThai())
                && promotionRepo.existsOverlapping(
                        submitted.getId() == null ? -1 : submitted.getId(),
                        ids, submitted.getBatDau(), submitted.getKetThuc()
                )) {
            throw new IllegalArgumentException(
                    "Một hoặc nhiều sản phẩm đã có khuyến mại trùng thời gian."
            );
        }

        KhuyenMai promotion = submitted.getId() == null
                ? new KhuyenMai()
                : promotionRepo.findById(submitted.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Khuyến mại không tồn tại."));
        promotion.setTenKhuyenMai(submitted.getTenKhuyenMai().trim());
        promotion.setLoaiGiam(submitted.getLoaiGiam());
        promotion.setGiaTri(submitted.getGiaTri());
        promotion.setBatDau(submitted.getBatDau());
        promotion.setKetThuc(submitted.getKetThuc());
        promotion.setTrangThai(Boolean.TRUE.equals(submitted.getTrangThai()));
        promotion.setSanPhams(new LinkedHashSet<>(products));
        return promotionRepo.save(promotion);
    }

    @Transactional
    public KhuyenMai toggle(Integer id) {
        KhuyenMai promotion = promotionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Khuyến mại không tồn tại."));
        promotion.setTrangThai(!Boolean.TRUE.equals(promotion.getTrangThai()));
        return save(
                promotion,
                promotion.getSanPhams().stream().map(SanPham::getId).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<Integer, PriceQuote> quoteProducts(
            Collection<SanPham> products,
            LocalDateTime at
    ) {
        Map<Integer, PriceQuote> quotes = new LinkedHashMap<>();
        for (SanPham product : products) {
            BigDecimal original = Optional.ofNullable(product.getGia()).orElse(BigDecimal.ZERO);
            quotes.put(product.getId(), new PriceQuote(original, original, null));
        }
        for (KhuyenMai promotion : promotionRepo.findActiveAt(at)) {
            for (SanPham product : promotion.getSanPhams()) {
                PriceQuote current = quotes.get(product.getId());
                if (current == null) continue;
                BigDecimal finalPrice = discounted(current.originalPrice(), promotion);
                if (finalPrice.compareTo(current.finalPrice()) < 0) {
                    quotes.put(
                            product.getId(),
                            new PriceQuote(current.originalPrice(), finalPrice, promotion)
                    );
                }
            }
        }
        return quotes;
    }

    private void validate(KhuyenMai promotion, Collection<Integer> productIds) {
        if (promotion.getTenKhuyenMai() == null || promotion.getTenKhuyenMai().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập tên khuyến mại.");
        }
        if (!Set.of(PERCENT, FIXED).contains(promotion.getLoaiGiam())) {
            throw new IllegalArgumentException("Loại giảm giá không hợp lệ.");
        }
        if (promotion.getGiaTri() == null
                || promotion.getGiaTri().signum() <= 0
                || promotion.getGiaTri().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Giá trị giảm phải là số nguyên dương.");
        }
        if (PERCENT.equals(promotion.getLoaiGiam())
                && promotion.getGiaTri().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Mức giảm phần trăm phải từ 1% đến 100%.");
        }
        if (PERCENT.equals(promotion.getLoaiGiam())
                && promotion.getGiaTri().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Mức giảm phần trăm không được vượt quá 100%.");
        }
        if (promotion.getBatDau() == null || promotion.getKetThuc() == null
                || !promotion.getBatDau().isBefore(promotion.getKetThuc())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một sản phẩm.");
        }
    }

    private BigDecimal discounted(BigDecimal original, KhuyenMai promotion) {
        BigDecimal result = PERCENT.equals(promotion.getLoaiGiam())
                ? original.multiply(
                        BigDecimal.ONE.subtract(
                                promotion.getGiaTri().divide(
                                        BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP
                                )
                        )
                )
                : original.subtract(promotion.getGiaTri());
        return result.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public record PriceQuote(
            BigDecimal originalPrice,
            BigDecimal finalPrice,
            KhuyenMai promotion
    ) {
    }
}
