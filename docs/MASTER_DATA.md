# Danh mục dùng chung BK-VTCMS

Frontend ở `G5_FE1`, backend ở `G5_BE`. Logo, tiêu đề trình duyệt, trang đăng nhập, workspace và trang công khai hiển thị BK-VTCMS. Tên package Java, database và các mã màn cũ được giữ để tương thích.

Khởi động lại backend để Flyway áp dụng **V11__master_data_catalogues.sql** và **V13__admin_training_catalogues.sql**, sau đó tải lại frontend. V11 bổ sung bảng và liên kết dữ liệu cũ; V13 chuyển quyền ngành nghề/khóa học sang Admin và thu hồi quyền của các role khác. Không cần chạy SQL thủ công.

| Vai trò quản lý | Menu | Bảng master | Mã màn danh sách / tạo / sửa |
| --- | --- | --- | --- |
| IT Support | Nhóm thiết bị | `m_equipment_group` | `ListEquipmentGroup` / `AddEquipmentGroup` / `EditEquipmentGroup` |
| IT Support | Vị trí kho | `m_storage_location` | `ListStorageLocation` / `AddStorageLocation` / `EditStorageLocation` |
| Admin | Ngành nghề | `m_profession` | `ListProfession` / `AddProfession` / `EditProfession` |
| Admin | Phòng học | `m_classroom` | `ListClassroom` / `AddClassroom` / `EditClassroom` |

Mỗi mã có một file JSX riêng trong `G5_FE1/src/screens/pages` và route `/screens/{mã_màn}`. Route sửa thêm `/{id}`. Mã màn được đăng ký trong `m_function` và cấp qua `role_function`; Admin có thể thu hồi/cấp lại tại **Vai trò & phân quyền** trong phạm vi vai trò thiết kế. Quyền được kiểm tra ở cả frontend và backend. Xóa danh mục dùng quyền màn danh sách.

**Ngành nghề** và **Danh mục khóa học** chỉ xuất hiện trong menu Admin. Các màn `ListCourse`, `AddCourse`, `EditCourse`, `CourseDetail` và ba màn ngành nghề chỉ chấp nhận role `ADMIN`; role khác bị chặn cả khi nhập URL trực tiếp hoặc còn quyền cũ trong database. Khi tạo/sửa khóa học, Admin chọn **Manager phụ trách** để Manager đó tiếp tục lập hợp đồng và điều phối lớp trong phạm vi được giao. Dữ liệu khóa học công khai và ô chọn khóa học trong hợp đồng vẫn hoạt động theo phạm vi hiện có.

Manager được chọn phải là tài khoản đang hoạt động và không bị khóa. Chỉ đổi Manager phụ trách khi khóa học chưa có hợp đồng hoặc lớp; dữ liệu đã triển khai giữ người phụ trách hiện tại để tránh tự chuyển quyền hồ sơ.

Các màn hỗ trợ tìm theo mã/tên, lọc trạng thái, phân trang, tạo, xem thông tin, sửa, xóa có xác nhận và chuyển trạng thái đang/ngừng sử dụng. Thông tin gồm mã, tên, mô tả và trạng thái. Nhãn mã theo từng màn: **Mã nhóm thiết bị**, **Mã vị trí**, **Mã ngành nghề**, **Mã phòng học**. Mã/tên không được trùng trong cùng danh mục, không phân biệt hoa/thường. Cập nhật và xóa kiểm tra phiên bản để tránh ghi đè thay đổi của người khác. Thao tác ghi được đưa vào nhật ký hệ thống.

Biểu mẫu thêm/sửa thiết bị dùng nhãn **Mã thiết bị** và không hiển thị trường serial.

## Các ô chọn nghiệp vụ

| Màn | Trường chọn | Tham chiếu database |
| --- | --- | --- |
| IT tạo/sửa thiết bị | Nhóm thiết bị, vị trí lưu kho | `equipment_assets.equipment_group_id`, `storage_location_id` |
| Giảng viên yêu cầu thiết bị | Nhóm thiết bị | `equipment_requests.equipment_group_id` |
| Admin tạo/sửa khóa học | Ngành nghề, Manager phụ trách | `training_courses.profession_id`, `manager_id` |
| Manager tạo/sửa buổi học | Phòng học | `training_sessions.classroom_id` |
| Kế toán tiếp nhận giao dịch | Ngân hàng | `financial_transactions.bank_id` |

API chỉ nhận ID hợp lệ của danh mục đang sử dụng. Không thể gửi một tên tùy ý để tự tạo danh mục qua biểu mẫu nghiệp vụ. Đổi tên danh mục vẫn giữ ID liên kết; màn nghiệp vụ hiển thị tên hiện tại. Các cột tên cũ được giữ lại làm thông tin lịch sử. Kiểm tra trùng phòng sử dụng ID phòng để việc đổi tên không làm mất phát hiện xung đột.

`m_bank` được khởi tạo 10 tên: Vietcombank, BIDV, VietinBank, Agribank, Techcombank, MBBank, ACB, VPBank, Sacombank và HDBank. Chưa có màn CRUD ngân hàng vì yêu cầu hiện tại chỉ cần dữ liệu khởi tạo và ô chọn.

Migration gom các tên đang dùng trong dữ liệu cũ thành danh mục, không phân biệt hoa/thường và bỏ khoảng trắng đầu/cuối khi đối chiếu, rồi gắn ID tương ứng. Tên ngân hàng cũ ngoài 10 ngân hàng trên được giữ thêm để bảo toàn dữ liệu. Trường cũ chưa có thông tin vẫn để trống, cần chọn danh mục khi sửa hồ sơ. Không tự tạo thiết bị, ngành nghề hay phòng học mẫu.

Danh mục có hồ sơ tham chiếu không được xóa. Có thể **ngừng sử dụng** để bỏ khỏi ô chọn mà vẫn giữ các hồ sơ cũ; chọn danh mục đang sử dụng khi lưu biểu mẫu nghiệp vụ. Có thể sửa trạng thái về đang sử dụng để bật lại.

## Ví dụ thao tác

1. IT Support vào **Nhóm thiết bị → Tạo mới**, nhập mã `DIEN`, tên `Thiết bị điện`, lưu. Tạo `KHO-A` tại **Vị trí kho**.
2. IT vào **Danh mục thiết bị → Thêm thiết bị**, chọn nhóm/vị trí vừa tạo. Giảng viên vào **Yêu cầu cấp thiết bị** và chọn cùng danh mục nhóm.
3. Admin tạo `DIEN-CN` tại **Ngành nghề**, rồi vào **Danh mục khóa học** để thêm khóa, chọn ngành và Manager phụ trách.
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
