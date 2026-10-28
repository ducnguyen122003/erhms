# Dashboard theo vai trò

Frontend đặt tại `G5_FE1`; backend tại `G5_BE/src/main/java/com/erhms/dashboard`. Bảy trang dashboard nằm ở bảy file riêng trong `G5_FE1/src/dashboards`. `RoleWorkspace.jsx` quản lý URL, menu, tải dữ liệu, tài khoản và phiên đăng nhập; `roles.js` xác định dashboard theo cặp role ID/mã.

## Khởi động và gán role

Chạy backend và frontend theo README. Flyway mặc định áp dụng migration đến V6 trước khi Hibernate kiểm tra schema. V5 thêm trạng thái tài khoản và bảng nghiệp vụ; dữ liệu nghiệp vụ mới ban đầu trống. V6 chuyển đúng bộ role V5 cũ sang ID cố định 1–7 và chuyển liên kết tài khoản theo role tương ứng, giữ ID user, mật khẩu và dữ liệu nghiệp vụ. Bộ role đã được sửa đúng 1–7 được giữ nguyên; bộ role không khớp mẫu V5 cũ không được tự sửa/cấp quyền.

Kiểm tra role hiện có trong PostgreSQL:

```sql
SELECT role_id, code, name FROM m_role ORDER BY role_id;
SELECT u.user_id, u.email, u.full_name, r.role_id, r.code
FROM m_user u JOIN m_role r ON r.role_id = u.role_id
ORDER BY u.user_id;
```

Chỉ chấp nhận chính xác các cặp dưới đây, phân biệt chữ hoa/thường:

| role_id | code | Dashboard |
| --- | --- | --- |
| 1 | `ADMIN` | Tổng quan hệ thống |
| 2 | `LECTURER` | Giảng viên |
| 3 | `STUDENT` | Học viên |
| 4 | `ACCOUNTANT` | Tài chính đào tạo |
| 5 | `IT_SUPPORT` | Thiết bị và hỗ trợ |
| 6 | `MANAGER` | Quản lý học viên |
| 7 | `HR` | Tuyển sinh và tuyển dụng |

Để gán role giảng viên cho tài khoản, thay email trong ví dụ dưới đây rồi chạy trong Query Tool và đăng xuất/đăng nhập lại. Với role khác, chọn đúng cặp ID/mã trong bảng:

```sql
UPDATE m_user
SET role_id = 2
WHERE email = 'email-tai-khoan-can-gan-role@example.com'
  AND EXISTS (SELECT 1 FROM m_role WHERE role_id = 2 AND code = 'LECTURER');
```

ID ngoài 1–7, thiếu role hoặc mã không khớp ID bị từ chối `403` với thông báo: **“Vai trò không hợp lệ. Tài khoản phải có role_id từ 1 đến 7 và mã role tương ứng.”** Không nhận `Lecture`, `TEACHER`, `USER`, `EMPLOYER_HR`, `SERVICE_MANAGER`, chữ thường hoặc tiền tố `ROLE_` khi đăng nhập/API. Ví dụ ID 10 dù có mã `STUDENT` vẫn lỗi; ID 1 có mã `HR` cũng lỗi. Đăng nhập sai mật khẩu vẫn trả lỗi thông tin đăng nhập `401`; chỉ kiểm tra lỗi role sau khi mật khẩu đúng. Tài khoản role sai không được tạo phiên đăng nhập hoặc sự kiện đăng nhập thành công. Nếu role trong database đổi thành sai sau khi đã đăng nhập, API bảo vệ trả `403` ở lần gọi tiếp theo.

V5 thêm `m_user.account_enabled` mặc định `true`, `account_locked` mặc định `false`. Tài khoản bị tắt/khóa không đăng nhập được và không dùng phiên đã có để đọc dữ liệu. Mỗi lần đăng nhập thành công tạo một dòng `user_login_events` để dashboard admin dùng số liệu thực tế.

## Nguồn số liệu

| Dashboard | Nguồn và cách tính chính |
| --- | --- |
| Admin | `m_user`, `m_role`, `user_login_events`, `request_events`: số tài khoản, được phép đăng nhập, bị khóa; nhật ký và số tài khoản đăng nhập theo ngày. |
| Giảng viên | `training_classes.lecturer_id` bằng user hiện tại; học viên ghi danh trong lớp, buổi dạy, buổi chưa chốt điểm danh, điểm chưa công bố; yêu cầu thiết bị do user tạo. |
| Học viên | `training_enrollments.student_id` bằng user hiện tại; lịch lớp đã ghi danh, điểm đã công bố, điểm danh, hợp đồng và thiết bị của user. Không nhận student ID từ client. |
| Kế toán | `financial_transactions`, `training_contracts`, `equipment_incidents`: học phí đã xác nhận tháng này, cọc đã nhận trừ khoản hoàn, công nợ từng hợp đồng, bồi thường chưa giải quyết. |
| IT Support | `equipment_assets`, `equipment_requests`, `equipment_loans`, `equipment_incidents`: tài sản, sẵn sàng cấp phát, đang bàn giao/chờ thu hồi, sự cố chưa xử lý. |
| Quản lý | `training_classes`, `training_enrollments`, `training_sessions`, `training_attendance`: học viên/lớp đang hoạt động, chờ xác nhận xếp lớp, hồ sơ thiếu, trùng lịch giảng viên/phòng, chuyên cần dưới 80%. |
| HR | `consultation_registrations`, `consultation_followups`, `training_contracts`: đăng ký từ web, hồ sơ tư vấn, lịch hẹn, hợp đồng nháp và hợp đồng tạo trong tuần hiện đã ký. |

Quan hệ dữ liệu bổ sung trong V5:

