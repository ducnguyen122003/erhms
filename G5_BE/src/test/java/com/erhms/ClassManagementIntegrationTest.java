package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:class_management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    User manager,otherManager,lecturer,student;
    UUID course,room;

    @BeforeEach void setup() {
        manager=account("MANAGER");otherManager=account("MANAGER");lecturer=account("LECTURER");
        course=course(manager);student=paidStudent(manager);room=UUID.randomUUID();
        jdbc.update("INSERT INTO m_classroom(id,code,name,name_key) VALUES (?,?,?,?)",room,room.toString(),"Phòng "+room,"phòng "+room);
    }
    User account(String role) {
        User value=new User();value.email=UUID.randomUUID()+"@class.test";value.fullName="Học viên "+UUID.randomUUID();
        value.passwordHash="unused-test-hash";value.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(value);
    }
    User managedStudent(User owner) {
        User value=account("STUDENT");jdbc.update("INSERT INTO student_profiles(user_id,manager_id) VALUES (?,?)",value.id,owner.id);return value;
    }
    User paidStudent(User owner) {User value=managedStudent(owner);paidContract(value,owner,course,1000);return value;}
    UUID paidContract(User learner,User owner,UUID courseId,int amount) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO training_contracts(id,code,student_id,course_id,manager_id,starts_on,status,tuition_due,handed_over_at) VALUES (?,?,?,?,?,'2027-01-04','ACTIVE',1000,CURRENT_TIMESTAMP)",id,id.toString(),learner.id,courseId,owner.id);
        if(amount>0)jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status,confirmed_at) VALUES (?,?,'TUITION',?,'CONFIRMED',CURRENT_TIMESTAMP)",UUID.randomUUID(),id,amount);
        return id;
    }
    UUID course(User owner) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',3,12,1000,'Thực hành','OPEN',?,20)",id,id.toString(),"Khóa "+id,owner.id);
        jdbc.update("INSERT INTO course_lecturers(course_id,lecturer_id) VALUES (?,?)",id,lecturer.id);return id;
    }
    Map<String,Object> values(Object...pairs) {
        var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    Map<String,Object> classInput() {return values("courseId",course.toString(),"name","Lớp thực hành","lecturerId",lecturer.id,"capacity",20,"startsOn","2027-01-04","endsOn","2027-03-31");}
    String path(String code,String id) {return "/api/workspace/screens/"+code+(id==null?"":"/"+id);}
    JsonNode body(MvcResult result)throws Exception {return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    ResultActions read(User actor,String code,String id)throws Exception {return mvc.perform(get(path(code,id)).with(user(actor.email).roles(actor.getRoleCode())));}
    JsonNode view(User actor,String code,String id)throws Exception {return body(read(actor,code,id).andExpect(status().isOk()).andReturn());}
    ResultActions write(User actor,String code,String id,Map<String,Object> input)throws Exception {return mvc.perform(post(path(code,id)).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    String createClass(Map<String,Object> input)throws Exception {return body(write(manager,"AddClass",null,input).andExpect(status().isOk()).andReturn()).path("id").asText();}
    String createClass()throws Exception {return createClass(classInput());}
    long version(String id)throws Exception {return view(manager,"ClassDetail",id).path("record").path("version").asLong();}
    ResultActions enroll(User actor,String id,long version,User...students)throws Exception {
        return write(actor,"ClassDetail",id+"/actions/add-students",values("version",version,"students",Arrays.stream(students).map(s->Map.of("id",s.id)).toList()));
    }
    int enrollmentCount(String id) {return jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE class_id=?",Integer.class,UUID.fromString(id));}
    Set<Long> eligible(String id)throws Exception {
        Set<Long> ids=new HashSet<>();for(JsonNode item:view(manager,"ClassDetail",id).path("related").path("unassignedStudents"))ids.add(item.path("id").asLong());return ids;
    }
    Map<String,Object> session(String id) {return values("classId",id,"title","Buổi thực hành","startsAt","2027-01-04T08:00:00+07:00","endsAt","2027-01-04T10:00:00+07:00","classroomId",room.toString(),"changeNote","Lập lịch lớp");}

    @Test void classesReceiveIncreasingCodesAndServerOwnedDefaults()throws Exception {
        var input=classInput();input.put("code","CLIENT-CODE");input.put("managerId",otherManager.id);
        String first=createClass(input),second=createClass();
        JsonNode a=view(manager,"ClassDetail",first).path("record"),b=view(manager,"ClassDetail",second).path("record");
        assertTrue(a.path("code").asText().matches("L-\\d{6,}"));
        assertEquals(Long.parseLong(a.path("code").asText().substring(2))+1,Long.parseLong(b.path("code").asText().substring(2)));
        assertNotEquals(first,second);assertEquals(manager.id,a.path("managerId").asLong());assertEquals("PLANNED",a.path("status").asText());
        assertEquals(12,a.path("totalSessions").asInt());assertEquals("2027-01-04",a.path("startsOn").asText());
        read(otherManager,"ClassDetail",first).andExpect(status().isNotFound());
        assertTrue(view(manager,"AddClass",null).path("options").path("courses").toString().contains(course.toString()));
        assertFalse(view(otherManager,"AddClass",null).path("options").path("courses").toString().contains(course.toString()));
    }

    @Test void onlyManagerWithPermissionCanCreateAndCsrfIsRequired()throws Exception {
        mvc.perform(get(path("AddClass",null))).andExpect(status().isUnauthorized());
        for(String role:List.of("ADMIN","HR","LECTURER","STUDENT","ACCOUNTANT","IT_SUPPORT")) {
            User actor=account(role);jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,'AddClass')",actor.getRoleId());
            try {read(actor,"AddClass",null).andExpect(status().isForbidden());write(actor,"AddClass",null,classInput()).andExpect(status().isForbidden());}
            finally {jdbc.update("DELETE FROM role_function WHERE role_id=? AND function_code='AddClass'",actor.getRoleId());}
        }
        jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='AddClass'");
        try {write(manager,"AddClass",null,classInput()).andExpect(status().isForbidden());}
        finally {jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (6,'AddClass')");}
        mvc.perform(post(path("AddClass",null)).with(user(manager.email).roles("MANAGER")).contentType("application/json").content(json.writeValueAsString(classInput()))).andExpect(status().isForbidden());
    }

    @Test void creationRejectsWrongCourseTeacherCapacityAndDates()throws Exception {
        var input=classInput();input.put("courseId",course(otherManager).toString());write(manager,"AddClass",null,input).andExpect(status().isNotFound());
        input=classInput();input.put("lecturerId",account("LECTURER").id);write(manager,"AddClass",null,input).andExpect(status().isBadRequest());
        input=classInput();input.put("capacity",0);write(manager,"AddClass",null,input).andExpect(status().isBadRequest());
        input=classInput();input.put("endsOn","2027-01-03");write(manager,"AddClass",null,input).andExpect(status().isBadRequest());
        input=classInput();input.remove("startsOn");write(manager,"AddClass",null,input).andExpect(status().isBadRequest());
        jdbc.update("UPDATE training_courses SET status='CLOSED' WHERE id=?",course);write(manager,"AddClass",null,classInput()).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_classes WHERE manager_id=?",Integer.class,manager.id));
    }

    @Test void eligibleStudentsAreScopedActiveAndUnassignedAndAppearInTheRosterAfterAdding()throws Exception {
        String target=createClass(),other=createClass();User foreign=paidStudent(otherManager),disabled=paidStudent(manager),assigned=paidStudent(manager);
        jdbc.update("UPDATE m_user SET account_enabled=FALSE WHERE user_id=?",disabled.id);
        enroll(manager,other,version(other),assigned).andExpect(status().isOk());
        assertEquals(Set.of(student.id),eligible(target));
        enroll(manager,target,version(target),student).andExpect(status().isOk());
        JsonNode detail=view(manager,"ClassDetail",target);
        assertEquals(1,detail.path("record").path("studentCount").asInt());assertEquals(student.id,detail.path("related").path("students").get(0).path("studentId").asLong());
        assertTrue(eligible(target).isEmpty());assertFalse(eligible(other).contains(student.id));
        enroll(manager,other,version(other),foreign).andExpect(status().is4xxClientError());
        enroll(manager,other,version(other),disabled).andExpect(status().is4xxClientError());
        enroll(otherManager,target,version(target),foreign).andExpect(status().isNotFound());
        enroll(lecturer,target,version(target),foreign).andExpect(status().isForbidden());
        write(manager,"TimeTable_EditSession",null,session(target)).andExpect(status().isOk());
        assertEquals(1,view(student,"TimeTable_Student",null).path("total").asInt());
        assertEquals(1,view(lecturer,"ClassDetail_Lecturer",target).path("related").path("students").size());
    }

    @Test void enrollmentRejectsOverCapacityDuplicatesAndStaleRequestsAtomically()throws Exception {
        var input=classInput();input.put("capacity",1);String target=createClass(input);User second=paidStudent(manager);
        enroll(manager,target,0,student,second).andExpect(status().is4xxClientError());assertEquals(0,enrollmentCount(target));
        enroll(manager,target,0,student,student).andExpect(status().isBadRequest());assertEquals(0,enrollmentCount(target));
        enroll(manager,target,0).andExpect(status().isBadRequest());
        enroll(manager,target,0,student).andExpect(status().isOk());
        enroll(manager,target,0,second).andExpect(status().isConflict());
        enroll(manager,target,version(target),second).andExpect(status().is4xxClientError());
        assertEquals(1,enrollmentCount(target));
        String other=createClass();enroll(manager,other,0,student).andExpect(status().is4xxClientError());assertEquals(0,enrollmentCount(other));
    }

    @Test void anInvalidStudentRollsBackTheWholeEnrollmentBatch()throws Exception {
        String target=createClass();User foreign=managedStudent(otherManager);
        enroll(manager,target,0,student,foreign).andExpect(status().is4xxClientError());assertEquals(0,enrollmentCount(target));
        assertTrue(eligible(target).contains(student.id));
        jdbc.update("UPDATE training_classes SET status='COMPLETED' WHERE id=?",UUID.fromString(target));
        enroll(manager,target,0,student).andExpect(status().isConflict());assertEquals(0,enrollmentCount(target));
    }

    @Test void concurrentCreationAllocatesUniqueCodes()throws Exception {
        List<MvcResult> results=race(()->write(manager,"AddClass",null,classInput()).andReturn(),()->write(manager,"AddClass",null,classInput()).andReturn());
        Set<String> codes=new HashSet<>();for(MvcResult result:results) {assertEquals(200,result.getResponse().getStatus());codes.add(view(manager,"ClassDetail",body(result).path("id").asText()).path("record").path("code").asText());}
        assertEquals(2,codes.size());
    }

    @Test void simultaneousEnrollmentsCannotExceedCapacity()throws Exception {
        var input=classInput();input.put("capacity",1);String target=createClass(input);User second=paidStudent(manager);
        List<Integer> statuses=race(()->enroll(manager,target,0,student).andReturn().getResponse().getStatus(),()->enroll(manager,target,0,second).andReturn().getResponse().getStatus());
        assertEquals(1,statuses.stream().filter(s->s==200).count());assertTrue(statuses.stream().allMatch(s->s==200||s==409));assertEquals(1,enrollmentCount(target));
    }

    @Test void simultaneousEnrollmentsCannotPlaceOneStudentInTwoClasses()throws Exception {
        String first=createClass(),second=createClass();
        List<Integer> statuses=race(()->enroll(manager,first,0,student).andReturn().getResponse().getStatus(),()->enroll(manager,second,0,student).andReturn().getResponse().getStatus());
        assertEquals(1,statuses.stream().filter(s->s==200).count());assertTrue(statuses.stream().allMatch(s->s==200||s==400||s==409));assertEquals(1,enrollmentCount(first)+enrollmentCount(second));
    }

    @Test void sessionsRespectClassDatesAndRoomTeacherConflicts()throws Exception {
        String target=createClass();var input=session(target);input.put("startsAt","2027-01-03T08:00:00+07:00");input.put("endsAt","2027-01-03T10:00:00+07:00");
        write(manager,"TimeTable_EditSession",null,input).andExpect(status().isBadRequest());
        input=session(target);input.put("endsAt","2027-04-01T00:01:00+07:00");write(manager,"TimeTable_EditSession",null,input).andExpect(status().isBadRequest());
        write(manager,"TimeTable_EditSession",null,session(target)).andExpect(status().isOk());
        write(manager,"TimeTable_EditSession",null,session(target)).andExpect(status().isConflict());
        String other=createClass();write(manager,"TimeTable_EditSession",null,session(other)).andExpect(status().isConflict());
        var edit=values("version",version(target),"status","PLANNED","lecturerId",lecturer.id,"capacity",20,"startsOn","2027-01-05","endsOn","2027-03-31","changeNote","Đổi ngày khai giảng");
        write(manager,"EditClass",target,edit).andExpect(status().isBadRequest());
        jdbc.update("UPDATE training_classes SET status='CANCELLED' WHERE id=?",UUID.fromString(target));
        write(manager,"TimeTable_EditSession",null,session(target)).andExpect(status().isConflict());
    }

    @Test void tuitionConfirmationQueuesTheStudentUntilManagerSelectsAClass()throws Exception {
        String target=createClass();User learner=managedStudent(manager);UUID contract=UUID.randomUUID();User accountant=account("ACCOUNTANT");
        jdbc.update("INSERT INTO training_contracts(id,code,student_id,course_id,manager_id,accountant_id,starts_on,status,tuition_due,handed_over_at) VALUES (?,?,?,?,?,?,'2027-01-04','SIGNED',0,CURRENT_TIMESTAMP)",contract,contract.toString(),learner.id,course,manager.id,accountant.id);
        assertFalse(eligible(target).contains(learner.id));
        write(accountant,"TuitionDetail",contract+"/actions/activate-zero",values("version",0,"changeNote","Xác nhận miễn học phí")).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE student_id=?",Integer.class,learner.id));
        assertTrue(eligible(target).contains(learner.id));enroll(manager,target,0,learner).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE student_id=? AND class_id=? AND contract_id=?",Integer.class,learner.id,UUID.fromString(target),contract));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_classes WHERE course_id=?",Integer.class,course));
    }

    @Test void onlyConfirmedFullTuitionForTheSameCourseQualifiesAndIsRecheckedOnSave()throws Exception {
        String target=createClass();User unpaid=managedStudent(manager),partial=managedStudent(manager),wrongCourse=managedStudent(manager),pending=managedStudent(manager);
        paidContract(unpaid,manager,course,0);paidContract(partial,manager,course,500);paidContract(wrongCourse,manager,course(manager),1000);
        UUID pendingContract=paidContract(pending,manager,course,1000);
        jdbc.update("UPDATE financial_transactions SET status='PENDING' WHERE contract_id=?",pendingContract);
        assertEquals(Set.of(student.id),eligible(target));
        for(User invalid:List.of(unpaid,partial,wrongCourse,pending))enroll(manager,target,0,invalid).andExpect(status().is4xxClientError());
        jdbc.update("UPDATE financial_transactions SET status='MISMATCH' WHERE contract_id IN (SELECT id FROM training_contracts WHERE student_id=?)",student.id);
        enroll(manager,target,0,student).andExpect(status().is4xxClientError());assertEquals(0,enrollmentCount(target));
    }

    private <T> List<T> race(Callable<T> first,Callable<T> second)throws Exception {
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            List<Future<T>> results=new ArrayList<>();for(Callable<T> operation:List.of(first,second))results.add(executor.submit(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return operation.call();}));
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();return List.of(results.get(0).get(30,TimeUnit.SECONDS),results.get(1).get(30,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
    }
}
