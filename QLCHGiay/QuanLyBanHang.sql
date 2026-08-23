/*
   DEVELOPMENT BOOTSTRAP ONLY — run manually against a disposable/local database.
   This file creates the database and sample rows; the application never executes it.
   Production/runtime upgrades belong in src/main/resources/schema.sql and must not
   contain demo invoices, payments, promotions, prices, or audit history.
*/
CREATE DATABASE QuanLyBanHang;
GO

USE QuanLyBanHang;
GO

/* Keep development restarts from abandoning 1,000-value IDENTITY cache blocks. */
ALTER DATABASE SCOPED CONFIGURATION SET IDENTITY_CACHE = OFF;
GO

/* =========================
   LOẠI SẢN PHẨM
========================= */

CREATE TABLE Loai(

    maLoai INT IDENTITY(1,1) PRIMARY KEY,

    tenLoai NVARCHAR(50) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   MÀU SẮC
========================= */

CREATE TABLE Mau(

    maMau INT IDENTITY(1,1) PRIMARY KEY,

    tenMau NVARCHAR(50) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   CHẤT LIỆU
========================= */

CREATE TABLE ChatLieu(

    maChatLieu INT IDENTITY(1,1) PRIMARY KEY,

    tenChatLieu NVARCHAR(50) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   SIZE
========================= */

CREATE TABLE Size(

    maSize INT IDENTITY(1,1) PRIMARY KEY,

    tenSize NVARCHAR(20) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   NHÀ CUNG CẤP
========================= */

CREATE TABLE NhaCungCap(

    maNCC INT IDENTITY(1,1) PRIMARY KEY,

    tenNCC NVARCHAR(100) NOT NULL,

    soDienThoai VARCHAR(15) NOT NULL,

    email VARCHAR(100) NOT NULL,

    diaChi NVARCHAR(200) NOT NULL,

    trangThai NVARCHAR(30) NOT NULL

);


/* =========================
   SẢN PHẨM
========================= */

CREATE TABLE SanPham(

    maSP INT IDENTITY(1,1) PRIMARY KEY,

    tenSP NVARCHAR(100) NOT NULL,

    maLoai INT,

    maMau INT,

    maChatLieu INT,

    maSize INT,

    gia DECIMAL(18,2),

    tonKho INT DEFAULT 0,

    trangThai VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    version BIGINT NOT NULL DEFAULT 0,

    FOREIGN KEY(maLoai)
        REFERENCES Loai(maLoai),

    FOREIGN KEY(maMau)
        REFERENCES Mau(maMau),

    FOREIGN KEY(maChatLieu)
        REFERENCES ChatLieu(maChatLieu),

    FOREIGN KEY(maSize)
        REFERENCES Size(maSize)

);


/* =========================
   CHI TIẾT SẢN PHẨM
========================= */

CREATE TABLE ChiTietSanPham(

    maChiTietSP INT IDENTITY(1,1) PRIMARY KEY,

    maSP INT,

    maNCC INT,

    moTa NVARCHAR(500),

    hinhAnh NVARCHAR(255),

    xuatXu NVARCHAR(100),

    thuongHieu NVARCHAR(100),

    trangThai NVARCHAR(50),

    FOREIGN KEY(maSP)
        REFERENCES SanPham(maSP),

    FOREIGN KEY(maNCC)
        REFERENCES NhaCungCap(maNCC)

);


/* =========================
   CHỨC VỤ
========================= */

CREATE TABLE ChucVu(

    maChucVu INT IDENTITY(1,1) PRIMARY KEY,

    tenChucVu NVARCHAR(50)

);
/* =========================
   NHÂN VIÊN
========================= */

CREATE TABLE NhanVien(

    maNhanVien INT IDENTITY(1,1) PRIMARY KEY,

    tenNhanVien NVARCHAR(100) NOT NULL,

    gioiTinh BIT,

    soDienThoai VARCHAR(15),

    namSinh INT,

    ngaySinh DATE,

    queQuan NVARCHAR(100),

    maChucVu INT,

    trangThai NVARCHAR(30),

    FOREIGN KEY(maChucVu)
        REFERENCES ChucVu(maChucVu)

);


/* =========================
   KHÁCH HÀNG
========================= */

CREATE TABLE KhachHang(

    maKH INT IDENTITY(1,1) PRIMARY KEY,

    tenKH NVARCHAR(100) NOT NULL,

    gioiTinh BIT,

    namSinh INT,

    ngaySinh DATE,

    soDienThoai VARCHAR(15),
    diaChi NVARCHAR(200),

    trangThai VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'

);


/* =========================
   TÀI KHOẢN
========================= */

CREATE TABLE TaiKhoan(

    maTaiKhoan INT IDENTITY(1,1) PRIMARY KEY,

    tenDangNhap VARCHAR(50) UNIQUE NOT NULL,

    matKhau VARCHAR(255) NOT NULL,

    vaiTro NVARCHAR(30),

    maNhanVien INT,

    trangThai NVARCHAR(30),

    soLanDangNhapSai INT NOT NULL
        CONSTRAINT DF_TaiKhoan_SoLanDangNhapSai DEFAULT 0,

    yeuCauDatLaiMatKhau BIT NOT NULL
        CONSTRAINT DF_TaiKhoan_YeuCauDatLaiMatKhau DEFAULT 0,

    tamKhoaDangNhap BIT NOT NULL
        CONSTRAINT DF_TaiKhoan_TamKhoaDangNhap DEFAULT 0,

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien)

);


/* =========================
   THÔNG BÁO PERSISTENT
========================= */

CREATE TABLE ThongBao(
    maThongBao INT IDENTITY(1,1) PRIMARY KEY,
    loai VARCHAR(50) NOT NULL,
    tieuDe NVARCHAR(150) NOT NULL,
    noiDung NVARCHAR(500) NOT NULL,
    thoiGianTao DATETIME2 NOT NULL
        CONSTRAINT DF_ThongBao_ThoiGianTao DEFAULT SYSDATETIME(),
    thoiGianDaDoc DATETIME2,
    maNguoiNhan INT NOT NULL,
    loaiDoiTuong VARCHAR(50),
    maDoiTuong INT,
    khoaChongTrung VARCHAR(200) NOT NULL,
    CONSTRAINT FK_ThongBao_TaiKhoan FOREIGN KEY(maNguoiNhan)
        REFERENCES TaiKhoan(maTaiKhoan),
    CONSTRAINT UX_ThongBao_NguoiNhan_Khoa UNIQUE(maNguoiNhan, khoaChongTrung)
);

CREATE INDEX IX_ThongBao_NguoiNhan_DaDoc_Tao
    ON ThongBao(maNguoiNhan, thoiGianDaDoc, thoiGianTao DESC);


/* =========================
   PHIÊN LÀM VIỆC
========================= */

CREATE TABLE PhienLamViec(

    maPhien INT IDENTITY(1,1) PRIMARY KEY,

    maNhanVien INT NOT NULL,

    batDau DATETIME2 NOT NULL,

    ketThuc DATETIME2,

    soSanPhamBan INT NOT NULL DEFAULT 0,

    soKhachHangMoi INT NOT NULL DEFAULT 0,

    doanhThu DECIMAL(18,2) NOT NULL DEFAULT 0,

    nhanVienDaXem BIT NOT NULL DEFAULT 0,

    adminDaXem BIT NOT NULL DEFAULT 0,

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien)

);


/* =========================
   CHI TIẾT GIỎ HÀNG
========================= */

CREATE TABLE ChiTietGioHang(

    maCTGioHang INT IDENTITY(1,1) PRIMARY KEY,

    maKH INT,

    maChiTietSP INT,

    soLuong INT,

    donGia DECIMAL(18,2),

    thanhTien AS (soLuong * donGia),

    ngayTao DATE DEFAULT GETDATE(),

    trangThai NVARCHAR(30),

    FOREIGN KEY(maKH)
        REFERENCES KhachHang(maKH),

    FOREIGN KEY(maChiTietSP)
        REFERENCES ChiTietSanPham(maChiTietSP)

);


/* =========================
   ĐƠN HÀNG
========================= */

CREATE TABLE DonHang(

    maDonHang INT IDENTITY(1,1) PRIMARY KEY,

    maKH INT,

    maNhanVien INT,

    ngayDatHang DATE DEFAULT GETDATE(),

    tongTien DECIMAL(18,2),

    trangThai NVARCHAR(50),

    FOREIGN KEY(maKH)
        REFERENCES KhachHang(maKH),

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien)

);


/* =========================
   HÓA ĐƠN
========================= */

CREATE TABLE HoaDon(

    maHoaDon INT IDENTITY(1,1) PRIMARY KEY,

    maDonHang INT,

    maNhanVien INT,

    maPhien INT,

    ngayLap DATE DEFAULT GETDATE(),

    tongTien DECIMAL(18,2),

    trangThai NVARCHAR(50),

    tenKhachHangSnapshot NVARCHAR(100),

    soDienThoaiKhachHangSnapshot VARCHAR(15),

    tenNhanVienSnapshot NVARCHAR(100),

    FOREIGN KEY(maDonHang)
        REFERENCES DonHang(maDonHang),

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien),

    FOREIGN KEY(maPhien)
        REFERENCES PhienLamViec(maPhien)

);


