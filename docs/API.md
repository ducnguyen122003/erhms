# REST API
Base URL qua web quản lý: `http://localhost:8082/api`; qua web tư vấn: `http://localhost:8081/api`; trực tiếp BE: `http://localhost:8080/api`.

| Method | Path | Mô tả |
|---|---|---|
| GET | /auth/csrf | Lấy token/headerName và session cookie |
| POST | /public/consultations | Đăng ký tư vấn công khai, không cần đăng nhập; cần CSRF header |
| POST | /auth/login | Form URL encoded: username, password; thêm CSRF header |
| GET | /auth/me | Tài khoản hiện tại, không trả password hash |
| PUT | /auth/me | Cập nhật họ tên, điện thoại của tài khoản đang đăng nhập; email không thay đổi; cần CSRF header |
| POST | /auth/change-password | Đổi mật khẩu bằng mật khẩu hiện tại, mật khẩu mới và xác nhận; cần CSRF header |
| POST | /auth/password-reset/request | Công khai: yêu cầu mã 6 chữ số qua email; cần CSRF header |
| POST | /auth/password-reset/verify | Công khai: xác nhận email và mã; trả reset token ngắn hạn; cần CSRF header |
| POST | /auth/password-reset/complete | Công khai: đặt mật khẩu bằng reset token và phần xác nhận; cần CSRF header |
| POST | /auth/logout | Hủy phiên; thêm CSRF header |
| GET | /admin/roles | ADMIN: các cặp ID/mã role hợp lệ thực tế trong database |
| GET | /admin/users?q=&roleId=&page=0&size=10 | ADMIN: tìm kiếm, lọc role và phân trang tài khoản |
| GET | /admin/users/{id} | ADMIN: xem chi tiết tài khoản mới nhất, không trả mật khẩu/hash |
| POST | /admin/users | ADMIN: tạo tài khoản với họ tên, email, điện thoại, quyền hạn, mật khẩu và xác nhận; cần CSRF header |
| PUT | /admin/users/{id} | ADMIN: sửa thông tin tài khoản và tùy chọn đổi mật khẩu; cần CSRF header |
| PATCH | /admin/users/{id}/status | ADMIN: vô hiệu hóa hoặc bật lại tài khoản, giữ dữ liệu; cần CSRF header |
| DELETE | /admin/users/{id} | ADMIN: xóa tài khoản không có dữ liệu nghiệp vụ liên quan; cần CSRF header |
| GET | /dashboard | Dashboard theo role của tài khoản trong database |
| GET | /dashboard/{slug} | Dashboard đúng role: admin, lecturer, student, accountant, it-support, manager, hr; khác role trả 403 |
| GET | /recruitment-requests?q=&status=&page=0&size=10 | Tìm kiếm/phân trang trong phạm vi quyền; bỏ status nếu không lọc |
| POST | /recruitment-requests | Nhân sự tạo bản nháp |
| GET | /recruitment-requests/{id} | Chi tiết |
| PUT | /recruitment-requests/{id} | Sửa bản nháp hoặc yêu cầu cần sửa |
| POST | /recruitment-requests/{id}/submit | Nhân sự gửi duyệt |
| POST | /recruitment-requests/{id}/approve | Quản lý duyệt |
| POST | /recruitment-requests/{id}/request-revision | Quản lý yêu cầu sửa, cần note |
| POST | /recruitment-requests/{id}/close | Quản lý đóng yêu cầu đã duyệt |
| GET | /recruitment-requests/{id}/history | Lịch sử xử lý |

Body tạo/sửa:
```json
{
  "title": "Công nhân vận hành máy",
  "quantity": 20,
  "shift": "DAY",
  "skills": "Đọc hướng dẫn thao tác, kiểm tra sản phẩm cơ bản",
  "location": "Lô A2, khu công nghiệp",
  "neededBy": "2027-01-15",
  "version": 0
}
```
Ngày trên chỉ là ví dụ, chọn ngày hiện tại hoặc tương lai. Shift: DAY/NIGHT/ROTATING. Khi sửa phải dùng version nhận từ lần GET mới nhất.

