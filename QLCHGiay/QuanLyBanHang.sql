CREATE DATABASE QuanLyBanHang;
GO

USE QuanLyBanHang;
GO

/* =========================
   LO?I S?N PH?M
========================= */

CREATE TABLE Loai(

    maLoai INT IDENTITY(1,1) PRIMARY KEY,

    tenLoai NVARCHAR(50) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   MÀU S?C
========================= */

CREATE TABLE Mau(

    maMau INT IDENTITY(1,1) PRIMARY KEY,

    tenMau NVARCHAR(50) NOT NULL,

    tonKho INT DEFAULT 0

);


/* =========================
   CH?T LI?U
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
   NHÀ CUNG C?P
========================= */

CREATE TABLE NhaCungCap(

    maNCC INT IDENTITY(1,1) PRIMARY KEY,

    tenNCC NVARCHAR(100) NOT NULL,

    soDienThoai VARCHAR(15),

    email VARCHAR(100),

    diaChi NVARCHAR(200),

    trangThai NVARCHAR(30)

);


/* =========================
   S?N PH?M
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
   CHI TI?T S?N PH?M
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
   CH?C V?
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

    soDienThoai VARCHAR(15),
diaChi NVARCHAR(200)

);


/* =========================
   TÀI KHO?N
========================= */

CREATE TABLE TaiKhoan(

    maTaiKhoan INT IDENTITY(1,1) PRIMARY KEY,

    tenDangNhap VARCHAR(50) UNIQUE NOT NULL,

    matKhau VARCHAR(255) NOT NULL,

    vaiTro NVARCHAR(30),

    maNhanVien INT,

    trangThai NVARCHAR(30),

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien)

);


/* =========================
   CHI TI?T GI? HÀNG
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
   ??N HÀNG
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
   HÓA ??N
========================= */

CREATE TABLE HoaDon(

    maHoaDon INT IDENTITY(1,1) PRIMARY KEY,

    maDonHang INT,

    maNhanVien INT,

    ngayLap DATE DEFAULT GETDATE(),

    tongTien DECIMAL(18,2),

    trangThai NVARCHAR(50),

    FOREIGN KEY(maDonHang)
        REFERENCES DonHang(maDonHang),

    FOREIGN KEY(maNhanVien)
        REFERENCES NhanVien(maNhanVien)

);


/* =========================
   CHI TI?T HÓA ??N
========================= */

