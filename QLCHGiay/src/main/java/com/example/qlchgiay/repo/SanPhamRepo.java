package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.SanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface SanPhamRepo extends JpaRepository<SanPham, Integer> {
    List<SanPham> findTop5ByTonKhoLessThanEqualOrderByTonKhoAsc(Integer threshold);
}
