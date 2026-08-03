package com.example.qlchgiay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "KhuyenMai")
public class KhuyenMai {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maKhuyenMai", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "tenKhuyenMai", nullable = false, length = 120)
    private String tenKhuyenMai;

    @Column(name = "loaiGiam", nullable = false, length = 20)
    private String loaiGiam;

    @Column(name = "giaTri", nullable = false, precision = 18, scale = 2)
    private BigDecimal giaTri;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "batDau", nullable = false)
    private LocalDateTime batDau;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "ketThuc", nullable = false)
    private LocalDateTime ketThuc;

    @Column(name = "trangThai", nullable = false)
    private Boolean trangThai = true;

    @ManyToMany
    @JoinTable(
            name = "KhuyenMaiSanPham",
            joinColumns = @JoinColumn(name = "maKhuyenMai"),
            inverseJoinColumns = @JoinColumn(name = "maSP")
    )
    private Set<SanPham> sanPhams = new LinkedHashSet<>();
}
