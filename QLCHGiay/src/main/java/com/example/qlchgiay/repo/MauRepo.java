package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.Mau;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MauRepo extends JpaRepository<Mau, Integer> {
    Optional<Mau> findFirstByTenMauIgnoreCase(String tenMau);
}
