package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.ChiTietHoaDon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChiTietHoaDonRepo extends JpaRepository<ChiTietHoaDon, Integer> {
    @Override
    @EntityGraph(attributePaths = {"maHoaDon", "maChiTietSP", "maChiTietSP.maSP", "maKhuyenMai"})
    List<ChiTietHoaDon> findAll();

    @EntityGraph(attributePaths = {"maChiTietSP", "maChiTietSP.maSP", "maKhuyenMai"})
    List<ChiTietHoaDon> findByMaHoaDonId(Integer hoaDonId);

    @Query("""
            SELECT COALESCE(SUM(line.soLuong), 0)
            FROM ChiTietHoaDon line
            WHERE line.maChiTietSP.maSP.id = :productId
              AND line.maHoaDon.trangThai = :unpaidStatus
              AND (:excludedInvoiceId IS NULL OR line.maHoaDon.id <> :excludedInvoiceId)
            """)
    Long sumCommittedQuantity(
            @Param("productId") Integer productId,
            @Param("unpaidStatus") String unpaidStatus,
            @Param("excludedInvoiceId") Integer excludedInvoiceId
    );

    void deleteByMaHoaDonId(Integer hoaDonId);
}
