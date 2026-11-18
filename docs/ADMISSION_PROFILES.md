# Gửi bản scan và tạo hồ sơ học viên

Khởi động lại backend để Flyway áp dụng các migration đến **V22__admission_academic_forwarding.sql**, rồi tải lại frontend. V20 thêm danh sách tệp theo loại giấy tờ; V21 tách quyền hai dashboard [Tư vấn & tiếp nhận hồ sơ / Hoàn tất thủ tục nhập học](ADMISSION_DASHBOARDS.md). Các bản scan đã gửi trước đây được giữ nguyên dữ liệu và chuyển vào mục **Bản scan hồ sơ cũ**, không tự suy đoán loại giấy tờ.

Luồng tuyển sinh:

1. HR ghi nhận **Cần tư vấn thêm**, **Hẹn nộp hồ sơ** hoặc **Không tiếp tục đăng ký**. Khi học viên đồng ý nộp hồ sơ, chọn **Hẹn nộp hồ sơ** (`DOCUMENT_APPOINTMENT`) và nhập ngày hẹn nộp. Xem [ADMISSION_CONSULTATION.md](ADMISSION_CONSULTATION.md).
2. Trong **Chi tiết đăng ký tư vấn**, danh sách **CCCD**, **Sơ yếu lý lịch**, **Giấy khai sinh bản sao**, **Ảnh thẻ**, **Đơn xin** nằm ngay trên trang ở phần **Bản scan hồ sơ giấy**, không có ô tick chọn mục. Bấm **Tải PDF** để mở popup riêng cho mục đó, tải một hoặc nhiều PDF rồi bấm **Xong** để về danh sách. Chỉ sau khi tải thành công, mục tự chuyển nền xanh, có biểu tượng xác nhận và dòng **Đã tải … tệp PDF**. Mở hoặc đóng popup khi chưa tải tệp vẫn giữ trạng thái **Chưa tải PDF**. Bấm **Quản lý PDF** để xem, thêm hoặc bỏ tệp; xóa hết tệp của mục sẽ đưa mục về trạng thái chưa tải. Đóng popup vẫn giữ các tệp vừa tải trong lượt chuẩn bị gửi này. Khi gửi, hệ thống tự lấy tất cả mục có tệp; cần ít nhất một PDF và không bắt buộc đủ cả năm mục. Mỗi tệp tối đa 2 MB, toàn bộ hồ sơ tối đa 50 tệp.
3. Nhập ghi chú cho quản lý và bấm **Gửi hồ sơ** ngay trên trang. Đủ cả 5 loại giấy tờ thì hiển thị **Đã hoàn thành hồ sơ** (`DOCUMENTS_COMPLETE`); thiếu loại nào thì hiển thị **Hồ sơ còn thiếu** (`DOCUMENTS_INCOMPLETE`) cùng danh sách mục còn thiếu. Nhiều PDF trong cùng loại chỉ được tính là một loại; bản scan cũ chưa phân loại không thay thế giấy tờ còn thiếu. Hồ sơ thiếu nằm ở dashboard **Tư vấn & tiếp nhận hồ sơ**; hồ sơ đủ tự chuyển sang **Hoàn tất thủ tục nhập học**. Chỉ tải tệp lên hoặc bấm **Xong** trong popup chưa gửi hồ sơ. Sau khi gửi, nút **Ghi nhận tư vấn** được thay bằng **Bổ sung / chỉnh sửa hồ sơ**; đường dẫn tư vấn cũ và API cũng chặn ghi thêm tư vấn.
   HR có thể mở **Bổ sung / chỉnh sửa hồ sơ** ở cả hồ sơ đủ và thiếu, giữ nguyên các tệp đang có, thêm PDF hoặc bỏ tệp cũ rồi tải tệp thay thế. Bấm **Lưu thay đổi hồ sơ** để lưu toàn bộ bộ hồ sơ và tính lại trạng thái. Có thể bỏ hết tệp khi chỉnh sửa; hồ sơ trở thành còn thiếu và vẫn được bổ sung về sau. Bấm **Hủy chỉnh sửa** để bỏ bản sửa chưa lưu. Lịch sử ghi người sửa, số tệp thêm/bỏ và ghi chú. Không xóa tài khoản hoặc hợp đồng khi sửa giấy tờ.
4. Hồ sơ đủ giấy tờ nằm chờ ở **Hoàn tất thủ tục nhập học → Chờ chuyển học vụ**. HR bấm **Chuyển quản lý học vụ** cho một hoặc nhiều hồ sơ. Sau khi xác nhận, hồ sơ sang trang **Đã chuyển học vụ**; Manager mới thấy hồ sơ trong **Cấp tài khoản học viên**, đối chiếu bản scan và chọn **Lập hợp đồng**. Cũng có thể vào **Hợp đồng đào tạo → Tạo hợp đồng**, chọn **Hồ sơ đã nhận**. Gmail / email được lấy từ hồ sơ, chưa cần tài khoản học viên.
5. Lưu hợp đồng để chuyển sang **Đã lập hợp đồng** (`CONTRACTED`). Khi chưa có tài khoản, hợp đồng giữ email đã cung cấp và chưa có `student_id`. Nút tạo tài khoản chỉ được mở sau khi có hợp đồng hợp lệ do Manager đó quản lý.
6. Chọn **Tạo tài khoản**, kiểm tra email rồi xác nhận. Hệ thống tạo tài khoản Student, tự gắn các hợp đồng của hồ sơ có cùng email vào tài khoản và gửi thông tin đăng nhập. Trạng thái chuyển thành **Đã tạo hồ sơ** (`PROFILE_CREATED`). Nếu đã có tài khoản Student phù hợp, giữ nguyên tài khoản/mật khẩu và chỉ hoàn tất liên kết. Sau đó Manager có thể ký và bàn giao hợp đồng.

