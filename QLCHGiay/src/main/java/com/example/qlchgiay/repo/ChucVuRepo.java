package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChucVu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChucVuRepo extends JpaRepository<ChucVu, Integer> {
    Optional<ChucVu> findFirstByTenChucVuIgnoreCase(String tenChucVu);
}