/* =========================
   LỊCH SỬ CHỈNH SỬA HÓA ĐƠN
========================= */

CREATE TABLE LichSuChinhSuaHoaDon(

    maLichSu INT IDENTITY(1,1) PRIMARY KEY,

    maHoaDon INT NOT NULL,

    maPhien INT,

    nguoiChinhSua NVARCHAR(100) NOT NULL,

    thoiGian DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

    duLieuTruoc NVARCHAR(MAX) NOT NULL,

    duLieuSau NVARCHAR(MAX) NOT NULL,

    FOREIGN KEY(maHoaDon)
        REFERENCES HoaDon(maHoaDon),

    FOREIGN KEY(maPhien)
        REFERENCES PhienLamViec(maPhien)

);


/* =========================
   KHUYẾN MẠI
========================= */

CREATE TABLE KhuyenMai(
    maKhuyenMai INT IDENTITY(1,1) PRIMARY KEY,
    tenKhuyenMai NVARCHAR(120) NOT NULL,
    loaiGiam VARCHAR(20) NOT NULL,
    giaTri DECIMAL(18,2) NOT NULL,
    batDau DATETIME2 NOT NULL,
    ketThuc DATETIME2 NOT NULL,
    trangThai BIT NOT NULL DEFAULT 1,
    CONSTRAINT CK_KhuyenMai_Loai CHECK (loaiGiam IN ('PHAN_TRAM', 'SO_TIEN')),
    CONSTRAINT CK_KhuyenMai_GiaTri CHECK (
        giaTri > 0
        AND giaTri = FLOOR(giaTri)
        AND (loaiGiam <> 'PHAN_TRAM' OR giaTri <= 100)
    ),
    CONSTRAINT CK_KhuyenMai_ThoiGian CHECK (batDau < ketThuc)
);

