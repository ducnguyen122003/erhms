# KCN Connect — Spring Boot + React + PostgreSQL

Dự án có hai website dùng chung backend và database:

| Thành phần | Thư mục | URL mặc định |
| --- | --- | --- |
| Website ngành nghề, đào tạo và đăng ký tư vấn | `G5_FE2` | http://localhost:8081 |
| Website quản lý với 7 dashboard theo vai trò | `G5_FE1` | http://localhost:8082 |
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

Flyway mặc định được bật và chạy trước bước Hibernate kiểm tra schema. Cấu hình trong `G5_BE/src/main/resources/application.yml` sử dụng `baseline-on-migrate: true`, `baseline-version: 1`; có thể đặt `FLYWAY_ENABLED=false` nếu schema đã được quản lý riêng và có đủ migration V1–V7.

- Database rỗng: chạy V1 tạo schema và dữ liệu demo, V2 tạo bảng tư vấn và V3 chuyển bảng sang cấu trúc hiện tại.
- Database đã có schema V1 nhưng chưa có lịch sử Flyway: đánh dấu baseline phiên bản 1 và chạy V2, V3. Dữ liệu hiện có được giữ lại.
- Database đã chạy V2: V3 đổi các cột tư vấn sang `description`, `created_date`, `program_name`, chuyển mã ngành/khóa học cũ thành tên hiển thị và bỏ `kind`, `consent` theo cấu trúc mới.
- V4 bổ sung `m_user.phone VARCHAR(20)` cho thông tin cá nhân; tài khoản cũ giữ nguyên và có điện thoại `NULL`.
- V5 bổ sung 7 role dashboard, trạng thái tài khoản, nhật ký đăng nhập và các bảng nghiệp vụ đào tạo, học phí, thiết bị, theo dõi tư vấn. V5 giữ nguyên user/mật khẩu hiện có và không chèn dữ liệu nghiệp vụ mẫu.
- V6 chuyển bộ role cũ do V1–V5 tạo sang ID cố định 1–7, gán tài khoản `USER` cũ về `HR` (7) và `SERVICE_MANAGER` về `MANAGER` (6). Giữ nguyên ID tài khoản, mật khẩu và dữ liệu nghiệp vụ; bộ role đã đúng theo bảng dưới đây được giữ nguyên. Role ngoài bộ chuẩn không được tự cấp quyền.
- V7 bổ sung `m_user.credentials_version` cùng bảng mã xác nhận/giới hạn gửi mail để khôi phục mật khẩu. Giữ nguyên tài khoản, role, mật khẩu và dữ liệu nghiệp vụ; không tạo mã xác nhận hoặc tài khoản mới khi migration.
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

## Dashboard theo vai trò

Sau khi đăng nhập, `G5_FE1` tự mở dashboard theo `m_user.role_id`, đồng thời kiểm tra `m_role.code` đúng với ID quy định. Tải lại trang giữ đúng dashboard; mở URL của role khác bị chặn ở cả giao diện và API. Mỗi dashboard có file React riêng:

| role_id | Role | Giao diện | File trong `G5_FE1/src/dashboards` | Đường dẫn |
| --- | --- | --- | --- | --- |
| 1 | `ADMIN` | Tổng quan hệ thống | `AdminDashboard.jsx` | `/dashboard/admin` |
| 2 | `LECTURER` | Giảng viên | `LecturerDashboard.jsx` | `/dashboard/lecturer` |
| 3 | `STUDENT` | Học viên | `StudentDashboard.jsx` | `/dashboard/student` |
| 4 | `ACCOUNTANT` | Tổng quan tài chính đào tạo | `AccountantDashboard.jsx` | `/dashboard/accountant` |
| 5 | `IT_SUPPORT` | Tổng quan thiết bị và hỗ trợ | `ITSupportDashboard.jsx` | `/dashboard/it-support` |
| 6 | `MANAGER` | Quản lý học viên | `ManagerDashboard.jsx` | `/dashboard/manager` |
| 7 | `HR` | Tuyển sinh và tuyển dụng | `HRDashboard.jsx` | `/dashboard/hr` |

Chỉ chấp nhận đúng cặp ID/mã trong bảng, phân biệt chữ hoa/thường. ID ngoài 1–7, thiếu role hoặc mã không khớp ID bị từ chối với HTTP `403` và thông báo lỗi vai trò; không đăng nhập hoặc tự chuyển sang dashboard khác. Không nhận các mã cũ như `USER`, `SERVICE_MANAGER`, `Lecture` hay tiền tố `ROLE_`. Phiên đang có cũng bị chặn nếu role trong database đổi thành không hợp lệ. Dashboard giảng viên/học viên chỉ hiển thị lịch, điểm, học phí, thiết bị thuộc tài khoản đó.

Số liệu được lấy qua `GET /api/dashboard` hoặc `/api/dashboard/{slug}` từ database. Các bảng nghiệp vụ mới chưa có dữ liệu sẽ hiển thị số 0/danh sách trống; điểm chưa công bố hiển thị `—`. Cần gán role cho tài khoản và nhập dữ liệu nghiệp vụ thật để có đầy đủ số liệu. Xem [hướng dẫn gán role, nguồn dữ liệu và API dashboard](docs/DASHBOARDS.md).

