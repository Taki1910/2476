package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "KhachHang")
public class KhachHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maKH", nullable = false)
    private Integer id;

    @NotBlank(message = "Vui lòng nhập họ tên khách hàng.")
    @Size(min = 2, max = 100, message = "Họ tên khách hàng phải có từ 2 đến 100 ký tự.")
    @Nationalized
    @Column(name = "tenKH", nullable = false, length = 100)
    private String tenKH;

    @Column(name = "gioiTinh")
    private Boolean gioiTinh;

    @Column(name = "namSinh")
    private Integer namSinh;

    @Column(name = "ngaySinh")
    private LocalDate ngaySinh;

    @Pattern(
            regexp = "^(03|05|07|08|09)\\d{8}$",
            message = "Số điện thoại không đúng định dạng số di động Việt Nam."
    )
    @Column(name = "soDienThoai", length = 10)
    private String soDienThoai;

    @Size(max = 200, message = "Địa chỉ không được vượt quá 200 ký tự.")
    @Nationalized
    @Column(name = "diaChi", length = 200)
    private String diaChi;

    @Column(name = "trangThai", nullable = false, length = 20)
    private String trangThai = "ACTIVE";

    @Transient
    public boolean isActive() {
        return trangThai == null || "ACTIVE".equalsIgnoreCase(trangThai);
    }

    @Transient
    public LocalDate getNgaySinhHieuLuc() {
        if (ngaySinh != null) return ngaySinh;
        return namSinh == null ? null : LocalDate.of(namSinh, 12, 31);
    }

    public void setNgaySinh(LocalDate ngaySinh) {
        this.ngaySinh = ngaySinh;
        this.namSinh = ngaySinh == null ? null : ngaySinh.getYear();
    }


}
