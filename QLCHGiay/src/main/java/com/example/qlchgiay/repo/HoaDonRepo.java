package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.HoaDon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface HoaDonRepo extends JpaRepository<HoaDon, Integer> {}
