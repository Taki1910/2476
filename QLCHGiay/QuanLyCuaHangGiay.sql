IF DB_ID(N'QuanLyCuaHangGiay') IS NULL
    CREATE DATABASE QuanLyCuaHangGiay;
GO
USE QuanLyCuaHangGiay;
GO

CREATE TABLE ChucVu (
    maChucVu INT IDENTITY PRIMARY KEY,
    tenChucVu NVARCHAR(50) NOT NULL
);
CREATE TABLE NhanVien (
    maNhanVien INT IDENTITY PRIMARY KEY,
    tenNhanVien NVARCHAR(100) NOT NULL,
    gioiTinh BIT,
    soDienThoai VARCHAR(15),
    namSinh INT,
    queQuan NVARCHAR(100),
    maChucVu INT,
    trangThai NVARCHAR(30),
    CONSTRAINT FK_NhanVien_ChucVu FOREIGN KEY (maChucVu) REFERENCES ChucVu(maChucVu)
);
CREATE TABLE TaiKhoan (
    maTaiKhoan INT IDENTITY PRIMARY KEY,
    tenDangNhap VARCHAR(50) NOT NULL UNIQUE,
    matKhau VARCHAR(255) NOT NULL,
    vaiTro NVARCHAR(30),
    maNhanVien INT,
    trangThai NVARCHAR(30),
    CONSTRAINT FK_TaiKhoan_NhanVien FOREIGN KEY (maNhanVien) REFERENCES NhanVien(maNhanVien)
);
CREATE TABLE Loai (
    maLoai INT IDENTITY PRIMARY KEY,
    tenLoai NVARCHAR(50) NOT NULL,
    tonKho INT DEFAULT 0
);
CREATE TABLE Mau (
    maMau INT IDENTITY PRIMARY KEY,
    tenMau NVARCHAR(50) NOT NULL,
    tonKho INT DEFAULT 0
);
CREATE TABLE ChatLieu (
    maChatLieu INT IDENTITY PRIMARY KEY,
    tenChatLieu NVARCHAR(50) NOT NULL,
    tonKho INT DEFAULT 0
);
CREATE TABLE [Size] (
    maSize INT IDENTITY PRIMARY KEY,
    tenSize NVARCHAR(20) NOT NULL,
    tonKho INT DEFAULT 0
);
CREATE TABLE NhaCungCap (
    maNCC INT IDENTITY PRIMARY KEY,
    tenNCC NVARCHAR(100) NOT NULL,
    soDienThoai VARCHAR(15),
    email VARCHAR(100),
    diaChi NVARCHAR(200),
    trangThai NVARCHAR(30)
);
CREATE TABLE SanPham (
    maSP INT IDENTITY PRIMARY KEY,
    tenSP NVARCHAR(100) NOT NULL,
    maLoai INT,
    maMau INT,
    maChatLieu INT,
    maSize INT,
    gia DECIMAL(18,2),
    tonKho INT DEFAULT 0,
    CONSTRAINT FK_SanPham_Loai FOREIGN KEY (maLoai) REFERENCES Loai(maLoai),
    CONSTRAINT FK_SanPham_Mau FOREIGN KEY (maMau) REFERENCES Mau(maMau),
    CONSTRAINT FK_SanPham_ChatLieu FOREIGN KEY (maChatLieu) REFERENCES ChatLieu(maChatLieu),
    CONSTRAINT FK_SanPham_Size FOREIGN KEY (maSize) REFERENCES [Size](maSize)
);
CREATE TABLE ChiTietSanPham (
    maChiTietSP INT IDENTITY PRIMARY KEY,
    maSP INT,
    maNCC INT,
    moTa NVARCHAR(500),
    hinhAnh NVARCHAR(255),
    xuatXu NVARCHAR(100),
    thuongHieu NVARCHAR(100),
    trangThai NVARCHAR(50),
    CONSTRAINT FK_CTSP_SanPham FOREIGN KEY (maSP) REFERENCES SanPham(maSP),
    CONSTRAINT FK_CTSP_NCC FOREIGN KEY (maNCC) REFERENCES NhaCungCap(maNCC)
);
CREATE TABLE KhachHang (
    maKH INT IDENTITY PRIMARY KEY,
    tenKH NVARCHAR(100) NOT NULL,
    gioiTinh BIT,
    namSinh INT,
    soDienThoai VARCHAR(15),
    diaChi NVARCHAR(200)
);
CREATE TABLE DonHang (
    maDonHang INT IDENTITY PRIMARY KEY,
    maKH INT,
    maNhanVien INT,
    ngayDatHang DATE DEFAULT GETDATE(),
    tongTien DECIMAL(18,2),
    trangThai NVARCHAR(50),
    CONSTRAINT FK_DonHang_KH FOREIGN KEY (maKH) REFERENCES KhachHang(maKH),
    CONSTRAINT FK_DonHang_NV FOREIGN KEY (maNhanVien) REFERENCES NhanVien(maNhanVien)
);
CREATE TABLE HoaDon (
    maHoaDon INT IDENTITY PRIMARY KEY,
    maDonHang INT,
    maNhanVien INT,
    ngayLap DATE DEFAULT GETDATE(),
    tongTien DECIMAL(18,2),
    trangThai NVARCHAR(50),
    CONSTRAINT FK_HoaDon_DonHang FOREIGN KEY (maDonHang) REFERENCES DonHang(maDonHang),
    CONSTRAINT FK_HoaDon_NV FOREIGN KEY (maNhanVien) REFERENCES NhanVien(maNhanVien)
);
CREATE TABLE ChiTietHoaDon (
    maCTHD INT IDENTITY PRIMARY KEY,
    maHoaDon INT,
    maChiTietSP INT,
    soLuong INT,
    donGia DECIMAL(18,2),
    thanhTien AS (soLuong * donGia),
    CONSTRAINT FK_CTHD_HD FOREIGN KEY (maHoaDon) REFERENCES HoaDon(maHoaDon),
    CONSTRAINT FK_CTHD_CTSP FOREIGN KEY (maChiTietSP) REFERENCES ChiTietSanPham(maChiTietSP)
);
CREATE TABLE ChiTietGioHang (
    maCTGioHang INT IDENTITY PRIMARY KEY,
    maKH INT,
    maChiTietSP INT,
    soLuong INT,
    donGia DECIMAL(18,2),
    thanhTien AS (soLuong * donGia),
    ngayTao DATE DEFAULT GETDATE(),
    trangThai NVARCHAR(30),
    CONSTRAINT FK_CTGH_KH FOREIGN KEY (maKH) REFERENCES KhachHang(maKH),
    CONSTRAINT FK_CTGH_CTSP FOREIGN KEY (maChiTietSP) REFERENCES ChiTietSanPham(maChiTietSP)
);
CREATE TABLE ThanhToan (
    maThanhToan INT IDENTITY PRIMARY KEY,
    maHoaDon INT,
    phuongThuc NVARCHAR(50),
    ngayThanhToan DATE DEFAULT GETDATE(),
    soTien DECIMAL(18,2),
    trangThai NVARCHAR(30),
    CONSTRAINT FK_ThanhToan_HD FOREIGN KEY (maHoaDon) REFERENCES HoaDon(maHoaDon)
);
GO

INSERT INTO ChucVu(tenChucVu) VALUES (N'Quản lý'), (N'Nhân viên');
INSERT INTO NhanVien(tenNhanVien, gioiTinh, soDienThoai, namSinh, queQuan, maChucVu, trangThai)
VALUES (N'Quản trị viên', 1, '0123456789', 2000, N'Việt Nam', 1, N'Hoạt động');
INSERT INTO TaiKhoan(tenDangNhap, matKhau, vaiTro, maNhanVien, trangThai)
VALUES ('admin', '123', N'Quản lý', 1, N'Hoạt động');
INSERT INTO Loai(tenLoai) VALUES (N'Giày chạy bộ'), (N'Giày bóng rổ'), (N'Giày thời trang');
INSERT INTO Mau(tenMau) VALUES (N'Đen'), (N'Trắng'), (N'Đỏ');
INSERT INTO ChatLieu(tenChatLieu) VALUES (N'Da'), (N'Vải'), (N'Lưới');
INSERT INTO [Size](tenSize) VALUES (N'38'), (N'39'), (N'40'), (N'41'), (N'42');
GO
