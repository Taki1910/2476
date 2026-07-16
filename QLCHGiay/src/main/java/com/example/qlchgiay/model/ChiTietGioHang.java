package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "ChiTietGioHang")
public class ChiTietGioHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maCTGioHang", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maKH")
    private KhachHang maKH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maChiTietSP")
    private ChiTietSanPham maChiTietSP;

    @Column(name = "soLuong")
    private Integer soLuong;

    @Column(name = "donGia", precision = 18, scale = 2)
    private BigDecimal donGia;

    @ColumnDefault("[soLuong]*[donGia]")
    @Column(name = "thanhTien", precision = 29, scale = 2)
    private BigDecimal thanhTien;

    @ColumnDefault("getdate()")
    @Column(name = "ngayTao")
    private LocalDate ngayTao;

    @Size(max = 30)
    @Nationalized
    @Column(name = "trangThai", length = 30)
    private String trangThai;


}