Body hành động:
```json
{"version": 1, "note": "Bổ sung yêu cầu kỹ năng cụ thể"}
```
Note chỉ bắt buộc với request-revision. Không có endpoint đổi status tùy ý.

Response danh sách: `{ "items": [...], "total": 20, "page": 0, "size": 10, "totalPages": 2 }`.
Lỗi nhập liệu: `{ "message": "...", "fields": { "quantity": "..." } }`.
401 chưa đăng nhập; 403 thiếu quyền/CSRF/role không hợp lệ; 404 không tìm thấy/ngoài doanh nghiệp; 409 bản ghi cũ hoặc trạng thái không cho phép.

Chỉ cho phép đúng cặp `roleId`/`role`: `1/ADMIN`, `2/LECTURER`, `3/STUDENT`, `4/ACCOUNTANT`, `5/IT_SUPPORT`, `6/MANAGER`, `7/HR`, phân biệt chữ hoa/thường. Với tài khoản được kích hoạt và không bị khóa, `POST /api/auth/login` kiểm tra mật khẩu trước khi kiểm tra role; mật khẩu đúng nhưng thiếu role, ID ngoài 1–7 hoặc mã không khớp trả `403` và `{ "code": "INVALID_ROLE", "message": "Vai trò không hợp lệ. Tài khoản phải có role_id từ 1 đến 7 và mã role tương ứng." }`, không tạo phiên đăng nhập hoặc nhật ký thành công. Mật khẩu sai của tài khoản đang hoạt động hoặc email không tồn tại vẫn trả `401` với thông báo đăng nhập thông thường. Các API bảo vệ kiểm tra role lại từ database cho phiên đã đăng nhập.

`POST /api/auth/login` trả lỗi trạng thái riêng khi tài khoản không được đăng nhập:

| Trạng thái | HTTP | `code` | `message` |
| --- | --- | --- | --- |
| Bị khóa | `401` | `ACCOUNT_LOCKED` | Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên để khôi phục. |
| Bị vô hiệu hóa | `401` | `ACCOUNT_DISABLED` | Tài khoản đang bị vô hiệu hóa. Vui lòng liên hệ quản trị viên để khôi phục. |

Nếu cả hai cờ cùng bật, lỗi khóa được ưu tiên. Các lần đăng nhập này không tạo phiên đăng nhập thành công hoặc ghi nhật ký đăng nhập thành công. Giao diện hiển thị nguyên thông báo backend, giữ màn đăng nhập và cho phép thử lại; không gọi `/api/auth/me` sau phản hồi đăng nhập lỗi.

Khi dùng Postman: giữ cookie jar, GET csrf trước, lấy `token` đặt vào header đúng `headerName` cho mọi request thay đổi. Login xong GET csrf một lần nữa trước khi POST/PUT/PATCH/DELETE tiếp.

Chi tiết dashboard, cấu trúc response, nguồn số liệu và cách gán role: [DASHBOARDS.md](DASHBOARDS.md).

## Tài khoản người dùng (ADMIN)

Mọi endpoint `/api/admin/**` yêu cầu phiên đăng nhập của đúng `1/ADMIN`; các role khác trả `403`. `GET /api/admin/roles` trả danh sách `[{ "id": 1, "code": "ADMIN", "name": "Quản trị viên" }, ...]` lấy từ `m_role`, chỉ gồm cặp ID/mã chuẩn từ 1 đến 7.

`GET /api/admin/users` tìm `q` trong họ tên, email và số điện thoại, không phân biệt hoa/thường; từ khóa tối đa 200 ký tự. `roleId` không bắt buộc; nếu có phải từ 1 đến 7. `page` bắt đầu từ 0, `size` từ 1 đến 100, mặc định 10. Kết quả sắp xếp theo ID giảm dần:

```json
{
  "items": [{ "id": 12, "fullName": "Nguyễn Văn An", "email": "an@example.com", "phone": "0912345678", "roleId": 3, "role": "STUDENT", "accountEnabled": true, "accountLocked": false }],
  "total": 1,
  "page": 0,
  "size": 10,
  "totalPages": 1
}
```

`GET /api/admin/users/{id}` trả `200` với một đối tượng cùng các trường như `items` phía trên, lấy thông tin mới nhất từ database; tài khoản không tồn tại trả `404`. Không trả mật khẩu hoặc hash.

`POST /api/admin/users` nhận:

```json
{
  "fullName": "Nguyễn Văn An",
  "email": "an@example.com",
  "phone": "0912345678",
  "roleId": 3,
  "password": "<mat-khau-it-nhat-8-ky-tu>",
  "confirmPassword": "<nhap-lai-dung-mat-khau>"
}
```

Họ tên bắt buộc 2–120 ký tự; email bắt buộc, tối đa 190 ký tự, được trim và chuẩn hóa chữ thường. Điện thoại có thể trống hoặc `null`; khi có phải đúng định dạng Việt Nam. `roleId` bắt buộc và phải khớp mã role chuẩn trong database. `password` và `confirmPassword` bắt buộc khi tạo; mật khẩu ít nhất 8 ký tự Unicode, tối đa 72 byte UTF-8, phần xác nhận phải khớp chính xác. Mật khẩu không được trim hoặc đổi hoa/thường. Backend chỉ lưu BCrypt hash và tài khoản mới có thể đăng nhập ngay; không cần gửi mail để kích hoạt mật khẩu.

`PUT /api/admin/users/{id}` nhận cùng các trường thông tin. `password` và `confirmPassword` tùy chọn: bỏ cả hai trường hoặc để cả hai trống để giữ nguyên hash hiện tại; muốn đổi mật khẩu phải nhập cả hai trường theo cùng quy tắc như khi tạo. API không cho sửa trạng thái khóa/kích hoạt bằng body này.

Tạo thành công trả `201`, sửa thành công trả `200`, cùng đối tượng tài khoản như `items` phía trên. API không trả mật khẩu hoặc hash trong bất kỳ phản hồi danh sách, chi tiết, tạo hoặc sửa nào. Email trùng không phân biệt hoa/thường trả `409` với `fields.email`. Dữ liệu sai trả `400` với lỗi trong `fields` tương ứng với `fullName`, `email`, `phone`, `roleId`, `password` hoặc `confirmPassword`; không tìm thấy tài khoản trả `404`.

Quản trị viên không tự hạ quyền tài khoản đang đăng nhập (`409`, `fields.roleId`); hệ thống cũng giữ ít nhất một quản trị viên hợp lệ đang hoạt động và không bị khóa. Admin đổi email, role hoặc mật khẩu tăng `credentials_version`, yêu cầu tài khoản bị sửa đăng nhập lại và làm mã/token khôi phục cũ hết hiệu lực; đổi họ tên/điện thoại giữ phiên. Nếu tự đổi email hoặc mật khẩu, lần gọi API bảo vệ tiếp theo trả `401`, `code: SESSION_EXPIRED`, và giao diện về đăng nhập.

`PATCH /api/admin/users/{id}/status` nhận JSON và cần CSRF header:

```json
{ "accountEnabled": false }
```

Gửi `false` để vô hiệu hóa, `true` để bật lại. `accountEnabled` bắt buộc và phải là boolean; thiếu, `null` hoặc sai kiểu trả `400`. Thành công trả `200` với đối tượng tài khoản cùng các trường như `items` phía trên; không có tài khoản trả `404`. Chỉ ADMIN được gọi; các role khác trả `403`.

