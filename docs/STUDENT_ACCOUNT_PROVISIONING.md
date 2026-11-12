# Cấp tài khoản học viên từ tuyển sinh hoặc Excel

Khởi động lại backend để áp dụng **V14__manager_student_accounts.sql** và **V15__student_account_excel_import.sql**, rồi tải lại frontend. V14 cấp quyền `ListStudentAccount` riêng cho `MANAGER`; V15 bổ sung nguồn hồ sơ và người nhập Excel, mặc định hồ sơ cũ thuộc nguồn tuyển sinh. Các migration không tạo tài khoản mẫu hoặc đổi thông tin học viên đang có.

## Sử dụng

1. HR kiểm tra email học viên và chuyển hồ sơ tư vấn sang **Đã liên hệ** (`CONTACTED`).
2. Quản lý học viên mở **Cấp tài khoản học viên** (`/screens/ListStudentAccount`). Danh sách hiển thị họ tên, email, điện thoại, khóa quan tâm, ngày đăng ký và trạng thái tài khoản; hỗ trợ tìm kiếm và phân trang.
3. Bấm **Tạo tài khoản**, kiểm tra họ tên/email trong cửa sổ xác nhận, rồi xác nhận tạo và gửi email. Email được lấy từ hồ sơ, không nhập một địa chỉ nhận khác tại đây.
4. Hệ thống tạo tài khoản `STUDENT` với mật khẩu ngẫu nhiên, gắn hồ sơ học viên với Manager đang cấp và gửi email chứa tên đăng nhập, mật khẩu ban đầu cùng đường dẫn đăng nhập.
5. Học viên đăng nhập bằng email và mật khẩu nhận được; có thể đổi mật khẩu trong thông tin cá nhân. Manager tiếp tục lập hợp đồng từ cùng hồ sơ đã tư vấn.

Danh sách gồm hồ sơ tuyển sinh `CONTACTED` và học viên nhập trực tiếp từ Excel, có nhãn phân biệt nguồn. Các trạng thái mới tiếp nhận, hẹn tư vấn, đã lập hợp đồng và đã đóng không xuất hiện. Đây là cùng hàng đợi Manager sử dụng khi tạo hợp đồng.

## Nhập học viên trực tiếp bằng Excel

1. Tại **Cấp tài khoản học viên**, chọn **Tải mẫu Excel** để tải `mau-nhap-hoc-vien.xlsx`.
2. Điền sheet **Hoc vien** từ dòng 2, giữ nguyên tên sheet và hàng tiêu đề. Bốn cột bắt buộc: **Họ và tên**, **Số điện thoại**, **Email**, **Khóa quan tâm**. Hai cột tùy chọn: **Thời gian liên hệ**, **Nội dung tư vấn**. Sheet **Huong dan** có hướng dẫn và ví dụ.
3. Chọn **Nhập từ Excel**, chọn file đã điền và bấm **Nhập danh sách**. Nhận file `.xlsx` tối đa 2 MB, 500 học viên mỗi lần; các ô dữ liệu phải là văn bản, số điện thoại giữ số 0 đầu.
4. Hệ thống kiểm tra toàn bộ file trước khi lưu. Email sai/trống, email trùng trong file hoặc trong hồ sơ/tài khoản hiện có, hồ sơ trùng họ tên–điện thoại–khóa quan tâm, và công thức Excel đều được báo theo dòng/cột. Nếu có lỗi, không lưu dòng nào; sửa file rồi nhập lại.
5. Khi thành công, danh sách tự tải lại từ trang đầu với nhãn **Nhập từ Excel**. Bấm **Tạo tài khoản** ở từng học viên để cấp tài khoản và gửi email; sau đó có thể **Lập hợp đồng** như hồ sơ tuyển sinh.

Bản mẫu đi kèm repository: [mau-nhap-hoc-vien.xlsx](templates/mau-nhap-hoc-vien.xlsx). File này được xuất từ API tải mẫu trong kiểm thử tích hợp.

