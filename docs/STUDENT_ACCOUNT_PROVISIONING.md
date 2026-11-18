# Cấp tài khoản học viên từ tuyển sinh hoặc Excel

Khởi động lại backend để áp dụng **V14__manager_student_accounts.sql**, **V15__student_account_excel_import.sql**, **V17__admission_scanned_profiles.java** và **V18__contracts_before_student_accounts.sql**, **V19__admission_consultation_outcomes.sql** và **V20__categorized_admission_documents.sql**, rồi tải lại frontend. V17 thêm bước gửi scan; V18 đổi thứ tự thành lập hợp đồng trước khi tạo tài khoản. Xem [ADMISSION_PROFILES.md](ADMISSION_PROFILES.md). V14 cấp quyền `ListStudentAccount` riêng cho `MANAGER`; V15 bổ sung nguồn hồ sơ và người nhập Excel, mặc định hồ sơ cũ thuộc nguồn tuyển sinh. Các migration không tạo tài khoản mẫu hoặc đổi thông tin học viên đang có.

## Sử dụng

Luồng chuyển hồ sơ sang học vụ cần migration **V22__admission_academic_forwarding.sql**. Nộp đủ PDF chỉ đưa hồ sơ vào trang chờ; HR phải xác nhận chuyển thì Manager mới nhận và xử lý hồ sơ.

1. HR kiểm tra email, ghi nhận kết quả tư vấn. Khi học viên đồng ý nộp hồ sơ, chọn **Hẹn nộp hồ sơ** (`DOCUMENT_APPOINTMENT`) và nhập ngày hẹn nộp.
2. HR mở chi tiết đăng ký, bấm **Tải PDF** ở từng loại giấy tờ (tối đa 2 MB/tệp), rồi **Gửi hồ sơ**. Đủ cả 5 loại thì hiển thị **Đã hoàn thành hồ sơ**, chưa đủ thì hiển thị **Hồ sơ còn thiếu**. Sau khi gửi không ghi nhận thêm tư vấn; HR dùng **Bổ sung / chỉnh sửa hồ sơ** để thêm, bỏ hoặc thay PDF ở cả hai trường hợp. Lưu sửa sẽ tính lại tình trạng giấy tờ mà không đổi hợp đồng/tài khoản. Xem [ADMISSION_PROFILES.md](ADMISSION_PROFILES.md).
3. HR chuyển hồ sơ từ **Hoàn tất thủ tục nhập học → Chờ chuyển học vụ** bằng nút **Chuyển quản lý học vụ**. Sau khi xác nhận, quản lý học viên mới nhận được hồ sơ trong **Cấp tài khoản học viên** (`/screens/ListStudentAccount`), và tuyển sinh theo dõi tại **Đã chuyển học vụ**. Danh sách học vụ hiển thị họ tên, email, điện thoại, khóa quan tâm, ngày đăng ký và trạng thái tài khoản; hỗ trợ tìm kiếm và phân trang. Hồ sơ Manager nhập trực tiếp bằng Excel giữ luồng riêng, không cần HR chuyển.
4. Chọn **Lập hợp đồng**, chọn hồ sơ đã nhận và khóa học phụ trách; Gmail / email được lấy từ hồ sơ và chưa cần có tài khoản. Lưu hợp đồng để chuyển sang **Đã lập hợp đồng** (`CONTRACTED`).
5. Trở lại **Cấp tài khoản học viên**, chọn **Tạo tài khoản**, kiểm tra email đã dùng trên hợp đồng rồi xác nhận. Hệ thống tạo tài khoản `STUDENT`, gắn hợp đồng theo email, gửi thông tin đăng nhập và chuyển sang **Đã tạo hồ sơ** (`PROFILE_CREATED`). Nếu đã có tài khoản phù hợp, giữ nguyên mật khẩu và hoàn tất liên kết.
6. Học viên đăng nhập bằng email và mật khẩu nhận được. Manager tiếp tục ký và bàn giao hợp đồng sau khi đã liên kết tài khoản.

Danh sách gồm hồ sơ tuyển sinh đã gửi scan, hồ sơ đã lập hợp đồng/chờ tài khoản và hồ sơ đã hoàn tất, cùng học viên nhập trực tiếp từ Excel. Nút tạo tài khoản bị chặn khi chưa có hợp đồng hợp lệ thuộc Manager; hợp đồng và hồ sơ phải có cùng email.

## Nhập học viên trực tiếp bằng Excel

1. Tại **Cấp tài khoản học viên**, chọn **Tải mẫu Excel** để tải `mau-nhap-hoc-vien.xlsx`.
2. Điền sheet **Hoc vien** từ dòng 2, giữ nguyên tên sheet và hàng tiêu đề. Bốn cột bắt buộc: **Họ và tên**, **Số điện thoại**, **Email**, **Khóa quan tâm**. Hai cột tùy chọn: **Thời gian liên hệ**, **Nội dung tư vấn**. Sheet **Huong dan** có hướng dẫn và ví dụ.
3. Chọn **Nhập từ Excel**, chọn file đã điền và bấm **Nhập danh sách**. Nhận file `.xlsx` tối đa 2 MB, 500 học viên mỗi lần; các ô dữ liệu phải là văn bản, số điện thoại giữ số 0 đầu.
4. Hệ thống kiểm tra toàn bộ file trước khi lưu. Email sai/trống, email trùng trong file hoặc trong hồ sơ/tài khoản hiện có, hồ sơ trùng họ tên–điện thoại–khóa quan tâm, và công thức Excel đều được báo theo dòng/cột. Nếu có lỗi, không lưu dòng nào; sửa file rồi nhập lại.
5. Khi thành công, danh sách tự tải lại từ trang đầu với nhãn **Nhập từ Excel**. Chọn **Lập hợp đồng** trước, sau đó **Tạo tài khoản**. Hợp đồng sẽ tự gắn với tài khoản theo email học viên.

