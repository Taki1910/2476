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
    )
END;

IF COL_LENGTH(N'dbo.HoaDon', N'maPhien') IS NULL
BEGIN
    ALTER TABLE dbo.HoaDon ADD maPhien INT NULL
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = N'FK_HoaDon_PhienLamViec'
)
BEGIN
    ALTER TABLE dbo.HoaDon
        ADD CONSTRAINT FK_HoaDon_PhienLamViec
            FOREIGN KEY(maPhien) REFERENCES dbo.PhienLamViec(maPhien)
END;

IF OBJECT_ID(N'dbo.LichSuChinhSuaHoaDon', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.LichSuChinhSuaHoaDon(
        maLichSu INT IDENTITY(1,1) PRIMARY KEY,
        maHoaDon INT NOT NULL,
        maPhien INT,
        nguoiChinhSua NVARCHAR(100) NOT NULL,
        thoiGian DATETIME2 NOT NULL
            CONSTRAINT DF_LichSuHoaDon_ThoiGian DEFAULT SYSDATETIME(),
        duLieuTruoc NVARCHAR(MAX) NOT NULL,
        duLieuSau NVARCHAR(MAX) NOT NULL,
        CONSTRAINT FK_LichSuHoaDon_HoaDon
            FOREIGN KEY(maHoaDon) REFERENCES dbo.HoaDon(maHoaDon) ON DELETE CASCADE,
        CONSTRAINT FK_LichSuHoaDon_Phien
            FOREIGN KEY(maPhien) REFERENCES dbo.PhienLamViec(maPhien)
    )
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_LichSuHoaDon_HoaDon_ThoiGian'
      AND object_id = OBJECT_ID(N'dbo.LichSuChinhSuaHoaDon')
)
BEGIN
    CREATE INDEX IX_LichSuHoaDon_HoaDon_ThoiGian
        ON dbo.LichSuChinhSuaHoaDon(maHoaDon, thoiGian DESC)
END;

/* Lịch sử mẫu: gắn vào hóa đơn mới nhất và chạy lại không tạo bản ghi trùng. */
INSERT INTO dbo.LichSuChinhSuaHoaDon
    (maHoaDon, maPhien, nguoiChinhSua, thoiGian, duLieuTruoc, duLieuSau)
SELECT
    hoaDon.maHoaDon,
    hoaDon.maPhien,
    N'Dữ liệu mẫu',
    DATEADD(MINUTE, mau.soPhut, SYSDATETIME()),
    mau.duLieuTruoc,
    mau.duLieuSau
FROM (VALUES
    (-30,
     N'Khách hàng: Khách lẻ | Trạng thái: Chưa thanh toán | Sản phẩm: Giày thể thao × 1 | Tổng tiền: 850000 đ',
     N'Khách hàng: Nguyễn Văn An | Trạng thái: Chưa thanh toán | Sản phẩm: Giày thể thao × 1 | Tổng tiền: 850000 đ'),
    (-20,
     N'Khách hàng: Nguyễn Văn An | Trạng thái: Chưa thanh toán | Sản phẩm: Giày thể thao × 1 | Tổng tiền: 850000 đ',
     N'Khách hàng: Nguyễn Văn An | Trạng thái: Chưa thanh toán | Sản phẩm: Giày thể thao × 2 | Tổng tiền: 1700000 đ'),
    (-10,
     N'Khách hàng: Nguyễn Văn An | Trạng thái: Chưa thanh toán | Sản phẩm: Giày thể thao × 2 | Tổng tiền: 1700000 đ',
     N'Khách hàng: Nguyễn Văn An | Trạng thái: Đã hủy | Sản phẩm: Giày thể thao × 2 | Tổng tiền: 1700000 đ')
) mau(soPhut, duLieuTruoc, duLieuSau)
CROSS JOIN (
    SELECT TOP (1) maHoaDon, maPhien
    FROM dbo.HoaDon
    ORDER BY maHoaDon DESC
) hoaDon
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.LichSuChinhSuaHoaDon lichSu
    WHERE lichSu.maHoaDon = hoaDon.maHoaDon
      AND lichSu.nguoiChinhSua = N'Dữ liệu mẫu'
      AND lichSu.duLieuTruoc = mau.duLieuTruoc
      AND lichSu.duLieuSau = mau.duLieuSau
);

IF COL_LENGTH(N'dbo.TaiKhoan', N'soLanDangNhapSai') IS NULL
BEGIN
    ALTER TABLE dbo.TaiKhoan
        ADD soLanDangNhapSai INT NOT NULL
            CONSTRAINT DF_TaiKhoan_SoLanDangNhapSai DEFAULT 0
END;

IF COL_LENGTH(N'dbo.TaiKhoan', N'yeuCauDatLaiMatKhau') IS NULL
BEGIN
    ALTER TABLE dbo.TaiKhoan
        ADD yeuCauDatLaiMatKhau BIT NOT NULL
            CONSTRAINT DF_TaiKhoan_YeuCauDatLaiMatKhau DEFAULT 0
END;

IF COL_LENGTH(N'dbo.TaiKhoan', N'tamKhoaDangNhap') IS NULL
BEGIN
    ALTER TABLE dbo.TaiKhoan
        ADD tamKhoaDangNhap BIT NOT NULL
            CONSTRAINT DF_TaiKhoan_TamKhoaDangNhap DEFAULT 0
