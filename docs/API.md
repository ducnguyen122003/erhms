# REST API
Base URL qua FE: `http://localhost:5173/api`; trực tiếp BE: `http://localhost:8080/api`.

| Method | Path | Mô tả |
|---|---|---|
| GET | /auth/csrf | Lấy token/headerName và session cookie |
| POST | /auth/login | Form URL encoded: username, password; thêm CSRF header |
| GET | /auth/me | Tài khoản hiện tại, không trả password hash |
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
