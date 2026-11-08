# Triển khai bộ màn ERHMS

Nguồn đối chiếu: `ERHMS_Ma_Tran_Quyen_Truy_Cap.xlsx` và thư mục `ERHMS_Full_Screens_and_Screen_Flow_SVG/ERHMS_Full_UI_SVG`. Ma trận có **108 mã màn/biến thể**; 3 bản tham chiếu HTML và 8 sơ đồ flow không phải route nghiệp vụ.

Frontend mới nằm trong `G5_FE1`, backend trong `G5_BE`. Có 80 component nghiệp vụ riêng tại `src/screens/pages`, một component riêng cho đổi mật khẩu và 9 component công khai tại `src/screens/public`. Các component dùng chung bảng, biểu mẫu, lịch tuần và xử lý lỗi; dữ liệu mẫu trong SVG không được đưa vào database hoặc giao diện vận hành. Các màn tài khoản, đăng nhập/khôi phục và 7 dashboard hiện có được nối lại vào luồng mới. Danh mục đầy đủ: [SCREEN_ROUTE_COVERAGE.md](SCREEN_ROUTE_COVERAGE.md).

## Chạy

Khởi động lại backend để Flyway áp dụng **V9** (bảng/cột nghiệp vụ) và **V10** (quyền theo mã màn), rồi chạy frontend:

```powershell
mvn -f G5_BE/pom.xml spring-boot:run
npm.cmd --prefix G5_FE1 run dev
```

Các terminal chạy riêng. Workspace: http://localhost:8082. Trang công khai mới: http://localhost:8082/public/HomePage. Website `G5_FE2` hiện có vẫn được giữ lại.

Migration bổ sung schema và quyền; không tạo học viên, lớp, hợp đồng, giao dịch hay tài sản mẫu. Dữ liệu nghiệp vụ cũ được giữ nguyên. Các trường trách nhiệm mới của bản ghi cũ có thể chưa được gán: cần liên kết khóa học/hợp đồng/lớp và người phụ trách đúng thực tế; hệ thống không tự đoán người sở hữu hoặc mở hồ sơ chưa được bàn giao cho role khác.

## Quyền và phạm vi

- Giữ 7 cặp `role_id`/mã chuẩn. Admin quản trị kỹ thuật; không mặc định có quyền xác nhận học phí, nhập điểm, bàn giao thiết bị hoặc đóng hồ sơ.
- Mỗi màn mới có `function_code` và `screen_code` bằng mã trong SVG, liên kết qua `role_function`. Quyền ✓ được cấp mặc định theo workbook; ĐX chưa được cấp. Ma trận quyền hiện tại có thể bật/tắt quyền trong phạm vi vai trò được thiết kế. Các mã quyền quản trị cũ được giữ để không làm mất quyền đã cấu hình.
- Quyền mở màn và quyền ghi dữ liệu được kiểm tra riêng ở backend. HR/Manager xem khóa học; chỉ Manager được sửa. Manager xem điểm danh/bảng điểm; chỉ Lecturer của lớp được ghi/chốt/công bố. Accountant/Manager xem biên bản IT; chỉ IT bàn giao và thu hồi. Thu hồi quyền có hiệu lực ở request tiếp theo.
- Student chỉ đọc hồ sơ, lịch, điểm đã công bố, chuyên cần, học phí và thiết bị của mình. Không có yêu cầu thiết bị/hỗ trợ hoặc đơn xin nghỉ tự gửi. Lecturer theo lớp được giao. Manager theo khóa/lớp/hợp đồng được giao. HR thấy hàng đợi chưa nhận và hồ sơ mình đã nhận; tư vấn sẽ nhận trách nhiệm hồ sơ. Accountant theo hợp đồng được bàn giao và phân công. IT làm việc trong phạm vi bộ phận thiết bị. Dashboard và báo cáo dùng cùng phạm vi với màn nghiệp vụ.

## Luồng vận hành

