USE QuanLyBanHang;
GO

IF OBJECT_ID(N'dbo.PhienLamViec', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PhienLamViec(
        maPhien INT IDENTITY(1,1) PRIMARY KEY,
        maNhanVien INT NOT NULL,
        batDau DATETIME2 NOT NULL,
        ketThuc DATETIME2,
        soSanPhamBan INT NOT NULL CONSTRAINT DF_PhienLamViec_SanPham DEFAULT 0,
        soKhachHangMoi INT NOT NULL CONSTRAINT DF_PhienLamViec_KhachHang DEFAULT 0,
        doanhThu DECIMAL(18,2) NOT NULL CONSTRAINT DF_PhienLamViec_DoanhThu DEFAULT 0,
        nhanVienDaXem BIT NOT NULL CONSTRAINT DF_PhienLamViec_NhanVienDaXem DEFAULT 0,
        adminDaXem BIT NOT NULL CONSTRAINT DF_PhienLamViec_AdminDaXem DEFAULT 0,
        CONSTRAINT FK_PhienLamViec_NhanVien
            FOREIGN KEY(maNhanVien) REFERENCES dbo.NhanVien(maNhanVien)
    );
END;
GO
