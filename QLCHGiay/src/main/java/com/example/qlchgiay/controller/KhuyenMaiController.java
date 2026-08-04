package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.KhuyenMai;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.service.KhuyenMaiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Controller
@RequestMapping("/khuyenmai")
public class KhuyenMaiController {
    private static final DateTimeFormatter FORM_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private final KhuyenMaiService promotionService;
    private final SanPhamRepo productRepo;

    public KhuyenMaiController(
            KhuyenMaiService promotionService,
            SanPhamRepo productRepo
    ) {
        this.promotionService = promotionService;
        this.productRepo = productRepo;
    }

    @GetMapping
    public String page(
            @RequestParam(name = "edit", required = false) Integer editId,
            HttpSession session,
            Model model
    ) {
        requireAdmin(session);
        KhuyenMai item = promotionService.findById(editId).orElseGet(this::newPromotion);
        loadPage(model, item, item.getSanPhams().stream().map(x -> x.getId()).toList());
        return "khuyenmai";
    }

    @PostMapping("/luu")
    public String save(
            @ModelAttribute KhuyenMai item,
            @RequestParam(name = "productIds", required = false) List<Integer> productIds,
            HttpSession session,
            Model model,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        try {
            KhuyenMai saved = promotionService.save(item, productIds);
            redirect.addFlashAttribute("success", "Đã lưu khuyến mại " + saved.getTenKhuyenMai() + ".");
            return "redirect:/khuyenmai?edit=" + saved.getId();
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            loadPage(model, item, productIds == null ? List.of() : productIds);
            return "khuyenmai";
        }
    }

    @PostMapping("/trang-thai/{id}")
    public String toggle(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        try {
            promotionService.toggle(id);
            redirect.addFlashAttribute("success", "Đã cập nhật trạng thái khuyến mại.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/khuyenmai";
    }

    private void loadPage(Model model, KhuyenMai item, Collection<Integer> selectedIds) {
        LocalDateTime now = LocalDateTime.now();
        List<KhuyenMai> promotions = promotionService.findAll();
        model.addAttribute("item", item);
        model.addAttribute("selectedProductIds", new LinkedHashSet<>(selectedIds));
        model.addAttribute("products", productRepo.findAllByOrderByTenSPAsc());
        model.addAttribute("promotions", promotions);
        model.addAttribute("now", now);
        YearMonth currentMonth = YearMonth.from(now);
        model.addAttribute("filterFrom", currentMonth.atDay(1));
        model.addAttribute("filterTo", currentMonth.atEndOfMonth());
        model.addAttribute(
                "activeCount",
                promotions.stream().filter(x -> isActive(x, now)).count()
        );
        model.addAttribute(
                "scheduledCount",
                promotions.stream().filter(x -> Boolean.TRUE.equals(x.getTrangThai())
                        && x.getBatDau().isAfter(now)).count()
        );
        model.addAttribute(
                "inactiveCount",
                promotions.stream().filter(x -> !Boolean.TRUE.equals(x.getTrangThai())).count()
        );
        model.addAttribute("percentType", KhuyenMaiService.PERCENT);
        model.addAttribute("fixedType", KhuyenMaiService.FIXED);
        model.addAttribute("startValue", item.getBatDau() == null ? "" : item.getBatDau().format(FORM_TIME));
        model.addAttribute("endValue", item.getKetThuc() == null ? "" : item.getKetThuc().format(FORM_TIME));
    }

    private KhuyenMai newPromotion() {
        LocalDateTime start = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        KhuyenMai promotion = new KhuyenMai();
        promotion.setLoaiGiam(KhuyenMaiService.PERCENT);
        promotion.setBatDau(start);
        promotion.setKetThuc(start.plusDays(7));
        promotion.setTrangThai(true);
        return promotion;
    }

    private boolean isActive(KhuyenMai promotion, LocalDateTime now) {
        return Boolean.TRUE.equals(promotion.getTrangThai())
                && !promotion.getBatDau().isAfter(now)
                && promotion.getKetThuc().isAfter(now);
    }

    private void requireAdmin(HttpSession session) {
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            throw new AccessDeniedException("Chỉ quản lý được quản lý khuyến mại.");
        }
    }
}
