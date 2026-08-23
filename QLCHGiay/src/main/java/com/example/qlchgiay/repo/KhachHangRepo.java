package com.example.qlchgiay.repo;
import com.example.qlchgiay.model.KhachHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface KhachHangRepo extends JpaRepository<KhachHang, Integer> {
    boolean existsBySoDienThoai(String soDienThoai);
    boolean existsBySoDienThoaiAndIdNot(String soDienThoai, Integer id);
    List<KhachHang> findAllByOrderByIdDesc();
    List<KhachHang> findByTrangThaiOrderByIdDesc(String trangThai);
}
