# Không gian quản trị — 07/11/2026

Frontend nằm trong `G5_FE1`, backend trong `G5_BE`. Mỗi màn có component và URL riêng:

| Màn | URL | File frontend | Quyền màn |
| --- | --- | --- | --- |
| Danh sách tài khoản | `/admin/users` | `src/admin/UserAccounts.jsx` | `USER_ACCOUNTS` |
| Xem thông tin tài khoản | `/admin/users/{id}/view` | `src/admin/UserDetailPage.jsx` | `USER_ACCOUNTS` |
| Cập nhật tài khoản | `/admin/users/{id}/edit` | `src/admin/UserEditPage.jsx` | `USER_ACCOUNTS` |
| Tạo / cấp tài khoản | `/admin/users/new` | `src/admin/UserCreatePage.jsx` | `USER_ACCOUNTS` |
| Vai trò và phân quyền | `/admin/permissions` | `src/admin/RolePermissionsPage.jsx` | `ROLE_PERMISSIONS` |
| Nhật ký hệ thống | `/admin/audit-logs` | `src/admin/SystemAuditPage.jsx` | `SYSTEM_AUDIT_LOG` |
| Cấu hình hệ thống | `/admin/settings` | `src/admin/SystemSettingsPage.jsx` | `SYSTEM_CONFIG` |

`AccountForm.jsx` dùng chung phần biểu mẫu tạo/sửa. `AdminUI.jsx`, `admin.css` và `useAdminData.js` cung cấp bố cục, bảng, hộp thoại và xử lý tải dữ liệu.

Sau khi tạo thành công, giao diện mở trang cập nhật của tài khoản vừa tạo và hiển thị **Tạo tài khoản thành công.** Thông báo được giữ qua lần chuyển trang này, xóa khi bắt đầu chỉnh sửa hoặc chuyển sang trang khác. Số điện thoại không bắt buộc; dữ liệu `null` hoặc thiếu trường từ API được hiển thị thành ô trống và vẫn lưu được. Nếu nhập số điện thoại, định dạng vẫn được kiểm tra.

## Tài khoản

Danh sách hiển thị số liệu từ database, tìm theo mã `TK-0001`, họ tên, email hoặc điện thoại; lọc theo vai trò, trạng thái và phân trang. Có hai thao tác riêng: **Xem** mở màn chi tiết chỉ đọc, **Chỉnh sửa** mở biểu mẫu cập nhật. Các ô mật khẩu chỉ xuất hiện khi mở mục đổi mật khẩu tùy chọn ở trang sửa; mã tài khoản chỉ đọc.

Màn xem dùng `GET /api/admin/users/{id}/profile`, có ba tab **Thông tin tài khoản**, **Vai trò**, **Lịch sử thao tác**. Hiển thị dữ liệu mới nhất, các màn được cấp cho vai trò, ngày đăng nhập gần nhất và lịch sử liên quan. Không có input, select, form lưu hoặc điều khiển khóa/vô hiệu hóa trong màn xem. Nút **Chỉnh sửa tài khoản** điều hướng sang trang sửa riêng; **Quay về danh sách** không ghi dữ liệu.

Ngày tạo lấy từ sự kiện tạo tài khoản; ngày cập nhật lấy từ các thay đổi hồ sơ đã ghi nhận. Tài khoản cũ không có sự kiện tương ứng hiển thị **Chưa ghi nhận**. Lịch sử tối đa 20 hoạt động, gồm thay đổi nhắm đến tài khoản này và hoạt động đăng nhập/đăng xuất/cập nhật hồ sơ/đổi mật khẩu của chính tài khoản. Không mở nhật ký toàn hệ thống hoặc thao tác với tài khoản khác qua màn xem. API không trả mật khẩu, hash, session hay mã khôi phục. Chức năng xem không thêm migration.

Trang sửa lưu thông tin tài khoản, trạng thái và ghi chú trong một transaction qua `PUT /api/admin/users/{id}/details`. Dùng `version` từ GET chi tiết để tránh ghi đè cập nhật của người khác; xung đột trả `409`, giữ dữ liệu người dùng đang nhập. `changeNote` được lưu vào nhật ký. Để trống mật khẩu để giữ nguyên; đổi email, mật khẩu, role hoặc trạng thái thu hồi phiên và mã khôi phục cũ như trước.

Trạng thái gồm `ACTIVE`, `DISABLED`, `LOCKED`, `DISABLED_LOCKED`. Khóa và vô hiệu hóa là hai cờ độc lập. Không cho tự đổi role, khóa hoặc vô hiệu hóa tài khoản đang dùng; giữ ít nhất một Admin hoạt động. Chỉ xóa được tài khoản không có dữ liệu nghiệp vụ; thao tác xóa cần xác nhận. Tổng tài khoản không đổi khi đổi trạng thái, số vô hiệu hóa và bị khóa có thể cùng đếm một tài khoản.

## Phân quyền bằng mã màn

Migration V8 tạo `m_function` (mã chức năng, mã màn hiển thị, tên, URL) và bảng liên kết `role_function(role_id, function_code)`. Khóa chính ghép ngăn cấp quyền trùng. `permission_revision` lưu phiên bản ma trận để chặn thao tác lưu trên dữ liệu cũ.

