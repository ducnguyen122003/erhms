# Quản lý tài khoản và khôi phục mật khẩu

## Tạo, xem, sửa, vô hiệu hóa, khóa và xóa tài khoản

Mặc định đăng nhập bằng `role_id = 1`, `m_role.code = ADMIN`. Chọn **Tài khoản** trên menu hoặc **Quản lý tài khoản** ở dashboard. Từ V8, quyền màn được lưu trong `role_function`; một role khác cũng truy cập được nếu được cấp quyền tương ứng. Hướng dẫn các màn mới, phân quyền, nhật ký và cấu hình: [ADMIN_WORKSPACE.md](ADMIN_WORKSPACE.md).

- Nhập họ tên, email hoặc số điện thoại vào ô tìm kiếm. Có thể lọc quyền hạn và chuyển trang.
- Bấm **Tạo tài khoản**, nhập họ và tên, email đăng nhập, số điện thoại nếu có, chọn quyền hạn và nhập **Mật khẩu**, **Xác nhận mật khẩu**. Họ tên, email, quyền hạn và hai ô mật khẩu bắt buộc.
- Ô quyền hạn lấy từ database, chỉ chấp nhận `1/ADMIN`, `2/LECTURER`, `3/STUDENT`, `4/ACCOUNTANT`, `5/IT_SUPPORT`, `6/MANAGER`, `7/HR` đúng mã và chữ hoa.
- Chọn **Xem** để mở màn chi tiết chỉ đọc với ba tab thông tin tài khoản, vai trò và lịch sử thao tác. Không có ô nhập hay nút lưu; dùng **Chỉnh sửa tài khoản** để chuyển sang màn sửa riêng. Có thể chọn trực tiếp **Chỉnh sửa** ở danh sách. Mật khẩu/hash không được trả về giao diện.
- Ở màn cập nhật, sửa thông tin rồi bấm **Lưu tài khoản**. Mở mục **Đổi mật khẩu (tùy chọn)** khi cần; để cả hai ô trống để giữ nguyên mật khẩu. Email không được trùng tài khoản khác; lỗi hiển thị tại biểu mẫu.
- Chọn **Trạng thái** gồm Hoạt động, Vô hiệu hóa, Bị khóa hoặc Vô hiệu hóa và bị khóa; nhập ghi chú thay đổi rồi bấm **Lưu tài khoản**. Thông tin và trạng thái được lưu cùng lúc, ghi chú được ghi vào nhật ký.
- Khóa/vô hiệu hóa chặn đăng nhập và thu hồi phiên, mã/token khôi phục cũ; dữ liệu nghiệp vụ được giữ nguyên. Không được tự khóa/vô hiệu hóa/đổi role hoặc làm mất Admin cuối cùng. Hai cờ độc lập: nếu muốn chỉ bỏ một cờ, chọn đúng trạng thái còn lại.
- Bấm **Xóa** rồi xác nhận để xóa tài khoản chưa có dữ liệu nghiệp vụ liên quan. Không thể xóa chính tài khoản đang đăng nhập hoặc quản trị viên hoạt động cuối cùng. Tài khoản có lịch sử tuyển dụng, lớp học, học phí, thiết bị hoặc tư vấn liên quan sẽ báo lỗi và được giữ nguyên để bảo toàn dữ liệu.

Mật khẩu cần ít nhất 8 ký tự Unicode và tối đa 72 byte UTF-8; phần xác nhận phải khớp chính xác, kể cả khoảng trắng và chữ hoa/thường. Backend chỉ lưu BCrypt hash. Tài khoản mới có thể đăng nhập ngay bằng email và mật khẩu admin đã nhập; tạo tài khoản và đăng nhập không cần cấu hình Gmail. **Quên mật khẩu?** vẫn dùng để khôi phục khi người dùng quên mật khẩu.

Quản trị viên đang đăng nhập giữ quyền `ADMIN` của chính mình. Khi admin sửa email, quyền hạn, mật khẩu hoặc thay đổi trạng thái bật/vô hiệu hóa hay khóa/mở khóa, phiên của tài khoản bị sửa hết hiệu lực; người dùng đăng nhập lại bằng thông tin mới khi tài khoản được phép hoạt động. Tự sửa email hoặc mật khẩu sẽ trở về màn đăng nhập. Sửa họ tên hoặc điện thoại giữ phiên hiện tại. Mã/token khôi phục được cấp trước những thay đổi này cũng hết hiệu lực.

