package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.KhachHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface KhachHangRepo extends JpaRepository<KhachHang, Integer> {}
