# Nộp tiền theo hợp đồng

Học viên mở **Nộp tiền & thanh toán**, chọn hợp đồng đã được duyệt bản ký và cấp tài khoản. Hệ thống hiển thị số tiền còn phải nộp, tài khoản nhận tiền, nội dung chuyển khoản cố định và mã QR.

Tài khoản nhận tiền theo thông tin được cung cấp: **BIDV – PGD Hàm Yên, 3419120803, NGUYEN ANH DUC**. Cấu hình này nằm tại `PaymentReceivingAccount.java`. Không gọi dịch vụ tạo QR bên ngoài; frontend dựng ảnh QR từ payload do backend cấp.

Mẫu hợp đồng mới dùng cùng tài khoản BIDV. Bản Word nguồn được giữ làm tài liệu tham chiếu; mẫu xuất PDF lấy thông tin ngân hàng từ cấu hình nhận tiền. Bản PDF đã gửi sang tuyển sinh được lưu cố định, không tạo lại khi đổi mẫu.

Mỗi học viên có mã cố định dạng `HV000001`: tiền tố `HV` và ID tài khoản được thêm số 0 cho đủ tối thiểu 6 chữ số. Mã không bị cắt khi ID lớn hơn 6 chữ số, không đổi khi đổi họ tên và dùng chung cho các hợp đồng của cùng học viên. Quy luật này áp dụng ngay cho cả tài khoản cũ và mới, không cấp lại hay ghi đè mã khi xem hồ sơ.

Nội dung chuyển khoản là **họ tên học viên không dấu + mã học viên**, ví dụ `NGUYEN ANH DUC HV000123`. Tên dài được rút gọn để nội dung vừa giới hạn 25 ký tự của QR; mã học viên luôn được giữ nguyên. Nội dung mới dùng chung cho học phí, tiền cọc và khoản nộp gộp. Bill đã gửi trước khi đổi quy luật vẫn được đối chiếu theo nội dung đã lưu cùng bill.

Học viên chọn học phí, tiền cọc hoặc cả hai, nhập số tiền cho từng khoản rồi chuyển khoản một lần theo tổng hiển thị. Khi chọn cả hai, bill lưu rõ số học phí và số tiền cọc. Một khoản đang có bill chờ đối chiếu không được chọn lại trong bill khác của cùng hợp đồng, kể cả khi chọn nộp gộp.

Học viên gửi bill PDF, PNG hoặc JPEG tối đa 2 MB, số tiền nguyên VND, ngày chuyển khoản và tên người chuyển. Mã giao dịch do học viên cung cấp chỉ là thông tin tham khảo; nó không được dùng để giữ chỗ mã giao dịch ngân hàng trong sổ thu.

Kế toán mở **Giao dịch thanh toán → Bill học viên gửi**, so bill với sao kê và nhập mã giao dịch thực tế, số tiền, ngày hạch toán và nội dung chuyển khoản. Sau khi đánh dấu đủ các mục đối chiếu, kế toán xác nhận hoặc ghi nhận sai lệch kèm lý do. Bill sai lệch được thông báo lại cho học viên và có thể gửi bản mới.

Tiền chỉ được ghi nhận khi kế toán xác nhận. Nếu giao dịch sao kê đang chờ đã được nhập trước đó và khớp hoàn toàn, hệ thống dùng lại bản ghi đó. Giao dịch đã được xác nhận hoặc đã gắn bill khác không được thu thêm lần nữa. Khoản chuyển gộp được ghi nhận thành hai khoản học phí và cọc trong cùng một lần xử lý; chỉ một bản ghi giữ mã ngân hàng thật để ngăn thu trùng. Bản ghi phân bổ cọc liên kết trực tiếp với bill chung, không tạo mã giao dịch ngân hàng giả.

Học viên chỉ xuất hiện trong **Chi tiết lớp → Thêm học viên** khi học phí đã được xác nhận đủ, hợp đồng đang hoạt động, khóa học khớp với lớp và học viên chưa được xếp vào lớp khác. Bill chờ duyệt, sai lệch, tiền cọc hoặc học phí còn thiếu không đủ điều kiện này. Hợp đồng miễn học phí vẫn cần kế toán xác nhận theo thao tác riêng.

Migration V27 bổ sung mã thanh toán và bảng `student_payment_submissions`. V28 thêm phân bổ học phí, tiền cọc và liên kết giao dịch cọc cho bill nộp gộp; các bill, nội dung chuyển khoản và giao dịch cũ được giữ lại, không tự ghi nhận thêm tiền.

Tham khảo định dạng và mã ngân hàng: [VietQR API Banks](https://www.vietqr.io/api-bank), [payload mẫu VietQR](https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh/api-tao-ma-qr). Thư viện dựng ảnh: [node-qrcode](https://github.com/soldair/node-qrcode).
