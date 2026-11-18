# Rà soát bảng database — 14/11/2026

## Kết luận

**Không có bảng nào đủ cơ sở xác định là không dùng để xóa.** Database PostgreSQL `erhms`, schema `public`, có đúng **40 bảng ứng dụng + `flyway_schema_history`**, khớp các migration đến V16. Không có bảng cũ nằm ngoài danh mục migration.

Cả 40 bảng ứng dụng có đường truy cập thực tế trong code: JPA, JDBC, SQL dùng chung hoặc dữ liệu cấu hình được migration khởi tạo. `flyway_schema_history` do Flyway sử dụng để quản lý nâng cấp database. Không tạo migration xóa bảng và không thay đổi dữ liệu PostgreSQL trong lần rà soát này.

## Kiểm tra database đang chạy

- PostgreSQL 17.11 tại `localhost:5432/erhms`; snapshot đọc trong transaction `REPEATABLE READ`, `transaction_read_only=on`, kết thúc bằng rollback.
- Đọc được toàn bộ 41 bảng; tổng cộng 604 dòng tại thời điểm kiểm tra, gồm 16 dòng lịch sử migration.
- Đối chiếu đủ 68 khóa ngoại bằng truy vấn tìm bản ghi không có bản ghi cha: **0 bản ghi mồ côi**.
- **0 constraint chưa được xác thực**, **0 index không hợp lệ**; không có view hoặc trigger người dùng ngoài schema hệ thống.
- Tất cả bản ghi lịch sử migration thành công, phiên bản hiện tại V16. Số dòng là snapshot, có thể thay đổi khi ứng dụng tiếp tục hoạt động.

## Danh mục và bằng chứng sử dụng

Các đường dẫn code bên dưới tính từ `G5_BE/src/main/java/com/erhms/`. Bảng không có dòng vẫn được giữ nếu chức năng đang sử dụng.

