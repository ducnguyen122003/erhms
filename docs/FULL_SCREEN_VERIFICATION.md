# Kiểm tra bộ màn ERHMS — 07/11/2026

Đối chiếu đủ **108 mã màn** trong workbook với manifest SVG và file giao diện. Các bước đăng nhập/khôi phục, trang cá nhân, tài khoản và dashboard hiện có được tái sử dụng; bổ sung 80 component nghiệp vụ, một màn đổi mật khẩu riêng và 9 component công khai.

## Backend

**193 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua.** Toàn bộ suite đã chạy; các thay đổi sau về điều kiện đóng hồ sơ/xung đột lịch được kiểm tra lại bằng toàn bộ 16 bài `ScreenWorkflowIntegrationTest`.

- Migration V1–V10 chạy trên H2 PostgreSQL mode; giữ tài khoản/dữ liệu cũ, không tạo dữ liệu nghiệp vụ minh họa.
- Mỗi một trong 80 mã nghiệp vụ mới được gọi GET với cả 7 vai trò: xác nhận endpoint tồn tại, quyền mặc định ✓ và từ chối role khác. Quyền ĐX chưa được cấp. Kiểm tra CSRF, thu hồi quyền, ID sai và hồ sơ ngoài phạm vi.
- Kiểm tra tư vấn → hợp đồng/ký → đối soát → xếp lớp; mã giao dịch trùng không thu lần hai, sai lệch/thu vượt nghĩa vụ không được xác nhận. Hợp đồng miễn phí cần kế toán xác nhận riêng, không tạo giao dịch 0 đồng.
- Kiểm tra lịch trùng, danh sách xung đột thực tế, lọc tuần và số liệu; giảng viên/Student chỉ thấy lịch trong phạm vi, Student không nhận roster người khác qua chi tiết buổi học.
- Manager không ghi điểm danh/điểm thay Lecturer. Điểm chưa công bố không xuất hiện cho Student. Chốt điểm danh/đóng hồ sơ giữ khóa dữ liệu và kiểm tra phiên bản.
- Kiểm tra cấp phát khi đủ cọc, sở hữu biên bản, thu hồi thực nhận, phê duyệt bồi thường, khấu trừ/hoàn cọc đúng một lần, phân tách IT và kế toán.
- Kiểm tra nghỉ học không được tính hoàn thành khóa và không đóng khi còn thiết bị/nghĩa vụ; dashboard HR/Manager/Accountant không lộ hồ sơ của người phụ trách khác.
- Tệp minh chứng chỉ nhận PDF/PNG/JPEG, cần xác thực/quyền hồ sơ; tệp chưa liên kết chỉ người tải lên đọc được, Student chỉ đọc minh chứng hợp đồng của mình.

## Frontend

**20 test đạt**; production build thành công. Bộ test kiểm tra đủ 108 mapping/file, số quyền từng role đúng workbook, quyền ĐX, chặn ID/role sai, menu theo grant, điều hướng dashboard và giữ route quản trị cũ.

## Chrome

**18 nhóm kiểm tra bộ màn đạt:** hiển thị đủ 80 màn nghiệp vụ theo role, 9 màn công khai, tạo khóa với cấu hình đánh giá/giảng viên và CSRF, giữ thông báo thành công, bảng điểm Manager chỉ đọc/Lecturer được công bố, tiếp nhận/xác nhận thanh toán riêng, màn đổi mật khẩu riêng, biên bản Student chỉ xem, gửi Contact chỉ chuyển thành công sau receipt và các màn ở 390px không tràn ngang trang.

**3 nhóm kiểm tra bổ sung đạt:** nhập liệu qua thời điểm debounce vẫn giữ bản nháp, từ chối điều hướng không mất dữ liệu, xung đột lịch hiển thị trạng thái lỗi và sửa/lưu lại được, lớp đã đóng không có nút nhập/công bố điểm.

**10 nhóm kiểm tra hồi quy tài khoản đạt:** tạo thành công giữ thông báo sau chuyển màn, điện thoại `null`/thiếu trường/trống lưu nhiều lần được, số sai vẫn bị chặn và lỗi tạo không mất thông tin đã nhập.

Không có runtime exception hoặc cảnh báo input React trong các kiểm tra đã ghi nhận. Helper và kết quả JSON/ảnh ở thư mục Git ignore `G5_FE1/node_modules/.cache/erhms-qa/`:

- `full-screens-browser.mjs`, `full-screens-browser-results.json`, `full-screens-*.png`
- `screen-conflict-browser.mjs`, `screen-conflict-browser-results.json`
- `account-form-regression.mjs`, `account-form-regression-results.json`

API trình duyệt được giả lập; backend dùng H2/SMTP loopback trong suite có sẵn. Không ghi PostgreSQL đang dùng, không gửi Gmail thật, không giao dịch với ngân hàng thật.

```powershell
mvn -f G5_BE/pom.xml test
npm.cmd --prefix G5_FE1 test
npm.cmd --prefix G5_FE1 run build
```

Hướng dẫn vận hành và các giới hạn tích hợp: [FULL_SCREEN_WORKSPACE.md](FULL_SCREEN_WORKSPACE.md). Danh mục: [SCREEN_ROUTE_COVERAGE.md](SCREEN_ROUTE_COVERAGE.md).