Bản mẫu đi kèm repository: [mau-nhap-hoc-vien.xlsx](templates/mau-nhap-hoc-vien.xlsx). File này được xuất từ API tải mẫu trong kiểm thử tích hợp.

Thao tác import chỉ lưu thông tin học viên, không tạo tài khoản hay gửi email. Học viên không cần qua tuyển sinh và không xuất hiện ở danh sách/dashboard tuyển sinh của HR. Backend dùng cùng dữ liệu hồ sơ và trạng thái sẵn sàng lập hợp đồng (`CONTACTED`), đồng thời ghi `intake_source=EXCEL` và `imported_by` để phân biệt nguồn và truy vết người nhập.

Hồ sơ thiếu email hợp lệ chưa được cấp tài khoản. Nếu email đã thuộc tài khoản Student đang hoạt động, Manager có thể hoàn tất tạo hồ sơ bằng tài khoản đó khi chưa thuộc quản lý khác; không gửi lại hoặc đổi mật khẩu. Email thuộc vai trò khác bị chặn. Tài khoản học viên đã khóa/vô hiệu hóa cần Admin xử lý. Nếu HR đổi email hoặc trạng thái sau khi Manager mở danh sách, thao tác bị chặn và yêu cầu tải lại hồ sơ.

## Gửi email

Dùng cấu hình SMTP hiện có: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_SSL`. Hướng dẫn trong [ACCOUNT_MANAGEMENT.md](ACCOUNT_MANAGEMENT.md). Đặt `STUDENT_LOGIN_URL` thành địa chỉ đăng nhập mà học viên truy cập được; mặc định dành cho máy lập trình là `http://localhost:8082/login`.

Ví dụ với backend chạy bằng Maven/IDE:

```powershell
$env:STUDENT_LOGIN_URL = "https://<ten-mien-trang-quan-ly>/login"
```

Khởi động lại backend sau khi đổi biến môi trường. Docker Compose nhận biến này từ `.env`.

Mật khẩu chỉ được lưu dưới dạng BCrypt và gửi trong nội dung email đến học viên; API, giao diện Manager và nhật ký không trả về hoặc lưu mật khẩu rõ. Mail gửi đồng bộ trong thao tác cấp tài khoản. Thiếu cấu hình hoặc lỗi SMTP làm thao tác thất bại và hoàn tác tài khoản/hồ sơ và liên kết hợp đồng vừa tạo, để Manager thử lại. Thành công nghĩa là máy chủ SMTP đã tiếp nhận thư; người nhận có thể cần kiểm tra thư rác.

## API và kiểm tra

- `GET /api/workspace/student-accounts?q=&page=0&size=10`: danh sách học viên, nguồn `ADMISSION`/`EXCEL`, trạng thái tài khoản và lý do chưa thể tạo.
- `GET /api/workspace/student-accounts/import-template`: tải file Excel mẫu, gồm sheet dữ liệu trống và hướng dẫn.
- `POST /api/workspace/student-accounts/import` với multipart field `file`: import thông tin, trả `importedCount` và `notice`; lỗi dữ liệu trả `EXCEL_VALIDATION` và `fields` dạng `row.3.email`.
- `POST /api/workspace/student-accounts/{admissionId}` với `{ "expectedEmail": "email-trong-ho-so", "version": 0 }`: kiểm tra lại hồ sơ, kiểm tra hợp đồng đã lập, tạo tài khoản, gắn hợp đồng theo email và gửi mail. Phản hồi chỉ gồm `accountId`, `email`, `notice`.
- Các API đều yêu cầu role `MANAGER` và quyền `ListStudentAccount`; POST yêu cầu CSRF. Role khác bị chặn ngay cả khi có grant sai trong database. Manager không được cấp quyền quản trị tài khoản chung.

Kiểm thử tự động dùng H2 và mailer giả lập: phân quyền, trạng thái hồ sơ, tìm kiếm/phân trang, email trùng, hồ sơ vừa đổi, đăng nhập bằng mật khẩu được gửi, dữ liệu BCrypt và hoàn tác khi gửi thư thất bại. Kiểm thử SMTP xác nhận nội dung/địa chỉ nhận qua mail sender giả lập; không gửi email thật.

`StudentAccountExcelIntegrationTest` kiểm tra file mẫu thực tế, import và lỗi toàn bộ file, trùng lặp, quyền/CSRF, phân biệt nguồn tuyển sinh, giới hạn file và luồng import → lập hợp đồng → cấp tài khoản và tự liên kết.
