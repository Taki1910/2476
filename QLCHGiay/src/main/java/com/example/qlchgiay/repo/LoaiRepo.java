package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.Loai;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoaiRepo extends JpaRepository<Loai, Integer> {
    Optional<Loai> findFirstByTenLoaiIgnoreCase(String tenLoai);
}
