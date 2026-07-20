# QLCHGiay
Project Spring Boot quản lý cửa hàng sneaker, giữ giao diện đăng nhập/dashboard của folder dự án và dùng SQL Server database `QuanLyBanHang`.

## Chạy
1. Chạy `QuanLyBanHang.sql` trong SSMS để tạo schema và dữ liệu mẫu cơ bản.
2. Nếu cần dữ liệu kiểm thử sản phẩm, chạy thêm `seed-test-products.sql` để tạo
   5 sản phẩm, mỗi sản phẩm có 5 biến thể. Script seed có thể chạy lại mà không
   tạo trùng dữ liệu.
3. Kiểm tra `src/main/resources/application.properties`.
4. Chạy `./mvnw spring-boot:run` hoặc `mvnw.cmd spring-boot:run`.
5. Mở http://localhost:8081/login

Các file SQL được lưu bằng UTF-8. Khi mở trong SSMS, chọn encoding UTF-8 và giữ
tiền tố `N` trước chuỗi Unicode để dữ liệu tiếng Việt được lưu đúng.

Tài khoản dữ liệu mẫu: admin / 123456
