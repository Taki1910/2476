package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.LichSuChinhSuaHoaDon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LichSuChinhSuaHoaDonRepo
        extends JpaRepository<LichSuChinhSuaHoaDon, Integer> {
    List<LichSuChinhSuaHoaDon>
    findByMaHoaDonIdOrderByThoiGianDesc(Integer invoiceId);
}
