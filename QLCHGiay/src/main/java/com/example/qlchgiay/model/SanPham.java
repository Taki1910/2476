package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "SanPham")
public class SanPham {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maSP", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "tenSP", nullable = false, length = 100)
    private String tenSP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maLoai")
    private Loai maLoai;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maMau")
    private Mau maMau;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maChatLieu")
    private ChatLieu maChatLieu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maSize")
    private com.example.qlchgiay.model.Size maSize;

    @Column(name = "gia", precision = 18, scale = 2)
    private BigDecimal gia;

    @ColumnDefault("0")
    @Column(name = "tonKho")
    private Integer tonKho;


}
