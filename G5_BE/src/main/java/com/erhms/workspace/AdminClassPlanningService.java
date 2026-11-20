package com.erhms.workspace;

import com.erhms.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static com.erhms.workspace.WorkflowData.*;

/** Admin owns the class calendar. Contract and enrollment workflows consume that fixed plan. */
@Service
public class AdminClassPlanningService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final WorkflowData d;
    private final MasterDataService masters;
    public AdminClassPlanningService(WorkflowData d,MasterDataService masters){this.d=d;this.masters=masters;}

    @Transactional
    public Map<String,Object> create(Map<String,Object> p,User actor){
        role(actor,"ADMIN");
        // One shared lock serializes all calendar writers before checking room and teacher overlaps.
        d.one("SELECT version FROM permission_revision WHERE id=1 FOR UPDATE");
        UUID courseId=uuid(p,"courseId");
        d.one("SELECT id FROM training_courses WHERE id=? FOR UPDATE",courseId);
        var course=d.record("courses",courseId.toString(),actor);
        if(!List.of("OPEN","ACTIVE").contains(course.get("status")))throw invalid("courseId","Chọn khóa học đang mở tuyển sinh hoặc đang hoạt động");
        String name=text(p,"name",160,true);
        long capacity=number(p,"capacity",1,500),teacher=number(p,"lecturerId",1,Long.MAX_VALUE);
        requireLecturer(courseId,teacher);
        int year=(int)number(p,"planYear",1900,9999);
        LocalDate first=date(p,"startsOn",true),last=date(p,"endsOn",true);
        if(first.getYear()!=year)throw invalid("planYear","Năm kế hoạch phải là năm bắt đầu lớp");
        if(last.isBefore(first)||ChronoUnit.DAYS.between(first,last)>366)throw invalid("endsOn","Ngày kết thúc phải từ ngày bắt đầu và trong phạm vi một năm học");
        var weekdays=weekdays(p);
        LocalTime start=time(p,"startTime"),end=time(p,"endTime");
        if(!end.isAfter(start))throw invalid("endTime","Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày");
        UUID roomId=uuid(p,"classroomId");String room=masters.requireActive("classrooms",p,"classroomId").get("name").toString();
        List<LocalDate> dates=new ArrayList<>();
        for(LocalDate day=first;!day.isAfter(last);day=day.plusDays(1))if(weekdays.contains(day.getDayOfWeek().getValue()))dates.add(day);
        if(dates.isEmpty())throw invalid("weekdays","Khoảng ngày đã chọn không có buổi học theo các thứ này");
        UUID key=UUID.randomUUID();
        for(var day:dates)checkConflict(key,null,teacher,roomId,room,day.atTime(start).atZone(ZONE).toOffsetDateTime(),day.atTime(end).atZone(ZONE).toOffsetDateTime());
        d.jdbc.update("INSERT INTO training_classes(id,code,name,lecturer_id,starts_on,ends_on,status,total_sessions,course_id,manager_id,capacity,plan_year,weekdays,start_time,end_time,classroom_id,plan_fixed) VALUES (?,?,?,?,?,?,'PLANNED',?,?,?,?,?,?,?,?,?,TRUE)",
                key,nextCode(),name,teacher,first,last,dates.size(),courseId,course.get("managerId"),capacity,year,csv(weekdays),start,end,roomId);
        int index=0;
        for(var day:dates){String title="Buổi "+(++index)+" - "+name;if(title.length()>160)title=title.substring(0,160);d.jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room,classroom_id,change_note) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),key,title,day.atTime(start).atZone(ZONE).toOffsetDateTime(),day.atTime(end).atZone(ZONE).toOffsetDateTime(),room,roomId,"Lịch cố định theo kế hoạch năm "+year);}
        return TrainingWorkflows.saved("ClassDetail",key);
    }

    @Transactional
    public Map<String,Object> update(String id,Map<String,Object> p,User actor){
        role(actor,"ADMIN");UUID key=uuid(id);d.record("classes",id,actor);
        d.one("SELECT version FROM permission_revision WHERE id=1 FOR UPDATE");
        d.lock("training_classes",key,p);var cl=d.record("classes",id,actor);
        if(!List.of("PLANNED","ACTIVE").contains(cl.get("status")))throw conflict("Không điều chỉnh lớp đã kết thúc hoặc bị hủy");
        requireUnchangedPlan(p,cl);
        String state=p.containsKey("status")?choice(p,"status","PLANNED","ACTIVE","CANCELLED"):cl.get("status").toString();
        long capacity=number(p,"capacity",1,500),occupied=ClassReservations.occupied(d,key,null);
        if(state.equals("CANCELLED")&&occupied>0)throw conflict("Lớp đã có hợp đồng giữ chỗ hoặc học viên, không thể hủy kế hoạch");
        if(occupied>capacity)throw invalid("capacity","Sĩ số tối đa nhỏ hơn số học viên và hợp đồng đã giữ chỗ");
        Long teacher=cl.get("lecturerId")==null?null:((Number)cl.get("lecturerId")).longValue();
        if(!state.equals("CANCELLED")){
            teacher=number(p,"lecturerId",1,Long.MAX_VALUE);
            if(cl.get("courseId")==null)d.requireRoleId(teacher,2);else requireLecturer(uuid(cl.get("courseId").toString()),teacher);
        }
        if(!state.equals("CANCELLED")&&d.count("SELECT COUNT(*) FROM training_sessions a JOIN training_sessions b ON a.id<>b.id JOIN training_classes bc ON bc.id=b.class_id WHERE a.class_id=? AND b.class_id<>? AND bc.status<>'CANCELLED' AND bc.lecturer_id=? AND a.starts_at<b.ends_at AND a.ends_at>b.starts_at",key,key,teacher)>0)
            throw conflict("Giảng viên bị trùng lịch với lớp khác");
        text(p,"changeNote",1000,true);
        d.jdbc.update("UPDATE training_classes SET lecturer_id=?,capacity=?,status=?,version=version+1 WHERE id=?",teacher,capacity,state,key);
        return TrainingWorkflows.saved("ClassDetail",key);
    }

    /** Legacy classes can still be maintained by Admin; generated annual calendars stay fixed. */
    @Transactional
    public Map<String,Object> session(String id,Map<String,Object> p,User actor){
        role(actor,"ADMIN");UUID key=id==null?UUID.randomUUID():uuid(id),classId=uuid(p,"classId");
        d.record("classes",classId.toString(),actor);
        d.one("SELECT version FROM permission_revision WHERE id=1 FOR UPDATE");
        d.one("SELECT id FROM training_classes WHERE id=? FOR UPDATE",classId);
        var cl=d.record("classes",classId.toString(),actor);
        if(Boolean.TRUE.equals(cl.get("planFixed")))throw conflict("Lịch học của kế hoạch năm đã cố định, không thay đổi buổi học");
        if(!List.of("PLANNED","ACTIVE").contains(cl.get("status")))throw conflict("Chỉ xếp lịch cho lớp đang chuẩn bị hoặc đang học");
        if(id!=null){d.record("sessions",id,actor);d.lock("training_sessions",key,p);var old=d.record("sessions",id,actor);
            if(Boolean.TRUE.equals(old.get("attendanceFinalized")))throw conflict("Buổi học đã chốt điểm danh, không thay đổi lịch");
            if(!classId.toString().equals(old.get("classId")))throw invalid("classId","Không chuyển buổi học sang lớp khác");}
        OffsetDateTime start=instant(p,"startsAt",true),end=instant(p,"endsAt",true);
        if(!end.isAfter(start))throw invalid("endsAt","Giờ kết thúc phải sau giờ bắt đầu");
        if(cl.get("startsOn")!=null&&start.isBefore(LocalDate.parse(cl.get("startsOn").toString()).atStartOfDay(ZONE).toOffsetDateTime()))throw invalid("startsAt","Buổi học không được trước ngày bắt đầu lớp");
        if(cl.get("endsOn")!=null&&end.isAfter(LocalDate.parse(cl.get("endsOn").toString()).plusDays(1).atStartOfDay(ZONE).toOffsetDateTime()))throw invalid("endsAt","Buổi học không được sau ngày kết thúc lớp");
        UUID roomId=uuid(p,"classroomId");String room=masters.requireActive("classrooms",p,"classroomId").get("name").toString();
        if(cl.get("lecturerId")==null)throw invalid("classId","Cần phân công giảng viên cho lớp");
        checkConflict(classId,key,((Number)cl.get("lecturerId")).longValue(),roomId,room,start,end);
        String title=text(p,"title",160,true),reason=text(p,"changeNote",1000,true);
        if(id==null)d.jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room,change_note,classroom_id) VALUES (?,?,?,?,?,?,?,?)",key,classId,title,start,end,room,reason,roomId);
        else d.jdbc.update("UPDATE training_sessions SET title=?,starts_at=?,ends_at=?,room=?,change_note=?,classroom_id=?,version=version+1 WHERE id=?",title,start,end,room,reason,roomId,key);
        return TrainingWorkflows.saved("TimeTable_Manager",key);
    }

    static String occupiedSlotsSql(String alias){
        return "((SELECT COUNT(*) FROM training_contracts reserved WHERE reserved.class_id="+alias+".id AND reserved.status<>'CANCELLED')+(SELECT COUNT(*) FROM training_enrollments occupied WHERE occupied.class_id="+alias+".id AND occupied.status IN ('ACTIVE','PENDING') AND NOT EXISTS(SELECT 1 FROM training_contracts linked WHERE linked.id=occupied.contract_id AND linked.class_id="+alias+".id AND linked.status<>'CANCELLED')))";
    }
    private void requireLecturer(UUID course,long teacher){
        d.requireRoleId(teacher,2);
        if(d.count("SELECT COUNT(*) FROM course_lecturers WHERE course_id=? AND lecturer_id=?",course,teacher)==0)throw invalid("lecturerId","Giảng viên chưa được cấu hình phụ trách khóa học này");
    }
    private void checkConflict(UUID classId,UUID sessionId,long teacher,UUID roomId,String room,OffsetDateTime start,OffsetDateTime end){
        UUID excluded=sessionId==null?new UUID(0,0):sessionId;
        if(d.count("SELECT COUNT(*) FROM training_sessions s JOIN training_classes c ON c.id=s.class_id WHERE c.status<>'CANCELLED' AND s.id<>? AND s.starts_at<? AND s.ends_at>? AND (s.class_id=? OR s.classroom_id=? OR (s.classroom_id IS NULL AND LOWER(s.room)=LOWER(?)) OR c.lecturer_id=?)",excluded,end,start,classId,roomId,room,teacher)>0)
            throw new WorkflowException(org.springframework.http.HttpStatus.CONFLICT,"TIMETABLE_CONFLICT","Trùng lịch lớp, phòng hoặc giảng viên. Chọn kế hoạch khác trước khi lưu.",Map.of("startTime","Lịch đang bị trùng"));
    }
    private String nextCode(){String code;do{code=String.format(Locale.ROOT,"L-%06d",d.count("SELECT nextval('training_class_code_seq')"));}while(d.count("SELECT COUNT(*) FROM training_classes WHERE code=?",code)>0);return code;}
    private static LocalTime time(Map<String,Object> p,String field){
        String value=text(p,field,8,true);if(!value.matches("\\d{2}:\\d{2}(:00)?"))throw invalid(field,"Nhập giờ học theo định dạng HH:mm");
        try{return LocalTime.parse(value);}catch(Exception e){throw invalid(field,"Giờ học không hợp lệ");}
    }
    private static SortedSet<Integer> weekdays(Map<String,Object> p){
        if(!(p.get("weekdays") instanceof List<?> values)||values.isEmpty()||values.size()>7)throw invalid("weekdays","Chọn từ một đến bảy thứ trong tuần");
        SortedSet<Integer> days=new TreeSet<>();for(Object value:values){if(value==null||!days.add((int)number(Map.of("weekdays",value),"weekdays",1,7)))throw invalid("weekdays","Thứ trong tuần không được trùng");}return days;
    }
    private static String csv(Collection<Integer> days){return days.stream().map(String::valueOf).collect(Collectors.joining(","));}
    private static void requireUnchangedPlan(Map<String,Object> p,Map<String,Object> cl){
        for(String field:List.of("courseId","name","startsOn","endsOn","classroomId"))if(p.containsKey(field)&&!Objects.toString(p.get(field),"").equals(Objects.toString(cl.get(field),"")))throw invalid(field,"Thông tin kế hoạch lớp đã cố định, không thể thay đổi");
        if(p.containsKey("planYear")&&!Objects.toString(p.get("planYear"),"").equals(Objects.toString(cl.get("planYear"),"")))throw invalid("planYear","Năm kế hoạch đã cố định");
        if(p.containsKey("weekdays")){var expected=cl.get("weekdays");if(!(expected instanceof List<?>)||!new TreeSet<>(weekdays(p)).equals(new TreeSet<>((List<Integer>)expected)))throw invalid("weekdays","Các thứ học đã cố định");}
        for(String field:List.of("startTime","endTime"))if(p.containsKey(field)&&(cl.get(field)==null||!time(p,field).equals(LocalTime.parse(cl.get(field).toString()))))throw invalid(field,"Giờ học đã cố định");
    }
}
