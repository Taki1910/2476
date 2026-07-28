package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

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

    @Size(max = 10, message = "Số điện thoại không được vượt quá 10 chữ số.")
    @Pattern(
            regexp = "^0\\d{9}$",
            message = "Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0."
    )
    @Column(name = "soDienThoai", length = 10)
    private String soDienThoai;

    @Size(max = 200, message = "Địa chỉ không được vượt quá 200 ký tự.")
    @Nationalized
    @Column(name = "diaChi", length = 200)
    private String diaChi;


}
