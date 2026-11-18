# Hai dashboard tuyển sinh

Khởi động lại backend để Flyway áp dụng các migration đến **V22__admission_academic_forwarding.sql**, rồi tải lại frontend. V21 thêm quyền dashboard; V22 lưu thời điểm/người chuyển sang học vụ. Hồ sơ đang có hợp đồng/tài khoản được giữ ở nhóm đã chuyển để tiếp tục xử lý; hồ sơ chưa được xử lý vẫn chờ tuyển sinh quyết định chuyển. Không tự chuyển các hồ sơ `PROFILE_PENDING` chưa có hợp đồng.

| Dashboard | Đường dẫn | Phạm vi |
| --- | --- | --- |
| **Tư vấn & tiếp nhận hồ sơ** | `/screens/ListAdmissionApplication` | Người quan tâm khóa học, đang tư vấn, hẹn nộp hoặc còn thiếu giấy tờ. Giữ các hồ sơ không tiếp tục để tra cứu lịch sử. Có nhập Excel. |
| **Hoàn tất thủ tục nhập học** | `/screens/ListAdmissionEnrollment` | Hai trang **Chờ chuyển học vụ** và **Đã chuyển học vụ**. Tuyển sinh chuyển hồ sơ đủ giấy tờ rồi theo dõi quá trình xử lý. |

Hai trang có thống kê, tìm kiếm và bộ lọc riêng; số liệu tính trên toàn bộ kết quả trong phạm vi của HR, không chỉ 25 bản ghi đang hiển thị. Hồ sơ chưa nhận được hiển thị ở hàng đợi tiếp nhận; hồ sơ đã nhận chỉ hiển thị cho HR được phân công. Nguồn Excel do Manager nhập giữ luồng riêng, không đưa vào hai dashboard của HR.

Đủ giấy tờ được tính theo **CCCD, Sơ yếu lý lịch, Giấy khai sinh bản sao, Ảnh thẻ, Đơn xin**, mỗi loại có ít nhất một tệp đã lưu. Nhiều PDF cùng loại chỉ tính là một loại. Sau khi gửi/bổ sung đủ, hồ sơ tự xuất hiện ở trang **Chờ chuyển học vụ**; học vụ chưa thấy hồ sơ, chưa tải được PDF và chưa được lập hợp đồng/cấp tài khoản.

Tại **Chờ chuyển học vụ** (`?tab=waiting`), bấm **Chuyển học vụ** trên từng hàng hoặc chọn nhiều hàng rồi **Chuyển quản lý học vụ**. Popup hiển thị danh sách để kiểm tra trước khi **Xác nhận chuyển**. Chọn tất cả chỉ áp dụng cho trang hiện tại; đổi tìm kiếm/bộ lọc/trang sẽ bỏ lượt chọn cũ. Mỗi lượt tối đa 100 hồ sơ; backend khóa các hồ sơ, kiểm tra phân công, phiên bản và đủ 5 loại giấy tờ trước khi chuyển. Nếu một hồ sơ không hợp lệ thì cả lượt không thay đổi. Không chuyển lặp hồ sơ đã chuyển.

Sau khi chuyển, hồ sơ nằm ở **Đã chuyển học vụ** (`?tab=forwarded`) với người/thời điểm chuyển và tiến độ bên học vụ. Manager mới thấy hồ sơ trong hàng đợi và biểu mẫu lập hợp đồng. HR vẫn bổ sung/chỉnh sửa PDF bằng [ADMISSION_PROFILES.md](ADMISSION_PROFILES.md). Trước khi chuyển, sửa khiến thiếu giấy tờ thì hồ sơ quay về dashboard tiếp nhận; sau khi chuyển, hồ sơ tiếp tục ở trang đã chuyển để theo dõi và hiển thị cảnh báo cần bổ sung nếu còn thiếu. Không tự chuyển lại hoặc mất dấu lịch sử chuyển khi sửa PDF. Nút quay lại từ chi tiết về đúng trang chờ/đã chuyển.

Trang **Đã chuyển học vụ** có ba tiến độ độc lập:

- **Hợp đồng:** chờ lập, chờ ký hoặc đã ký. Hợp đồng hủy không được tính. Nếu một hồ sơ có nhiều hợp đồng, vẫn chỉ hiển thị một hàng và có số hợp đồng còn chờ ký.
- **Tài khoản:** chờ lập hợp đồng, chờ cấp hoặc đã cấp. Theo liên kết tài khoản thực tế trên hồ sơ, không suy đoán từ việc email xuất hiện trong DB.
- **Học phí:** chờ lập/bàn giao hợp đồng, chưa đóng, đóng một phần hoặc hoàn tất. Chỉ cộng giao dịch **TUITION / CONFIRMED**; không cộng khoản chờ đối soát, sai lệch hoặc cọc. Số tiền chỉ công khai sau khi bàn giao. Khoản còn phải đóng được tính riêng từng hợp đồng, không dùng tiền đóng dư của hợp đồng này để bù cho hợp đồng khác. **Xem học phí** mở màn đối chiếu/hướng dẫn hiện có với email học viên được điền sẵn.

HR chỉ theo dõi các tiến độ này; việc lập/ký hợp đồng và cấp tài khoản vẫn do Manager, xác nhận tiền vẫn do Accountant theo quyền hiện hành. Quyền của hai dashboard có thể cấp/thu hồi độc lập. Mở màn, menu, liên kết chi tiết và học phí đều kiểm tra quyền tương ứng.

API đọc danh sách dùng `GET /api/workspace/screens/{code}?q=...&status=...&page=...&tab=waiting|forwarded`. Backend áp phạm vi từng trang trước khi tìm/lọc, trả về `tabs`, `canForward`, `filters`, `stats`, `items`, `total`, `totalPages`. Các mã lọc tiến độ ở trang đã chuyển: `NO_CONTRACT`, `WAIT_SIGNATURE`, `WAIT_ACCOUNT`, `ACCOUNT_CREATED`, `WAIT_HANDOVER`, `UNPAID`, `PARTIALLY_PAID`, `PAID`.

API chuyển dùng `POST /api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles`, JSON `{ "admissions": [{ "id": "<UUID>", "version": 3 }] }`. Cần HR có cả quyền `ListAdmissionEnrollment` và `AdmissionConsultation`, đúng phân công và CSRF. Thành công ghi `forwarded_at`, `forwarded_by`, tăng phiên bản và thêm lịch sử `FORWARDED`. Nguồn Excel do Manager nhập không cần qua bước chuyển của HR.
