# KCN Connect — Spring Boot + React + PostgreSQL

Bộ khung có mã nguồn FE/BE riêng, form tuyển dụng lưu dữ liệu, đăng nhập và luồng phê duyệt. Giao diện và thông báo bằng tiếng Việt. Đây là starter để phát triển dự án, chưa phải toàn bộ hệ thống trong report.

## 1. Chạy nhanh trên Windows bằng Docker Desktop
Cài và mở Docker Desktop (Linux containers). Giải nén toàn bộ ZIP, mở thư mục `kcn-connect-starter`, chạy `start.bat`. Lần đầu cần Internet để tải image và dependency.

Hoặc mở PowerShell trong thư mục đó:
```powershell
Copy-Item .env.example .env
docker compose up --build -d
```
Chỉ copy `.env` lần đầu; không ghi đè cấu hình bạn đã chỉnh. Trên macOS/Linux:
```bash
sh start.sh
```

- FE: http://localhost:5173
- BE health: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432 / database `kcn_connect` / user `kcn` / password theo `DB_PASSWORD` trong `.env`.
- Tài khoản nhân sự: `hr@kcn.local`
- Tài khoản quản lý: `manager@kcn.local`
- Mật khẩu mẫu cả hai: `KcnDemo@2026!` (theo DEMO_PASSWORD trong `.env.example`). Chỉ dành cho máy phát triển.

Xem tình trạng và log:
```bash
docker compose ps
docker compose logs -f backend
```
Dừng, vẫn giữ dữ liệu:
```bash
docker compose down
```
Volume `postgres_data` giữ dữ liệu giữa các lần chạy. Không dùng `down -v` nếu muốn giữ dữ liệu. Thay mật khẩu DB trong `.env` không tự thay mật khẩu của PostgreSQL đã khởi tạo trong volume. DEMO_PASSWORD chỉ áp dụng khi tạo tài khoản lần đầu, không reset tài khoản đã có.

## 2. Thử luồng FE → BE → database
1. Đăng nhập bằng nhân sự.
2. Chọn **Tạo nhu cầu**, điền vị trí, số lượng, ca, địa điểm, ngày và kỹ năng.
3. Chọn **Lưu bản nháp**. Mở lại bản ghi, chọn **Gửi duyệt**.
4. Đăng xuất; đăng nhập quản lý.
5. Mở bản ghi, nhập ghi chú và chọn **Yêu cầu sửa**, hoặc **Phê duyệt**.
6. Với yêu cầu cần sửa, nhân sự sửa/lưu rồi gửi lại.
7. Quản lý có thể đóng yêu cầu đã duyệt. Mở chi tiết để xem lịch sử.
8. Tải lại trang hoặc khởi động lại stack: bản ghi vẫn nằm trong PostgreSQL.

Các danh sách ban đầu trống có chủ đích: seed chỉ tạo doanh nghiệp và hai tài khoản. Trang Lộ trình ghi rõ module nào chưa triển khai.

## 3. Chạy riêng khi lập trình
Yêu cầu: Java 17, Maven 3.9+, Node.js 22.12+ hoặc 24, PostgreSQL 17. Nếu dùng Docker chạy toàn bộ thì không cần cài riêng Java/Node/Maven/PostgreSQL.

### PostgreSQL
Từ thư mục gốc, đã có `.env`:
```bash
docker compose up -d db
```
Hoặc tạo database `kcn_connect` trong PostgreSQL cài sẵn, rồi cập nhật DB_URL/DB_USER/DB_PASSWORD. Nếu đang chạy stack toàn bộ, dừng frontend/backend trước để tránh trùng cổng:
```bash
docker compose stop frontend backend
```

### Backend — PowerShell
```powershell
cd backend
$env:DB_URL="jdbc:postgresql://localhost:5432/kcn_connect"
$env:DB_USER="kcn"
$env:DB_PASSWORD="kcn_local_dev_2026"
$env:SEED_DEMO="true"
$env:DEMO_PASSWORD="KcnDemo@2026!"
mvn spring-boot:run
```
Các giá trị phải khớp `.env` của database. Spring không tự đọc file `.env` khi chạy trực tiếp bằng Maven/IDE; cần cấu hình environment variables như trên.

