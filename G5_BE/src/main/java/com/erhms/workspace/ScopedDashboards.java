package com.erhms.workspace;

import com.erhms.dashboard.dto.DashboardView;
import com.erhms.dashboard.dto.DashboardView.*;
import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;

/** Dashboard summaries use the same ownership predicates as the corresponding screens. */
@Service
public class ScopedDashboards {
    final WorkflowData d;static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public ScopedDashboards(WorkflowData d){this.d=d;}
    String scope(String resource,User user){return "("+d.base(resource,user)+") records";}
    long count(String resource,User user,String condition,Object...args){return d.count("SELECT COUNT(*) FROM "+scope(resource,user)+" WHERE "+condition,args);}
    BigDecimal sum(String resource,User user,String expression,String condition,Object...args){return d.jdbc.queryForObject("SELECT COALESCE(SUM("+expression+"),0) FROM "+scope(resource,user)+" WHERE "+condition,BigDecimal.class,args);}
    Metric metric(String key,String label,Number value,String unit){return new Metric(key,label,value,unit,"Trong phạm vi được phân công","green");}
    Task task(String key,String label,long count){return new Task(key,label,"Kiểm tra hồ sơ được giao",count,"amber");}
    Panel panel(String key,String title,List<String> columns,List<Map<String,Object>> rows,Function<Map<String,Object>,List<String>> cells){return new Panel(key,title,"Dữ liệu theo quyền và phạm vi được phân công.",columns,rows.stream().map(r->new Row(r.get("id").toString(),cells.apply(r),s(r,"status"),"neutral",null)).toList());}
    static String s(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    List<Map<String,Object>> recent(String resource,User user,String condition,String order){return d.rows("SELECT * FROM "+scope(resource,user)+" WHERE "+condition+" ORDER BY "+order+" LIMIT 6");}
    OffsetDateTime at(LocalDate day){return day.atStartOfDay(ZONE).toOffsetDateTime();}
    Chart chart(User u,String resource,String timeColumn,String condition,String title){
        List<Point> points=new ArrayList<>();LocalDate today=LocalDate.now(ZONE);
        for(int i=6;i>=0;i--){LocalDate day=today.minusDays(i);points.add(new Point(day.format(DateTimeFormatter.ofPattern("dd/MM")),count(resource,u,condition+" AND "+timeColumn+">=? AND "+timeColumn+"<?",at(day),at(day.plusDays(1)))));}
        return new Chart(title,"Dữ liệu trong phạm vi phân công theo ngày.",points);
    }
    public DashboardView accountant(User u){
        LocalDate today=LocalDate.now(ZONE),month=today.withDayOfMonth(1);BigDecimal tuition=sum("payments",u,"amount","kind='TUITION' AND status='CONFIRMED' AND created_at>=? AND created_at<?",at(month),at(month.plusMonths(1)));
        BigDecimal held=sum("payments",u,"CASE WHEN kind='DEPOSIT' THEN amount WHEN kind='REFUND' THEN -amount ELSE 0 END","status='CONFIRMED'");
        BigDecimal offsets=d.jdbc.queryForObject("SELECT COALESCE(SUM(s.deduction_amount),0) FROM deposit_settlements s JOIN training_contracts c ON c.id=s.contract_id WHERE "+WorkflowData.contractScope(u,"c"),BigDecimal.class);held=held.subtract(offsets).max(BigDecimal.ZERO);
        BigDecimal due=sum("contracts",u,"GREATEST(tuition_due-tuition_paid,0)","status IN ('SIGNED','ACTIVE')"),comp=sum("incidents",u,"COALESCE(approved_amount,compensation_due)","status<>'RESOLVED'");
        long pending=count("payments",u,"status='PENDING'"),mismatch=count("payments",u,"status='MISMATCH'"),refunds=count("payments",u,"kind='REFUND' AND status='PENDING'");
        var transactions=panel("transactions","Giao dịch cần đối soát",List.of("Hợp đồng / học viên","Khoản thu","Số tiền"),recent("payments",u,"status IN ('PENDING','MISMATCH')","created_at DESC"),r->List.of(s(r,"contractCode")+" · "+s(r,"studentName"),s(r,"kind"),s(r,"amount")+" đ"));
        var contracts=panel("payments","Tình trạng học phí theo hợp đồng",List.of("Hợp đồng","Học viên","Học phí","Đã xác nhận"),recent("contracts",u,"status IN ('SIGNED','ACTIVE')","created_at DESC"),r->List.of(s(r,"code"),s(r,"studentName"),s(r,"tuitionDue")+" đ",s(r,"tuitionPaid")+" đ"));
        return new DashboardView("ACCOUNTANT",Instant.now(),List.of(metric("tuition","Học phí đã thu tháng này",tuition,"đ"),metric("deposits","Cọc thiết bị đang giữ",held,"đ"),metric("receivables","Học phí còn phải thu",due,"đ"),metric("compensation","Bồi thường chờ thu",comp,"đ")),List.of(metric("pending","Chờ đối soát",pending,""),metric("mismatch","Cần kiểm tra",mismatch,""),metric("refunds","Chờ hoàn cọc",refunds,"")),List.of(transactions,contracts),List.of(task("pending","Giao dịch chờ xác minh",pending),task("mismatch","Giao dịch sai lệch",mismatch),task("refunds","Hoàn cọc thiết bị",refunds),task("compensation","Bồi thường chờ thanh toán",count("incidents",u,"status<>'RESOLVED' AND compensation_due>0"))),chart(u,"payments","created_at","status='CONFIRMED'","Giao dịch đã xác nhận trong tuần"),null);
    }
    public DashboardView manager(User u){
        long classes=count("classes",u,"status='ACTIVE'"),students=d.count("SELECT COUNT(DISTINCT e.student_id) FROM training_enrollments e JOIN training_classes c ON c.id=e.class_id WHERE e.status='ACTIVE' AND "+WorkflowData.classScope(u,"c"));
        long assignments=count("assignments",u,"assignment_status='PENDING' AND status IN ('PENDING','ACTIVE')"),incomplete=count("closures",u,"records_complete=FALSE AND closed_at IS NULL"),withdrawn=count("closures",u,"status='WITHDRAWN' AND closed_at IS NULL");
        long conflicts=d.count("SELECT COUNT(DISTINCT a.id) FROM training_sessions a JOIN training_classes c ON c.id=a.class_id WHERE "+WorkflowData.classScope(u,"c")+" AND a.ends_at>CURRENT_TIMESTAMP AND EXISTS(SELECT 1 FROM training_sessions b JOIN training_classes bc ON bc.id=b.class_id WHERE b.id<>a.id AND a.starts_at<b.ends_at AND a.ends_at>b.starts_at AND (b.class_id=a.class_id OR c.lecturer_id=bc.lecturer_id OR (a.classroom_id=b.classroom_id OR (a.classroom_id IS NULL AND b.classroom_id IS NULL AND a.room IS NOT NULL AND LOWER(a.room)=LOWER(b.room)))))");
        var pending=panel("assignments","Kết quả xếp lớp cần kiểm tra",List.of("Học viên","Lớp đề xuất","Hồ sơ"),recent("assignments",u,"assignment_status='PENDING'","created_at"),r->List.of(s(r,"studentName"),s(r,"classCode"),Boolean.TRUE.equals(r.get("recordsComplete"))?"Đầy đủ":"Cần kiểm tra"));
        var attention=new Panel("attention","Theo dõi lớp học & học viên","Trong phạm vi quản lý.",List.of("Nội dung","Số lượng"),List.of(new Row("incomplete",List.of("Hồ sơ chưa hoàn tất",Long.toString(incomplete)),"Cần kiểm tra","amber",null),new Row("withdrawn",List.of("Nghỉ giữa chừng chưa đóng",Long.toString(withdrawn)),"Theo dõi","amber",null)));
        return new DashboardView("MANAGER",Instant.now(),List.of(metric("students","Học viên đang học",students,""),metric("classes","Lớp đang hoạt động",classes,""),metric("assignments","Chờ kiểm tra xếp lớp",assignments,""),metric("incomplete","Hồ sơ chưa hoàn tất",incomplete,"")),List.of(metric("assignments","Xếp lớp",assignments,""),metric("conflicts","Lịch học",conflicts,""),metric("incomplete","Hoàn tất hồ sơ",incomplete,"")),List.of(pending,attention),List.of(task("assignments","Kiểm tra xếp lớp",assignments),task("conflicts","Xung đột lịch học",conflicts),task("incomplete","Hồ sơ chưa đủ điều kiện",incomplete),task("withdrawn","Nghỉ học cần rà soát",withdrawn)),chart(u,"assignments","created_at","1=1","Học viên ghi danh trong tuần"),null);
    }
    public DashboardView hr(User u){
        LocalDate today=LocalDate.now(ZONE),week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long registrations=count("admissions",u,"created_date>=? AND created_date<?",at(week),at(week.plusWeeks(1))),consulting=count("admissions",u,"status IN ('CONTACTED','APPOINTMENT')"),drafts=count("contracts",u,"status='DRAFT'"),contracts=count("contracts",u,"status IN ('SIGNED','ACTIVE','COMPLETED') AND created_at>=? AND created_at<?",at(week),at(week.plusWeeks(1))),fresh=count("admissions",u,"status='NEW'"),appointments=count("admissions",u,"status='APPOINTMENT' AND appointment_at>=? AND appointment_at<?",at(today),at(today.plusDays(1))),payments=count("contracts",u,"status IN ('SIGNED','ACTIVE') AND tuition_paid<tuition_due");
        var applications=panel("registrations","Đăng ký tư vấn mới từ website",List.of("Người đăng ký","Khóa quan tâm","Điện thoại / email","Ngày đăng ký"),recent("admissions",u,"1=1","created_date DESC"),r->List.of(s(r,"fullName"),s(r,"programName"),s(r,"phone")+" · "+s(r,"email"),s(r,"createdDate")));
        return new DashboardView("HR",Instant.now(),List.of(metric("registrations","Đăng ký mới tuần này",registrations,""),metric("consulting","Đang tư vấn",consulting,""),metric("drafts","Hợp đồng chờ ký",drafts,""),metric("contracts","Hợp đồng tuần này đã ký",contracts,"")),List.of(metric("fresh","Tiếp nhận",fresh,""),metric("consulting","Tư vấn",consulting,""),metric("drafts","Hợp đồng",drafts,"")),List.of(applications),List.of(task("appointments","Lịch hẹn tư vấn",appointments),task("drafts","Hợp đồng đang chờ ký",drafts),task("fresh","Đăng ký mới cần tiếp nhận",fresh),task("payments","Hợp đồng chờ xác nhận học phí",payments)),chart(u,"admissions","created_date","1=1","Đăng ký tư vấn trong tuần"),null);
    }
}