CREATE TABLE KhuyenMaiSanPham(
    maKhuyenMai INT NOT NULL,
    maSP INT NOT NULL,
    PRIMARY KEY(maKhuyenMai, maSP),
    FOREIGN KEY(maKhuyenMai) REFERENCES KhuyenMai(maKhuyenMai) ON DELETE CASCADE,
    FOREIGN KEY(maSP) REFERENCES SanPham(maSP)
);

/* =========================
   CHI TIẾT HÓA ĐƠN
========================= */

CREATE TABLE ChiTietHoaDon(

    maCTHD INT IDENTITY(1,1) PRIMARY KEY,

    maHoaDon INT,

    maChiTietSP INT,

    soLuong INT,

    donGia DECIMAL(18,2),

    giaGoc DECIMAL(18,2),

    maKhuyenMai INT,

    tenSanPhamSnapshot NVARCHAR(100),

    maSanPhamSnapshot VARCHAR(30),

    moTaBienTheSnapshot NVARCHAR(200),

    tenKhuyenMaiSnapshot NVARCHAR(120),

    thanhTien AS (soLuong * donGia),

    FOREIGN KEY(maHoaDon)
        REFERENCES HoaDon(maHoaDon),

    FOREIGN KEY(maChiTietSP)
        REFERENCES ChiTietSanPham(maChiTietSP),

    FOREIGN KEY(maKhuyenMai)
        REFERENCES KhuyenMai(maKhuyenMai)

);


/* =========================
   THANH TOÁN
========================= */

CREATE TABLE ThanhToan(

    maThanhToan INT IDENTITY(1,1) PRIMARY KEY,

    maHoaDon INT,

    phuongThuc NVARCHAR(50),
	
	ngayThanhToan DATE DEFAULT GETDATE(),

    soTien DECIMAL(18,2),

    trangThai NVARCHAR(30),

    FOREIGN KEY(maHoaDon)
        REFERENCES HoaDon(maHoaDon)

);

INSERT INTO Loai(tenLoai, tonKho)
VALUES
(N'Giày Sneaker',50),
(N'Giày Thể Thao',40),
(N'Dép',20),
(N'Giày Boot',15);

/* =========================
   MÀU SẮC
========================= */

INSERT INTO Mau(tenMau, tonKho)
VALUES
(N'Trắng',30),
(N'Đen',40),
(N'Đỏ',20),
(N'Xanh',15);

/* =========================
   CHẤT LIỆU
========================= */

