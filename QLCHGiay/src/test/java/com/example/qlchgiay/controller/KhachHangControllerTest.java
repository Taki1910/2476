package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.KhachHang;
import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.KhachHangRepo;
import com.example.qlchgiay.service.WorkSessionService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KhachHangControllerTest {
    @Mock private KhachHangRepo repo;
    @Mock private WorkSessionService workSessionService;
    @Mock private HttpSession session;

    private KhachHangController controller;

    @BeforeEach
    void setUp() {
        controller = new KhachHangController(repo, workSessionService);
        TaiKhoan account = new TaiKhoan();
        account.setVaiTro("Admin");
        when(session.getAttribute("user")).thenReturn(account);
    }

    @Test
    void createReturnsFormErrorInsteadOfThrowingForInvalidBirthYear() {
        KhachHang customer = validCustomer();
        customer.setNamSinh(Year.now().getValue() + 1);
        BindingResult errors = new BeanPropertyBindingResult(customer, "item");

        String view = controller.create(
                session,
                customer,
                errors,
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("khachhang-form", view);
        assertTrue(errors.hasFieldErrors("namSinh"));
        verify(repo, never()).saveAndFlush(customer);
    }

    @Test
    void createReturnsFieldErrorForDuplicatePhone() {
        KhachHang customer = validCustomer();
        BindingResult errors = new BeanPropertyBindingResult(customer, "item");
        when(repo.existsBySoDienThoai(customer.getSoDienThoai())).thenReturn(true);

        String view = controller.create(
                session,
                customer,
                errors,
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("khachhang-form", view);
        assertTrue(errors.hasFieldErrors("soDienThoai"));
        verify(repo, never()).saveAndFlush(customer);
    }

    @Test
    void createSavesValidCustomer() {
        KhachHang customer = validCustomer();
        BindingResult errors = new BeanPropertyBindingResult(customer, "item");

        String view = controller.create(
                session,
                customer,
                errors,
                new ExtendedModelMap(),
                new RedirectAttributesModelMap()
        );

        assertEquals("redirect:/khachhang", view);
        verify(repo).saveAndFlush(customer);
    }

    private KhachHang validCustomer() {
        KhachHang customer = new KhachHang();
        customer.setTenKH("Khách hàng kiểm thử");
        customer.setGioiTinh(true);
        customer.setNamSinh(2000);
        customer.setSoDienThoai("0901234567");
        customer.setDiaChi("Hà Nội");
        return customer;
    }
}
