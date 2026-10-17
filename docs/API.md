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
| POST | /auth/logout | Hủy phiên; thêm CSRF header |
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
401 chưa đăng nhập; 403 thiếu quyền/CSRF; 404 không tìm thấy/ngoài doanh nghiệp; 409 bản ghi cũ hoặc trạng thái không cho phép.

Khi dùng Postman: giữ cookie jar, GET csrf trước, lấy `token` đặt vào header đúng `headerName` cho mọi request thay đổi. Login xong GET csrf một lần nữa trước khi POST/PUT tiếp.

## Thông tin cá nhân

`GET /api/auth/me` trả `{ "id": 1, "email": "user@example.com", "fullName": "Nguyễn Văn An", "role": "USER", "roleId": 1, "phone": null }`. Số điện thoại được bổ sung bằng migration V4 vào cột `m_user.phone VARCHAR(20)`; tài khoản cũ có giá trị `NULL`.

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