Không thể tự vô hiệu hóa tài khoản đang đăng nhập hoặc vô hiệu hóa quản trị viên hợp lệ đang hoạt động cuối cùng (`409`). Mỗi lần trạng thái thực sự thay đổi, backend tăng `credentials_version`: phiên và mã/token khôi phục trước đó hết hiệu lực, kể cả sau khi bật lại. Phiên cũ gọi API bảo vệ trả `401`, `code: SESSION_EXPIRED`, và bị hủy. Gửi lại đúng trạng thái hiện tại không thay đổi phiên bản. Vô hiệu hóa chặn đăng nhập, truy cập API bảo vệ và nhận mã khôi phục mới. Bật lại cho phép đăng nhập bằng mật khẩu hiện có khi role hợp lệ và `accountLocked` là `false`; endpoint này không xóa cờ khóa. Thông tin tài khoản và toàn bộ dữ liệu nghiệp vụ được giữ nguyên. Giao diện yêu cầu xác nhận cả hai thao tác.

`PATCH /api/admin/users/{id}/lock` nhận JSON và cần CSRF header:

```json
{ "accountLocked": true }
```

Gửi `true` để khóa, `false` để mở khóa. `accountLocked` bắt buộc và phải là JSON boolean; thiếu, `null`, chuỗi hoặc số trả `400`. Khi thiếu hoặc `null`, lỗi nằm trong `fields.accountLocked`; sai kiểu JSON trả thông báo dữ liệu không hợp lệ. Chỉ đúng `1/ADMIN` được gọi; chưa đăng nhập trả `401`, role khác hoặc thiếu CSRF trả `403`, tài khoản không tồn tại trả `404`. Thành công trả `200` cùng đối tượng tài khoản như danh sách, không có mật khẩu hoặc hash.

Không thể tự khóa tài khoản đang đăng nhập hoặc khóa quản trị viên hợp lệ đang hoạt động cuối cùng (`409`, `fields.accountLocked`). Khi cờ khóa thực sự thay đổi, backend tăng `credentials_version` để phiên và mã/token khôi phục cũ không được dùng lại, kể cả sau khi mở khóa. Gửi lại đúng trạng thái hiện có không thay đổi phiên bản. Khóa chặn đăng nhập, API bảo vệ và nhận mã khôi phục mới; mở khóa giữ nguyên mật khẩu, role, cờ `accountEnabled` và dữ liệu nghiệp vụ. Vì vậy mở khóa không tự bật tài khoản đang vô hiệu hóa, còn kích hoạt lại không tự mở khóa. Giao diện yêu cầu xác nhận cả khóa và mở khóa.

Dashboard lấy số liệu từ `GET /api/dashboard/admin`. Giao diện tải lại API này sau thao tác quản trị thành công và khi trở về dashboard: tổng tài khoản đếm mọi bản ghi `m_user`, đang hoạt động đếm `account_enabled = TRUE AND account_locked = FALSE`, bị khóa đếm `account_locked = TRUE`, vô hiệu hóa đếm `account_enabled = FALSE`. Cờ khóa và vô hiệu hóa độc lập nên hai số cuối có thể cùng tính một tài khoản.

`DELETE /api/admin/users/{id}` không cần body, yêu cầu CSRF header. Thành công trả `204` và xóa bản ghi tài khoản thực sự. Tài khoản không tồn tại trả `404`. Trả `409` khi xóa chính tài khoản đang đăng nhập, quản trị viên hoạt động cuối cùng hoặc tài khoản có dữ liệu nghiệp vụ liên quan. Các tham chiếu nghiệp vụ được bảo vệ gồm:

- `recruitment_requests.created_by`, `request_events.actor_id`.
- `training_classes.lecturer_id`, `training_enrollments.student_id`, `training_attendance.student_id`, `training_contracts.student_id`.
- `equipment_requests.requested_by`, `equipment_loans.student_id`, `equipment_incidents.reported_by`.
- `consultation_followups.assigned_to`.

Khi xóa bị từ chối, dữ liệu tài khoản và nghiệp vụ được giữ nguyên. Chỉ `user_login_events` và `password_reset_challenges` có khóa ngoại xóa kèm tài khoản; không xóa dây chuyền dữ liệu nghiệp vụ. Phiên của tài khoản đã xóa không còn dùng được; API bảo vệ trả `401`, `code: SESSION_EXPIRED`. Bổ sung CRUD này không cần migration mới; dùng schema V1–V7 hiện có.

