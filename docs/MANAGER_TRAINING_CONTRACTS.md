# Hợp đồng đào tạo do Manager quản lý

Áp dụng cập nhật ngày 10/11/2026. Khởi động lại backend để Flyway chạy **V12__manager_training_contracts.sql**, chạy lại frontend và tải lại trang để nhận quyền mới.

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
2. HR mở **Đăng ký tư vấn & tuyển sinh → Ghi nhận tư vấn**, xác minh email và lưu trạng thái **Đã liên hệ** (`CONTACTED`).
3. Admin tạo tài khoản Student với đúng email đã xác minh, nếu học viên chưa có tài khoản.
4. Manager vào **Hợp đồng đào tạo → Tạo hợp đồng**, chọn **Hồ sơ đã tư vấn**. Chỉ hồ sơ ở trạng thái **Đã liên hệ** xuất hiện khi tạo mới; hồ sơ mới tiếp nhận, hẹn tư vấn, đã đóng hoặc đã lập hợp đồng không xuất hiện.
5. Chọn tài khoản học viên khớp email và khóa học đang mở/hoạt động thuộc Manager, nhập ngày bắt đầu, học phí, cọc, hạn thanh toán và điều khoản.
6. Lưu hợp đồng. Hệ thống chuyển hồ sơ đăng ký sang **Đã lập hợp đồng** (`CONTRACTED`). HR không tự chọn trạng thái này khi chưa có hợp đồng.
7. Manager có thể sửa hợp đồng nháp. Hồ sơ hiện tại vẫn có trong ô chọn dù đã chuyển sang `CONTRACTED`; khi đổi hồ sơ thì chỉ được chọn hồ sơ `CONTACTED`. Nếu chuyển sang hồ sơ khác và hồ sơ cũ không còn hợp đồng hiệu lực, hệ thống đưa hồ sơ cũ về `CONTACTED`.
8. Manager ghi ngày ký, minh chứng và chọn kế toán tiếp nhận. Hợp đồng chuyển sang đã ký và bàn giao. Kế toán tiếp tục đối soát; xếp lớp, lịch học và các bước sau dùng luồng hiện có.

Backend kiểm tra lại trạng thái hồ sơ ngay lúc lưu dưới khóa database. Nếu HR vừa đổi trạng thái sau khi Manager mở biểu mẫu, hệ thống chặn lưu và báo lỗi tại **Hồ sơ đã tư vấn**. Hợp đồng đã ký không được sửa qua màn cập nhật bản nháp.

## Dữ liệu cũ và danh mục mã màn

Giữ hợp đồng, mã hợp đồng, nội dung, trạng thái và người tạo cũ. Các hợp đồng đã có `manager_id` được giữ nguyên. Nếu thiếu Manager nhưng đã liên kết khóa học có người quản lý, migration bổ sung đúng người quản lý khóa đó; không tự gán ngẫu nhiên hồ sơ chưa có trách nhiệm.

Các file FE riêng của năm màn giữ nguyên. Điều chỉnh vai trò nằm trong `docs/screen-access-overrides.json` và danh mục runtime FE/BE; `m_function` và route cũ tiếp tục dùng được. Script nhập thiết kế áp dụng lớp điều chỉnh cho catalog runtime, còn migration V10 lịch sử giữ quyền gốc; thay đổi quyền mới nằm ở V12. Script nhập đầy đủ cần bộ SVG/manifest gốc.

## Kiểm tra

Backend kiểm tra chuyển quyền, phạm vi hợp đồng nháp, kiểm tra trạng thái hồ sơ, lựa chọn học viên/khóa học, chỉnh sửa, ký/bàn giao, tải minh chứng, quyền cũ bị thu hồi và migration giữ hợp đồng cũ. Frontend kiểm tra menu/route theo vai trò. Chrome với API mô phỏng kiểm tra ghim tài khoản khi cuộn menu và trang trên desktop/mobile; HR không còn thao tác hợp đồng; Manager tạo hợp đồng và giữ thông báo thành công sau điều hướng. Các kiểm tra dùng H2 và dữ liệu trình duyệt mô phỏng, không sửa PostgreSQL đang sử dụng.

Kết quả: **86 kiểm thử backend liên quan**, **22 kiểm thử frontend**, **6 nhóm kiểm tra Chrome** đều qua; production build frontend thành công. Chưa áp dụng V12 lên database thật.
