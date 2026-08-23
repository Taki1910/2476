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

IF COL_LENGTH(N'dbo.SanPham', N'trangThai') IS NULL
BEGIN
    ALTER TABLE dbo.SanPham ADD trangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_SanPham_TrangThai DEFAULT 'ACTIVE'
END;

IF COL_LENGTH(N'dbo.SanPham', N'version') IS NULL
BEGIN
    ALTER TABLE dbo.SanPham ADD version BIGINT NOT NULL
        CONSTRAINT DF_SanPham_Version DEFAULT 0
END;

IF COL_LENGTH(N'dbo.KhachHang', N'trangThai') IS NULL
BEGIN
    ALTER TABLE dbo.KhachHang ADD trangThai VARCHAR(20) NOT NULL
        CONSTRAINT DF_KhachHang_TrangThai DEFAULT 'ACTIVE'
END;

IF COL_LENGTH(N'dbo.KhachHang', N'ngaySinh') IS NULL
    ALTER TABLE dbo.KhachHang ADD ngaySinh DATE NULL;
IF COL_LENGTH(N'dbo.NhanVien', N'ngaySinh') IS NULL
    ALTER TABLE dbo.NhanVien ADD ngaySinh DATE NULL;

/* Legacy birth-year rows use 31 December so eligibility is never granted early. */
UPDATE dbo.KhachHang
SET ngaySinh = DATEFROMPARTS(namSinh, 12, 31)
WHERE ngaySinh IS NULL AND namSinh BETWEEN 1900 AND YEAR(GETDATE());

UPDATE dbo.NhanVien
SET ngaySinh = DATEFROMPARTS(namSinh, 12, 31)
WHERE ngaySinh IS NULL AND namSinh BETWEEN 1900 AND YEAR(GETDATE());

IF COL_LENGTH(N'dbo.HoaDon', N'tenKhachHangSnapshot') IS NULL
    ALTER TABLE dbo.HoaDon ADD tenKhachHangSnapshot NVARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.HoaDon', N'soDienThoaiKhachHangSnapshot') IS NULL
    ALTER TABLE dbo.HoaDon ADD soDienThoaiKhachHangSnapshot VARCHAR(15) NULL;
IF COL_LENGTH(N'dbo.HoaDon', N'tenNhanVienSnapshot') IS NULL
    ALTER TABLE dbo.HoaDon ADD tenNhanVienSnapshot NVARCHAR(100) NULL;

IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'tenSanPhamSnapshot') IS NULL
    ALTER TABLE dbo.ChiTietHoaDon ADD tenSanPhamSnapshot NVARCHAR(100) NULL;
IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'maSanPhamSnapshot') IS NULL
    ALTER TABLE dbo.ChiTietHoaDon ADD maSanPhamSnapshot VARCHAR(30) NULL;
IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'moTaBienTheSnapshot') IS NULL
    ALTER TABLE dbo.ChiTietHoaDon ADD moTaBienTheSnapshot NVARCHAR(200) NULL;
IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'tenKhuyenMaiSnapshot') IS NULL
    ALTER TABLE dbo.ChiTietHoaDon ADD tenKhuyenMaiSnapshot NVARCHAR(120) NULL;

/* Backfill hóa đơn cũ chưa có nhân viên trước khi áp NOT NULL. */
UPDATE invoice
SET maNhanVien = COALESCE(customerOrder.maNhanVien, fallbackEmployee.maNhanVien)
FROM dbo.HoaDon invoice
LEFT JOIN dbo.DonHang customerOrder ON customerOrder.maDonHang = invoice.maDonHang
OUTER APPLY (
    SELECT TOP (1) employee.maNhanVien
    FROM dbo.NhanVien employee
    WHERE LOWER(COALESCE(employee.trangThai, N'')) NOT LIKE N'%ngừng%'
      AND LOWER(COALESCE(employee.trangThai, N'')) NOT LIKE N'%inactive%'
      AND LOWER(COALESCE(employee.trangThai, N'')) NOT LIKE N'%disable%'
      AND LOWER(COALESCE(employee.trangThai, N'')) NOT LIKE N'%khóa%'
    ORDER BY employee.maNhanVien
) fallbackEmployee
WHERE invoice.maNhanVien IS NULL;

