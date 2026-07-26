package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChiTietSanPham;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChiTietSanPhamRepo extends JpaRepository<ChiTietSanPham, Integer> {
    List<ChiTietSanPham> findAllByOrderByMaSPTenSPAsc();
    Optional<ChiTietSanPham> findFirstByMaSPId(Integer productId);
}
