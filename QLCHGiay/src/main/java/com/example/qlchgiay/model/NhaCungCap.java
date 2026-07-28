package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "NhaCungCap")
public class NhaCungCap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maNCC", nullable = false)
    private Integer id;

    @NotBlank(message = "Vui lòng nhập tên nhà cung cấp.")
    @Size(min = 2, max = 100, message = "Tên nhà cung cấp phải có từ 2 đến 100 ký tự.")
    @Nationalized
    @Column(name = "tenNCC", nullable = false, length = 100)
    private String tenNCC;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 15, message = "Số điện thoại không được vượt quá 15 ký tự.")
    @Pattern(
            regexp = "^(?:0\\d{9}|\\+84\\d{9})$",
            message = "Số điện thoại phải bắt đầu bằng 0 hoặc +84 và có đúng 9 chữ số theo sau."
    )
    @Column(name = "soDienThoai", nullable = false, length = 15)
    private String soDienThoai;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự.")
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @NotBlank(message = "Vui lòng nhập địa chỉ.")
    @Size(min = 5, max = 200, message = "Địa chỉ phải có từ 5 đến 200 ký tự.")
    @Nationalized
    @Column(name = "diaChi", nullable = false, length = 200)
    private String diaChi;

    @NotBlank(message = "Vui lòng chọn trạng thái hợp tác.")
    @Size(max = 30, message = "Trạng thái không được vượt quá 30 ký tự.")
    @Pattern(
            regexp = "^(Hoạt động|Ngừng hợp tác)$",
            message = "Trạng thái nhà cung cấp không hợp lệ."
    )
    @Nationalized
    @Column(name = "trangThai", nullable = false, length = 30)
    private String trangThai;


}
