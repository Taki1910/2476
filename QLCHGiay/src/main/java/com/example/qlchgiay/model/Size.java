package com.example.qlchgiay.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "Size")
public class Size {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maSize", nullable = false)
    private Integer id;

    @jakarta.validation.constraints.Size(max = 20)
    @NotNull
    @Nationalized
    @Column(name = "tenSize", nullable = false, length = 20)
    private String tenSize;

    @ColumnDefault("0")
    @Column(name = "tonKho")
    private Integer tonKho;


}