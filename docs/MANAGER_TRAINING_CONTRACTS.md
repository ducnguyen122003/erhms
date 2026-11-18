# Hợp đồng đào tạo do Manager quản lý

Luồng hợp đồng trước tài khoản áp dụng từ V18; khởi động lại backend để áp dụng `V18__contracts_before_student_accounts.sql`. Bản phân quyền hợp đồng ban đầu áp dụng ngày 10/11/2026. Khởi động lại backend để Flyway chạy **V12__manager_training_contracts.sql**, chạy lại frontend và tải lại trang để nhận quyền mới.

## Thanh điều hướng

Khối người dùng và đăng xuất nằm cố định ở đáy thanh điều hướng trên desktop và menu điện thoại. Chỉ phần menu ở giữa cuộn; phần tài khoản vẫn truy cập được khi danh sách menu dài hoặc trang nội dung đang cuộn.

## Phân quyền hợp đồng

| Mã màn | Manager | HR | Accountant |
| --- | --- | --- | --- |
| `ListTrainingContract` | Danh sách hợp đồng được giao, gồm bản nháp | Không truy cập | Xem hợp đồng đã bàn giao và được phân công |
| `AddTrainingContract` | Tạo hợp đồng | Không truy cập | Không truy cập |
| `EditTrainingContract` | Sửa bản nháp của mình | Không truy cập | Không truy cập |
| `TrainingContractDetail` | Xem trong phạm vi quản lý | Không truy cập | Xem trong phạm vi kế toán |
| `ContractSignature` | Ghi nhận ký, minh chứng và bàn giao | Không truy cập | Không truy cập |

Migration thu hồi cả năm quyền trên khỏi HR và cấp cho Manager. Backend kiểm tra vai trò, quyền màn và người phụ trách. Admin không thể cấp lại các màn hợp đồng cho HR trong ma trận quyền. HR không thể gọi API tạo/sửa/ký hoặc tải minh chứng ký, kể cả nếu còn quyền cũ trong phiên hoặc dữ liệu cấp quyền bị sửa trực tiếp.

HR tiếp tục tiếp nhận đăng ký, xác minh email, tư vấn và cập nhật lịch hẹn. Màn trạng thái thanh toán hiện có chỉ cho xem tình hình hồ sơ do HR tiếp nhận sau khi Manager bàn giao; không mở sang màn quản lý hợp đồng. Dashboard HR tập trung số liệu đăng ký/liên hệ; dashboard Manager có nút tạo hợp đồng và danh sách hợp đồng chờ ký.

## Luồng sử dụng

1. Khách gửi đăng ký tư vấn/tuyển sinh.
2. HR mở **Đăng ký tư vấn & tuyển sinh → Ghi nhận tư vấn**, xác minh email và ghi nhận một trong ba kết quả tư vấn. Khi học viên đồng ý tiếp tục, lưu **Hẹn nộp hồ sơ** (`DOCUMENT_APPOINTMENT`) kèm ngày hẹn nộp.
3. HR lưu đủ 5 loại giấy tờ, sau đó bấm **Chuyển quản lý học vụ** ở dashboard **Hoàn tất thủ tục nhập học**. Manager chỉ nhận và lập hợp đồng cho hồ sơ đã được HR chuyển. Hồ sơ nhập trực tiếp từ Excel không cần qua bước scan/chuyển của HR.
4. Manager vào **Hợp đồng đào tạo → Tạo hợp đồng**, chọn **Hồ sơ đã nhận** và khóa học đang mở/hoạt động do mình quản lý. Có thể chọn **Lập hợp đồng** ngay tại hàng đợi **Cấp tài khoản học viên**.
5. Gmail / email được lấy từ hồ sơ đã nhận. Nhập ngày bắt đầu, học phí, cọc, hạn thanh toán và điều khoản. Chưa cần có tài khoản học viên; backend lưu email, họ tên và điện thoại vào hợp đồng.
6. Lưu hợp đồng nháp; hồ sơ chuyển sang **Đã lập hợp đồng** (`CONTRACTED`). Manager mới được tạo tài khoản tại **Cấp tài khoản học viên**. Tài khoản được cấp bằng đúng email trên hợp đồng; hệ thống tự gắn hợp đồng vào tài khoản và chuyển hồ sơ sang **Đã tạo hồ sơ** (`PROFILE_CREATED`).
7. Manager có thể sửa bản nháp của mình. Khi đổi hồ sơ chưa gắn tài khoản, hồ sơ cũ không còn hợp đồng được đưa về `PROFILE_PENDING` (hoặc `CONTACTED` với nguồn Excel). Hồ sơ/tài khoản cũ đã hoàn tất được giữ nguyên. Không chuyển hợp đồng đã gắn tài khoản sang học viên khác.
8. Sau khi đã liên kết tài khoản, Manager ghi ngày ký, minh chứng và kế toán tiếp nhận để ký/bàn giao. Kế toán tiếp tục đối soát; xếp lớp và giảng dạy dùng luồng hiện có.

Backend kiểm tra lại trạng thái hồ sơ ngay lúc lưu dưới khóa database. Nếu HR vừa đổi trạng thái sau khi Manager mở biểu mẫu, hệ thống chặn lưu và báo lỗi tại **Hồ sơ đã nhận**. Hợp đồng đã ký không được sửa qua màn cập nhật bản nháp.

## Dữ liệu cũ và danh mục mã màn

Giữ hợp đồng, mã hợp đồng, nội dung, trạng thái và người tạo cũ. Các hợp đồng đã có `manager_id` được giữ nguyên. Nếu thiếu Manager nhưng đã liên kết khóa học có người quản lý, migration bổ sung đúng người quản lý khóa đó; không tự gán ngẫu nhiên hồ sơ chưa có trách nhiệm.

Các file FE riêng của năm màn giữ nguyên. Điều chỉnh vai trò nằm trong `docs/screen-access-overrides.json` và danh mục runtime FE/BE; `m_function` và route cũ tiếp tục dùng được. Script nhập thiết kế áp dụng lớp điều chỉnh cho catalog runtime, còn migration V10 lịch sử giữ quyền gốc; thay đổi quyền mới nằm ở V12. Script nhập đầy đủ cần bộ SVG/manifest gốc.

## Kiểm tra

Backend kiểm tra chuyển quyền, phạm vi hợp đồng nháp, kiểm tra trạng thái hồ sơ, lựa chọn học viên/khóa học, chỉnh sửa, ký/bàn giao, tải minh chứng, quyền cũ bị thu hồi và migration giữ hợp đồng cũ. Frontend kiểm tra menu/route theo vai trò. Chrome với API mô phỏng kiểm tra ghim tài khoản khi cuộn menu và trang trên desktop/mobile; HR không còn thao tác hợp đồng; Manager tạo hợp đồng và giữ thông báo thành công sau điều hướng. Các kiểm tra dùng H2 và dữ liệu trình duyệt mô phỏng, không sửa PostgreSQL đang sử dụng.

Kết quả: **86 kiểm thử backend liên quan**, **22 kiểm thử frontend**, **6 nhóm kiểm tra Chrome** đều qua; production build frontend thành công. Chưa áp dụng V12 lên database thật.
