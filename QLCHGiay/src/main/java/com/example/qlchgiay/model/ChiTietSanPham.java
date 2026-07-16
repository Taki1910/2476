package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "ChiTietSanPham")
public class ChiTietSanPham {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maChiTietSP", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maSP")
    private SanPham maSP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maNCC")
    private NhaCungCap maNCC;

    @Size(max = 500)
    @Nationalized
    @Column(name = "moTa", length = 500)
    private String moTa;

    @Size(max = 255)
    @Nationalized
    @Column(name = "hinhAnh")
    private String hinhAnh;

    @Size(max = 100)
    @Nationalized
    @Column(name = "xuatXu", length = 100)
    private String xuatXu;

    @Size(max = 100)
    @Nationalized
    @Column(name = "thuongHieu", length = 100)
    private String thuongHieu;

    @Size(max = 50)
    @Nationalized
    @Column(name = "trangThai", length = 50)
    private String trangThai;


}