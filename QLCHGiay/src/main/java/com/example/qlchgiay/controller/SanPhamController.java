package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.*;
import com.example.qlchgiay.repo.ChatLieuRepo;
import com.example.qlchgiay.repo.LoaiRepo;
import com.example.qlchgiay.repo.MauRepo;
import com.example.qlchgiay.repo.ChiTietSanPhamRepo;
import com.example.qlchgiay.repo.SanPhamRepo;
import com.example.qlchgiay.repo.SizeRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
public class SanPhamController {
    private static final BigDecimal PRICE_STEP = BigDecimal.valueOf(1_000);
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final SanPhamRepo sanPhamRepo;
    private final LoaiRepo loaiRepo;
    private final MauRepo mauRepo;
    private final ChatLieuRepo chatLieuRepo;
    private final SizeRepo sizeRepo;
    private final ChiTietSanPhamRepo chiTietSanPhamRepo;

    @org.springframework.beans.factory.annotation.Autowired
    public SanPhamController(SanPhamRepo sanPhamRepo, LoaiRepo loaiRepo, MauRepo mauRepo,
                             ChatLieuRepo chatLieuRepo, SizeRepo sizeRepo,
                             ChiTietSanPhamRepo chiTietSanPhamRepo) {
        this.sanPhamRepo = sanPhamRepo;
        this.loaiRepo = loaiRepo;
        this.mauRepo = mauRepo;
        this.chatLieuRepo = chatLieuRepo;
        this.sizeRepo = sizeRepo;
        this.chiTietSanPhamRepo = chiTietSanPhamRepo;
    }

    SanPhamController(SanPhamRepo sanPhamRepo, LoaiRepo loaiRepo, MauRepo mauRepo,
                      ChatLieuRepo chatLieuRepo, SizeRepo sizeRepo) {
        this(sanPhamRepo, loaiRepo, mauRepo, chatLieuRepo, sizeRepo, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleInvalidInput(
            IllegalArgumentException exception,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute("error", exception.getMessage());
        return "redirect:/sanpham";
    }

    @GetMapping("/sanpham/them")
    public String showCreate(HttpSession session, Model model) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) return "redirect:/sanpham";
        model.addAttribute("sanPham", new SanPham());
        model.addAttribute("imageUrl", null);
        model.addAttribute("pageTitle", "Thêm sản phẩm");
        loadOptions(model);
        return "sanpham-form";
    }

