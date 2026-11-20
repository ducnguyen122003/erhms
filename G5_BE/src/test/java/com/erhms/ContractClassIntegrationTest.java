package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.EnrollmentCourses;
import com.erhms.workspace.ContractTemplateData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:contract_class;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ContractClassIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired UserRepository users;@Autowired RoleRepository roles;
    @Autowired ContractTemplateData templates;
    User admin,manager,otherManager,hr,student,accountant,teacher;UUID admission,cl,course;int planSlot;
    @BeforeEach void setup()throws Exception{
        admin=actor("ADMIN");manager=actor("MANAGER");otherManager=actor("MANAGER");hr=actor("HR");student=actor("STUDENT");accountant=actor("ACCOUNTANT");teacher=actor("LECTURER");planSlot=0;
        jdbc.update("INSERT INTO student_profiles(user_id,manager_id) VALUES (?,?)",student.id,manager.id);
        admission=admission(student,EnrollmentCourses.NAMES.get(0));
        course=course(manager,EnrollmentCourses.NAMES.get(0));cl=plan(course,"2027-01-04",20);
    }
    User actor(String role){var u=new User();u.email=UUID.randomUUID()+"@contract-class.test";u.fullName="Học viên "+UUID.randomUUID();u.passwordHash="fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
    UUID admission(User learner,String program){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO consultation_registrations(id,full_name,email,phone,program_name,enrollment_course,contact_time,created_date) VALUES (?,?,?,'0912345678','Sửa máy',?,'any',CURRENT_TIMESTAMP)",id,learner.fullName,learner.email,program);jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,forwarded_at,forwarded_by) VALUES (?,?,?,'PROFILE_PENDING',CURRENT_TIMESTAMP,?)",UUID.randomUUID(),id,hr.id,hr.id);return id;}
    Map<String,Object> values(Object...pairs){var p=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)p.put((String)pairs[i],pairs[i+1]);return p;}
    UUID course(User owner,String name){return UUID.fromString(String.format(Locale.ROOT,"64000000-0000-0000-0000-%012d",EnrollmentCourses.NAMES.indexOf(name)+1));}
    UUID plan(UUID selectedCourse,String start,int capacity)throws Exception{
        return plan(selectedCourse,start,capacity,manager);
    }
    UUID plan(UUID selectedCourse,String start,int capacity,User owner)throws Exception{
        UUID room=UUID.randomUUID();jdbc.update("INSERT INTO m_classroom(id,code,name,name_key) VALUES (?,?,?,?)",room,room.toString(),"Phòng "+room,"phòng "+room);
        LocalDate first=LocalDate.parse(start);int hour=8+2*planSlot++;
        var input=values("courseId",selectedCourse.toString(),"managerId",owner.id,"name","Lớp "+room,"planYear",first.getYear(),"startsOn",start,"endsOn",first.plusMonths(4).toString(),"weekdays",List.of(first.getDayOfWeek().getValue()),"startTime",String.format(Locale.ROOT,"%02d:00",hour),"endTime",String.format(Locale.ROOT,"%02d:00",hour+2),"classroomId",room.toString(),"lecturerId",teacher.id,"capacity",capacity);
        return UUID.fromString(body(write(admin,"AddClass",null,input).andExpect(status().isOk()).andReturn()).path("id").asText());
    }
    Map<String,Object> input(){return values("consultationId",admission.toString(),"classId",cl.toString(),"paymentDueOn","2027-01-01","tuitionDue",1000,"depositDue",200,"terms","Theo lớp đã đăng ký");}
    String path(String screen,String id){return "/api/workspace/screens/"+screen+(id==null?"":"/"+id);}
    ResultActions write(User actor,String screen,String id,Map<String,Object> input)throws Exception{return mvc.perform(post(path(screen,id)).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode body(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode read(String screen,String id)throws Exception{return body(mvc.perform(get(path(screen,id)).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    String create(Map<String,Object> input)throws Exception{return body(write(manager,"AddTrainingContract",null,input).andExpect(status().isOk()).andReturn()).path("id").asText();}
    boolean eligible(UUID target)throws Exception{for(var row:read("ClassDetail",target.toString()).path("related").path("unassignedStudents"))if(row.path("id").asLong()==student.id)return true;return false;}
    String importPayment(String contract,int amount)throws Exception{
        return body(write(accountant,"ListPayment","new/actions/import",values("contractId",contract,"kind","TUITION","amount",amount,"bankReference",UUID.randomUUID().toString(),"payerName",student.fullName,"bankId","55000000-0000-0000-0000-000000000002","memo","Đối chiếu theo lớp","evidence","Sao kê đã kiểm tra")).andExpect(status().isOk()).andReturn()).path("id").asText();
    }
    void confirmPayment(String tx)throws Exception{
        write(accountant,"PaymentDetail",tx+"/actions/confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã so sao kê")).andExpect(status().isOk());
    }

    @Test void selectingAFixedClassIsMandatoryAndDeterminesItsCourseAndOpeningDate()throws Exception{
        var missing=input();missing.remove("classId");write(manager,"AddTrainingContract",null,missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.classId").exists());
        var forged=input();forged.put("startsOn","2027-02-01");write(manager,"AddTrainingContract",null,forged).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.startsOn").exists());
        String id=create(input());var record=read("TrainingContractDetail",id).path("record");assertEquals(cl.toString(),record.path("classId").asText());assertEquals(course.toString(),record.path("courseId").asText());assertEquals("2027-01-04",record.path("startsOn").asText());assertEquals("DRAFT",record.path("status").asText());assertFalse(eligible(cl));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(id)));
    }

    @Test void foreignWrongCourseUnfixedAndClosedClassesCannotBeBound()throws Exception{
        UUID foreign=plan(course(otherManager,EnrollmentCourses.NAMES.get(0)),"2027-01-04",20,otherManager);var p=input();p.put("classId",foreign.toString());write(manager,"AddTrainingContract",null,p).andExpect(status().isNotFound());
        UUID other=plan(course(manager,EnrollmentCourses.NAMES.get(1)),"2027-01-04",20);p=input();p.put("classId",other.toString());p.put("courseId",course.toString());write(manager,"AddTrainingContract",null,p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.classId").exists());
        jdbc.update("UPDATE training_classes SET plan_fixed=FALSE WHERE id=?",cl);write(manager,"AddTrainingContract",null,input()).andExpect(status().isBadRequest());jdbc.update("UPDATE training_classes SET plan_fixed=TRUE WHERE id=?",cl);
        for(String state:List.of("COMPLETED","CANCELLED")){jdbc.update("UPDATE training_classes SET status=? WHERE id=?",state,cl);write(manager,"AddTrainingContract",null,input()).andExpect(status().isBadRequest());}
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=?",Integer.class,admission));
    }

    @Test void contractReservationsConsumeCapacityAndEditingTheSameDraftDoesNotCountItTwice()throws Exception{
        jdbc.update("UPDATE training_classes SET capacity=1 WHERE id=?",cl);String first=create(input());
        assertEquals(1,read("ClassDetail",cl.toString()).path("record").path("reservedCount").asInt());assertEquals(0,read("ClassDetail",cl.toString()).path("record").path("remainingSlots").asInt());
        var available=read("EditTrainingContract",first).path("options").path("classes");assertEquals(1,available.size());assertEquals(cl.toString(),available.get(0).path("id").asText());assertEquals(1,available.get(0).path("remainingSlots").asInt());
        var edit=input();edit.put("version",0);write(manager,"EditTrainingContract",first,edit).andExpect(status().isOk());
        UUID next=admission(actor("STUDENT"),EnrollmentCourses.NAMES.get(0));var second=input();second.put("consultationId",next.toString());write(manager,"AddTrainingContract",null,second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.classId").exists());
        jdbc.update("UPDATE training_contracts SET status='CANCELLED' WHERE id=?",UUID.fromString(first));String replacement=create(second);assertNotEquals(first,replacement);
    }

    @Test void onlyConfirmedFullTuitionUnlocksPlacementIntoTheContractSelectedClass()throws Exception{
        String id=create(input());UUID contract=UUID.fromString(id);
        jdbc.update("UPDATE training_contracts SET status='SIGNED',signature_approved_at=CURRENT_TIMESTAMP,handed_over_at=CURRENT_TIMESTAMP,accountant_id=? WHERE id=?",accountant.id,contract);
        UUID other=plan(course,"2027-01-11",20);
        assertFalse(eligible(cl));String firstPayment=importPayment(id,400);assertFalse(eligible(cl));confirmPayment(firstPayment);assertFalse(eligible(cl));assertFalse(eligible(other));
        String balancePayment=importPayment(id,600);assertFalse(eligible(cl));confirmPayment(balancePayment);assertTrue(eligible(cl));assertFalse(eligible(other));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,contract));
        write(manager,"ClassDetail",other+"/actions/add-students",values("version",0,"students",List.of(Map.of("id",student.id)))).andExpect(status().isBadRequest());
        write(manager,"ClassDetail",cl+"/actions/add-students",values("version",0,"students",List.of(Map.of("id",student.id)))).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND class_id=?",Integer.class,contract,cl));
        var after=read("ClassDetail",cl.toString()).path("record");assertEquals(1,after.path("studentCount").asInt());assertEquals(1,after.path("reservedCount").asInt());assertEquals(19,after.path("remainingSlots").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE recipient_id=? AND target_screen='ClassDetail' AND target_id=?",Integer.class,manager.id,cl.toString()));
    }

    @Test void pastPlansRejectNewSelectionsWhileAnExistingBoundDraftRemainsEditable()throws Exception{
        String id=create(input());LocalDate oldStart=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(1),oldEnd=oldStart.plusMonths(4);jdbc.update("UPDATE training_classes SET plan_year=?,starts_on=?,ends_on=? WHERE id=?",oldStart.getYear(),oldStart,oldEnd,cl);
        var edit=input();edit.put("version",0);write(manager,"EditTrainingContract",id,edit).andExpect(status().isOk());
        var next=input();next.put("consultationId",admission(actor("STUDENT"),EnrollmentCourses.NAMES.get(0)).toString());write(manager,"AddTrainingContract",null,next).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.classId").exists());
    }

    @SuppressWarnings("unchecked")
    Map<String,Object> completeTemplate(){
        var details=new LinkedHashMap<String,Object>();
        for(var section:(List<Map<String,Object>>)templates.metadata().get("sections"))for(var field:(List<Map<String,Object>>)section.get("fields")){
            if(!Boolean.TRUE.equals(field.get("requiredForPdf"))||Boolean.TRUE.equals(field.get("readOnly"))||field.containsKey("condition"))continue;
            Object value=switch(field.get("type").toString()){case "date"->"2026-11-01";case "number"->1000;case "email"->"center@example.test";case "select"->((List<?>)field.get("source")).get(0);case "checkbox"->false;default->"Đã xác nhận chương trình đào tạo";};
            details.put(field.get("key").toString(),value);
        }
        details.put("studentDateOfBirth","2000-01-02");details.put("studentIdentityNumber","001200000001");details.put("studentAddress","Hà Nội");details.put("emergencyContactName","Nguyễn Văn A");details.put("emergencyRelationship","Bố");details.put("emergencyContactPhone","0987654321");return details;
    }

    @Test void thePdfUsesTheSelectedClassDatesAndIgnoresForgedTemplateDates()throws Exception{
        var details=completeTemplate();details.put("startsOn","2099-01-01");details.put("endsOn","2099-04-01");details.put("latestOpeningOn","2099-02-01");
        var input=input();input.put("templateData",details);String id=create(input);var record=read("TrainingContractDetail",id).path("record");var template=record.path("templateData");
        assertEquals("2027-01-04",record.path("classStartsOn").asText());assertEquals("2027-05-04",record.path("classEndsOn").asText());
        assertEquals("2027-01-04",template.path("startsOn").asText());assertEquals("2027-05-04",template.path("endsOn").asText());assertEquals("2027-01-04",template.path("latestOpeningOn").asText());
        var pdf=mvc.perform(get("/api/workspace/contracts/"+id+"/pdf").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn();
        try(var document=Loader.loadPDF(pdf.getResponse().getContentAsByteArray())){String text=new PDFTextStripper().getText(document);assertTrue(text.contains("2027"));assertFalse(text.contains("2099"));assertTrue(text.replaceAll("\\s+","").contains(student.fullName.replaceAll("\\s+","")));}
    }
}
