package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChiTietHoaDon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChiTietHoaDonRepo extends JpaRepository<ChiTietHoaDon, Integer> {
    List<ChiTietHoaDon> findByMaHoaDonId(Integer hoaDonId);
}
