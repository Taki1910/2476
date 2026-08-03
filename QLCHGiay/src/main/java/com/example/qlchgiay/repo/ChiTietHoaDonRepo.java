package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChiTietHoaDon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChiTietHoaDonRepo extends JpaRepository<ChiTietHoaDon, Integer> {
    @Override
    @EntityGraph(attributePaths = {"maHoaDon", "maChiTietSP", "maChiTietSP.maSP", "maKhuyenMai"})
    List<ChiTietHoaDon> findAll();

    @EntityGraph(attributePaths = {"maChiTietSP", "maChiTietSP.maSP", "maKhuyenMai"})
    List<ChiTietHoaDon> findByMaHoaDonId(Integer hoaDonId);
    void deleteByMaHoaDonId(Integer hoaDonId);
}