Thao tác import chỉ lưu thông tin học viên, không tạo tài khoản hay gửi email. Học viên không cần qua tuyển sinh và không xuất hiện ở danh sách/dashboard tuyển sinh của HR. Backend dùng cùng dữ liệu hồ sơ và trạng thái sẵn sàng cấp tài khoản (`CONTACTED`), đồng thời ghi `intake_source=EXCEL` và `imported_by` để phân biệt nguồn và truy vết người nhập.

Hồ sơ thiếu email hợp lệ chưa được cấp tài khoản. Nếu email đã thuộc bất kỳ tài khoản nào, hệ thống không tạo trùng, đổi vai trò hay đặt lại mật khẩu. Tài khoản học viên đã khóa/vô hiệu hóa cần Admin xử lý. Nếu HR đổi email hoặc trạng thái sau khi Manager mở danh sách, thao tác bị chặn và yêu cầu tải lại hồ sơ.

## Gửi email

Dùng cấu hình SMTP hiện có: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_SSL`. Hướng dẫn trong [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md). Đặt `STUDENT_LOGIN_URL` thành địa chỉ đăng nhập mà học viên truy cập được; mặc định dành cho máy lập trình là `http://localhost:8082/login`.

Ví dụ với backend chạy bằng Maven/IDE:

```powershell
$env:STUDENT_LOGIN_URL = "https://<ten-mien-trang-quan-ly>/login"
```

Khởi động lại backend sau khi đổi biến môi trường. Docker Compose nhận biến này từ `.env`.

Mật khẩu chỉ được lưu dưới dạng BCrypt và gửi trong nội dung email đến học viên; API, giao diện Manager và nhật ký không trả về hoặc lưu mật khẩu rõ. Mail gửi đồng bộ trong thao tác cấp tài khoản. Thiếu cấu hình hoặc lỗi SMTP làm thao tác thất bại và hoàn tác tài khoản/hồ sơ vừa tạo, để Manager thử lại. Thành công nghĩa là máy chủ SMTP đã tiếp nhận thư; người nhận có thể cần kiểm tra thư rác.

## API và kiểm tra

- `GET /api/workspace/student-accounts?q=&page=0&size=10`: danh sách học viên, nguồn `ADMISSION`/`EXCEL`, trạng thái tài khoản và lý do chưa thể tạo.
- `GET /api/workspace/student-accounts/import-template`: tải file Excel mẫu, gồm sheet dữ liệu trống và hướng dẫn.
- `POST /api/workspace/student-accounts/import` với multipart field `file`: import thông tin, trả `importedCount` và `notice`; lỗi dữ liệu trả `EXCEL_VALIDATION` và `fields` dạng `row.3.email`.
- `POST /api/workspace/student-accounts/{admissionId}` với `{ "expectedEmail": "email-trong-ho-so", "version": 0 }`: kiểm tra lại hồ sơ, tạo tài khoản và gửi mail. Phản hồi chỉ gồm `accountId`, `email`, `notice`.
- Các API đều yêu cầu role `MANAGER` và quyền `ListStudentAccount`; POST yêu cầu CSRF. Role khác bị chặn ngay cả khi có grant sai trong database. Manager không được cấp quyền quản trị tài khoản chung.

Kiểm thử tự động dùng H2 và mailer giả lập: phân quyền, trạng thái hồ sơ, tìm kiếm/phân trang, email trùng, hồ sơ vừa đổi, đăng nhập bằng mật khẩu được gửi, dữ liệu BCrypt và hoàn tác khi gửi thư thất bại. Kiểm thử SMTP xác nhận nội dung/địa chỉ nhận qua mail sender giả lập; không gửi email thật.

`StudentAccountExcelIntegrationTest` kiểm tra file mẫu thực tế, import và lỗi toàn bộ file, trùng lặp, quyền/CSRF, phân biệt nguồn tuyển sinh, giới hạn file và luồng import → cấp tài khoản → lập hợp đồng.
