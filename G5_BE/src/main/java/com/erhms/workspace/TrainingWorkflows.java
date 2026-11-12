package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class TrainingWorkflows {
    final WorkflowData d;final MasterDataService masters;
    public TrainingWorkflows(WorkflowData d,MasterDataService masters){this.d=d;this.masters=masters;}
    static Map<String,Object> saved(String screen,Object id){return Map.of("screen",screen,"id",String.valueOf(id),"notice","Đã lưu thông tin thành công.");}

    public Map<String,Object> course(String id,Map<String,Object> p,User u){
        role(u,"ADMIN");UUID key=id==null?UUID.randomUUID():uuid(id);
        if(id!=null){d.record("courses",id,u);d.lock("training_courses",key,p);}
        long manager=number(p,"managerId",1,Long.MAX_VALUE);
        if(d.count("SELECT COUNT(*) FROM m_user u JOIN m_role r ON r.role_id=u.role_id WHERE u.user_id=? AND r.role_id=6 AND r.code='MANAGER' AND u.account_enabled=TRUE AND u.account_locked=FALSE",manager)==0)throw invalid("managerId","Chọn quản lý học viên đang hoạt động để phụ trách khóa học");
        if(id!=null&&((Number)d.record("courses",id,u).get("managerId")).longValue()!=manager
                &&(d.count("SELECT COUNT(*) FROM training_contracts WHERE course_id=?",key)>0||d.count("SELECT COUNT(*) FROM training_classes WHERE course_id=?",key)>0))
            throw invalid("managerId","Khóa học đã có hợp đồng hoặc lớp; không thể đổi quản lý phụ trách");
        String name=text(p,"name",160,true),code=text(p,"code",40,true),profession=masters.requireActive("professions",p,"professionId").get("name").toString(),objectives=text(p,"objectives",4000,true);
        long months=number(p,"durationMonths",1,24),sessions=number(p,"totalSessions",1,1000),capacity=number(p,"classCapacity",1,500);
        String status=choice(p,"status","DRAFT","OPEN","ACTIVE","CLOSED");BigDecimal fee=money(p,"tuitionFee");
        var assessments=items(p,"assessments");BigDecimal weights=BigDecimal.ZERO;Set<String> titles=new HashSet<>();
        for(var a:assessments){if(!titles.add(text(a,"title",160,true)))throw invalid("assessments","Tên đầu điểm bị trùng");BigDecimal weight=money(a,"weight");if(weight.signum()<=0||weight.compareTo(BigDecimal.valueOf(100))>0)throw invalid("assessments","Trọng số phải lớn hơn 0 và không vượt quá 100");weights=weights.add(weight);}
        if(!assessments.isEmpty()&&weights.compareTo(BigDecimal.valueOf(100))!=0)throw invalid("assessments","Tổng trọng số phải bằng 100%");
        var lecturers=items(p,"lecturers");Set<Long> teacherIds=new HashSet<>();
        for(var l:lecturers){long teacher=number(l,"id",1,Long.MAX_VALUE);d.requireRoleId(teacher,2);if(!teacherIds.add(teacher))throw invalid("lecturers","Giảng viên bị trùng");}
        if(status.equals("OPEN")&&(assessments.isEmpty()||lecturers.isEmpty()))throw invalid("status","Cấu hình đầu điểm và giảng viên trước khi mở tuyển sinh");
        if(d.count("SELECT COUNT(*) FROM training_courses WHERE code=? AND id<>?",code,key)>0)throw invalid("code","Mã khóa học đã tồn tại");
        if(id==null)d.jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,entry_requirements,status,manager_id,class_capacity,profession_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",key,code,name,profession,months,sessions,fee,objectives,text(p,"entryRequirements",2000,false),status,manager,capacity,uuid(p,"professionId"));
        else {
            var existing=d.rows("SELECT title,weight FROM course_assessments WHERE course_id=? ORDER BY title",key);
            boolean graded=d.count("SELECT COUNT(*) FROM training_grades g JOIN training_enrollments e ON e.id=g.enrollment_id JOIN training_classes c ON c.id=e.class_id WHERE c.course_id=?",key)>0;
            if(graded){if(existing.size()!=assessments.size())throw invalid("assessments","Đã có điểm; không thay đổi cấu trúc đánh giá");for(var a:assessments)if(existing.stream().noneMatch(x->x.get("title").equals(a.get("title"))&&new BigDecimal(x.get("weight").toString()).compareTo(money(a,"weight"))==0))throw invalid("assessments","Đã có điểm; không thay đổi cấu trúc đánh giá");}
            d.jdbc.update("UPDATE training_courses SET code=?,name=?,profession=?,duration_months=?,total_sessions=?,tuition_fee=?,objectives=?,entry_requirements=?,status=?,class_capacity=?,profession_id=?,manager_id=?,version=version+1 WHERE id=?",code,name,profession,months,sessions,fee,objectives,text(p,"entryRequirements",2000,false),status,capacity,uuid(p,"professionId"),manager,key);
        }
        d.jdbc.update("DELETE FROM course_assessments WHERE course_id=?",key);for(var a:assessments)d.jdbc.update("INSERT INTO course_assessments(id,course_id,title,weight) VALUES (?,?,?,?)",UUID.randomUUID(),key,text(a,"title",160,true),money(a,"weight"));
        d.jdbc.update("DELETE FROM course_lecturers WHERE course_id=?",key);for(Long teacher:teacherIds)d.jdbc.update("INSERT INTO course_lecturers(course_id,lecturer_id) VALUES (?,?)",key,teacher);
        return saved("CourseDetail",key);
    }
    public Map<String,Object> consultation(String id,Map<String,Object> p,User u){
        role(u,"HR");UUID key=uuid(id);d.record("admissions",id,u);
        // Lock the parent even when no follow-up exists yet; two HR staff cannot claim the same registration.
        d.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",key);d.record("admissions",id,u);
        var rows=d.rows("SELECT * FROM consultation_followups WHERE consultation_id=?",key);
        String channel=choice(p,"channel","PHONE","EMAIL","IN_PERSON"),status=choice(p,"status","CONTACTED","APPOINTMENT","CONTRACTED","CLOSED");
        boolean contracted=d.count("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED'",key)>0;
        if(contracted&&!status.equals("CONTRACTED"))throw invalid("status","Hồ sơ đã có hợp đồng, không thay đổi trạng thái Đã lập hợp đồng");
        if(!contracted&&status.equals("CONTRACTED"))throw invalid("status","Trạng thái Đã lập hợp đồng được cập nhật khi Manager tạo hợp đồng");
        String notes=text(p,"notes",2000,true);OffsetDateTime appointment=instant(p,"appointmentAt",false);
        if(p.containsKey("email")){String email=text(p,"email",190,false);if(!email.isEmpty()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid("email","Email không hợp lệ");d.jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",email.isEmpty()?null:email.toLowerCase(Locale.ROOT),key);}
        if(status.equals("APPOINTMENT")&&appointment==null)throw invalid("appointmentAt","Nhập lịch hẹn tiếp theo");
        if(rows.isEmpty())d.jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,appointment_at,channel,notes) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),key,u.id,status,appointment,channel,notes);
        else{long version=number(p,"version",0,Long.MAX_VALUE);if(version!=((Number)rows.get(0).get("version")).longValue())throw conflict("Kết quả tư vấn đã được cập nhật. Hãy tải lại.");d.jdbc.update("UPDATE consultation_followups SET assigned_to=?,status=?,appointment_at=?,channel=?,notes=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE consultation_id=?",u.id,status,appointment,channel,notes,key);}
        d.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content,appointment_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),key,u.id,channel,status,notes,appointment);
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
        if(id!=null){d.record("contracts",id,u);d.lock("training_contracts",key,p);existing=d.record("contracts",id,u);if(!"DRAFT".equals(existing.get("status")))throw conflict("Chỉ được sửa hợp đồng bản nháp");}
        UUID admission=uuid(p,"consultationId");
        // Consultation updates take the same parent lock; recheck the latest status before creating a contract.
        UUID previousAdmission=existing==null||existing.get("consultationId")==null?null:uuid(existing.get("consultationId").toString());
        var admissionLocks=new TreeSet<UUID>();admissionLocks.add(admission);if(previousAdmission!=null)admissionLocks.add(previousAdmission);
        for(UUID locked:admissionLocks)d.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",locked);
        var application=d.one("SELECT r.*,COALESCE(f.status,'NEW') AS status FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE r.id=?",admission);
        boolean keepExisting=existing!=null&&admission.toString().equals(Objects.toString(existing.get("consultationId"),""))&&"CONTRACTED".equals(application.get("status"));
        if(!"CONTACTED".equals(application.get("status"))&&!keepExisting)throw invalid("consultationId","Chỉ hồ sơ ở trạng thái Đã liên hệ mới được tạo hợp đồng");
        UUID course=uuid(p,"courseId");d.one("SELECT id FROM training_courses WHERE id=? FOR UPDATE",course);
        var co=d.record("courses",course.toString(),u);
        if(!List.of("OPEN","ACTIVE").contains(co.get("status")))throw invalid("courseId","Chọn khóa học đang mở tuyển sinh hoặc đang hoạt động");
        if(d.count("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND course_id=? AND id<>? AND status<>'CANCELLED'",admission,course,key)>0)throw invalid("consultationId","Hồ sơ này đã có hợp đồng cho khóa học được chọn");
        long student=number(p,"studentId",1,Long.MAX_VALUE);d.requireRoleId(student,3);
        if(d.count("SELECT COUNT(*) FROM m_user WHERE user_id=? AND LOWER(TRIM(email))=LOWER(TRIM(?))",student,application.get("email"))==0)throw invalid("studentId","Tài khoản học viên phải khớp email của hồ sơ đăng ký đã tư vấn");
        LocalDate start=date(p,"startsOn",true),due=date(p,"paymentDueOn",true);BigDecimal tuition=money(p,"tuitionDue"),deposit=money(p,"depositDue");
        String terms=text(p,"terms",4000,true),schedule=text(p,"paymentSchedule",2000,false);
        if(id==null)d.jdbc.update("INSERT INTO training_contracts(id,code,student_id,consultation_id,course_id,tuition_due,deposit_due,status,created_by,manager_id,starts_on,payment_due_on,payment_schedule,terms) VALUES (?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?)",key,"HD-"+key.toString().substring(0,8).toUpperCase(Locale.ROOT),student,admission,course,tuition,deposit,u.id,co.get("managerId"),start,due,schedule,terms);
        else d.jdbc.update("UPDATE training_contracts SET student_id=?,consultation_id=?,course_id=?,tuition_due=?,deposit_due=?,manager_id=?,starts_on=?,payment_due_on=?,payment_schedule=?,terms=?,version=version+1 WHERE id=?",student,admission,course,tuition,deposit,co.get("managerId"),start,due,schedule,terms,key);
        d.jdbc.update("UPDATE consultation_followups SET status='CONTRACTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",admission);
        if(previousAdmission!=null&&!previousAdmission.equals(admission))d.jdbc.update("UPDATE consultation_followups SET status='CONTACTED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=? AND status='CONTRACTED' AND NOT EXISTS(SELECT 1 FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED')",previousAdmission,previousAdmission);
        return saved("TrainingContractDetail",key);
    }
    public Map<String,Object> signature(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");d.record("contracts",id,u);UUID key=uuid(id);d.lock("training_contracts",key,p);var c=d.record("contracts",id,u);
        if(!"DRAFT".equals(c.get("status")))throw conflict("Hợp đồng đã được ký hoặc không còn ở trạng thái bản nháp");
        long accountant=number(p,"accountantId",1,Long.MAX_VALUE);d.requireRoleId(accountant,4);
        LocalDate signed=date(p,"signedOn",true);if(signed.isAfter(LocalDate.now()))throw invalid("signedOn","Ngày ký không được ở tương lai");
        d.jdbc.update("UPDATE training_contracts SET status='SIGNED',signed_on=?,signature_method=?,signature_evidence=?,accountant_id=?,handover_note=?,handed_over_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",signed,choice(p,"signatureMethod","PAPER","ELECTRONIC"),text(p,"signatureEvidence",1000,true),accountant,text(p,"handoverNote",1000,false),key);
        return saved("TrainingContractDetail",key);
    }
    public Map<String,Object> classroom(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");UUID key=uuid(id);var currentClass=d.record("classes",id,u);d.lock("training_classes",key,p);
        long teacher=number(p,"lecturerId",1,Long.MAX_VALUE),capacity=number(p,"capacity",1,500);d.requireRoleId(teacher,2);
        if(currentClass.get("courseId")!=null&&d.count("SELECT COUNT(*) FROM course_lecturers WHERE course_id=? AND lecturer_id=?",uuid(currentClass.get("courseId").toString()),teacher)==0)throw invalid("lecturerId","Giảng viên chưa được cấu hình phụ trách khóa học này");
        if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE class_id=? AND status IN ('ACTIVE','PENDING')",key)>capacity)throw invalid("capacity","Sĩ số tối đa nhỏ hơn số học viên hiện có");
        LocalDate start=date(p,"startsOn",true),end=date(p,"endsOn",false);if(end!=null&&end.isBefore(start))throw invalid("endsOn","Ngày kết thúc phải sau ngày bắt đầu");
        if(d.count("SELECT COUNT(*) FROM training_sessions a JOIN training_sessions b ON a.id<>b.id JOIN training_classes bc ON bc.id=b.class_id WHERE a.class_id=? AND b.class_id<>? AND bc.lecturer_id=? AND a.starts_at<b.ends_at AND a.ends_at>b.starts_at",key,key,teacher)>0)throw conflict("Giảng viên bị trùng lịch với lớp khác");
        String reason=text(p,"changeNote",1000,true);
        String state=p.containsKey("status")?choice(p,"status","PLANNED","ACTIVE"):currentClass.get("status").toString();
        if(List.of("COMPLETED","CANCELLED").contains(currentClass.get("status")))throw conflict("Không điều chỉnh lớp đã kết thúc hoặc bị hủy");
        d.jdbc.update("UPDATE training_classes SET lecturer_id=?,capacity=?,starts_on=?,ends_on=?,status=?,version=version+1 WHERE id=?",teacher,capacity,start,end,state,key);
        return saved("ClassDetail",key);
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
    public Map<String,Object> session(String id,Map<String,Object> p,User u){
        role(u,"MANAGER");UUID key=id==null?UUID.randomUUID():uuid(id),clId=uuid(p,"classId");var cl=d.record("classes",clId.toString(),u);
        if(id!=null){var old=d.record("sessions",id,u);d.lock("training_sessions",key,p);if(Boolean.TRUE.equals(old.get("attendanceFinalized")))throw conflict("Buổi học đã chốt điểm danh, không thay đổi lịch");}
        OffsetDateTime start=instant(p,"startsAt",true),end=instant(p,"endsAt",true);
        if(!end.isAfter(start))throw invalid("endsAt","Giờ kết thúc phải sau giờ bắt đầu");
        String room=masters.requireActive("classrooms",p,"classroomId").get("name").toString(),title=text(p,"title",160,true),reason=text(p,"changeNote",1000,true);
        if(cl.get("lecturerId")==null)throw invalid("classId","Cần phân công giảng viên cho lớp");
        // Serialize schedule changes across rooms and lecturers using the singleton revision row.
        d.one("SELECT version FROM permission_revision WHERE id=1 FOR UPDATE");
        if(d.count("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE s.id<>? AND s.starts_at<? AND s.ends_at>? AND (s.class_id=? OR s.classroom_id=? OR (s.classroom_id IS NULL AND LOWER(s.room)=LOWER(?)) OR c.lecturer_id=?)",key,end,start,clId,uuid(p,"classroomId"),room,cl.get("lecturerId"))>0)
            throw new WorkflowException(org.springframework.http.HttpStatus.CONFLICT,"TIMETABLE_CONFLICT","Trùng lịch lớp, phòng hoặc giảng viên. Chọn phương án khác trước khi lưu.",Map.of("startsAt","Lịch đang bị trùng"));
        if(id==null)d.jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room,change_note,classroom_id) VALUES (?,?,?,?,?,?,?,?)",key,clId,title,start,end,room,reason,uuid(p,"classroomId"));
        else d.jdbc.update("UPDATE training_sessions SET class_id=?,title=?,starts_at=?,ends_at=?,room=?,change_note=?,classroom_id=?,version=version+1 WHERE id=?",clId,title,start,end,room,reason,uuid(p,"classroomId"),key);
        return saved("TimeTable_Manager",key);
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
    public void autoAssign(UUID contractId){
        var ct=d.one("SELECT * FROM training_contracts WHERE id=?",contractId);
        if(d.count("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",contractId)>0)return;
        if(ct.get("courseId")==null||ct.get("startsOn")==null)return; // Existing legacy contracts need a programme assigned first.
        UUID courseId=uuid(ct.get("courseId").toString());var course=d.one("SELECT * FROM training_courses WHERE id=? FOR UPDATE",courseId);
        LocalDate start=LocalDate.parse(ct.get("startsOn").toString()),end=start.plusMonths(((Number)course.get("durationMonths")).longValue());
        var classes=d.rows("SELECT cl.* FROM training_classes cl WHERE cl.course_id=? AND cl.starts_on=? AND cl.status IN ('PLANNED','ACTIVE') AND (SELECT COUNT(*) FROM training_enrollments e WHERE e.class_id=cl.id AND e.status IN ('PENDING','ACTIVE'))<cl.capacity ORDER BY cl.code",courseId,start);
        UUID classId;
        if(!classes.isEmpty())classId=uuid(classes.get(0).get("id").toString());
        else{
            classId=UUID.randomUUID();var teachers=d.rows("SELECT l.lecturer_id FROM course_lecturers l JOIN m_user u ON u.user_id=l.lecturer_id WHERE l.course_id=? AND u.account_enabled=TRUE AND u.account_locked=FALSE AND NOT EXISTS(SELECT 1 FROM training_classes c WHERE c.lecturer_id=l.lecturer_id AND c.status IN ('PLANNED','ACTIVE') AND c.starts_on<=? AND (c.ends_on IS NULL OR c.ends_on>=?)) ORDER BY l.lecturer_id",courseId,end,start);
            Object teacher=teachers.isEmpty()?null:teachers.get(0).get("lecturerId");
            d.jdbc.update("INSERT INTO training_classes(id,code,name,lecturer_id,starts_on,ends_on,status,total_sessions,course_id,manager_id,capacity) VALUES (?,?,?,?,?,?,'PLANNED',?,?,?,?)",classId,"L-"+classId.toString().substring(0,8).toUpperCase(Locale.ROOT),course.get("name"),teacher,start,end,course.get("totalSessions"),courseId,course.get("managerId"),course.get("classCapacity"));
        }
        d.jdbc.update("INSERT INTO training_enrollments(id,class_id,student_id,status,assignment_status,contract_id) VALUES (?,?,?,'ACTIVE','PENDING',?)",UUID.randomUUID(),classId,ct.get("studentId"),contractId);
        notify(((Number)course.get("managerId")).longValue(),"Kiểm tra xếp lớp tự động","Học phí đã được xác nhận cho hợp đồng "+ct.get("code")+". Vui lòng kiểm tra lớp và phân công.","AutoAssignmentReview",null,null);
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
