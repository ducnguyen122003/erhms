package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admin_class_plans;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AdminClassPlanningIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired UserRepository users;@Autowired RoleRepository roles;
    User admin,manager,otherManager,teacher,otherTeacher;UUID course,room,otherRoom;
    @BeforeEach void setup(){
        admin=actor("ADMIN");manager=actor("MANAGER");otherManager=actor("MANAGER");teacher=actor("LECTURER");otherTeacher=actor("LECTURER");
        course=course(manager);room=room();otherRoom=room();
    }
    User actor(String role){var u=new User();u.email=UUID.randomUUID()+"@annual-plan.test";u.fullName="Kiểm thử "+role;u.passwordHash="fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
    UUID course(User owner){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',4,20,1000,'Fixture','OPEN',?,20)",id,id.toString(),"Khóa "+id,owner.id);jdbc.update("INSERT INTO course_lecturers(course_id,lecturer_id) VALUES (?,?),(?,?)",id,teacher.id,id,otherTeacher.id);return id;}
    UUID room(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO m_classroom(id,code,name,name_key) VALUES (?,?,?,?)",id,id.toString(),"Phòng "+id,"phòng "+id);return id;}
    Map<String,Object> values(Object...pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    Map<String,Object> plan(){return values("courseId",course.toString(),"name","Lớp kế hoạch","planYear",2027,"startsOn","2027-01-04","endsOn","2027-01-17","weekdays",List.of(3,1),"startTime","08:00","endTime","10:00","classroomId",room.toString(),"lecturerId",teacher.id,"capacity",20);}
    String path(String screen,String id){return "/api/workspace/screens/"+screen+(id==null?"":"/"+id);}
    ResultActions write(User actor,String screen,String id,Map<String,Object> input)throws Exception{return mvc.perform(post(path(screen,id)).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    ResultActions read(User actor,String screen,String id)throws Exception{return mvc.perform(get(path(screen,id)).with(user(actor.email).roles(actor.getRoleCode())));}
    JsonNode body(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode detail(User actor,String id)throws Exception{return body(read(actor,"ClassDetail",id).andExpect(status().isOk()).andReturn());}
    String create(Map<String,Object> input)throws Exception{return body(write(admin,"AddClass",null,input).andExpect(status().isOk()).andReturn()).path("id").asText();}
    Map<String,Object> edit(String id)throws Exception{return values("version",detail(admin,id).path("record").path("version").asLong(),"lecturerId",teacher.id,"capacity",20,"status","PLANNED","changeNote","Điều chỉnh phân công");}
    int classes(){return jdbc.queryForObject("SELECT COUNT(*) FROM training_classes WHERE course_id=?",Integer.class,course);}

    @Test void adminCreatesAnAnnualPlanAndGeneratesExactlyItsWeeklyLocalDates()throws Exception{
        var p=plan();p.put("managerId",otherManager.id);p.put("code","CLIENT-CODE");String first=create(p);
        var record=detail(admin,first).path("record");assertTrue(record.path("code").asText().matches("L-\\d{6,}"));assertTrue(record.path("planFixed").asBoolean());assertEquals(manager.id,record.path("managerId").asLong());assertEquals(2027,record.path("planYear").asInt());assertEquals(4,record.path("totalSessions").asInt());
        assertEquals(List.of("2027-01-04","2027-01-06","2027-01-11","2027-01-13"),jdbc.query("SELECT starts_at FROM training_sessions WHERE class_id=? ORDER BY starts_at",(rs,n)->rs.getObject(1,OffsetDateTime.class).atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().toString(),UUID.fromString(first)));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE class_id=? AND classroom_id=?",Integer.class,UUID.fromString(first),room));
        assertEquals(teacher.id,detail(manager,first).path("record").path("lecturerId").asLong());read(otherManager,"ClassDetail",first).andExpect(status().isNotFound());
        var next=plan();next.put("planYear",2028);next.put("startsOn","2028-01-03");next.put("endsOn","2028-01-16");String second=create(next);
        assertEquals(Long.parseLong(record.path("code").asText().substring(2))+1,Long.parseLong(detail(admin,second).path("record").path("code").asText().substring(2)));
        var year=body(mvc.perform(get(path("ListClass",null)).param("year","2027").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());assertTrue(year.path("items").toString().contains(first));assertFalse(year.path("items").toString().contains(second));
        var foreign=plan();foreign.put("courseId",course(otherManager).toString());foreign.put("startTime","10:00");foreign.put("endTime","12:00");String other=create(foreign);assertEquals(otherManager.id,detail(admin,other).path("record").path("managerId").asLong());
    }

    @Test void onlyAdminCanWritePlansAndCalendarEvenWithAnInjectedRoleGrant()throws Exception{
        mvc.perform(post(path("AddClass",null)).with(user(admin.email).roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(plan()))).andExpect(status().isForbidden());
        for(String role:List.of("MANAGER","HR","LECTURER","STUDENT","ACCOUNTANT","IT_SUPPORT")){
            User wrong=role.equals("MANAGER")?manager:actor(role);
            jdbc.update("INSERT INTO role_function(role_id,function_code) SELECT ?,'AddClass' WHERE NOT EXISTS(SELECT 1 FROM role_function WHERE role_id=? AND function_code='AddClass')",wrong.getRoleId(),wrong.getRoleId());
            try{read(wrong,"AddClass",null).andExpect(status().isForbidden());write(wrong,"AddClass",null,plan()).andExpect(status().isForbidden());}
            finally{jdbc.update("DELETE FROM role_function WHERE role_id=? AND function_code='AddClass'",wrong.getRoleId());}
        }
        String id=create(plan());write(manager,"EditClass",id,edit(id)).andExpect(status().isForbidden());write(manager,"TimeTable_EditSession",null,values("classId",id)).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_function WHERE role_id=1 AND function_code='AddClass'");
        try{write(admin,"AddClass",null,plan()).andExpect(status().isForbidden());}finally{jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (1,'AddClass')");}
    }

    @Test void invalidYearsWeekdaysHoursLecturersAndCoursesNeverCreatePartialPlans()throws Exception{
        for(Map<String,Object> change:List.of(values("planYear",2026),values("weekdays",List.of()),values("weekdays",List.of(1,1)),values("weekdays",List.of(8)),values("startTime","10:00","endTime","08:00"),values("startTime","08:00","endTime","08:00"),values("endsOn","2026-12-31"),values("endsOn","2028-01-06"),values("startsOn","2027-01-05","endsOn","2027-01-05","weekdays",List.of(1)),values("lecturerId",actor("LECTURER").id))){var p=plan();p.putAll(change);write(admin,"AddClass",null,p).andExpect(status().isBadRequest());}
        jdbc.update("UPDATE m_user SET account_locked=TRUE WHERE user_id=?",teacher.id);write(admin,"AddClass",null,plan()).andExpect(status().isBadRequest());jdbc.update("UPDATE m_user SET account_locked=FALSE WHERE user_id=?",teacher.id);
        jdbc.update("UPDATE training_courses SET status='CLOSED' WHERE id=?",course);write(admin,"AddClass",null,plan()).andExpect(status().isBadRequest());assertEquals(0,classes());
    }

    @Test void roomAndTeacherConflictsRollbackWholePlansWhileTouchingTimeRangesAreAllowed()throws Exception{
        String id=create(plan());var byTeacher=plan();byTeacher.put("classroomId",otherRoom.toString());write(admin,"AddClass",null,byTeacher).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
        var byRoom=plan();byRoom.put("lecturerId",otherTeacher.id);write(admin,"AddClass",null,byRoom).andExpect(status().isConflict());assertEquals(1,classes());
        var adjacent=plan();adjacent.put("startTime","10:00");adjacent.put("endTime","12:00");create(adjacent);assertEquals(2,classes());
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM training_sessions WHERE class_id=?",Integer.class,UUID.fromString(id)));
    }

    @Test void fixedPlansRejectScheduleChangesAndSessionsAndCapacityCannotDiscardReservations()throws Exception{
        String id=create(plan());var changed=edit(id);changed.put("weekdays",List.of(2,4));write(admin,"EditClass",id,changed).andExpect(status().isBadRequest());
        changed=edit(id);changed.put("startsOn","2027-01-05");write(admin,"EditClass",id,changed).andExpect(status().isBadRequest());
        String session=jdbc.queryForObject("SELECT CAST(id AS VARCHAR) FROM training_sessions WHERE class_id=? ORDER BY starts_at FETCH FIRST 1 ROW ONLY",String.class,UUID.fromString(id));
        write(admin,"TimeTable_EditSession",session,values("classId",id,"version",0)).andExpect(status().isConflict());
        UUID contract=UUID.randomUUID();jdbc.update("INSERT INTO training_contracts(id,code,course_id,class_id,manager_id,status,student_email,applicant_name) VALUES (?,?,?,?,?,'DRAFT','reserved@student.test','Reserved learner')",contract,contract.toString(),course,UUID.fromString(id),manager.id);
        User learner=actor("STUDENT");jdbc.update("INSERT INTO training_enrollments(id,student_id,class_id,contract_id,status) VALUES (?,?,?,?,'ACTIVE')",UUID.randomUUID(),learner.id,UUID.fromString(id),contract);
        changed=edit(id);changed.put("capacity",1);write(admin,"EditClass",id,changed).andExpect(status().isOk());
        jdbc.update("INSERT INTO training_contracts(id,code,course_id,class_id,manager_id,status,student_email,applicant_name) VALUES (?,?,?,?,?,'DRAFT','second@student.test','Second learner')",UUID.randomUUID(),UUID.randomUUID().toString(),course,UUID.fromString(id),manager.id);
        changed=edit(id);changed.put("capacity",1);write(admin,"EditClass",id,changed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.capacity").exists());
        changed=edit(id);changed.put("capacity",2);write(admin,"EditClass",id,changed).andExpect(status().isOk());
        write(admin,"EditClass",id,changed).andExpect(status().isConflict());
    }

    @Test void concurrentPlansGetUniqueCodesWithoutBypassingCalendarSerialization()throws Exception{
        var first=plan();var second=plan();second.put("startTime","10:00");second.put("endTime","12:00");
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try{List<Future<MvcResult>> results=new ArrayList<>();for(var input:List.of(first,second))results.add(executor.submit(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return write(admin,"AddClass",null,input).andReturn();}));assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();Set<String> codes=new HashSet<>();for(var result:results){var response=result.get(30,TimeUnit.SECONDS);assertEquals(200,response.getResponse().getStatus());codes.add(detail(admin,body(response).path("id").asText()).path("record").path("code").asText());}assertEquals(2,codes.size());}
        finally{executor.shutdownNow();}
    }

    @Test void cancellingAnUnusedPlanReleasesItsCalendarWhileReservedClassesStayProtected()throws Exception{
        String old=create(plan());var cancel=edit(old);cancel.put("status","CANCELLED");write(admin,"EditClass",old,cancel).andExpect(status().isOk());
        String replacement=create(plan());assertEquals("CANCELLED",detail(admin,old).path("record").path("status").asText());assertNotEquals(old,replacement);
        UUID contract=UUID.randomUUID();jdbc.update("INSERT INTO training_contracts(id,code,class_id,course_id,manager_id,status,student_email,applicant_name) VALUES (?,?,?,?,?,'DRAFT','reserved@student.test','Reserved learner')",contract,contract.toString(),UUID.fromString(replacement),course,manager.id);
        cancel=edit(replacement);cancel.put("status","CANCELLED");write(admin,"EditClass",replacement,cancel).andExpect(status().isConflict());
        assertEquals("PLANNED",detail(admin,replacement).path("record").path("status").asText());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE id=? AND status='DRAFT'",Integer.class,contract));
    }
}