## Quên mật khẩu (không cần đăng nhập)

Cả ba endpoint dùng JSON và yêu cầu CSRF token/cookie lấy từ `GET /api/auth/csrf`. Email được chuẩn hóa trim/chữ thường và chỉ tài khoản đang hoạt động với cặp role hợp lệ mới nhận mã.

1. `POST /api/auth/password-reset/request` với `{ "email": "an@example.com" }`: trả `200` với phản hồi chung không xác nhận email có tồn tại hay không. Phản hồi có `message`, `retryAfterSeconds: 60` và `expiresInSeconds: 600`; mã gửi qua SMTP gồm đúng 6 chữ số, có thể bắt đầu bằng `0`, hết hạn sau 600 giây. Mail được gửi bất đồng bộ sau khi transaction lưu mã hoàn tất; API thành công không xác nhận thư đã đến hộp thư.
2. `POST /api/auth/password-reset/verify` với `{ "email": "an@example.com", "code": "012345" }`: nếu hợp lệ, trả `{ "resetToken": "<token-ngau-nhien>", "expiresInSeconds": 300 }`. `code` là chuỗi để giữ số 0 ở đầu. Mã chỉ dùng để đổi lấy token một lần.
3. `POST /api/auth/password-reset/complete` nhận:

```json
{
  "email": "an@example.com",
  "resetToken": "<token-nhan-duoc-o-buoc-verify>",
  "newPassword": "<mat-khau-moi>",
  "confirmPassword": "<nhap-lai-mat-khau-moi>"
}
```

Mật khẩu mới ít nhất 8 ký tự Unicode, tối đa 72 byte UTF-8; phần xác nhận phải khớp chính xác. Thành công trả `204`: backend lưu BCrypt hash, tiêu thụ token và vô hiệu hóa các phiên cũ của tài khoản; không tự đăng nhập. Token chỉ dùng một lần và hết hạn sau 5 phút. Đổi email, role hoặc mật khẩu trong thời gian khôi phục khiến yêu cầu cũ không còn hợp lệ.

Gửi lại mã cách nhau tối thiểu 60 giây; tối đa 5 yêu cầu/email/giờ và 20 yêu cầu/địa chỉ IP/giờ. Mã mới thay thế mã/token trước. Tối đa 5 lần thử mã; hết lượt cần yêu cầu mã mới. Vượt giới hạn trả `429` với `retryAfterSeconds`; thiếu CSRF trả `403`. Thiếu cấu hình mail trả `503`, `code: MAIL_NOT_CONFIGURED`. Mã sai/hết hạn, token sai/hết hạn và mật khẩu không hợp lệ trả lỗi phù hợp kèm `message`/`code`/`fields` nếu có; không trả mã email, mật khẩu hoặc hash.

Migration V7 tạo `password_reset_challenges`, `password_reset_rate_limits` và `m_user.credentials_version`; mã/token được lưu dưới dạng hash. Cấu hình Gmail, cách dùng và lưu ý kiểm chứng gửi thư: [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).

## Thông tin cá nhân

`GET /api/auth/me` trả `{ "id": 1, "email": "user@example.com", "fullName": "Nguyễn Văn An", "role": "HR", "roleId": 7, "phone": null }`. Số điện thoại được bổ sung bằng migration V4 vào cột `m_user.phone VARCHAR(20)`; tài khoản cũ có giá trị `NULL`.

`PUT /api/auth/me` yêu cầu đăng nhập và CSRF header, nhận:

```json
{
  "fullName": "Nguyễn Văn An",
  "phone": "0912345678"
}
```

