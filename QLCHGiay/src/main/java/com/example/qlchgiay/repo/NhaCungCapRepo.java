package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.NhaCungCap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface NhaCungCapRepo extends JpaRepository<NhaCungCap, Integer> {
    List<NhaCungCap> findAllByOrderByIdDesc();
}