Các mã hiện có: `DASHBOARD` (`FE-01`), `USER_ACCOUNTS` (`FE-02`), `ROLE_PERMISSIONS` (`FE-03`), `SYSTEM_CONFIG` (`FE-04`), `SYSTEM_AUDIT_LOG` (`FE-05`), `PROFILE` (`FE-06`), `RECRUITMENT` (`FE-07`).

Mặc định tất cả 7 role có Dashboard theo role và trang cá nhân; Admin có 4 màn quản trị; HR và Manager có màn tuyển dụng. Màn tuyển dụng tiếp tục giới hạn HR/Manager vì quy trình và phạm vi dữ liệu đã được thiết kế cho hai role này. Ma trận khóa các ô không phù hợp. Các màn quản trị có thể được cấp cho role khác một cách rõ ràng qua ma trận; API cũng kiểm tra quyền mới, không chỉ hiện menu.

`GET /api/auth/permissions` trả quyền hiện tại. `ScreenAccessConfiguration` kiểm tra quyền từ database ở từng request, nên thu hồi quyền tác động lên phiên đã đăng nhập. Frontend lọc menu, kiểm tra URL và tải lại quyền khi chuyển trang hoặc trở lại cửa sổ. Dashboard luôn là dashboard của chính role; cấp quyền màn không mở dữ liệu lớp/học viên/nghiệp vụ ngoài phạm vi.

Hai quyền `USER_ACCOUNTS` và `ROLE_PERMISSIONS` của Admin được giữ để tránh mất đường quản lý/khôi phục hệ thống. POST/PUT/PATCH/DELETE vẫn yêu cầu CSRF. Các kiểm tra `role_id`/mã chuẩn 1–7 và khóa/vô hiệu hóa tài khoản tiếp tục được áp dụng.

## Nhật ký

`OperationAuditFilter` ghi metadata của các thao tác POST/PUT/PATCH/DELETE: tài khoản, phân quyền, cấu hình, thông tin cá nhân, mật khẩu, khôi phục, đăng nhập/đăng xuất, tư vấn và tuyển dụng. Ghi cả thành công và thất bại, HTTP status, người thao tác, role, mã đối tượng, thời gian và ghi chú sửa tài khoản. Không ghi request body, mật khẩu, hash, OTP hoặc token.

`system_audit_log` giữ tên và role người thực hiện tại thời điểm thao tác. Xóa người dùng chỉ đặt FK tác giả về NULL, không xóa lịch sử. V8 sao chép nhật ký đăng nhập và sự kiện tuyển dụng cũ vào bảng mới. Dashboard Admin đọc bảng nhật ký chung; biểu đồ đăng nhập vẫn dùng `user_login_events` để giữ đúng ý nghĩa.

Màn nhật ký có tìm kiếm, lọc loại thao tác/ngày, phân trang và hộp thoại chi tiết. Các chỉ số phía trên tính cho hôm nay theo `Asia/Ho_Chi_Minh`; danh sách tính theo bộ lọc được chọn. Các API nhật ký chỉ đọc, không có thao tác xóa/sửa lịch sử trong giao diện.

## Cấu hình

`system_settings` lưu tên đơn vị, email thông báo, ngôn ngữ mặc định, phiên bản và người/thời gian cập nhật. Lưu cấu hình có kiểm tra phiên bản và tạo nhật ký. Tên đơn vị cập nhật nhãn trung tâm trên giao diện; email thông báo được dùng làm Reply-To của email khôi phục; tiêu đề và chữ ký email dùng tên đơn vị. Địa chỉ From vẫn là tài khoản gửi SMTP đã cấu hình.

Múi giờ và thời hạn khôi phục hiển thị đúng chính sách hiện hành. Hiện giao diện hỗ trợ tiếng Việt. Mật khẩu SMTP được giữ trong cấu hình môi trường/backend, không được trả về màn cấu hình. Trạng thái **Đã cấu hình** chỉ nghĩa là có thông số SMTP; nút **Kiểm tra kết nối email** thực hiện kết nối/xác thực mà không gửi thư thử. Thất bại không trả chi tiết chứa thông tin đăng nhập.

Banking hiện chưa có tích hợp kết nối trong repo nên hiển thị **Chưa cấu hình**. Không dùng trạng thái kết nối hoặc số liệu mẫu trong ảnh như dữ liệu thật.

## Cập nhật database và chạy

Khởi động lại backend để Flyway áp dụng `V8__administration_workspace.sql`, sau đó tải lại frontend. V8 giữ dữ liệu người dùng, hash mật khẩu, các bảng nghiệp vụ và bộ 7 role; thêm bảng, chỉ mục và cột `m_user.admin_version`. Không sửa migration V1–V7 đã chạy.

```powershell
mvn -f G5_BE/pom.xml spring-boot:run
npm.cmd --prefix G5_FE1 run dev
```

Không cần nhập số liệu từ ảnh vào database. Cấu hình Gmail và mật khẩu ứng dụng: [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).
