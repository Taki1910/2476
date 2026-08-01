package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThanhToanRepo extends JpaRepository<ThanhToan, Integer> {
    boolean existsByMaHoaDonId(Integer hoaDonId);
}