    @PostMapping("/sanpham/them")
    @Transactional
    public String create(HttpSession session, @RequestParam String tenSP,
                         @RequestParam(required = false) Integer maLoai,
                         @RequestParam(required = false) Integer maMau,
                         @RequestParam(required = false) Integer maChatLieu,
                         @RequestParam(required = false) Integer maSize,
                         @RequestParam(required = false) String tenLoaiMoi,
                         @RequestParam(required = false) String tenMauMoi,
                         @RequestParam(required = false) String tenChatLieuMoi,
                         @RequestParam(required = false) String tenSizeMoi,
                         @RequestParam BigDecimal gia, @RequestParam Integer tonKho,
                         @RequestParam(required = false) String hinhAnh,
                         @RequestParam(required = false) MultipartFile hinhAnhFile,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền thêm sản phẩm hoặc thay đổi tồn kho."
            );
            return "redirect:/sanpham";
        }
        validateImageFile(hinhAnhFile);
        boolean hasUploadedImage = hinhAnhFile != null && !hinhAnhFile.isEmpty();
        String imageForValidation = hasUploadedImage ? "/uploads/products/new-image" : hinhAnh;
        SanPham sanPham = new SanPham();
        applyForm(
                sanPham, tenSP, maLoai, maMau, maChatLieu, maSize,
                tenLoaiMoi, tenMauMoi, tenChatLieuMoi, tenSizeMoi,
                gia, tonKho, imageForValidation
        );
        SanPham existingVariant = findMatchingVariant(sanPham);
        if (existingVariant != null) {
            throw new IllegalArgumentException(
                    "Biến thể đã tồn tại (#SP-" + existingVariant.getId()
                            + "). Hãy chỉnh sửa biến thể hiện có thay vì tạo trùng."
            );
        }
        try {
            sanPhamRepo.save(sanPham);
            sanPhamRepo.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException(
                    "Biến thể vừa được tạo ở phiên khác. Hãy tải lại danh sách.",
                    exception
            );
        }
        String imagePath = hasUploadedImage ? storeImage(hinhAnhFile) : hinhAnh;
        saveProductImage(sanPham, imagePath);
        redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm thành công.");
        return "redirect:/sanpham";
    }

    @PostMapping("/sanpham/them-hang-loat")
    @Transactional
    public String createBatch(
            HttpSession session,
            @RequestParam String tenSP,
            @RequestParam(required = false) Integer maLoai,
            @RequestParam(required = false) List<Integer> maMaus,
            @RequestParam(required = false) Integer maChatLieu,
            @RequestParam(required = false) List<Integer> maSizes,
            @RequestParam(required = false) String tenLoaiMoi,
            @RequestParam(required = false) String tenMauMoi,
            @RequestParam(required = false) String tenChatLieuMoi,
            @RequestParam(required = false) String tenSizeMoi,
            @RequestParam BigDecimal gia,
            @RequestParam Integer tonKho,
            @RequestParam(required = false) String hinhAnh,
            @RequestParam(required = false) MultipartFile hinhAnhFile,
            RedirectAttributes redirectAttributes
    ) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền tạo biến thể hoặc thay đổi tồn kho."
            );
            return "redirect:/sanpham";
        }

        validateImageFile(hinhAnhFile);
        String productName = validateProductBasics(
                tenSP,
                gia,
                tonKho,
                hinhAnhFile != null && !hinhAnhFile.isEmpty() ? "/uploads/products/new-image" : hinhAnh
        );
        Loai category = resolveLoai(maLoai, tenLoaiMoi);
        ChatLieu material = resolveChatLieu(maChatLieu, tenChatLieuMoi);
        List<Mau> colors = resolveColors(maMaus, tenMauMoi);
        List<Size> sizes = resolveSizes(maSizes, tenSizeMoi);
        List<SanPham> existingVariants = sanPhamRepo.findByTenSPIgnoreCase(productName);

        List<String> duplicates = new ArrayList<>();
        for (Mau color : colors) {
            for (Size size : sizes) {
                existingVariants.stream()
                        .filter(existing -> sameReference(existing.getMaLoai(), category))
                        .filter(existing -> sameReference(existing.getMaMau(), color))
                        .filter(existing -> sameReference(existing.getMaChatLieu(), material))
                        .filter(existing -> sameReference(existing.getMaSize(), size))
                        .findFirst()
                        .ifPresent(existing -> duplicates.add(
                                color.getTenMau() + " / " + size.getTenSize()
                                        + " (#SP-" + existing.getId() + ")"
                        ));
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Không thể tạo lô vì biến thể đã tồn tại: " + String.join(", ", duplicates) + "."
            );
        }

        List<SanPham> variants = new ArrayList<>();
        for (Mau color : colors) {
            for (Size size : sizes) {
                SanPham variant = new SanPham();
                variant.setTenSP(productName);
                variant.setMaLoai(category);
                variant.setMaMau(color);
                variant.setMaChatLieu(material);
                variant.setMaSize(size);
                variant.setGia(gia);
                variant.setTonKho(tonKho);
                variant.setTrangThai("ACTIVE");
                variants.add(variant);
            }
        }

        try {
            variants = sanPhamRepo.saveAll(variants);
            sanPhamRepo.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException(
                    "Một biến thể vừa được tạo ở phiên khác. Hãy tải lại và chọn lại.",
                    exception
            );
        }

        String imagePath = hinhAnhFile != null && !hinhAnhFile.isEmpty()
                ? storeImage(hinhAnhFile)
                : hinhAnh;
        for (SanPham variant : variants) {
            saveProductImage(variant, imagePath);
        }
        redirectAttributes.addFlashAttribute(
                "success",
                "Đã tạo " + variants.size() + " biến thể sản phẩm trong một giao dịch."
        );
        return "redirect:/sanpham";
    }

    @GetMapping("/sanpham/{id}")
    public String detail(@PathVariable Integer id, HttpSession session, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        if (SessionUserControllerAdvice.isEmployee(session) && !sanPham.isActive()) {
            redirectAttributes.addFlashAttribute("error", "Sản phẩm này đã ngừng bán.");
            return "redirect:/sanpham";
        }
        model.addAttribute("sanPham", sanPham);
        var variants = sanPhamRepo.findByTenSPIgnoreCase(sanPham.getTenSP()).stream()
                .filter(item -> !SessionUserControllerAdvice.isEmployee(session) || item.isActive())
                .toList();
        model.addAttribute("variants", variants);
        model.addAttribute("imageUrl", imageUrl(sanPham.getHinhAnh()));
        return "sanpham-detail";
    }

    @GetMapping("/sanpham/sua/{id}")
    public String showUpdate(@PathVariable Integer id, HttpSession session, Model model,
                             RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền chỉnh sửa sản phẩm."
            );
            return "redirect:/sanpham";
        }
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        model.addAttribute("sanPham", sanPham);
        model.addAttribute("imageUrl", imageUrl(sanPham.getHinhAnh()));
        model.addAttribute("pageTitle", "Cập nhật sản phẩm");
        loadOptions(model);
        return "sanpham-form";
    }

    @PostMapping("/sanpham/sua/{id}")
    @Transactional
    public String update(@PathVariable Integer id, HttpSession session,
                         @RequestParam String tenSP,
                         @RequestParam(required = false) Integer maLoai,
                         @RequestParam(required = false) Integer maMau,
                         @RequestParam(required = false) Integer maChatLieu,
                         @RequestParam(required = false) Integer maSize,
                         @RequestParam(required = false) String tenLoaiMoi,
                         @RequestParam(required = false) String tenMauMoi,
                         @RequestParam(required = false) String tenChatLieuMoi,
                         @RequestParam(required = false) String tenSizeMoi,
                         @RequestParam BigDecimal gia, @RequestParam Integer tonKho,
                         @RequestParam(required = false) String hinhAnh,
                         @RequestParam(required = false) MultipartFile hinhAnhFile,
                         @RequestParam("version") Long expectedVersion,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền chỉnh sửa sản phẩm."
            );
            return "redirect:/sanpham";
        }
        validateImageFile(hinhAnhFile);
        boolean hasUploadedImage = hinhAnhFile != null && !hinhAnhFile.isEmpty();
        String imagePath = hasUploadedImage ? "/uploads/products/new-image" : hinhAnh;
        SanPham sanPham = sanPhamRepo.findByIdForUpdate(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        if (!Objects.equals(sanPham.getVersion(), expectedVersion)) {
            throw new IllegalArgumentException(
                    "Sản phẩm đã thay đổi ở phiên khác. Vui lòng tải lại trước khi lưu."
            );
        }
        if ((imagePath == null || imagePath.isBlank()) && sanPham.getHinhAnh() != null) {
            imagePath = sanPham.getHinhAnh();
        }
        applyForm(
                sanPham, tenSP, maLoai, maMau, maChatLieu, maSize,
                tenLoaiMoi, tenMauMoi, tenChatLieuMoi, tenSizeMoi,
                gia, tonKho, imagePath
        );
        SanPham duplicate = findMatchingVariant(sanPham, id);
        if (duplicate != null) {
            throw new IllegalArgumentException(
                    "Không thể cập nhật vì tổ hợp loại, màu, chất liệu và size đã tồn tại ở #SP-"
                            + duplicate.getId() + "."
            );
        }
        try {
            sanPhamRepo.save(sanPham);
            sanPhamRepo.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException(
                    "Biến thể trùng với dữ liệu vừa được cập nhật ở phiên khác. Hãy tải lại.",
                    exception
            );
        }
        if (hasUploadedImage) imagePath = storeImage(hinhAnhFile);
        saveProductImage(sanPham, imagePath);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật sản phẩm thành công.");
        return "redirect:/sanpham";
    }

    @PostMapping("/sanpham/xoa/{id}")
    @Transactional
    public String delete(@PathVariable Integer id, HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Tài khoản nhân viên không có quyền xóa sản phẩm."
            );
            return "redirect:/sanpham";
        }
        SanPham product = sanPhamRepo.findByIdForUpdate(id).orElse(null);
        if (product == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        product.setTrangThai(product.isActive() ? "INACTIVE" : "ACTIVE");
        sanPhamRepo.save(product);
        redirectAttributes.addFlashAttribute(
                "success",
                product.isActive() ? "Đã kích hoạt lại sản phẩm." : "Đã ngừng bán sản phẩm."
        );
        return "redirect:/sanpham";
    }

    private void applyForm(SanPham sanPham, String tenSP, Integer maLoai, Integer maMau,
                           Integer maChatLieu, Integer maSize,
                           String tenLoaiMoi, String tenMauMoi,
                           String tenChatLieuMoi, String tenSizeMoi,
                           BigDecimal gia, Integer tonKho, String hinhAnh) {
        String normalizedName = tenSP == null ? "" : tenSP.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Tên sản phẩm phải có từ 1 đến 100 ký tự.");
        }
        if (!isValidPrice(gia)) {
            throw new IllegalArgumentException("Giá bán phải là số nguyên dương và chia hết cho 1.000 VNĐ.");
        }
        if (tonKho == null || tonKho <= 0) {
            throw new IllegalArgumentException("Số lượng tồn phải lớn hơn 0.");
        }
        sanPham.setTenSP(normalizedName);
        sanPham.setGia(gia);
        sanPham.setTonKho(tonKho);
        requireImage(hinhAnh);
        sanPham.setMaLoai(resolveLoai(maLoai, tenLoaiMoi));
        sanPham.setMaMau(resolveMau(maMau, tenMauMoi));
        sanPham.setMaChatLieu(resolveChatLieu(maChatLieu, tenChatLieuMoi));
        sanPham.setMaSize(resolveSize(maSize, tenSizeMoi));
    }

    private String validateProductBasics(
            String tenSP,
            BigDecimal gia,
            Integer tonKho,
            String image
    ) {
        String normalizedName = tenSP == null ? "" : tenSP.trim().replaceAll("\\s+", " ");
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Tên sản phẩm phải có từ 1 đến 100 ký tự.");
        }
        if (!isValidPrice(gia)) {
            throw new IllegalArgumentException("Giá bán phải là số nguyên dương và chia hết cho 1.000 VNĐ.");
        }
        if (tonKho == null || tonKho <= 0) {
            throw new IllegalArgumentException("Số lượng tồn phải lớn hơn 0.");
        }
        requireImage(image);
        return normalizedName;
    }

    private boolean isValidPrice(BigDecimal price) {
        return price != null
                && price.signum() > 0
                && price.stripTrailingZeros().scale() <= 0
                && price.remainder(PRICE_STEP).signum() == 0;
    }

    private void requireImage(String image) {
        if (normalizeImage(image) == null) {
            throw new IllegalArgumentException("Sản phẩm đang kinh doanh bắt buộc phải có ảnh.");
        }
    }

    private List<Mau> resolveColors(List<Integer> ids, String customName) {
        LinkedHashSet<Mau> colors = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).distinct().forEach(id -> colors.add(
                    mauRepo.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Màu không tồn tại."))
            ));
        }
        if (normalizeOption(customName, 50, "Tên màu") != null) {
            colors.add(resolveMau(null, customName));
        }
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm ít nhất một màu.");
        }
        return List.copyOf(colors);
    }

    private List<Size> resolveSizes(List<Integer> ids, String customName) {
        LinkedHashSet<Size> sizes = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).distinct().forEach(id -> sizes.add(
                    resolveSize(id, null)
            ));
        }
        if (normalizeOption(customName, 20, "Tên size") != null) {
            sizes.add(resolveSize(null, customName));
        }
        if (sizes.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm ít nhất một size.");
        }
        return List.copyOf(sizes);
    }

    private void saveProductImage(SanPham product, String rawImage) {
        if (chiTietSanPhamRepo == null || product.getId() == null
                || rawImage == null || rawImage.isBlank()) {
            return;
        }
        ChiTietSanPham detail = chiTietSanPhamRepo.findFirstByMaSPId(product.getId())
                .orElseGet(ChiTietSanPham::new);
        detail.setMaSP(product);
        detail.setHinhAnh(normalizeImage(rawImage));
        chiTietSanPhamRepo.save(detail);
    }

    private String normalizeImage(String rawImage) {
        if (rawImage == null || rawImage.isBlank()) {
            return null;
        }
        String image = rawImage.trim();
        if (image.length() > 255) {
            throw new IllegalArgumentException("Đường dẫn ảnh không được vượt quá 255 ký tự.");
        }
        if (!(image.startsWith("http://") || image.startsWith("https://")
                || image.startsWith("/images/") || image.startsWith("/uploads/products/"))) {
            throw new IllegalArgumentException("Ảnh phải là URL http(s), đường dẫn /images/ hoặc ảnh đã tải lên.");
        }
        return image;
    }

    private String imageUrl(String image) {
        if (image == null || image.isBlank()) return null;
        return image.startsWith("/") ? image : "/images/products/" + image;
    }

    @GetMapping("/uploads/products/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> productImage(@PathVariable String filename) {
        Path root = uploadRoot();
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(target);
        if (!resource.exists() || !resource.isReadable()) return ResponseEntity.notFound().build();
        MediaType mediaType = mediaType(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(mediaType)
                .body(resource);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Ảnh không được vượt quá 5 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !Set.of(
                "image/png", "image/jpeg", "image/webp", "image/gif", "image/svg+xml"
        ).contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG, JPG, WEBP, GIF hoặc SVG.");
        }
    }

    private String storeImage(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : extensionFromContentType(file.getContentType());
        if (!Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg").contains(extension)) {
            throw new IllegalArgumentException("Định dạng phần mở rộng của ảnh không hợp lệ.");
        }
        String filename = UUID.randomUUID() + extension;
        Path root = uploadRoot();
        try {
            Files.createDirectories(root);
            file.transferTo(root.resolve(filename));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể lưu ảnh sản phẩm.", exception);
        }
        return "/uploads/products/" + filename;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> ".img";
        };
    }

    private Path uploadRoot() {
        return Path.of(System.getProperty("user.dir"), "uploads", "products").toAbsolutePath().normalize();
    }

    private MediaType mediaType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        return MediaType.IMAGE_JPEG;
    }

    private SanPham findMatchingVariant(SanPham candidate) {
        return findMatchingVariant(candidate, null);
    }

    private SanPham findMatchingVariant(SanPham candidate, Integer excludedId) {
        return sanPhamRepo.findByTenSPIgnoreCase(candidate.getTenSP()).stream()
                .filter(existing -> !Objects.equals(existing.getId(), excludedId))
                .filter(existing -> sameReference(existing.getMaLoai(), candidate.getMaLoai()))
                .filter(existing -> sameReference(existing.getMaMau(), candidate.getMaMau()))
                .filter(existing -> sameReference(existing.getMaChatLieu(), candidate.getMaChatLieu()))
                .filter(existing -> sameReference(existing.getMaSize(), candidate.getMaSize()))
                .findFirst()
                .orElse(null);
    }

    private boolean sameReference(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left instanceof Loai leftItem && right instanceof Loai rightItem) {
            return Objects.equals(leftItem.getId(), rightItem.getId());
        }
        if (left instanceof Mau leftItem && right instanceof Mau rightItem) {
            return Objects.equals(leftItem.getId(), rightItem.getId());
        }
        if (left instanceof ChatLieu leftItem && right instanceof ChatLieu rightItem) {
            return Objects.equals(leftItem.getId(), rightItem.getId());
        }
        if (left instanceof Size leftItem && right instanceof Size rightItem) {
            return Objects.equals(leftItem.getId(), rightItem.getId());
        }
        return false;
    }

    private Loai resolveLoai(Integer id, String customName) {
        String name = normalizeOption(customName, 50, "Tên loại");
        if (name != null) {
            return loaiRepo.findFirstByTenLoaiIgnoreCase(name).orElseGet(() -> {
                Loai item = new Loai();
                item.setTenLoai(name);
                item.setTonKho(0);
                return loaiRepo.save(item);
            });
        }
        if (id == null) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm loại sản phẩm.");
        }
        return loaiRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại."));
    }

    private Mau resolveMau(Integer id, String customName) {
        String name = normalizeOption(customName, 50, "Tên màu");
        if (name != null) {
            return mauRepo.findFirstByTenMauIgnoreCase(name).orElseGet(() -> {
                Mau item = new Mau();
                item.setTenMau(name);
                item.setTonKho(0);
                return mauRepo.save(item);
            });
        }
        if (id == null) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm màu.");
        }
        return mauRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Màu không tồn tại."));
    }

    private ChatLieu resolveChatLieu(Integer id, String customName) {
        String name = normalizeOption(customName, 50, "Tên chất liệu");
        if (name != null) {
            return chatLieuRepo.findFirstByTenChatLieuIgnoreCase(name).orElseGet(() -> {
                ChatLieu item = new ChatLieu();
                item.setTenChatLieu(name);
                item.setTonKho(0);
                return chatLieuRepo.save(item);
            });
        }
        if (id == null) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm chất liệu.");
        }
        return chatLieuRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chất liệu không tồn tại."));
    }

    private Size resolveSize(Integer id, String customName) {
        String name = normalizeSize(customName);
        if (name != null) {
            var exact = sizeRepo.findFirstByTenSizeIgnoreCase(name);
            if (exact.isPresent()) return exact.get();
            var equivalent = sizeRepo.findAll().stream()
                    .filter(item -> name.equals(normalizeSize(item.getTenSize())))
                    .findFirst();
            if (equivalent.isPresent()) return equivalent.get();
            Size item = new Size();
            item.setTenSize(name);
            item.setTonKho(0);
            return sizeRepo.save(item);
        }
        if (id == null) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm size.");
        }
        Size selected = sizeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Size không tồn tại."));
        normalizeSize(selected.getTenSize());
        return selected;
    }

    private String normalizeSize(String rawSize) {
        String name = normalizeOption(rawSize, 20, "Tên size");
        if (name == null) return null;
        if (!name.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException("Size phải là số nguyên dương, không nhận số thập phân.");
        }
        try {
            return Integer.toString(Integer.parseInt(name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Size không hợp lệ.", exception);
        }
    }

    private String normalizeOption(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " không được vượt quá " + maxLength + " ký tự.");
        }
        return normalized;
    }

    private void loadOptions(Model model) {
        model.addAttribute("loaiList", loaiRepo.findAll());
        model.addAttribute("mauList", mauRepo.findAll());
        model.addAttribute("chatLieuList", chatLieuRepo.findAll());
        model.addAttribute("sizeList", sizeRepo.findAll());
        model.addAttribute("sanPhamGoiYList", sanPhamRepo.findAllByOrderByTenSPAsc());
    }

    private boolean loggedIn(HttpSession session) {
        return SessionUserControllerAdvice.hasBusinessAccess(session);
    }
}
