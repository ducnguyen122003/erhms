# Quản lý lớp học

Vai trò **Quản lý học viên (MANAGER)** có thể tạo lớp, thêm học viên chưa được xếp lớp và lập lịch cho từng lớp.

## Cách sử dụng

1. Vào **Lớp học & giảng viên → Tạo lớp học**.
2. Chọn khóa học được giao phụ trách, nhập tên lớp, sĩ số tối đa và ngày bắt đầu học. Có thể nhập ngày kết thúc dự kiến và chọn giảng viên thuộc khóa học ngay tại đây hoặc phân công sau.
3. Lưu lớp. Hệ thống cấp mã tăng dần như `L-000001`, `L-000002` và mở chi tiết lớp.
4. Trong **Thêm học viên chưa được xếp lớp**, tìm theo tên, email hoặc điện thoại, chọn học viên rồi bấm **Thêm học viên vào lớp**. Danh sách chỉ lấy tài khoản học viên đang hoạt động, thuộc phạm vi quản lý, **đã được kế toán xác nhận đủ học phí của khóa học tương ứng** và chưa có ghi danh đang học/chờ học. Hồ sơ đã kết thúc tại chính lớp này cũng không được thêm lại để giữ lịch sử điểm và điểm danh.
5. Trong **Lịch học của lớp**, bấm **Thêm buổi học**, chọn ngày giờ và phòng. Lớp được chọn sẵn; lưu xong quay lại chi tiết lớp. Bấm **Sửa lịch** để chỉnh buổi chưa chốt điểm danh.

Lớp cần có giảng viên trước khi xếp lịch. Buổi học phải nằm trong khoảng ngày của lớp, giờ kết thúc sau giờ bắt đầu, không trùng lịch lớp, phòng hoặc giảng viên. Không thêm học viên hay xếp lịch cho lớp đã kết thúc hoặc bị hủy.

Nếu danh sách học viên hoặc sĩ số đã thay đổi do người khác cập nhật, bấm **Tải lại danh sách** rồi chọn lại. Máy chủ kiểm tra lại quyền, trạng thái tài khoản, sĩ số và việc xếp lớp trong cùng giao dịch.

## API và dữ liệu

| Thao tác | Endpoint |
| --- | --- |
| Khóa học/giảng viên để tạo lớp | `GET /api/workspace/screens/AddClass` |
| Tạo lớp | `POST /api/workspace/screens/AddClass` |
| Chi tiết, danh sách lớp và học viên chưa xếp lớp | `GET /api/workspace/screens/ClassDetail/{id}` |
| Thêm học viên | `POST /api/workspace/screens/ClassDetail/{id}/actions/add-students` |
| Tạo/sửa buổi học | `POST /api/workspace/screens/TimeTable_EditSession[/{id}]` |

Tạo lớp nhận `courseId`, `name`, `capacity`, `startsOn`, tùy chọn `endsOn` và `lecturerId`. Máy chủ tự gán quản lý, trạng thái `PLANNED`, số buổi theo khóa học và mã lớp. Thêm học viên nhận `{"version":0,"students":[{"id":123}]}`. Danh sách ứng viên nằm trong `related.unassignedStudents`; danh sách đã xếp lớp nằm trong `related.students`.

Migration **V16** cấp quyền `AddClass` cho MANAGER và tạo sequence cấp mã lớp. UUID, mã lớp và các ghi danh cũ được giữ nguyên. Sequence không quay lại số cũ sau rollback nên mã có thể có khoảng trống. Backend cần khởi động lại để Flyway áp dụng V16; frontend cần chạy bản mới.

Xác nhận đủ học phí chuyển hợp đồng sang `ACTIVE` và thông báo cho Manager chọn lớp; không tự tạo lớp hay ghi danh mới. Học phí chỉ tính giao dịch `TUITION` đã `CONFIRMED`, không tính cọc, giao dịch chờ xử lý hoặc sai lệch. Hợp đồng miễn học phí cũng cần Accountant xác nhận trước. Khi thêm học viên, máy chủ kiểm tra lại học phí và liên kết ghi danh với hợp đồng đủ điều kiện; mỗi hợp đồng chỉ có một ghi danh.

## Kiểm tra

`ClassManagementIntegrationTest` kiểm tra tạo lớp/mã tăng, phân quyền và CSRF, phạm vi khóa học/học viên, ngày học, sĩ số, xử lý cả lô khi có học viên không hợp lệ, xếp lớp đồng thời, lịch trùng, học phí chưa đủ/chưa xác nhận/sai khóa và việc chờ Manager xếp lớp sau xác nhận miễn học phí. `FreshDatabaseMigrationTest` kiểm tra nâng cấp V16 giữ nguyên lớp cũ và chỉ cấp quyền tạo lớp cho MANAGER.
