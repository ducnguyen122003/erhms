# Kiểm tra không gian quản trị — 07/11/2026

## Sửa thông báo tạo tài khoản và số điện thoại trống

- Frontend: **13 test đạt**, production build thành công.
- Chrome: **10/10 nhóm kiểm tra đạt**, không có runtime exception hoặc cảnh báo React về input. Đã kiểm tra tải/lưu nhiều lần khi điện thoại là `null` hoặc thiếu trường, xóa số đã nhập, chặn số sai định dạng, tạo thất bại giữ dữ liệu và không báo thành công, tạo không có điện thoại vẫn hiện thông báo thành công sau khi chuyển màn/tải lại quyền, lưu tiếp tài khoản vừa tạo, quay lại không lặp thông báo cũ và chuyển hướng dashboard.
- Helper và kết quả: `G5_FE1/node_modules/.cache/erhms-qa/account-form-regression.mjs`, `account-form-regression-results.json`; ảnh `account-form-create-success.png`, `account-form-null-phone-saved.png`. API được giả lập, không ghi dữ liệu thật. Backend hiện đã chấp nhận điện thoại trống và chuẩn hóa thành `null`; lần sửa này chỉ thay đổi frontend.

## Bổ sung màn xem tài khoản chỉ đọc

- Backend mới nhất: **177 test đạt, 0 thất bại/lỗi/bỏ qua**. Có thêm 5 bài tích hợp xác nhận GET hồ sơ không thay đổi tài khoản/audit, không lộ thông tin xác thực, giới hạn lịch sử đúng tài khoản, quyền màn hiện tại và lỗi `401`/`403`/`404`. Sự kiện tạo mới được liên kết với mã tài khoản để đọc ngày tạo thật; dữ liệu cũ không có sự kiện trả `null`.
- Frontend: **13 test đạt**, production build thành công. Đường dẫn `/admin/users/{id}/view` dùng `UserDetailPage.jsx`, tách khỏi trang sửa và kiểm tra `USER_ACCOUNTS`.
- Chrome: **12/12 nhóm kiểm tra màn xem đạt**, không có runtime exception. Kiểm tra nút Xem/Chỉnh sửa riêng, thông tin mới nhất từ API, ba tab và điều hướng bàn phím, ngày/điện thoại/lịch sử trống, cả hai cờ trạng thái, quay lại danh sách, chuyển sang chỉnh sửa, lỗi quyền/phiên/tài khoản không tồn tại và giao diện 390px. Không có input/select/textarea/form trong màn xem; toàn bộ quá trình xem/chuyển tab không gửi request ghi dữ liệu.

Helper/kết quả mới: `G5_FE1/node_modules/.cache/erhms-qa/user-detail-browser.mjs`, `user-detail-results.json`, ảnh `user-detail-*.png`. API trình duyệt được giả lập, backend dùng H2; không sửa dữ liệu PostgreSQL và không cần migration mới.

## Đợt triển khai các màn quản trị ban đầu

Đã hoàn tất các kiểm tra sau:

- Backend: **172 test, 0 thất bại, 0 lỗi, 0 bỏ qua**. Bộ kiểm tra mới có 11 bài tích hợp về ma trận quyền, thu hồi quyền trên phiên hiện tại, cấp quyền màn quản trị cho role khác, quyền khôi phục của Admin, CSRF, phiên bản dữ liệu, lưu tài khoản nguyên tử, lọc/số liệu tài khoản, audit và cấu hình.
- Migration V8: chạy trên H2 PostgreSQL mode, giữ nguyên người dùng, hash, trạng thái và dữ liệu nghiệp vụ; thêm đúng bảng/cột; đưa nhật ký đăng nhập/sự kiện tuyển dụng vào nhật ký chung; khóa chính ghép chặn quyền trùng. Migration chỉ chạy một lần.
- SMTP: máy SMTP loopback kiểm tra tên đơn vị trong tiêu đề/chữ ký và email thông báo trong Reply-To; giữ địa chỉ From cấu hình, không dùng dữ liệu ví dụ trong ảnh hoặc gửi đến Gmail thật.
- Frontend: **13 test đạt**, production build thành công.
- Chrome: **17/17 nhóm kiểm tra đạt**, không có runtime exception. Đã kiểm tra danh sách/counters/bộ lọc, điều hướng đến trang sửa riêng, payload phiên bản/CSRF/ghi chú/trạng thái, bảo vệ tài khoản của chính mình, xung đột giữ dữ liệu đang nhập, tạo/xóa, ma trận quyền, lọc/xem chi tiết nhật ký, cấu hình/branding, nút kiểm tra SMTP, thu hồi quyền và 5 màn ở 390px.

Kiểm tra trình duyệt dùng fixture chặn toàn bộ `/api`; kiểm thử backend dùng H2 và SMTP loopback. Không chỉnh người dùng hoặc dữ liệu PostgreSQL đang sử dụng. Không chạy kiểm tra SMTP đến tài khoản thật. Banking chưa có tích hợp nên màn cấu hình báo chưa cấu hình.

Helper Chrome, JSON kết quả và ảnh kiểm tra nằm trong thư mục bị Git ignore: `G5_FE1/node_modules/.cache/erhms-qa/admin-workspace-*`, `admin-v8-*.png`. Không đưa dữ liệu fixture vào source ứng dụng hoặc migration.

Chạy lại:

```powershell
mvn -f G5_BE/pom.xml test
npm.cmd --prefix G5_FE1 test
npm.cmd --prefix G5_FE1 run build
```

Luồng sử dụng và API: [ADMIN_WORKSPACE.md](ADMIN_WORKSPACE.md), [API.md](API.md).
