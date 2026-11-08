# Kiểm tra danh mục dùng chung — 08/11/2026

## Backend

Chạy bằng Maven offline với profile kiểm thử H2/PostgreSQL, không ghi vào PostgreSQL đang sử dụng:

```powershell
mvn -o -q -f G5_BE/pom.xml '-Dtest=MasterDataIntegrationTest,ScreenWorkflowIntegrationTest,FreshDatabaseMigrationTest,AdminWorkspaceIntegrationTest,DashboardIntegrationTest' test
```

**80 kiểm thử qua, không lỗi**, gồm:

- CRUD bốn danh mục, kiểm tra vai trò cho toàn bộ 12 màn, thu hồi quyền và CSRF.
- Chặn trùng mã/tên không phân biệt hoa thường, chống ghi đè bằng phiên bản, tìm kiếm và xóa danh mục chưa sử dụng.
- Lưu ID master vào thiết bị và khóa học; chặn ID không tồn tại, danh mục ngừng sử dụng và nhập tên tùy ý.
- Chặn xóa nhóm/vị trí/ngành nghề có dữ liệu tham chiếu, giữ tên hiện tại khi danh mục được đổi tên.
- Ngân hàng có 10 mục trên database mới; các biểu mẫu nhận đúng danh sách lựa chọn.
- Migration V10 → V11 giữ dữ liệu cũ, gộp tên khác hoa/thường/khoảng trắng, liên kết ID nhóm, vị trí, ngành nghề, phòng và ngân hàng; chạy lại không nhân đôi.
- Các luồng hợp đồng, thanh toán, yêu cầu thiết bị, bàn giao, lịch học và dashboard vẫn qua. Đổi tên phòng vẫn phát hiện xung đột theo ID.
- Mã màn được đưa vào ma trận phân quyền; thao tác tạo/sửa/xóa được ghi nhật ký.

Lần chạy toàn bộ có **199 kiểm thử**, trong đó 197 qua và 2 test SMTP loopback báo `SocketException: An established connection was aborted by the software in your host machine`. Chạy lại riêng nhóm SMTP vẫn gặp ngắt kết nối (3/6 test lỗi); các phần sửa trong lượt này không thay đổi SMTP. Không kết nối Gmail hoặc gửi email thực tế trong quá trình kiểm tra.

## Frontend

- `npm.cmd --prefix G5_FE1 test`: **21/21 qua**.
- `npm.cmd --prefix G5_FE1 run build`: qua.
- Chrome headless với API mô phỏng: kiểm tra tạo/sửa/xóa và thông báo sau điều hướng; lỗi xóa do đang sử dụng; cả 12 màn riêng; các ô select nghiệp vụ và 10 ngân hàng; tên BK-VTCMS; màn hình desktop và chiều rộng 390px. Dữ liệu mô phỏng chỉ dùng trong trình duyệt kiểm thử.
- Không có lỗi JavaScript trong những ca trình duyệt đã kiểm tra. Ô tìm kiếm và bộ lọc danh mục cùng chiều cao, cùng hàng.

Mã kiểm tra và ảnh trong thư mục bỏ qua bởi Git: `G5_FE1/node_modules/.cache/erhms-qa/master-data-*`. Log Maven trong `G5_BE/target/master-data-*.log`.

Chưa áp dụng migration lên database thật. Cần khởi động lại backend để Flyway chạy V11; chạy lại frontend để nhận giao diện mới.