1. **Cấu hình khóa học:** Manager tạo khóa, thời lượng, sức chứa lớp, học phí tham khảo, các đầu điểm/trọng số tổng 100% và danh sách giảng viên đủ điều kiện. Khóa nháp chưa công khai. Sau khi mở tuyển sinh, nội dung xuất hiện ở trang công khai.
2. **Tiếp nhận:** khách gửi Contact → lưu vào bảng đăng ký thực tế → HR xem chi tiết, ghi kết quả tư vấn, lịch hẹn và email đã xác minh nếu cần bổ sung. Có lịch sử từng lần tư vấn. Không có trang tra cứu đăng ký công khai.
3. **Hợp đồng:** Admin cấp tài khoản Student với email đã được xác minh; HR liên kết hồ sơ với tài khoản này, chọn khóa và thỏa thuận học phí/cọc. Chỉ sửa bản nháp. Ghi ngày ký, minh chứng và kế toán tiếp nhận để bàn giao. Manager/Accountant xem hợp đồng đã được bàn giao; không sửa hợp đồng thay HR.
4. **Đối soát:** Accountant tiếp nhận chứng từ ngân hàng, đối chiếu hợp đồng/người học, mục đích, số tiền và mã ngân hàng. Giao dịch chưa xác nhận không làm giảm công nợ. Sai lệch phải được làm rõ; tín hiệu trùng mở bản ghi gốc và không thu lần hai. Màn này chưa tự kết nối ngân hàng bên ngoài; không giả lập trạng thái Banking đã tích hợp.
5. **Xếp lớp:** khi học phí đã được xác nhận đủ nghĩa vụ hợp đồng, hệ thống xếp lớp theo khóa/ngày bắt đầu/sức chứa và danh sách giảng viên đủ điều kiện. Nếu chưa có giảng viên khả dụng, lớp giữ chờ phân công để Manager kiểm tra. Hợp đồng học phí bằng 0 cần Accountant xác nhận căn cứ miễn phí, không tạo giao dịch tiền bằng 0. Manager kiểm tra và điều chỉnh lớp, giảng viên, sĩ số/lịch trong phạm vi được giao.
6. **Giảng dạy:** Manager lập lịch, server chặn trùng lớp/phòng/giảng viên. Lecturer điểm danh, lưu tạm hoặc chốt đủ học viên; dữ liệu đã chốt không tự mở lại. Điều chỉnh trước khi chốt phải có lý do. Bảng điểm dùng cấu hình khóa, điểm 0–10; công bố khi đủ đầu điểm. Student chỉ thấy điểm đã công bố.
7. **Thiết bị:** Lecturer tạo yêu cầu cho học viên trong lớp; IT tiếp nhận, chuẩn bị và bàn giao thiết bị khả dụng khi đủ điều kiện cọc. Lưu biên bản ký nhận và lịch trả; không cấp một thiết bị đồng thời cho hai lượt đang hiệu lực. Student xem biên bản của mình. IT có thể chủ động lập và hoàn tất phiếu bảo trì.
8. **Thu hồi/quyết toán:** IT xác nhận thực nhận đúng thiết bị, hiện trạng và minh chứng; có hỏng/mất thì tạo hồ sơ sự cố. IT ghi kỹ thuật và đề xuất; Accountant ghi khoản đã được duyệt, căn cứ phê duyệt và thu riêng hoặc khấu trừ cọc. Số tiền phê duyệt/căn cứ phải đến từ quy trình của trung tâm, không lấy giá trị minh họa trong bản vẽ. Chỉ hoàn cọc một lần sau thu hồi và rà soát nghĩa vụ, có chứng từ thực tế.
9. **Hoàn tất/nghỉ học:** Manager xác nhận học tập dựa trên kết quả đã công bố; server kiểm tra học tập, thiết bị và tài chính trước khi đóng. Nghỉ giữa chừng có ngày hiệu lực/lý do/minh chứng, tự chuyển thiết bị chưa trả sang chờ thu hồi và không coi là hoàn thành khóa. Còn nghĩa vụ thì hồ sơ giữ mở.

