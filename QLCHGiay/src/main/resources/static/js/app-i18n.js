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
        'Cài đặt': 'Settings',
        'Đăng xuất': 'Log out',
        'Đăng nhập': 'Log in',
        'Tên đăng nhập': 'Username',
        'Mật khẩu': 'Password',
        'Mật khẩu mới': 'New password',
        'Xác nhận mật khẩu': 'Confirm password',
        'Ghi nhớ đăng nhập': 'Remember me',
        'Quên mật khẩu?': 'Forgot password?',
        'Đặt lại mật khẩu': 'Reset password',
        'Cập nhật mật khẩu': 'Update password',
        'Quay lại đăng nhập': 'Back to login',
        'Hệ thống Quản lý Cửa hàng Giày Sneaker': 'Sneaker Store Management System',
        'Xác minh tài khoản bằng số điện thoại của nhân viên, sau đó tạo mật khẩu mới.':
            'Verify the account with the employee phone number, then create a new password.',
        'Số điện thoại xác minh': 'Verification phone number',
        'Số điện thoại mẫu của tài khoản admin: 0911111111':
            'Sample phone number for the admin account: 0911111111',
        'Sai tên đăng nhập hoặc mật khẩu': 'Incorrect username or password',
        'Đặt lại mật khẩu thành công': 'Password reset successfully',
        'Thông tin không chính xác': 'The information is incorrect',
        'Quản lý sản phẩm': 'Product management',
        'Tổng sản phẩm': 'Total products',
        'Sản phẩm': 'Product',
        'Tên sản phẩm': 'Product name',
        'Tên sản phẩm *': 'Product name *',
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
        'Màu': 'Color',
        'Màu sắc': 'Color',
        'Chất liệu': 'Material',
        'Giá': 'Price',
        'Giá bán': 'Sale price',
        'Giá bán (VNĐ) *': 'Sale price (VND) *',
        'Size': 'Size',
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
        'Đang xử lý': 'Processing',
        'Hoàn thành': 'Completed',
        'Đã hủy': 'Cancelled',
        'Hủy': 'Cancel',
        'HÓA ĐƠN BÁN HÀNG': 'SALES INVOICE',
        'Hóa đơn chưa có sản phẩm.': 'This invoice has no products.',
        'In hóa đơn': 'Print invoice',
        'Chọn khách hàng và sản phẩm — đơn hàng sẽ được tạo tự động.':
            'Select a customer and products — the order will be created automatically.',
        'Chọn ngày từ lịch, không nhập thủ công': 'Choose a date from the calendar; do not type it manually',
        'Hóa đơn mới luôn ở trạng thái Chưa thanh toán.': 'New invoices always start as Unpaid.',
        'Danh sách này lấy trực tiếp từ kho. Tìm rồi bấm vào đúng mã, màu và size cần bán.':
            'This list comes directly from inventory. Find and select the correct code, color, and size.',
        'Chưa chọn sản phẩm. Hãy tìm và bấm vào sản phẩm ở phía trên.':
            'No product selected. Find and select a product above.',
        'Kho chưa có sản phẩm.': 'There are no products in inventory.',
        'Không tìm thấy sản phẩm phù hợp.': 'No matching products found.',
        'Bỏ sản phẩm': 'Remove product',
        'Tìm sản phẩm trong kho': 'Search inventory',
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
        const languageCode = document.getElementById('languageCode');
        const languageFlag = document.getElementById('languageFlag');
        if (languageButton && languageCode && languageFlag) {
            const targetIsEnglish = language === 'vi';
            languageCode.textContent = targetIsEnglish ? 'EN' : 'VI';
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
