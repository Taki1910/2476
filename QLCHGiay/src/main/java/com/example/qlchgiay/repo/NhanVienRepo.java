package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.NhanVien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NhanVienRepo extends JpaRepository<NhanVien, Integer> {
    @Query("""
            SELECT nv FROM NhanVien nv
            LEFT JOIN FETCH nv.maChucVu
            ORDER BY nv.id DESC
            """)
    List<NhanVien> findAllWithPositionOrderByIdDesc();

    @Query("""
            SELECT nv FROM NhanVien nv
            LEFT JOIN FETCH nv.maChucVu
            WHERE LOWER(COALESCE(nv.trangThai, '')) NOT LIKE '%ngừng%'
              AND LOWER(COALESCE(nv.trangThai, '')) NOT LIKE '%inactive%'
              AND LOWER(COALESCE(nv.trangThai, '')) NOT LIKE '%disable%'
              AND LOWER(COALESCE(nv.trangThai, '')) NOT LIKE '%khóa%'
            ORDER BY nv.id DESC
            """)
    List<NhanVien> findActiveWithPositionOrderByIdDesc();
}