Email đăng nhập là trường chỉ đọc. DTO cập nhật hồ sơ chỉ nhận họ tên và điện thoại; khóa `email` gửi thêm không làm thay đổi email trong database. Họ tên bắt buộc 2–120 ký tự. Điện thoại không bắt buộc; để trống hoặc `null` sẽ xóa số đã lưu. Khi nhập, số điện thoại phải đúng định dạng Việt Nam như form tư vấn; backend bỏ khoảng trắng và dấu phân cách.

API chỉ sửa tài khoản của phiên hiện tại và trả UserView đã cập nhật. Dữ liệu không hợp lệ trả `400` với `fields`; thiếu CSRF trả `403`; chưa đăng nhập trả `401`.

### Đổi mật khẩu

`POST /api/auth/change-password` yêu cầu đăng nhập và CSRF header:

```json
{
  "oldPassword": "<mat-khau-hien-tai>",
  "newPassword": "<mat-khau-moi>",
  "confirmPassword": "<nhap-lai-mat-khau-moi>"
}
```

Cả ba trường bắt buộc; mật khẩu mới có ít nhất 8 ký tự Unicode và tối đa 72 byte UTF-8. Mật khẩu được so sánh chính xác, giữ nguyên khoảng trắng, không trim hoặc đổi hoa/thường. `oldPassword` phải khớp BCrypt hash đang lưu; `confirmPassword` phải khớp hoàn toàn `newPassword`. Giá trị mật khẩu quá giới hạn BCrypt cũng được từ chối với `400`.

Thành công trả `204`, chỉ lưu BCrypt hash mới cho tài khoản đang đăng nhập và giữ phiên hiện tại hoạt động. Những lần đăng nhập tiếp theo dùng mật khẩu mới. Sai mật khẩu cũ, xác nhận lệch hoặc dữ liệu không hợp lệ trả `400` với thông báo an toàn; thiếu CSRF trả `403`; chưa đăng nhập trả `401`. API không trả hash hoặc mật khẩu. Cập nhật hồ sơ và đổi mật khẩu cùng khóa bản ghi tài khoản để tránh ghi đè khi gửi đồng thời.

## Đăng ký tư vấn

`POST /api/public/consultations`, Content-Type `application/json`. Lấy token qua `GET /api/auth/csrf` và gửi đúng session cookie, CSRF header từ response. Endpoint không yêu cầu tài khoản.

```json
{
  "fullName": "Nguyễn Văn An",
  "phone": "0912345678",
  "email": "an@example.com",
  "contactTime": "morning",
  "description": "Tôi muốn đăng ký tư vấn khóa điện công nghiệp.",
  "program_name": "Điện công nghiệp nền tảng"
}
```

Họ tên 2–120 ký tự; số điện thoại Việt Nam 10 chữ số bắt đầu 0 hoặc dạng 84/+84 theo sau 9 chữ số; email không bắt buộc, tối đa 190 ký tự; lời nhắn không bắt buộc, tối đa 2000 ký tự. Backend trim chuỗi, bỏ khoảng trắng/dấu phân cách của điện thoại; email và lời nhắn trống lưu NULL.

`contactTime`: `any`, `morning`, `afternoon`. `program_name` bắt buộc, tối đa 255 ký tự, là text của option đang được chọn, ví dụ `Điện công nghiệp nền tảng`. Checkbox đồng ý chỉ được kiểm tra trên giao diện; request không gửi `kind`, `consent` hoặc mã `interest`. ID và thời điểm tạo do backend cấp.

Lưu thành công vào `consultation_registrations` trả HTTP `201` và `{ "id": "<UUID>", "fullName": "Nguyễn Văn An", "created_date": "<UTC timestamp>" }`. Bảng có đúng 8 cột: `id`, `full_name`, `phone`, `email`, `contact_time`, `description`, `created_date`, `program_name`. Java dùng `createdDate`/`programName` và ánh xạ tới tên cột tương ứng. Lỗi dữ liệu trả `400` với `message`/`fields`; lỗi tên chương trình nằm ở `fields.programName`. Thiếu hoặc sai CSRF trả `403`. Không có API công khai để đọc danh sách đăng ký.
