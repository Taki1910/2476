package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Mau")
public class Mau {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maMau", nullable = false)
    private Integer id;

    @Size(max = 50)
    @NotNull
    @Nationalized
    @Column(name = "tenMau", nullable = false, length = 50)
    private String tenMau;

    @ColumnDefault("0")
    @Column(name = "tonKho")
    private Integer tonKho;


}