| Bảng | Số dòng | Chức năng / code sử dụng |
| --- | ---: | --- |
| `asset_maintenance` | 0 | Phiếu bảo trì, hoàn tất bảo trì: `workspace/EquipmentWorkflows.java` |
| `consultation_followups` | 2 | Người phụ trách và trạng thái tư vấn: `workspace/TrainingWorkflows.java` |
| `consultation_notes` | 4 | Lịch sử trao đổi tư vấn: `workspace/TrainingWorkflows.java`, `WorkspaceService.java` |
| `consultation_registrations` | 7 | Đăng ký tư vấn, hồ sơ tuyển sinh, nhập Excel: `consultation/service/ConsultationService.java`, `workspace/AdmissionExcelService.java` |
| `course_assessments` | 3 | Đầu điểm, trọng số, công bố điểm: `workspace/TrainingWorkflows.java` |
| `course_lecturers` | 1 | Giảng viên được phân công theo khóa: `workspace/TrainingWorkflows.java` |
| `deposit_settlements` | 0 | Quyết toán/hoàn cọc và chống hoàn trùng: `workspace/FinanceWorkflows.java` |
| `equipment_assets` | 2 | Danh mục thiết bị và trạng thái: `workspace/EquipmentWorkflows.java` |
| `equipment_incidents` | 0 | Sự cố thiết bị, phê duyệt bồi thường: `workspace/EquipmentWorkflows.java`, `FinanceWorkflows.java` |
| `equipment_loans` | 1 | Bàn giao, thu hồi thiết bị: `workspace/EquipmentWorkflows.java` |
| `equipment_requests` | 1 | Đề nghị cấp thiết bị: `workspace/EquipmentWorkflows.java` |
| `financial_transactions` | 2 | Đối soát học phí, cọc, hoàn tiền, bồi thường: `workspace/FinanceWorkflows.java` |
| `flyway_schema_history` | 16 | Metadata Flyway; không phải bảng nghiệp vụ có thể bỏ |
| `m_bank` | 10 | Ngân hàng để tiếp nhận thanh toán: `workspace/MasterDataService.java`, `FinanceWorkflows.java` |
| `m_classroom` | 0 | Phòng học để xếp lịch và kiểm tra trùng phòng: `workspace/MasterDataService.java`, `TrainingWorkflows.java` |
| `m_equipment_group` | 1 | Nhóm thiết bị: `workspace/MasterDataService.java`, `EquipmentWorkflows.java` |
| `m_function` | 101 | Danh mục chức năng và màn được cấp quyền: `admin/service/ScreenPermissionsService.java` |
| `m_profession` | 1 | Ngành nghề của khóa học: `workspace/MasterDataService.java`, `TrainingWorkflows.java` |
| `m_role` | 7 | Vai trò tài khoản: `role/entity/Role.java`, `admin/service/ScreenPermissionsService.java` |
| `m_storage_location` | 1 | Kho/vị trí thiết bị: `workspace/MasterDataService.java`, `EquipmentWorkflows.java` |
| `m_user` | 12 | Tài khoản, đăng nhập và liên kết nghiệp vụ: `user/entity/User.java`, `shared/config/SecurityConfig.java` |
| `password_reset_challenges` | 0 | Mã xác nhận, token khôi phục mật khẩu: `user/recovery/PasswordResetService.java` |
| `password_reset_rate_limits` | 0 | Giới hạn tần suất yêu cầu khôi phục: `user/recovery/PasswordResetService.java` |
| `payment_instructions` | 0 | Hướng dẫn thanh toán của kế toán cho hợp đồng: `workspace/FinanceWorkflows.java`, `WorkspaceService.java` |
| `permission_revision` | 1 | Phiên bản quyền và khóa đồng thời khi sửa lịch: `admin/service/ScreenPermissionsService.java`, `workspace/TrainingWorkflows.java` |
| `recruitment_requests` | 5 | Nhu cầu tuyển dụng: `recruitment/service/RecruitmentService.java` |
| `request_events` | 13 | Lịch sử thay đổi nhu cầu tuyển dụng: `recruitment/service/RecruitmentService.java` |
| `role_function` | 152 | Quyền từng vai trò: `admin/service/ScreenPermissionsService.java` |
| `student_profiles` | 1 | Hồ sơ học viên và quản lý phụ trách: `workspace/StudentAccountsService.java`, `TrainingWorkflows.java` |
| `system_audit_log` | 181 | Nhật ký thao tác, truy vết tài khoản: `admin/service/SystemAuditService.java` |
| `system_settings` | 1 | Tên trung tâm, cấu hình và nội dung email: `admin/service/SystemSettingsService.java` |
| `training_attendance` | 0 | Điểm danh theo buổi học: `workspace/TrainingWorkflows.java` |
| `training_classes` | 1 | Lớp học, sĩ số, giảng viên và ngày bắt đầu: `workspace/TrainingWorkflows.java` |
| `training_contracts` | 1 | Hợp đồng đào tạo, nghĩa vụ tài chính: `workspace/TrainingWorkflows.java`, `FinanceWorkflows.java` |
| `training_courses` | 1 | Khóa học, ngành nghề và quản lý: `workspace/TrainingWorkflows.java` |
| `training_enrollments` | 1 | Học viên thuộc lớp, hợp đồng và đóng hồ sơ: `workspace/TrainingWorkflows.java` |
| `training_grades` | 0 | Điểm học viên và công bố kết quả: `workspace/TrainingWorkflows.java` |
| `training_sessions` | 0 | Lịch học, phòng học và trạng thái chốt điểm danh: `workspace/TrainingWorkflows.java` |
| `user_login_events` | 70 | Lần đăng nhập gần nhất, số liệu dashboard: `dashboard/repository/DashboardRepository.java`, `admin/service/AdminUserProfileService.java` |
| `user_notifications` | 4 | Thông báo và trạng thái đã đọc: `workspace/TrainingWorkflows.java`, `WorkspaceService.java` |
| `workflow_files` | 0 | Minh chứng tải lên/tải xuống: `workspace/WorkflowFileController.java` |

11 bảng hiện trống vẫn phục vụ chức năng được triển khai. Ví dụ, xóa `m_classroom` sẽ làm hỏng phần xếp lịch; xóa `training_attendance` hoặc `training_grades` sẽ làm hỏng điểm danh/bảng điểm khi lớp bắt đầu học. Các bảng lịch sử cũng có vai trò riêng: `request_events` phục vụ lịch sử từng nhu cầu, `user_login_events` phục vụ thống kê đăng nhập, `system_audit_log` phục vụ nhật ký toàn hệ thống.

## Kiểm thử tự động

Đã chạy toàn bộ backend bằng `mvn.cmd -q test`: **267 test đạt, 0 thất bại, 0 lỗi, 0 bỏ qua**. Các integration test dùng H2 ở chế độ tương thích PostgreSQL, tách khỏi database đang sử dụng. Kiểm tra trực tiếp PostgreSQL ở trên chỉ đọc metadata, số lượng bản ghi và quan hệ khóa ngoại.

Rà soát coverage phát hiện hai chức năng đã có code nhưng còn thiếu kiểm thử ghi dữ liệu riêng: phiếu bảo trì (`asset_maintenance`) và hướng dẫn thanh toán (`payment_instructions`). Đã bổ sung **3 kiểm thử API** cho các đường tạo/cập nhật/đọc, trạng thái, phạm vi truy cập và phiên bản dữ liệu trong `ScreenWorkflowIntegrationTest`. Chạy lại lớp kiểm thử này bằng `mvn.cmd -q -Dtest=ScreenWorkflowIntegrationTest test`: **26/26 test đạt**, gồm cả 3 test mới. Tổng số test backend sau bổ sung là **270**, không còn test thất bại trong các lần chạy kiểm tra.
