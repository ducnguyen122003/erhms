# Kế hoạch lớp học theo năm

Admin vào **Cấu hình lịch học theo khóa**, chọn một trong bốn khóa cố định rồi chọn **Tạo lịch học**. Biểu mẫu gồm năm kế hoạch, quản lý học vụ phụ trách, giảng viên, sĩ số, phòng học, ngày khai giảng và ngày kết thúc. Các thứ học trong tuần cùng giờ bắt đầu/kết thúc tạo thành lịch cố định của lớp. Hệ thống sinh tất cả buổi học trong khoảng ngày đã chọn và kiểm tra trùng lớp, phòng, giảng viên trước khi lưu.

Danh sách cố định gồm: Sửa chữa máy tính Laptop chuyên nghiệp (4 tháng, 14.200.000 đ), Sửa chữa máy tính PC & máy in (4 tháng, 13.000.000 đ), Sửa chữa Điện tử - Điện gia dụng (4 tháng, 11.000.000 đ), Sửa chữa Điện lạnh, Điện gia dụng & Inverter (7 tháng, 21.900.000 đ). Tên, thời lượng và học phí gốc lấy từ cấu hình hệ thống; admin tạo lịch cho các khóa này. Học phí thỏa thuận của từng hợp đồng vẫn được quản lý ở nghiệp vụ hợp đồng.

Các mục quản trị ngành nghề, danh mục khóa học và danh sách lớp của admin được thay bằng chức năng cấu hình lịch học theo khóa. Giảng viên được chọn từ tài khoản giảng viên đang hoạt động; quản lý học vụ được gắn với từng kế hoạch để phân quyền hợp đồng và học viên đúng người phụ trách.

Năm kế hoạch là năm khai giảng; lớp có thể học tiếp sang năm sau, tối đa 366 ngày. Mỗi năm có kế hoạch riêng, không tự tạo lớp cho các năm tiếp theo. Danh sách lớp hỗ trợ lọc theo năm.

Sau khi tạo, ngày khai giảng, ngày kết thúc, khóa đào tạo, phòng, các thứ học và giờ học được cố định. Admin có thể điều chỉnh giảng viên, sĩ số, trạng thái kèm lý do; việc đổi giảng viên vẫn kiểm tra xung đột. Kế hoạch chưa có hợp đồng giữ chỗ hoặc học viên có thể hủy để lập lại. Lịch cũ chưa thuộc kế hoạch cố định chỉ được admin duy trì, QLHV xem lịch và quản lý học viên.

QLHV chọn lớp khi lập hợp đồng. Lớp phải thuộc đúng khóa và phạm vi phụ trách, có lịch đã chốt, còn hoạt động và còn chỗ. Ngày bắt đầu, ngày kết thúc và ngày khai giảng trong bản PDF lấy từ lớp; biểu mẫu không cho đặt ngày riêng cho từng học viên.

Mỗi hợp đồng chưa hủy, kể cả bản nháp, giữ một chỗ trong lớp. Học viên đã vào lớp không bị tính chỗ hai lần nếu hợp đồng đã giữ chỗ; học viên cũ chưa có hợp đồng liên kết lớp vẫn được tính vào sĩ số. Đổi lớp trong hợp đồng bản nháp kiểm tra lại chỗ trống. Hợp đồng đã gửi ký không được đổi lớp qua chức năng sửa bản nháp.

Giữ chỗ trên hợp đồng chưa tạo danh sách học viên đang học. Sau khi kế toán xác nhận đủ học phí, QLHV chỉ được xếp học viên vào lớp đã ghi trên hợp đồng. Bill chờ đối chiếu hoặc tiền cọc không thay thế điều kiện học phí. Hợp đồng cũ chưa liên kết lớp tiếp tục được xử lý theo khóa học; khi xếp lớp, liên kết lớp được ghi nhận.

Migration V29 thêm kế hoạch năm và liên kết lớp trên hợp đồng, cấp quyền lập lớp/lịch cho admin và thu hồi quyền sửa kế hoạch của QLHV. V30 bổ sung bốn khóa cố định và quyền **Cấu hình lịch học theo khóa**, đồng thời ngừng các màn hình sửa danh mục cũ. Lịch, hợp đồng và PDF đã ký cũ được giữ nguyên.
