package com.example.qlchgiay.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.qlchgiay.model.TaiKhoan;

@Repository
public interface TaiKhoanRepo extends JpaRepository<TaiKhoan, Integer> {

    @Query("""
            SELECT t FROM TaiKhoan t
            LEFT JOIN FETCH t.maNhanVien nv
            LEFT JOIN FETCH nv.maChucVu
            WHERE t.tenDangNhap = :tenDangNhap AND t.matKhau = :matKhau
            """)
    TaiKhoan findByTenDangNhapAndMatKhau(@Param("tenDangNhap") String tenDangNhap,
                                         @Param("matKhau") String matKhau);

    TaiKhoan findByTenDangNhap(String tenDangNhap);
}
