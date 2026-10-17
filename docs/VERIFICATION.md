# Kết quả kiểm chứng — 16/10/2026

## Đã chạy thành công
- Backend Maven package: compile và tạo executable JAR thành công (Java 17, Spring Boot 3.5.16).
- 9 integration tests: 9 đạt, 0 lỗi, 0 thất bại. Chạy Spring context, Flyway migration V1, JPA schema validation và MockMvc trên H2 chế độ PostgreSQL.
- Bao phủ: người chưa đăng nhập, CSRF bắt buộc, số lượng/ngày không hợp lệ, cách ly doanh nghiệp, nhân sự không được duyệt, bản ghi cũ trả 409, nhánh yêu cầu sửa và luồng phê duyệt/đóng, đăng nhập thực và phiên.
- HTTP smoke trên server Spring Boot thực: đăng nhập bằng cookie + CSRF, tạo, gửi duyệt, phê duyệt, đóng, lịch sử, tìm kiếm và logout. Database kiểm thử H2.
- Frontend: npm install tạo lockfile; npm run build thành công.
- Trình duyệt Chromium: thao tác FE thật qua Vite proxy tới Spring Boot, đăng nhập nhân sự, nhập form, lưu, gửi duyệt, đăng xuất, đăng nhập quản lý, phê duyệt và tải lại trang vẫn thấy bản ghi. Không có lỗi JavaScript.
- Kiểm tra giao diện 1440px và 390px. Đã sửa tràn ngang trên mobile; bảng cuộn trong vùng riêng.
- Đọc hình render form, desktop và mobile để kiểm tra bố cục.
- Kiểm tra cú pháp POM, Compose YAML, Spring YAML và package-lock JSON.

## Chưa chạy trong môi trường tạo file
- Chưa chạy Docker Compose và PostgreSQL thật vì không có Docker daemon/PostgreSQL runtime sẵn dùng. Kiểm thử H2 không chứng minh mọi hành vi PostgreSQL hay healthcheck container.
- Chưa chạy trên Windows thực, chưa kiểm thử tải, bảo mật chuyên sâu hoặc production.
- Không khẳng định đã hoàn thiện các module đào tạo/ứng tuyển/nhận việc.

## Kiểm tra tiếp trên máy bạn
Chạy start.bat hoặc docker compose up --build -d; xem docker compose ps. Khi cả ba service sẵn sàng, thử luồng trong README và chạy python scripts/smoke_test.py. Script này sẽ kiểm tra API với PostgreSQL thật của stack và để lại một bản ghi [SMOKE] đã đóng.
