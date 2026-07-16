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
@Table(name = "ThanhToan")
public class ThanhToan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maThanhToan", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maHoaDon")
    private HoaDon maHoaDon;

    @Size(max = 50)
    @Nationalized
    @Column(name = "phuongThuc", length = 50)
    private String phuongThuc;

    @ColumnDefault("getdate()")
    @Column(name = "ngayThanhToan")
    private LocalDate ngayThanhToan;

    @Column(name = "soTien", precision = 18, scale = 2)
    private BigDecimal soTien;

    @Size(max = 30)
    @Nationalized
    @Column(name = "trangThai", length = 30)
    private String trangThai;


}