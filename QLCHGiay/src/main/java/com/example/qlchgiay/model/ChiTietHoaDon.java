package com.example.qlchgiay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ChiTietHoaDon")
public class ChiTietHoaDon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maCTHD", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maHoaDon")
    private HoaDon maHoaDon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maChiTietSP")
    private ChiTietSanPham maChiTietSP;

    @Column(name = "soLuong")
    private Integer soLuong;

    @Column(name = "donGia", precision = 18, scale = 2)
    private BigDecimal donGia;

    @Column(name = "giaGoc", precision = 18, scale = 2)
    private BigDecimal giaGoc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maKhuyenMai")
    private KhuyenMai maKhuyenMai;

    @ColumnDefault("[soLuong]*[donGia]")
    @Column(name = "thanhTien", precision = 29, scale = 2, insertable = false, updatable = false)
    private BigDecimal thanhTien;


}
