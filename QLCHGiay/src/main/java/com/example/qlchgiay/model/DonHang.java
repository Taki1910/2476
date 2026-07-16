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
@Table(name = "DonHang")
public class DonHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maDonHang", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maKH")
    private KhachHang maKH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maNhanVien")
    private NhanVien maNhanVien;

    @ColumnDefault("getdate()")
    @Column(name = "ngayDatHang")
    private LocalDate ngayDatHang;

    @Column(name = "tongTien", precision = 18, scale = 2)
    private BigDecimal tongTien;

    @Size(max = 50)
    @Nationalized
    @Column(name = "trangThai", length = 50)
    private String trangThai;


}