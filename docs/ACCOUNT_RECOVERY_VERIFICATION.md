# Kiểm tra quản lý tài khoản và quên mật khẩu — 29/10/2026

## Thông báo trạng thái đăng nhập và cấu hình mail

- Đăng nhập tài khoản bị khóa trả `401 ACCOUNT_LOCKED`; bị vô hiệu hóa trả `401 ACCOUNT_DISABLED`, kèm thông báo tiếng Việt và hướng dẫn liên hệ quản trị viên. Cả hai trạng thái cùng bật ưu tiên bị khóa. Không tạo phiên hợp lệ hoặc nhật ký đăng nhập thành công; mở khóa/kích hoạt lại cho phép đăng nhập mới.
- Kiểm tra lại trạng thái database sau xác thực, bao gồm trường hợp admin đổi cờ ngay trong lúc kiểm tra mật khẩu. Tài khoản hoạt động nhập sai mật khẩu và email không tồn tại vẫn nhận lỗi chung. CSRF tiếp tục bắt buộc.
- Cấu hình mail có `SMTP_FROM` trống dùng địa chỉ tại `spring.mail.username`. Kiểm thử SMTP loopback xác nhận cả envelope và MIME From; bốn bài kiểm tra Spring Boot nạp `application.yml` xác nhận cấu hình qua biến môi trường, nhập trực tiếp, địa chỉ gửi riêng và thiếu thông tin xác thực. Log cấu hình chỉ ghi tên trường còn thiếu.

Kết quả ngày 29/10: **159 test backend đạt, 0 thất bại/lỗi/bỏ qua**, gồm **12 trường hợp đăng nhập theo trạng thái**, **4 test nạp cấu hình mail** và **5 test SMTP**. Chrome đạt **10/10 nhóm kiểm tra login**, không có runtime exception: đúng thông báo, không chuyển dashboard sau lỗi, thử lại sau khi mở khóa, chống gửi lặp và hiển thị 390px. Browser dùng API fixture; backend dùng H2 và SMTP loopback. Không thử gửi Gmail thật hoặc sửa tài khoản PostgreSQL.

## Khóa/mở khóa và cập nhật số liệu dashboard

- `PATCH /api/admin/users/{id}/lock` kiểm tra ADMIN, CSRF và JSON boolean; chặn tự khóa, xử lý `400`/`404`/`409`, giữ dữ liệu. Thay đổi thực sự tăng `credentials_version`, gửi lại cùng trạng thái không tăng.
- Khóa chặn đăng nhập và thu hồi phiên, mã/token khôi phục cũ; mở khóa không khôi phục phiên/mã/token cũ hoặc tự kích hoạt tài khoản bị vô hiệu hóa.
- Dashboard tải lại API sau mỗi thao tác tài khoản thành công, khi quay lại bằng menu/lịch sử trình duyệt và khi cửa sổ Admin trở lại. Response của request đã hủy không ghi đè dữ liệu mới.
- Kiểm tra API thật trong H2 sau 6 chuyển đổi trạng thái: tổng giữ nguyên, đang hoạt động đếm tài khoản bật và không khóa, bị khóa/vô hiệu hóa đếm độc lập. Browser cũng kiểm tra tạo/xóa cập nhật tổng và hủy/lỗi không thay đổi số liệu.

Kết quả ở lần bổ sung khóa/mở khóa: **141 test backend đạt, 0 lỗi**, gồm **37 test quản lý tài khoản**; **10 test frontend đạt**, production build thành công. Chrome đạt **18/18 nhóm kiểm tra**, không có runtime exception; đã kiểm tra xác nhận/hủy, CSRF, gửi lặp, `401`/`409`, counters, quay lại/focus, response đến muộn và giao diện 390px. Helper `account-lock-browser.mjs` và kết quả nằm trong cache bị Git ignore. Browser dùng API fixture, backend dùng H2; không thay đổi tài khoản trong PostgreSQL đang dùng. Không cần migration mới.

## Bổ sung vô hiệu hóa và kích hoạt lại tài khoản

- `PATCH /api/admin/users/{id}/status` chỉ cho ADMIN, cần CSRF và nhận boolean `accountEnabled`; đầu vào thiếu/null/sai kiểu bị từ chối. Không tồn tại trả `404`, tự vô hiệu hóa trả `409`.
- Vô hiệu hóa chặn đăng nhập và vô hiệu hóa phiên cũ ngay ở lần gọi API bảo vệ tiếp theo (`401`, `SESSION_EXPIRED`). Bật lại cho phép đăng nhập mới bằng mật khẩu hiện có; phiên và mã/token khôi phục trước đó không sống lại.
- Đổi trạng thái giữ nguyên thông tin, mật khẩu, role và dữ liệu nghiệp vụ. Gửi lại cùng trạng thái không tăng phiên bản. Bật lại không gỡ `accountLocked`.
- Giao diện có xác nhận/hủy cho cả hai thao tác, chặn gửi lặp và tự vô hiệu hóa, xử lý lỗi `401`/`409`, cập nhật danh sách sau thành công. Có thông báo riêng khi tài khoản vẫn đang khóa.
- `app.mail.from` mặc định lấy địa chỉ đã cấu hình tại `spring.mail.username`, hỗ trợ nhập Gmail trực tiếp trong `application.yml`; hướng dẫn cấu hình và lấy mã đã được bổ sung trong [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).

