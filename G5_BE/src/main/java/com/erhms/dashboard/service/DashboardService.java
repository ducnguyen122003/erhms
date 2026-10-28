package com.erhms.dashboard.service;

import com.erhms.dashboard.dto.DashboardView;
import com.erhms.dashboard.dto.DashboardView.*;
import com.erhms.dashboard.repository.DashboardRepository;
import com.erhms.role.DashboardRole;
import com.erhms.role.InvalidRoleException;
import com.erhms.shared.exception.BusinessException;
import com.erhms.user.service.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PAID_TUITION = "(SELECT COALESCE(SUM(f.amount),0) FROM financial_transactions f WHERE f.contract_id=c.id AND f.kind='TUITION' AND f.status='CONFIRMED')";
    private static final String OUTSTANDING = "GREATEST(c.tuition_due-" + PAID_TUITION + ",0)";
    private final DashboardRepository db;
    private final CurrentUser current;

    public DashboardService(DashboardRepository db, CurrentUser current) { this.db = db; this.current = current; }

    public DashboardView get(String slug) {
        var user = current.get();
        var role = DashboardRole.resolve(user.getRoleId(), user.getRoleCode())
                .orElseThrow(InvalidRoleException::new);
        if (slug != null) {
            var requested = DashboardRole.bySlug(slug)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Dashboard không tồn tại"));
            if (requested != role) throw new AccessDeniedException("Bạn không có quyền truy cập dashboard này");
        }
        var time = new Time(Instant.now());
        return switch (role) {
            case ADMIN -> admin(time);
            case LECTURER -> lecturer(user.id, time);
            case STUDENT -> student(user.id, time);
            case ACCOUNTANT -> accountant(time);
            case IT_SUPPORT -> support(time);
            case MANAGER -> manager(time);
            case HR -> hr(time);
        };
    }

    private DashboardView admin(Time t) {
        long accounts = n("SELECT COUNT(*) FROM m_user");
        long enabled = n("SELECT COUNT(*) FROM m_user WHERE account_enabled=TRUE AND account_locked=FALSE");
        long locked = n("SELECT COUNT(*) FROM m_user WHERE account_locked=TRUE");
        long disabled = n("SELECT COUNT(*) FROM m_user WHERE account_enabled=FALSE");
        long roles = db.rows("SELECT role_id,code FROM m_role").stream().map(row -> DashboardRole.resolve(((Number)row.get("role_id")).longValue(),s(row,"code")))
                .flatMap(Optional::stream).distinct().count();
        long logins = n("SELECT COUNT(*) FROM user_login_events WHERE occurred_at>=? AND occurred_at<?",t.day(),t.nextDay());
        long events = n("SELECT COUNT(*) FROM request_events WHERE occurred_at>=? AND occurred_at<?",t.day(),t.nextDay());
        var activity = panel("activity", "Nhật ký hoạt động gần đây", "Các lần đăng nhập và thao tác tuyển dụng đã được ghi nhận.",
                List.of("Hoạt động / đối tượng", "Người thực hiện", "Thời gian"),
                """
                SELECT * FROM (
                    SELECT e.id, e.action AS title, r.title AS detail, u.full_name, e.occurred_at
                    FROM request_events e JOIN m_user u ON u.user_id=e.actor_id
                    JOIN recruitment_requests r ON r.id=e.request_id
                    UNION ALL
                    SELECT l.id, 'LOGIN' AS title, u.email AS detail, u.full_name, l.occurred_at
                    FROM user_login_events l JOIN m_user u ON u.user_id=l.user_id
                ) activity ORDER BY occurred_at DESC, id FETCH FIRST 6 ROWS ONLY
                """, row -> row(row, List.of(action(s(row,"title")),s(row,"full_name"),date(row,"occurred_at")), "Đã ghi nhận","green",s(row,"detail")));
        return view(DashboardRole.ADMIN,t,
                List.of(m("accounts","Tổng tài khoản",accounts,"","Trong toàn hệ thống"),m("enabled","Đang hoạt động",enabled,"","Tài khoản được phép đăng nhập"),m("roles","Vai trò hệ thống",roles,"","Các vai trò dashboard được cấu hình"),m("locked","Tài khoản bị khóa",locked,"","Cần kiểm tra trạng thái truy cập","amber")),
                List.of(m("logins","Đăng nhập hôm nay",logins,"lượt","Các lần đăng nhập thành công"),m("events","Thao tác hôm nay",events,"lượt","Nhật ký yêu cầu tuyển dụng"),m("disabled","Chưa kích hoạt / vô hiệu hóa",disabled,"","Tài khoản bị tắt truy cập","amber")),
                List.of(activity), List.of(task("disabled","Tài khoản bị vô hiệu hóa","Kiểm tra trạng thái tài khoản",disabled,"amber"),task("locked","Tài khoản đang bị khóa","Kiểm tra quyền truy cập",locked,"amber"),task("roles","Vai trò được cấu hình","Đã ánh xạ quyền dashboard",roles,"green")),
                chart(t,"Tài khoản đăng nhập trong tuần","Số tài khoản đăng nhập thành công theo ngày, từ nhật ký thực tế.",
                        "SELECT COUNT(DISTINCT user_id) FROM user_login_events WHERE occurred_at>=? AND occurred_at<?"),null);
    }

    private DashboardView lecturer(long id, Time t) {
        long classes=n("SELECT COUNT(*) FROM training_classes WHERE lecturer_id=? AND status='ACTIVE'",id);
        long students=n("SELECT COUNT(DISTINCT e.student_id) FROM training_enrollments e JOIN training_classes c ON c.id=e.class_id WHERE c.lecturer_id=? AND c.status='ACTIVE' AND e.status='ACTIVE'",id);
        long sessions=n("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.lecturer_id=? AND c.status<>'CANCELLED' AND s.starts_at>=? AND s.starts_at<?",id,t.week(),t.nextWeek());
        long requests=n("SELECT COUNT(*) FROM equipment_requests WHERE requested_by=? AND status IN ('NEW','PREPARING','READY')",id);
        long today=n("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.lecturer_id=? AND c.status<>'CANCELLED' AND s.starts_at>=? AND s.starts_at<?",id,t.day(),t.nextDay());
        long attendance=n("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.lecturer_id=? AND c.status<>'CANCELLED' AND s.ends_at<=? AND s.attendance_finalized=FALSE",id,t.now());
        long grades=n("SELECT COUNT(*) FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id JOIN training_classes c ON c.id=e.class_id WHERE c.lecturer_id=? AND g.published_at IS NULL",id);
        var schedule=schedule("Lịch dạy hôm nay & sắp tới","Chỉ hiển thị các lớp được phân công cho bạn.","c.lecturer_id=?",t,id);
        var classPanel=panel("classes","Các lớp đang phụ trách","Tình trạng các lớp trong phạm vi giảng dạy.",List.of("Lớp / nội dung","Học viên","Tiến độ"),
                """
                SELECT c.id,c.code,c.name,c.status,c.total_sessions,
                  (SELECT COUNT(*) FROM training_enrollments e WHERE e.class_id=c.id AND e.status='ACTIVE') AS students,
                  (SELECT COUNT(*) FROM training_sessions s WHERE s.class_id=c.id AND s.ends_at<=?) AS completed
                FROM training_classes c WHERE c.lecturer_id=? AND c.status IN ('ACTIVE','PLANNED')
                ORDER BY c.code FETCH FIRST 6 ROWS ONLY
                """, r->row(r,List.of(s(r,"code")+" · "+s(r,"name"),s(r,"students")+" học viên",s(r,"completed")+" / "+s(r,"total_sessions")+" buổi"),status(s(r,"status")),tone(s(r,"status")),null),t.now(),id);
        return view(DashboardRole.LECTURER,t,
                List.of(m("classes","Lớp đang phụ trách",classes,"","Các lớp được phân công"),m("students","Học viên đang học",students,"","Trong các lớp đang phụ trách"),m("sessions","Buổi dạy tuần này",sessions,"","Theo lịch giảng dạy"),m("requests","Yêu cầu thiết bị mở",requests,"","Đang được IT xử lý","amber")),
                List.of(m("today","Lịch dạy",today,"ca","Trong ngày hôm nay"),m("attendance","Điểm danh",attendance,"buổi","Chưa hoàn tất","amber"),m("grades","Nhập điểm",grades,"mục","Chưa công bố","amber")),
                List.of(schedule,classPanel),List.of(task("attendance","Hoàn tất điểm danh","Các buổi đã kết thúc",attendance,"amber"),task("grades","Nhập điểm thực hành","Các đầu điểm chưa công bố",grades,"amber"),task("requests","Theo dõi yêu cầu thiết bị","Yêu cầu do bạn tạo",requests,"green")),
                chart(t,"Lịch giảng dạy trong tuần","Số buổi dạy được phân công theo ngày.","SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.lecturer_id=? AND c.status<>'CANCELLED' AND s.starts_at>=? AND s.starts_at<?",id),null);
    }

    private DashboardView student(long id, Time t) {
        long recorded=n("SELECT COUNT(*) FROM training_attendance a WHERE a.student_id=? AND a.status IN ('PRESENT','LATE','ABSENT') AND EXISTS (SELECT 1 FROM training_sessions s JOIN training_enrollments e ON e.class_id=s.class_id WHERE s.id=a.session_id AND e.student_id=a.student_id)",id);
        long present=n("SELECT COUNT(*) FROM training_attendance a WHERE a.student_id=? AND a.status IN ('PRESENT','LATE') AND EXISTS (SELECT 1 FROM training_sessions s JOIN training_enrollments e ON e.class_id=s.class_id WHERE s.id=a.session_id AND e.student_id=a.student_id)",id);
        var attendance=recorded==0?BigDecimal.ZERO:BigDecimal.valueOf(present*100.0/recorded).setScale(1,RoundingMode.HALF_UP);
        var grade=a("SELECT AVG(g.score) FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id WHERE e.student_id=? AND g.published_at IS NOT NULL AND g.published_at<=? AND g.score IS NOT NULL",id,t.now());
        if (grade != null) grade=grade.setScale(1,RoundingMode.HALF_UP);
        var tuition=a("SELECT COALESCE(SUM("+OUTSTANDING+"),0) FROM training_contracts c WHERE c.student_id=? AND c.status IN ('SIGNED','ACTIVE')",id);
        long equipment=n("SELECT COUNT(*) FROM equipment_loans WHERE student_id=? AND returned_at IS NULL AND return_status<>'RETURNED'",id);
        String own="EXISTS (SELECT 1 FROM training_enrollments e WHERE e.class_id=c.id AND e.student_id=? AND e.status='ACTIVE')";
        var schedule=schedule("Lịch học hôm nay & sắp tới","Lịch học được cập nhật theo các lớp của bạn.",own,t,id);
        var grades=panel("grades","Kết quả học tập gần đây","Các đầu điểm đã được giảng viên công bố.",List.of("Nội dung đánh giá","Lớp","Ngày công bố","Điểm / 10"),
                """
                SELECT g.id,g.title,g.score,g.published_at,c.code FROM training_grades g
                JOIN training_enrollments e ON e.id=g.enrollment_id JOIN training_classes c ON c.id=e.class_id
                WHERE e.student_id=? AND g.published_at IS NOT NULL AND g.published_at<=? AND g.score IS NOT NULL
                ORDER BY g.published_at DESC,g.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"title"),s(r,"code"),date(r,"published_at"),s(r,"score")),"Đã công bố","green",null),id,t.now());
        var devices=panel("equipment","Thiết bị của tôi","Thông tin thiết bị đang được bàn giao cho bạn.",List.of("Thiết bị","Mã tài sản","Ngày nhận","Tiền cọc"),
                """
                SELECT l.id,a.name,a.code,l.received_at,l.deposit_amount,l.return_status FROM equipment_loans l
                JOIN equipment_assets a ON a.id=l.asset_id
                WHERE l.student_id=? AND l.returned_at IS NULL AND l.return_status<>'RETURNED'
                ORDER BY l.received_at DESC,l.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"name"),s(r,"code"),date(r,"received_at"),money(r,"deposit_amount")),status(s(r,"return_status")),tone(s(r,"return_status")),null),id);
        long total=n("SELECT COALESCE(SUM(c.total_sessions),0) FROM training_classes c WHERE EXISTS (SELECT 1 FROM training_enrollments e WHERE e.class_id=c.id AND e.student_id=? AND e.status IN ('ACTIVE','COMPLETED')) AND c.status<>'CANCELLED'",id);
        long completed=n("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE s.ends_at<=? AND c.status<>'CANCELLED' AND EXISTS (SELECT 1 FROM training_enrollments e WHERE e.class_id=c.id AND e.student_id=? AND e.status IN ('ACTIVE','COMPLETED'))",t.now(),id);
        long absent=n("SELECT COUNT(*) FROM training_attendance a WHERE a.student_id=? AND a.status='ABSENT' AND EXISTS (SELECT 1 FROM training_sessions s JOIN training_enrollments e ON e.class_id=s.class_id WHERE s.id=a.session_id AND e.student_id=a.student_id)",id);
        var dates=db.rows("SELECT MAX(c.ends_on) AS end_date FROM training_classes c WHERE c.status<>'CANCELLED' AND EXISTS (SELECT 1 FROM training_enrollments e WHERE e.class_id=c.id AND e.student_id=? AND e.status IN ('ACTIVE','COMPLETED'))",id);
        Object end=dates.get(0).get("end_date");
        long upcoming=n("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.status<>'CANCELLED' AND s.starts_at>=? AND s.starts_at<? AND "+own,t.day(),t.nextWeek(),id);
        return view(DashboardRole.STUDENT,t,
                List.of(m("attendance","Chuyên cần",attendance,"%",recorded==0?"Chưa có dữ liệu điểm danh":present+" / "+recorded+" buổi đã điểm danh"),m("grade","Điểm trung bình tạm tính",grade,"/ 10",grade==null?"Chưa có điểm đã công bố":"Từ các đầu điểm đã công bố"),m("tuition","Học phí còn phải nộp",tuition,"đ","Theo các hợp đồng đang hiệu lực"),m("equipment","Thiết bị đang giữ",equipment,"","Đã được nhận bàn giao")),
                List.of(m("progress","Tiến độ",total==0?0:Math.min(100,Math.round(completed*100.0/total)),"%",completed+" / "+total+" buổi đã diễn ra"),m("upcoming","Buổi học sắp tới",upcoming,"buổi","Từ hôm nay đến cuối tuần"),m("absent","Vắng học",absent,"buổi","Theo điểm danh đã ghi nhận","amber")),
                List.of(schedule,grades,devices),List.of(task("tuition","Theo dõi học phí","Hợp đồng còn dư nợ học phí",n("SELECT COUNT(*) FROM training_contracts c WHERE c.student_id=? AND c.status IN ('SIGNED','ACTIVE') AND "+OUTSTANDING+">0",id),"amber"),task("returns","Thiết bị đến hạn trả","Kiểm tra lịch thu hồi",n("SELECT COUNT(*) FROM equipment_loans WHERE student_id=? AND returned_at IS NULL AND return_status<>'RETURNED' AND return_due_at<=?",id,t.nextDay()),"amber")),
                chart(t,"Lịch học trong tuần","Số buổi học trong các lớp đã xếp.","SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.status<>'CANCELLED' AND "+own+" AND s.starts_at>=? AND s.starts_at<?",id),new Progress(completed,total,absent,end==null?null:end.toString()));
    }

    private DashboardView accountant(Time t) {
        var tuition=a("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE kind='TUITION' AND status='CONFIRMED' AND created_at>=? AND created_at<?",t.month(),t.nextMonth());
        var deposits=a("SELECT GREATEST(COALESCE(SUM(CASE WHEN kind='DEPOSIT' THEN amount WHEN kind='REFUND' THEN -amount ELSE 0 END),0),0) FROM financial_transactions WHERE status='CONFIRMED'");
        var receivables=a("SELECT COALESCE(SUM("+OUTSTANDING+"),0) FROM training_contracts c WHERE c.status IN ('SIGNED','ACTIVE')");
        var compensation=a("SELECT COALESCE(SUM(compensation_due),0) FROM equipment_incidents WHERE status<>'RESOLVED'");
        long pending=n("SELECT COUNT(*) FROM financial_transactions WHERE status='PENDING'");
        long mismatch=n("SELECT COUNT(*) FROM financial_transactions WHERE status='MISMATCH'");
        long refunds=n("SELECT COUNT(*) FROM financial_transactions WHERE kind='REFUND' AND status='PENDING'");
        long unpaid=n("SELECT COUNT(*) FROM training_contracts c WHERE c.status IN ('SIGNED','ACTIVE') AND "+OUTSTANDING+">0");
        var transactions=panel("transactions","Giao dịch cần đối soát","Kiểm tra giao dịch trước khi xác nhận vào hệ thống.",List.of("Hợp đồng / học viên","Loại giao dịch","Số tiền","Thời gian"),
                """
                SELECT f.id,c.code,u.full_name,f.kind,f.amount,f.created_at,f.status FROM financial_transactions f
                JOIN training_contracts c ON c.id=f.contract_id JOIN m_user u ON u.user_id=c.student_id
                WHERE f.status IN ('PENDING','MISMATCH') ORDER BY f.created_at DESC,f.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"code")+" · "+s(r,"full_name"),kind(s(r,"kind")),money(r,"amount"),date(r,"created_at")),status(s(r,"status")),tone(s(r,"status")),null));
        var payments=panel("payments","Tình trạng học phí theo hợp đồng","Công nợ được tính từ học phí và giao dịch đã xác nhận.",List.of("Hợp đồng","Học viên","Học phí","Đã thanh toán","Còn phải thu"),
                "SELECT c.id,c.code,u.full_name,c.tuition_due,"+PAID_TUITION+" AS paid,"+OUTSTANDING+" AS remaining FROM training_contracts c JOIN m_user u ON u.user_id=c.student_id WHERE c.status IN ('SIGNED','ACTIVE') ORDER BY remaining DESC,c.code FETCH FIRST 6 ROWS ONLY",
                r->row(r,List.of(s(r,"code"),s(r,"full_name"),money(r,"tuition_due"),money(r,"paid"),money(r,"remaining")),number(r,"remaining").signum()>0?"Còn công nợ":"Đã thanh toán",number(r,"remaining").signum()>0?"amber":"green",null));
        return view(DashboardRole.ACCOUNTANT,t,
                List.of(m("tuition","Học phí đã thu tháng này",tuition,"đ","Chỉ tính giao dịch đã xác nhận"),m("deposits","Cọc thiết bị đang giữ",deposits,"đ","Tiền cọc đã nhận trừ khoản hoàn"),m("receivables","Học phí còn phải thu",receivables,"đ",unpaid+" hợp đồng còn dư nợ","amber"),m("compensation","Bồi thường chờ thu",compensation,"đ","Từ hồ sơ sự cố chưa giải quyết","amber")),
                List.of(m("pending","Chờ đối soát",pending,"","Giao dịch chưa xác nhận","amber"),m("mismatch","Cần kiểm tra",mismatch,"","Sai lệch thông tin","red"),m("refunds","Chờ hoàn cọc",refunds,"","Giao dịch hoàn cọc đang chờ","amber")),
                List.of(transactions,payments),List.of(task("pending","Giao dịch chờ xác minh","Đối chiếu thông tin hợp đồng",pending,"amber"),task("mismatch","Giao dịch sai lệch","Rà soát trước khi xác nhận",mismatch,"red"),task("refunds","Hoàn cọc thiết bị","Kiểm tra khoản hoàn đang chờ",refunds,"green"),task("compensation","Bồi thường chờ thanh toán","Hồ sơ sự cố còn khoản phải thu",n("SELECT COUNT(*) FROM equipment_incidents WHERE status<>'RESOLVED' AND compensation_due>0"),"amber")),
                chart(t,"Giao dịch đã xác nhận trong tuần","Số giao dịch thực tế được ghi nhận theo ngày.","SELECT COUNT(*) FROM financial_transactions WHERE status='CONFIRMED' AND created_at>=? AND created_at<?"),null);
    }

    private DashboardView support(Time t) {
        long total=n("SELECT COUNT(*) FROM equipment_assets WHERE status<>'RETIRED'");
        long assigned=n("SELECT COUNT(DISTINCT asset_id) FROM equipment_loans WHERE returned_at IS NULL AND return_status<>'RETURNED'");
        long available=n("SELECT COUNT(*) FROM equipment_assets a WHERE a.status='AVAILABLE' AND NOT EXISTS (SELECT 1 FROM equipment_loans l WHERE l.asset_id=a.id AND l.returned_at IS NULL AND l.return_status<>'RETURNED')");
        long maintenance=n("SELECT COUNT(*) FROM equipment_assets WHERE status='MAINTENANCE'");
        long pending=n("SELECT COUNT(*) FROM equipment_requests WHERE status='NEW'");
        long handovers=n("SELECT COUNT(*) FROM equipment_requests WHERE status='READY'");
        long returns=n("SELECT COUNT(*) FROM equipment_loans WHERE returned_at IS NULL AND return_status<>'RETURNED' AND (return_status='RETURN_DUE' OR return_due_at<=?)",t.nextDay());
        long incidents=n("SELECT COUNT(*) FROM equipment_incidents WHERE status<>'RESOLVED'");
        var requests=panel("requests","Yêu cầu cấp thiết bị từ giảng viên","Kiểm tra tồn kho, chuẩn bị thiết bị và hẹn bàn giao.",List.of("Yêu cầu / người tạo","Lớp","Số lượng","Ngày tạo"),
                """
                SELECT r.id,r.title,u.full_name,c.code,r.quantity,r.status,r.created_at FROM equipment_requests r
                JOIN m_user u ON u.user_id=r.requested_by LEFT JOIN training_classes c ON c.id=r.class_id
                WHERE r.status IN ('NEW','PREPARING','READY') ORDER BY r.created_at,r.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"title")+" · "+s(r,"full_name"),s(r,"code"),s(r,"quantity"),date(r,"created_at")),status(s(r,"status")),tone(s(r,"status")),null));
        var loans=panel("loans","Lịch bàn giao & thu hồi","Các thiết bị đang lưu hành, ưu tiên hạn thu hồi sắp tới.",List.of("Thiết bị","Học viên","Ngày bàn giao","Hạn trả"),
                """
                SELECT l.id,a.name,a.code,u.full_name,l.received_at,l.return_due_at,l.return_status FROM equipment_loans l
                JOIN equipment_assets a ON a.id=l.asset_id JOIN m_user u ON u.user_id=l.student_id
                WHERE l.returned_at IS NULL AND l.return_status<>'RETURNED'
                ORDER BY l.return_due_at ASC NULLS LAST,l.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"code")+" · "+s(r,"name"),s(r,"full_name"),date(r,"received_at"),date(r,"return_due_at")),status(s(r,"return_status")),tone(s(r,"return_status")),null));
        return view(DashboardRole.IT_SUPPORT,t,
                List.of(m("equipment","Tổng thiết bị",total,"","Theo danh mục tài sản còn sử dụng"),m("assigned","Đang cấp cho học viên",assigned,"","Theo biên bản bàn giao chưa thu hồi"),m("available","Sẵn sàng cấp phát",available,"","Thiết bị khả dụng trong kho"),m("maintenance","Đang bảo trì",maintenance,"","Tạm dừng cấp phát","amber")),
                List.of(m("pending","Cấp phát",pending,"","Yêu cầu chờ xử lý","amber"),m("handovers","Bàn giao",handovers,"","Yêu cầu đã chuẩn bị xong"),m("returns","Thu hồi",returns,"","Đến hạn hoặc đang chờ thu hồi","amber")),
                List.of(requests,loans),List.of(task("pending","Yêu cầu cấp phát mới","Tiếp nhận yêu cầu từ giảng viên",pending,"amber"),task("handovers","Bàn giao theo yêu cầu","Thiết bị đã sẵn sàng",handovers,"green"),task("returns","Thu hồi thiết bị","Hoàn thành kiểm tra và nhập kho",returns,"amber"),task("incidents","Sự cố / bảo trì mở","Các hồ sơ chưa xử lý xong",incidents,"red")),
                chart(t,"Yêu cầu thiết bị trong tuần","Số yêu cầu cấp thiết bị được tạo theo ngày.","SELECT COUNT(*) FROM equipment_requests WHERE created_at>=? AND created_at<?"),null);
    }

    private DashboardView manager(Time t) {
        long students=n("SELECT COUNT(DISTINCT e.student_id) FROM training_enrollments e JOIN training_classes c ON c.id=e.class_id WHERE e.status='ACTIVE' AND c.status='ACTIVE'");
        long classes=n("SELECT COUNT(*) FROM training_classes WHERE status='ACTIVE'");
        long assignments=n("SELECT COUNT(*) FROM training_enrollments WHERE assignment_status='PENDING' AND status IN ('PENDING','ACTIVE')");
        long incomplete=n("SELECT COUNT(*) FROM training_enrollments WHERE records_complete=FALSE AND status IN ('PENDING','ACTIVE')");
        long conflicts=n("SELECT COUNT(DISTINCT a.id) FROM training_sessions a JOIN training_classes ca ON ca.id=a.class_id WHERE ca.status<>'CANCELLED' AND a.ends_at>? AND EXISTS (SELECT 1 FROM training_sessions b JOIN training_classes cb ON cb.id=b.class_id WHERE b.id<>a.id AND cb.status<>'CANCELLED' AND a.starts_at<b.ends_at AND a.ends_at>b.starts_at AND ((ca.lecturer_id IS NOT NULL AND ca.lecturer_id=cb.lecturer_id) OR (a.room IS NOT NULL AND a.room<>'' AND a.room=b.room)))",t.now());
        long closing=n("SELECT COUNT(*) FROM training_classes WHERE status='ACTIVE' AND ends_on>=? AND ends_on<=?",t.today(),t.today().plusDays(7));
        long withdrawn=n("SELECT COUNT(*) FROM training_enrollments WHERE status='WITHDRAWN'");
        long lowAttendance=n("SELECT COUNT(*) FROM (SELECT a.student_id FROM training_attendance a JOIN training_sessions s ON s.id=a.session_id JOIN training_classes c ON c.id=s.class_id WHERE c.status='ACTIVE' AND a.status IN ('PRESENT','LATE','ABSENT') AND EXISTS (SELECT 1 FROM training_enrollments e WHERE e.student_id=a.student_id AND e.class_id=c.id AND e.status='ACTIVE') GROUP BY a.student_id HAVING SUM(CASE WHEN a.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END)*1.0/COUNT(*)<0.8) attention");
        var assignmentPanel=panel("assignments","Kết quả xếp lớp cần kiểm tra","Kiểm tra hồ sơ học viên trước khi xác nhận lớp.",List.of("Học viên","Lớp đề xuất","Tình trạng hồ sơ"),
                """
                SELECT e.id,u.full_name,c.code,c.name,e.assignment_status,e.records_complete FROM training_enrollments e
                JOIN m_user u ON u.user_id=e.student_id JOIN training_classes c ON c.id=e.class_id
                WHERE e.assignment_status='PENDING' AND e.status IN ('PENDING','ACTIVE')
                ORDER BY e.created_at,e.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"full_name"),s(r,"code")+" · "+s(r,"name"),Boolean.TRUE.equals(r.get("records_complete"))?"Đã đầy đủ":"Cần bổ sung"),"Chờ kiểm tra","amber",null));
        var attention=new Panel("attention","Theo dõi lớp học & học viên","Các chỉ số cần điều phối trong hệ thống.",List.of("Nội dung","Số lượng"),List.of(
                new Row("attendance",List.of("Học viên chuyên cần dưới 80%",String.valueOf(lowAttendance)),"Cần theo dõi","amber","Tỷ lệ từ các buổi có điểm danh"),
                new Row("closing",List.of("Lớp kết thúc trong 7 ngày tới",String.valueOf(closing)),"Sắp kết thúc","green",null),
                new Row("withdrawn",List.of("Học viên nghỉ giữa chừng",String.valueOf(withdrawn)),"Cần rà soát","red",null)));
        return view(DashboardRole.MANAGER,t,
                List.of(m("students","Học viên đang học",students,"","Thuộc các khóa đang mở"),m("classes","Lớp đang hoạt động",classes,"","Theo dữ liệu lớp học"),m("assignments","Chờ kiểm tra xếp lớp",assignments,"","Xác minh lớp được đề xuất","amber"),m("incomplete","Hồ sơ chưa hoàn tất",incomplete,"","Cần bổ sung hồ sơ học viên","amber")),
                List.of(m("assignments","Xếp lớp",assignments,"","Kết quả cần kiểm tra","amber"),m("conflicts","Lịch học",conflicts,"","Buổi trùng giảng viên hoặc phòng","red"),m("incomplete","Hoàn tất hồ sơ",incomplete,"","Đang chờ cập nhật","amber")),
                List.of(assignmentPanel,attention),List.of(task("assignments","Kiểm tra xếp lớp","Xác nhận lớp và thông tin học viên",assignments,"amber"),task("conflicts","Xung đột lịch học","Rà soát phòng và giảng viên",conflicts,"red"),task("incomplete","Hồ sơ chưa đủ điều kiện","Kiểm tra thông tin còn thiếu",incomplete,"amber"),task("withdrawn","Nghỉ học cần rà soát","Theo dõi học viên đã nghỉ",withdrawn,"red")),
                chart(t,"Học viên được ghi danh trong tuần","Số hồ sơ ghi danh được tạo theo ngày.","SELECT COUNT(*) FROM training_enrollments WHERE created_at>=? AND created_at<?"),null);
    }

    private DashboardView hr(Time t) {
        long registrations=n("SELECT COUNT(*) FROM consultation_registrations WHERE created_date>=? AND created_date<?",t.week(),t.nextWeek());
        long consulting=n("SELECT COUNT(*) FROM consultation_followups WHERE status IN ('CONTACTED','APPOINTMENT')");
        long drafts=n("SELECT COUNT(*) FROM training_contracts WHERE status='DRAFT'");
        long contracts=n("SELECT COUNT(*) FROM training_contracts WHERE status IN ('SIGNED','ACTIVE','COMPLETED') AND created_at>=? AND created_at<?",t.week(),t.nextWeek());
        long fresh=n("SELECT COUNT(*) FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE f.id IS NULL OR f.status='NEW'");
        long appointments=n("SELECT COUNT(*) FROM consultation_followups WHERE status='APPOINTMENT' AND appointment_at>=? AND appointment_at<?",t.day(),t.nextDay());
        long pendingPayments=n("SELECT COUNT(*) FROM training_contracts c WHERE c.status IN ('SIGNED','ACTIVE') AND "+OUTSTANDING+">0");
        var panel=panel("registrations","Đăng ký tư vấn mới từ website","Thông tin đăng ký được lấy từ cơ sở dữ liệu website công khai.",List.of("Người đăng ký","Khóa học quan tâm","Điện thoại / email","Ngày đăng ký"),
                """
                SELECT r.id,r.full_name,r.program_name,r.phone,r.email,r.created_date,COALESCE(f.status,'NEW') AS status
                FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id
                ORDER BY r.created_date DESC,r.id FETCH FIRST 6 ROWS ONLY
                """,r->row(r,List.of(s(r,"full_name"),s(r,"program_name"),s(r,"phone")+(s(r,"email").isBlank()?"":" · "+s(r,"email")),date(r,"created_date")),status(s(r,"status")),tone(s(r,"status")),null));
        return view(DashboardRole.HR,t,
                List.of(m("registrations","Đăng ký mới tuần này",registrations,"","Nguồn từ website công khai"),m("consulting","Đang tư vấn",consulting,"","Hồ sơ đang được liên hệ"),m("drafts","Hợp đồng chờ ký",drafts,"","Chưa chuyển bước thanh toán","amber"),m("contracts","Hợp đồng tuần này đã ký",contracts,"","Hợp đồng tạo trong tuần và đã ký")),
                List.of(m("fresh","Tiếp nhận",fresh,"","Đăng ký mới cần liên hệ","amber"),m("consulting","Tư vấn",consulting,"","Hồ sơ đang theo dõi"),m("drafts","Hợp đồng",drafts,"","Đang chờ ký","amber")),
                List.of(panel),List.of(task("appointments","Lịch hẹn tư vấn","Lịch hẹn được ghi nhận trong hôm nay",appointments,"amber"),task("drafts","Hợp đồng đang chờ ký","Rà soát thông tin và điều khoản",drafts,"amber"),task("fresh","Đăng ký mới cần tiếp nhận","Liên hệ và ghi nhận nhu cầu",fresh,"green"),task("payments","Hợp đồng chờ xác nhận học phí","Theo dõi khoản học phí còn phải thu",pendingPayments,"green")),
                chart(t,"Đăng ký tư vấn trong tuần","Số đăng ký mới được tiếp nhận từ website công khai.","SELECT COUNT(*) FROM consultation_registrations WHERE created_date>=? AND created_date<?"),null);
    }

    private Panel schedule(String title,String description,String scope,Time t,long id) {
        return panel("schedule",title,description,List.of("Lớp / nội dung","Thời gian","Phòng"),
                "SELECT s.id,c.code,s.title,s.starts_at,s.ends_at,s.room,s.attendance_finalized FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.status<>'CANCELLED' AND "+scope+" AND s.ends_at>=? ORDER BY s.starts_at,s.id FETCH FIRST 6 ROWS ONLY",
                r->row(r,List.of(s(r,"code")+" · "+s(r,"title"),date(r,"starts_at")+" – "+time(r,"ends_at"),s(r,"room")),Boolean.TRUE.equals(r.get("attendance_finalized"))?"Đã điểm danh":"Theo lịch",Boolean.TRUE.equals(r.get("attendance_finalized"))?"green":"neutral",null),id,t.day());
    }

    private DashboardView view(DashboardRole role,Time t,List<Metric> stats,List<Metric> highlights,List<Panel> panels,List<Task> tasks,Chart chart,Progress progress) {
        return new DashboardView(role.name(),t.now(),stats,highlights,panels,tasks,chart,progress);
    }
    private Panel panel(String key,String title,String description,List<String> columns,String sql,Function<Map<String,Object>,Row> mapper,Object...args) {
        return new Panel(key,title,description,columns,db.rows(sql,args).stream().map(mapper).toList());
    }
    private static Row row(Map<String,Object> r,List<String> cells,String status,String tone,String detail) { return new Row(s(r,"id"),cells,status,tone,detail); }
    private static Metric m(String key,String label,Number value,String unit,String description) { return m(key,label,value,unit,description,"green"); }
    private static Metric m(String key,String label,Number value,String unit,String description,String tone) { return new Metric(key,label,value,unit,description,tone); }
    private static Task task(String key,String title,String description,long count,String tone) { return new Task(key,title,description,count,tone); }
    private long n(String sql,Object...args) { return db.count(sql,args); }
    private BigDecimal a(String sql,Object...args) { return db.amount(sql,args); }
    private static String s(Map<String,Object> r,String key) { return Objects.toString(r.get(key),""); }
    private static BigDecimal number(Map<String,Object> r,String key) { return new BigDecimal(s(r,key)); }
    private static String money(Map<String,Object> r,String key) { return number(r,key).setScale(0,RoundingMode.HALF_UP).toPlainString()+" đ"; }
    private static Instant instant(Object value) {
        if (value instanceof OffsetDateTime time) return time.toInstant();
        if (value instanceof Timestamp time) return time.toInstant();
        if (value instanceof Instant time) return time;
        return null;
    }
    private static String date(Map<String,Object> r,String key) { var i=instant(r.get(key)); return i==null?"":DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE).format(i); }
    private static String time(Map<String,Object> r,String key) { var i=instant(r.get(key)); return i==null?"":DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE).format(i); }
    private Chart chart(Time t,String title,String description,String sql,Object...prefix) {
        var points=new ArrayList<Point>();
        for(int i=6;i>=0;i--) {
            LocalDate day=t.today().minusDays(i);
            var args=new ArrayList<Object>(Arrays.asList(prefix));
            args.add(day.atStartOfDay(ZONE).toInstant()); args.add(day.plusDays(1).atStartOfDay(ZONE).toInstant());
            points.add(new Point(day.format(DateTimeFormatter.ofPattern("dd/MM")),n(sql,args.toArray())));
        }
        return new Chart(title,description,points);
    }
    private static String kind(String raw) { return switch(raw) { case "TUITION"->"Học phí"; case "DEPOSIT"->"Cọc thiết bị"; case "REFUND"->"Hoàn cọc"; case "COMPENSATION"->"Bồi thường"; default->raw; }; }
    private static String action(String raw) { return switch(raw) { case "LOGIN"->"Đăng nhập hệ thống"; case "CREATED"->"Tạo yêu cầu tuyển dụng"; case "UPDATED"->"Cập nhật yêu cầu"; case "SUBMIT"->"Gửi yêu cầu tuyển dụng"; case "APPROVE"->"Duyệt yêu cầu"; case "REQUEST-REVISION"->"Yêu cầu chỉnh sửa"; case "CLOSE"->"Đóng yêu cầu"; default->raw; }; }
    private static String status(String raw) { return switch(raw) { case "ACTIVE"->"Đang hoạt động"; case "PLANNED"->"Sắp khai giảng"; case "COMPLETED"->"Đã hoàn tất"; case "PENDING"->"Chờ đối soát"; case "CONFIRMED"->"Đã xác nhận"; case "MISMATCH"->"Sai lệch thông tin"; case "NEW"->"Chưa tiếp nhận"; case "PREPARING"->"Đang chuẩn bị"; case "READY"->"Sẵn sàng bàn giao"; case "DELIVERED"->"Đã bàn giao"; case "RETURN_DUE"->"Chờ thu hồi"; case "CONTACTED"->"Đang tư vấn"; case "APPOINTMENT"->"Hẹn tư vấn"; case "CONTRACTED"->"Đã lập hợp đồng"; case "CLOSED"->"Đã đóng"; default->raw; }; }
    private static String tone(String raw) { return switch(raw) { case "MISMATCH","REJECTED"->"red"; case "NEW","PENDING","RETURN_DUE","PLANNED"->"amber"; case "PREPARING","APPOINTMENT"->"neutral"; default->"green"; }; }
    private record Time(Instant now) {
        LocalDate today() { return now.atZone(ZONE).toLocalDate(); }
        Instant day() { return today().atStartOfDay(ZONE).toInstant(); }
        Instant nextDay() { return today().plusDays(1).atStartOfDay(ZONE).toInstant(); }
        Instant week() { return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE).toInstant(); }
        Instant nextWeek() { return week().atZone(ZONE).plusWeeks(1).toInstant(); }
        Instant month() { return today().withDayOfMonth(1).atStartOfDay(ZONE).toInstant(); }
        Instant nextMonth() { return month().atZone(ZONE).plusMonths(1).toInstant(); }
    }
}
