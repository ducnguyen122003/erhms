# Kiểm chứng đăng ký tư vấn — 17/10/2026

## Cập nhật cấu trúc 8 cột và tên chương trình

- Maven package đạt 30 tests, không failures/errors/skipped; FE2 production build thành công.
- Migration V3 được kiểm tra trên H2 và áp dụng thành công vào PostgreSQL `erhms`. Bảng hiện có đúng `id`, `full_name`, `phone`, `email`, `contact_time`, `description`, `created_date`, `program_name`.
- Test nâng cấp bao phủ cả 10 mã lựa chọn cũ, chuyển thành tên hiển thị tương ứng và giữ ID, thông tin liên hệ, mô tả, thời điểm tạo. Kiểm tra database thật bằng số lượng và hash các trường giữ lại xác nhận bản ghi có sẵn được bảo toàn.
- Chrome thực gửi đúng 6 khóa JSON: `fullName`, `phone`, `email`, `contactTime`, `description`, `program_name`. Chọn `electric` gửi và lưu phần chữ `Điện công nghiệp nền tảng`; nhận HTTP 201, UUID và `created_date`, không có lỗi JavaScript.
- Đã xác nhận bản ghi qua kết nối PostgreSQL riêng rồi xóa đúng bản ghi giả theo UUID; bản ghi cũ được giữ lại. Đã dừng Chrome và các server kiểm thử riêng ở cổng 8083/8084.
- Giữ nguyên cấu hình tắt Flyway mà người dùng đã đặt; phiên kiểm thử bật Flyway bằng tham số chạy để áp dụng V3. Backend đang chạy trong IDE cần khởi động lại để nhận DTO/entity mới.

## Kiểm chứng cấu trúc trước V3

- Backend: `mvn test` đạt 29 tests, không failures/errors/skipped. Bao gồm 6 tests tư vấn và test nâng cấp database có schema nhưng chưa có Flyway history, giữ dữ liệu cũ.
- Backend executable JAR đóng gói thành công.
- `G5_FE1` và `G5_FE2`: production build thành công. FE2 build đủ 11 trang HTML.
- FE2 dev và preview: phục vụ HTML cổng 8081, proxy `/api` được xác nhận. FE1 hiện có tại cổng 8082 trả HTTP 200.
- Chrome thực: mở trang liên hệ cổng 8081, nhập đủ các trường và click gửi. Nhận đúng một POST HTTP 201, hiện dialog thành công cùng UUID, reset form và mở lại nút gửi; không có lỗi JavaScript. Form rỗng bị chặn trước khi gọi API.
- PostgreSQL 17 database `erhms`: baseline phiên bản 1 và migration V2 thực thi thành công. Truy vấn bằng kết nối SQL riêng xác nhận bản ghi đã commit, khớp họ tên, điện thoại, email, loại tư vấn, ngành/khóa học, thời gian liên hệ, lời nhắn, sự đồng ý và thời điểm tạo.
- Sau migration, số bản ghi các bảng cũ giữ nguyên: `m_role` 2, `m_user` 3, `recruitment_requests` 5, `request_events` 13.
- Bản ghi kiểm thử dùng dữ liệu giả đã được xóa theo đúng UUID; bảng và lịch sử migration được giữ lại.
- `docker compose config --quiet` đạt; chưa chạy toàn bộ container Docker.

Để kiểm thử mà không ngắt backend đang chạy trong IDE tại 8080, phiên backend mới dùng tạm cổng 8083; FE2 8081 proxy tới phiên này qua `API_TARGET`. Đã dừng các server và Chrome do phiên kiểm thử tạo. Cấu hình mặc định của hai frontend vẫn proxy tới backend 8080. Cần khởi động lại backend trong IDE để nhận code mới, rồi chạy frontend theo README.
