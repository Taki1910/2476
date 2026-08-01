package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.HoaDon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HoaDonRepo extends JpaRepository<HoaDon, Integer> {
    @Override
    @EntityGraph(attributePaths = {"maDonHang", "maDonHang.maKH", "maNhanVien"})
    java.util.List<HoaDon> findAll();

    @Override
    @EntityGraph(attributePaths = {"maDonHang", "maDonHang.maKH", "maNhanVien"})
    Optional<HoaDon> findById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"maDonHang", "maNhanVien"})
    @Query("SELECT h FROM HoaDon h WHERE h.id = :id")
    Optional<HoaDon> findByIdForUpdate(@Param("id") Integer id);
}
