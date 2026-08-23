# QLCHGiay
Project Spring Boot quản lý cửa hàng sneaker, giữ giao diện đăng nhập/dashboard của folder dự án và dùng SQL Server database `QuanLyBanHang`.

Bảng lưu tóm tắt phiên làm việc được tạo idempotent từ `src/main/resources/schema.sql` khi ứng dụng khởi động. File `migrate-work-session.sql` dành cho trường hợp cần chạy migration thủ công; `QuanLyBanHang.sql` đã bao gồm bảng này cho các lần khởi tạo mới.

## Chạy
1. Chạy `QuanLyBanHang.sql` trong SSMS để tạo schema và dữ liệu mẫu cơ bản.
2. Chạy `seed-test-products.sql` để tạo các mẫu giày với biến thể đủ
   loại/màu/chất liệu/size, kèm ảnh minh họa theo thương hiệu.
3. Chạy `seed-dashboard-data.sql` để bổ sung dữ liệu bán hàng của 12 tháng gần
   nhất, khách hàng mẫu, hai sản phẩm tồn kho thấp và hai chương trình khuyến mại.
   Các script seed có thể chạy lại mà không tạo trùng dữ liệu; ngày báo cáo và
   thời gian khuyến mại được tính theo ngày chạy hiện tại.
   Nếu chạy bằng `sqlcmd`, dùng tùy chọn UTF-8:
   `sqlcmd -S localhost -U sa -P <mật_khẩu> -C -f 65001 -i seed-test-products.sql` rồi chạy tiếp `seed-dashboard-data.sql`.
4. Khai báo mật khẩu SQL Server ngoài mã nguồn:
   PowerShell: `$env:DB_PASSWORD='<mật_khẩu>'`.
   Có thể ghi đè thêm `DB_URL`, `DB_USERNAME` và `SERVER_PORT` nếu cần.
5. Chạy `./mvnw spring-boot:run` hoặc `mvnw.cmd spring-boot:run`.
6. Mở http://localhost:8081/login

Các file SQL được lưu bằng UTF-8. Khi mở trong SSMS, chọn encoding UTF-8 và giữ
tiền tố `N` trước chuỗi Unicode để dữ liệu tiếng Việt được lưu đúng.

Tài khoản dữ liệu mẫu: admin / 123456. Mật khẩu dữ liệu cũ được tự động nâng cấp
sang BCrypt sau lần đăng nhập thành công đầu tiên.

Nếu quên mật khẩu, liên hệ trực tiếp quản lý cửa hàng để được xác minh và cấp lại
qua kênh nội bộ. Sau khi đăng nhập, tài khoản Admin/Quản lý có thể đặt lại mật
khẩu cho tài khoản cần hỗ trợ tại trang `Cài đặt`. Mật khẩu mới được lưu bằng
BCrypt; hệ thống không cho phép đặt lại công khai chỉ bằng số điện thoại.

Khi một tài khoản nhân viên nhập sai mật khẩu 3 lần liên tiếp, Dashboard của
Admin/Quản lý hiển thị yêu cầu hỗ trợ và dẫn thẳng tới tài khoản cần xử lý trong
`Cài đặt`. Sau quá 5 lần sai liên tiếp (lần thứ 6), tài khoản bị tạm khóa cho tới
khi Admin/Quản lý đặt lại mật khẩu hoặc bấm `Mở khóa`. Đăng nhập đúng trước khi
bị khóa, đặt lại mật khẩu hoặc mở khóa thành công sẽ xóa yêu cầu này.
