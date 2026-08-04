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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
public class SanPhamController {
    private static final BigDecimal MINIMUM_PRICE = BigDecimal.valueOf(1_000_000);
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
        validateImageFile(hinhAnhFile);
        String imagePath = hinhAnhFile != null && !hinhAnhFile.isEmpty()
                ? storeImage(hinhAnhFile)
                : hinhAnh;
        SanPham sanPham = new SanPham();
        applyForm(
                sanPham, tenSP, maLoai, maMau, maChatLieu, maSize,
                tenLoaiMoi, tenMauMoi, tenChatLieuMoi, tenSizeMoi,
                gia, tonKho, imagePath
        );
        SanPham existingVariant = findMatchingVariant(sanPham);
        if (existingVariant != null) {
            int addedStock = sanPham.getTonKho();
            existingVariant.setTonKho(safeStock(existingVariant) + addedStock);
            existingVariant.setGia(sanPham.getGia());
            sanPhamRepo.save(existingVariant);
            saveProductImage(existingVariant, imagePath);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Biến thể #SP-" + existingVariant.getId()
                            + " đã tồn tại. Đã cộng " + addedStock + " sản phẩm vào tồn kho."
            );
        } else {
            sanPhamRepo.save(sanPham);
            saveProductImage(sanPham, imagePath);
            redirectAttributes.addFlashAttribute("success", "Đã thêm sản phẩm thành công.");
        }
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
        model.addAttribute("sanPham", sanPham);
        model.addAttribute("imageUrl", imageUrl(sanPham.getHinhAnh()));
        return "sanpham-detail";
    }

    @GetMapping("/sanpham/sua/{id}")
    public String showUpdate(@PathVariable Integer id, HttpSession session, Model model,
                             RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
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
                         RedirectAttributes redirectAttributes) {
        if (!loggedIn(session)) return "redirect:/login";
        validateImageFile(hinhAnhFile);
        String imagePath = hinhAnhFile != null && !hinhAnhFile.isEmpty()
                ? storeImage(hinhAnhFile)
                : hinhAnh;
        SanPham sanPham = sanPhamRepo.findById(id).orElse(null);
        if (sanPham == null) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        applyForm(
                sanPham, tenSP, maLoai, maMau, maChatLieu, maSize,
                tenLoaiMoi, tenMauMoi, tenChatLieuMoi, tenSizeMoi,
                gia, tonKho, imagePath
        );
        sanPhamRepo.save(sanPham);
        saveProductImage(sanPham, imagePath);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật sản phẩm thành công.");
        return "redirect:/sanpham";
    }

    @PostMapping("/sanpham/xoa/{id}")
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
        if (!sanPhamRepo.existsById(id)) {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy sản phẩm.");
            return "redirect:/sanpham";
        }
        try {
            sanPhamRepo.deleteById(id);
            sanPhamRepo.flush();
            redirectAttributes.addFlashAttribute("success", "Đã xóa sản phẩm thành công.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "Không thể xóa sản phẩm đã phát sinh hóa đơn hoặc giỏ hàng.");
        }
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
        if (gia == null || gia.compareTo(MINIMUM_PRICE) < 0) {
            throw new IllegalArgumentException(
                    "Giá bán phải từ 1.000.000 VNĐ trở lên."
            );
        }
        if (tonKho == null || tonKho <= 0) {
            throw new IllegalArgumentException("Số lượng tồn phải lớn hơn 0.");
        }
        sanPham.setTenSP(normalizedName);
        sanPham.setGia(gia);
        sanPham.setTonKho(tonKho);
        normalizeImage(hinhAnh);
        sanPham.setMaLoai(resolveLoai(maLoai, tenLoaiMoi));
        sanPham.setMaMau(resolveMau(maMau, tenMauMoi));
        sanPham.setMaChatLieu(resolveChatLieu(maChatLieu, tenChatLieuMoi));
        sanPham.setMaSize(resolveSize(maSize, tenSizeMoi));
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
        return sanPhamRepo.findByTenSPIgnoreCase(candidate.getTenSP()).stream()
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

    private int safeStock(SanPham product) {
        return product.getTonKho() == null ? 0 : product.getTonKho();
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
        String name = normalizeOption(customName, 20, "Tên size");
        if (name != null) {
            return sizeRepo.findFirstByTenSizeIgnoreCase(name).orElseGet(() -> {
                Size item = new Size();
                item.setTenSize(name);
                item.setTonKho(0);
                return sizeRepo.save(item);
            });
        }
        if (id == null) {
            throw new IllegalArgumentException("Vui lòng chọn hoặc thêm size.");
        }
        return sizeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Size không tồn tại."));
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
        return session.getAttribute("user") instanceof TaiKhoan;
    }
}