UPDATE customerOrder
SET maNhanVien = invoice.maNhanVien
FROM dbo.DonHang customerOrder
INNER JOIN dbo.HoaDon invoice ON invoice.maDonHang = customerOrder.maDonHang
WHERE customerOrder.maNhanVien IS NULL
  AND invoice.maNhanVien IS NOT NULL;

UPDATE invoice
SET tenKhachHangSnapshot = COALESCE(invoice.tenKhachHangSnapshot, customer.tenKH, N'Khách lẻ'),
    soDienThoaiKhachHangSnapshot = COALESCE(invoice.soDienThoaiKhachHangSnapshot, customer.soDienThoai),
    tenNhanVienSnapshot = COALESCE(invoice.tenNhanVienSnapshot, employee.tenNhanVien, N'Chưa phân công')
FROM dbo.HoaDon invoice
LEFT JOIN dbo.DonHang customerOrder ON customerOrder.maDonHang = invoice.maDonHang
LEFT JOIN dbo.KhachHang customer ON customer.maKH = customerOrder.maKH
LEFT JOIN dbo.NhanVien employee ON employee.maNhanVien = invoice.maNhanVien
WHERE invoice.tenKhachHangSnapshot IS NULL
   OR invoice.tenNhanVienSnapshot IS NULL;

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