Kết quả ở lần bổ sung vô hiệu hóa: **133 test backend đạt**, gồm **29 test quản lý tài khoản**; **10 test frontend đạt**, production build thành công. Chrome đạt **9/9 nhóm kiểm tra trạng thái**, không có runtime exception; đã kiểm tra giao diện desktop và 390px. Browser dùng fixture cho toàn bộ `/api`, backend dùng H2; không thay đổi tài khoản trong PostgreSQL và chưa kiểm tra gửi đến Gmail thật.

## Bổ sung mật khẩu khi tạo và CRUD đầy đủ

Đã kiểm tra lần bổ sung này:

- ADMIN tạo tài khoản với mật khẩu/xác nhận hợp lệ và tài khoản đó đăng nhập ngay; thiếu mật khẩu, xác nhận sai, dưới 8 ký tự Unicode hoặc quá 72 byte UTF-8 bị chặn. Tạo/đăng nhập không phụ thuộc Gmail SMTP.
- Xem chi tiết qua API lấy dữ liệu mới nhất; danh sách, chi tiết, phản hồi tạo/sửa và form không đưa mật khẩu/hash hiện tại ra ngoài.
- Sửa thông tin khi bỏ trống cả hai ô mật khẩu giữ nguyên hash; đổi mật khẩu yêu cầu xác nhận chính xác. Thay email/role/mật khẩu vô hiệu hóa phiên và mã/token khôi phục cũ; đổi họ tên/điện thoại giữ phiên.
- Xóa tài khoản chưa có dữ liệu nghiệp vụ trả `204`, xóa kèm nhật ký đăng nhập/mã khôi phục và chặn phiên cũ. Bản ghi không tồn tại trả `404`; tự xóa, xóa quản trị viên hoạt động cuối cùng hoặc tài khoản có dữ liệu nghiệp vụ trả `409`, không làm mất dữ liệu liên quan.
- Các API tạo/xem/sửa/xóa kiểm tra đúng ADMIN từ database; các yêu cầu thay đổi cần CSRF. Sau xóa, danh sách/tổng số/phân trang cập nhật; giao diện hiển thị lỗi nghiệp vụ và có bước xác nhận xóa.
- Luồng quên mật khẩu qua email, giới hạn gửi/thử mã, xác nhận một lần và vô hiệu hóa phiên tiếp tục hoạt động.

Backend chạy thành công **126 test, 0 thất bại, 0 lỗi, 0 bỏ qua**, gồm **22 test quản lý tài khoản**. Các test đã kiểm tra riêng từng loại trong 10 liên kết nghiệp vụ khiến thao tác xóa trả `409`, xóa tài khoản trống cùng dữ liệu xác thực phụ thuộc, tái sử dụng email, chặn phiên đã xóa và vô hiệu hóa mã/token khi admin đổi mật khẩu. Luồng yêu cầu/hoàn tất khôi phục và xóa dùng cùng thứ tự khóa tài khoản trước bản ghi khôi phục.

Frontend: **10/10 test**, production build thành công. Chrome: **17/17 nhóm kiểm tra**, không có runtime exception. Có kiểm tra 6 trường tạo tài khoản, mật khẩu/xác nhận/Unicode/72 byte, payload/CSRF, GET chi tiết mới nhất, sửa không gửi mật khẩu khi bỏ trống, sửa có mật khẩu, hủy/xác nhận xóa, lỗi `409`, bảo vệ tài khoản admin, logout khi tự đổi mật khẩu và màn hình 390px cuộn tới nút tạo được.

## Kiểm tra các chức năng tiếp tục hoạt động

- Admin: chỉ ADMIN với đúng ID/mã trong database được tìm kiếm/tạo/xem/sửa/xóa; tìm kiếm và phân trang hoạt động; email không trùng khi khác chữ hoa/thường; đầu vào và role sai bị chặn; mật khẩu và trạng thái tài khoản được giữ khi chỉ sửa thông tin; tự hạ quyền và tự xóa bị chặn.
- Khôi phục: phản hồi yêu cầu không tiết lộ email tồn tại, mã/token không lộ trong response hoặc log, mã/token hết hạn hoặc đã dùng bị từ chối, giới hạn gửi/thử mã hoạt động, xác nhận mật khẩu và giới hạn UTF-8 được kiểm tra. Đặt lại mật khẩu tăng phiên bản thông tin xác thực và chặn phiên đăng nhập cũ.
- SMTP: JavaMail gửi thư thật qua máy SMTP loopback trong test; MIME nhận đúng địa chỉ gửi/nhận và giữ nguyên mã có số 0 ở đầu. Không kết nối Gmail thật hoặc gửi thư tới người dùng thật.
- Migration V7: kiểm tra dữ liệu của 18 bảng trước đó được giữ nguyên, cột `credentials_version` ban đầu bằng 0, hai bảng khôi phục ban đầu trống và migration không chạy lặp.
- Luồng email → mã 6 số → mật khẩu/xác nhận vẫn hoạt động; kiểm thử tích hợp khôi phục, gửi mail và vô hiệu hóa phiên tiếp tục thành công.

Kiểm tra trình duyệt dùng API fixture trong helper bị Git ignore, không đưa dữ liệu mẫu vào ứng dụng hoặc database. Các bài tích hợp backend dùng H2 PostgreSQL mode và SMTP local. Lần bổ sung CRUD không thêm migration và không xóa/sửa tài khoản trong PostgreSQL đang dùng.

Để gửi qua Gmail thật, cần cấu hình tài khoản gửi và mật khẩu ứng dụng, sau đó kiểm tra việc nhận thư. Xem [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md).
