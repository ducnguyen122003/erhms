# KCN Connect — ngành nghề & đào tạo ngắn hạn

## Mở trên máy
Giữ nguyên thư mục `assets`. Cài Node.js 22.12+ hoặc 24, mở terminal trong `G5_FE2` và chạy:

```powershell
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:8081. Backend `G5_BE` phải chạy tại http://localhost:8080 để lưu đăng ký tư vấn. Nếu backend ở địa chỉ khác, đặt `$env:API_TARGET = "http://localhost:<cong>"` trước khi chạy frontend. Server chuyển tiếp `/api` tới backend; không mở HTML bằng `file://` hoặc Python static server khi cần gửi form.

Website quản lý `G5_FE1` chạy riêng tại http://localhost:8082. Hướng dẫn backend và Docker nằm trong `../README.md`.

## Các trang chính
- `index.html`: trang chủ, định hướng ngành nghề và bài báo liên quan.
- `ve-chung-toi.html`: giới thiệu, sứ mệnh, tầm nhìn, giá trị và nội dung đồng hành.
- `nganh-nghe.html`: 6 nhóm nghề, tìm kiếm và bộ lọc.
- `dao-tao.html`: danh sách chương trình đào tạo.
- `lo-trinh.html`: các bước từ tư vấn tới chuẩn bị cho công việc.
- `doanh-nghiep.html`: ví dụ doanh nghiệp tham khảo; chưa phải danh sách đối tác xác nhận.
- `lien-he.html`: form chỉ tư vấn học nghề; đã bỏ Tìm việc và Doanh nghiệp.

## Trang chi tiết khóa học
- `khoa-hoc-an-toan-san-xuat.html`
- `khoa-hoc-dien-cong-nghiep.html`
- `khoa-hoc-kiem-tra-chat-luong.html`

Đường dẫn cũ `viec-lam.html` chuyển tới `nganh-nghe.html`.

## File dùng chung
- `app.js`: dữ liệu `careers`, `courses`, tìm kiếm, lọc, hộp thoại và form.
- `styles.css`: giao diện các trang và bố cục responsive.
- `assets/`: ảnh minh họa bằng AI, logo SVG, font và giấy phép font.

Footer có điện thoại 0368488333 và email contact@csgroup.vn theo thông tin được cung cấp.

## Phạm vi bản thiết kế
Các nhóm nghề và chương trình là nội dung minh họa cho dự án. Thời lượng, lịch khai giảng, học phí, điều kiện tham gia và hoàn thành cần xác nhận theo lớp.

Form gửi `fullName`, `phone`, `email`, `contactTime`, `description`, `program_name` tới `POST /api/public/consultations`. `program_name` là phần chữ hiển thị của option đã chọn. Checkbox đồng ý được kiểm tra trên giao diện. Backend lưu vào bảng PostgreSQL `consultation_registrations`, tự cấp `id`, `created_date` và trả mã đăng ký khi thành công. Khi có lỗi, form giữ thông tin đã nhập và hiển thị lỗi để thử lại. Luồng này chưa tự gửi email hoặc phân công tư vấn viên.

## Build

```powershell
npm.cmd run build
npm.cmd run preview
```

Build tạo các trang trong `dist`; preview chạy cổng 8081 và proxy API theo `API_TARGET`. Docker dùng Nginx phục vụ HTML và proxy API tới `backend:8080`.