END;

IF OBJECT_ID(N'dbo.KhuyenMai', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.KhuyenMai(
        maKhuyenMai INT IDENTITY(1,1) PRIMARY KEY,
        tenKhuyenMai NVARCHAR(120) NOT NULL,
        loaiGiam VARCHAR(20) NOT NULL,
        giaTri DECIMAL(18,2) NOT NULL,
        batDau DATETIME2 NOT NULL,
        ketThuc DATETIME2 NOT NULL,
        trangThai BIT NOT NULL CONSTRAINT DF_KhuyenMai_TrangThai DEFAULT 1,
        CONSTRAINT CK_KhuyenMai_Loai CHECK (loaiGiam IN ('PHAN_TRAM', 'SO_TIEN')),
        CONSTRAINT CK_KhuyenMai_ThoiGian CHECK (batDau < ketThuc)
    )
END;

IF OBJECT_ID(N'dbo.KhuyenMaiSanPham', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.KhuyenMaiSanPham(
        maKhuyenMai INT NOT NULL,
        maSP INT NOT NULL,
        CONSTRAINT PK_KhuyenMaiSanPham PRIMARY KEY(maKhuyenMai, maSP),
        CONSTRAINT FK_KhuyenMaiSanPham_KhuyenMai
            FOREIGN KEY(maKhuyenMai) REFERENCES dbo.KhuyenMai(maKhuyenMai) ON DELETE CASCADE,
        CONSTRAINT FK_KhuyenMaiSanPham_SanPham
            FOREIGN KEY(maSP) REFERENCES dbo.SanPham(maSP)
    )
END;

IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'giaGoc') IS NULL
BEGIN
    ALTER TABLE dbo.ChiTietHoaDon ADD giaGoc DECIMAL(18,2) NULL
END;

UPDATE dbo.ChiTietHoaDon SET giaGoc = donGia WHERE giaGoc IS NULL;

IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'maKhuyenMai') IS NULL
BEGIN
    ALTER TABLE dbo.ChiTietHoaDon ADD maKhuyenMai INT NULL
END;

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_ChiTietHoaDon_KhuyenMai')
BEGIN
    ALTER TABLE dbo.ChiTietHoaDon
        ADD CONSTRAINT FK_ChiTietHoaDon_KhuyenMai
            FOREIGN KEY(maKhuyenMai) REFERENCES dbo.KhuyenMai(maKhuyenMai)
END;

/* Dữ liệu mẫu khuyến mại: chạy lại không tạo bản ghi trùng. */
IF EXISTS (SELECT 1 FROM dbo.SanPham)
   AND NOT EXISTS (SELECT 1 FROM dbo.KhuyenMai WHERE tenKhuyenMai = N'Demo - Giảm 15% tuần này')
BEGIN
    INSERT INTO dbo.KhuyenMai
        (tenKhuyenMai, loaiGiam, giaTri, batDau, ketThuc, trangThai)
    VALUES
        (N'Demo - Giảm 15% tuần này', 'PHAN_TRAM', 15,
         DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, 14, SYSDATETIME()), 1)
END;

IF EXISTS (SELECT 1 FROM dbo.SanPham)
   AND NOT EXISTS (SELECT 1 FROM dbo.KhuyenMai WHERE tenKhuyenMai = N'Demo - Giảm 200.000đ tháng tới')
BEGIN
    INSERT INTO dbo.KhuyenMai
        (tenKhuyenMai, loaiGiam, giaTri, batDau, ketThuc, trangThai)
    VALUES
        (N'Demo - Giảm 200.000đ tháng tới', 'SO_TIEN', 200000,
         DATEADD(DAY, 15, SYSDATETIME()), DATEADD(DAY, 30, SYSDATETIME()), 1)
END;

IF EXISTS (SELECT 1 FROM dbo.SanPham)
   AND NOT EXISTS (SELECT 1 FROM dbo.KhuyenMai WHERE tenKhuyenMai = N'Demo - Chương trình đã tắt')
BEGIN
    INSERT INTO dbo.KhuyenMai
        (tenKhuyenMai, loaiGiam, giaTri, batDau, ketThuc, trangThai)
    VALUES
        (N'Demo - Chương trình đã tắt', 'PHAN_TRAM', 10,
         DATEADD(DAY, -30, SYSDATETIME()), DATEADD(DAY, -15, SYSDATETIME()), 0)
END;

/* Liên kết theo ID thực tế, không phụ thuộc IDENTITY hiện tại hay ID mẫu cố định. */
INSERT INTO dbo.KhuyenMaiSanPham(maKhuyenMai, maSP)
SELECT promotion.maKhuyenMai, product.maSP
FROM dbo.KhuyenMai promotion
CROSS JOIN (SELECT MAX(maSP) AS maSP FROM dbo.SanPham) product
WHERE promotion.tenKhuyenMai IN (
          N'Demo - Giảm 15% tuần này',
          N'Demo - Giảm 200.000đ tháng tới',
          N'Demo - Chương trình đã tắt'
      )
  AND product.maSP IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.KhuyenMaiSanPham relation
      WHERE relation.maKhuyenMai = promotion.maKhuyenMai
        AND relation.maSP = product.maSP
  );