### Backend — macOS/Linux
```bash
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/kcn_connect
export DB_USER=kcn
export DB_PASSWORD=kcn_local_dev_2026
export SEED_DEMO=true
export DEMO_PASSWORD='KcnDemo@2026!'
mvn spring-boot:run
```

### Frontend — terminal khác
```bash
cd frontend
npm ci
npm run dev
```
Vite proxy `/api` tới localhost:8080. Mở http://localhost:5173. Nếu backend dùng cổng khác, đặt API_TARGET trước khi chạy Vite. Không mở trực tiếp index.html bằng file://.

## 4. Cấu trúc và công nghệ
- `backend/`: Maven, Spring Boot 3.5.16, Java 17, Spring Web, JPA, Validation, Security, Flyway, Actuator.
- `frontend/`: React 19, Vite, CSS responsive, lucide-react; có package-lock.
- `compose.yaml`: PostgreSQL → backend → frontend, có healthcheck.
- `backend/src/main/resources/db/migration/`: SQL versioned. Hibernate chỉ validate schema; không tự update.
- `docs/ARCHITECTURE.md`: module, trạng thái, quyền và hướng mở rộng.
- `docs/API.md`: endpoint, request/response, cách dùng session và CSRF.
- `docs/VERIFICATION.md`: kết quả và giới hạn kiểm chứng trong môi trường tạo file.

## 5. Build và kiểm tra
```bash
cd backend
mvn test
mvn package
```
Test dùng H2 chế độ PostgreSQL, chạy migration thật của dự án và kiểm tra qua MockMvc. Đây không thay thế kiểm thử trên PostgreSQL thật.

Sau khi stack Docker sẵn sàng, có thể kiểm tra REST API với Python 3 (không cần thư viện ngoài), từ thư mục gốc:
```bash
python scripts/smoke_test.py
```
Script tạo một nhu cầu có tiền tố `[SMOKE]`, gửi duyệt, phê duyệt rồi đóng; bản ghi kiểm tra được giữ lại để xem lịch sử. Nếu đã đổi mật khẩu mẫu, đặt biến môi trường DEMO_PASSWORD tương ứng trước khi chạy.
```bash
cd frontend
npm ci
npm run build
```

## 6. Đã có và cần phát triển tiếp
Đã có: xác thực session, CSRF, BCrypt, hai vai trò, phạm vi doanh nghiệp, tạo/sửa/xem/tìm kiếm/phân trang nhu cầu, chuyển trạng thái có điều kiện, version chống ghi đè, lịch sử, validation FE/BE, xử lý loading/lỗi/rỗng.

Chưa có: đăng ký/quên mật khẩu, quản trị tài khoản, đủ bảy vai trò, tin tuyển dụng công khai, hồ sơ công nhân, ứng tuyển/phỏng vấn, phân lớp/đào tạo, offer/nhận việc, email, CI/CD, cấu hình production. Trang Lộ trình là mô tả phạm vi, không gọi API giả.

## 7. Lỗi thường gặp
- Docker không chạy: mở Docker Desktop, chờ engine sẵn sàng.
- Cổng bận: chỉnh FRONTEND_PORT/BACKEND_PORT/DB_PORT trong .env. Địa chỉ truy cập đổi theo cổng mới.
- BE chưa khởi động: xem `docker compose logs backend`; lần build đầu có thể lâu.
- 401: đăng nhập lại. 403 CSRF: tải lại trang và đăng nhập lại nếu phiên hết hạn.
- 409: dữ liệu đã thay đổi hoặc hành động không hợp lệ; đóng và mở lại chi tiết.
- `password authentication failed`: kiểm tra password trong DB hiện có; thay .env không đổi mật khẩu volume cũ.
- Ngày không hợp lệ: chọn ngày hôm nay hoặc tương lai theo giờ Việt Nam.
