package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "TaiKhoan")
public class TaiKhoan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maTaiKhoan", nullable = false)
    private Integer id;

    @Size(max = 50)
    @NotNull
    @Column(name = "tenDangNhap", nullable = false, length = 50)
    private String tenDangNhap;

    @Size(max = 255)
    @NotNull
    @Column(name = "matKhau", nullable = false)
    private String matKhau;

    @Size(max = 30)
    @Nationalized
    @Column(name = "vaiTro", length = 30)
    private String vaiTro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maNhanVien")
    private NhanVien maNhanVien;

    @Size(max = 30)
    @Nationalized
    @Column(name = "trangThai", length = 30)
    private String trangThai;


}