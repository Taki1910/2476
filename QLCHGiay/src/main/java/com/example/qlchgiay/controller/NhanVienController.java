package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.ChucVu;
import com.example.qlchgiay.model.NhanVien;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.ChucVuRepo;
import com.example.qlchgiay.repo.NhanVienRepo;
import com.example.qlchgiay.repo.TaiKhoanRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/nhanvien")
public class NhanVienController {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{4,50}$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");

    private final NhanVienRepo employeeRepo;
    private final TaiKhoanRepo accountRepo;
    private final ChucVuRepo positionRepo;
    private final PasswordEncoder passwordEncoder;

    public NhanVienController(
            NhanVienRepo employeeRepo,
            TaiKhoanRepo accountRepo,
            ChucVuRepo positionRepo,
            PasswordEncoder passwordEncoder
    ) {
        this.employeeRepo = employeeRepo;
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @InitBinder
    void trimTextFields(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(
            HttpSession session,
            @RequestParam(name = "accountId", required = false) Integer accountId,
            Model model
    ) {
        requireAdmin(session);
        loadList(model);
        model.addAttribute("selectedAccountId", accountId);
        return "nhanvien";
    }

    @GetMapping("/them")
    public String createForm(HttpSession session, Model model) {
        requireAdmin(session);
        NhanVien employee = new NhanVien();
        employee.setGioiTinh(true);
        model.addAttribute("item", employee);
        return showForm(model, "Thêm nhân viên", null);
    }

    @PostMapping("/them")
    @Transactional
    public String create(
            HttpSession session,
            @ModelAttribute("item") NhanVien employee,
            @RequestParam String tenDangNhap,
            @RequestParam String matKhau,
            @RequestParam String xacNhanMatKhau,
            Model model,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        String error = validateEmployee(employee);
        if (error == null) error = validateNewAccount(tenDangNhap, matKhau, xacNhanMatKhau);
        if (error != null) {
            model.addAttribute("error", error);
            return showForm(model, "Thêm nhân viên", tenDangNhap);
        }

        employee.setMaChucVu(employeePosition());
        employee.setTrangThai("Đang làm");
        employeeRepo.save(employee);

        TaiKhoan account = new TaiKhoan();
        account.setTenDangNhap(tenDangNhap);
        account.setMatKhau(passwordEncoder.encode(matKhau));
        account.setVaiTro("Nhân viên");
        account.setMaNhanVien(employee);
        account.setTrangThai("Hoạt động");
        account.setSoLanDangNhapSai(0);
        account.setYeuCauDatLaiMatKhau(false);
        account.setTamKhoaDangNhap(false);
        accountRepo.save(account);

        redirect.addFlashAttribute("success", "Đã thêm nhân viên @" + tenDangNhap + ".");
        return "redirect:/nhanvien";
    }

    @GetMapping("/sua/{id}")
    public String updateForm(
            @PathVariable Integer id,
            HttpSession session,
            Model model,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        NhanVien employee = employeeRepo.findById(id).orElse(null);
        if (employee == null) return missing(redirect);
        TaiKhoan account = accountRepo.findFirstByMaNhanVienId(id).orElse(null);
        requireEmployeeAccount(account);
        model.addAttribute("item", employee);
        model.addAttribute("account", account);
        return showForm(model, "Cập nhật nhân viên", account == null ? null : account.getTenDangNhap());
    }

    @PostMapping("/sua/{id}")
    @Transactional
    public String update(
            @PathVariable Integer id,
            HttpSession session,
            @ModelAttribute("item") NhanVien form,
            @RequestParam(defaultValue = "false") boolean active,
            Model model,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        NhanVien employee = employeeRepo.findById(id).orElse(null);
        if (employee == null) return missing(redirect);
        TaiKhoan account = accountRepo.findFirstByMaNhanVienId(id).orElse(null);
        requireEmployeeAccount(account);

        String error = validateEmployee(form);
        if (error != null) {
            form.setId(id);
            model.addAttribute("account", account);
            model.addAttribute("error", error);
            return showForm(model, "Cập nhật nhân viên", account == null ? null : account.getTenDangNhap());
        }

        employee.setTenNhanVien(form.getTenNhanVien());
        employee.setGioiTinh(form.getGioiTinh());
        employee.setSoDienThoai(form.getSoDienThoai());
        employee.setNgaySinh(form.getNgaySinh());
        employee.setQueQuan(form.getQueQuan());
        employee.setTrangThai(active ? "Đang làm" : "Ngừng làm");
        employeeRepo.save(employee);
        if (account != null) {
            account.setTrangThai(active ? "Hoạt động" : "Ngừng hoạt động");
            if (!active) account.setTamKhoaDangNhap(true);
            accountRepo.save(account);
        }

        redirect.addFlashAttribute("success", "Đã cập nhật thông tin nhân viên.");
        return "redirect:/nhanvien";
    }

    @PostMapping("/trang-thai/{id}")
    @Transactional
    public String toggleStatus(
            @PathVariable Integer id,
            HttpSession session,
            RedirectAttributes redirect
    ) {
        requireAdmin(session);
        NhanVien employee = employeeRepo.findById(id).orElse(null);
        if (employee == null) return missing(redirect);
        TaiKhoan account = accountRepo.findFirstByMaNhanVienId(id).orElse(null);
        requireEmployeeAccount(account);

        boolean activate = !isActive(employee.getTrangThai());
        employee.setTrangThai(activate ? "Đang làm" : "Ngừng làm");
        employeeRepo.save(employee);
        if (account != null) {
            account.setTrangThai(activate ? "Hoạt động" : "Ngừng hoạt động");
            account.setTamKhoaDangNhap(!activate);
            if (activate) account.setSoLanDangNhapSai(0);
            accountRepo.save(account);
        }
        redirect.addFlashAttribute(
                "success",
                activate ? "Đã kích hoạt lại nhân viên." : "Đã ngừng hoạt động nhân viên."
        );
        return "redirect:/nhanvien";
    }

    private void loadList(Model model) {
        List<TaiKhoan> accounts = accountRepo.findAllWithEmployeesOrderByUsername();
        Map<Integer, TaiKhoan> accountsByEmployeeId = new LinkedHashMap<>();
        accounts.stream()
                .filter(account -> !SessionUserControllerAdvice.isAdmin(account))
                .filter(account -> account.getMaNhanVien() != null)
                .filter(account -> account.getMaNhanVien().getId() != null)
                .forEach(account -> accountsByEmployeeId.put(account.getMaNhanVien().getId(), account));

        var adminEmployeeIds = accounts.stream()
                .filter(SessionUserControllerAdvice::isAdmin)
                .map(TaiKhoan::getMaNhanVien)
                .filter(Objects::nonNull)
                .map(NhanVien::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        var employees = employeeRepo.findAllWithPositionOrderByIdDesc().stream()
                .filter(employee -> !adminEmployeeIds.contains(employee.getId()))
                .toList();

        model.addAttribute("items", employees);
        model.addAttribute("accounts", accountsByEmployeeId.values());
        model.addAttribute("accountsByEmployeeId", accountsByEmployeeId);
        model.addAttribute("activeCount", employees.stream().filter(x -> isActive(x.getTrangThai())).count());
        model.addAttribute("lockedCount", accountsByEmployeeId.values().stream()
                .filter(x -> Boolean.TRUE.equals(x.getTamKhoaDangNhap())).count());
    }

    private String validateEmployee(NhanVien employee) {
        if (employee.getTenNhanVien() == null || employee.getTenNhanVien().length() < 2
                || employee.getTenNhanVien().length() > 100) {
            return "Họ tên nhân viên phải có từ 2 đến 100 ký tự.";
        }
        if (employee.getSoDienThoai() != null && !PHONE.matcher(employee.getSoDienThoai()).matches()) {
            return "Số điện thoại nhân viên phải gồm đúng 10 chữ số và bắt đầu bằng 0.";
        }
        if (employee.getNamSinh() != null
                && (employee.getNamSinh() < 1900 || employee.getNamSinh() > Year.now().getValue())) {
            return "Năm sinh phải từ 1900 đến năm hiện tại.";
        }
        LocalDate birthDate = employee.getNgaySinhHieuLuc();
        if (birthDate == null || birthDate.isAfter(LocalDate.now().minusYears(18))) {
            return "Nhân viên phải đủ 18 tuổi trở lên.";
        }
        if (employee.getQueQuan() != null && employee.getQueQuan().length() > 100) {
            return "Quê quán không được vượt quá 100 ký tự.";
        }
        return null;
    }

    private String validateNewAccount(String username, String password, String confirmation) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return "Tên đăng nhập phải có 4–50 ký tự, chỉ gồm chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.";
        }
        if (accountRepo.existsByTenDangNhapIgnoreCase(username)) {
            return "Tên đăng nhập đã tồn tại.";
        }
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mật khẩu phải có ít nhất 8 ký tự và không quá 72 byte.";
        }
        if (!password.equals(confirmation)) return "Mật khẩu xác nhận không khớp.";
        return null;
    }

    private ChucVu employeePosition() {
        return positionRepo.findFirstByTenChucVuIgnoreCase("Nhân viên").orElse(null);
    }

    private String showForm(Model model, String title, String username) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("tenDangNhap", username);
        return "nhanvien-form";
    }

    private String missing(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Không tìm thấy nhân viên.");
        return "redirect:/nhanvien";
    }

    private boolean isActive(String status) {
        return status == null || !status.toLowerCase().contains("ngừng");
    }

    private void requireAdmin(HttpSession session) {
        if (!SessionUserControllerAdvice.isAdmin(session)) {
            throw new AccessDeniedException("Chỉ quản lý được quản lý nhân viên");
        }
    }

    private void requireEmployeeAccount(TaiKhoan account) {
        if (SessionUserControllerAdvice.isAdmin(account)) {
            throw new AccessDeniedException("Không thể thay đổi tài khoản quản lý tại đây");
        }
    }
}
