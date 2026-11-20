package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:fixed_course_schedules;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FixedCourseSchedulesIntegrationTest {
    static final String BASE="/api/workspace/screens/";
    static final String FIRST="64000000-0000-0000-0000-000000000001",SECOND="64000000-0000-0000-0000-000000000002";
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;
    User admin,manager,otherManager,teacher;UUID room;
    @BeforeEach void setup(){admin=actor("ADMIN");manager=actor("MANAGER");otherManager=actor("MANAGER");teacher=actor("LECTURER");room=UUID.randomUUID();jdbc.update("INSERT INTO m_classroom(id,code,name,name_key) VALUES (?,?,?,?)",room,room.toString(),"Phòng "+room,"phòng "+room);}
    User actor(String role){var u=new User();u.email=UUID.randomUUID()+"@fixed-course.test";u.fullName="Kiểm thử "+role;u.passwordHash="fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
    Map<String,Object> values(Object...pairs){var p=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)p.put((String)pairs[i],pairs[i+1]);return p;}
    ResultActions read(User actor,String path)throws Exception{return mvc.perform(get(BASE+path).with(user(actor.email).roles(actor.getRoleCode())));}
    ResultActions write(User actor,String path,Map<String,Object> input)throws Exception{return mvc.perform(post(BASE+path).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode schedules(String query)throws Exception{return body(read(admin,"CourseSchedules"+query).andExpect(status().isOk()).andReturn());}
    Map<String,Object> plan(String course,long owner,int year,int hour,String title){return values("courseId",course,"managerId",owner,"name",title,"planYear",year,"startsOn",year+"-01-04","endsOn",year+"-01-17","weekdays",List.of(1,3),"startTime",String.format(Locale.ROOT,"%02d:00",hour),"endTime",String.format(Locale.ROOT,"%02d:00",hour+2),"classroomId",room.toString(),"lecturerId",teacher.id,"capacity",20);}
    String create(Map<String,Object> input)throws Exception{return body(write(admin,"AddClass",input).andExpect(status().isOk()).andReturn()).path("id").asText();}
    UUID legacyCourse(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?, 'Historical course','Điện',3,10,900,'Historical objectives','OPEN',?,20)",id,id.toString(),manager.id);return id;}

    @Test void bothTheConfigurationAndPublicCatalogContainExactlyTheFourFixedPrograms()throws Exception{
        UUID legacy=legacyCourse();var data=schedules("");var courses=data.path("courses");assertEquals(4,courses.size());assertEquals(4,data.path("options").path("courses").size());
        String[] names={"Sửa chữa máy tính Laptop chuyên nghiệp","Sửa chữa máy tính PC & máy in","Sửa chữa Điện tử - Điện gia dụng","Sửa chữa Điện lạnh, Điện gia dụng & Inverter"};int[] months={4,4,4,7};long[] fees={14200000,13000000,11000000,21900000};
        for(int i=0;i<4;i++){assertEquals(names[i],courses.get(i).path("name").asText());assertEquals(months[i],courses.get(i).path("durationMonths").asInt());assertEquals(fees[i],courses.get(i).path("tuitionFee").asLong());assertFalse(courses.get(i).has("managerId"));}
        var publicCourses=body(mvc.perform(get("/api/public/training-courses")).andExpect(status().isOk()).andReturn());assertEquals(4,publicCourses.size());assertFalse(publicCourses.toString().contains(legacy.toString()));
        for(var course:publicCourses){assertFalse(course.has("managerId"));assertFalse(course.has("fixedCatalog"));assertFalse(course.has("version"));}
        mvc.perform(get("/api/public/training-courses/"+legacy)).andExpect(status().isNotFound());
    }

    @Test void legacyCourseAndProfessionCrudRemainBlockedEvenWhenOldGrantsAreInjected()throws Exception{
        UUID legacy=legacyCourse();
        for(String code:List.of("ListCourse","AddCourse","EditCourse","CourseDetail","ListProfession","AddProfession","EditProfession")){
            jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (1,?)",code);
            read(admin,code).andExpect(status().isForbidden());write(admin,code+"/"+legacy,values("name","Forged program")).andExpect(status().isForbidden());
        }
        write(admin,"AddCourse",values("name","Fifth course")).andExpect(status().isForbidden());
        mvc.perform(post("/api/master-data/screens/AddProfession").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var p=plan(legacy.toString(),manager.id,2027,8,"Legacy plan");write(admin,"AddClass",p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.courseId").exists());
        assertEquals(4,schedules("").path("courses").size());assertEquals("Historical course",jdbc.queryForObject("SELECT name FROM training_courses WHERE id=?",String.class,legacy));
    }

    @Test void administratorsChooseManagerAndAnyActiveLecturerSeparatelyForEachClass()throws Exception{
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM course_lecturers WHERE lecturer_id=?",Integer.class,teacher.id));
        String own=create(plan(FIRST,manager.id,2027,8,"Manager one")),other=create(plan(FIRST,otherManager.id,2027,10,"Manager two"));
        read(manager,"ClassDetail/"+own).andExpect(status().isOk());read(manager,"ClassDetail/"+other).andExpect(status().isNotFound());read(otherManager,"ClassDetail/"+other).andExpect(status().isOk());read(otherManager,"ClassDetail/"+own).andExpect(status().isNotFound());
        var ownOptions=body(read(manager,"AddTrainingContract").andExpect(status().isOk()).andReturn()).path("options").path("classes");assertTrue(ownOptions.toString().contains(own));assertFalse(ownOptions.toString().contains(other));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_courses WHERE fixed_catalog=TRUE AND manager_id IS NOT NULL",Integer.class));
    }

    @Test void configurationFiltersReturnOnlyTheSelectedCourseYearAndStatus()throws Exception{
        String first=create(plan(FIRST,manager.id,2027,8,"Laptop January")),second=create(plan(SECOND,otherManager.id,2027,10,"PC January"));
        String future=create(plan(FIRST,manager.id,2028,8,"Laptop next year"));
        var data=schedules("?courseId="+FIRST+"&year=2027&q=Laptop&status=PLANNED");assertEquals(1,data.path("total").asInt());assertEquals(first,data.path("items").get(0).path("id").asText());assertEquals(data.path("items"),data.path("plans"));assertFalse(data.path("items").toString().contains(second));assertFalse(data.path("items").toString().contains(future));
        assertEquals(1,data.path("courses").get(0).path("classCount").asInt());assertTrue(data.path("options").path("planYears").toString().contains("2028"));
        read(admin,"CourseSchedules?courseId="+UUID.randomUUID()).andExpect(status().isBadRequest());read(admin,"CourseSchedules?year=1899").andExpect(status().isBadRequest());read(admin,"CourseSchedules?status=UNKNOWN").andExpect(status().isBadRequest());
    }

    @Test void theNewConfigurationFunctionEnforcesRolesAndImmediatePermissionRevocation()throws Exception{
        for(String role:List.of("MANAGER","HR","LECTURER","STUDENT","ACCOUNTANT","IT_SUPPORT")){User wrong=actor(role);jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,'CourseSchedules')",wrong.getRoleId());read(wrong,"CourseSchedules").andExpect(status().isForbidden());}
        jdbc.update("DELETE FROM role_function WHERE role_id=1 AND function_code='CourseSchedules'");read(admin,"CourseSchedules").andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_classes WHERE manager_id IN (?,?)",Integer.class,manager.id,otherManager.id));
    }
}
