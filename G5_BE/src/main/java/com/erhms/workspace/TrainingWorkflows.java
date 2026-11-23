package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class TrainingWorkflows {
    private static final String PAID_UNASSIGNED_CONTRACT="c.status='ACTIVE' AND COALESCE((SELECT SUM(t.amount) FROM financial_transactions t WHERE t.contract_id=c.id AND t.kind='TUITION' AND t.status='CONFIRMED'),0)>=c.tuition_due AND NOT EXISTS(SELECT 1 FROM training_enrollments linked WHERE linked.contract_id=c.id)";
    private static final String MATCH_CONTRACT_COURSE="(c.course_id=? OR (c.course_id IS NULL AND c.registered_course_name=?))";
    private static final String MATCH_SELECTED_CLASS="(c.class_id IS NULL OR c.class_id=?)";
    final WorkflowData d;final ContractTemplateData contractTemplate;
    public TrainingWorkflows(WorkflowData d,ContractTemplateData contractTemplate){this.d=d;this.contractTemplate=contractTemplate;}
    static Map<String,Object> saved(String screen,Object id){return Map.of("screen",screen,"id",String.valueOf(id),"notice","Đã lưu thông tin thành công.");}

    public List<Map<String,Object>> unassignedStudents(String classId,User u){
        role(u,"MANAGER");var cl=d.record("classes",classId,u);
        if(cl.get("courseId")==null||!List.of("PLANNED","ACTIVE").contains(cl.get("status")))return List.of();
        var registered=EnrollmentCourses.find(cl.get("courseName"));
        return d.rows("SELECT scoped.id,scoped.student_code,scoped.full_name,scoped.email,scoped.phone FROM ("+d.base("students",u)+") scoped JOIN m_user account ON account.user_id=scoped.id WHERE account.account_enabled=TRUE AND account.account_locked=FALSE AND NOT EXISTS(SELECT 1 FROM training_enrollments e WHERE e.student_id=scoped.id AND (e.status IN ('ACTIVE','PENDING') OR e.class_id=?)) AND EXISTS(SELECT 1 FROM training_contracts c WHERE c.student_id=scoped.id AND "+MATCH_CONTRACT_COURSE+" AND c.manager_id=? AND "+MATCH_SELECTED_CLASS+" AND "+PAID_UNASSIGNED_CONTRACT+") ORDER BY scoped.full_name,scoped.id",uuid(classId),uuid(cl.get("courseId").toString()),registered==null?null:registered.name(),u.id,uuid(classId));
    }

    public Map<String,Object> addClassStudents(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");UUID key=uuid(id);var cl=d.record("classes",id,u);
        if(!List.of("PLANNED","ACTIVE").contains(cl.get("status")))throw conflict("Chỉ thêm học viên vào lớp đang chuẩn bị hoặc đang học");
        if(cl.get("courseId")==null)throw invalid("students","Lớp cần liên kết khóa học trước khi xếp học viên");
        UUID courseId=uuid(cl.get("courseId").toString());
        var registered=EnrollmentCourses.find(cl.get("courseName"));
        String registeredName=registered==null?null:registered.name();
        var selected=items(p,"students");if(selected.isEmpty())throw invalid("students","Chọn ít nhất một học viên chưa được xếp lớp");
        SortedSet<Long> students=new TreeSet<>();
        for(var item:selected)if(!students.add(number(item,"id",1,Long.MAX_VALUE)))throw invalid("students","Danh sách học viên bị trùng");
        Map<Long,UUID> contracts=new LinkedHashMap<>();
        for(long student:students){
            d.record("students",Long.toString(student),u);
            var eligible=d.rows("SELECT c.id FROM training_contracts c WHERE c.student_id=? AND "+MATCH_CONTRACT_COURSE+" AND c.manager_id=? AND "+MATCH_SELECTED_CLASS+" AND "+PAID_UNASSIGNED_CONTRACT+" ORDER BY c.class_id NULLS LAST,c.id",student,courseId,registeredName,u.id,key);
            if(eligible.isEmpty())throw invalid("students","Chỉ xếp lớp cho học viên đã được kế toán xác nhận đủ học phí của khóa học này và chưa được xếp lớp");
            contracts.put(student,uuid(eligible.get(0).get("id").toString()));
        }
        // Finance and closure workflows lock the contract first. Keep the same order:
        // contracts (sorted) -> course -> students (sorted) -> class.
        for(UUID contract:new TreeSet<>(contracts.values()))d.one("SELECT id FROM training_contracts WHERE id=? FOR UPDATE",contract);
        d.one("SELECT id FROM training_courses WHERE id=? FOR UPDATE",courseId);
        var latestCourse=d.record("courses",courseId.toString(),u);
        registered=EnrollmentCourses.find(latestCourse.get("name"));registeredName=registered==null?null:registered.name();
        for(long student:students){
            d.one("SELECT user_id FROM m_user WHERE user_id=? FOR UPDATE",student);d.record("students",Long.toString(student),u);d.requireRoleId(student,3);
            if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE student_id=? AND (status IN ('ACTIVE','PENDING') OR class_id=?)",student,key)>0)throw conflict("Học viên đã được xếp lớp hoặc đã có hồ sơ tại lớp này. Hãy tải lại danh sách.");
            if(d.count("SELECT COUNT(*) FROM training_contracts c WHERE c.id=? AND c.student_id=? AND "+MATCH_CONTRACT_COURSE+" AND c.manager_id=? AND "+MATCH_SELECTED_CLASS+" AND "+PAID_UNASSIGNED_CONTRACT,contracts.get(student),student,courseId,registeredName,u.id,key)==0)throw conflict("Thông tin học phí hoặc xếp lớp đã thay đổi. Hãy tải lại danh sách học viên.");
        }
        d.lock("training_classes",key,p);cl=d.record("classes",id,u);
        if(!List.of("PLANNED","ACTIVE").contains(cl.get("status")))throw conflict("Chỉ thêm học viên vào lớp đang chuẩn bị hoặc đang học");
        long additional=0;
        for(UUID contract:contracts.values())if(d.count("SELECT COUNT(*) FROM training_contracts WHERE id=? AND class_id=?",contract,key)==0)additional++;
        if(ClassReservations.occupied(d,key,null)+additional>((Number)cl.get("capacity")).longValue())throw invalid("students","Số học viên được chọn vượt quá chỗ trống đã trừ các hợp đồng giữ chỗ của lớp");
        for(long student:students){
            d.jdbc.update("UPDATE training_contracts SET course_id=COALESCE(course_id,?),class_id=COALESCE(class_id,?),version=version+1 WHERE id=? AND (course_id IS NULL OR class_id IS NULL)",courseId,key,contracts.get(student));
            d.jdbc.update("INSERT INTO training_enrollments(id,class_id,student_id,status,assignment_status,contract_id) VALUES (?,?,?,'ACTIVE',?,?)",UUID.randomUUID(),key,student,cl.get("lecturerId")==null?"PENDING":"CONFIRMED",contracts.get(student));
        }
        d.jdbc.update("UPDATE training_classes SET version=version+1 WHERE id=?",key);
        return saved("ClassDetail",key);
    }

    public Map<String,Object> consultation(String id,Map<String,Object> p,User u){
        role(u,"HR");UUID key=uuid(id);d.record("admissions",id,u);
        // Lock the parent even when no follow-up exists yet; two HR staff cannot claim the same registration.
        d.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",key);d.record("admissions",id,u);
        var rows=d.rows("SELECT * FROM consultation_followups WHERE consultation_id=?",key);
        String previous=rows.isEmpty()?"NEW":rows.get(0).get("status").toString();
        if(previous.equals("NOT_PROCEEDING"))throw conflict("Hồ sơ đã Không tiếp tục đăng ký, không thể ghi nhận tư vấn thêm. Hãy tạo hồ sơ đăng ký mới khi học viên có nhu cầu trở lại.");
        if(List.of("PROFILE_PENDING","PROFILE_CREATED","CONTRACTED").contains(previous)||(!rows.isEmpty()&&rows.get(0).get("profileSubmittedAt")!=null))throw conflict("Hồ sơ đã gửi, không ghi nhận tư vấn thêm. Hãy dùng chức năng bổ sung hoặc chỉnh sửa hồ sơ.");
        String channel=choice(p,"channel","PHONE","EMAIL","IN_PERSON"),status=choice(p,"status","NEEDS_COUNSELING","DOCUMENT_APPOINTMENT","NOT_PROCEEDING","PROFILE_PENDING","PROFILE_CREATED","CONTRACTED");
        boolean managed=List.of("PROFILE_PENDING","PROFILE_CREATED","CONTRACTED").contains(previous);
        if(List.of("PROFILE_PENDING","PROFILE_CREATED","CONTRACTED").contains(status)&&!status.equals(previous))throw invalid("status","Trạng thái hồ sơ được cập nhật tự động khi gửi bản scan, lập hợp đồng hoặc cấp tài khoản");
        if(managed&&!status.equals(previous))throw invalid("status","Hồ sơ đã gửi quản lý; chỉ được bổ sung nội dung tư vấn, không đổi trạng thái");
        boolean contracted=d.count("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED'",key)>0;
        if(contracted&&!List.of("CONTRACTED","PROFILE_CREATED").contains(status))throw invalid("status","Hồ sơ đã có hợp đồng, không đưa về giai đoạn tư vấn");
        if(!contracted&&status.equals("CONTRACTED"))throw invalid("status","Trạng thái Đã lập hợp đồng được cập nhật khi Manager tạo hợp đồng");
        String notes=text(p,"notes",2000,status.equals("NEEDS_COUNSELING")||managed);
        OffsetDateTime appointment=status.equals("NEEDS_COUNSELING")?instant(p,"appointmentAt",false):managed&&rows.get(0).get("appointmentAt")!=null?OffsetDateTime.parse(rows.get(0).get("appointmentAt").toString()):null;
        LocalDate documentDue=status.equals("DOCUMENT_APPOINTMENT")?date(p,"documentDueOn",true):managed&&rows.get(0).get("documentDueOn")!=null?LocalDate.parse(rows.get(0).get("documentDueOn").toString()):null;
        String reason=status.equals("NOT_PROCEEDING")?text(p,"discontinuationReason",2000,true):null;
        if(status.equals("NOT_PROCEEDING"))notes="";
        if(p.containsKey("email")){String email=text(p,"email",190,false).toLowerCase(Locale.ROOT);if(!email.isEmpty()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid("email","Email không hợp lệ");
            String stored=Objects.toString(d.one("SELECT email FROM consultation_registrations WHERE id=?",key).get("email"),"").strip().toLowerCase(Locale.ROOT);
            if(!email.isEmpty()){
                if(contracted&&!email.equals(stored))throw invalid("email","Email đã dùng để lập hợp đồng; không đổi email khi cấp tài khoản học viên");
                d.jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",email,key);
            }}
        if(rows.isEmpty())d.jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,appointment_at,channel,notes,document_due_on,discontinuation_reason) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),key,u.id,status,appointment,channel,notes.isEmpty()?null:notes,documentDue,reason);
        else{long version=number(p,"version",0,Long.MAX_VALUE);if(version!=((Number)rows.get(0).get("version")).longValue())throw conflict("Kết quả tư vấn đã được cập nhật. Hãy tải lại.");d.jdbc.update("UPDATE consultation_followups SET assigned_to=?,status=?,appointment_at=?,channel=?,notes=?,document_due_on=?,discontinuation_reason=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE consultation_id=?",u.id,status,appointment,channel,notes.isEmpty()?null:notes,documentDue,reason,key);}
        String history=status.equals("NOT_PROCEEDING")?reason:notes.isEmpty()?"Hẹn nộp hồ sơ ngày "+documentDue.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+".":notes;
        d.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content,appointment_at,document_due_on,discontinuation_reason) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),key,u.id,channel,status,history,appointment,documentDue,reason);
        return saved("ListAdmissionApplicationDetail",key);
    }
    public Map<String,Object> student(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");d.record("students",id,u);long key=numericId(id);
        d.one("SELECT user_id FROM m_user WHERE user_id=? FOR UPDATE",key);boolean exists=d.count("SELECT COUNT(*) FROM student_profiles WHERE user_id=?",key)>0;
        if(exists)d.lock("student_profiles",key,p);
        LocalDate birth=date(p,"dateOfBirth",true);if(birth.isAfter(LocalDate.now()))throw invalid("dateOfBirth","Ngày sinh không được ở tương lai");
        String name=text(p,"fullName",120,true),phone=text(p,"phone",20,true),emergency=text(p,"emergencyPhone",20,false);
        if(!phone.matches("(?:0[0-9]{9}|\\+?84[0-9]{9})"))throw invalid("phone","Số điện thoại không hợp lệ");
        if(!emergency.isEmpty()&&!emergency.matches("(?:0[0-9]{9}|\\+?84[0-9]{9})"))throw invalid("emergencyPhone","Số điện thoại khẩn cấp không hợp lệ");
        // Login email is administered through accounts; an administrative student form cannot change credentials.
        String identity=text(p,"identityNumber",40,false),address=text(p,"address",500,true),contact=text(p,"emergencyName",120,false);
        d.jdbc.update("UPDATE m_user SET full_name=?,phone=?,admin_version=admin_version+1 WHERE user_id=?",name,phone,key);
        if(!exists)d.jdbc.update("INSERT INTO student_profiles(user_id,date_of_birth,identity_number,address,emergency_name,emergency_phone,manager_id) VALUES (?,?,?,?,?,?,?)",key,birth,identity,address,contact,emergency,u.id);
        else d.jdbc.update("UPDATE student_profiles SET date_of_birth=?,identity_number=?,address=?,emergency_name=?,emergency_phone=?,version=version+1 WHERE user_id=?",birth,identity,address,contact,emergency,key);
        return saved("StudentProfileDetail",key);
    }
    public Map<String,Object> contract(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");UUID key=id==null?UUID.randomUUID():uuid(id);Map<String,Object> existing=null;
        if(id!=null){existing=d.record("contracts",id,u);if(!"DRAFT".equals(existing.get("status")))throw conflict("Chỉ được sửa hợp đồng bản nháp");}
        UUID admission=uuid(p,"consultationId");
        // Consultation updates take the same parent lock; recheck the latest status before creating a contract.
        UUID previousAdmission=existing==null||existing.get("consultationId")==null?null:uuid(existing.get("consultationId").toString());
        var admissionLocks=new TreeSet<UUID>();admissionLocks.add(admission);if(previousAdmission!=null)admissionLocks.add(previousAdmission);
        for(UUID locked:admissionLocks)d.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",locked);
        if(id!=null){
            d.lock("training_contracts",key,p);existing=d.record("contracts",id,u);
            if(!Objects.equals(previousAdmission,existing.get("consultationId")==null?null:uuid(existing.get("consultationId").toString())))throw conflict("Hồ sơ liên kết hợp đồng vừa thay đổi. Hãy tải lại.");
            if(!"DRAFT".equals(existing.get("status")))throw conflict("Chỉ được sửa hợp đồng bản nháp");
        }
        var application=d.one("SELECT r.*,f.forwarded_at,COALESCE(f.status,'NEW') AS status FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE r.id=?",admission);
        if("ADMISSION".equals(application.get("intakeSource"))&&application.get("forwardedAt")==null)throw invalid("consultationId","Chờ tuyển sinh chuyển hồ sơ sang quản lý học vụ trước khi lập hợp đồng.");
        boolean keepExisting=existing!=null&&admission.toString().equals(Objects.toString(existing.get("consultationId"),""))&&List.of("CONTRACTED","PROFILE_CREATED").contains(application.get("status"));
        boolean ready=List.of("PROFILE_PENDING","PROFILE_CREATED").contains(application.get("status"))||("EXCEL".equals(application.get("intakeSource"))&&"CONTACTED".equals(application.get("status")));
        if(!ready&&!keepExisting)throw invalid("consultationId","Chỉ lập hợp đồng sau khi đã nhận bản scan hồ sơ hoặc nhập học viên từ Excel");
        var registeredCourse=EnrollmentCourses.find(application.get("enrollmentCourse"));
        boolean sameAdmission=existing!=null&&admission.toString().equals(Objects.toString(existing.get("consultationId"),""));
        String requestedClass=text(p,"classId",100,false);
        UUID selectedClass=!requestedClass.isEmpty()?uuid(requestedClass):existing!=null&&existing.get("classId")!=null?uuid(existing.get("classId").toString()):null;
        if(selectedClass==null)throw invalid("classId","Chọn lớp đã được admin chốt kế hoạch trước khi lập hợp đồng.");
        var targetClass=d.record("classes",selectedClass.toString(),u);
        if(targetClass.get("courseId")==null)throw invalid("classId","Lớp cần liên kết khóa đào tạo trước khi lập hợp đồng.");
        UUID course;
        String requestedCourse=text(p,"courseId",100,false);
        if(!requestedCourse.isEmpty())course=uuid(requestedCourse);
        else course=uuid(targetClass.get("courseId").toString());
        boolean sameCourse=sameAdmission&&Objects.equals(existing.get("courseId"),course.toString());
        Map<String,Object> co=null;
        if(course!=null){
            d.one("SELECT id FROM training_courses WHERE id=? FOR UPDATE",course);
            co=d.record("courses",course.toString(),u);
            if(!List.of("OPEN","ACTIVE").contains(co.get("status")))throw invalid("courseId","Chọn khóa học đang mở tuyển sinh hoặc đang hoạt động");
            if(registeredCourse!=null&&!sameCourse&&!EnrollmentCourses.normalized(registeredCourse.name()).equals(EnrollmentCourses.normalized(co.get("name").toString())))
                throw invalid("courseId","Khóa học trên hợp đồng phải khớp khóa học đã đăng ký trong hồ sơ.");
        }
        var classLocks=new TreeSet<UUID>();classLocks.add(selectedClass);
        if(existing!=null&&existing.get("classId")!=null)classLocks.add(uuid(existing.get("classId").toString()));
        for(UUID locked:classLocks)d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",locked);
        targetClass=d.record("classes",selectedClass.toString(),u);
        if(!Boolean.TRUE.equals(targetClass.get("planFixed"))||targetClass.get("startsOn")==null||targetClass.get("endsOn")==null||targetClass.get("lecturerId")==null)
            throw invalid("classId","Chọn lớp có lịch cố định và giảng viên đã được admin xác nhận.");
        if(!course.toString().equals(targetClass.get("courseId")))throw invalid("classId","Lớp phải thuộc đúng khóa học trên hợp đồng.");
        if(!List.of("PLANNED","ACTIVE").contains(targetClass.get("status")))throw invalid("classId","Chọn lớp đang chuẩn bị khai giảng hoặc đang hoạt động.");
        boolean sameClass=existing!=null&&selectedClass.toString().equals(existing.get("classId"));
        if(!sameClass&&LocalDate.parse(targetClass.get("endsOn").toString()).isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw invalid("classId","Lớp đã kết thúc thời gian đào tạo; chọn lớp trong kế hoạch hiện tại.");
        d.requireRoleId(((Number)targetClass.get("lecturerId")).longValue(),2);
        if(ClassReservations.occupied(d,selectedClass,id==null?null:key)>=((Number)targetClass.get("capacity")).longValue())
            throw invalid("classId","Lớp đã đủ sĩ số, bao gồm các hợp đồng đang giữ chỗ. Chọn lớp còn chỗ trống.");
        String courseName=sameCourse&&existing.get("registeredCourseName")!=null?existing.get("registeredCourseName").toString():registeredCourse!=null?registeredCourse.name():co.get("name").toString();
        int courseMonths=sameCourse&&existing.get("registeredDurationMonths")!=null?((Number)existing.get("registeredDurationMonths")).intValue():registeredCourse!=null?registeredCourse.durationMonths():((Number)co.get("durationMonths")).intValue();
        long managerId=u.id;
        if(d.count("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND (registered_course_name=? OR course_id=?) AND id<>? AND status<>'CANCELLED'",admission,courseName,course,key)>0)throw invalid("consultationId","Hồ sơ này đã có hợp đồng cho khóa học được chọn");
        String email=text(application,"email",190,true).toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid("email","Cần cập nhật email hợp lệ của học viên trước khi lập hợp đồng");
        String applicant=text(application,"fullName",160,true),phone=text(application,"phone",30,false);
        var accounts=d.rows("SELECT user_id AS id,role_id,account_enabled,account_locked FROM m_user WHERE LOWER(TRIM(email))=?",email);
        if(accounts.size()>1)throw invalid("email","Email có nhiều tài khoản; cần quản trị viên kiểm tra trước khi lập hợp đồng");
        Long student=null;
        if(!accounts.isEmpty()){
            var account=accounts.get(0);
            if(((Number)account.get("roleId")).longValue()!=3||!Boolean.TRUE.equals(account.get("accountEnabled"))||Boolean.TRUE.equals(account.get("accountLocked")))throw invalid("email","Email thuộc tài khoản không phải học viên đang hoạt động");
            student=((Number)account.get("id")).longValue();
        }
        if(p.get("studentId")!=null&&!Objects.equals(student,number(p,"studentId",1,Long.MAX_VALUE)))throw invalid("studentId","Tài khoản phải khớp email học viên trong hồ sơ; không chọn tài khoản khác");
        if(existing!=null&&existing.get("studentId")!=null&&!Objects.equals(((Number)existing.get("studentId")).longValue(),student))throw invalid("consultationId","Hợp đồng đã gắn tài khoản; không chuyển sang học viên khác");
        LocalDate start=LocalDate.parse(targetClass.get("startsOn").toString()),due=date(p,"paymentDueOn",true);
        if(p.get("startsOn")!=null&&!p.get("startsOn").toString().isBlank()&&!start.equals(date(p,"startsOn",true)))
            throw invalid("startsOn","Ngày bắt đầu hợp đồng phải trùng ngày khai giảng cố định của lớp đã chọn.");
        BigDecimal tuition=p.containsKey("tuitionDue")?money(p,"tuitionDue"):sameCourse?new BigDecimal(existing.get("tuitionDue").toString()):registeredCourse!=null?registeredCourse.tuitionFee():money(p,"tuitionDue");
        BigDecimal deposit=money(p,"depositDue");
        String terms=text(p,"terms",4000,false),schedule=existing==null?"":Objects.toString(existing.get("paymentSchedule"),"");
        if(id==null)d.jdbc.update("INSERT INTO training_contracts(id,code,student_id,student_email,applicant_name,applicant_phone,consultation_id,course_id,class_id,registered_course_name,registered_duration_months,tuition_due,deposit_due,status,created_by,manager_id,starts_on,payment_due_on,payment_schedule,terms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?)",key,"HD-"+key.toString().substring(0,8).toUpperCase(Locale.ROOT),student,email,applicant,phone,admission,course,selectedClass,courseName,courseMonths,tuition,deposit,u.id,managerId,start,due,schedule,terms);
        else d.jdbc.update("UPDATE training_contracts SET student_id=?,student_email=?,applicant_name=?,applicant_phone=?,consultation_id=?,course_id=?,class_id=?,registered_course_name=?,registered_duration_months=?,tuition_due=?,deposit_due=?,manager_id=?,starts_on=?,payment_due_on=?,payment_schedule=?,terms=?,version=version+1 WHERE id=?",student,email,applicant,phone,admission,course,selectedClass,courseName,courseMonths,tuition,deposit,managerId,start,due,schedule,terms,key);
        d.jdbc.update("UPDATE consultation_followups SET status=CASE WHEN profile_student_id IS NOT NULL THEN 'PROFILE_CREATED' ELSE 'CONTRACTED' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",admission);
        if(previousAdmission!=null&&!previousAdmission.equals(admission))d.jdbc.update("UPDATE consultation_followups SET status=CASE WHEN profile_student_id IS NOT NULL THEN 'PROFILE_CREATED' WHEN (SELECT intake_source FROM consultation_registrations WHERE id=?)='EXCEL' THEN 'CONTACTED' ELSE 'PROFILE_PENDING' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=? AND status='CONTRACTED' AND NOT EXISTS(SELECT 1 FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED')",previousAdmission,previousAdmission,previousAdmission);
        if(previousAdmission!=null&&!previousAdmission.equals(admission))d.jdbc.update("UPDATE training_contracts SET template_data='{}' WHERE id=?",key);
        contractTemplate.save(key,p,u);
        return saved("TrainingContractDetail",key);
    }
    public Map<String,Object> assignment(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");var enrollment=d.record("assignments",id,u);UUID key=uuid(id);
        d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",uuid(enrollment.get("classId").toString()));d.lock("training_enrollments",key,p);
        if(enrollment.get("closedAt")!=null||!List.of("ACTIVE","PENDING").contains(enrollment.get("status")))throw conflict("Ghi danh đã kết thúc, không thay đổi xếp lớp");
        var cl=d.record("classes",enrollment.get("classId").toString(),u);
        if(cl.get("lecturerId")==null)throw conflict("Cần phân công giảng viên cho lớp trước khi xác nhận");
        if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE class_id=? AND status IN ('ACTIVE','PENDING')",uuid(cl.get("id").toString()))>((Number)cl.get("capacity")).longValue())throw conflict("Lớp vượt sức chứa");
        d.jdbc.update("UPDATE training_enrollments SET assignment_status='CONFIRMED',version=version+1 WHERE id=?",key);
        d.jdbc.update("UPDATE training_classes SET status='ACTIVE',version=version+1 WHERE id=? AND status='PLANNED' AND starts_on<=CURRENT_DATE",uuid(cl.get("id").toString()));return saved("AutoAssignmentDetail",key);
    }
    public Map<String,Object> attendance(String id,Map<String,Object> p,User u,boolean correction){
        role(u,"LECTURER");UUID sessionId=correction?uuid(p,"sessionId"):uuid(id);var session=d.record("sessions",sessionId.toString(),u);d.lock("training_sessions",sessionId,p);
        if(Boolean.TRUE.equals(session.get("attendanceFinalized")))throw conflict("Điểm danh đã chốt. Không được chỉnh sửa ngoài thời hạn/quyền được cấp");
        UUID classId=uuid(session.get("classId").toString());var records=correction?List.of(p):items(p,"records");Set<Long> seen=new HashSet<>();
        for(var r:records){long student=number(r,"studentId",1,Long.MAX_VALUE);if(!seen.add(student))throw invalid("records","Học viên bị trùng");d.requireClassStudent(classId,student);
            String status=choice(r,"status","PRESENT","LATE","EXCUSED","ABSENT"),note=text(r,"note",1000,correction);
            int updated=d.jdbc.update("UPDATE training_attendance SET status=?,note=?,version=version+1 WHERE session_id=? AND student_id=?",status,note,sessionId,student);
            if(updated==0)d.jdbc.update("INSERT INTO training_attendance(id,session_id,student_id,status,note) VALUES (?,?,?,?,?)",UUID.randomUUID(),sessionId,student,status,note);
        }
        boolean finalize=!correction&&flag(p,"finalize");
        if(finalize&&d.count("SELECT COUNT(*) FROM training_enrollments e WHERE e.class_id=? AND e.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM training_attendance a WHERE a.session_id=? AND a.student_id=e.student_id)",classId,sessionId)>0)throw invalid("records","Điểm danh đủ học viên trước khi chốt");
        d.jdbc.update("UPDATE training_sessions SET attendance_finalized=?,version=version+1 WHERE id=?",finalize,sessionId);
        return saved("ClassAttendance",sessionId);
    }
    public Map<String,Object> grades(String id,Map<String,Object> p,User u,boolean publish){
        role(u,"LECTURER");UUID clId=uuid(id);var cl=d.record("classes",id,u);d.lock("training_classes",clId,p);
        if(List.of("COMPLETED","CANCELLED").contains(cl.get("status")))throw conflict("Lớp đã kết thúc, không sửa kết quả học tập");
        var assessments=d.rows("SELECT title FROM course_assessments WHERE course_id=?",cl.get("courseId")==null?null:uuid(cl.get("courseId").toString()));
        if(assessments.isEmpty())throw conflict("Khóa học chưa cấu hình đầu điểm và trọng số");
        Set<String> titles=new HashSet<>();for(var a:assessments)titles.add(a.get("title").toString());Set<String> unique=new HashSet<>();
        for(var r:items(p,"records")){UUID enrollment=uuid(r,"enrollmentId");String title=text(r,"title",160,true);
            if(!titles.contains(title)||!unique.add(enrollment+":"+title))throw invalid("records","Đầu điểm không thuộc cấu hình hoặc bị trùng");
            if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE id=? AND class_id=? AND status='ACTIVE' AND closed_at IS NULL",enrollment,clId)==0)throw invalid("records","Học viên không thuộc lớp đang học hoặc hồ sơ đã đóng");
            BigDecimal score=r.get("score")==null||"".equals(r.get("score"))?null:money(r,"score");if(score!=null&&score.compareTo(BigDecimal.TEN)>0)throw invalid("records","Điểm phải từ 0 đến 10");
            int changed=d.jdbc.update("UPDATE training_grades SET score=?,published_at=NULL WHERE enrollment_id=? AND title=?",score,enrollment,title);
            if(changed==0)d.jdbc.update("INSERT INTO training_grades(id,enrollment_id,title,score) VALUES (?,?,?,?)",UUID.randomUUID(),enrollment,title,score);
        }
        if(publish){if(d.count("SELECT COUNT(*) FROM training_enrollments e CROSS JOIN course_assessments a WHERE e.class_id=? AND e.status='ACTIVE' AND a.course_id=? AND NOT EXISTS(SELECT 1 FROM training_grades g WHERE g.enrollment_id=e.id AND g.title=a.title AND g.score IS NOT NULL)",clId,uuid(cl.get("courseId").toString()))>0)throw conflict("Chưa đủ điểm theo cấu hình để công bố");
            d.jdbc.update("UPDATE training_grades SET published_at=CURRENT_TIMESTAMP WHERE enrollment_id IN(SELECT id FROM training_enrollments WHERE class_id=? AND status='ACTIVE' AND closed_at IS NULL)",clId);
        }
        d.jdbc.update("UPDATE training_classes SET version=version+1 WHERE id=?",clId);return saved("ClassGradebook",clId);
    }
    public void paymentReadyForAssignment(UUID contractId){
        var ct=d.one("SELECT * FROM training_contracts WHERE id=?",contractId);
        if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",contractId)>0)return;
        if(ct.get("managerId")==null||(ct.get("courseId")==null&&EnrollmentCourses.find(ct.get("registeredCourseName"))==null))return;
        if(d.count("SELECT COUNT(*) FROM training_contracts c WHERE c.id=? AND "+PAID_UNASSIGNED_CONTRACT,contractId)==0)return;
        if(ct.get("classId")!=null)notify(((Number)ct.get("managerId")).longValue(),"Học viên đã đủ học phí, chờ vào lớp đã đăng ký","Học phí đã được xác nhận đủ cho hợp đồng "+ct.get("code")+". Mở lớp đã chọn trên hợp đồng để hoàn tất xếp học viên.","ClassDetail",ct.get("classId").toString(),null);
        else notify(((Number)ct.get("managerId")).longValue(),"Học viên đã đủ học phí, chờ xếp lớp","Học phí đã được kế toán xác nhận đủ cho hợp đồng "+ct.get("code")+". Mở danh sách lớp, chọn lớp thuộc khóa học và thêm học viên.","ListClass",null,null);
    }
    public Map<String,Object> closureConditions(Map<String,Object> enrollment){
        UUID e=uuid(enrollment.get("id").toString());long student=((Number)enrollment.get("studentId")).longValue();Object contract=enrollment.get("contractId");
        long grades=d.count("SELECT COUNT(*) FROM training_grades WHERE enrollment_id=? AND score IS NOT NULL AND published_at IS NOT NULL",e);
        long missing=d.count("SELECT COUNT(*) FROM course_assessments a JOIN training_classes cl ON cl.course_id=a.course_id JOIN training_enrollments en ON en.class_id=cl.id WHERE en.id=? AND NOT EXISTS(SELECT 1 FROM training_grades g WHERE g.enrollment_id=en.id AND g.title=a.title AND g.score IS NOT NULL AND g.published_at IS NOT NULL)",e);
        boolean academic=Boolean.TRUE.equals(enrollment.get("recordsComplete"))&&grades>0&&missing==0;
        boolean assets=d.count("SELECT COUNT(*) FROM equipment_loans WHERE student_id=? AND returned_at IS NULL AND (contract_id=? OR (contract_id IS NULL AND class_id=?))",student,contract==null?null:uuid(contract.toString()),uuid(enrollment.get("classId").toString()))==0;
        boolean finance=false;
        if(contract!=null){UUID ct=uuid(contract.toString());var c=d.one("SELECT tuition_due FROM training_contracts WHERE id=?",ct);BigDecimal paid=amount("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE contract_id=? AND kind='TUITION' AND status='CONFIRMED'",ct);
            BigDecimal deposits=amount("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE contract_id=? AND kind='DEPOSIT' AND status='CONFIRMED'",ct);
            long unresolved=d.count("SELECT COUNT(*) FROM equipment_incidents i JOIN equipment_loans l ON l.id=i.loan_id WHERE l.contract_id=? AND i.settled_at IS NULL",ct);
            BigDecimal requiredDeposit=amount("SELECT deposit_due FROM training_contracts WHERE id=?",ct);
            finance=paid.compareTo(new BigDecimal(c.get("tuitionDue").toString()))>=0&&deposits.compareTo(requiredDeposit)>=0&&unresolved==0&&(deposits.signum()==0||d.count("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",ct)>0);
        }
        boolean withdrawal="WITHDRAWN".equals(enrollment.get("status"));return Map.of("academic",academic,"assets",assets,"finance",finance,"withdrawal",withdrawal,"ready",assets&&finance&&(withdrawal||academic));
    }
    BigDecimal amount(String sql,Object...args){return d.jdbc.queryForObject(sql,BigDecimal.class,args);}
    public Map<String,Object> close(String id,Map<String,Object> p,User u,String action){
        role(u,"MANAGER");var e=d.record("closures",id,u);UUID key=uuid(id);
        if(e.get("contractId")!=null)d.one("SELECT id FROM training_contracts WHERE id=? FOR UPDATE",uuid(e.get("contractId").toString()));
        d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",uuid(e.get("classId").toString()));d.lock("training_enrollments",key,p);
        if(e.get("closedAt")!=null)throw conflict("Hồ sơ đã đóng");
        if(action.equals("withdraw")){
            LocalDate date=date(p,"withdrawnOn",true);d.jdbc.update("UPDATE training_enrollments SET status='WITHDRAWN',withdrawn_on=?,withdrawal_reason=?,withdrawal_evidence=?,version=version+1 WHERE id=?",date,text(p,"withdrawalReason",1000,true),text(p,"withdrawalEvidence",1000,true),key);
            d.jdbc.update("UPDATE equipment_loans SET return_status='RETURN_DUE',version=version+1 WHERE student_id=? AND class_id=? AND returned_at IS NULL",e.get("studentId"),uuid(e.get("classId").toString()));
        }else if(action.equals("academic")){
            // Administrative confirmation supplements, and never replaces, published lecturer results.
            var candidate=new LinkedHashMap<>(e);candidate.put("recordsComplete",true);
            if(!Boolean.TRUE.equals(closureConditions(candidate).get("academic")))throw conflict("Chưa đủ kết quả học tập đã công bố theo cấu hình khóa học");
            text(p,"changeNote",1000,true);d.jdbc.update("UPDATE training_enrollments SET records_complete=TRUE,version=version+1 WHERE id=?",key);
        }else{
            if(!Boolean.TRUE.equals(closureConditions(e).get("ready")))throw conflict("Chưa đủ xác nhận học tập, thiết bị và tài chính để đóng hồ sơ");
            boolean withdrawn="WITHDRAWN".equals(e.get("status"));d.jdbc.update("UPDATE training_enrollments SET status=?,closure_outcome=?,closed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",withdrawn?"WITHDRAWN":"COMPLETED",withdrawn?"WITHDRAWN":"COMPLETED",key);
            if(e.get("contractId")!=null)d.jdbc.update("UPDATE training_contracts SET status=?,version=version+1 WHERE id=?",withdrawn?"CANCELLED":"COMPLETED",uuid(e.get("contractId").toString()));
            d.jdbc.update("UPDATE training_classes SET status='COMPLETED',version=version+1 WHERE id=? AND NOT EXISTS(SELECT 1 FROM training_enrollments en WHERE en.class_id=training_classes.id AND en.closed_at IS NULL)",uuid(e.get("classId").toString()));
        }return saved("TrainingClosureDetail",key);
    }
    public void notify(long recipient,String title,String content,String target,String id,User sender){d.jdbc.update("INSERT INTO user_notifications(id,recipient_id,sender_id,title,content,target_screen,target_id) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),recipient,sender==null?null:sender.id,title,content,target,id);}
    public Map<String,Object> sendNotification(Map<String,Object> p,User u){
        role(u,"MANAGER");String title=text(p,"title",160,true),content=text(p,"content",4000,true);OffsetDateTime when=instant(p,"scheduledAt",false);
        var recipients=items(p,"recipients");if(recipients.isEmpty())throw invalid("recipients","Chọn ít nhất một người nhận");Set<Long> unique=new HashSet<>();
        for(var r:recipients){long id=number(r,"id",1,Long.MAX_VALUE);if(!unique.add(id))throw invalid("recipients","Người nhận bị trùng");d.record("students",Long.toString(id),u);UUID key=UUID.randomUUID();d.jdbc.update("INSERT INTO user_notifications(id,recipient_id,sender_id,title,content,scheduled_at) VALUES (?,?,?,?,?,?)",key,id,u.id,title,content,when==null?OffsetDateTime.now():when);}
        return saved("ListNotification",u.id);
    }
}
