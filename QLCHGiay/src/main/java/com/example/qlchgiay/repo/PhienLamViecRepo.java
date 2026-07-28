package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.PhienLamViec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhienLamViecRepo extends JpaRepository<PhienLamViec, Integer> {
    List<PhienLamViec> findByMaNhanVienIdAndKetThucIsNull(Integer employeeId);

    List<PhienLamViec>
    findByMaNhanVienIdAndKetThucIsNotNullAndNhanVienDaXemFalseOrderByKetThucDesc(Integer employeeId);

    List<PhienLamViec> findByKetThucIsNotNullAndAdminDaXemFalseOrderByKetThucDesc();
}
