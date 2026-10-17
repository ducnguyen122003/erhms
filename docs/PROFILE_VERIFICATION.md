# Kiểm chứng thông tin cá nhân — 17/10/2026

## Cập nhật khóa email và đổi mật khẩu

- Backend Maven package đạt 54 tests, không failures/errors/skipped; FE1 production build thành công.
- Email chỉ đọc và vẫn có thể focus/sao chép. Profile PUT chỉ gửi `fullName`, `phone`; DTO/backend không cập nhật email khi client gửi thêm khóa này.
- Thêm `POST /api/auth/change-password`, yêu cầu mật khẩu hiện tại đúng, mật khẩu mới ít nhất 8 ký tự Unicode/tối đa 72 byte UTF-8, xác nhận khớp chính xác. Giữ nguyên khoảng trắng và hoa/thường.
- 12 integration tests mật khẩu bao phủ mật khẩu cũ sai, thiếu trường, xác nhận lệch, Unicode, giới hạn BCrypt, CSRF/phân quyền, đồng thời hai lần đổi mật khẩu và lưu hash cho đúng tài khoản.
- Chrome thực trên PostgreSQL: cập nhật tên/điện thoại thành công; email giữ nguyên. Sai mật khẩu cũ trả 400, nhập lệch/không hợp lệ bị chặn trên form; mật khẩu đúng trả 204 và xóa cả ba ô nhập. Phiên hiện tại vẫn đăng nhập; sau logout, mật khẩu cũ trả 401 và mật khẩu mới đăng nhập thành công.
- SQL qua kết nối riêng xác nhận hash BCrypt đã được lưu cho tài khoản kiểm thử. Log backend không chứa các giá trị mật khẩu của phiên kiểm thử; DTO che mật khẩu và validation không đưa mật khẩu vào lỗi binding.
- Mobile 390px không tràn ngang, cả ba ô mật khẩu hiển thị đúng. Không có JavaScript runtime exception.
- Đã xóa đúng tài khoản giả theo ID; so sánh số lượng/hash dữ liệu xác nhận tài khoản và mật khẩu của người dùng hiện có không thay đổi. Đã đóng Chrome và server kiểm thử riêng 8083/8085. Tính năng này không cần migration mới.

## Kiểm chứng hồ sơ trước khi khóa email

- Backend Maven package đạt 41 tests, không failures/errors/skipped. Trong đó có 9 integration tests hồ sơ và 1 unit test cho cập nhật kết thúc sau logout.
- FE1 production build thành công.
- Migration V4 đã áp dụng vào PostgreSQL `erhms`, thêm `m_user.phone VARCHAR(20)` nullable. Kiểm tra số lượng và hash các trường hiện có xác nhận 3 tài khoản cũ được giữ nguyên.
- Chrome thực: đăng nhập tài khoản kiểm thử, bấm avatar/tên cạnh logout, mở form và cập nhật email, họ tên, điện thoại. Nhận PUT 200, sidebar đổi tên ngay và kết nối SQL riêng xác nhận ba trường đã commit.
- Đổi email vẫn giữ phiên hiện tại; tải lại trang giữ dữ liệu; logout và đăng nhập bằng email mới với mật khẩu cũ thành công.
- Số điện thoại không hợp lệ bị chặn trước khi gửi API. Backend kiểm tra dữ liệu, email trùng, CSRF, và chỉ cập nhật tài khoản hiện tại.
- Phiên được xác định bằng user ID cố định; test hai phiên đồng thời xác nhận tái sử dụng email cũ không làm phiên cũ chuyển sang tài khoản khác.
- Chrome kiểm tra phản hồi PUT bị trì hoãn tới sau logout: giao diện giữ trạng thái đăng xuất, GET `/api/auth/me` trả 401. Unit test xác nhận controller không tạo lại session đã logout.
- Giao diện mobile 390px: nút hồ sơ 42×42px hiển thị và bấm được; không tràn ngang. Không có JavaScript runtime exception.
- Tài khoản giả đã được xóa theo đúng ID và thông tin kiểm thử. Đã đóng Chrome và các server kiểm thử riêng; các server của người dùng không bị dừng.

Phiên kiểm thử dùng backend 8083, frontend 8085 proxy về backend này. Cấu hình mặc định vẫn là FE1 8082 và backend 8080. Cấu hình tắt Flyway của người dùng được giữ nguyên; phiên kiểm thử bật Flyway bằng tham số chạy để áp dụng V4. Cần khởi động lại backend trong IDE và tải lại FE1 để nhận tính năng mới.