IF OBJECT_ID(N'dbo.ThongBao', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ThongBao(
        maThongBao INT IDENTITY(1,1) PRIMARY KEY,
        loai VARCHAR(50) NOT NULL,
        tieuDe NVARCHAR(150) NOT NULL,
        noiDung NVARCHAR(500) NOT NULL,
        thoiGianTao DATETIME2 NOT NULL CONSTRAINT DF_ThongBao_ThoiGianTao DEFAULT SYSDATETIME(),
        thoiGianDaDoc DATETIME2 NULL,
        maNguoiNhan INT NOT NULL,
        loaiDoiTuong VARCHAR(50) NULL,
        maDoiTuong INT NULL,
        khoaChongTrung VARCHAR(200) NOT NULL,
        CONSTRAINT FK_ThongBao_TaiKhoan FOREIGN KEY(maNguoiNhan)
            REFERENCES dbo.TaiKhoan(maTaiKhoan),
        CONSTRAINT UX_ThongBao_NguoiNhan_Khoa UNIQUE(maNguoiNhan, khoaChongTrung)
    )
    CREATE INDEX IX_ThongBao_NguoiNhan_DaDoc_Tao
        ON dbo.ThongBao(maNguoiNhan, thoiGianDaDoc, thoiGianTao DESC)
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

UPDATE line
SET tenSanPhamSnapshot = COALESCE(line.tenSanPhamSnapshot, product.tenSP, N'Sản phẩm'),
    maSanPhamSnapshot = COALESCE(line.maSanPhamSnapshot, CONCAT('SP-', product.maSP)),
    moTaBienTheSnapshot = COALESCE(
        line.moTaBienTheSnapshot,
        CONCAT(category.tenLoai, N' / ', color.tenMau, N' / Size ', productSize.tenSize)
    ),
    tenKhuyenMaiSnapshot = COALESCE(line.tenKhuyenMaiSnapshot, promotion.tenKhuyenMai)
FROM dbo.ChiTietHoaDon line
LEFT JOIN dbo.ChiTietSanPham detail ON detail.maChiTietSP = line.maChiTietSP
LEFT JOIN dbo.SanPham product ON product.maSP = detail.maSP
LEFT JOIN dbo.Loai category ON category.maLoai = product.maLoai
LEFT JOIN dbo.Mau color ON color.maMau = product.maMau
LEFT JOIN dbo.Size productSize ON productSize.maSize = product.maSize
LEFT JOIN dbo.KhuyenMai promotion ON promotion.maKhuyenMai = line.maKhuyenMai
WHERE line.tenSanPhamSnapshot IS NULL
   OR line.maSanPhamSnapshot IS NULL;

/* Business constraints: validate existing rows before enforcing them. */
UPDATE dbo.HoaDon
SET trangThai = CASE
    WHEN LOWER(COALESCE(trangThai, N'')) LIKE N'%hủy%' THEN N'Đã hủy'
    WHEN LOWER(COALESCE(trangThai, N'')) LIKE N'%thanh toán%'
      OR LOWER(COALESCE(trangThai, N'')) LIKE N'%hoàn thành%' THEN N'Đã thanh toán'
    ELSE N'Chưa thanh toán'
END;

IF EXISTS (SELECT 1 FROM dbo.SanPham WHERE tonKho IS NULL OR tonKho < 0 OR gia IS NULL OR gia < 0)
    THROW 51000, N'Không thể áp dụng ràng buộc: SanPham có giá hoặc tồn kho không hợp lệ.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.SanPham
    WHERE maLoai IS NULL OR maMau IS NULL OR maChatLieu IS NULL OR maSize IS NULL
)
    THROW 51000, N'Không thể áp dụng ràng buộc: SanPham thiếu thuộc tính biến thể.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.SanPham
    GROUP BY tenSP, maLoai, maMau, maChatLieu, maSize
    HAVING COUNT(*) > 1
)
    THROW 51000, N'Không thể tạo khóa duy nhất: tồn tại biến thể sản phẩm trùng.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.HoaDon
    WHERE maDonHang IS NULL OR maNhanVien IS NULL OR ngayLap IS NULL
       OR tongTien IS NULL OR tongTien < 0 OR trangThai IS NULL
)
    THROW 51000, N'Không thể áp dụng ràng buộc: HoaDon có dữ liệu bắt buộc không hợp lệ.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.ChiTietHoaDon
    WHERE maHoaDon IS NULL OR maChiTietSP IS NULL OR soLuong IS NULL OR soLuong <= 0
       OR donGia IS NULL OR donGia < 0 OR giaGoc IS NULL OR giaGoc < 0
)
    THROW 51000, N'Không thể áp dụng ràng buộc: ChiTietHoaDon có dữ liệu không hợp lệ.', 1;
IF EXISTS (
    SELECT 1 FROM dbo.ThanhToan
    WHERE maHoaDon IS NULL OR phuongThuc IS NULL OR ngayThanhToan IS NULL
       OR soTien IS NULL OR soTien < 0 OR trangThai IS NULL
)
    THROW 51000, N'Không thể áp dụng ràng buộc: ThanhToan có dữ liệu không hợp lệ.', 1;
IF EXISTS (SELECT maHoaDon FROM dbo.ThanhToan GROUP BY maHoaDon HAVING COUNT(*) > 1)
    THROW 51000, N'Không thể tạo khóa duy nhất: một hóa đơn có nhiều thanh toán.', 1;
IF EXISTS (
    SELECT maNhanVien FROM dbo.TaiKhoan
    WHERE maNhanVien IS NOT NULL GROUP BY maNhanVien HAVING COUNT(*) > 1
)
    THROW 51000, N'Không thể tạo khóa duy nhất: một nhân viên có nhiều tài khoản.', 1;
IF EXISTS (
    SELECT maNhanVien FROM dbo.PhienLamViec
    WHERE ketThuc IS NULL GROUP BY maNhanVien HAVING COUNT(*) > 1
)
    THROW 51000, N'Không thể tạo khóa duy nhất: một nhân viên có nhiều phiên đang mở.', 1;

