/* DEVELOPMENT TEST CATALOG ONLY — never execute as an application startup script. */
USE QuanLyBanHang;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    /*
       Schema hien tai luu moi bien the (mau + size) thanh mot dong SanPham.
       Script này tạo các mẫu giày và biến thể đủ loại, màu, chất liệu, size để test.
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
        (N'Puma All-Pro Nitro', N'Giày bóng rổ', N'Xám',      N'Lưới', N'43', 3090000,  1, N'Puma', N'Việt Nam', N'Giày bóng rổ đệm Nitro, biến thể xám size 43.'),

        /* Nike Dunk Low: cùng mẫu, khác màu và size; có màu Trắng ở hai size. */
        (N'Nike Dunk Low', N'Giày thời trang', N'Trắng',    N'Da tổng hợp', N'38', 2890000,  6, N'Nike', N'Việt Nam', N'Nike Dunk Low trắng size 38.'),
        (N'Nike Dunk Low', N'Giày thời trang', N'Đen',      N'Da tổng hợp', N'39', 2890000,  6, N'Nike', N'Việt Nam', N'Nike Dunk Low đen size 39.'),
        (N'Nike Dunk Low', N'Giày thời trang', N'Trắng',    N'Da tổng hợp', N'40', 2990000,  7, N'Nike', N'Việt Nam', N'Nike Dunk Low trắng size 40.'),
        (N'Nike Dunk Low', N'Giày thời trang', N'Xanh navy',N'Da tổng hợp', N'40', 2990000,  4, N'Nike', N'Việt Nam', N'Nike Dunk Low xanh navy size 40.'),

        /* Adidas Forum Low: cùng mẫu, màu Trắng ở hai size và nhiều màu khác. */
        (N'Adidas Forum Low', N'Giày thời trang', N'Trắng', N'Da tổng hợp', N'39', 2690000,  6, N'Adidas', N'Indonesia', N'Adidas Forum Low trắng size 39.'),
        (N'Adidas Forum Low', N'Giày thời trang', N'Đỏ',   N'Da tổng hợp', N'40', 2790000,  5, N'Adidas', N'Indonesia', N'Adidas Forum Low đỏ size 40.'),
        (N'Adidas Forum Low', N'Giày thời trang', N'Trắng', N'Da tổng hợp', N'41', 2790000,  4, N'Adidas', N'Indonesia', N'Adidas Forum Low trắng size 41.'),
        (N'Adidas Forum Low', N'Giày thời trang', N'Xám',   N'Da tổng hợp', N'41', 2790000,  3, N'Adidas', N'Indonesia', N'Adidas Forum Low xám size 41.'),

        /* Các thuộc tính có sẵn từ bootstrap: Dép, Boot, Xanh, Vải và Cao su. */
        (N'Puma Slide Comfort', N'Dép', N'Xanh', N'Cao su', N'40', 890000, 14, N'Puma', N'Việt Nam', N'Dép Puma đế cao su mềm, màu xanh size 40.'),
        (N'Puma Slide Comfort', N'Dép', N'Đen', N'Cao su', N'41', 890000, 9, N'Puma', N'Việt Nam', N'Dép Puma đế cao su mềm, màu đen size 41.'),
        (N'Timberland Classic Boot', N'Giày Boot', N'Đen', N'Da', N'42', 4590000, 4, N'Timberland', N'Việt Nam', N'Giày boot da cổ điển màu đen size 42.'),
        (N'Vans Authentic', N'Giày Thể Thao', N'Xanh', N'Vải', N'39', 1590000, 8, N'Vans', N'Việt Nam', N'Giày Vans vải canvas màu xanh size 39.');

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
        (SELECT TOP (1) maNCC
         FROM NhaCungCap
         WHERE tenNCC = N'Kicks Zone Test Supplier'
           AND trangThai = N'Hoạt động');

    IF @MaNCC IS NULL
        THROW 51000, N'Nhà cung cấp kiểm thử không hoạt động.', 1;

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

    UPDATE detail
    SET hinhAnh = CASE
        WHEN product.tenSP LIKE N'%Adidas%' THEN N'/images/products/adidas.svg'
        WHEN product.tenSP LIKE N'%Converse%' THEN N'/images/products/converse.svg'
        WHEN product.tenSP LIKE N'%New Balance%' THEN N'/images/products/new-balance.svg'
        WHEN product.tenSP LIKE N'%Puma%' THEN N'/images/products/puma.svg'
        WHEN product.tenSP LIKE N'%Vans%' THEN N'/images/products/vans.svg'
        WHEN product.tenSP LIKE N'%Timberland%' THEN N'/images/products/jordan.svg'
        ELSE N'/images/products/nike.svg'
    END
    FROM ChiTietSanPham detail
    INNER JOIN SanPham product ON product.maSP = detail.maSP
    WHERE detail.hinhAnh IS NULL
      AND product.tenSP IN (
          N'Nike Air Max Pulse', N'Adidas Ultraboost Light',
          N'Converse Run Star Hike', N'New Balance 550', N'Puma All-Pro Nitro',
          N'Nike Dunk Low', N'Adidas Forum Low', N'Puma Slide Comfort',
          N'Timberland Classic Boot', N'Vans Authentic'
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
        N'Puma All-Pro Nitro',
        N'Nike Dunk Low',
        N'Adidas Forum Low',
        N'Puma Slide Comfort',
        N'Timberland Classic Boot',
        N'Vans Authentic'
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