- `training_classes` → `training_sessions` và `training_enrollments`; lớp liên kết giảng viên qua `lecturer_id`.
- Ghi danh liên kết học viên qua `student_id`; `training_grades.enrollment_id` và `training_attendance.session_id` chứa kết quả học tập.
- `training_contracts.student_id` liên kết học viên, có thể liên kết đăng ký tư vấn qua `consultation_id`; `financial_transactions.contract_id` chứa các giao dịch học phí/cọc/hoàn cọc/bồi thường.
- `equipment_loans` liên kết tài sản và học viên; `equipment_requests.requested_by` là người tạo yêu cầu; `equipment_incidents` lưu sự cố và mức bồi thường.
- `consultation_followups.consultation_id` bổ sung trạng thái tư vấn và lịch hẹn cho đăng ký từ website.

Mỗi bản ghi nghiệp vụ dùng UUID; các liên kết user dùng `m_user.user_id` BIGINT hiện có. Chi tiết cột, CHECK và trạng thái hợp lệ nằm trong `G5_BE/src/main/resources/db/migration/V5__role_dashboards.sql`. Phần thay đổi này triển khai dashboard đọc dữ liệu; các màn tạo/sửa lớp, giao dịch và thiết bị chưa nằm trong phạm vi. Có thể nhập dữ liệu thật theo schema để dashboard cập nhật khi bấm **Cập nhật dữ liệu**.

Dashboard admin tự tải lại sau khi tạo/sửa/xóa, vô hiệu hóa/kích hoạt lại hoặc khóa/mở khóa tài khoản thành công; quay về dashboard và đưa cửa sổ Admin trở lại cũng tải số liệu mới. Tổng tài khoản giữ nguyên khi đổi trạng thái. Đang hoạt động đếm tài khoản được bật và không khóa; số bị khóa và số vô hiệu hóa đếm độc lập nên một tài khoản có thể nằm trong cả hai nhóm. Xem thao tác quản trị trong [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).

Không chèn số liệu trong ảnh vào frontend hoặc migration. Khi chưa có dữ liệu: số đếm/số tiền là 0, bảng hiển thị trạng thái trống, điểm trung bình chưa công bố là `null` và giao diện hiển thị `—`. Tiến độ là số buổi đã diễn ra chia tổng buổi của lớp; số buổi vắng là dữ liệu điểm danh riêng, không suy ra số buổi có mặt từ những buổi chưa điểm danh. Các mốc ngày/tuần/tháng dùng múi giờ Việt Nam; biểu đồ hiển thị 7 ngày gần nhất.

## API

Yêu cầu đăng nhập bằng session cookie:

```text
GET /api/dashboard
GET /api/dashboard/admin
GET /api/dashboard/lecturer
GET /api/dashboard/student
GET /api/dashboard/accountant
GET /api/dashboard/it-support
GET /api/dashboard/manager
GET /api/dashboard/hr
```

`/api/dashboard` xác định role từ `role_id` và mã của user trong database. Endpoint theo slug chỉ cho phép role tương ứng, kể cả admin cũng chỉ mở dashboard admin. `401`: chưa đăng nhập hoặc phiên hết hiệu lực sau khi admin đổi thông tin/trạng thái tài khoản; `403`: role không hợp lệ, không phù hợp dashboard hoặc tài khoản bị khóa/tắt trực tiếp trong database mà không tăng phiên bản; `404`: slug không tồn tại.

Response chung:

```text
role, updatedAt
stats[]: key, label, value (number|null), unit, description, tone
highlights[]: cấu trúc giống stats
panels[]: key, title, description, columns[], rows[]
  row: id, cells[], status, statusTone, detail (tùy chọn)
tasks[]: key, title, description, count, tone
chart: title, description, points[{label, value}]
progress (chỉ học viên): completed, total, absent, endDate
```

Các panel trả tối đa 6 bản ghi gần nhất; số đếm tổng quan được truy vấn trên toàn bộ phạm vi dữ liệu, không tính từ 6 dòng hiển thị. Bấm tên dòng hoặc **Chi tiết** để xem dữ liệu API trả về. Menu điều hướng tới từng phần dashboard có dữ liệu; HR/manager còn có màn nhu cầu tuyển dụng hiện hữu. Hồ sơ và đổi mật khẩu tiếp tục dùng API cũ.

## Kiểm tra

```powershell
mvn -f .\G5_BE\pom.xml test
npm.cmd --prefix .\G5_FE1 test
npm.cmd --prefix .\G5_FE1 run build
```

`DashboardIntegrationTest` kiểm tra đăng nhập đúng bảy cặp ID/mã, 42 cặp URL khác role, từ chối alias/mã sai hoa thường/ID ngoài 1–7/mã lệch ID, mật khẩu sai trước lỗi role, không tạo session/sự kiện khi role sai và chặn phiên cũ sau khi role bị thay đổi. Ngoài ra có kiểm tra user chưa đăng nhập, role database ghi đè authority giả từ client, khóa tài khoản, dữ liệu riêng giảng viên/học viên và các số liệu từ bản ghi được tạo trong test. Test dùng H2 PostgreSQL mode, các bản ghi test được rollback. `FreshDatabaseMigrationTest` kiểm tra migration chạy một lần và bảo toàn dữ liệu cũ, chuyển đúng bộ role V5 cũ và giữ nguyên bộ role đã chuẩn hóa.

Các test frontend trong `G5_FE1/tests/roles.test.js` kiểm tra đúng cặp role ID/mã, từ chối ID hoặc mã không hợp lệ, đường dẫn mặc định, tải lại, URL khác role và giới hạn truy cập nghiệp vụ tuyển dụng.
