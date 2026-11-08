# Danh mục dùng chung BK-VTCMS

Frontend ở `G5_FE1`, backend ở `G5_BE`. Logo, tiêu đề trình duyệt, trang đăng nhập, workspace và trang công khai hiển thị BK-VTCMS. Tên package Java, database và các mã màn cũ được giữ để tương thích.

Khởi động lại backend để Flyway áp dụng **V11__master_data_catalogues.sql**, sau đó chạy lại frontend. Migration bổ sung bảng, liên kết dữ liệu cũ và cấp quyền mặc định; không cần chạy SQL thủ công.

| Vai trò quản lý | Menu | Bảng master | Mã màn danh sách / tạo / sửa |
| --- | --- | --- | --- |
| IT Support | Nhóm thiết bị | `m_equipment_group` | `ListEquipmentGroup` / `AddEquipmentGroup` / `EditEquipmentGroup` |
| IT Support | Vị trí kho | `m_storage_location` | `ListStorageLocation` / `AddStorageLocation` / `EditStorageLocation` |
| Manager | Ngành nghề | `m_profession` | `ListProfession` / `AddProfession` / `EditProfession` |
| Admin | Phòng học | `m_classroom` | `ListClassroom` / `AddClassroom` / `EditClassroom` |

Mỗi mã có một file JSX riêng trong `G5_FE1/src/screens/pages` và route `/screens/{mã_màn}`. Route sửa thêm `/{id}`. Mã màn được đăng ký trong `m_function` và cấp qua `role_function`; Admin có thể thu hồi/cấp lại tại **Vai trò & phân quyền** trong phạm vi vai trò thiết kế. Quyền được kiểm tra ở cả frontend và backend. Xóa danh mục dùng quyền màn danh sách.

Các màn hỗ trợ tìm theo mã/tên, lọc trạng thái, phân trang, tạo, xem thông tin, sửa, xóa có xác nhận và chuyển trạng thái đang/ngừng sử dụng. Thông tin gồm mã, tên, mô tả và trạng thái. Nhãn mã theo từng màn: **Mã nhóm thiết bị**, **Mã vị trí**, **Mã ngành nghề**, **Mã phòng học**. Mã/tên không được trùng trong cùng danh mục, không phân biệt hoa/thường. Cập nhật và xóa kiểm tra phiên bản để tránh ghi đè thay đổi của người khác. Thao tác ghi được đưa vào nhật ký hệ thống.

Biểu mẫu thêm/sửa thiết bị dùng nhãn **Mã thiết bị** và không hiển thị trường serial.

## Các ô chọn nghiệp vụ

| Màn | Trường chọn | Tham chiếu database |
| --- | --- | --- |
| IT tạo/sửa thiết bị | Nhóm thiết bị, vị trí lưu kho | `equipment_assets.equipment_group_id`, `storage_location_id` |
| Giảng viên yêu cầu thiết bị | Nhóm thiết bị | `equipment_requests.equipment_group_id` |
| Manager tạo/sửa khóa học | Ngành nghề | `training_courses.profession_id` |
| Manager tạo/sửa buổi học | Phòng học | `training_sessions.classroom_id` |
| Kế toán tiếp nhận giao dịch | Ngân hàng | `financial_transactions.bank_id` |

API chỉ nhận ID hợp lệ của danh mục đang sử dụng. Không thể gửi một tên tùy ý để tự tạo danh mục qua biểu mẫu nghiệp vụ. Đổi tên danh mục vẫn giữ ID liên kết; màn nghiệp vụ hiển thị tên hiện tại. Các cột tên cũ được giữ lại làm thông tin lịch sử. Kiểm tra trùng phòng sử dụng ID phòng để việc đổi tên không làm mất phát hiện xung đột.

`m_bank` được khởi tạo 10 tên: Vietcombank, BIDV, VietinBank, Agribank, Techcombank, MBBank, ACB, VPBank, Sacombank và HDBank. Chưa có màn CRUD ngân hàng vì yêu cầu hiện tại chỉ cần dữ liệu khởi tạo và ô chọn.

Migration gom các tên đang dùng trong dữ liệu cũ thành danh mục, không phân biệt hoa/thường và bỏ khoảng trắng đầu/cuối khi đối chiếu, rồi gắn ID tương ứng. Tên ngân hàng cũ ngoài 10 ngân hàng trên được giữ thêm để bảo toàn dữ liệu. Trường cũ chưa có thông tin vẫn để trống, cần chọn danh mục khi sửa hồ sơ. Không tự tạo thiết bị, ngành nghề hay phòng học mẫu.

Danh mục có hồ sơ tham chiếu không được xóa. Có thể **ngừng sử dụng** để bỏ khỏi ô chọn mà vẫn giữ các hồ sơ cũ; chọn danh mục đang sử dụng khi lưu biểu mẫu nghiệp vụ. Có thể sửa trạng thái về đang sử dụng để bật lại.

## Ví dụ thao tác

1. IT Support vào **Nhóm thiết bị → Tạo mới**, nhập mã `DIEN`, tên `Thiết bị điện`, lưu. Tạo `KHO-A` tại **Vị trí kho**.
2. IT vào **Danh mục thiết bị → Thêm thiết bị**, chọn nhóm/vị trí vừa tạo. Giảng viên vào **Yêu cầu cấp thiết bị** và chọn cùng danh mục nhóm.
3. Manager tạo `DIEN-CN` tại **Ngành nghề**, rồi chọn ngành này khi thêm khóa học.
4. Admin tạo `TH-01` tại **Phòng học**; Manager chọn phòng khi thêm buổi học vào thời khóa biểu.
5. Kế toán vào **Giao dịch → Tiếp nhận giao dịch**, chọn một trong các ngân hàng đã khởi tạo.

## API danh mục

| API | Chức năng |
| --- | --- |
| `GET /api/master-data/screens/List{DanhMuc}?q=&status=&page=0` | Danh sách, tìm kiếm và lọc |
| `GET /api/master-data/screens/Add{DanhMuc}` | Kiểm tra quyền mở màn tạo |
| `GET /api/master-data/screens/Edit{DanhMuc}/{id}` | Thông tin cập nhật |
| `POST /api/master-data/screens/Add{DanhMuc}` | Tạo mới |
| `PUT /api/master-data/screens/Edit{DanhMuc}/{id}` | Cập nhật, kèm `version` |
| `DELETE /api/master-data/screens/List{DanhMuc}/{id}` | Xóa, body có `version` |

`{DanhMuc}` là `EquipmentGroup`, `StorageLocation`, `Profession` hoặc `Classroom`. Các API ghi yêu cầu CSRF. Dữ liệu lựa chọn được trả trong `options` của API workspace tương ứng; người dùng nghiệp vụ không cần quyền quản trị danh mục để chọn dữ liệu.

Kết quả kiểm thử: [MASTER_DATA_VERIFICATION.md](MASTER_DATA_VERIFICATION.md).