Thông báo trong hệ thống được gửi theo người nhận và có thể hẹn giờ. Chỉ Manager có quyền gửi mặc định. Liên kết từ thông báo/dashboard vẫn kiểm tra quyền và phạm vi của màn đích. Các báo cáo lọc kỳ và xuất CSV theo dữ liệu được phép xem.

## Minh chứng và dữ liệu

Tải PDF/PNG/JPEG tối đa 2 MB hoặc nhập tham chiếu hồ sơ đã được xác minh. Tệp lưu trong database; tải xuống yêu cầu phiên đăng nhập và quyền đối với hồ sơ liên quan. Tệp chưa liên kết chỉ người tải lên xem được. Không đưa mật khẩu, mã OTP hoặc nội dung tệp vào nhật ký.

Biểu mẫu giữ dữ liệu khi API báo lỗi; hiển thị lỗi trường, thông báo thành công sau điều hướng và kiểm tra phiên bản để tránh ghi đè. Các biểu mẫu nghiệp vụ cảnh báo khi bỏ thay đổi chưa lưu. Thao tác ghi được ghi vào nhật ký hệ thống cùng mã màn/đối tượng và người thao tác.

## API

| API | Mục đích |
| --- | --- |
| `GET /api/workspace/screens/{code}` | Danh sách, bộ lọc `q/status/page`, lịch tuần `from/to`, dữ liệu biểu mẫu và lựa chọn trong phạm vi |
| `GET /api/workspace/screens/{code}/{id}` | Chi tiết, dữ liệu liên quan, điều kiện nghiệp vụ |
| `POST /api/workspace/screens/{formCode}[/{id}]` | Lưu biểu mẫu đúng quyền/trạng thái; cập nhật cần `version` |
| `POST /api/workspace/screens/{code}/{id}/actions/{action}` | Chốt/công bố, đối soát, thu hồi, quyết toán, đóng hồ sơ… |
| `GET /api/workspace/screens/Reports_{role}?from=YYYY-MM-DD&to=YYYY-MM-DD` | Báo cáo theo role; mã role theo danh mục |
| `POST /api/workspace/files` | Multipart `file` và `screen`, có CSRF |
| `GET /api/workspace/files/{id}` | Tải minh chứng có kiểm tra quyền |
| `GET /api/public/training-courses[/{id}]` | Khóa học đã công khai; không trả người phụ trách/dữ liệu nội bộ |
| `POST /api/public/consultations` | Tiếp tục dùng API đăng ký tư vấn đã có |

Các màn tài khoản, hồ sơ và xác thực tiếp tục dùng API đã có trong [API.md](API.md). Nhận `403` khi không có quyền, `404` khi bản ghi ngoài phạm vi/không tồn tại, `400` khi sai dữ liệu, `409` khi xung đột phiên bản/trạng thái hoặc chưa đủ điều kiện. POST yêu cầu CSRF.

## Tái tạo danh mục thiết kế

```powershell
python -X utf8 scripts/import_screen_design.py --xlsx "C:\Users\FPT SHOP\Downloads\ERHMS_Ma_Tran_Quyen_Truy_Cap.xlsx"
```

Script đọc workbook thành dữ liệu quyền, đối chiếu manifest SVG và bỏ dữ liệu vận hành minh họa. Khi thay đổi thiết kế sau khi V10 đã áp dụng, tạo migration mới; không chạy lại bản V10 đã sửa lên database cũ.

## Bổ sung danh mục dùng chung

BK-VTCMS có thêm 12 màn danh mục riêng, đưa tổng số mã màn lên 120. Khởi động lại backend để áp dụng V11. IT Support quản lý nhóm thiết bị/vị trí kho, Manager quản lý ngành nghề, Admin quản lý phòng học. Các biểu mẫu dùng ID danh mục và kế toán chọn ngân hàng từ `m_bank`. Chi tiết: [MASTER_DATA.md](MASTER_DATA.md).