INSERT INTO ChatLieu(tenChatLieu, tonKho)
VALUES
(N'Da',30),
(N'Canvas',40),
(N'Vải',25),
(N'Cao su',50);

/* =========================
   SIZE
========================= */

INSERT INTO Size(tenSize, tonKho)
VALUES
('38',15),
('39',20),
('40',30),
('41',25),
('42',20);

/* =========================
   NHÀ CUNG CẤP
========================= */

INSERT INTO NhaCungCap
(tenNCC,soDienThoai,email,diaChi,trangThai)
VALUES
(N'Nike Việt Nam','0901111111','nike@gmail.com',N'Hà Nội',N'Hoạt động'),
(N'Adidas Việt Nam','0902222222','adidas@gmail.com',N'TP. Hồ Chí Minh',N'Hoạt động'),
(N'Converse Việt Nam','0903333333','converse@gmail.com',N'Đà Nẵng',N'Hoạt động');

/* =========================
   SẢN PHẨM
========================= */

INSERT INTO SanPham
(tenSP,maLoai,maMau,maChatLieu,maSize,gia,tonKho)
VALUES
(N'Nike Air Force 1',1,1,1,3,2500000,20),
(N'Adidas Superstar',1,2,2,4,2200000,15),
(N'Converse Chuck Taylor',2,2,2,3,1800000,10),
(N'Nike Jordan Low',1,3,1,5,3200000,12),
(N'Vans Old Skool',2,2,2,2,1900000,18);

/* =========================
   CHI TIẾT SẢN PHẨM
========================= */

INSERT INTO ChiTietSanPham
(maSP,maNCC,moTa,hinhAnh,xuatXu,thuongHieu,trangThai)
VALUES
(1,1,N'Giày Nike Air Force 1 chính hãng',N'/images/products/nike.svg',N'Việt Nam',N'Nike',N'Còn hàng'),
(2,2,N'Adidas Superstar',N'/images/products/adidas.svg',N'Việt Nam',N'Adidas',N'Còn hàng'),
(3,3,N'Converse Chuck Taylor',N'/images/products/converse.svg',N'Việt Nam',N'Converse',N'Còn hàng'),
(4,1,N'Nike Jordan Low',N'/images/products/jordan.svg',N'Indonesia',N'Nike',N'Còn hàng'),
(5,3,N'Vans Old Skool',N'/images/products/vans.svg',N'Trung Quốc',N'Vans',N'Còn hàng');

/* =========================
   CHỨC VỤ
========================= */

INSERT INTO ChucVu(tenChucVu)
VALUES
(N'Quản lý'),
(N'Nhân viên');

/* =========================
   NHÂN VIÊN
========================= */

INSERT INTO NhanVien
(tenNhanVien,gioiTinh,soDienThoai,namSinh,ngaySinh,queQuan,maChucVu,trangThai)
VALUES
(N'Nguyễn Thành Đạt',1,'0911111111',1998,'1998-04-12',N'Hà Nội',1,N'Đang làm'),
(N'Trần Ngọc Mai',0,'0922222222',2000,'2000-09-21',N'TP. Hồ Chí Minh',2,N'Đang làm'),
(N'Lê Minh Quân',1,'0933333333',1999,'1999-02-17',N'Đà Nẵng',2,N'Đang làm');

/* =========================
   PHIÊN LÀM VIỆC MẪU
========================= */

INSERT INTO PhienLamViec
(maNhanVien,batDau,ketThuc,soSanPhamBan,soKhachHangMoi,doanhThu,nhanVienDaXem,adminDaXem)
VALUES
(1,'2026-07-01T08:00:00','2026-07-01T17:30:00',1,1,2500000,1,1);

/* =========================
   KHÁCH HÀNG
========================= */

INSERT INTO KhachHang
(tenKH,gioiTinh,namSinh,ngaySinh,soDienThoai,diaChi)
VALUES
(N'Phạm Kiên Trung',1,2004,'2004-06-15','0988888888',N'Hà Nội'),
(N'Nguyễn Thùy Dương',0,2002,'2002-11-03','0977777777',N'TP. Hồ Chí Minh'),
(N'Hoàng Minh Đức',1,1999,'1999-01-25','0966666666',N'Đà Nẵng');

/* =========================
   TÀI KHOẢN
========================= */

