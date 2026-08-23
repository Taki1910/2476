package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.PhienLamViec;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhienLamViecRepo extends JpaRepository<PhienLamViec, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PhienLamViec> findByMaNhanVienIdAndKetThucIsNull(Integer employeeId);

    List<PhienLamViec>
    findByMaNhanVienIdAndKetThucIsNotNullAndNhanVienDaXemFalseOrderByKetThucDesc(Integer employeeId);

    List<PhienLamViec> findByKetThucIsNotNullAndAdminDaXemFalseOrderByKetThucDesc();
}