HR cần quyền `AdmissionConsultation` để tải, gửi và sửa scan, đồng thời chỉ xử lý đăng ký trong phạm vi được phân công. Tệp mới phải do HR đang thao tác tải lên; tệp đã gắn đúng hồ sơ được giữ lại khi sửa. Manager cần quyền `ListStudentAccount` để tiếp nhận và tải bản scan trong hàng đợi. Email đã dùng trên hợp đồng không được thay đổi qua chức năng sửa giấy tờ. HR không đưa hồ sơ đã gửi về giai đoạn tư vấn.

`record.documentStatus` được tính trực tiếp từ giấy tờ hiện tại, nên hồ sơ đã gửi trước đây cũng được phân loại chính xác từ giấy tờ đang có; migration V21 chỉ bổ sung quyền dashboard. Mã tiến trình nội bộ `PROFILE_PENDING`, `CONTRACTED`, `PROFILE_CREATED` vẫn phục vụ lập hợp đồng và cấp tài khoản; khi còn `PROFILE_PENDING`, giao diện thay nhãn **Tạo hồ sơ** bằng tình trạng đủ/thiếu. Khi đã có hợp đồng/tài khoản, hiển thị cả tiến trình và tình trạng giấy tờ. Sửa giấy tờ không đổi tiến trình hợp đồng/tài khoản hoặc ngày gửi hồ sơ ban đầu.

Thiếu bản scan, sai quyền, tệp sai loại, phiên bản hồ sơ cũ hoặc gửi lặp đều bị chặn. Nếu gửi email tài khoản thất bại, tài khoản/hồ sơ mới được hoàn tác và liên kết hợp đồng cũng được hoàn tác; trạng thái vẫn là **Đã lập hợp đồng** để thử lại. Hồ sơ nhập trực tiếp bằng Excel giữ luồng riêng: không cần gửi scan qua HR, vẫn phải lập hợp đồng trước khi tạo tài khoản và chuyển sang `PROFILE_CREATED`.

API gửi bản scan đã tải lên:

```http
POST /api/workspace/screens/ListAdmissionApplicationDetail/{id}/actions/submit-profile
Content-Type: application/json

{
  "version": 0,
  "documents": [
    {"type": "IDENTITY_CARD", "files": ["<UUID-mat-truoc>", "<UUID-mat-sau>"]},
    {"type": "BIRTH_CERTIFICATE", "files": ["<UUID-khai-sinh>"]}
  ],
  "note": "Đã đối chiếu bản gốc"
}
```

Các mã giấy tờ mới: `IDENTITY_CARD`, `CURRICULUM_VITAE`, `BIRTH_CERTIFICATE`, `ID_PHOTO`, `APPLICATION_FORM`. `LEGACY_SCAN` chỉ dùng cho dữ liệu cũ, không nhận khi gửi mới. `documents` tự chứa các mục có tệp đã tải thành công, mỗi nhóm có một hoặc nhiều ID tệp; backend kiểm tra loại hợp lệ, tệp không lặp, PDF và quyền sở hữu trước khi lưu toàn bộ trong một transaction.

Sửa bộ hồ sơ dùng `POST /api/workspace/screens/ListAdmissionApplicationDetail/{id}/actions/update-profile` với cùng cấu trúc `{version, documents, note}`. `documents` bắt buộc là toàn bộ bộ hồ sơ muốn giữ sau khi lưu (được phép `[]` khi sửa). `LEGACY_SCAN` chỉ được giữ lại từ đúng hồ sơ cũ, không nhận tệp mới cho loại này. API khóa hồ sơ, kiểm tra phiên bản trước khi thay danh sách trong một transaction; lỗi bất kỳ tệp nào không làm mất bộ hồ sơ hiện tại. Tệp đã bỏ không còn xuất hiện trong hồ sơ/hợp đồng, dữ liệu tệp gốc vẫn được giữ.

Tải riêng từng tệp qua `POST /api/workspace/files` với multipart fields `file` và `screen=AdmissionConsultation`, lấy `id` trong phản hồi để gửi trong `documents[].files`. Giấy tờ gửi mới chỉ nhận PDF, kể cả mục Ảnh thẻ; các bản scan ảnh cũ vẫn tải được. Các thao tác POST yêu cầu CSRF. Hai trạng thái mới cũng có trong bộ lọc danh sách tuyển sinh.
