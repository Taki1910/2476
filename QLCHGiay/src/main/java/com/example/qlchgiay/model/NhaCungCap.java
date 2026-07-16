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
@Table(name = "NhaCungCap")
public class NhaCungCap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maNCC", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "tenNCC", nullable = false, length = 100)
    private String tenNCC;

    @Size(max = 15)
    @Column(name = "soDienThoai", length = 15)
    private String soDienThoai;

    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @Size(max = 200)
    @Nationalized
    @Column(name = "diaChi", length = 200)
    private String diaChi;

    @Size(max = 30)
    @Nationalized
    @Column(name = "trangThai", length = 30)
    private String trangThai;


}