package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "KhachHang")
public class KhachHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maKH", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "tenKH", nullable = false, length = 100)
    private String tenKH;

    @Column(name = "gioiTinh")
    private Boolean gioiTinh;

    @Column(name = "namSinh")
    private Integer namSinh;

    @Size(max = 10)
    @Column(name = "soDienThoai", length = 10)
    private String soDienThoai;

    @Size(max = 200)
    @Nationalized
    @Column(name = "diaChi", length = 200)
    private String diaChi;


}
