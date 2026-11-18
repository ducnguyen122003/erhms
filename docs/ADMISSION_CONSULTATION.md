# Kết quả tư vấn tuyển sinh

Khởi động lại backend để áp dụng `V19__admission_consultation_outcomes.sql`, rồi tải lại frontend.

Màn **Ghi nhận kết quả tư vấn** có ba lựa chọn:

| Kết quả | Khi sử dụng | Thông tin ghi nhận |
| --- | --- | --- |
| **Cần tư vấn thêm** (`NEEDS_COUNSELING`) | Học viên còn cân nhắc, cần giải đáp hoặc muốn trao đổi lại | Nội dung tư vấn bắt buộc; lịch hẹn liên hệ lại tùy chọn, gồm ngày/giờ. |
| **Hẹn nộp hồ sơ** (`DOCUMENT_APPOINTMENT`) | Học viên đồng ý tiếp tục và hẹn ngày nộp | Ngày hẹn nộp hồ sơ bắt buộc, chỉ cần ngày; ghi chú tùy chọn. |
| **Không tiếp tục đăng ký** (`NOT_PROCEEDING`) | Học viên xác nhận không còn nhu cầu | Lý do không tiếp tục bắt buộc. |

Mỗi lần mở **Ghi nhận tư vấn** là một lượt mới: email, hình thức liên hệ, kết quả, nội dung, lịch liên hệ lại, ngày nộp và lý do đều để trống. Email để trống nghĩa là giữ email của hồ sơ. Hồ sơ đã gửi quản lý không còn ghi nhận tư vấn; mở đường dẫn cũ chỉ được xem và API từ chối ghi thêm.

Các ô nhập tự thay đổi theo lựa chọn. Backend kiểm tra lại thông tin bắt buộc. Ngày hẹn nộp hồ sơ và lý do không tiếp tục được lưu riêng tại `document_due_on`, `discontinuation_reason` trong kết quả hiện tại và lịch sử. Mỗi lần lưu vẫn ghi người thực hiện, hình thức liên hệ và thời điểm; nội dung tư vấn cũ được giữ trong lịch sử.

Lịch liên hệ lại không được dùng thay cho ngày hẹn nộp hồ sơ. Khi đổi sang không tiếp tục, lịch hẹn và ngày nộp ở kết quả hiện tại được xóa; lịch sử trước đó được giữ. Bộ lọc danh sách, chi tiết hồ sơ và dashboard dùng các kết quả mới. Dashboard hiển thị số hồ sơ cần tư vấn thêm và số lịch hẹn nộp hồ sơ trong hôm nay.

**Không tiếp tục đăng ký** kết thúc lượt hồ sơ: ẩn thao tác ghi nhận tư vấn, mở đường dẫn form chỉ được xem và API từ chối ghi thêm hoặc đổi kết quả, kể cả từ form đã mở trước đó. Học viên quay lại cần một hồ sơ mới. Tuyển sinh có thể import lại email/họ tên/số điện thoại/khóa học của hồ sơ đã dừng; hệ thống cấp mã hồ sơ mới ở trạng thái **Mới tiếp nhận**, giữ nguyên hồ sơ và lịch sử cũ. Nếu vẫn có hồ sơ khác chưa dừng trùng thông tin, trùng trong chính file hoặc email đã thuộc tài khoản, file vẫn bị từ chối.

Sau khi ghi nhận **Hẹn nộp hồ sơ** cùng ngày hẹn, HR tải và gửi bản scan. Đủ cả 5 loại giấy tờ thì hiển thị **Đã hoàn thành hồ sơ**, còn thiếu loại nào thì hiển thị **Hồ sơ còn thiếu**. Cả hai trường hợp có chức năng **Bổ sung / chỉnh sửa hồ sơ** để thêm, bỏ hoặc thay PDF, tính lại tình trạng khi lưu. Manager tiếp tục **lập hợp đồng trước → cấp tài khoản và gắn hợp đồng theo email**, như [ADMISSION_PROFILES.md](ADMISSION_PROFILES.md).

V19 chuyển dữ liệu cũ:

- `CONTACTED` của nguồn tuyển sinh và `APPOINTMENT` chuyển thành `NEEDS_COUNSELING`, giữ nội dung và lịch liên hệ lại. Lịch tư vấn cũ không được tự coi là lịch nộp hồ sơ.
- `CLOSED` chuyển thành `NOT_PROCEEDING`, dùng ghi chú cũ làm lý do. Nếu ghi chú trống, ghi rõ dữ liệu cũ chưa có lý do.
- Lịch sử được chuyển tương ứng; các trạng thái gửi scan, hợp đồng và đã tạo hồ sơ được giữ.
- `CONTACTED` của nguồn nhập Excel trực tiếp bởi Manager vẫn giữ để sử dụng luồng lập hợp đồng hiện có.

API lưu kết quả vẫn là `POST /api/workspace/screens/AdmissionConsultation/{id}`, yêu cầu HR, quyền màn hình, CSRF, đúng người phụ trách và phiên bản dữ liệu. Dữ liệu gồm `status`, `channel`, `notes`, `appointmentAt`, `documentDueOn`, `discontinuationReason`; ngày nộp gửi dạng `YYYY-MM-DD`, lịch liên hệ gửi dạng ISO có múi giờ.
