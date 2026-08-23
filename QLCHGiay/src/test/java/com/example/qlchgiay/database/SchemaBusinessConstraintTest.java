package com.example.qlchgiay.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaBusinessConstraintTest {

    @Test
    void runtimeMigrationContainsBusinessIntegrityBackstopsAndNoDemoWrites()
            throws IOException {
        String schema;
        try (var stream = getClass().getResourceAsStream("/schema.sql")) {
            assertTrue(stream != null, "schema.sql must be available at runtime");
            schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(schema.contains("CK_SanPham_TonKho"));
        assertTrue(schema.contains("CK_ChiTietHoaDon_SoLuong"));
        assertTrue(schema.contains("CK_HoaDon_TrangThai"));
        assertTrue(schema.contains("UX_SanPham_BienThe"));
        assertTrue(schema.contains("UX_ThanhToan_HoaDon"));
        assertTrue(schema.contains("UX_TaiKhoan_NhanVien"));
        assertTrue(schema.contains("UX_PhienLamViec_DangMo"));
        assertTrue(schema.contains("CREATE TABLE dbo.ThongBao"));
        assertTrue(schema.contains("UX_ThongBao_NguoiNhan_Khoa"));
        assertTrue(schema.contains("IX_ThongBao_NguoiNhan_DaDoc_Tao"));
        assertTrue(schema.contains("ALTER TABLE dbo.KhachHang ADD ngaySinh DATE NULL"));
        assertTrue(schema.contains("ALTER TABLE dbo.NhanVien ADD ngaySinh DATE NULL"));
        assertTrue(schema.contains("DATEFROMPARTS(namSinh, 12, 31)"));
        int employeeBackfill = schema.indexOf("SET maNhanVien = COALESCE(");
        int invoiceValidation = schema.indexOf("Không thể áp dụng ràng buộc: HoaDon");
        assertTrue(employeeBackfill >= 0);
        assertTrue(employeeBackfill < invoiceValidation);
        assertTrue(schema.contains("DROP COLUMN thanhTien"));
        assertTrue(schema.contains("ADD thanhTien AS (soLuong * donGia)"));
        assertTrue(schema.contains("BEGIN\n    DECLARE @cascadeHistoryFk SYSNAME"));
        assertFalse(schema.toUpperCase().contains("INSERT INTO DBO.HOADON"));
        assertFalse(schema.toUpperCase().contains("INSERT INTO DBO.THANHTOAN"));
        assertFalse(schema.toUpperCase().contains("INSERT INTO DBO.LICHSUCHINHSUAHOADON"));
    }
}
