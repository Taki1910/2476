package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Getter
@Setter
@Entity
@Table(name = "HoaDon")
public class HoaDon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maHoaDon", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maDonHang")
    private DonHang maDonHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maNhanVien")
    private NhanVien maNhanVien;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maPhien")
    private PhienLamViec maPhien;

    @ColumnDefault("getdate()")
    @Column(name = "ngayLap")
    private LocalDate ngayLap;

    @Column(name = "tongTien", precision = 18, scale = 2)
    private BigDecimal tongTien;

    @Size(max = 50)
    @Nationalized
    @Column(name = "trangThai", length = 50)
    private String trangThai;

    @Nationalized
    @Column(name = "tenKhachHangSnapshot", length = 100)
    private String tenKhachHangSnapshot;

    @Column(name = "soDienThoaiKhachHangSnapshot", length = 15)
    private String soDienThoaiKhachHangSnapshot;

    @Nationalized
    @Column(name = "tenNhanVienSnapshot", length = 100)
    private String tenNhanVienSnapshot;

    @Transient
    public String getTrangThaiHienThi() {
        if (trangThai == null || trangThai.isBlank()) return "Chưa thanh toán";
        String value = trangThai.trim().toLowerCase(Locale.ROOT);
        if (value.contains("hủy")) return "Đã hủy";
        if (value.contains("đã thanh toán") || value.contains("hoàn thành")) return "Đã thanh toán";
        return "Chưa thanh toán";
    }

    @Transient
    public String getTenKhachHangHienThi() {
        if (tenKhachHangSnapshot != null && !tenKhachHangSnapshot.isBlank()) return tenKhachHangSnapshot;
        return maDonHang != null && maDonHang.getMaKH() != null
                ? maDonHang.getMaKH().getTenKH() : "Khách lẻ";
    }

    @Transient
    public String getSoDienThoaiKhachHangHienThi() {
        if (soDienThoaiKhachHangSnapshot != null && !soDienThoaiKhachHangSnapshot.isBlank()) {
            return soDienThoaiKhachHangSnapshot;
        }
        return maDonHang != null && maDonHang.getMaKH() != null
                ? maDonHang.getMaKH().getSoDienThoai() : null;
    }

    @Transient
    public String getTenNhanVienHienThi() {
        if (tenNhanVienSnapshot != null && !tenNhanVienSnapshot.isBlank()) return tenNhanVienSnapshot;
        return maNhanVien != null ? maNhanVien.getTenNhanVien() : "Chưa phân công";
    }

}
