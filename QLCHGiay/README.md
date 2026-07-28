# QLCHGiay
Project Spring Boot quản lý cửa hàng sneaker, giữ giao diện đăng nhập/dashboard của folder dự án và dùng SQL Server database `QuanLyBanHang`.

Bảng lưu tóm tắt phiên làm việc được tạo idempotent từ `src/main/resources/schema.sql` khi ứng dụng khởi động. File `migrate-work-session.sql` dành cho trường hợp cần chạy migration thủ công; `QuanLyBanHang.sql` đã bao gồm bảng này cho các lần khởi tạo mới.

## Chạy
1. Chạy `QuanLyBanHang.sql` trong SSMS để tạo schema và dữ liệu mẫu cơ bản.
2. Chạy `seed-dashboard-data.sql` để bổ sung dữ liệu bán hàng của 12 tháng gần
   nhất, khách hàng mẫu và hai sản phẩm tồn kho thấp. Script này có thể chạy lại
   an toàn, dữ liệu biểu đồ được tính theo ngày chạy hiện tại.
   Nếu chạy bằng `sqlcmd`, dùng tùy chọn UTF-8:
   `sqlcmd -S localhost -U sa -P <mật_khẩu> -C -f 65001 -i seed-dashboard-data.sql`.
3. Nếu cần dữ liệu kiểm thử sản phẩm, chạy thêm `seed-test-products.sql` để tạo
   5 sản phẩm, mỗi sản phẩm có 5 biến thể. Script seed có thể chạy lại mà không
   tạo trùng dữ liệu.
4. Kiểm tra `src/main/resources/application.properties`.
5. Chạy `./mvnw spring-boot:run` hoặc `mvnw.cmd spring-boot:run`.
6. Mở http://localhost:8081/login

Các file SQL được lưu bằng UTF-8. Khi mở trong SSMS, chọn encoding UTF-8 và giữ
tiền tố `N` trước chuỗi Unicode để dữ liệu tiếng Việt được lưu đúng.

Tài khoản dữ liệu mẫu: admin / 123456

Nếu quên mật khẩu, mở liên kết **Quên mật khẩu?** ở trang đăng nhập và xác minh
bằng số điện thoại nhân viên. Tài khoản `admin` dùng số mẫu `0911111111`.
