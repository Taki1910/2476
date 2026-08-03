(() => {
    const STORAGE_KEY = 'appLanguage';
    const DEFAULT_LANGUAGE = 'vi';
    let originalDocumentTitle = '';
    const messages = {
        vi: {
            'page.title': 'Bảng điều khiển - KICKS ZONE',
            'nav.dashboard': 'Bảng điều khiển',
            'nav.products': 'Giày & Dép',
            'nav.accessories': 'Phụ kiện giày',
            'nav.customers': 'Khách hàng',
            'nav.invoices': 'Hóa đơn',
            'nav.reports': 'Báo cáo',
            'nav.suppliers': 'Nhà cung cấp',
            'nav.chatbot': 'Chatbot hỗ trợ',
            'nav.promotions': 'Khuyến mại',
            'nav.settings': 'Cài đặt',
            'nav.logout': 'Đăng xuất',
            'search.placeholder': 'Tìm kiếm nhanh...',
            'notification.title': 'Thông báo',
            'language.toEnglish': 'Chuyển sang tiếng Anh',
            'language.toVietnamese': 'Switch to Vietnamese',
            'theme.toDark': 'Chuyển sang chế độ tối',
            'theme.toLight': 'Chuyển sang chế độ sáng',
            'quick.invoice': 'Tạo hóa đơn',
            'quick.product': 'Thêm giày mới',
            'quick.report': 'Xuất báo cáo',
            'kpi.todayRevenue': 'Doanh thu hôm nay',
            'kpi.sold': 'Giày đã bán',
            'kpi.invoices': 'Hóa đơn mới',
            'kpi.customers': 'Khách hàng mới',
            'compare.yesterday': 'so với hôm qua',
            'compare.lastWeek': 'so với tuần trước',
            'compare.lastMonth': 'so với tháng trước',
            'chart.revenue': 'Doanh thu 6 tháng gần nhất',
            'chart.revenueDataset': 'Doanh thu (triệu VNĐ)',
            'chart.category': 'Cơ cấu danh mục sản phẩm',
            'range.sixMonths': '6 tháng gần nhất',
            'range.year': 'Cả năm',
            'table.recent': 'Giao dịch gần đây',
            'table.viewAll': 'Xem tất cả',
            'table.invoiceCode': 'Mã hóa đơn',
            'table.customer': 'Khách hàng',
            'table.order': 'Đơn hàng',
            'table.total': 'Tổng tiền',
            'table.status': 'Trạng thái',
            'stock.title': 'Cảnh báo tồn kho ít',
            'stock.viewProducts': 'Xem sản phẩm',
            'unit.pairs': 'đôi',
            'unit.orders': 'đơn',
            'unit.customers': 'khách',
            'unit.products': 'sản phẩm',
            'unit.remainingPairs': 'Còn {value} đôi',
            'status.unknown': 'Chưa cập nhật',
            'status.paid': 'Đã thanh toán',
            'status.completed': 'Hoàn thành',
            'status.cancelled': 'Đã hủy',
            'status.pending': 'Chờ xử lý'
        },
        en: {
            'page.title': 'Dashboard - KICKS ZONE',
            'nav.dashboard': 'Dashboard',
            'nav.products': 'Shoes & Sandals',
            'nav.accessories': 'Shoe Accessories',
            'nav.customers': 'Customers',
            'nav.invoices': 'Invoices',
            'nav.reports': 'Reports',
            'nav.suppliers': 'Suppliers',
            'nav.chatbot': 'Support Chatbot',
            'nav.promotions': 'Promotions',
            'nav.settings': 'Settings',
            'nav.logout': 'Log out',
            'search.placeholder': 'Quick search...',
            'notification.title': 'Notifications',
            'language.toEnglish': 'Switch to English',
            'language.toVietnamese': 'Switch to Vietnamese',
            'theme.toDark': 'Switch to dark mode',
            'theme.toLight': 'Switch to light mode',
            'quick.invoice': 'Create invoice',
            'quick.product': 'Add new shoes',
            'quick.report': 'Export report',
            'kpi.todayRevenue': "Today's revenue",
            'kpi.sold': 'Shoes sold',
            'kpi.invoices': 'New invoices',
            'kpi.customers': 'New customers',
            'compare.yesterday': 'vs. yesterday',
            'compare.lastWeek': 'vs. last week',
            'compare.lastMonth': 'vs. last month',
            'chart.revenue': 'Revenue over the last 6 months',
            'chart.revenueDataset': 'Revenue (million VND)',
            'chart.category': 'Product category breakdown',
            'range.sixMonths': 'Last 6 months',
            'range.year': 'Full year',
            'table.recent': 'Recent transactions',
            'table.viewAll': 'View all',
            'table.invoiceCode': 'Invoice ID',
            'table.customer': 'Customer',
            'table.order': 'Order',
            'table.total': 'Total',
            'table.status': 'Status',
            'stock.title': 'Low-stock warning',
            'stock.viewProducts': 'View products',
            'unit.pairs': 'pairs',
            'unit.orders': 'orders',
            'unit.customers': 'customers',
            'unit.products': 'products',
            'unit.remainingPairs': '{value} pairs left',
            'status.unknown': 'Not updated',
            'status.paid': 'Paid',
            'status.completed': 'Completed',
            'status.cancelled': 'Cancelled',
            'status.pending': 'Pending'
        }
    };
    const exactTextEn = {
        'Bảng điều khiển - KICKS ZONE': 'Dashboard - KICKS ZONE',
        'Quản lý sản phẩm - KICKS ZONE': 'Product Management - KICKS ZONE',
        'Quản lý khách hàng - KICKS ZONE': 'Customer Management - KICKS ZONE',
        'Quản lý hóa đơn - KICKS ZONE': 'Invoice Management - KICKS ZONE',
        'Quản lý nhà cung cấp - KICKS ZONE': 'Supplier Management - KICKS ZONE',
        'Báo cáo thống kê - KICKS ZONE': 'Analytics Report - KICKS ZONE',
        'Trợ lý cửa hàng - KICKS ZONE': 'Store Assistant - KICKS ZONE',
        'Quản lý khuyến mại - KICKS ZONE': 'Promotion Management - KICKS ZONE',
        'Cài đặt - KICKS ZONE': 'Settings - KICKS ZONE',
        'Đăng nhập - KICKS ZONE': 'Login - KICKS ZONE',
        'Quên mật khẩu - KICKS ZONE': 'Forgot Password - KICKS ZONE',
        'Sản phẩm - KICKS ZONE': 'Product - KICKS ZONE',
        'Chi tiết sản phẩm - KICKS ZONE': 'Product Details - KICKS ZONE',
        'Chi tiết hóa đơn': 'Invoice details',
        'Chi tiết khách hàng': 'Customer details',
        'Chi tiết nhà cung cấp': 'Supplier details',
        'Khách hàng': 'Customers',
        'Nhà cung cấp': 'Suppliers',
        'Thêm mới khách hàng': 'Add customer',
        'Cập nhật khách hàng': 'Update customer',
        'Thêm mới nhà cung cấp': 'Add supplier',
        'Cập nhật nhà cung cấp': 'Update supplier',
        'Tạo hóa đơn mới': 'Create invoice',
        'Cập nhật hóa đơn': 'Update invoice',
        'Thêm sản phẩm mới': 'Add product',
        'Cập nhật sản phẩm': 'Update product',
        'Bảng điều khiển': 'Dashboard',
        'Giày & Dép': 'Shoes & Sandals',
        'Phụ kiện giày': 'Shoe Accessories',
        'Khách hàng': 'Customers',
        'Hóa đơn': 'Invoices',
        'Báo cáo': 'Reports',
        'Nhà cung cấp': 'Suppliers',
        'Chatbot hỗ trợ': 'Support Chatbot',
        'Chatbot hỗ trợ cửa hàng': 'Store Support Chatbot',
        'Khuyến mại': 'Promotions',
        'Cài đặt': 'Settings',
        'Đăng xuất': 'Log out',
        'Đăng nhập': 'Log in',
        'Tên đăng nhập': 'Username',
        'Mật khẩu': 'Password',
        'Mật khẩu mới': 'New password',
        'Xác nhận mật khẩu': 'Confirm password',
        'Ghi nhớ đăng nhập': 'Remember me',
        'Quên mật khẩu?': 'Forgot password?',
        'Quay lại đăng nhập': 'Back to login',
        'Hệ thống Quản lý Cửa hàng Giày Sneaker': 'Sneaker Store Management System',
        'Sai tên đăng nhập hoặc mật khẩu': 'Incorrect username or password',
        'Quản lý sản phẩm': 'Product management',
        'Sắp xếp mặc định: tồn kho cao nhất trước.':
            'Default order: highest stock first.',
        'Sắp xếp mặc định: sản phẩm mới thêm trước.':
            'Default order: newest products first.',
        'Tổng sản phẩm': 'Total products',
        'Sản phẩm': 'Product',
        'Tên sản phẩm': 'Product name',
        'Tên sản phẩm *': 'Product name *',
        'Nhập tên để tìm sản phẩm hoặc biến thể đã có':
            'Enter a name to find existing products or variants',
        'Sản phẩm và biến thể đã có': 'Existing products and variants',
        'Thêm sản phẩm': 'Add product',
        'Lưu sản phẩm': 'Save product',
        'Nhập đầy đủ thông tin giày hoặc dép.': 'Enter complete shoe or sandal information.',
        'Danh sách sản phẩm': 'Product list',
        'Toàn bộ sản phẩm': 'All products',
        'Đang kinh doanh': 'Currently available',
        'Tồn kho từ 1 đến 5': 'Stock from 1 to 5',
        'Cần nhập kho': 'Restock required',
        'Sản phẩm cửa hàng': 'Store product',
        'Xóa lọc': 'Clear filters',
        'Đóng bộ lọc nâng cao': 'Close advanced filters',
        'Sắp xếp theo giá': 'Sort by price',
        'Giá thấp → cao': 'Price: low to high',
        'Giá cao → thấp': 'Price: high to low',
        '+ Thêm loại mới...': '+ Add a new category...',
        '+ Thêm màu mới...': '+ Add a new color...',
        '+ Thêm chất liệu mới...': '+ Add a new material...',
        '+ Thêm size mới...': '+ Add a new size...',
        'Nhập tên loại mới': 'Enter a new category name',
        'Nhập tên màu mới': 'Enter a new color name',
        'Nhập tên chất liệu mới': 'Enter a new material name',
        'Nhập size mới': 'Enter a new size',
        'Giá trị mới sẽ được lưu khi lưu sản phẩm.':
            'The new value will be saved with the product.',
        'Thêm': 'Add',
        'Danh sách': 'List',
        'Quay lại danh sách': 'Back to list',
        'Cập nhật': 'Update',
        'Chi tiết sản phẩm': 'Product details',
        'Loại': 'Category',
        'Loại *': 'Category *',
        'Màu': 'Color',
        'Màu *': 'Color *',
        'Màu sắc': 'Color',
        'Chất liệu': 'Material',
        'Chất liệu *': 'Material *',
        'Giá': 'Price',
        'Giá bán': 'Sale price',
        'Giá bán (VNĐ) *': 'Sale price (VND) *',
        'Size': 'Size',
        'Size *': 'Size *',
        'Tồn kho': 'Stock',
        'Số lượng tồn': 'Stock quantity',
        'Số lượng tồn *': 'Stock quantity *',
        'Còn hàng': 'In stock',
        'Sắp hết hàng': 'Low stock',
        'Hết hàng': 'Out of stock',
        'Tất cả loại': 'All categories',
        'Tất cả màu': 'All colors',
        'Tất cả size': 'All sizes',
        'Tất cả tồn kho': 'All stock levels',
        'Bộ lọc nâng cao': 'Advanced filters',
        'Mở bộ lọc nâng cao': 'Open advanced filters',
        'Lọc theo loại': 'Filter by category',
        'Lọc theo màu': 'Filter by color',
        'Lọc theo size': 'Filter by size',
        'Lọc theo tồn kho': 'Filter by stock',
        '-- Chọn loại --': '-- Select category --',
        '-- Chọn màu --': '-- Select color --',
        '-- Chọn chất liệu --': '-- Select material --',
        '-- Chọn size --': '-- Select size --',
        'Quản lý khách hàng': 'Customer management',
        'Tổng khách hàng': 'Total customers',
        'Khách hàng mới': 'New customers',
        'Khách hàng nam': 'Male customers',
        'Khách hàng nữ': 'Female customers',
        'Chi tiết khách hàng': 'Customer details',
        'Thêm khách hàng': 'Add customer',
        'Sửa khách hàng': 'Edit customer',
        'Họ tên': 'Full name',
        'Họ tên *': 'Full name *',
        'Giới tính': 'Gender',
        'Nam': 'Male',
        'Nữ': 'Female',
        'Năm sinh': 'Year of birth',
        'Số điện thoại': 'Phone number',
        'Số điện thoại *': 'Phone number *',
        'Điện thoại': 'Phone',
        'Địa chỉ': 'Address',
        'Đúng 10 chữ số, bắt đầu bằng 0': 'Exactly 10 digits, starting with 0',
        'Tất cả giới tính': 'All genders',
        'Quản lý hóa đơn': 'Invoice management',
        'Tổng hóa đơn': 'Total invoices',
        'Doanh thu hóa đơn': 'Invoice revenue',
        'Hóa đơn mới': 'New invoices',
        'Tạo hóa đơn': 'Create invoice',
        'Tạo nhanh khách mới': 'Quickly add a new customer',
        'Thông tin hóa đơn': 'Invoice information',
        'Sản phẩm khách mua': 'Purchased products',
        'Sản phẩm đã chọn': 'Selected products',
        'Lưu hóa đơn': 'Save invoice',
        'Chọn khách hàng *': 'Select customer *',
        'Chọn khách hàng...': 'Select a customer...',
        'Khách hàng đã có': 'Existing customer',
        'Ngày lập': 'Invoice date',
        'Ngày lập *': 'Invoice date *',
        'Nhân viên': 'Employee',
        'Nhân viên phụ trách': 'Assigned employee',
        'NHÂN VIÊN PHỤ TRÁCH': 'ASSIGNED EMPLOYEE',
        'KHÁCH HÀNG': 'CUSTOMER',
        'Khách lẻ': 'Walk-in customer',
        'Không có số điện thoại': 'No phone number',
        'Chưa phân công': 'Unassigned',
        'Mã hóa đơn': 'Invoice ID',
        'Khách hàng / Đơn hàng': 'Customer / Order',
        'Đơn hàng': 'Order',
        'Đơn giá': 'Unit price',
        'Số lượng': 'Quantity',
        'Thành tiền': 'Subtotal',
        'Tổng tiền': 'Total',
        'Tổng thanh toán': 'Total payment',
        'Trạng thái': 'Status',
        'Tất cả trạng thái': 'All statuses',
        'Chưa thanh toán': 'Unpaid',
        'Đã thanh toán': 'Paid',
        'Đã ghi nhận': 'Recorded',
        'Chờ thanh toán': 'Awaiting payment',
        'Đang xử lý': 'Processing',
        'Hoàn thành': 'Completed',
        'Đã hủy': 'Cancelled',
        'Hủy': 'Cancel',
        'HÓA ĐƠN BÁN HÀNG': 'SALES INVOICE',
        'Hóa đơn chưa có sản phẩm.': 'This invoice has no products.',
        'In hóa đơn': 'Print invoice',
        'Chọn khách hàng và sản phẩm — đơn hàng sẽ được tạo tự động.':
            'Select a customer and products — the order will be created automatically.',
        'Ngày lập được hệ thống tự động ghi nhận và không thể chỉnh sửa.':
            'The invoice date is recorded automatically by the system and cannot be edited.',
        'Hóa đơn mới luôn ở trạng thái Chưa thanh toán.': 'New invoices always start as Unpaid.',
        'Danh sách này lấy trực tiếp từ kho. Tìm rồi bấm vào đúng mã, màu và size cần bán.':
            'This list comes directly from inventory. Find and select the correct code, color, and size.',
        'Chưa chọn sản phẩm. Hãy tìm và bấm vào sản phẩm ở phía trên.':
            'No product selected. Find and select a product above.',
        'Kho chưa có sản phẩm.': 'There are no products in inventory.',
        'Không tìm thấy sản phẩm phù hợp.': 'No matching products found.',
        'Bỏ sản phẩm': 'Remove product',
        'Tìm sản phẩm trong kho': 'Search inventory',
        'Quản lý khuyến mại': 'Promotion management',
        'Lập lịch giảm giá theo sản phẩm; hóa đơn tự áp dụng chương trình đang hiệu lực.':
            'Schedule product discounts; invoices automatically apply active promotions.',
        'Tạo mới': 'Create new',
        'Đang áp dụng': 'Active',
        'Sắp diễn ra': 'Scheduled',
        'Đã tắt': 'Disabled',
        'Tổng quan khuyến mại': 'Promotion overview',
        'Tạo khuyến mại': 'Create promotion',
        'Sửa khuyến mại': 'Edit promotion',
        'Tên khuyến mại *': 'Promotion name *',
        'Ví dụ: Cuối tuần giảm 15%': 'Example: 15% off this weekend',
        'Loại giảm *': 'Discount type *',
        'Theo phần trăm': 'Percentage',
        'Theo số tiền': 'Fixed amount',
        'Giá trị *': 'Value *',
        'Từ 0,01 đến 100%.': 'From 0.01 to 100%.',
        'Số tiền giảm sẽ không làm giá bán thấp hơn 0 đ.':
            'The discount will not reduce the sale price below 0 ₫.',
        'Bắt đầu *': 'Start *',
        'Kết thúc *': 'End *',
        'Kích hoạt chương trình': 'Enable promotion',
        'Sản phẩm áp dụng *': 'Applicable products *',
        'Tìm theo tên hoặc mã sản phẩm...': 'Search by product name or ID...',
        'Hủy sửa': 'Cancel editing',
        'Lưu khuyến mại': 'Save promotion',
        'Các chương trình': 'Promotions',
        'Đang chạy': 'Active',
        'Đã kết thúc': 'Ended',
        'Chưa có chương trình khuyến mại.': 'No promotions yet.',
        'Tắt khuyến mại': 'Disable promotion',
        'Bật khuyến mại': 'Enable promotion',
        'Thời gian kết thúc phải sau thời gian bắt đầu.':
            'The end time must be after the start time.',
        'Đã cập nhật trạng thái khuyến mại.': 'Promotion status updated.',
        'Có sản phẩm khuyến mại không còn tồn tại.':
            'One or more promotion products no longer exist.',
        'Một hoặc nhiều sản phẩm đã có khuyến mại trùng thời gian.':
            'One or more products already have an overlapping promotion.',
        'Khuyến mại không tồn tại.': 'The promotion no longer exists.',
        'Vui lòng nhập tên khuyến mại.': 'Enter a promotion name.',
        'Loại giảm giá không hợp lệ.': 'The discount type is invalid.',
        'Giá trị giảm phải lớn hơn 0.': 'The discount value must be greater than 0.',
        'Mức giảm phần trăm không được vượt quá 100%.':
            'The percentage discount cannot exceed 100%.',
        'Vui lòng chọn ít nhất một sản phẩm.': 'Select at least one product.',
        'Quản lý nhà cung cấp': 'Supplier management',
        'Tổng nhà cung cấp': 'Total suppliers',
        'Đang hợp tác': 'Active suppliers',
        'Tạm ngừng': 'Paused',
        'Nhập hàng tháng này': 'Purchases this month',
        'Chi tiết nhà cung cấp': 'Supplier details',
        'Thêm nhà cung cấp': 'Add supplier',
        'Sửa nhà cung cấp': 'Edit supplier',
        'Tên nhà cung cấp *': 'Supplier name *',
        'Tên': 'Name',
        'Hoạt động': 'Active',
        'Ngừng hợp tác': 'Inactive',
        'Báo cáo thống kê': 'Analytics report',
        'Tổng doanh thu': 'Total revenue',
        'Sản phẩm đã bán': 'Products sold',
        'Sản phẩm bán': 'Products sold',
        'Số đơn': 'Orders',
        'Mã báo cáo': 'Report ID',
        'Thời gian': 'Period',
        'Doanh thu': 'Revenue',
        'Doanh thu 6 tháng gần nhất': 'Revenue over the last 6 months',
        'Sản phẩm bán chạy': 'Best-selling products',
        'Báo cáo doanh thu gần đây': 'Recent revenue reports',
        '6 tháng gần nhất': 'Last 6 months',
        '12 tháng gần nhất': 'Last 12 months',
        'Năm nay': 'This year',
        'Tháng': 'Month',
        'Trợ lý cửa hàng': 'Store assistant',
        'Câu hỏi gợi ý': 'Suggested questions',
        'Phản hồi tự động': 'Automated response',
        'Vừa xong': 'Just now',
        'Giao diện': 'Appearance',
        'Chế độ sáng / tối': 'Light / dark mode',
        'Đang dùng chế độ sáng': 'Using light mode',
        'Đang dùng chế độ tối': 'Using dark mode',
        'Tự động theo giờ: hiện đang là chế độ sáng (06:00–17:59)':
            'Automatic by time: currently light mode (06:00–17:59)',
        'Tự động theo giờ: hiện đang là chế độ tối (18:00–05:59)':
            'Automatic by time: currently dark mode (18:00–05:59)',
        'Sáng': 'Light',
        'Tối': 'Dark',
        'Tự động': 'Automatic',
        'Cỡ hiển thị': 'Display size',
        'Nhỏ': 'Small',
        'Vừa': 'Medium',
        'Lớn': 'Large',
        'Thông báo': 'Notifications',
        'Cảnh báo tồn kho': 'Stock alerts',
        'Xác nhận trước khi xóa': 'Confirm before deleting',
        'Người dùng': 'User',
        'Quản lý': 'Manager',
        'Nhân viên': 'Employee',
        'Chọn giao diện phù hợp với môi trường làm việc.':
            'Choose an appearance that suits your work environment.',
        'Tùy chỉnh giao diện và cách hệ thống hoạt động trên thiết bị này.':
            'Customize the appearance and how the system works on this device.',
        'Chọn các cảnh báo cần hiển thị khi bán hàng.':
            'Choose which alerts to display while selling.',
        'Thay đổi kích thước chữ': 'Change the text size',
        'Khi sản phẩm còn từ 5 đôi': 'When a product has 5 pairs or fewer',
        'Hạn chế thao tác nhầm': 'Reduce accidental actions',
        'Điều hướng chính': 'Main navigation',
        'Chọn chế độ giao diện': 'Choose appearance mode',
        'Bật hoặc tắt cảnh báo tồn kho': 'Toggle stock alerts',
        'Bật hoặc tắt xác nhận trước khi xóa': 'Toggle delete confirmation',
        'Đặt lại mật khẩu tài khoản': 'Reset account password',
        'Chỉ quản lý được dùng chức năng này sau khi xác minh trực tiếp người yêu cầu.':
            'Only managers may use this feature after directly verifying the requester.',
        'Không thể đặt lại mật khẩu': 'Unable to reset password',
        'Đã đặt lại mật khẩu': 'Password reset',
        'Tài khoản cần đặt lại': 'Account to reset',
        'Chọn tài khoản': 'Select an account',
        'Tạm khóa': 'Locked',
        'Tối thiểu 8 ký tự; gửi mật khẩu qua kênh nội bộ an toàn.':
            'At least 8 characters; send the password through a secure internal channel.',
        'Nhập lại mật khẩu mới': 'Re-enter the new password',
        'Đặt lại mật khẩu': 'Reset password',
        'Mở khóa': 'Unlock',
        'Lựa chọn được đồng bộ giữa Cài đặt, Dashboard và các trang quản lý chính; tùy chọn được lưu tự động trên trình duyệt này.':
            'Preferences are synchronized across Settings, Dashboard, and management pages, and saved automatically in this browser.',
        'Mật khẩu phải có ít nhất 8 ký tự và không quá 72 byte':
            'The password must contain at least 8 characters and no more than 72 bytes',
        'Mật khẩu xác nhận không khớp': 'The password confirmation does not match',
        'Tài khoản cần đặt lại không còn tồn tại': 'The account to reset no longer exists',
        'Vui lòng chọn tài khoản cần mở khóa': 'Select an account to unlock',
        'Tài khoản cần mở khóa không còn tồn tại': 'The account to unlock no longer exists',
        'Tạo hóa đơn': 'Create invoice',
        'Thêm giày mới': 'Add new shoes',
        'Xuất báo cáo': 'Export report',
        'Doanh thu hôm nay': "Today's revenue",
        'Giày đã bán': 'Shoes sold',
        'Giao dịch gần đây': 'Recent transactions',
        'Cảnh báo tồn kho ít': 'Low-stock warning',
        'Cơ cấu danh mục sản phẩm': 'Product category breakdown',
        'Xem tất cả': 'View all',
        'Xem sản phẩm': 'View products',
        'Mã': 'ID',
        'Thao tác': 'Actions',
        'Xem chi tiết': 'View details',
        'Chỉnh sửa': 'Edit',
        'Lịch sử chỉnh sửa': 'Edit history',
        'Mỗi lần lưu lại hóa đơn đều ghi người sửa, thời gian và nội dung trước/sau.':
            'Every invoice update records the editor, time, and before/after content.',
        'Hóa đơn này chưa có lần chỉnh sửa nào.': 'This invoice has not been edited yet.',
        'Người chỉnh sửa': 'Editor',
        'Trước chỉnh sửa': 'Before',
        'Sau chỉnh sửa': 'After',
        'Thông tin trước chỉnh sửa': 'Information before editing',
        'Thông tin sau chỉnh sửa': 'Information after editing',
        'Thao tác hóa đơn': 'Invoice actions',
        'Xác nhận thanh toán': 'Confirm payment',
        'Thao tác thành công.': 'Action completed successfully.',
        'Không thể thực hiện thao tác.': 'Unable to complete the action.',
        'Đã thanh toán và cập nhật tồn kho.': 'Payment recorded and inventory updated.',
        'Nhân viên chỉ được chỉnh sửa hóa đơn do mình tạo trong phiên làm việc hiện tại.':
            'Employees may only edit invoices they created in the current work session.',
        'Không thể chỉnh sửa hóa đơn đã thanh toán.': 'Paid invoices cannot be edited.',
        'Xóa hóa đơn': 'Delete invoice',
        'Nhân viên chỉ được xóa hóa đơn do mình tạo trong phiên làm việc hiện tại.':
            'Employees may only delete invoices they created in the current work session.',
        'Xóa': 'Delete',
        'Lưu': 'Save',
        'Quay lại': 'Back',
        'Email': 'Email',
        'Thông tin tài khoản': 'Account information',
        'Tìm kiếm nhanh...': 'Quick search...',
        'Tìm tên, loại, màu...': 'Search by name, category, color...',
        'Tìm tên, số điện thoại...': 'Search by name or phone number...',
        'Tìm mã hóa đơn, nhân viên...': 'Search by invoice ID or employee...',
        'Tìm mã, tên, loại, màu hoặc size...': 'Search by code, name, category, color, or size...',
        'Nhập câu hỏi về sản phẩm, tồn kho hoặc hóa đơn...':
            'Ask about products, stock, or invoices...',
        'Nhập tên tài khoản': 'Enter your username',
        'Nhập mật khẩu': 'Enter your password',
        'Nhập lại mật khẩu mới': 'Re-enter the new password',
        'Tối thiểu 6 ký tự': 'At least 6 characters',
        'Ví dụ: 0911 111 111': 'Example: 0911 111 111',
        'Địa chỉ giao hàng (không bắt buộc)': 'Shipping address (optional)',
        'Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 0':
            'The phone number must contain exactly 10 digits and start with 0'
    };
    const originalTextNodes = new WeakMap();
    const originalAttributes = new WeakMap();
    let translatingDocument = false;

    function normalizeLanguage(value) {
        return value === 'en' ? 'en' : DEFAULT_LANGUAGE;
    }

    function getLanguage() {
        try {
            return normalizeLanguage(localStorage.getItem(STORAGE_KEY));
        } catch (error) {
            return DEFAULT_LANGUAGE;
        }
    }

    function translate(key, language = getLanguage()) {
        return messages[language][key] || messages[DEFAULT_LANGUAGE][key] || key;
    }

    function translateGenericText(value, language) {
        if (language === 'vi' || !value) {
            return value;
        }

        const direct = exactTextEn[value];
        if (direct) {
            return direct;
        }

        let match = value.match(/^Hiển thị (\d+) (sản phẩm|khách hàng|hóa đơn|nhà cung cấp)$/);
        if (match) {
            const nouns = {
                'sản phẩm': 'products',
                'khách hàng': 'customers',
                'hóa đơn': 'invoices',
                'nhà cung cấp': 'suppliers'
            };
            return `Showing ${match[1]} ${nouns[match[2]]}`;
        }
        match = value.match(/^Còn (\d+) đôi$/);
        if (match) {
            return `${match[1]} pairs left`;
        }
        match = value.match(/^Còn (\d+)$/);
        if (match) {
            return `${match[1]} left`;
        }
        match = value.match(/^(\d+) đôi$/);
        if (match) {
            return `${match[1]} pairs`;
        }
        match = value.match(/^(\d+) đơn$/);
        if (match) {
            return `${match[1]} orders`;
        }
        match = value.match(/^(\d+) khách$/);
        if (match) {
            return `${match[1]} customers`;
        }
        match = value.match(/^Đã chọn (\d+) sản phẩm$/);
        if (match) {
            return `${match[1]} products selected`;
        }
        match = value.match(/^(\d+) sản phẩm$/);
        if (match) {
            return `${match[1]} products`;
        }
        match = value.match(/^Đã lưu khuyến mại (.+)\.$/);
        if (match) {
            return `Promotion ${match[1]} saved.`;
        }
        match = value.match(/^Đã đặt lại mật khẩu cho tài khoản (@.+)$/);
        if (match) {
            return `Password reset for account ${match[1]}`;
        }
        match = value.match(/^Đã mở khóa đăng nhập cho tài khoản (@.+)$/);
        if (match) {
            return `Login unlocked for account ${match[1]}`;
        }
        match = value.match(/^(@\S+)( — (?:Quản lý|Nhân viên))?( — Tạm khóa)?$/);
        if (match) {
            const role = match[2] === ' — Quản lý'
                ? ' — Manager'
                : match[2] === ' — Nhân viên' ? ' — Employee' : '';
            return `${match[1]}${role}${match[3] ? ' — Locked' : ''}`;
        }
        match = value.match(/^· Phiên #(\d+)$/);
        if (match) {
            return `· Session #${match[1]}`;
        }
        match = value.match(/^Tháng (\d+)(\/\d{4})?$/);
        if (match) {
            return `Month ${match[1]}${match[2] || ''}`;
        }
        match = value.match(/^Trang (\d+) \/ (\d+)$/);
        if (match) {
            return `Page ${match[1]} / ${match[2]}`;
        }
        return value;
    }

    function translateTextNode(node, language) {
        if (!originalTextNodes.has(node)) {
            originalTextNodes.set(node, node.nodeValue);
        }
        const original = originalTextNodes.get(node);
        const match = original.match(/^(\s*)([\s\S]*?)(\s*)$/);
        if (!match || !match[2]) {
            return;
        }
        node.nodeValue = match[1] + translateGenericText(match[2], language) + match[3];
    }

    function translateAttributes(element, language) {
        if (element.id === 'languageToggleBtn') {
            return;
        }
        const attributes = ['placeholder', 'title', 'aria-label'];
        if (['BUTTON', 'INPUT'].includes(element.tagName)) {
            attributes.push('value');
        }

        if (!originalAttributes.has(element)) {
            originalAttributes.set(element, new Map());
        }
        const originals = originalAttributes.get(element);

        attributes.forEach((attribute) => {
            if (!element.hasAttribute(attribute)) {
                return;
            }
            if (!originals.has(attribute)) {
                originals.set(attribute, element.getAttribute(attribute));
            }
            element.setAttribute(
                attribute,
                translateGenericText(originals.get(attribute), language)
            );
        });
    }

    function translateGenericDocument(language, root = document.body) {
        if (!root) {
            return;
        }
        translatingDocument = true;
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        let node = walker.nextNode();
        while (node) {
            const parentTag = node.parentElement?.tagName;
            if (!['SCRIPT', 'STYLE', 'NOSCRIPT'].includes(parentTag)) {
                translateTextNode(node, language);
            }
            node = walker.nextNode();
        }

        if (root.nodeType === Node.ELEMENT_NODE) {
            translateAttributes(root, language);
        }
        root.querySelectorAll('*').forEach((element) => {
            translateAttributes(element, language);
        });
        translatingDocument = false;
    }

    function formatMetric(element, language) {
        const value = Number(element.dataset.value || 0);
        const number = new Intl.NumberFormat(language === 'en' ? 'en-US' : 'vi-VN', {
            maximumFractionDigits: 0
        }).format(value);
        const metric = element.dataset.metric;

        if (metric === 'currency') {
            element.textContent = `${number} ₫`;
            return;
        }
        if (metric === 'remainingPairs') {
            element.textContent = translate('unit.remainingPairs', language)
                .replace('{value}', number);
            return;
        }

        const suffixKey = {
            pairs: 'unit.pairs',
            orders: 'unit.orders',
            customers: 'unit.customers'
        }[metric];
        if (suffixKey) {
            element.textContent = `${number} ${translate(suffixKey, language)}`;
        }
    }

    function translateStatus(value, language) {
        if (language === 'vi') {
            return value || translate('status.unknown', language);
        }

        const normalized = (value || '').toLocaleLowerCase('vi');
        if (normalized.includes('đã thanh toán')) {
            return translate('status.paid', language);
        }
        if (normalized.includes('hoàn thành')) {
            return translate('status.completed', language);
        }
        if (normalized.includes('hủy')) {
            return translate('status.cancelled', language);
        }
        if (normalized.includes('chờ') || normalized.includes('xử lý')) {
            return translate('status.pending', language);
        }
        return translate('status.unknown', language);
    }

    function applyLanguage(value, persist = false) {
        const language = normalizeLanguage(value);
        document.documentElement.lang = language;
        document.title = translateGenericText(originalDocumentTitle, language);

        document.querySelectorAll('[data-i18n]').forEach((element) => {
            element.textContent = translate(element.dataset.i18n, language);
        });
        document.querySelectorAll('[data-i18n-placeholder]').forEach((element) => {
            element.placeholder = translate(element.dataset.i18nPlaceholder, language);
        });
        document.querySelectorAll('[data-i18n-title]').forEach((element) => {
            const text = translate(element.dataset.i18nTitle, language);
            element.title = text;
            element.setAttribute('aria-label', text);
        });
        document.querySelectorAll('[data-metric][data-value]').forEach((element) => {
            formatMetric(element, language);
        });
        document.querySelectorAll('[data-status]').forEach((element) => {
            element.textContent = translateStatus(element.dataset.status, language);
        });

        const languageButton = document.getElementById('languageToggleBtn');
        const languageFlag = document.getElementById('languageFlag');
        if (languageButton && languageFlag) {
            const targetIsEnglish = language === 'vi';
            languageFlag.src = targetIsEnglish
                ? languageButton.dataset.flagEn
                : languageButton.dataset.flagVi;
            const titleKey = targetIsEnglish
                ? 'language.toEnglish'
                : 'language.toVietnamese';
            languageButton.title = translate(titleKey, language);
            languageButton.setAttribute('aria-label', translate(titleKey, language));
        }

        if (persist) {
            try {
                localStorage.setItem(STORAGE_KEY, language);
            } catch (error) {
                // Keep the selected language for the current page if storage is unavailable.
            }
        }

        translateGenericDocument(language);
        window.dispatchEvent(new CustomEvent('app-language-change', {
            detail: { language }
        }));
        return language;
    }

    window.appI18n = {
        get: getLanguage,
        set(value) {
            return applyLanguage(value, true);
        },
        t: translate
    };
    window.dashboardI18n = window.appI18n;

    function initialize() {
        originalDocumentTitle = document.title;
        applyLanguage(getLanguage());
        const observer = new MutationObserver((mutations) => {
            if (translatingDocument) {
                return;
            }
            const language = getLanguage();
            mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === Node.TEXT_NODE) {
                        translateTextNode(node, language);
                    } else if (node.nodeType === Node.ELEMENT_NODE) {
                        translateGenericDocument(language, node);
                    }
                });
            });
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize, { once: true });
    } else {
        initialize();
    }

    window.addEventListener('storage', (event) => {
        if (event.key === STORAGE_KEY) {
            applyLanguage(event.newValue);
        }
    });
})();
