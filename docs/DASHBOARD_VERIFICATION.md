# Kiểm tra dashboard — 28/10/2026

- Backend: `mvn -o '-Dmaven.repo.local=C:/Users/FPT SHOP/.m2/repository' -f G5_BE/pom.xml test` thành công: **87 test, 0 lỗi, 0 thất bại, 0 bỏ qua**. Log lần chạy tại `G5_BE/target/strict-role-test.log`.
- `DashboardIntegrationTest`: **30 test**, bao gồm đăng nhập bảy cặp ID/mã chuẩn, 42 cặp URL dashboard khác role, số liệu từ database và phạm vi dữ liệu giảng viên/học viên.
- Kiểm tra quyền mới: ID ngoài 1–7 dù có mã chuẩn bị từ chối; mã chuẩn nằm ở sai ID, alias cũ và mã sai hoa thường cũng bị từ chối. Login trả `403`, mã lỗi `INVALID_ROLE`, thông báo tiếng Việt; không tạo security context hoặc sự kiện đăng nhập thành công.
- Mật khẩu sai trả `401` trước khi kiểm tra lỗi role. Session đã đăng nhập bị chặn khi role ID hoặc mã trong database đổi thành không hợp lệ. Authority giả từ client không thay đổi quyền trong database.
- `FreshDatabaseMigrationTest`: **10 test**, bao gồm chuyển bộ role V5 cũ sang ID 1–7, giữ user ID/mật khẩu/liên kết nghiệp vụ, giữ nguyên bộ role được sửa đúng thủ công và giữ nguyên ID/mã ngoài bộ chuẩn để runtime từ chối. Flyway chạy lại không áp dụng migration đã hoàn tất.
- Các test còn lại xác minh tài khoản demo sau migration dùng `HR` (7)/`MANAGER` (6), quyền sở hữu/duyệt yêu cầu tuyển dụng, hồ sơ, đổi mật khẩu, tư vấn và CSRF.
- Frontend: **7/7 test** phân quyền thành công; production build thành công. Kiểm tra toàn bộ bảy cặp ID/mã, 42 URL khác role, ID ngoài phạm vi/thiếu/sai kiểu, mã không khớp và alias cũ.
- Chrome headless với API fixture: bảy dashboard hợp lệ hoạt động trên desktop/mobile; form đăng nhập, tải lại, đăng xuất, chi tiết và lỗi API hoạt động. `roleId=8` dù có mã `ADMIN`, hoặc ID 1 với mã `STUDENT`, đều báo lỗi trước khi gọi API dashboard. Lỗi `INVALID_ROLE` từ phiên cũ cho phép về màn đăng nhập; lỗi role khi login giữ người dùng ở form với thông báo rõ ràng. Không có runtime exception.

Backend tests chạy các migration thật trên H2 ở chế độ PostgreSQL. Lần kiểm tra này không áp dụng migration hoặc sửa tài khoản trong PostgreSQL đang dùng; các thay đổi role và dữ liệu nghiệp vụ thử nghiệm được tạo trong database kiểm thử. Kết quả H2 chưa thay thế kiểm thử migration trên PostgreSQL thực tế của người dùng.

API fixture trình duyệt chỉ dùng trong phiên kiểm thử, không đưa vào source ứng dụng hoặc database.
