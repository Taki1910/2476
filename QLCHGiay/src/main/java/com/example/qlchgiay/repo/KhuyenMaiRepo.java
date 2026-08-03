package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.KhuyenMai;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface KhuyenMaiRepo extends JpaRepository<KhuyenMai, Integer> {
    @Override
    @EntityGraph(attributePaths = "sanPhams")
    List<KhuyenMai> findAll();

    @Override
    @EntityGraph(attributePaths = "sanPhams")
    Optional<KhuyenMai> findById(Integer id);

    @EntityGraph(attributePaths = "sanPhams")
    @Query("""
            SELECT DISTINCT k FROM KhuyenMai k
            WHERE k.trangThai = true
              AND k.batDau <= :at
              AND k.ketThuc > :at
            """)
    List<KhuyenMai> findActiveAt(@Param("at") LocalDateTime at);

    @Query("""
            SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END
            FROM KhuyenMai k JOIN k.sanPhams s
            WHERE k.trangThai = true
              AND s.id IN :productIds
              AND k.id <> :promotionId
              AND k.batDau < :endAt
              AND k.ketThuc > :startAt
            """)
    boolean existsOverlapping(
            @Param("promotionId") Integer promotionId,
            @Param("productIds") Set<Integer> productIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