Sau khi tạo, sửa, xóa, bật/vô hiệu hóa hoặc khóa/mở khóa thành công, giao diện tải lại số liệu dashboard từ database. Chuyển về **Dashboard** cũng lấy số liệu mới, không cần tải lại cả trang. **Tổng tài khoản** đếm toàn bộ tài khoản; **Đang hoạt động** đếm tài khoản được kích hoạt và không bị khóa; **Tài khoản bị khóa** đếm cờ khóa; mục **Tài khoản bị vô hiệu hóa** đếm cờ vô hiệu hóa. Một tài khoản có thể vừa bị vô hiệu hóa vừa bị khóa nên xuất hiện trong cả hai mục cần chú ý; không cộng các mục này để suy ra tổng tài khoản.

## Thông báo khi tài khoản không được đăng nhập

Màn đăng nhập hiển thị thông báo theo trạng thái tài khoản do backend trả về:

- Tài khoản bị khóa: **Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên để khôi phục.** (`401`, `ACCOUNT_LOCKED`).
- Tài khoản bị vô hiệu hóa: **Tài khoản đang bị vô hiệu hóa. Vui lòng liên hệ quản trị viên để khôi phục.** (`401`, `ACCOUNT_DISABLED`).

Nếu cả hai trạng thái cùng bật, ưu tiên thông báo bị khóa. Đăng nhập bị từ chối không tạo phiên đăng nhập thành công hoặc nhật ký đăng nhập thành công; giao diện giữ màn đăng nhập và cho phép thử lại. Email không tồn tại hoặc mật khẩu sai của tài khoản đang hoạt động vẫn dùng thông báo lỗi đăng nhập chung. Sau khi quản trị viên mở khóa và kích hoạt lại nếu cần, người dùng có thể đăng nhập bằng mật khẩu hiện có.

## Quên mật khẩu

1. Mở web quản lý tại http://localhost:8082 rồi bấm **Quên mật khẩu?**.
2. Nhập email đã đăng ký và gửi yêu cầu.
3. Mở thư ERHMS, nhập đủ mã 6 chữ số, kể cả số `0` ở đầu. Mã có hiệu lực 10 phút. Có thể gửi lại sau 60 giây; chỉ mã mới nhất có hiệu lực.
4. Sau khi xác nhận, nhập **Mật khẩu mới** và **Xác nhận mật khẩu mới**, rồi lưu trong vòng 5 phút.
5. Đăng nhập bằng email đó và mật khẩu mới. Các phiên cũ đã bị kết thúc.

Mật khẩu cần ít nhất 8 ký tự Unicode và tối đa 72 byte UTF-8; hai ô phải trùng hoàn toàn. Mỗi mã có tối đa 5 lần thử. Hệ thống giới hạn 5 yêu cầu/email/giờ và 20 yêu cầu/IP/giờ. Giao diện hiển thị thời gian chờ khi cần gửi lại.

Thông báo yêu cầu mã dùng chung cho email có và không có trong hệ thống. Chỉ tài khoản hoạt động, không khóa và có role hợp lệ mới nhận mail. Nếu chưa nhận, kiểm tra email, thư rác và cấu hình gửi thư; yêu cầu thành công chỉ xác nhận hệ thống đã tiếp nhận.

## Cấu hình gửi mã bằng Gmail

