package com.example.qlchgiay.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ThongBao")
public class AppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maThongBao", nullable = false)
    private Integer id;

    @Column(name = "loai", nullable = false, length = 50)
    private String type;

    @Nationalized
    @Column(name = "tieuDe", nullable = false, length = 150)
    private String title;

    @Nationalized
    @Column(name = "noiDung", nullable = false, length = 500)
    private String message;

    @Column(name = "thoiGianTao", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "thoiGianDaDoc")
    private LocalDateTime readAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maNguoiNhan", nullable = false)
    private TaiKhoan recipient;

    @Column(name = "loaiDoiTuong", length = 50)
    private String relatedEntityType;

    @Column(name = "maDoiTuong")
    private Integer relatedEntityId;

    @Column(name = "khoaChongTrung", nullable = false, length = 200)
    private String dedupeKey;

    @PrePersist
    void assignCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
