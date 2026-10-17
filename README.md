# KCN Connect — Spring Boot + React + PostgreSQL

Dự án có hai website dùng chung backend và database:

| Thành phần | Thư mục | URL mặc định |
| --- | --- | --- |
| Website ngành nghề, đào tạo và đăng ký tư vấn | `G5_FE2` | http://localhost:8081 |
| Website quản lý nhu cầu tuyển dụng | `G5_FE1` | http://localhost:8082 |
| REST API Spring Boot | `G5_BE` | http://localhost:8080 |

## Chạy khi lập trình trên Windows

Yêu cầu Java 17, Maven, Node.js 22.12+ hoặc 24 và PostgreSQL. Chạy các lệnh bên dưới từ thư mục gốc dự án trong các terminal riêng. Nếu backend đang chạy trong IDE, khởi động lại để nhận API và migration mới; nếu frontend đang chạy, dừng và chạy lại để nhận cấu hình cổng.

### 1. Backend

Backend mặc định kết nối database `erhms` tại `localhost:5432`. Tạo database này nếu chưa có:

```sql
CREATE DATABASE erhms ENCODING 'UTF8';
```

Đặt thông tin kết nối phù hợp với PostgreSQL của bạn, rồi chạy:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/erhms"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "<mat-khau-PostgreSQL-cua-ban>"
mvn -f .\G5_BE\pom.xml spring-boot:run
```

Spring Boot không tự đọc file `.env` ở thư mục gốc khi chạy Maven/IDE. Có thể đặt các biến trên trong cấu hình Run của IDE. Kiểm tra backend tại http://localhost:8080/actuator/health.

Khi được bật, Flyway chạy trước bước Hibernate kiểm tra schema. Cấu hình hiện tại trong `G5_BE/src/main/resources/application.yml` đang tắt Flyway; để tự chạy migration khi khởi động, đặt `spring.flyway.enabled: true`, `spring.flyway.baseline-on-migrate: true`, `spring.flyway.baseline-version: 1`.

- Database rỗng: chạy V1 tạo schema và dữ liệu demo, V2 tạo bảng tư vấn và V3 chuyển bảng sang cấu trúc hiện tại.
- Database đã có schema V1 nhưng chưa có lịch sử Flyway: đánh dấu baseline phiên bản 1 và chạy V2, V3. Dữ liệu hiện có được giữ lại.
- Database đã chạy V2: V3 đổi các cột tư vấn sang `description`, `created_date`, `program_name`, chuyển mã ngành/khóa học cũ thành tên hiển thị và bỏ `kind`, `consent` theo cấu trúc mới.
- V4 bổ sung `m_user.phone VARCHAR(20)` cho thông tin cá nhân; tài khoản cũ giữ nguyên và có điện thoại `NULL`.
- Những lần khởi động sau chỉ chạy migration chưa áp dụng. Không cần tự tạo bảng tư vấn hoặc sửa migration V1.

### 2. Website quản lý — cổng 8082

```powershell
npm.cmd --prefix .\G5_FE1 ci
npm.cmd --prefix .\G5_FE1 run dev
```

Mở http://localhost:8082. Các tài khoản của database mới tạo bằng V1: `user1@erhms.local`, `user2@erhms.local`, `manager@erhms.local`; mật khẩu demo `Demo@123456`. Database đang sử dụng giữ nguyên tài khoản/mật khẩu của nó.

### 3. Website tư vấn — cổng 8081

```powershell
npm.cmd --prefix .\G5_FE2 ci
npm.cmd --prefix .\G5_FE2 run dev
```

Mở http://localhost:8081/lien-he.html. Cần chạy qua server này để gửi form; mở file HTML trực tiếp hoặc chạy Python static server sẽ không có proxy API.

Cả hai frontend chuyển tiếp `/api` tới `http://localhost:8080`. Nếu backend dùng địa chỉ khác, đặt `$env:API_TARGET = "http://localhost:<cong>"` trước khi chạy frontend. Cổng frontend được cố định: nếu cổng bận, Vite báo lỗi thay vì tự chuyển sang cổng khác.

## Xem và cập nhật thông tin cá nhân

Trong web quản lý cổng 8082, bấm avatar hoặc tên người dùng cạnh nút đăng xuất để mở **Thông tin cá nhân**. Có thể cập nhật họ tên và số điện thoại, rồi bấm lưu. Số điện thoại có thể để trống. Email đăng nhập được khóa, chỉ xem và sao chép; backend không cho cập nhật email qua API hồ sơ.