Bật Xác minh 2 bước cho tài khoản Gmail gửi thư, rồi tạo một mật khẩu ứng dụng cho backend. Google yêu cầu Xác minh 2 bước để tạo mật khẩu ứng dụng; một số tài khoản tổ chức hoặc chế độ bảo vệ có thể không cung cấp tùy chọn này. Xem [hướng dẫn Google](https://support.google.com/accounts/answer/185833?hl=vi).

### Cấu hình trực tiếp trong `application.yml`

Có thể cấu hình trực tiếp trong `G5_BE/src/main/resources/application.yml`. Thay các mục `spring.mail` và `app.mail` đang có bằng giá trị tương ứng trong ví dụ dưới, giữ nguyên các cấu hình database, JPA, Flyway và những mục khác. **Không thêm hai khóa gốc `spring:` hoặc `app:` trùng với khóa đã có.** Thay các giá trị mẫu bằng thông tin tài khoản gửi thư của bạn:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: "<email-Gmail-gui-ma>"
    password: "<mat-khau-ung-dung-Google>"
    default-encoding: UTF-8
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
      mail.smtp.ssl.enable: false
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
app:
  mail:
    from: ${spring.mail.username}
```

Khởi động lại backend sau khi sửa. Không commit mật khẩu ứng dụng thật lên Git. Cấu hình bằng biến môi trường ở phần dưới giúp giữ thông tin bí mật ngoài source.

`spring.mail.username` là **Gmail gửi thư**, `spring.mail.password` là **mật khẩu ứng dụng Google**, thường gồm 16 ký tự. Sau khi bật Xác minh 2 bước, mở [trang Mật khẩu ứng dụng](https://myaccount.google.com/apppasswords) để tạo theo [hướng dẫn Google](https://support.google.com/mail/answer/185833?hl=vi). Không nhập mật khẩu đăng nhập Gmail thông thường hoặc mã xác minh 2 bước vào trường này.

### Cấu hình bằng biến môi trường

Giữ cấu hình `${SMTP_...}` hiện có trong `application.yml` nếu dùng cách này. Đặt các biến trong đúng terminal trước khi chạy backend, hoặc phần Environment variables của cấu hình Run trong IDE:

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

Cổng 587 dùng STARTTLS cho máy chủ `smtp.gmail.com`, theo [tài liệu SMTP của Google](https://developers.google.com/workspace/gmail/imap/imap-smtp). `SMTP_FROM` mặc định dùng `SMTP_USER`; dùng cùng địa chỉ Gmail gửi thư. `SMTP_PASSWORD` là mật khẩu ứng dụng, không phải mật khẩu đăng nhập web ERHMS. Khi đổi mật khẩu tài khoản Google, cần tạo lại mật khẩu ứng dụng theo [quy định của Google](https://support.google.com/accounts/answer/185833?hl=vi).

Spring Boot chạy bằng Maven hoặc IDE **không tự đọc `.env`** ở thư mục gốc. Khi chạy Docker Compose, khai báo các biến trên trong `.env`, rồi tạo lại service backend để nhận cấu hình. Không đưa mật khẩu ứng dụng vào source hoặc chia sẻ trong chat.

Các giá trị mặc định là host `smtp.gmail.com`, port `587`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_SSL=false`. Phải cung cấp tài khoản gửi và mật khẩu ứng dụng; địa chỉ gửi dùng `SMTP_FROM` hoặc `SMTP_USER`. Thiếu cấu hình cần thiết trả `503` với `code = MAIL_NOT_CONFIGURED`; chức năng đăng nhập và quản lý tài khoản vẫn dùng được.

### Khi web báo `MAIL_NOT_CONFIGURED`

Thông báo này phát sinh trước khi backend kết nối Gmail: một giá trị bắt buộc đang trống. Log backend ghi tên các cấu hình còn thiếu (`spring.mail.host`, `spring.mail.username`, `spring.mail.password`, `app.mail.from`) và không ghi giá trị, mật khẩu hoặc mã xác nhận.

- Nếu giữ `${SMTP_USER:}` và `${SMTP_PASSWORD:}` trong `application.yml`, phải đặt hai biến tương ứng trong đúng tiến trình chạy backend. Lệnh `$env:...` ở một terminal không thay đổi backend đã chạy hoặc cấu hình Run của IDE; dừng backend rồi chạy lại trong terminal đó, hoặc đặt biến trong cấu hình Run.
- Nếu nhập trực tiếp trong `application.yml`, sửa đúng `spring.mail.username` và `spring.mail.password`, không phải khối ví dụ trong tài liệu. Khởi động lại backend. `app.mail.from` trống, kể cả `SMTP_FROM` được đặt thành chuỗi rỗng, sẽ dùng địa chỉ tại `spring.mail.username`.
- Nếu dùng Docker, cập nhật cấu hình rồi chạy `docker compose up -d --build backend` tại thư mục gốc để tạo lại backend với image và môi trường mới. Spring Boot chạy trực tiếp bằng IDE/Maven không tự đọc `.env` của Docker.

Sai mật khẩu ứng dụng hoặc không kết nối được SMTP là lỗi ở bước gửi thư; khác với lỗi thiếu cấu hình này. Không cần bật log nội dung thư hoặc cung cấp mật khẩu để xác định tên cấu hình còn thiếu.

### Khi log báo không gửi được email khôi phục mật khẩu

Dòng cảnh báo **Không gửi được email khôi phục mật khẩu. Kiểm tra cấu hình SMTP.** chỉ xác nhận tác vụ gửi thư đã thất bại; riêng dòng này chưa đủ để xác định nguyên nhân. Lỗi xảy ra sau khi hệ thống tiếp nhận yêu cầu nên thông báo thành công trên web không đảm bảo thư đã được gửi.

**Nếu đang dùng mật khẩu đăng nhập Gmail thông thường ở `SMTP_PASSWORD` hoặc `spring.mail.password`, cần thay bằng mật khẩu ứng dụng.** Đăng nhập đúng tài khoản Google đã đặt ở `SMTP_USER` hoặc `spring.mail.username`, bật Xác minh 2 bước, mở [Mật khẩu ứng dụng](https://myaccount.google.com/apppasswords) rồi tạo mật khẩu dành cho backend. Cập nhật cấu hình bằng mật khẩu ứng dụng vừa tạo; dừng backend và chạy lại trong đúng terminal hoặc cấu hình Run của IDE đã sửa. Thay biến môi trường ở một terminal khác không cập nhật tiến trình backend đang chạy.

Với Gmail, đối chiếu các giá trị `smtp.gmail.com`, cổng `587`, xác thực `true`, STARTTLS `true`, SSL trực tiếp `false`. Dùng đúng địa chỉ Gmail gửi thư làm username và địa chỉ From. Password phải là **mật khẩu ứng dụng Google 16 ký tự**, không phải mật khẩu đăng nhập Gmail, mật khẩu ERHMS hoặc mã 6 số. Nếu Google hiển thị mật khẩu theo nhóm, nhập 16 ký tự liền nhau, không kèm khoảng trắng phân nhóm. Khi đổi mật khẩu Google, mật khẩu ứng dụng cũ bị thu hồi; cần tạo lại theo [hướng dẫn Google](https://support.google.com/accounts/answer/185833?hl=vi).

Để kiểm tra DNS/kết nối TCP từ chính máy chạy backend trên Windows:

```powershell
Test-NetConnection smtp.gmail.com -Port 587
```

`TcpTestSucceeded: False` cần kiểm tra mạng, DNS, VPN, tường lửa hoặc chính sách chặn SMTP. `True` chỉ xác nhận kết nối TCP, chưa xác nhận TLS, mật khẩu hoặc việc Gmail nhận thư. Backend chạy trong Docker hay máy chủ khác phải được kiểm tra từ đúng môi trường đó.

Các dấu `?` trong câu tiếng Việt ở console là vấn đề hiển thị/encoding riêng, không xác định nguyên nhân SMTP. Chọn UTF-8 cho terminal/console của IDE rồi khởi động lại để đọc đúng tiếng Việt. Không bật `mail.debug`, log nội dung thư hay gửi mật khẩu ứng dụng/mã xác nhận khi trao đổi lỗi.

### Lấy mã khôi phục trong Gmail

Có thể mở https://mail.google.com và đăng nhập bằng **email đã đăng ký trong ERHMS mà bạn vừa nhập ở bước Quên mật khẩu** để lấy mã. Kiểm tra Hộp thư đến và Thư rác/Spam, mở thư ERHMS và nhập mã 6 chữ số trong vòng 10 phút. Nếu email đó thuộc nhà cung cấp khác, mở hộp thư của nhà cung cấp tương ứng.

Gmail gửi thư trong cấu hình SMTP và email người nhận có thể khác nhau. Hệ thống gửi mã đến email người nhận đã đăng ký, không tự chuyển mã đến tài khoản Gmail gửi thư. Mã khôi phục 6 số do backend ERHMS tạo; đây là mã khác với mã xác minh 2 bước của Google và mật khẩu ứng dụng dùng để cấu hình SMTP.

## Kiểm tra sau khi cấu hình

Khởi động lại backend để nhận cấu hình và áp dụng migration V8 cho các màn quản trị mới; các migration trước chỉ chạy nếu chưa được áp dụng. Sau đó tải lại frontend. Đăng nhập admin, tạo tài khoản với một hộp thư bạn kiểm soát và mật khẩu hợp lệ, rồi kiểm tra đăng nhập trực tiếp bằng tài khoản đó. Vô hiệu hóa hoặc khóa tài khoản để kiểm tra đăng nhập/phiên cũ bị chặn, kiểm tra các số liệu khi quay về dashboard, sau đó bật lại/mở khóa và đăng nhập bằng cùng mật khẩu. Thử cả trường hợp tài khoản vừa vô hiệu hóa vừa bị khóa: mở khóa vẫn phải giữ trạng thái vô hiệu hóa và ngược lại. Sau khi cấu hình Gmail, thực hiện đầy đủ luồng quên mật khẩu. Xác nhận thư thực sự có trong hộp thư, mật khẩu mới đăng nhập được và mật khẩu cũ không còn dùng được.

Nếu không nhận thư, xem thư rác và trạng thái SMTP trong log backend, kiểm tra host/port, Xác minh 2 bước và mật khẩu ứng dụng. Mail được gửi sau khi transaction lưu mã hoàn tất; lỗi kết nối/gửi SMTP có thể xảy ra sau phản hồi yêu cầu. Không bật log nội dung thư, mã hoặc mật khẩu để xử lý sự cố.

Các kiểm thử tự động của ứng dụng có thể dùng H2 và máy SMTP cục bộ để kiểm tra mã, giới hạn thử, đổi mật khẩu và vô hiệu hóa phiên. Kết quả đó không xác nhận Gmail thật đã gửi đến hộp thư; kiểm tra Gmail cần cấu hình thực tế và bước nhận thư phía trên.

## Database và API

Migration V7 thêm `m_user.credentials_version BIGINT NOT NULL DEFAULT 0`, `password_reset_challenges` và `password_reset_rate_limits`. Dữ liệu user, role, hash mật khẩu và các bảng nghiệp vụ hiện có được giữ nguyên. Migration không tạo tài khoản hoặc mã khôi phục.

Mã và token khôi phục được lưu dạng hash. Đặt lại mật khẩu hoặc admin thay email/quyền hạn/mật khẩu/trạng thái `account_enabled` hay `account_locked` làm tăng `credentials_version` để vô hiệu hóa phiên và mã/token khôi phục cũ; chỉ biết email hoặc mã đã dùng không đủ để đặt lại mật khẩu. Bật/vô hiệu hóa chỉ cập nhật cờ `m_user.account_enabled`; khóa/mở khóa chỉ cập nhật cờ `m_user.account_locked`. Hai trạng thái độc lập, không xóa tài khoản hoặc dữ liệu nghiệp vụ. Gửi lại đúng trạng thái hiện có không tăng `credentials_version`.

Xóa là xóa bản ghi `m_user` thực sự, không phải đổi trạng thái tài khoản. Chỉ nhật ký đăng nhập `user_login_events` và mã khôi phục `password_reset_challenges` của tài khoản được xóa kèm qua khóa ngoại. Dữ liệu nghiệp vụ không được xóa dây chuyền. Nếu có dữ liệu liên quan, API trả `409`; nếu xóa thành công, phiên cũ của tài khoản không còn truy cập được API bảo vệ.

API quản trị, `PATCH /api/admin/users/{id}/status`, `PATCH /api/admin/users/{id}/lock` và ba bước `/api/auth/password-reset/{request,verify,complete}` được mô tả trong [API.md](API.md). Mọi POST/PUT/PATCH/DELETE đều cần CSRF token, kể cả các yêu cầu khôi phục chưa đăng nhập.
