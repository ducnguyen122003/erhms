# Nhập học sinh đăng ký tư vấn từ Excel

Tại `/screens/ListAdmissionApplication`, nhân viên tuyển sinh có quyền mở màn hình sẽ thấy hai nút **Tải mẫu Excel** và **Nhập từ Excel**.

Có thể dùng ngay [file Excel mẫu trong dự án](templates/mau-nhap-hoc-sinh.xlsx). Nút tải mẫu trên giao diện luôn tạo mẫu theo phiên bản backend đang chạy.

1. Bấm **Tải mẫu Excel** để tải `mau-nhap-hoc-sinh.xlsx`.
2. Điền dữ liệu vào sheet **Hoc sinh**, từ dòng 2. Giữ nguyên hàng tiêu đề. Sheet **Huong dan** có hướng dẫn và ví dụ; dữ liệu ví dụ không được nhập vào hệ thống.
3. Bấm **Nhập từ Excel**, chọn file đã điền và bấm **Nhập danh sách**.
4. Khi thành công, danh sách và số liệu được tải lại, bộ lọc được xóa để hiển thị các hồ sơ mới ở trạng thái **Mới tiếp nhận**.

| Cột | Quy tắc |
| --- | --- |
| Họ và tên (*) | Bắt buộc, 2–120 ký tự. |
| Số điện thoại (*) | Bắt buộc, số Việt Nam bắt đầu bằng `0`, `84` hoặc `+84`. Giữ định dạng Text để không mất số `0` đầu. |
| Email | Không bắt buộc; nếu điền phải đúng định dạng, tối đa 190 ký tự. |
| Khóa quan tâm (*) | Bắt buộc, tối đa 255 ký tự. |
| Thời gian liên hệ | Chọn Bất kỳ, Buổi sáng hoặc Buổi chiều. Để trống được hiểu là Bất kỳ. |
| Nội dung tư vấn | Không bắt buộc, tối đa 2.000 ký tự. |

Mỗi file phải là `.xlsx`, tối đa **2 MB** và **500 học sinh**. Bỏ qua các dòng trống. Không nhập công thức vào ô dữ liệu; hãy dán giá trị văn bản. Mẫu để trống phần nhập dữ liệu để tránh tạo nhầm hồ sơ ví dụ.

Toàn bộ file được kiểm tra trước khi lưu. Nếu có dòng sai hoặc hồ sơ trùng, hệ thống trả lỗi kèm số dòng Excel và không lưu dòng nào. Sửa file rồi chọn lại để nhập. Hồ sơ trùng được xác định theo họ tên, số điện thoại và khóa quan tâm; không ghi đè hồ sơ hiện có.

Chức năng tạo hồ sơ đăng ký tư vấn, không tự tạo tài khoản đăng nhập cho học sinh.

## API và triển khai

- `GET /api/workspace/admissions/import-template`: tải file mẫu Excel có hướng dẫn.
- `POST /api/workspace/admissions/import`: gửi multipart với trường `file`; yêu cầu phiên đăng nhập, quyền màn hình, vai trò tuyển sinh và CSRF như các thao tác khác.
- Thành công trả `{ importedCount, notice }`; lỗi dữ liệu trả thông báo và `fields` theo dòng/cột.
- Danh sách trả thêm `canImport` để giao diện hiển thị chức năng theo quyền.

Khởi động lại backend sau khi cập nhật để nhận API và thư viện xử lý Excel mới. Frontend đang chạy Vite sẽ cập nhật mã; bản triển khai cần build lại. Không cần migration database cho chức năng này.
