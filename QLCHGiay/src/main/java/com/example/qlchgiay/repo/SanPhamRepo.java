package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.SanPham;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SanPhamRepo extends JpaRepository<SanPham, Integer> {
    @Override
    @EntityGraph(attributePaths = {"maLoai", "maMau", "maChatLieu", "maSize"})
    List<SanPham> findAll();

    @Override
    @EntityGraph(attributePaths = {"maLoai", "maMau", "maChatLieu", "maSize"})
    List<SanPham> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = {"maLoai", "maMau", "maChatLieu", "maSize"})
    Optional<SanPham> findById(Integer id);

    @EntityGraph(attributePaths = {"maLoai", "maMau", "maChatLieu", "maSize"})
    List<SanPham> findAllByOrderByTenSPAsc();
    List<SanPham> findByTenSPIgnoreCase(String tenSP);

    @EntityGraph(attributePaths = {"maMau", "maSize"})
    List<SanPham> findTop5ByTonKhoLessThanEqualOrderByTonKhoAsc(Integer threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SanPham s WHERE s.id = :id")
    Optional<SanPham> findByIdForUpdate(@Param("id") Integer id);
}
