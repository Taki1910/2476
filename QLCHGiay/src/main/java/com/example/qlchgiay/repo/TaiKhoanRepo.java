package com.example.qlchgiay.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.qlchgiay.model.TaiKhoan;

import java.util.List;
import java.util.Optional;

public interface TaiKhoanRepo extends JpaRepository<TaiKhoan, Integer> {

    @Query("""
            SELECT t FROM TaiKhoan t
            LEFT JOIN FETCH t.maNhanVien nv
            LEFT JOIN FETCH nv.maChucVu
            WHERE t.tenDangNhap = :tenDangNhap
            """)
    Optional<TaiKhoan> findWithEmployeeByTenDangNhap(
            @Param("tenDangNhap") String tenDangNhap
    );

    TaiKhoan findByTenDangNhap(String tenDangNhap);

    boolean existsByTenDangNhapIgnoreCase(String tenDangNhap);

    Optional<TaiKhoan> findFirstByMaNhanVienId(Integer employeeId);

    @Query("""
            SELECT t FROM TaiKhoan t
            LEFT JOIN FETCH t.maNhanVien nv
            LEFT JOIN FETCH nv.maChucVu
            ORDER BY t.tenDangNhap ASC
            """)
    List<TaiKhoan> findAllWithEmployeesOrderByUsername();

    List<TaiKhoan> findByYeuCauDatLaiMatKhauTrueOrderByTenDangNhapAsc();
}
