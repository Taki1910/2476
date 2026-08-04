package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

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

    @OneToMany(mappedBy = "maSP", fetch = FetchType.LAZY)
    private Set<ChiTietSanPham> chiTietSanPhams = new LinkedHashSet<>();

    @Transient
    public String getHinhAnh() {
        String image = chiTietSanPhams.stream()
                .map(ChiTietSanPham::getHinhAnh)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (image == null || image.startsWith("/")
                || image.startsWith("http://") || image.startsWith("https://")) {
            return image;
        }
        return switch (image.toLowerCase()) {
            case "af1.jpg" -> "/images/products/nike.svg";
            case "superstar.jpg" -> "/images/products/adidas.svg";
            case "converse.jpg" -> "/images/products/converse.svg";
            case "jordan.jpg" -> "/images/products/jordan.svg";
            case "vans.jpg" -> "/images/products/vans.svg";
            default -> "/images/products/" + image;
        };
    }

}