## Quản lý tài khoản và quên mật khẩu

Quản trị viên (`role_id = 1`, `code = ADMIN`) mở **Tài khoản người dùng** trong menu hoặc nút **Quản lý tài khoản** ở dashboard. Trang `/admin/users` cho phép tìm theo họ tên, email, số điện thoại, lọc quyền hạn và phân trang, cùng đầy đủ chức năng tạo, xem chi tiết, sửa và xóa tài khoản. Khi tạo, nhập **họ và tên, email đăng nhập, số điện thoại, quyền hạn, mật khẩu và xác nhận mật khẩu**; số điện thoại không bắt buộc. Ô chọn quyền hạn lấy 7 role hợp lệ từ database; email trùng bị từ chối. Mật khẩu cần ít nhất 8 ký tự Unicode và tối đa 72 byte UTF-8, hai ô phải khớp chính xác. Tài khoản mới đăng nhập ngay bằng mật khẩu admin đã đặt, không cần cấu hình Gmail.

Khi sửa, để trống cả hai ô mật khẩu để giữ mật khẩu hiện tại hoặc nhập cả hai để đổi. Admin đổi email, quyền hạn hoặc mật khẩu sẽ buộc tài khoản đó đăng nhập lại. Có thể **Vô hiệu hóa**, **Kích hoạt lại**, **Khóa tài khoản** hoặc **Mở khóa** sau khi xác nhận: dữ liệu được giữ nguyên, phiên và mã/token khôi phục cũ hết hiệu lực mỗi lần đổi trạng thái. Tài khoản chỉ được đăng nhập khi vừa được kích hoạt vừa không bị khóa và có role hợp lệ; bật lại không tự mở khóa, mở khóa không tự bật lại. Không thể tự hạ quyền/vô hiệu hóa/khóa/xóa tài khoản đang đăng nhập hoặc vô hiệu hóa/khóa/xóa quản trị viên hoạt động cuối cùng. Xóa tài khoản chưa có dữ liệu nghiệp vụ là xóa thực sự; tài khoản đang gắn với lịch sử tuyển dụng, lớp học, học phí, thiết bị hoặc tư vấn sẽ báo lỗi và giữ dữ liệu. Bổ sung này không cần migration mới; khởi động lại backend/frontend để nhận code.

Sau thao tác quản trị thành công và khi quay về Dashboard, giao diện tải lại số liệu từ database. **Đang hoạt động** chỉ đếm tài khoản được kích hoạt và không bị khóa; các mục bị khóa và vô hiệu hóa đếm từng cờ trạng thái độc lập nên có thể cùng tính một tài khoản.

Tại màn đăng nhập, bấm **Quên mật khẩu?**, nhập email đã đăng ký, nhận mã 6 chữ số, xác nhận mã rồi nhập mật khẩu mới và xác nhận mật khẩu. Mã có hiệu lực 10 phút; sau khi xác nhận, phần đặt mật khẩu có hiệu lực 5 phút. Gửi lại mã sau ít nhất 60 giây. Đặt lại thành công sẽ quay về đăng nhập và kết thúc các phiên cũ của tài khoản.

Để gửi mã qua Gmail, cấu hình các biến sau trong terminal chạy backend hoặc phần Environment variables của IDE. Dùng [mật khẩu ứng dụng Google](https://support.google.com/accounts/answer/185833?hl=vi) sau khi bật Xác minh 2 bước:

```powershell
$env:SMTP_HOST = "smtp.gmail.com"
$env:SMTP_PORT = "587"
$env:SMTP_USER = "<email-Gmail-gui-ma>"
$env:SMTP_PASSWORD = "<mat-khau-ung-dung-Google>"
$env:SMTP_FROM = $env:SMTP_USER
$env:SMTP_AUTH = "true"
$env:SMTP_STARTTLS = "true"
$env:SMTP_SSL = "false"
mvn -f .\G5_BE\pom.xml spring-boot:run
```

Gmail hỗ trợ `smtp.gmail.com` với TLS trên cổng 587 theo [tài liệu Google](https://developers.google.com/workspace/gmail/imap/imap-smtp). Spring Boot chạy bằng Maven/IDE không tự nạp `.env`; Docker Compose nhận các biến SMTP từ `.env`. Thiếu cấu hình gửi mail sẽ báo `MAIL_NOT_CONFIGURED` và không gửi mã. Xem [hướng dẫn tài khoản, khôi phục mật khẩu và kiểm tra Gmail](docs/ACCOUNT_MANAGEMENT.md).

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
npm.cmd --prefix .\G5_FE1 test
npm.cmd --prefix .\G5_FE1 run build
npm.cmd --prefix .\G5_FE2 run build
```

Backend tests dùng H2 ở chế độ PostgreSQL, chạy các migration thật, kiểm tra đăng ký tư vấn, validation, CSRF, phân quyền và luồng tuyển dụng. Website `G5_FE2` build đầy đủ các trang HTML.
