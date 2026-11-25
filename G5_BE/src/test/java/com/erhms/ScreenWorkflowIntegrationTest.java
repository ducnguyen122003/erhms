package com.erhms;

import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.role.repository.RoleRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:screen_workflows;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScreenWorkflowIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 Map<String,User> actors;User admin,manager,lecturer,student,hr,it,accountant,otherStudent,otherManager,otherHr;
 static final String GROUP="10000000-0000-0000-0000-000000000001",LOCATION="20000000-0000-0000-0000-000000000001",PROFESSION="30000000-0000-0000-0000-000000000001",ROOM="40000000-0000-0000-0000-000000000001",BANK="55000000-0000-0000-0000-000000000001";
 @BeforeEach void setup(){
  for(String[] entry:List.of(new String[]{"m_equipment_group",GROUP,"Điện"},new String[]{"m_storage_location",LOCATION,"Kho A"},new String[]{"m_profession",PROFESSION,"Điện"},new String[]{"m_classroom",ROOM,"P-01"}))jdbc.update("INSERT INTO "+entry[0]+"(id,code,name,name_key) VALUES (?,?,?,?)",UUID.fromString(entry[1]),"FIXTURE",entry[2],entry[2].toLowerCase(Locale.ROOT));actors=new LinkedHashMap<>();for(String role:List.of("ADMIN","MANAGER","LECTURER","STUDENT","HR","IT_SUPPORT","ACCOUNTANT"))actors.put(role,create(role));admin=actors.get("ADMIN");manager=actors.get("MANAGER");lecturer=actors.get("LECTURER");student=actors.get("STUDENT");hr=actors.get("HR");it=actors.get("IT_SUPPORT");accountant=actors.get("ACCOUNTANT");otherStudent=create("STUDENT");otherManager=create("MANAGER");otherHr=create("HR");}
 User create(String role){User u=new User();u.email=UUID.randomUUID()+"@screen.test";u.fullName="Kiểm thử "+role+" "+UUID.randomUUID().toString().substring(0,6);u.passwordHash="not-a-login-fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
 String path(String code,String id){return "/api/workspace/screens/"+code+(id==null?"":"/"+id);}
 ResultActions read(User user,String code,String id)throws Exception{return mvc.perform(get(path(code,id)).with(user(user.email).roles(user.getRoleCode())));}
 JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
 JsonNode view(User user,String code,String id)throws Exception{return body(read(user,code,id).andExpect(status().isOk()).andReturn());}
 ResultActions write(User user,String code,String id,Map<String,Object> input)throws Exception{if(code.equals("AddTrainingContract")||code.equals("EditTrainingContract"))input=ClassPlanTestFixtures.withClass(jdbc,user.id,input);return mvc.perform(post(path(code,id)).with(user(user.email).roles(user.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
 JsonNode save(User user,String code,String id,Map<String,Object> input)throws Exception{return body(write(user,code,id,input).andExpect(status().isOk()).andReturn());}
 ResultActions act(User user,String code,String id,String action,Map<String,Object> input)throws Exception{return mvc.perform(post(path(code,id)+"/actions/"+action).with(user(user.email).roles(user.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
 JsonNode action(User user,String code,String id,String action,Map<String,Object> input)throws Exception{return body(act(user,code,id,action,input).andExpect(status().isOk()).andReturn());}
 Map<String,Object> values(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
 long version(User u,String code,String id)throws Exception{return view(u,code,id).path("record").path("version").asLong();}
 Map<String,Object> courseInput(){return values("name","Khóa thực hành","code","KH-"+UUID.randomUUID().toString().substring(0,8),"professionId",PROFESSION,"managerId",manager.id,"durationMonths",3,"totalSessions",2,"classCapacity",10,"tuitionFee",1000,"objectives","Kỹ năng thực hành","entryRequirements","Theo hồ sơ","status","OPEN","assessments",List.of(values("title","Thực hành","weight",100)),"lecturers",List.of(values("id",lecturer.id)));}
 String createCourse()throws Exception{UUID id=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,profession_id,manager_id,duration_months,total_sessions,class_capacity,tuition_fee,objectives,status) VALUES (?,?,'Khóa thực hành','Điện',?,?,3,1,10,1000,'Kỹ năng thực hành','OPEN')",id,id.toString(),UUID.fromString(PROFESSION),manager.id);jdbc.update("INSERT INTO course_lecturers(course_id,lecturer_id) VALUES (?,?)",id,lecturer.id);jdbc.update("INSERT INTO course_assessments(id,course_id,title,weight) VALUES (?, ?,'Thực hành',100)",UUID.randomUUID(),id);return id.toString();}
 String createContract(String course)throws Exception{
  String admission=body(mvc.perform(post("/api/public/consultations").with(csrf()).contentType("application/json").content(json.writeValueAsString(values("fullName",student.fullName,"phone","0912345678","email",student.email,"contactTime","any","program_name","Khóa thực hành")))).andExpect(status().isCreated()).andReturn()).path("id").asText();
  save(hr,"AdmissionConsultation",admission,values("version",0,"channel","PHONE","status","DOCUMENT_APPOINTMENT","notes","Đã tư vấn thông tin khóa học","documentDueOn","2026-12-15"));
  jdbc.update("UPDATE consultation_followups SET status='PROFILE_PENDING',forwarded_at=CURRENT_TIMESTAMP,forwarded_by=? WHERE consultation_id=?",hr.id,UUID.fromString(admission));
  String contract=save(manager,"AddTrainingContract",null,values("consultationId",admission,"courseId",course,"studentId",student.id,"startsOn","2026-11-15","paymentDueOn","2026-11-14","tuitionDue",1000,"depositDue",200,"terms","Điều khoản theo hồ sơ xác nhận","paymentSchedule","")).path("id").asText();
  // Finance and class tests start from an existing, approved paper contract.
  // ContractSigningIntegrationTest covers sending, scanning and review through the API.
  jdbc.update("UPDATE training_contracts SET status='SIGNED',student_id=?,signed_on=DATE '2026-11-01',signature_method='PAPER',signature_evidence='Existing approved paper contract',accountant_id=?,handed_over_at=CURRENT_TIMESTAMP,signature_approved_at=CURRENT_TIMESTAMP,signature_approved_by=?,version=version+1 WHERE id=?",student.id,accountant.id,manager.id,UUID.fromString(contract));return contract;
 }
 String importAndConfirm(String contract,String kind,int amount)throws Exception{
  String id=action(accountant,"ListPayment","new","import",values("contractId",contract,"kind",kind,"amount",amount,"bankReference",UUID.randomUUID().toString(),"payerName",student.fullName,"bankId",BANK,"memo","Đối chiếu theo hợp đồng","evidence","Chứng từ đã kiểm tra")).path("id").asText();
  action(accountant,"PaymentDetail",id,"confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã khớp chứng từ"));
  return id;
 }
 @Test void everyImportedBusinessScreenHasAWorkingEndpointAndDefaultsMatchTheWorkbook()throws Exception{
  var source=json.readValue(new ClassPathResource("workspace/screen-catalog.json").getInputStream(),new TypeReference<List<Map<String,Object>>>(){});int checked=0;
  for(var screen:source){String code=(String)screen.get("code");if(screen.containsKey("masterType")||"student_accounts".equals(screen.get("kind"))||Boolean.TRUE.equals(screen.get("public"))||!code.equals(screen.get("functionCode")))continue;
   @SuppressWarnings("unchecked")List<String> permitted=(List<String>)screen.get("roles");
   for(String role:actors.keySet()){
    var request=read(actors.get(role),code,null);
    if(permitted.contains(role))request.andExpect(status().isOk());else request.andExpect(status().isForbidden());
   }checked++;
  }assertEquals(85,checked);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM role_function WHERE function_code='SendNotification' AND role_id IN(1,2,7)",Integer.class));
 }
 @Test void publicCourseCatalogueOnlyExposesPublishedContentAndNoInternalAssignment()throws Exception{
  String legacy=createCourse();mvc.perform(get("/api/public/training-courses/"+legacy)).andExpect(status().isNotFound());
  String course="64000000-0000-0000-0000-000000000001";var published=body(mvc.perform(get("/api/public/training-courses/"+course)).andExpect(status().isOk()).andReturn());assertFalse(published.has("managerId"));assertFalse(published.has("version"));
  jdbc.update("UPDATE training_courses SET status='DRAFT' WHERE id=?",UUID.fromString(course));mvc.perform(get("/api/public/training-courses/"+course)).andExpect(status().isNotFound());
 }
 @Test void onlyAdminCanManageCoursesEvenIfAnotherRoleHasAnAccidentalGrant()throws Exception{
  String course=createCourse();read(admin,"CourseDetail",course).andExpect(status().isForbidden());write(admin,"AddCourse",null,courseInput()).andExpect(status().isForbidden());write(admin,"EditCourse",course,courseInput()).andExpect(status().isForbidden());
  for(User actor:actors.values())if(!actor.equals(admin)){
   for(String code:List.of("ListCourse","AddCourse","EditCourse","CourseDetail")){
    read(actor,code,code.equals("EditCourse")||code.equals("CourseDetail")?course:null).andExpect(status().isForbidden());
    jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,?)",actor.getRoleId(),code);
    read(actor,code,code.equals("EditCourse")||code.equals("CourseDetail")?course:null).andExpect(status().isForbidden());
   }
   write(actor,"AddCourse",null,courseInput()).andExpect(status().isForbidden());
   write(actor,"EditCourse",course,courseInput()).andExpect(status().isForbidden());
  }
  mvc.perform(post(path("AddCourse",null)).with(user(admin.email).roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void confirmedTuitionEnrollsOnceAndDuplicateSignalsNeverCollectTwice()throws Exception{
  String ct=createContract(createCourse());String payment=importAndConfirm(ct,"TUITION",1000);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
  act(accountant,"PaymentDetail",payment,"confirm",values("version",1,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Lặp lại")).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=? AND status='CONFIRMED'",Integer.class,UUID.fromString(ct)));
  String reference=view(accountant,"PaymentDetail",payment).path("record").path("bankReference").asText();JsonNode duplicate=action(accountant,"ListPayment","new","import",values("contractId",ct,"bankReference",reference));assertEquals("PaymentDetail_Duplicate",duplicate.path("screen").asText());assertEquals(payment,duplicate.path("id").asText());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
  assertEquals(1,view(manager,"AutoAssignmentReview",null).path("total").asInt());assertEquals(0,view(otherManager,"AutoAssignmentReview",null).path("total").asInt());
  read(otherStudent,"TuitionDetail_Student",ct).andExpect(status().isNotFound());read(student,"TuitionDetail_Student",ct).andExpect(status().isOk());
  read(hr,"TrainingContractDetail",ct).andExpect(status().isForbidden());
 }
 @Test void mismatchedOrOverpaidTransactionsRemainUnconfirmedAndDoNotAssignStudents()throws Exception{
  String ct=createContract(createCourse());String payment=action(accountant,"ListPayment","new","import",values("contractId",ct,"kind","TUITION","amount",2000,"bankReference",UUID.randomUUID().toString(),"payerName",student.fullName,"bankId",BANK,"memo","Sai số tiền","evidence","Kiểm thử")).path("id").asText();
  act(accountant,"PaymentDetail",payment,"confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã kiểm tra")).andExpect(status().isConflict());
  action(accountant,"PaymentDetail",payment,"mismatch",values("version",0,"memo","Đang xác minh khoản vượt nghĩa vụ"));
  act(accountant,"PaymentDetail",payment,"confirm",values("version",1)).andExpect(status().isConflict());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
 }
 @Test void schedulesBlockOverlapsForRoomClassAndLecturerAndRejectStaleEdits()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();
  jdbc.update("UPDATE training_classes SET plan_fixed=FALSE WHERE id=?",UUID.fromString(cl));jdbc.update("DELETE FROM training_sessions WHERE class_id=?",UUID.fromString(cl));
  var session=values("classId",cl,"title","Thực hành","startsAt","2026-11-15T01:00:00Z","endsAt","2026-11-15T03:00:00Z","classroomId",ROOM,"changeNote","Lập lịch");String id=save(admin,"TimeTable_EditSession",null,session).path("id").asText();
  write(admin,"TimeTable_EditSession",null,session).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
  jdbc.update("UPDATE m_classroom SET name='Phòng đã đổi tên',name_key='phòng đã đổi tên' WHERE id=?",UUID.fromString(ROOM));
  assertEquals("Phòng đã đổi tên",view(admin,"TimeTable_EditSession",id).path("record").path("room").asText());
  write(admin,"TimeTable_EditSession",null,session).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
  session.put("version",9);write(admin,"TimeTable_EditSession",id,session).andExpect(status().isConflict());
  write(lecturer,"TimeTable_EditSession",id,session).andExpect(status().isForbidden());
  assertEquals(1,view(student,"TimeTable_Student",null).path("total").asInt());assertEquals(0,view(otherStudent,"TimeTable_Student",null).path("total").asInt());
  jdbc.update("INSERT INTO training_enrollments(id,class_id,student_id,status) VALUES (?,?,?,'ACTIVE')",UUID.randomUUID(),UUID.fromString(cl),otherStudent.id);
  assertEquals(1,view(student,"TimeTable_Student",id).path("related").path("attendance").size());
  mvc.perform(get(path("TimeTable_Manager",null)).param("from","2026-11-15").param("to","2026-11-15").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.stats[0].value").value(1));
  mvc.perform(get(path("TimeTable_Manager",null)).param("from","2026-11-16").param("to","2026-11-22").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.stats[0].value").value(0));
  assertEquals(0,view(admin,"TimeTable_Conflict",null).path("total").asInt());
  jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room) VALUES (?,?,?,? ,?,?)",UUID.randomUUID(),UUID.fromString(cl),"Lịch cũ đang xung đột",OffsetDateTime.parse("2026-11-15T02:00:00Z"),OffsetDateTime.parse("2026-11-15T04:00:00Z"),"P-01");
  assertEquals(2,view(admin,"TimeTable_Conflict",null).path("total").asInt());
 }
 @Test void automaticallyEnrolledStudentAppearsForAttendanceGradingAndClosure()throws Exception{
  String course=createCourse();
  List<String> tests=List.of("Điểm kiểm tra lần 1","Điểm kiểm tra lần 2","Điểm kiểm tra cuối kỳ");
  jdbc.update("DELETE FROM course_assessments WHERE course_id=?",UUID.fromString(course));
  for(int index=0;index<tests.size();index++){
   jdbc.update("INSERT INTO course_assessments(id,course_id,title,weight) VALUES (?,?,?,?)",UUID.randomUUID(),UUID.fromString(course),tests.get(index),index==2?40:30);
  }
  String ct=createContract(course);
  jdbc.update("UPDATE training_contracts SET deposit_due=0 WHERE id=?",UUID.fromString(ct));
  importAndConfirm(ct,"TUITION",1000);
  String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();
  String enrollment=view(manager,"AutoAssignmentReview",null).path("items").get(0).path("id").asText();
  UUID classId=UUID.fromString(cl);
  String session=jdbc.queryForObject("SELECT CAST(id AS VARCHAR) FROM training_sessions WHERE class_id=?",String.class,classId);
  OffsetDateTime firstStart=OffsetDateTime.now().minusDays(10);
  jdbc.update("UPDATE training_classes SET total_sessions=5 WHERE id=?",classId);
  jdbc.update("UPDATE training_sessions SET starts_at=?,ends_at=? WHERE id=?",firstStart,firstStart.plusHours(2),UUID.fromString(session));
  var additionalSessions=new ArrayList<UUID>();
  for(int index=1;index<=4;index++){
   UUID additional=UUID.randomUUID();
   OffsetDateTime start=firstStart.plusDays(index==4?30:index);
   jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room) VALUES (?,?,?,?,?,?)",additional,classId,"Buổi học "+(index+1),start,start.plusHours(2),"P-01");
   additionalSessions.add(additional);
  }
  var attendance=view(lecturer,"ClassAttendance",session).path("related").path("attendance");
  assertEquals(1,attendance.size());assertEquals(student.id,attendance.get(0).path("studentId").asLong());
  var roster=view(lecturer,"ClassGradebook",cl).path("related").path("students");
  assertEquals(1,roster.size());assertEquals(enrollment,roster.get(0).path("enrollmentId").asText());assertEquals("ACTIVE",roster.get(0).path("status").asText());
  User unrelatedLecturer=create("LECTURER");
  read(unrelatedLecturer,"ClassAttendance",session).andExpect(status().isNotFound());
  read(unrelatedLecturer,"ClassGradebook",cl).andExpect(status().isNotFound());
  act(manager,"ClassAttendance",session,"save",values("version",0,"records",List.of(),"finalize",true)).andExpect(status().isForbidden());
  action(lecturer,"ClassAttendance",session,"save",values("version",0,"records",List.of(values("studentId",student.id,"status","PRESENT","note","")),"finalize",true));
  saveAttendanceAfterFinalization(session);
  List<String> attendanceStatuses=List.of("PRESENT","LATE","EXCUSED","PRESENT");
  for(int index=0;index<additionalSessions.size();index++){
   action(lecturer,"ClassAttendance",additionalSessions.get(index).toString(),"save",
     values("version",0,"records",List.of(values("studentId",student.id,"status",attendanceStatuses.get(index),"note","")),"finalize",true));
  }
  var records=List.of(values("enrollmentId",enrollment,"title",tests.get(0),"score",8),
    values("enrollmentId",enrollment,"title",tests.get(1),"score",8),values("enrollmentId",enrollment,"title",tests.get(2),"score",4));
  var grades=values("version",version(lecturer,"ClassGradebook",cl),"records",records);
  act(manager,"ClassGradebook",cl,"save",grades).andExpect(status().isForbidden());
  action(lecturer,"ClassGradebook",cl,"save",grades);
  assertEquals(0,view(student,"StudentResults",null).path("total").asInt());
  grades.put("version",version(lecturer,"ClassGradebook",cl));
  action(lecturer,"ClassGradebook",cl,"publish",grades);
  assertEquals(3,view(student,"StudentResults",null).path("total").asInt());
  assertEquals(0,view(otherStudent,"StudentResults",null).path("total").asInt());

  var confirmation=values("version",0,"changeNote","Đã rà soát hồ sơ và kết quả được công bố");
  var conditions=closureConditions(enrollment);
  var details=conditions.path("academicDetails");
  assertEquals(3,details.path("attendedSessions").asInt());
  assertEquals(5,details.path("totalSessions").asInt());
  assertFalse(details.path("attendanceComplete").asBoolean());
  assertFalse(details.path("attendancePass").asBoolean());
  assertFalse(details.path("finalPass").asBoolean());
  assertTrue(details.path("totalPass").asBoolean());
  assertFalse(conditions.path("academicFailureEligible").asBoolean());
  act(manager,"TrainingClosureDetail",enrollment,"academic-fail",confirmation).andExpect(status().isConflict());
  act(manager,"TrainingClosureDetail",enrollment,"academic",confirmation).andExpect(status().isConflict());

  OffsetDateTime lastStart=firstStart.plusDays(4);
  jdbc.update("UPDATE training_sessions SET starts_at=?,ends_at=? WHERE id=?",lastStart,lastStart.plusHours(2),additionalSessions.get(3));
  conditions=closureConditions(enrollment);
  assertFalse(conditions.path("academicEligible").asBoolean());
  assertTrue(conditions.path("academicFailureEligible").asBoolean());
  details=conditions.path("academicDetails");
  assertTrue(details.path("attendanceComplete").asBoolean());
  assertTrue(details.path("attendancePass").asBoolean());
  assertEquals(80.0,details.path("attendancePercent").asDouble());
  act(manager,"TrainingClosureDetail",enrollment,"academic",confirmation).andExpect(status().isConflict());

  records.forEach(record->record.put("score",5));
  grades.put("version",version(lecturer,"ClassGradebook",cl));
  action(lecturer,"ClassGradebook",cl,"publish",grades);
  details=closureConditions(enrollment).path("academicDetails");
  assertTrue(details.path("finalPass").asBoolean());
  assertFalse(details.path("totalPass").asBoolean());
  assertEquals(5.0,details.path("totalScore").asDouble());
  act(manager,"TrainingClosureDetail",enrollment,"academic",confirmation).andExpect(status().isConflict());

  records.get(2).put("score",5.01);
  grades.put("version",version(lecturer,"ClassGradebook",cl));
  action(lecturer,"ClassGradebook",cl,"save",grades);
  conditions=closureConditions(enrollment);
  assertTrue(conditions.path("academicDetails").path("finalScore").isNull());
  assertFalse(conditions.path("academicEligible").asBoolean());
  assertFalse(conditions.path("academicFailureEligible").asBoolean());
  grades.put("version",version(lecturer,"ClassGradebook",cl));
  action(lecturer,"ClassGradebook",cl,"publish",grades);
  conditions=closureConditions(enrollment);
  assertEquals(5.004,conditions.path("academicDetails").path("totalScore").asDouble());
  assertTrue(conditions.path("academicEligible").asBoolean());
  assertFalse(conditions.path("academicFailureEligible").asBoolean());
  act(manager,"TrainingClosureDetail",enrollment,"academic-fail",confirmation).andExpect(status().isConflict());
  assertFalse(conditions.path("ready").asBoolean());
  assertFalse(view(it,"TrainingClosureDetail",enrollment).path("related").path("conditions").has("academicDetails"));

  UUID asset=UUID.randomUUID(),loan=UUID.randomUUID();
  jdbc.update("INSERT INTO equipment_assets(id,code,name,status) VALUES (?,?,?,'ASSIGNED')",asset,"TS-"+asset,"Thiết bị thực hành");
  jdbc.update("INSERT INTO equipment_loans(id,asset_id,student_id,class_id,contract_id) VALUES (?,?,?,?,?)",loan,asset,student.id,classId,UUID.fromString(ct));
  action(manager,"TrainingClosureDetail",enrollment,"academic",confirmation);
  var confirmed=view(manager,"TrainingClosureDetail",enrollment);
  assertTrue(confirmed.path("related").path("conditions").path("academic").asBoolean());
  assertFalse(confirmed.path("related").path("conditions").path("ready").asBoolean());
  assertFalse(confirmed.path("record").path("academicCompletedAt").asText().isBlank());
  assertEquals("ACTIVE",confirmed.path("record").path("status").asText());
  assertEquals("RETURN_DUE",view(it,"ListAssetDetail",loan.toString()).path("record").path("returnStatus").asText());
  assertTrue(view(student,"ListNotification",null).path("items").toString().contains("Đã hoàn thành khóa học"));
  grades.put("version",version(lecturer,"ClassGradebook",cl));
  act(lecturer,"ClassGradebook",cl,"save",grades).andExpect(status().isBadRequest());
  act(manager,"TrainingClosureDetail_Ready",enrollment,"close",values("version",1)).andExpect(status().isConflict());
  action(it,"ListAssetDetail",loan.toString(),"confirm",values("version",1,"identityChecked",true,"receivedChecked",true,
    "returnedAt",OffsetDateTime.now().toString(),"returnCondition","Hoạt động tốt","returnEvidence","Biên bản thu hồi","hasIncident",false));
  assertTrue(closureConditions(enrollment).path("ready").asBoolean());
  act(it,"TrainingClosureDetail_Ready",enrollment,"close",values("version",1)).andExpect(status().isForbidden());action(manager,"TrainingClosureDetail_Ready",enrollment,"close",values("version",1));assertEquals("COMPLETED",view(manager,"TrainingClosureDetail",enrollment).path("record").path("closureOutcome").asText());
 }
 JsonNode closureConditions(String enrollment)throws Exception{return view(manager,"TrainingClosureDetail",enrollment).path("related").path("conditions");}
 void saveAttendanceAfterFinalization(String session)throws Exception{act(lecturer,"ClassAttendance",session,"save",values("version",1,"records",List.of(values("studentId",student.id,"status","ABSENT","note","")),"finalize",true)).andExpect(status().isConflict());}
 @Test void handoverRecoveryAndRefundAreAtomicScopedAndCannotBeRepeated()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);importAndConfirm(ct,"DEPOSIT",200);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();
  String req=save(lecturer,"AddEquipmentRequest",null,values("classId",cl,"studentId",student.id,"title","Bộ thiết bị thực hành","quantity",1,"requestType","INITIAL","neededOn","2026-11-15","description","Dùng trong buổi thực hành")).path("id").asText();
  write(student,"AddEquipmentRequest",null,Map.of()).andExpect(status().isForbidden());action(it,"EquipmentRequestDetail_IT",req,"prepare",values("version",0));action(it,"EquipmentRequestDetail_IT",req,"ready",values("version",1));
  String asset=save(it,"AddAsset",null,values("code","TS-"+UUID.randomUUID().toString().substring(0,8),"name","Bộ thực hành","serialNumber","SN1","purchasedOn","2026-11-01","recordedValue",500,"assetCondition","Tốt","accessories","Đầy đủ","status","AVAILABLE")).path("id").asText();
  String loan=save(it,"AssetHandover",req,values("version",2,"assets",List.of(values("id",asset)),"equipmentChecked",true,"studentChecked",true,"signedBy",student.fullName,"receiptEvidence","Biên bản ký","receivedAt","2026-11-07T01:00:00Z","returnDueAt","2027-02-15T01:00:00Z")).path("id").asText();
  read(student,"StudentAssetReceipt",loan).andExpect(status().isOk());read(otherStudent,"StudentAssetReceipt",loan).andExpect(status().isNotFound());
  write(accountant,"DepositRefund",ct,values("version",2)).andExpect(status().isConflict());
  var proposal=values("contractVersion",version(accountant,"DepositRefund",ct),"refundPercent",100,"reason","Thiết bị đầy đủ và hoạt động tốt","recipientName",student.fullName,"bankName","Vietcombank","accountNumber","0001234567");
  act(it,"ListAssetDetail",loan,"request-refund",proposal).andExpect(status().isConflict());
  action(it,"ListAssetDetail",loan,"confirm",values("version",0,"identityChecked",true,"receivedChecked",true,"returnedAt","2026-11-07T04:00:00Z","returnCondition","Đầy đủ và hoạt động tốt","returnEvidence","Biên bản thu hồi","hasIncident",false));
  act(it,"ListAssetDetail",loan,"request-refund",proposal).andExpect(status().isConflict());
  jdbc.update("UPDATE training_enrollments SET records_complete=TRUE,academic_completed_at=CURRENT_TIMESTAMP WHERE contract_id=?",UUID.fromString(ct));
  long oldVersion=version(accountant,"DepositRefund",ct);
  write(accountant,"DepositRefund",ct,values("version",oldVersion)).andExpect(status().isConflict());
  proposal.put("contractVersion",oldVersion);
  act(accountant,"ListAssetDetail",loan,"request-refund",proposal).andExpect(status().isForbidden());
  action(it,"ListAssetDetail",loan,"request-refund",proposal);
  assertEquals("PENDING",view(accountant,"DepositRefund",ct).path("record").path("refundStage").asText());
  assertFalse(view(student,"StudentAssetReceipt",loan).path("related").has("refundReview"));
  read(create("ACCOUNTANT"),"DepositRefund",ct).andExpect(status().isNotFound());
  assertTrue(view(accountant,"ListNotification",null).path("items").toString().contains("Phiếu đề nghị hoàn tiền cọc"));
  var refund=values("version",oldVersion,"recipient","Tên bị sửa","accountDetails","999999","bankReference",UUID.randomUUID().toString(),"evidence","Chứng từ hoàn","recipientVerified",true);
  write(accountant,"DepositRefund",ct,refund).andExpect(status().isConflict());
  refund.put("version",version(accountant,"DepositRefund",ct));
  var review=view(accountant,"DepositRefund",ct).path("related").path("refundReview").get(0);
  assertEquals("0001234567",review.path("accountNumber").asText());
  assertEquals(200,review.path("refundAmount").asInt());
  save(accountant,"DepositRefund",ct,refund);
  assertEquals(student.fullName,jdbc.queryForObject("SELECT recipient FROM deposit_settlements WHERE contract_id=?",String.class,UUID.fromString(ct)));
  assertEquals("Vietcombank · 0001234567",jdbc.queryForObject("SELECT account_details FROM deposit_settlements WHERE contract_id=?",String.class,UUID.fromString(ct)));
  assertEquals("SETTLED",view(accountant,"DepositRefund",ct).path("record").path("refundStage").asText());
  refund.put("version",version(accountant,"DepositRefund",ct));write(accountant,"DepositRefund",ct,refund).andExpect(status().isConflict());
  proposal.put("contractVersion",version(accountant,"DepositRefund",ct));
  act(it,"ListAssetDetail",loan,"request-refund",proposal).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals("AVAILABLE",view(it,"AssetDetail",asset).path("record").path("status").asText());
 }
 @Test void notificationsAreScopedAndProposedSendRightsRemainDenied()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);save(manager,"SendNotification",null,values("title","Lịch học mới","content","Kiểm tra thời khóa biểu","recipients",List.of(values("id",student.id)),"scheduledAt",""));
  var rows=view(student,"ListNotification",null).path("items");assertTrue(rows.size()>0);String id=rows.get(0).path("id").asText();read(otherStudent,"ViewNotification",id).andExpect(status().isNotFound());
  action(student,"ViewNotification",id,"read",Map.of());assertEquals("READ",view(student,"ViewNotification",id).path("record").path("status").asText());
  write(lecturer,"SendNotification",null,Map.of()).andExpect(status().isForbidden());saveNotificationToOutsider();
 }
 void saveNotificationToOutsider()throws Exception{write(manager,"SendNotification",null,values("title","Không được gửi","content","Kiểm tra","recipients",List.of(values("id",otherStudent.id)),"scheduledAt","")).andExpect(status().isNotFound());}
 @Test void notificationPreviewListsNewestForTheRecipientAndCountsUnreadAcrossPages()throws Exception{
  OffsetDateTime now=OffsetDateTime.now();
  for(int number=1;number<=26;number++){
   jdbc.update("INSERT INTO user_notifications(id,recipient_id,title,content,scheduled_at) VALUES (?,?,?,?,?)",
     new UUID(0,number),student.id,"Thông báo "+number,"Nội dung",now.minusMinutes(40-number));
  }
  jdbc.update("INSERT INTO user_notifications(id,recipient_id,title,content,scheduled_at) VALUES (?,?,?,?,?)",
    UUID.randomUUID(),student.id,"Chưa đến giờ gửi","Nội dung",now.plusDays(1));
  jdbc.update("INSERT INTO user_notifications(id,recipient_id,title,content,scheduled_at) VALUES (?,?,?,?,?)",
    UUID.randomUUID(),otherStudent.id,"Thông báo riêng","Nội dung",now.minusMinutes(1));
  var latest=view(student,"ListNotification",null);
  assertEquals(26,latest.path("total").asInt());assertEquals(25,latest.path("items").size());
  assertEquals("Thông báo 26",latest.path("items").get(0).path("title").asText());
  action(student,"ViewNotification",new UUID(0,26).toString(),"read",Map.of());
  assertEquals("READ",view(student,"ListNotification",null).path("items").get(0).path("status").asText());
  mvc.perform(get(path("ListNotification",null)).param("status","UNREAD").with(user(student.email).roles("STUDENT")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.items[0].title").value("Thông báo 25"));
 }
 @Test void evidenceFilesAreAuthenticatedValidatedAndPrivateUntilLinkedToAScopedRecord()throws Exception{
  MockMultipartFile file=new MockMultipartFile("file","bien-ban.pdf","application/pdf","%PDF-1.4\nTest evidence".getBytes(StandardCharsets.US_ASCII));
  String id=body(mvc.perform(multipart("/api/workspace/files").file(file).param("screen","RecordWithdrawal").with(user(manager.email).roles("MANAGER")).with(csrf())).andExpect(status().isOk()).andReturn()).path("id").asText();
  mvc.perform(get("/api/workspace/files/"+id)).andExpect(status().isUnauthorized());mvc.perform(get("/api/workspace/files/"+id).with(user(student.email).roles("STUDENT"))).andExpect(status().isNotFound());mvc.perform(get("/api/workspace/files/"+id).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
  String contract=createContract(createCourse());jdbc.update("UPDATE training_contracts SET signature_evidence=? WHERE id=?","file:"+id+":bien-ban.pdf",UUID.fromString(contract));
  mvc.perform(get("/api/workspace/files/"+id).with(user(student.email).roles("STUDENT"))).andExpect(status().isOk());mvc.perform(get("/api/workspace/files/"+id).with(user(otherStudent.email).roles("STUDENT"))).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","bad.html","text/html","<script>test</script>".getBytes())).param("screen","RecordWithdrawal").with(user(manager.email).roles("MANAGER")).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void screenRevocationAppliesToReadsAndWritesAndInvalidIdentifiersFailClearly()throws Exception{
  jdbc.update("DELETE FROM role_function WHERE role_id=1 AND function_code='AddCourse'");write(admin,"AddCourse",null,Map.of()).andExpect(status().isForbidden());read(admin,"AddCourse",null).andExpect(status().isForbidden());read(it,"AssetDetail","not-a-uuid").andExpect(status().isBadRequest());read(it,"UnknownScreen",null).andExpect(status().isNotFound());
 }
 Map<String,String> loanFixture()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);importAndConfirm(ct,"DEPOSIT",200);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();String en=view(manager,"AutoAssignmentReview",null).path("items").get(0).path("id").asText();
  String request=save(lecturer,"AddEquipmentRequest",null,values("classId",cl,"studentId",student.id,"title","Thiết bị học tập","quantity",1,"requestType","INITIAL","neededOn","2026-11-15","description","Học thực hành")).path("id").asText();
  action(it,"EquipmentRequestDetail_IT",request,"prepare",values("version",0));action(it,"EquipmentRequestDetail_IT",request,"ready",values("version",1));
  String asset=save(it,"AddAsset",null,values("code","TS-"+UUID.randomUUID().toString().substring(0,8),"name","Bộ thực hành","serialNumber","SN1","purchasedOn","2026-11-01","recordedValue",500,"assetCondition","Tốt","accessories","Đầy đủ","status","AVAILABLE")).path("id").asText();
  String loan=save(it,"AssetHandover",request,values("version",2,"assets",List.of(values("id",asset)),"equipmentChecked",true,"studentChecked",true,"signedBy",student.fullName,"receiptEvidence","Biên bản ký","receivedAt","2026-11-07T01:00:00Z","returnDueAt","2027-02-15T01:00:00Z")).path("id").asText();return Map.of("contract",ct,"class",cl,"enrollment",en,"asset",asset,"loan",loan);
 }
 @Test void approvedDamageOffsetsDepositOnceAndTechnicalAndFinancialDutiesStaySeparate()throws Exception{
  var f=loanFixture();String loan=f.get("loan"),ct=f.get("contract");
  jdbc.update("UPDATE training_enrollments SET academic_completed_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(f.get("enrollment")));
  String incident=action(it,"ListAssetDetail",loan,"confirm",values("version",0,"identityChecked",true,"receivedChecked",true,"returnedAt","2026-11-07T04:00:00Z","returnCondition","Thiếu phụ kiện","returnEvidence","Biên bản thu hồi","hasIncident",true)).path("id").asText();
  var technical=values("version",0,"description","Thiếu phụ kiện","technicalNote","Thiếu dây kết nối theo biên bản","compensationDue",50,"evidence","Minh chứng kỹ thuật");
  act(accountant,"AssetIncidentDetail",incident,"save",technical).andExpect(status().isForbidden());action(it,"AssetIncidentDetail",incident,"save",technical);
  write(accountant,"DepositRefund",ct,values("version",version(accountant,"DepositRefund",ct))).andExpect(status().isConflict());
  action(accountant,"AssetIncidentDetail_Accountant",incident,"settle",values("version",1,"approvedAmount",50,"approvalReference","Phê duyệt theo biên bản đã xác nhận","settlementMethod","DEPOSIT"));
  technical.put("version",2);act(it,"AssetIncidentDetail",incident,"save",technical).andExpect(status().isConflict());
  var context=view(it,"ListAssetDetail",loan).path("related").path("refundContext");
  var review=values("contractVersion",context.path("contractVersion").asLong(),"refundPercent",100,
    "reason","Thiếu phụ kiện đã duyệt khấu trừ 50 đồng","recipientName",student.fullName,"bankName","Ngân hàng kiểm thử","accountNumber","0012345678");
  act(it,"ListAssetDetail",loan,"request-refund",review).andExpect(status().isBadRequest());
  review.put("refundPercent",75);
  action(it,"ListAssetDetail",loan,"request-refund",review);
  var proposal=view(accountant,"DepositRefund",ct).path("related").path("refundReview").get(0);
  assertEquals(75,proposal.path("refundPercent").asInt());
  assertEquals("0012345678",proposal.path("accountNumber").asText());
  save(accountant,"DepositRefund",ct,values("version",version(accountant,"DepositRefund",ct),"recipient","Không thay người nhận trên phiếu","accountDetails","Không thay tài khoản trên phiếu","bankReference",UUID.randomUUID().toString(),"evidence","Chứng từ hoàn cọc","recipientVerified",true));
  assertEquals(150,jdbc.queryForObject("SELECT refund_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals(50,jdbc.queryForObject("SELECT deduction_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals("RESOLVED",view(accountant,"AssetIncidentDetail_Accountant",incident).path("record").path("status").asText());
  assertEquals(student.fullName,jdbc.queryForObject("SELECT recipient FROM deposit_settlements WHERE contract_id=?",String.class,UUID.fromString(ct)));
 }
 @Test void zeroPercentProposalClosesTheDepositWithoutARefundTransaction()throws Exception{
  var f=loanFixture();String ct=f.get("contract"),loan=f.get("loan"),enrollment=f.get("enrollment");
  save(manager,"RecordWithdrawal",enrollment,values("version",0,"withdrawnOn","2026-11-07","withdrawalReason","Theo hồ sơ xác nhận","withdrawalEvidence","Biên bản nghỉ học"));
  action(it,"Recovery_Withdrawal",loan,"confirm",values("version",1,"identityChecked",true,"receivedChecked",true,"returnedAt","2026-11-07T04:00:00Z","returnCondition","Thiết bị hỏng nặng","returnEvidence","Biên bản thu hồi","hasIncident",false));
  var context=view(it,"Recovery_Withdrawal",loan).path("related").path("refundContext");
  action(it,"Recovery_Withdrawal",loan,"request-refund",values("contractVersion",context.path("contractVersion").asLong(),"refundPercent",0,
    "reason","Thiết bị hỏng nặng theo biên bản thu hồi","recipientName",student.fullName,"bankName","Ngân hàng kiểm thử","accountNumber","0012345678"));
  save(accountant,"DepositRefund",ct,values("version",version(accountant,"DepositRefund",ct),"bankReference",UUID.randomUUID().toString(),
    "evidence","Biên bản đối chiếu không phát sinh chuyển khoản","recipientVerified",true));
  UUID contractId=UUID.fromString(ct);
  assertEquals(0,jdbc.queryForObject("SELECT refund_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,contractId));
  assertEquals(200,jdbc.queryForObject("SELECT deduction_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,contractId));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=? AND kind='REFUND'",Integer.class,contractId));
  assertTrue(view(manager,"TrainingClosureDetail_Withdrawal",enrollment).path("related").path("conditions").path("ready").asBoolean());
 }
 @Test void withdrawalIsNotCourseCompletionAndCannotCloseWithOutstandingEquipment()throws Exception{
  var f=loanFixture();String enrollment=f.get("enrollment");save(manager,"RecordWithdrawal",enrollment,values("version",0,"withdrawnOn","2026-11-07","withdrawalReason","Theo hồ sơ xác nhận","withdrawalEvidence","Biên bản nghỉ học"));
  var detail=view(manager,"TrainingClosureDetail_Withdrawal",enrollment);assertEquals("WITHDRAWN",detail.path("record").path("status").asText());assertFalse(detail.path("related").path("conditions").path("ready").asBoolean());assertEquals("RETURN_DUE",view(it,"Recovery_Withdrawal",f.get("loan")).path("record").path("returnStatus").asText());act(manager,"TrainingClosureDetail_Withdrawal",enrollment,"close",values("version",1)).andExpect(status().isConflict());
 }
 @Test void aFailedLearnerCanRegisterOnceAtSeventyPercentAfterReturningEquipment()throws Exception{
  var fixture=loanFixture();
  String oldContract=fixture.get("contract"),oldClass=fixture.get("class"),enrollment=fixture.get("enrollment"),loan=fixture.get("loan");
  UUID oldContractId=UUID.fromString(oldContract),oldClassId=UUID.fromString(oldClass),enrollmentId=UUID.fromString(enrollment);
  var academicReview=values("version",version(manager,"TrainingClosureDetail",enrollment),"changeNote","Đã rà soát kết quả công bố");
  act(manager,"TrainingClosureDetail",enrollment,"academic-fail",academicReview).andExpect(status().isConflict());

  OffsetDateTime previousDay=OffsetDateTime.now().minusDays(1);
  UUID session=jdbc.queryForObject("SELECT id FROM training_sessions WHERE class_id=?",UUID.class,oldClassId);
  jdbc.update("UPDATE training_sessions SET starts_at=?,ends_at=?,attendance_finalized=TRUE WHERE id=?",previousDay,previousDay.plusHours(2),session);
  jdbc.update("INSERT INTO training_attendance(id,session_id,student_id,status) VALUES (?,?,?,'PRESENT')",UUID.randomUUID(),session,student.id);
  List<String> subjects=List.of("Điểm kiểm tra lần 1","Điểm kiểm tra lần 2","Điểm kiểm tra cuối kỳ");
  for(int index=0;index<subjects.size();index++)
   jdbc.update("INSERT INTO training_grades(id,enrollment_id,title,score,published_at) VALUES (?,?,?,?,?)",UUID.randomUUID(),enrollmentId,subjects.get(index),index==2?4:8,previousDay);

  assertFalse(closureConditions(enrollment).path("academicEligible").asBoolean());
  action(manager,"TrainingClosureDetail",enrollment,"academic-fail",academicReview);
  assertEquals("RETURN_DUE",view(it,"ListAssetDetail",loan).path("record").path("returnStatus").asText());
  assertEquals(0,view(student,"StudentRetake",null).path("items").size());
  assertFalse(view(student,"ListNotification",null).path("items").toString().contains("StudentRetake"));

  UUID course=jdbc.queryForObject("SELECT course_id FROM training_contracts WHERE id=?",UUID.class,oldContractId);
  LocalDate retakeStart=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(30);
  String targetClass=ClassPlanTestFixtures.withClass(jdbc,manager.id,values("courseId",course,"consultationId","fixture","startsOn",retakeStart)).get("classId").toString();
  action(it,"ListAssetDetail",loan,"confirm",values("version",version(it,"ListAssetDetail",loan),"identityChecked",true,"receivedChecked",true,
    "returnedAt","2026-11-07T04:00:00Z","returnCondition","Đầy đủ phụ kiện","returnEvidence","Biên bản đã thu hồi","hasIncident",false));
  var available=view(student,"StudentRetake",null).path("items");
  assertEquals(1,available.size());
  String offer=available.get(0).path("id").asText();
  assertTrue(view(student,"ListNotification",null).path("items").toString().contains(offer));
  read(otherStudent,"StudentRetake",offer).andExpect(status().isNotFound());
  read(manager,"StudentRetake",offer).andExpect(status().isForbidden());
  var detail=view(student,"StudentRetake",offer);
  assertTrue(detail.path("canWrite").asBoolean());
  assertEquals(targetClass,detail.path("related").path("classes").get(0).path("id").asText());
  assertEquals(700,detail.path("record").path("retakeTuition").asInt());
  act(student,"StudentRetake",offer,"register",values("accepted",false,"classId",targetClass)).andExpect(status().isBadRequest());
  act(otherStudent,"StudentRetake",offer,"register",values("accepted",true,"classId",targetClass)).andExpect(status().isNotFound());
  act(student,"StudentRetake",offer,"register",values("accepted",true,"classId",oldClass)).andExpect(status().isBadRequest());

  var registration=values("accepted",true,"classId",targetClass);
  var registered=action(student,"StudentRetake",offer,"register",registration);
  String retake=registered.path("id").asText();
  assertEquals("TuitionDetail_Student",registered.path("screen").asText());
  assertNotEquals(oldContract,retake);
  assertEquals(retake,action(student,"StudentRetake",offer,"register",registration).path("id").asText());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE student_id=?",Integer.class,student.id));
  var contract=view(student,"TuitionDetail_Student",retake).path("record");
  assertEquals(targetClass,contract.path("classId").asText());
  assertEquals(retakeStart.toString(),contract.path("startsOn").asText());
  assertEquals(700,contract.path("tuitionDue").asInt());
  assertEquals(200,contract.path("depositDue").asInt());
  assertEquals("Điều khoản theo hồ sơ xác nhận",contract.path("terms").asText());
  assertEquals("RETAKE_ACCEPTANCE",contract.path("signatureMethod").asText());
  assertNotEquals(view(student,"TuitionDetail_Student",oldContract).path("record").path("paymentReference"),contract.path("paymentReference"));
  mvc.perform(get("/api/workspace/student-payments/contracts/"+retake).with(user(student.email).roles("STUDENT")))
    .andExpect(status().isOk()).andExpect(jsonPath("$.canSubmit").value(true)).andExpect(jsonPath("$.contract.tuitionRemaining").value(700));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(retake)));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",Integer.class,oldContractId));
  importAndConfirm(retake,"TUITION",700);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND class_id=? AND status='ACTIVE'",Integer.class,UUID.fromString(retake),UUID.fromString(targetClass)));
 }
 @Test void dashboardsApplyTheSameOwnershipAsBusinessScreens()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);User otherAccountant=create("ACCOUNTANT");
  for(User u:List.of(otherManager,otherHr,otherAccountant)){
   var result=body(mvc.perform(get("/api/dashboard").with(user(u.email).roles(u.getRoleCode()))).andExpect(status().isOk()).andReturn());assertFalse(result.path("panels").toString().contains(student.fullName));
   for(JsonNode metric:result.path("stats"))assertEquals(0,metric.path("value").asDouble(),u.getRoleCode()+":"+metric.path("key").asText());
  }
 }
 @Test void signedContractsAndOwnedAdmissionsCannotBeEditedByOtherStaff()throws Exception{
  String ct=createContract(createCourse());String admission=view(manager,"TrainingContractDetail",ct).path("record").path("consultationId").asText();assertEquals("CONTRACTED",view(hr,"ListAdmissionApplicationDetail",admission).path("record").path("status").asText());read(otherHr,"ListAdmissionApplicationDetail",admission).andExpect(status().isNotFound());write(manager,"EditTrainingContract",ct,values("version",1)).andExpect(status().isConflict());
 }
 @Test void zeroTuitionRequiresAnAccountantConfirmationAndDoesNotInventAZeroAmountPayment()throws Exception{
  String ct=createContract(createCourse());jdbc.update("UPDATE training_contracts SET tuition_due=0 WHERE id=?",UUID.fromString(ct));
  act(hr,"TuitionDetail",ct,"activate-zero",values("version",1,"changeNote","Miễn phí")).andExpect(status().isForbidden());action(accountant,"TuitionDetail",ct,"activate-zero",values("version",1,"changeNote","Căn cứ hợp đồng miễn học phí đã ký"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND student_id=? AND status='ACTIVE'",Integer.class,UUID.fromString(ct),student.id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
 }
 String registration(String state)throws Exception{
  String id=body(mvc.perform(post("/api/public/consultations").with(csrf()).contentType("application/json").content(json.writeValueAsString(values("fullName",student.fullName,"phone","0912345678","email",student.email,"contactTime","any","program_name","Khóa thực hành")))).andExpect(status().isCreated()).andReturn()).path("id").asText();
  if(!state.equals("NEW"))save(hr,"AdmissionConsultation",id,values("version",0,"channel","PHONE","status",state.startsWith("PROFILE_")?"DOCUMENT_APPOINTMENT":state,"notes","Đã xác minh hồ sơ","appointmentAt",state.equals("NEEDS_COUNSELING")?"2026-12-15T01:00:00Z":"","documentDueOn",state.equals("DOCUMENT_APPOINTMENT")||state.startsWith("PROFILE_")?"2026-12-15":"","discontinuationReason",state.equals("NOT_PROCEEDING")?"Không còn nhu cầu":""));
  if(state.startsWith("PROFILE_"))jdbc.update("UPDATE consultation_followups SET status=?,forwarded_at=CURRENT_TIMESTAMP,forwarded_by=? WHERE consultation_id=?",state,hr.id,UUID.fromString(id));return id;
 }
 Map<String,Object> contractInput(String course,String admission){return values("consultationId",admission,"courseId",course,"studentId",student.id,"startsOn","2026-12-15","paymentDueOn","2026-12-14","tuitionDue",1000,"depositDue",200,"terms","Điều khoản đã thỏa thuận","paymentSchedule","");}
 @Test void receivedProfilesAreSelectableForContractsBeforeAccounts()throws Exception{
  String course=createCourse();
  for(String state:List.of("NEW","NEEDS_COUNSELING","DOCUMENT_APPOINTMENT","NOT_PROCEEDING")){
   String id=registration(state);assertFalse(view(manager,"AddTrainingContract",null).path("options").path("admissions").toString().contains(id));
   write(manager,"AddTrainingContract",null,contractInput(course,id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.consultationId").exists());
  }
  String id=registration("PROFILE_PENDING");assertTrue(view(manager,"AddTrainingContract",null).path("options").path("admissions").toString().contains(id));
  write(hr,"AdmissionConsultation",id,values("version",0,"channel","PHONE","status","CONTRACTED","notes","Không được tự chuyển trạng thái")).andExpect(status().isConflict());
  String ct=save(manager,"AddTrainingContract",null,contractInput(course,id)).path("id").asText();
  assertEquals(manager.id,jdbc.queryForObject("SELECT created_by FROM training_contracts WHERE id=?",Long.class,UUID.fromString(ct)));
  assertEquals("CONTRACTED",view(hr,"ListAdmissionApplicationDetail",id).path("record").path("status").asText());
  assertFalse(view(manager,"AddTrainingContract",null).path("options").path("admissions").toString().contains(id));
  write(manager,"AddTrainingContract",null,contractInput(course,id)).andExpect(status().isBadRequest());
 }
 @Test void managerCanEditAnOwnedDraftWhileHrAndOtherManagersCannotManageItOrSkipSignatureReview()throws Exception{
  String course=createCourse(),admission=registration("PROFILE_PENDING"),ct=save(manager,"AddTrainingContract",null,contractInput(course,admission)).path("id").asText();
  assertEquals(1,view(manager,"ListTrainingContract",null).path("total").asInt());assertEquals(0,view(accountant,"ListTrainingContract",null).path("total").asInt());
  assertTrue(view(manager,"EditTrainingContract",ct).path("options").path("admissions").toString().contains(admission));
  var edit=contractInput(course,admission);edit.put("version",0);edit.put("terms","Điều khoản đã cập nhật");save(manager,"EditTrainingContract",ct,edit);
  for(String code:List.of("ListTrainingContract","AddTrainingContract","EditTrainingContract","TrainingContractDetail","ContractSignature"))read(hr,code,code.startsWith("List")||code.startsWith("Add")?null:ct).andExpect(status().isForbidden());
  write(hr,"AddTrainingContract",null,contractInput(course,admission)).andExpect(status().isForbidden());write(hr,"EditTrainingContract",ct,edit).andExpect(status().isForbidden());write(hr,"ContractSignature",ct,Map.of()).andExpect(status().isForbidden());
  read(otherManager,"TrainingContractDetail",ct).andExpect(status().isNotFound());write(otherManager,"EditTrainingContract",ct,edit).andExpect(status().isNotFound());
  jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (7,'AddTrainingContract')");write(hr,"AddTrainingContract",null,Map.of()).andExpect(status().isForbidden());
  write(manager,"ContractSignature",ct,values("version",1,"signedOn","2026-11-01","signatureMethod","PAPER","signatureEvidence","Biên bản ký","accountantId",accountant.id,"handoverNote","Bàn giao")).andExpect(status().isConflict());
  assertEquals(0,view(accountant,"ListTrainingContract",null).path("total").asInt());assertEquals(0,view(hr,"PaymentStatus_Admissions",null).path("total").asInt());
 }
 @Test void contactStatusIsRecheckedAtSubmitAndManagersOnlySelectTheirOwnCourses()throws Exception{
  String course=createCourse(),id=registration("PROFILE_PENDING");assertTrue(view(manager,"AddTrainingContract",null).path("options").path("admissions").toString().contains(id));
  write(otherManager,"AddTrainingContract",null,contractInput(course,id)).andExpect(status().isNotFound());
  write(hr,"AdmissionConsultation",id,values("version",0,"channel","PHONE","status","NOT_PROCEEDING","notes","Hồ sơ không tiếp tục")).andExpect(status().isConflict());
  jdbc.update("UPDATE consultation_followups SET status='NOT_PROCEEDING' WHERE consultation_id=?",UUID.fromString(id));
  write(manager,"AddTrainingContract",null,contractInput(course,id)).andExpect(status().isBadRequest());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=?",Integer.class,UUID.fromString(id)));
 }
 @Test void contractPermissionsCanBeRevokedAndHrCannotUploadSignatureEvidence()throws Exception{
  jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='AddTrainingContract'");read(manager,"AddTrainingContract",null).andExpect(status().isForbidden());write(manager,"AddTrainingContract",null,Map.of()).andExpect(status().isForbidden());
  jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (7,'ContractSignature')");
  mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","signed.pdf","application/pdf","%PDF-1.4\nEvidence".getBytes())).param("screen","ContractSignature").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isForbidden());
 }
 @Test void changingTheRegistrationOnADraftRestoresThePreviousReceivedProfile()throws Exception{
  String course=createCourse(),first=registration("PROFILE_PENDING"),second=registration("PROFILE_PENDING");
  String ct=save(manager,"AddTrainingContract",null,contractInput(course,first)).path("id").asText();
  var edit=contractInput(course,second);edit.put("version",0);save(manager,"EditTrainingContract",ct,edit);
  assertEquals("PROFILE_PENDING",view(hr,"ListAdmissionApplicationDetail",first).path("record").path("status").asText());
  assertEquals("CONTRACTED",view(hr,"ListAdmissionApplicationDetail",second).path("record").path("status").asText());
  String eligible=view(manager,"AddTrainingContract",null).path("options").path("admissions").toString();assertTrue(eligible.contains(first));assertFalse(eligible.contains(second));
 }
 @Test void paymentInstructionsPersistUpdatesAndRemainScopedWithVersionAndCsrfProtection()throws Exception{
  String contract=createContract(createCourse());UUID contractId=UUID.fromString(contract);User otherAccountant=create("ACCOUNTANT");
  var input=values("content","Chuyển khoản theo mã hợp đồng đã được xác minh");
  mvc.perform(post(path("PaymentInstruction",contract)).with(user(accountant.email).roles("ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
  write(manager,"PaymentInstruction",contract,input).andExpect(status().isForbidden());
  write(otherAccountant,"PaymentInstruction",contract,input).andExpect(status().isNotFound());
  write(accountant,"PaymentInstruction",contract,values("content"," ")).andExpect(status().isBadRequest());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM payment_instructions WHERE contract_id=?",Integer.class,contractId));
  assertEquals("TuitionDetail",save(accountant,"PaymentInstruction",contract,input).path("screen").asText());
  var instruction=view(accountant,"PaymentInstruction",contract).path("related").path("instruction").get(0);
  assertEquals(input.get("content"),instruction.path("content").asText());assertEquals(0,instruction.path("version").asLong());
  assertEquals(input.get("content"),view(student,"TuitionDetail_Student",contract).path("related").path("instruction").get(0).path("content").asText());
  read(otherStudent,"TuitionDetail_Student",contract).andExpect(status().isNotFound());read(otherAccountant,"PaymentInstruction",contract).andExpect(status().isNotFound());
  var update=values("version",0,"content","Hướng dẫn mới đã đối chiếu tài khoản nhận");save(accountant,"PaymentInstruction",contract,update);
  write(accountant,"PaymentInstruction",contract,values("version",0,"content","Ghi đè dữ liệu cũ")).andExpect(status().isConflict());
  var current=view(student,"TuitionDetail_Student",contract).path("related").path("instruction");assertEquals(1,current.size());assertEquals(update.get("content"),current.get(0).path("content").asText());assertEquals(1,current.get(0).path("version").asLong());
  assertEquals(accountant.id,jdbc.queryForObject("SELECT created_by FROM payment_instructions WHERE contract_id=?",Long.class,contractId));
  jdbc.update("DELETE FROM role_function WHERE role_id=4 AND function_code='PaymentInstruction'");
  write(accountant,"PaymentInstruction",contract,values("version",1,"content","Không còn quyền")).andExpect(status().isForbidden());
  assertEquals(update.get("content"),jdbc.queryForObject("SELECT content FROM payment_instructions WHERE contract_id=?",String.class,contractId));
 }
 String maintenanceAsset()throws Exception{return save(it,"AddAsset",null,values("code","BT-"+UUID.randomUUID().toString().substring(0,8),"name","Thiết bị cần bảo trì","purchasedOn","2026-11-01","recordedValue",500,"assetCondition","Cần kiểm tra","status","AVAILABLE")).path("id").asText();}
 Map<String,Object> maintenanceInput(String asset){return values("assetId",asset,"ownerId",it.id,"source","Kiểm tra định kỳ","maintenanceType","Bảo dưỡng","discoveredOn","2026-11-07","description","Kiểm tra và thay dây kết nối","dueOn","2026-11-14","note","Theo phiếu kỹ thuật");}
 @Test void maintenanceHistoryAndAssetStateCompleteTogetherWithRoleAndVersionChecks()throws Exception{
  String asset=maintenanceAsset();UUID assetId=UUID.fromString(asset);var input=maintenanceInput(asset);
  write(manager,"AssetMaintenance",asset,input).andExpect(status().isForbidden());
  var invalidOwner=new LinkedHashMap<>(input);invalidOwner.put("ownerId",manager.id);write(it,"AssetMaintenance",asset,invalidOwner).andExpect(status().isBadRequest());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM asset_maintenance WHERE asset_id=?",Integer.class,assetId));
  assertEquals("AVAILABLE",view(it,"AssetDetail",asset).path("record").path("status").asText());
  save(it,"AssetMaintenance",asset,input);
  var detail=view(it,"AssetDetail",asset);assertEquals("MAINTENANCE",detail.path("record").path("status").asText());assertEquals(1,detail.path("record").path("version").asLong());
  var history=detail.path("related").path("maintenance");assertEquals(1,history.size());assertEquals("OPEN",history.get(0).path("status").asText());assertEquals(it.fullName,history.get(0).path("ownerName").asText());assertEquals(input.get("description"),history.get(0).path("description").asText());
  read(student,"AssetMaintenance",asset).andExpect(status().isForbidden());
  act(accountant,"AssetDetail",asset,"complete-maintenance",values("version",1,"assetCondition","Đã sửa")).andExpect(status().isForbidden());
  act(it,"AssetDetail",asset,"complete-maintenance",values("version",0,"assetCondition","Đã sửa")).andExpect(status().isConflict());
  action(it,"AssetDetail",asset,"complete-maintenance",values("version",1,"assetCondition","Đã kiểm tra, hoạt động tốt"));
  var completed=view(it,"AssetDetail",asset);assertEquals("AVAILABLE",completed.path("record").path("status").asText());assertEquals("Đã kiểm tra, hoạt động tốt",completed.path("record").path("assetCondition").asText());assertEquals(2,completed.path("record").path("version").asLong());
  assertEquals("COMPLETED",completed.path("related").path("maintenance").get(0).path("status").asText());assertEquals(1,completed.path("related").path("maintenance").get(0).path("version").asLong());
  act(it,"AssetDetail",asset,"complete-maintenance",values("version",2,"assetCondition","Lặp lại")).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM asset_maintenance WHERE asset_id=?",Integer.class,assetId));
 }
 @Test void assignedAssetsAndRevokedMaintenanceRightsCannotCreateMaintenanceHistory()throws Exception{
  var fixture=loanFixture();String assigned=fixture.get("asset");
  write(it,"AssetMaintenance",assigned,maintenanceInput(assigned)).andExpect(status().isConflict());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM asset_maintenance WHERE asset_id=?",Integer.class,UUID.fromString(assigned)));
  assertEquals("ASSIGNED",view(it,"AssetDetail",assigned).path("record").path("status").asText());
  String available=maintenanceAsset();jdbc.update("DELETE FROM role_function WHERE role_id=5 AND function_code='AssetMaintenance'");
  write(it,"AssetMaintenance",available,maintenanceInput(available)).andExpect(status().isForbidden());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM asset_maintenance WHERE asset_id=?",Integer.class,UUID.fromString(available)));
  assertEquals("AVAILABLE",view(it,"AssetDetail",available).path("record").path("status").asText());
 }
}