ALTER TABLE dbo.SanPham ALTER COLUMN maLoai INT NOT NULL;
ALTER TABLE dbo.SanPham ALTER COLUMN maMau INT NOT NULL;
ALTER TABLE dbo.SanPham ALTER COLUMN maChatLieu INT NOT NULL;
ALTER TABLE dbo.SanPham ALTER COLUMN maSize INT NOT NULL;
ALTER TABLE dbo.SanPham ALTER COLUMN gia DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.SanPham ALTER COLUMN tonKho INT NOT NULL;
ALTER TABLE dbo.HoaDon ALTER COLUMN maDonHang INT NOT NULL;
ALTER TABLE dbo.HoaDon ALTER COLUMN maNhanVien INT NOT NULL;
ALTER TABLE dbo.HoaDon ALTER COLUMN ngayLap DATE NOT NULL;
ALTER TABLE dbo.HoaDon ALTER COLUMN tongTien DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.HoaDon ALTER COLUMN trangThai NVARCHAR(50) NOT NULL;
ALTER TABLE dbo.ChiTietHoaDon ALTER COLUMN maHoaDon INT NOT NULL;
ALTER TABLE dbo.ChiTietHoaDon ALTER COLUMN maChiTietSP INT NOT NULL;

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.ChiTietHoaDon')
      AND name IN (N'soLuong', N'donGia')
      AND is_nullable = 1
) AND COL_LENGTH(N'dbo.ChiTietHoaDon', N'thanhTien') IS NOT NULL
    ALTER TABLE dbo.ChiTietHoaDon DROP COLUMN thanhTien;

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.ChiTietHoaDon')
      AND name = N'soLuong' AND is_nullable = 1
)
    ALTER TABLE dbo.ChiTietHoaDon ALTER COLUMN soLuong INT NOT NULL;
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.ChiTietHoaDon')
      AND name = N'donGia' AND is_nullable = 1
)
    ALTER TABLE dbo.ChiTietHoaDon ALTER COLUMN donGia DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.ChiTietHoaDon ALTER COLUMN giaGoc DECIMAL(18,2) NOT NULL;

IF COL_LENGTH(N'dbo.ChiTietHoaDon', N'thanhTien') IS NULL
    ALTER TABLE dbo.ChiTietHoaDon ADD thanhTien AS (soLuong * donGia);