CREATE TABLE ChiTietHoaDon(

    maCTHD INT IDENTITY(1,1) PRIMARY KEY,

    maHoaDon INT,

    maChiTietSP INT,

    soLuong INT,

    donGia DECIMAL(18,2),

    thanhTien AS (soLuong * donGia),

    FOREIGN KEY(maHoaDon)
        REFERENCES HoaDon(maHoaDon),

    FOREIGN KEY(maChiTietSP)
        REFERENCES ChiTietSanPham(maChiTietSP)

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
(N'Giày Th? Thao',40),
(N'Dép',20),
(N'Giày Boot',15);

/* =========================
   MÀU S?C
========================= */

INSERT INTO Mau(tenMau, tonKho)
VALUES
(N'Tr?ng',30),
(N'?en',40),
(N'??',20),
(N'Xanh',15);

/* =========================
   CH?T LI?U
========================= */

INSERT INTO ChatLieu(tenChatLieu, tonKho)
VALUES
(N'Da',30),
(N'Canvas',40),
(N'V?i',25),
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
   NHÀ CUNG C?P
========================= */

INSERT INTO NhaCungCap
(tenNCC,soDienThoai,email,diaChi,trangThai)
VALUES
(N'Nike Vi?t Nam','0901111111','nike@gmail.com',N'Hà N?i',N'Ho?t ??ng'),
(N'Adidas Vi?t Nam','0902222222','adidas@gmail.com',N'HCM',N'Ho?t ??ng'),
(N'Converse Vi?t Nam','0903333333','converse@gmail.com',N'?à N?ng',N'Ho?t ??ng');

/* =========================
   S?N PH?M
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
   CHI TI?T S?N PH?M
========================= */

INSERT INTO ChiTietSanPham
(maSP,maNCC,moTa,hinhAnh,xuatXu,thuongHieu,trangThai)
VALUES
(1,1,N'Giày Nike Air Force 1 chính hãng','af1.jpg',N'Vi?t Nam','Nike',N'Còn hàng'),
(2,2,N'Adidas Superstar','superstar.jpg',N'Vi?t Nam','Adidas',N'Còn hàng'),
(3,3,N'Converse Chuck Taylor','converse.jpg',N'Vi?t Nam','Converse',N'Còn hàng'),
(4,1,N'Nike Jordan Low','jordan.jpg',N'Indonesia','Nike',N'Còn hàng'),
(5,3,N'Vans Old Skool','vans.jpg',N'Trung Qu?c','Vans',N'Còn hàng');

/* =========================
   CH?C V?
========================= */

INSERT INTO ChucVu(tenChucVu)
VALUES
(N'Qu?n lý'),
(N'Nhân viên');

/* =========================
   NHÂN VIÊN
========================= */

INSERT INTO NhanVien
(tenNhanVien,gioiTinh,soDienThoai,namSinh,queQuan,maChucVu,trangThai)
VALUES
(N'Nguy?n V?n A',1,'0911111111',1998,N'Hà N?i',1,N'?ang làm'),
(N'Tr?n Th? B',0,'0922222222',2000,N'HCM',2,N'?ang làm'),
(N'Lê V?n C',1,'0933333333',1999,N'?à N?ng',2,N'?ang làm');

/* =========================
   KHÁCH HÀNG
========================= */

INSERT INTO KhachHang
(tenKH,gioiTinh,namSinh,soDienThoai,diaChi)
VALUES
(N'Ph?m Kiên Trung',1,2004,'0988888888',N'Hà N?i'),
(N'Nguy?n Th? D',0,2002,'0977777777',N'HCM'),
(N'Hoàng V?n E',1,1999,'0966666666',N'?à N?ng');

/* =========================
   TÀI KHO?N
========================= */

INSERT INTO TaiKhoan
(tenDangNhap,matKhau,vaiTro,maNhanVien,trangThai)
VALUES
('admin','123456',N'Admin',1,N'Ho?t ??ng'),
('nhanvien1','123456',N'Nhân viên',2,N'Ho?t ??ng'),
('nhanvien2','123456',N'Nhân viên',3,N'Ho?t ??ng');

/* =========================
   CHI TI?T GI? HÀNG
========================= */

INSERT INTO ChiTietGioHang
(maKH,maChiTietSP,soLuong,donGia,trangThai)
VALUES
(1,1,1,2500000,N'?ang ch?n'),
(2,2,2,2200000,N'?ang ch?n'),
(3,3,1,1800000,N'?ang ch?n');

/* =========================
   ??N HÀNG
========================= */

INSERT INTO DonHang
(maKH,maNhanVien,ngayDatHang,tongTien,trangThai)
VALUES
(1,1,'2026-07-01',2500000,N'?ã xác nh?n'),
(2,2,'2026-07-02',4400000,N'?ang giao'),
(3,3,'2026-07-03',1800000,N'Hoàn thành');

/* =========================
   HÓA ??N
========================= */

INSERT INTO HoaDon
(maDonHang,maNhanVien,ngayLap,tongTien,trangThai)
VALUES
(1,1,'2026-07-01',2500000,N'?ã thanh toán'),
(2,2,'2026-07-02',4400000,N'?ã thanh toán'),
(3,3,'2026-07-03',1800000,N'?ã thanh toán');

/* =========================
   CHI TI?T HÓA ??N
========================= */

INSERT INTO ChiTietHoaDon
(maHoaDon,maChiTietSP,soLuong,donGia)
VALUES
(1,1,1,2500000),
(2,2,2,2200000),
(3,3,1,1800000);

/* =========================
   THANH TOÁN
========================= */

INSERT INTO ThanhToan
(maHoaDon,phuongThuc,ngayThanhToan,soTien,trangThai)
VALUES
(1,N'Ti?n m?t','2026-07-01',2500000,N'Thành công'),
(2,N'Chuy?n kho?n','2026-07-02',4400000,N'Thành công'),
(3,N'Ví ?i?n t?','2026-07-03',1800000,N'Thành công');