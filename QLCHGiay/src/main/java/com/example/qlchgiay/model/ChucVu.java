package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "ChucVu")
public class ChucVu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maChucVu", nullable = false)
    private Integer id;

    @Size(max = 50)
    @Nationalized
    @Column(name = "tenChucVu", length = 50)
    private String tenChucVu;


}