ALTER TABLE dbo.ThanhToan ALTER COLUMN maHoaDon INT NOT NULL;
ALTER TABLE dbo.ThanhToan ALTER COLUMN phuongThuc NVARCHAR(50) NOT NULL;
ALTER TABLE dbo.ThanhToan ALTER COLUMN ngayThanhToan DATE NOT NULL;
ALTER TABLE dbo.ThanhToan ALTER COLUMN soTien DECIMAL(18,2) NOT NULL;
ALTER TABLE dbo.ThanhToan ALTER COLUMN trangThai NVARCHAR(30) NOT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SanPham_TonKho')
    ALTER TABLE dbo.SanPham ADD CONSTRAINT CK_SanPham_TonKho CHECK (tonKho >= 0);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SanPham_Gia')
    ALTER TABLE dbo.SanPham ADD CONSTRAINT CK_SanPham_Gia CHECK (gia >= 0);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_SanPham_TrangThai')
    ALTER TABLE dbo.SanPham ADD CONSTRAINT CK_SanPham_TrangThai CHECK (trangThai IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_KhachHang_TrangThai')
    ALTER TABLE dbo.KhachHang ADD CONSTRAINT CK_KhachHang_TrangThai CHECK (trangThai IN ('ACTIVE', 'ARCHIVED'));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_HoaDon_TongTien')
    ALTER TABLE dbo.HoaDon ADD CONSTRAINT CK_HoaDon_TongTien CHECK (tongTien >= 0);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_HoaDon_TrangThai')
    ALTER TABLE dbo.HoaDon ADD CONSTRAINT CK_HoaDon_TrangThai CHECK (trangThai IN (N'Chưa thanh toán', N'Đã thanh toán', N'Đã hủy'));
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_ChiTietHoaDon_SoLuong')
    ALTER TABLE dbo.ChiTietHoaDon ADD CONSTRAINT CK_ChiTietHoaDon_SoLuong CHECK (soLuong > 0);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_ChiTietHoaDon_Gia')
    ALTER TABLE dbo.ChiTietHoaDon ADD CONSTRAINT CK_ChiTietHoaDon_Gia CHECK (donGia >= 0 AND giaGoc >= 0);
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_ThanhToan_SoTien')
    ALTER TABLE dbo.ThanhToan ADD CONSTRAINT CK_ThanhToan_SoTien CHECK (soTien >= 0);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_SanPham_BienThe' AND object_id = OBJECT_ID(N'dbo.SanPham'))
    CREATE UNIQUE INDEX UX_SanPham_BienThe ON dbo.SanPham(tenSP, maLoai, maMau, maChatLieu, maSize);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_ThanhToan_HoaDon' AND object_id = OBJECT_ID(N'dbo.ThanhToan'))
    CREATE UNIQUE INDEX UX_ThanhToan_HoaDon ON dbo.ThanhToan(maHoaDon);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_TaiKhoan_NhanVien' AND object_id = OBJECT_ID(N'dbo.TaiKhoan'))
    CREATE UNIQUE INDEX UX_TaiKhoan_NhanVien ON dbo.TaiKhoan(maNhanVien) WHERE maNhanVien IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_PhienLamViec_DangMo' AND object_id = OBJECT_ID(N'dbo.PhienLamViec'))
    CREATE UNIQUE INDEX UX_PhienLamViec_DangMo ON dbo.PhienLamViec(maNhanVien) WHERE ketThuc IS NULL;

IF EXISTS (
    SELECT 1 FROM sys.foreign_keys fk
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.LichSuChinhSuaHoaDon')
      AND fk.referenced_object_id = OBJECT_ID(N'dbo.HoaDon')
      AND fk.delete_referential_action = 1
)
BEGIN
    DECLARE @cascadeHistoryFk SYSNAME
    DECLARE @dropHistoryFkSql NVARCHAR(4000)
    SELECT TOP (1) @cascadeHistoryFk = fk.name
    FROM sys.foreign_keys fk
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.LichSuChinhSuaHoaDon')
      AND fk.referenced_object_id = OBJECT_ID(N'dbo.HoaDon')
      AND fk.delete_referential_action = 1
    SET @dropHistoryFkSql = N'ALTER TABLE dbo.LichSuChinhSuaHoaDon DROP CONSTRAINT '
                            + QUOTENAME(@cascadeHistoryFk)
    EXEC sys.sp_executesql @dropHistoryFkSql
END;

IF EXISTS (
    SELECT 1 FROM dbo.KhuyenMai
    WHERE giaTri <= 0
       OR giaTri <> FLOOR(giaTri)
       OR (loaiGiam = 'PHAN_TRAM' AND giaTri > 100)
)
    THROW 51000, N'Không thể áp dụng ràng buộc: KhuyenMai có giá trị giảm không hợp lệ.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = N'CK_KhuyenMai_GiaTri'
      AND parent_object_id = OBJECT_ID(N'dbo.KhuyenMai')
)
BEGIN
    ALTER TABLE dbo.KhuyenMai WITH CHECK
        ADD CONSTRAINT CK_KhuyenMai_GiaTri CHECK (
            giaTri > 0
            AND giaTri = FLOOR(giaTri)
            AND (loaiGiam <> 'PHAN_TRAM' OR giaTri <= 100)
        )
END;
IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys fk
    WHERE fk.parent_object_id = OBJECT_ID(N'dbo.LichSuChinhSuaHoaDon')
      AND fk.referenced_object_id = OBJECT_ID(N'dbo.HoaDon')
)
    ALTER TABLE dbo.LichSuChinhSuaHoaDon ADD CONSTRAINT FK_LichSuHoaDon_HoaDon
        FOREIGN KEY(maHoaDon) REFERENCES dbo.HoaDon(maHoaDon);
