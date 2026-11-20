package com.erhms;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed class fixtures for tests whose subject is another contract workflow. */
public final class ClassPlanTestFixtures {
    private ClassPlanTestFixtures() {}

    public static Map<String,Object> withClass(JdbcTemplate jdbc,long manager,Map<String,Object> input){
        var result=new LinkedHashMap<>(input);
        if(result.containsKey("classId"))return result;
        if(!result.containsKey("consultationId"))return result;
        UUID course=input.get("courseId")==null?null:UUID.fromString(input.get("courseId").toString());
        if(course==null){
            var registrations=jdbc.queryForList("SELECT enrollment_course FROM consultation_registrations WHERE id=?",UUID.fromString(input.get("consultationId").toString()));
            Object name=registrations.isEmpty()?null:registrations.get(0).get("enrollment_course");
            if(name==null){result.put("classId",UUID.randomUUID().toString());return result;}
            var courses=jdbc.queryForList("SELECT id FROM training_courses WHERE manager_id=? AND name=? AND status IN ('OPEN','ACTIVE')",manager,name);
            if(!courses.isEmpty())course=(UUID)courses.get(0).get("id");
            else{
                course=UUID.randomUUID();
                jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',4,1,14200000,'Fixture','OPEN',?,500)",course,course.toString(),name,manager);
            }
        }
        var courses=jdbc.queryForList("SELECT manager_id FROM training_courses WHERE id=?",course);
        if(courses.isEmpty()){result.put("classId",UUID.randomUUID().toString());return result;}
        long owner=((Number)courses.get(0).get("manager_id")).longValue();
        LocalDate start=LocalDate.parse(input.getOrDefault("startsOn","2027-01-04").toString());
        var existing=jdbc.queryForList("SELECT id FROM training_classes WHERE course_id=? AND manager_id=? AND starts_on=? AND plan_fixed=TRUE AND status='PLANNED' AND capacity=500",course,owner,start);
        if(!existing.isEmpty()){result.put("classId",existing.get(0).get("id").toString());return result;}
        UUID id=UUID.randomUUID(),room=UUID.randomUUID();long teacher;
        var lecturers=jdbc.queryForList("SELECT u.user_id FROM course_lecturers cl JOIN m_user u ON u.user_id=cl.lecturer_id WHERE cl.course_id=? AND u.account_enabled=TRUE AND u.account_locked=FALSE ORDER BY u.user_id",course);
        if(!lecturers.isEmpty())teacher=((Number)lecturers.get(0).get("user_id")).longValue();
        else{
            String teacherEmail=UUID.randomUUID()+"@class-fixture.test";
            jdbc.update("INSERT INTO m_user(email,password_hash,full_name,role_id) VALUES (?,'fixture','Giảng viên kế hoạch',2)",teacherEmail);
            teacher=jdbc.queryForObject("SELECT user_id FROM m_user WHERE email=?",Long.class,teacherEmail);
            jdbc.update("INSERT INTO course_lecturers(course_id,lecturer_id) VALUES (?,?)",course,teacher);
        }
        jdbc.update("INSERT INTO m_classroom(id,code,name,name_key) VALUES (?,?,?,?)",room,room.toString(),"Phòng "+room,"phòng "+room);
        LocalDate end=start.plusMonths(4),today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if(end.isBefore(today))end=today.plusDays(30);
        jdbc.update("INSERT INTO training_classes(id,code,name,course_id,manager_id,lecturer_id,starts_on,ends_on,status,total_sessions,capacity,plan_year,weekdays,start_time,end_time,classroom_id,plan_fixed) VALUES (?,?,?, ?,?,?,?,?,'PLANNED',1,500,?,?,'08:00:00','10:00:00',?,TRUE)",id,"FX-"+id,"Lớp cố định "+id,course,owner,teacher,start,end,start.getYear(),String.valueOf(start.getDayOfWeek().getValue()),room);
        jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room,classroom_id) VALUES (?,?,'Buổi kế hoạch',?,?,?,?)",UUID.randomUUID(),id,start.atTime(8,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(),start.atTime(10,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime(),"Phòng "+room,room);
        result.put("classId",id.toString());return result;
    }
}
