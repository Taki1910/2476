/* DEVELOPMENT DEMO DATA ONLY — never execute as an application startup script. */
USE QuanLyBanHang;
GO

/*
    Dữ liệu mẫu cho Dashboard/Báo cáo.
    - Có thể chạy lại an toàn, không tạo trùng theo số điện thoại và tháng bán.
    - Ngày bán được tính theo ngày chạy để biểu đồ 6 tháng gần nhất luôn có dữ liệu.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

BEGIN TRY
    DECLARE @LoaiId INT = (SELECT TOP 1 maLoai FROM Loai ORDER BY maLoai);
    DECLARE @MauId INT = (SELECT TOP 1 maMau FROM Mau ORDER BY maMau);
    DECLARE @ChatLieuId INT = (SELECT TOP 1 maChatLieu FROM ChatLieu ORDER BY maChatLieu);
    DECLARE @SizeId INT = (SELECT TOP 1 maSize FROM Size ORDER BY maSize);
    DECLARE @NhaCungCapId INT = (
        SELECT TOP 1 maNCC
        FROM NhaCungCap
        WHERE trangThai = N'Hoạt động'
        ORDER BY maNCC
    );
    DECLARE @NhanVienId INT = (SELECT TOP 1 maNhanVien FROM NhanVien ORDER BY maNhanVien);
    DECLARE @NhanVienName NVARCHAR(100) =
        (SELECT tenNhanVien FROM NhanVien WHERE maNhanVien = @NhanVienId);

    IF @NhaCungCapId IS NULL
        THROW 51000, N'Không có nhà cung cấp đang hoạt động để tạo dữ liệu Dashboard.', 1;

    IF NOT EXISTS (SELECT 1 FROM SanPham WHERE tenSP = N'New Balance 550 Demo')
    BEGIN
        INSERT INTO SanPham
            (tenSP, maLoai, maMau, maChatLieu, maSize, gia, tonKho)
        VALUES
            (N'New Balance 550 Demo', @LoaiId, @MauId, @ChatLieuId, @SizeId, 2450000, 3);
    END;

    IF NOT EXISTS (SELECT 1 FROM SanPham WHERE tenSP = N'Puma Suede Classic Demo')
    BEGIN
        INSERT INTO SanPham
            (tenSP, maLoai, maMau, maChatLieu, maSize, gia, tonKho)
        VALUES
            (N'Puma Suede Classic Demo', @LoaiId, @MauId, @ChatLieuId, @SizeId, 1950000, 5);
    END;

    INSERT INTO ChiTietSanPham
        (maSP, maNCC, moTa, hinhAnh, xuatXu, thuongHieu, trangThai)
    SELECT
        sp.maSP,
        @NhaCungCapId,
        N'Sản phẩm mẫu dùng cho cảnh báo tồn kho và biểu đồ',
        NULL,
        N'Việt Nam',
        CASE
            WHEN sp.tenSP LIKE N'New Balance%' THEN N'New Balance'
            ELSE N'Puma'
        END,
        N'Còn hàng'
    FROM SanPham sp
    WHERE sp.tenSP IN (N'New Balance 550 Demo', N'Puma Suede Classic Demo')
      AND NOT EXISTS (
        SELECT 1
        FROM ChiTietSanPham ct
        WHERE ct.maSP = sp.maSP
      );

    UPDATE detail
    SET
        detail.moTa = N'Sản phẩm mẫu dùng cho cảnh báo tồn kho và biểu đồ',
        detail.hinhAnh = CASE
            WHEN product.tenSP LIKE N'New Balance%' THEN N'/images/products/new-balance.svg'
            ELSE N'/images/products/puma.svg'
        END,
        detail.xuatXu = N'Việt Nam',
        detail.thuongHieu = CASE
            WHEN product.tenSP LIKE N'New Balance%' THEN N'New Balance'
            ELSE N'Puma'
        END,
        detail.trangThai = N'Còn hàng'
    FROM ChiTietSanPham detail
    INNER JOIN SanPham product ON product.maSP = detail.maSP
    WHERE product.tenSP IN (N'New Balance 550 Demo', N'Puma Suede Classic Demo');

    IF NOT EXISTS (SELECT 1 FROM KhuyenMai WHERE tenKhuyenMai = N'Demo - Thành viên mới')
        INSERT INTO KhuyenMai
            (tenKhuyenMai, loaiGiam, giaTri, batDau, ketThuc, trangThai)
        VALUES
            (N'Demo - Thành viên mới', 'PHAN_TRAM', 10,
             DATEADD(DAY, -7, SYSDATETIME()), DATEADD(DAY, 23, SYSDATETIME()), 1);

    IF NOT EXISTS (SELECT 1 FROM KhuyenMai WHERE tenKhuyenMai = N'Demo - Giảm 200K')
        INSERT INTO KhuyenMai
            (tenKhuyenMai, loaiGiam, giaTri, batDau, ketThuc, trangThai)
        VALUES
            (N'Demo - Giảm 200K', 'SO_TIEN', 200000,
             DATEADD(DAY, 1, SYSDATETIME()), DATEADD(DAY, 31, SYSDATETIME()), 1);

    UPDATE KhuyenMai
    SET batDau = DATEADD(DAY, -7, SYSDATETIME()),
        ketThuc = DATEADD(DAY, 23, SYSDATETIME()),
        trangThai = 1
    WHERE tenKhuyenMai = N'Demo - Thành viên mới';

    UPDATE KhuyenMai
    SET batDau = DATEADD(DAY, 1, SYSDATETIME()),
        ketThuc = DATEADD(DAY, 31, SYSDATETIME()),
        trangThai = 1
    WHERE tenKhuyenMai = N'Demo - Giảm 200K';

    INSERT INTO KhuyenMaiSanPham(maKhuyenMai, maSP)
    SELECT promotion.maKhuyenMai, product.maSP
    FROM KhuyenMai promotion
    INNER JOIN SanPham product ON
        (promotion.tenKhuyenMai = N'Demo - Thành viên mới'
         AND product.tenSP = N'New Balance 550 Demo')
        OR
        (promotion.tenKhuyenMai = N'Demo - Giảm 200K'
         AND product.tenSP = N'Puma Suede Classic Demo')
    WHERE NOT EXISTS (
        SELECT 1
        FROM KhuyenMaiSanPham linked
        WHERE linked.maKhuyenMai = promotion.maKhuyenMai
          AND linked.maSP = product.maSP
    );

    DECLARE @DemoSales TABLE (
        monthOffset INT PRIMARY KEY,
        customerName NVARCHAR(100) NOT NULL,
        phone VARCHAR(15) NOT NULL,
        address NVARCHAR(200) NOT NULL,
        quantity INT NOT NULL,
        productRank INT NOT NULL,
        paymentMethod NVARCHAR(50) NOT NULL
    );

    INSERT INTO @DemoSales
        (monthOffset, customerName, phone, address, quantity, productRank, paymentMethod)
    VALUES
        (11, N'Nguyễn Hoàng Nam', '0950000011', N'Hà Nội',            1, 1, N'Tiền mặt'),
        (10, N'Lê Minh Anh',      '0950000010', N'Hải Phòng',         2, 2, N'Chuyển khoản'),
        (9,  N'Trần Gia Hân',     '0950000009', N'Đà Nẵng',           2, 3, N'Ví điện tử'),
        (8,  N'Phạm Quang Huy',   '0950000008', N'Cần Thơ',           3, 4, N'Chuyển khoản'),
        (7,  N'Võ Ngọc Mai',      '0950000007', N'Nghệ An',           1, 5, N'Tiền mặt'),
        (6,  N'Đặng Quốc Bảo',    '0950000006', N'TP. Hồ Chí Minh',   3, 6, N'Ví điện tử'),
        (5,  N'Bùi Khánh Linh',   '0950000005', N'Bình Dương',        2, 7, N'Chuyển khoản'),
        (4,  N'Nguyễn Tuấn Kiệt', '0950000004', N'Bắc Ninh',          4, 1, N'Tiền mặt'),
        (3,  N'Hoàng Thùy Dương', '0950000003', N'Quảng Ninh',        2, 2, N'Ví điện tử'),
        (2,  N'Phan Đức Long',    '0950000002', N'Thừa Thiên Huế',    3, 3, N'Chuyển khoản'),
        (1,  N'Đỗ Thanh Vân',     '0950000001', N'Đồng Nai',          4, 4, N'Tiền mặt'),
        (0,  N'Trần Nhật Minh',   '0950000000', N'TP. Hồ Chí Minh',   2, 5, N'Ví điện tử');

    INSERT INTO KhachHang
        (tenKH, gioiTinh, namSinh, ngaySinh, soDienThoai, diaChi)
    SELECT
        demo.customerName,
        CASE WHEN demo.monthOffset % 2 = 0 THEN 0 ELSE 1 END,
        1995 + (demo.monthOffset % 8),
        DATEFROMPARTS(1995 + (demo.monthOffset % 8), 6, 15),
        demo.phone,
        demo.address
    FROM @DemoSales demo
    WHERE NOT EXISTS (
        SELECT 1
        FROM KhachHang customer
        WHERE customer.soDienThoai = demo.phone
    );

    UPDATE customer
    SET
        customer.tenKH = demo.customerName,
        customer.gioiTinh = CASE WHEN demo.monthOffset % 2 = 0 THEN 0 ELSE 1 END,
        customer.namSinh = 1995 + (demo.monthOffset % 8),
        customer.ngaySinh = DATEFROMPARTS(1995 + (demo.monthOffset % 8), 6, 15),
        customer.diaChi = demo.address
    FROM KhachHang customer
    INNER JOIN @DemoSales demo ON demo.phone = customer.soDienThoai;

    DECLARE
        @MonthOffset INT,
        @Phone VARCHAR(15),
        @Quantity INT,
        @ProductRank INT,
        @PaymentMethod NVARCHAR(50),
        @CustomerId INT,
        @CustomerName NVARCHAR(100),
        @ProductDetailId INT,
        @ProductName NVARCHAR(100),
        @ProductCode VARCHAR(30),
        @VariantDescription NVARCHAR(200),
        @UnitPrice DECIMAL(18, 2),
        @Total DECIMAL(18, 2),
        @SaleDate DATE,
        @OrderId INT,
        @InvoiceId INT;

    DECLARE demo_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT monthOffset, phone, quantity, productRank, paymentMethod
        FROM @DemoSales
        ORDER BY monthOffset DESC;

    OPEN demo_cursor;
    FETCH NEXT FROM demo_cursor
        INTO @MonthOffset, @Phone, @Quantity, @ProductRank, @PaymentMethod;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SELECT @CustomerId = maKH,
               @CustomerName = tenKH
        FROM KhachHang
        WHERE soDienThoai = @Phone;

        SET @ProductDetailId = NULL;
        SET @UnitPrice = NULL;

        ;WITH RankedProducts AS (
            SELECT
                detail.maChiTietSP,
                product.gia,
                ROW_NUMBER() OVER (ORDER BY detail.maChiTietSP) AS productRow
            FROM ChiTietSanPham detail
            INNER JOIN SanPham product ON product.maSP = detail.maSP
            WHERE product.gia IS NOT NULL
        )
        SELECT
            @ProductDetailId = maChiTietSP,
            @UnitPrice = gia
        FROM RankedProducts
        WHERE productRow = @ProductRank;

        IF @ProductDetailId IS NULL
        BEGIN
            SELECT TOP 1
                @ProductDetailId = detail.maChiTietSP,
                @UnitPrice = product.gia
            FROM ChiTietSanPham detail
            INNER JOIN SanPham product ON product.maSP = detail.maSP
            WHERE product.gia IS NOT NULL
            ORDER BY detail.maChiTietSP;
        END;

        IF @ProductDetailId IS NULL
            THROW 51000, N'Không có chi tiết sản phẩm để tạo dữ liệu Dashboard.', 1;

        SELECT
            @ProductName = product.tenSP,
            @ProductCode = CONCAT('SP-', product.maSP),
            @VariantDescription = CONCAT(
                category.tenLoai, N' / ', color.tenMau, N' / Size ', productSize.tenSize
            )
        FROM ChiTietSanPham detail
        INNER JOIN SanPham product ON product.maSP = detail.maSP
        INNER JOIN Loai category ON category.maLoai = product.maLoai
        INNER JOIN Mau color ON color.maMau = product.maMau
        INNER JOIN Size productSize ON productSize.maSize = product.maSize
        WHERE detail.maChiTietSP = @ProductDetailId;

        SET @SaleDate = CASE
            WHEN @MonthOffset = 0 THEN CONVERT(DATE, GETDATE())
            ELSE DATEFROMPARTS(
                YEAR(DATEADD(MONTH, -@MonthOffset, GETDATE())),
                MONTH(DATEADD(MONTH, -@MonthOffset, GETDATE())),
                12
            )
        END;
        SET @Total = @UnitPrice * @Quantity;
        SET @OrderId = NULL;
        SET @InvoiceId = NULL;

        SELECT TOP 1 @OrderId = maDonHang
        FROM DonHang
        WHERE maKH = @CustomerId
          AND ngayDatHang = @SaleDate;

        IF @OrderId IS NULL
        BEGIN
            INSERT INTO DonHang
                (maKH, maNhanVien, ngayDatHang, tongTien, trangThai)
            VALUES
                (@CustomerId, @NhanVienId, @SaleDate, @Total, N'Đã thanh toán');

            SET @OrderId = CONVERT(INT, SCOPE_IDENTITY());
        END;

        UPDATE DonHang
        SET
            maNhanVien = @NhanVienId,
            tongTien = @Total,
            trangThai = N'Đã thanh toán'
        WHERE maDonHang = @OrderId;

        SELECT TOP 1 @InvoiceId = maHoaDon
        FROM HoaDon
        WHERE maDonHang = @OrderId;

        IF @InvoiceId IS NULL
        BEGIN
            INSERT INTO HoaDon
                (maDonHang, maNhanVien, ngayLap, tongTien, trangThai,
                 tenKhachHangSnapshot, soDienThoaiKhachHangSnapshot,
                 tenNhanVienSnapshot)
            VALUES
                (@OrderId, @NhanVienId, @SaleDate, @Total, N'Đã thanh toán',
                 @CustomerName, @Phone, @NhanVienName);

            SET @InvoiceId = CONVERT(INT, SCOPE_IDENTITY());
        END;

        UPDATE HoaDon
        SET
            maNhanVien = @NhanVienId,
            ngayLap = @SaleDate,
            tongTien = @Total,
            trangThai = N'Đã thanh toán',
            tenKhachHangSnapshot = @CustomerName,
            soDienThoaiKhachHangSnapshot = @Phone,
            tenNhanVienSnapshot = @NhanVienName
        WHERE maHoaDon = @InvoiceId;

        IF NOT EXISTS (
            SELECT 1
            FROM ChiTietHoaDon
            WHERE maHoaDon = @InvoiceId
              AND maChiTietSP = @ProductDetailId
        )
        BEGIN
            INSERT INTO ChiTietHoaDon
                (maHoaDon, maChiTietSP, soLuong, donGia, giaGoc,
                 tenSanPhamSnapshot, maSanPhamSnapshot, moTaBienTheSnapshot)
            VALUES
                (@InvoiceId, @ProductDetailId, @Quantity, @UnitPrice, @UnitPrice,
                 @ProductName, @ProductCode, @VariantDescription);
        END;

        UPDATE ChiTietHoaDon
        SET
            soLuong = @Quantity,
            donGia = @UnitPrice,
            giaGoc = @UnitPrice,
            tenSanPhamSnapshot = @ProductName,
            maSanPhamSnapshot = @ProductCode,
            moTaBienTheSnapshot = @VariantDescription
        WHERE maHoaDon = @InvoiceId
          AND maChiTietSP = @ProductDetailId;

        IF NOT EXISTS (
            SELECT 1
            FROM ThanhToan
            WHERE maHoaDon = @InvoiceId
        )
        BEGIN
            INSERT INTO ThanhToan
                (maHoaDon, phuongThuc, ngayThanhToan, soTien, trangThai)
            VALUES
                (@InvoiceId, @PaymentMethod, @SaleDate, @Total, N'Thành công');
        END;

        UPDATE ThanhToan
        SET
            phuongThuc = @PaymentMethod,
            ngayThanhToan = @SaleDate,
            soTien = @Total,
            trangThai = N'Thành công'
        WHERE maHoaDon = @InvoiceId;

        FETCH NEXT FROM demo_cursor
            INTO @MonthOffset, @Phone, @Quantity, @ProductRank, @PaymentMethod;
    END;

    CLOSE demo_cursor;
    DEALLOCATE demo_cursor;

    IF @InvoiceId IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
           FROM LichSuChinhSuaHoaDon
           WHERE maHoaDon = @InvoiceId
       )
    BEGIN
        INSERT INTO LichSuChinhSuaHoaDon
            (maHoaDon, maPhien, nguoiChinhSua, thoiGian, duLieuTruoc, duLieuSau)
        VALUES
            (@InvoiceId, NULL, @NhanVienName, SYSDATETIME(),
             N'{"trangThai":"Chưa thanh toán"}',
             N'{"trangThai":"Đã thanh toán"}');
    END;

    COMMIT TRANSACTION;
    PRINT N'Đã bổ sung dữ liệu mẫu Dashboard thành công.';
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'demo_cursor') > -1
        CLOSE demo_cursor;

    IF CURSOR_STATUS('local', 'demo_cursor') >= -1
        DEALLOCATE demo_cursor;

    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;
GO