Trong phần **Đổi mật khẩu**, nhập mật khẩu hiện tại, mật khẩu mới và xác nhận mật khẩu mới. Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8. Backend kiểm tra mật khẩu cũ và phần xác nhận trước khi lưu hash BCrypt mới. Khi thành công, ba ô mật khẩu được xóa; phiên hiện tại vẫn hoạt động và lần đăng nhập tiếp theo dùng mật khẩu mới. Nhập sai mật khẩu cũ hoặc xác nhận không khớp sẽ không đổi mật khẩu.

Thông tin được lưu trong bảng `m_user`; migration V4 bổ sung cột `phone`. API `GET /api/auth/me` cung cấp thông tin, `PUT /api/auth/me` cập nhật họ tên/điện thoại và `POST /api/auth/change-password` đổi mật khẩu cho tài khoản đang đăng nhập. Tính năng đổi mật khẩu không cần thêm cột database. Nếu backend đang chạy trong IDE, khởi động lại sau khi cập nhật code để nhận tính năng mới.

## Đăng ký tư vấn và kiểm tra dữ liệu

1. Mở trang liên hệ ở cổng 8081, nhập họ tên, số điện thoại, email nếu có, thời gian liên hệ, ngành/khóa học quan tâm và lời nhắn.
2. Đồng ý sử dụng thông tin để tư vấn rồi bấm **Gửi đăng ký tư vấn**.
3. Frontend lấy CSRF token, gọi `POST /api/public/consultations`; backend kiểm tra dữ liệu và lưu transaction vào PostgreSQL. Không cần đăng nhập để đăng ký.
4. Khi lưu thành công, API trả `201` cùng mã đăng ký; website hiển thị mã này. Nếu có lỗi, form giữ thông tin để người dùng sửa hoặc gửi lại.
5. Trong công cụ quản lý PostgreSQL, kết nối đúng database backend đang dùng và chạy:

```sql
SELECT id, full_name, phone, email, contact_time,
       description, created_date, program_name
FROM consultation_registrations
ORDER BY created_date DESC;
```

`program_name` lưu phần chữ hiển thị của lựa chọn ngành/khóa học, ví dụ `Điện công nghiệp nền tảng`; `contact_time` lưu `any`, `morning` hoặc `afternoon`. Email và mô tả để trống được lưu `NULL`. Checkbox đồng ý chỉ kiểm tra trên giao diện. Mã đăng ký và `created_date` do backend cấp. API công khai chỉ nhận đăng ký; không cung cấp danh sách thông tin cá nhân.

Chi tiết request, response và CSRF trong [docs/API.md](docs/API.md).

## Chạy bằng Docker Compose

Mở Docker Desktop. Chỉ khi chưa có `.env`, copy `.env.example` thành `.env` và cấu hình thông tin database. Từ thư mục gốc:

```powershell
docker compose up --build -d
docker compose ps
```

`start.bat` / `start.sh` cũng chạy stack này. Compose sử dụng các thư mục `G5_BE`, `G5_FE1`, `G5_FE2`; Nginx của hai frontend proxy API tới service `backend:8080`.

Compose dùng database riêng trong volume `postgres_data`, tên mặc định `kcn_connect` (đổi bằng `DB_NAME` trong `.env`). PostgreSQL đang chạy trực tiếp trên máy và database `erhms` không tự chuyển sang database của Compose. Dừng các dịch vụ phát triển đang chiếm cổng trước khi chạy toàn bộ stack; nếu đã có PostgreSQL tại cổng 5432, có thể đặt `DB_PORT=5433` trong `.env` cho database Docker.

Các cổng Docker lấy từ `PUBLIC_FRONTEND_PORT=8081`, `FRONTEND_PORT=8082`, `BACKEND_PORT=8080`, `DB_PORT=5432`. Mật khẩu database lấy từ `.env`. Đổi mật khẩu trong file không tự đổi mật khẩu của PostgreSQL đã khởi tạo trong volume.

```powershell
docker compose logs -f backend
docker compose down
```

`down` giữ dữ liệu volume. Không dùng `down -v` nếu muốn giữ dữ liệu.

## Build và kiểm thử

```powershell
mvn -f .\G5_BE\pom.xml test
npm.cmd --prefix .\G5_FE1 run build
npm.cmd --prefix .\G5_FE2 run build
```

Backend tests dùng H2 ở chế độ PostgreSQL, chạy các migration thật, kiểm tra đăng ký tư vấn, validation, CSRF, phân quyền và luồng tuyển dụng. Website `G5_FE2` build đầy đủ các trang HTML.
