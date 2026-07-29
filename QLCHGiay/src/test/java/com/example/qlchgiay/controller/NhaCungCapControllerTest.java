package com.example.qlchgiay.controller;

import com.example.qlchgiay.model.TaiKhoan;
import com.example.qlchgiay.repo.NhaCungCapRepo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NhaCungCapControllerTest {
    @Mock private NhaCungCapRepo repo;
    @Mock private HttpSession session;

    private NhaCungCapController controller;

    @BeforeEach
    void setUp() {
        controller = new NhaCungCapController(repo);
    }

    @Test
    void employeeCannotDeleteSupplier() {
        TaiKhoan employee = new TaiKhoan();
        employee.setVaiTro("Nhân viên");
        when(session.getAttribute("user")).thenReturn(employee);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.delete(7, session, redirect);

        assertEquals("redirect:/nhacungcap", view);
        assertEquals(
                "Tài khoản nhân viên không có quyền xóa nhà cung cấp.",
                redirect.getFlashAttributes().get("error")
        );
        verify(repo, never()).deleteById(7);
    }
}
