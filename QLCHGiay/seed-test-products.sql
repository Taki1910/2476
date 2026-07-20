USE QuanLyBanHang;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    /*
       Schema hien tai luu moi bien the (mau + size) thanh mot dong SanPham.
       Script nay tao 5 san pham, moi san pham co 5 bien the de test.
       Co the chay lai script ma khong tao trung bien the.
    */

    IF NOT EXISTS (SELECT 1 FROM Loai WHERE tenLoai = N'Giày chạy bộ')
        INSERT INTO Loai (tenLoai, tonKho) VALUES (N'Giày chạy bộ', 0);
    IF NOT EXISTS (SELECT 1 FROM Loai WHERE tenLoai = N'Giày thời trang')
        INSERT INTO Loai (tenLoai, tonKho) VALUES (N'Giày thời trang', 0);
    IF NOT EXISTS (SELECT 1 FROM Loai WHERE tenLoai = N'Giày bóng rổ')
        INSERT INTO Loai (tenLoai, tonKho) VALUES (N'Giày bóng rổ', 0);

    IF NOT EXISTS (SELECT 1 FROM ChatLieu WHERE tenChatLieu = N'Lưới')
        INSERT INTO ChatLieu (tenChatLieu, tonKho) VALUES (N'Lưới', 0);
    IF NOT EXISTS (SELECT 1 FROM ChatLieu WHERE tenChatLieu = N'Da tổng hợp')
        INSERT INTO ChatLieu (tenChatLieu, tonKho) VALUES (N'Da tổng hợp', 0);
    IF NOT EXISTS (SELECT 1 FROM ChatLieu WHERE tenChatLieu = N'Canvas')
        INSERT INTO ChatLieu (tenChatLieu, tonKho) VALUES (N'Canvas', 0);

    DECLARE @Colors TABLE (tenMau NVARCHAR(50));
    INSERT INTO @Colors (tenMau)
    VALUES (N'Trắng'), (N'Đen'), (N'Đỏ'), (N'Xanh navy'), (N'Xám'), (N'Be');

    INSERT INTO Mau (tenMau, tonKho)
    SELECT c.tenMau, 0
    FROM @Colors c
    WHERE NOT EXISTS (SELECT 1 FROM Mau m WHERE m.tenMau = c.tenMau);

    DECLARE @Sizes TABLE (tenSize NVARCHAR(20));
    INSERT INTO @Sizes (tenSize)
    VALUES (N'38'), (N'39'), (N'40'), (N'41'), (N'42'), (N'43');

    INSERT INTO Size (tenSize, tonKho)
    SELECT s.tenSize, 0
    FROM @Sizes s
    WHERE NOT EXISTS (SELECT 1 FROM Size z WHERE z.tenSize = s.tenSize);

    IF NOT EXISTS (SELECT 1 FROM NhaCungCap WHERE tenNCC = N'Kicks Zone Test Supplier')
    BEGIN
        INSERT INTO NhaCungCap
            (tenNCC, soDienThoai, email, diaChi, trangThai)
        VALUES
            (N'Kicks Zone Test Supplier', '0909999999', 'test-supplier@kickszone.vn',
             N'Thành phố Hồ Chí Minh', N'Hoạt động');
    END;

    DECLARE @Variants TABLE
    (
        tenSP        NVARCHAR(100) NOT NULL,
        tenLoai      NVARCHAR(50)  NOT NULL,
        tenMau       NVARCHAR(50)  NOT NULL,
        tenChatLieu  NVARCHAR(50)  NOT NULL,
        tenSize      NVARCHAR(20)  NOT NULL,
        gia          DECIMAL(18,2) NOT NULL,
        tonKho       INT           NOT NULL,
        thuongHieu   NVARCHAR(100) NOT NULL,
        xuatXu       NVARCHAR(100) NOT NULL,
        moTa         NVARCHAR(500) NOT NULL
    );

    INSERT INTO @Variants
        (tenSP, tenLoai, tenMau, tenChatLieu, tenSize, gia, tonKho,
         thuongHieu, xuatXu, moTa)
    VALUES
        /* Nike Air Max Pulse: 5 bien the */
        (N'Nike Air Max Pulse', N'Giày chạy bộ', N'Trắng',    N'Lưới',        N'39', 3190000, 12, N'Nike',     N'Việt Nam', N'Giày chạy bộ đệm Air, biến thể trắng size 39.'),
        (N'Nike Air Max Pulse', N'Giày chạy bộ', N'Đen',      N'Lưới',        N'40', 3190000,  8, N'Nike',     N'Việt Nam', N'Giày chạy bộ đệm Air, biến thể đen size 40.'),
        (N'Nike Air Max Pulse', N'Giày chạy bộ', N'Đỏ',       N'Lưới',        N'41', 3290000,  5, N'Nike',     N'Việt Nam', N'Giày chạy bộ đệm Air, biến thể đỏ size 41.'),
        (N'Nike Air Max Pulse', N'Giày chạy bộ', N'Xám',      N'Lưới',        N'42', 3190000,  3, N'Nike',     N'Việt Nam', N'Giày chạy bộ đệm Air, biến thể xám size 42.'),
        (N'Nike Air Max Pulse', N'Giày chạy bộ', N'Xanh navy',N'Lưới',        N'43', 3290000,  0, N'Nike',     N'Việt Nam', N'Giày chạy bộ đệm Air, biến thể xanh navy size 43.'),

        /* Adidas Ultraboost Light: 5 bien the */
        (N'Adidas Ultraboost Light', N'Giày chạy bộ', N'Trắng',    N'Lưới', N'38', 3890000, 10, N'Adidas', N'Indonesia', N'Giày chạy bộ Boost, biến thể trắng size 38.'),
        (N'Adidas Ultraboost Light', N'Giày chạy bộ', N'Đen',      N'Lưới', N'39', 3890000,  7, N'Adidas', N'Indonesia', N'Giày chạy bộ Boost, biến thể đen size 39.'),
        (N'Adidas Ultraboost Light', N'Giày chạy bộ', N'Xám',      N'Lưới', N'40', 3990000,  4, N'Adidas', N'Indonesia', N'Giày chạy bộ Boost, biến thể xám size 40.'),
        (N'Adidas Ultraboost Light', N'Giày chạy bộ', N'Xanh navy',N'Lưới', N'41', 3990000,  2, N'Adidas', N'Indonesia', N'Giày chạy bộ Boost, biến thể xanh navy size 41.'),
        (N'Adidas Ultraboost Light', N'Giày chạy bộ', N'Đỏ',       N'Lưới', N'42', 4090000,  1, N'Adidas', N'Indonesia', N'Giày chạy bộ Boost, biến thể đỏ size 42.'),

        /* Converse Run Star Hike: 5 bien the */
        (N'Converse Run Star Hike', N'Giày thời trang', N'Trắng', N'Canvas', N'38', 2290000, 15, N'Converse', N'Việt Nam', N'Giày cổ cao đế răng cưa, biến thể trắng size 38.'),
        (N'Converse Run Star Hike', N'Giày thời trang', N'Đen',   N'Canvas', N'39', 2290000, 11, N'Converse', N'Việt Nam', N'Giày cổ cao đế răng cưa, biến thể đen size 39.'),
        (N'Converse Run Star Hike', N'Giày thời trang', N'Đỏ',    N'Canvas', N'40', 2390000,  6, N'Converse', N'Việt Nam', N'Giày cổ cao đế răng cưa, biến thể đỏ size 40.'),
        (N'Converse Run Star Hike', N'Giày thời trang', N'Be',    N'Canvas', N'41', 2390000,  4, N'Converse', N'Việt Nam', N'Giày cổ cao đế răng cưa, biến thể be size 41.'),
        (N'Converse Run Star Hike', N'Giày thời trang', N'Xám',   N'Canvas', N'42', 2390000,  0, N'Converse', N'Việt Nam', N'Giày cổ cao đế răng cưa, biến thể xám size 42.'),

        /* New Balance 550: 5 bien the */
        (N'New Balance 550', N'Giày thời trang', N'Trắng',    N'Da tổng hợp', N'39', 2790000, 13, N'New Balance', N'Indonesia', N'Giày phong cách retro, biến thể trắng size 39.'),
        (N'New Balance 550', N'Giày thời trang', N'Đen',      N'Da tổng hợp', N'40', 2790000,  9, N'New Balance', N'Indonesia', N'Giày phong cách retro, biến thể đen size 40.'),
        (N'New Balance 550', N'Giày thời trang', N'Xanh navy',N'Da tổng hợp', N'41', 2890000,  5, N'New Balance', N'Indonesia', N'Giày phong cách retro, biến thể xanh navy size 41.'),
        (N'New Balance 550', N'Giày thời trang', N'Đỏ',       N'Da tổng hợp', N'42', 2890000,  2, N'New Balance', N'Indonesia', N'Giày phong cách retro, biến thể đỏ size 42.'),
        (N'New Balance 550', N'Giày thời trang', N'Xám',      N'Da tổng hợp', N'43', 2890000,  0, N'New Balance', N'Indonesia', N'Giày phong cách retro, biến thể xám size 43.'),

        /* Puma All-Pro Nitro: 5 bien the */
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Trắng',    N'Lưới', N'39', 2990000, 10, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể trắng size 39.'),
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Đen',      N'Lưới', N'40', 2990000,  8, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể đen size 40.'),
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Đỏ',       N'Lưới', N'41', 3090000,  5, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể đỏ size 41.'),
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Xanh navy',N'Lưới', N'42', 3090000,  3, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể xanh navy size 42.'),
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Xám',      N'Lưới', N'43', 3090000,  1, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể xám size 43.');

    INSERT INTO SanPham (tenSP, maLoai, maMau, maChatLieu, maSize, gia, tonKho)
    SELECT v.tenSP, l.maLoai, m.maMau, c.maChatLieu, s.maSize, v.gia, v.tonKho
    FROM @Variants v
    INNER JOIN Loai l ON l.tenLoai = v.tenLoai
    INNER JOIN Mau m ON m.tenMau = v.tenMau
    INNER JOIN ChatLieu c ON c.tenChatLieu = v.tenChatLieu
    INNER JOIN Size s ON s.tenSize = v.tenSize
    WHERE NOT EXISTS
    (
        SELECT 1
        FROM SanPham p
        WHERE p.tenSP = v.tenSP
          AND p.maMau = m.maMau
          AND p.maSize = s.maSize
    );

    DECLARE @MaNCC INT =
        (SELECT TOP (1) maNCC FROM NhaCungCap WHERE tenNCC = N'Kicks Zone Test Supplier');

    INSERT INTO ChiTietSanPham
        (maSP, maNCC, moTa, hinhAnh, xuatXu, thuongHieu, trangThai)
    SELECT p.maSP, @MaNCC, v.moTa, NULL, v.xuatXu, v.thuongHieu,
           CASE WHEN p.tonKho > 0 THEN N'Còn hàng' ELSE N'Hết hàng' END
    FROM @Variants v
    INNER JOIN Mau m ON m.tenMau = v.tenMau
    INNER JOIN Size s ON s.tenSize = v.tenSize
    INNER JOIN SanPham p
        ON p.tenSP = v.tenSP
       AND p.maMau = m.maMau
       AND p.maSize = s.maSize
    WHERE NOT EXISTS
    (
        SELECT 1 FROM ChiTietSanPham d WHERE d.maSP = p.maSP
    );

    COMMIT TRANSACTION;

    SELECT p.tenSP AS sanPham,
           COUNT(*) AS soBienThe,
           SUM(p.tonKho) AS tongTonKho,
           MIN(p.gia) AS giaThapNhat,
           MAX(p.gia) AS giaCaoNhat
    FROM SanPham p
    WHERE p.tenSP IN
    (
        N'Nike Air Max Pulse',
        N'Adidas Ultraboost Light',
        N'Converse Run Star Hike',
        N'New Balance 550',
        N'Puma All-Pro Nitro'
    )
    GROUP BY p.tenSP
    ORDER BY p.tenSP;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
