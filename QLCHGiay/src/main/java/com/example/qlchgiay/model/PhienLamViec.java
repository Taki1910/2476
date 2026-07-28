package com.example.qlchgiay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "PhienLamViec")
public class PhienLamViec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maPhien", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maNhanVien", nullable = false)
    private NhanVien maNhanVien;

    @Column(name = "batDau", nullable = false)
    private LocalDateTime batDau;

    @Column(name = "ketThuc")
    private LocalDateTime ketThuc;

    @ColumnDefault("0")
    @Column(name = "soSanPhamBan", nullable = false)
    private Integer soSanPhamBan = 0;

    @ColumnDefault("0")
    @Column(name = "soKhachHangMoi", nullable = false)
    private Integer soKhachHangMoi = 0;

    @ColumnDefault("0")
    @Column(name = "doanhThu", nullable = false, precision = 18, scale = 2)
    private BigDecimal doanhThu = BigDecimal.ZERO;

    @ColumnDefault("0")
    @Column(name = "nhanVienDaXem", nullable = false)
    private Boolean nhanVienDaXem = false;

    @ColumnDefault("0")
    @Column(name = "adminDaXem", nullable = false)
    private Boolean adminDaXem = false;
}