INSERT INTO TaiKhoan
(tenDangNhap,matKhau,vaiTro,maNhanVien,trangThai)
VALUES
('admin','$2a$10$cTQezFkcDVqQSolbd2ROWOog6tBV0E92.H86p.Hj4mupL2FZiJGN.',N'Admin',1,N'Hoạt động'),
('nhanvien1','$2a$10$cTQezFkcDVqQSolbd2ROWOog6tBV0E92.H86p.Hj4mupL2FZiJGN.',N'Nhân viên',2,N'Hoạt động'),
('nhanvien2','$2a$10$cTQezFkcDVqQSolbd2ROWOog6tBV0E92.H86p.Hj4mupL2FZiJGN.',N'Nhân viên',3,N'Hoạt động');

/* =========================
   KHUYẾN MẠI MẪU
========================= */

INSERT INTO KhuyenMai
(tenKhuyenMai,loaiGiam,giaTri,batDau,ketThuc,trangThai)
VALUES
(N'Ưu đãi thành viên mới','PHAN_TRAM',10,DATEADD(DAY,-7,SYSDATETIME()),DATEADD(DAY,23,SYSDATETIME()),1),
(N'Giảm 200K giày Nike','SO_TIEN',200000,DATEADD(DAY,1,SYSDATETIME()),DATEADD(DAY,31,SYSDATETIME()),1),
(N'Back to School','PHAN_TRAM',15,DATEADD(DAY,-60,SYSDATETIME()),DATEADD(DAY,-30,SYSDATETIME()),0);

INSERT INTO KhuyenMaiSanPham(maKhuyenMai,maSP)
VALUES
(1,1),
(1,2),
(2,4),
(3,3),
(3,5);

/* =========================
   CHI TIẾT GIỎ HÀNG
========================= */

INSERT INTO ChiTietGioHang
(maKH,maChiTietSP,soLuong,donGia,trangThai)
VALUES
(1,1,1,2500000,N'Đang chọn'),
(2,2,2,2200000,N'Đang chọn'),
(3,3,1,1800000,N'Đang chọn');

/* =========================
   ĐƠN HÀNG
========================= */

INSERT INTO DonHang
(maKH,maNhanVien,ngayDatHang,tongTien,trangThai)
VALUES
(1,1,'2026-07-01',2500000,N'Đã xác nhận'),
(2,2,'2026-07-02',4400000,N'Đang giao'),
(3,3,'2026-07-03',1800000,N'Đã thanh toán');

/* =========================
   HÓA ĐƠN
========================= */

INSERT INTO HoaDon
(maDonHang,maNhanVien,ngayLap,tongTien,trangThai,
 tenKhachHangSnapshot,soDienThoaiKhachHangSnapshot,tenNhanVienSnapshot,maPhien)
VALUES
(1,1,'2026-07-01',2500000,N'Đã thanh toán',N'Phạm Kiên Trung','0988888888',N'Nguyễn Thành Đạt',1),
(2,2,'2026-07-02',4400000,N'Đã thanh toán',N'Nguyễn Thùy Dương','0977777777',N'Trần Ngọc Mai',NULL),
(3,3,'2026-07-03',1800000,N'Đã thanh toán',N'Hoàng Minh Đức','0966666666',N'Lê Minh Quân',NULL);

INSERT INTO LichSuChinhSuaHoaDon
(maHoaDon,maPhien,nguoiChinhSua,thoiGian,duLieuTruoc,duLieuSau)
VALUES
(1,1,N'Nguyễn Thành Đạt','2026-07-01T10:30:00',
 N'{"trangThai":"Chưa thanh toán","tongTien":2500000}',
 N'{"trangThai":"Đã thanh toán","tongTien":2500000}');

/* =========================
   CHI TIẾT HÓA ĐƠN
========================= */

INSERT INTO ChiTietHoaDon
(maHoaDon,maChiTietSP,soLuong,donGia,giaGoc,
 tenSanPhamSnapshot,maSanPhamSnapshot,moTaBienTheSnapshot)
VALUES
(1,1,1,2500000,2500000,N'Nike Air Force 1','SP-1',N'Giày Sneaker / Trắng / Size 40'),
(2,2,2,2200000,2200000,N'Adidas Superstar','SP-2',N'Giày Sneaker / Đen / Size 41'),
(3,3,1,1800000,1800000,N'Converse Chuck Taylor','SP-3',N'Giày Thể Thao / Đen / Size 40');

/* =========================
   THANH TOÁN
========================= */

INSERT INTO ThanhToan
(maHoaDon,phuongThuc,ngayThanhToan,soTien,trangThai)
VALUES
(1,N'Tiền mặt','2026-07-01',2500000,N'Thành công'),
(2,N'Chuyển khoản','2026-07-02',4400000,N'Thành công'),
(3,N'Ví điện tử','2026-07-03',1800000,N'Thành công');
