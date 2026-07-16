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
@Table(name = "NhanVien")
public class NhanVien {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maNhanVien", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "tenNhanVien", nullable = false, length = 100)
    private String tenNhanVien;

    @Column(name = "gioiTinh")
    private Boolean gioiTinh;

    @Size(max = 15)
    @Column(name = "soDienThoai", length = 15)
    private String soDienThoai;

    @Column(name = "namSinh")
    private Integer namSinh;

    @Size(max = 100)
    @Nationalized
    @Column(name = "queQuan", length = 100)
    private String queQuan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maChucVu")
    private ChucVu maChucVu;

    @Size(max = 30)
    @Nationalized
    @Column(name = "trangThai", length = 30)
    private String trangThai;


}