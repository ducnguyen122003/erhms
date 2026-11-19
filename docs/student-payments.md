# Nộp tiền theo hợp đồng

Học viên mở **Nộp tiền & thanh toán**, chọn hợp đồng đã được duyệt bản ký và cấp tài khoản. Hệ thống hiển thị số tiền còn phải nộp, tài khoản nhận tiền, nội dung chuyển khoản cố định và mã QR.

Tài khoản nhận tiền theo thông tin được cung cấp: **BIDV – PGD Hàm Yên, 3419120803, NGUYEN ANH DUC**. Cấu hình này nằm tại `PaymentReceivingAccount.java`. Không gọi dịch vụ tạo QR bên ngoài; frontend dựng ảnh QR từ payload do backend cấp.

Mẫu hợp đồng mới dùng cùng tài khoản BIDV. Bản Word nguồn được giữ làm tài liệu tham chiếu; mẫu xuất PDF lấy thông tin ngân hàng từ cấu hình nhận tiền. Bản PDF đã gửi sang tuyển sinh được lưu cố định, không tạo lại khi đổi mẫu.

Mỗi hợp đồng có `payment_reference` duy nhất do database cấp. Nội dung học phí là `BKHP <mã>`, tiền cọc là `BKCOC <mã>`. Nội dung không vượt 25 ký tự, kể cả mã lớn nhất của BIGINT. Mỗi loại khoản thu chỉ có một bill đang chờ đối chiếu trong cùng hợp đồng.

Học viên gửi bill PDF, PNG hoặc JPEG tối đa 2 MB, số tiền nguyên VND, ngày chuyển khoản và tên người chuyển. Mã giao dịch do học viên cung cấp chỉ là thông tin tham khảo; nó không được dùng để giữ chỗ mã giao dịch ngân hàng trong sổ thu.

Kế toán mở **Giao dịch thanh toán → Bill học viên gửi**, so bill với sao kê và nhập mã giao dịch thực tế, số tiền, ngày hạch toán và nội dung chuyển khoản. Sau khi đánh dấu đủ các mục đối chiếu, kế toán xác nhận hoặc ghi nhận sai lệch kèm lý do. Bill sai lệch được thông báo lại cho học viên và có thể gửi bản mới.

Tiền chỉ được ghi nhận khi kế toán xác nhận. Nếu giao dịch sao kê đang chờ đã được nhập trước đó và khớp hoàn toàn, hệ thống dùng lại bản ghi đó. Giao dịch đã được xác nhận hoặc đã gắn bill khác không được thu thêm lần nữa.

Học viên chỉ xuất hiện trong **Chi tiết lớp → Thêm học viên** khi học phí đã được xác nhận đủ, hợp đồng đang hoạt động, khóa học khớp với lớp và học viên chưa được xếp vào lớp khác. Bill chờ duyệt, sai lệch, tiền cọc hoặc học phí còn thiếu không đủ điều kiện này. Hợp đồng miễn học phí vẫn cần kế toán xác nhận theo thao tác riêng.

Migration V27 chỉ bổ sung mã thanh toán và bảng `student_payment_submissions`; các số tiền và giao dịch cũ được giữ lại.

Tham khảo định dạng và mã ngân hàng: [VietQR API Banks](https://www.vietqr.io/api-bank), [payload mẫu VietQR](https://www.vietqr.io/danh-sach-api/link-tao-ma-nhanh/api-tao-ma-qr). Thư viện dựng ảnh: [node-qrcode](https://github.com/soldair/node-qrcode).
