package com.example.qlchgiay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "LichSuChinhSuaHoaDon")
public class LichSuChinhSuaHoaDon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maLichSu", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maHoaDon", nullable = false)
    private HoaDon maHoaDon;

    @Column(name = "maPhien")
    private Integer maPhien;

    @Nationalized
    @Column(name = "nguoiChinhSua", nullable = false, length = 100)
    private String nguoiChinhSua;

    @Column(name = "thoiGian", nullable = false)
    private LocalDateTime thoiGian;

    @Lob
    @Nationalized
    @Column(name = "duLieuTruoc", nullable = false, columnDefinition = "nvarchar(max)")
    private String duLieuTruoc;

    @Lob
    @Nationalized
    @Column(name = "duLieuSau", nullable = false, columnDefinition = "nvarchar(max)")
    private String duLieuSau;
}
