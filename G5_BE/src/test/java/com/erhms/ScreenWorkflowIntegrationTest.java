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
 ResultActions write(User user,String code,String id,Map<String,Object> input)throws Exception{return mvc.perform(post(path(code,id)).with(user(user.email).roles(user.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
 JsonNode save(User user,String code,String id,Map<String,Object> input)throws Exception{return body(write(user,code,id,input).andExpect(status().isOk()).andReturn());}
 ResultActions act(User user,String code,String id,String action,Map<String,Object> input)throws Exception{return mvc.perform(post(path(code,id)+"/actions/"+action).with(user(user.email).roles(user.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
 JsonNode action(User user,String code,String id,String action,Map<String,Object> input)throws Exception{return body(act(user,code,id,action,input).andExpect(status().isOk()).andReturn());}
 Map<String,Object> values(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
 long version(User u,String code,String id)throws Exception{return view(u,code,id).path("record").path("version").asLong();}
 String createCourse()throws Exception{return save(manager,"AddCourse",null,values("name","Khóa thực hành","code","KH-"+UUID.randomUUID().toString().substring(0,8),"professionId",PROFESSION,"durationMonths",3,"totalSessions",2,"classCapacity",10,"tuitionFee",1000,"objectives","Kỹ năng thực hành","entryRequirements","Theo hồ sơ","status","OPEN","assessments",List.of(values("title","Thực hành","weight",100)),"lecturers",List.of(values("id",lecturer.id)))).path("id").asText();}
 String createContract(String course)throws Exception{
  String admission=body(mvc.perform(post("/api/public/consultations").with(csrf()).contentType("application/json").content(json.writeValueAsString(values("fullName",student.fullName,"phone","0912345678","email",student.email,"contactTime","any","program_name","Khóa thực hành")))).andExpect(status().isCreated()).andReturn()).path("id").asText();
  save(hr,"AdmissionConsultation",admission,values("version",0,"channel","PHONE","status","CONTACTED","notes","Đã tư vấn thông tin khóa học","appointmentAt",""));
  String contract=save(hr,"AddTrainingContract",null,values("consultationId",admission,"courseId",course,"studentId",student.id,"startsOn","2026-11-15","paymentDueOn","2026-11-14","tuitionDue",1000,"depositDue",200,"terms","Điều khoản theo hồ sơ xác nhận","paymentSchedule","")).path("id").asText();
  save(hr,"ContractSignature",contract,values("version",0,"signedOn","2026-11-01","signatureMethod","PAPER","signatureEvidence","Biên bản ký đã xác minh","accountantId",accountant.id,"handoverNote","Bàn giao đào tạo và kế toán"));return contract;
 }
 String importAndConfirm(String contract,String kind,int amount)throws Exception{
  String id=action(accountant,"ListPayment","new","import",values("contractId",contract,"kind",kind,"amount",amount,"bankReference",UUID.randomUUID().toString(),"payerName",student.fullName,"bankId",BANK,"memo","Đối chiếu theo hợp đồng","evidence","Chứng từ đã kiểm tra")).path("id").asText();
  action(accountant,"PaymentDetail",id,"confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã khớp chứng từ"));return id;
 }
 @Test void everyImportedBusinessScreenHasAWorkingEndpointAndDefaultsMatchTheWorkbook()throws Exception{
  var source=json.readValue(new ClassPathResource("workspace/screen-catalog.json").getInputStream(),new TypeReference<List<Map<String,Object>>>(){});int checked=0;
  for(var screen:source){String code=(String)screen.get("code");if(screen.containsKey("masterType")||Boolean.TRUE.equals(screen.get("public"))||!code.equals(screen.get("functionCode")))continue;
   @SuppressWarnings("unchecked")List<String> permitted=(List<String>)screen.get("roles");
   for(String role:actors.keySet()){
    var request=read(actors.get(role),code,null);
    if(permitted.contains(role))request.andExpect(status().isOk());else request.andExpect(status().isForbidden());
   }checked++;
  }assertEquals(80,checked);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM role_function WHERE function_code='SendNotification' AND role_id IN(1,2,7)",Integer.class));
 }
 @Test void publicCourseCatalogueOnlyExposesPublishedContentAndNoInternalAssignment()throws Exception{
  String course=createCourse();var published=body(mvc.perform(get("/api/public/training-courses/"+course)).andExpect(status().isOk()).andReturn());assertFalse(published.has("managerId"));assertFalse(published.has("version"));
  jdbc.update("UPDATE training_courses SET status='DRAFT' WHERE id=?",UUID.fromString(course));mvc.perform(get("/api/public/training-courses/"+course)).andExpect(status().isNotFound());
 }
 @Test void scopedManagementAndTechnicalAdminNeverInheritBusinessActions()throws Exception{
  String course=createCourse();read(otherManager,"CourseDetail",course).andExpect(status().isNotFound());read(admin,"CourseDetail",course).andExpect(status().isForbidden());
  jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (1,'AddCourse')");write(admin,"AddCourse",null,Map.of()).andExpect(status().isForbidden());
  mvc.perform(post(path("AddCourse",null)).with(user(manager.email).roles("MANAGER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void confirmationCreatesOneScopedEnrollmentAndDuplicateSignalsNeverCollectTwice()throws Exception{
  String ct=createContract(createCourse());String payment=importAndConfirm(ct,"TUITION",1000);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
  act(accountant,"PaymentDetail",payment,"confirm",values("version",1,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Lặp lại")).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=? AND status='CONFIRMED'",Integer.class,UUID.fromString(ct)));
  String reference=view(accountant,"PaymentDetail",payment).path("record").path("bankReference").asText();JsonNode duplicate=action(accountant,"ListPayment","new","import",values("contractId",ct,"bankReference",reference));assertEquals("PaymentDetail_Duplicate",duplicate.path("screen").asText());assertEquals(payment,duplicate.path("id").asText());
  assertEquals(1,view(manager,"AutoAssignmentReview",null).path("total").asInt());assertEquals(0,view(otherManager,"AutoAssignmentReview",null).path("total").asInt());
  read(otherStudent,"TuitionDetail_Student",ct).andExpect(status().isNotFound());read(student,"TuitionDetail_Student",ct).andExpect(status().isOk());
  assertFalse(view(hr,"TrainingContractDetail",ct).path("canWrite").asBoolean());
 }
 @Test void mismatchedOrOverpaidTransactionsRemainUnconfirmedAndDoNotAssignStudents()throws Exception{
  String ct=createContract(createCourse());String payment=action(accountant,"ListPayment","new","import",values("contractId",ct,"kind","TUITION","amount",2000,"bankReference",UUID.randomUUID().toString(),"payerName",student.fullName,"bankId",BANK,"memo","Sai số tiền","evidence","Kiểm thử")).path("id").asText();
  act(accountant,"PaymentDetail",payment,"confirm",values("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã kiểm tra")).andExpect(status().isConflict());
  action(accountant,"PaymentDetail",payment,"mismatch",values("version",0,"memo","Đang xác minh khoản vượt nghĩa vụ"));
  act(accountant,"PaymentDetail",payment,"confirm",values("version",1)).andExpect(status().isConflict());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
 }
 @Test void schedulesBlockOverlapsForRoomClassAndLecturerAndRejectStaleEdits()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();
  var session=values("classId",cl,"title","Thực hành","startsAt","2026-11-15T01:00:00Z","endsAt","2026-11-15T03:00:00Z","classroomId",ROOM,"changeNote","Lập lịch");String id=save(manager,"TimeTable_EditSession",null,session).path("id").asText();
  write(manager,"TimeTable_EditSession",null,session).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
  jdbc.update("UPDATE m_classroom SET name='Phòng đã đổi tên',name_key='phòng đã đổi tên' WHERE id=?",UUID.fromString(ROOM));
  assertEquals("Phòng đã đổi tên",view(manager,"TimeTable_EditSession",id).path("record").path("room").asText());
  write(manager,"TimeTable_EditSession",null,session).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIMETABLE_CONFLICT"));
  session.put("version",9);write(manager,"TimeTable_EditSession",id,session).andExpect(status().isConflict());
  write(lecturer,"TimeTable_EditSession",id,session).andExpect(status().isForbidden());
  assertEquals(1,view(student,"TimeTable_Student",null).path("total").asInt());assertEquals(0,view(otherStudent,"TimeTable_Student",null).path("total").asInt());
  jdbc.update("INSERT INTO training_enrollments(id,class_id,student_id,status) VALUES (?,?,?,'ACTIVE')",UUID.randomUUID(),UUID.fromString(cl),otherStudent.id);
  assertEquals(1,view(student,"TimeTable_Student",id).path("related").path("attendance").size());
  mvc.perform(get(path("TimeTable_Manager",null)).param("from","2026-11-15").param("to","2026-11-15").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.stats[0].value").value(1));
  mvc.perform(get(path("TimeTable_Manager",null)).param("from","2026-11-16").param("to","2026-11-22").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.stats[0].value").value(0));
  assertEquals(0,view(manager,"TimeTable_Conflict",null).path("total").asInt());
  jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,room) VALUES (?,?,?,? ,?,?)",UUID.randomUUID(),UUID.fromString(cl),"Lịch cũ đang xung đột",OffsetDateTime.parse("2026-11-15T02:00:00Z"),OffsetDateTime.parse("2026-11-15T04:00:00Z"),"P-01");
  assertEquals(2,view(manager,"TimeTable_Conflict",null).path("total").asInt());
 }
 @Test void gradePublicationAttendanceAndClosureRequireTheResponsibleRolesAndCompleteData()throws Exception{
  String ct=createContract(createCourse());jdbc.update("UPDATE training_contracts SET deposit_due=0 WHERE id=?",UUID.fromString(ct));importAndConfirm(ct,"TUITION",1000);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();String enrollment=view(manager,"AutoAssignmentReview",null).path("items").get(0).path("id").asText();
  String session=save(manager,"TimeTable_EditSession",null,values("classId",cl,"title","Buổi thực hành","startsAt","2026-11-15T01:00:00Z","endsAt","2026-11-15T03:00:00Z","classroomId",ROOM,"changeNote","Lập lịch")).path("id").asText();
  act(manager,"ClassAttendance",session,"save",values("version",0,"records",List.of(),"finalize",true)).andExpect(status().isForbidden());
  action(lecturer,"ClassAttendance",session,"save",values("version",0,"records",List.of(values("studentId",student.id,"status","PRESENT","note","")),"finalize",true));
  saveAttendanceAfterFinalization(session);
  var grades=values("version",0,"records",List.of(values("enrollmentId",enrollment,"title","Thực hành","score",8.5)));
  act(manager,"ClassGradebook",cl,"save",grades).andExpect(status().isForbidden());action(lecturer,"ClassGradebook",cl,"save",grades);assertEquals(0,view(student,"StudentResults",null).path("total").asInt());
  grades.put("version",1);action(lecturer,"ClassGradebook",cl,"publish",grades);assertEquals(1,view(student,"StudentResults",null).path("total").asInt());assertEquals(0,view(otherStudent,"StudentResults",null).path("total").asInt());
  action(manager,"TrainingClosureDetail",enrollment,"academic",values("version",0,"changeNote","Đã rà soát hồ sơ và kết quả được công bố"));assertTrue(view(manager,"TrainingClosureDetail_Ready",enrollment).path("related").path("conditions").path("ready").asBoolean());
  act(it,"TrainingClosureDetail_Ready",enrollment,"close",values("version",1)).andExpect(status().isForbidden());action(manager,"TrainingClosureDetail_Ready",enrollment,"close",values("version",1));assertEquals("COMPLETED",view(manager,"TrainingClosureDetail",enrollment).path("record").path("closureOutcome").asText());
 }
 void saveAttendanceAfterFinalization(String session)throws Exception{act(lecturer,"ClassAttendance",session,"save",values("version",1,"records",List.of(values("studentId",student.id,"status","ABSENT","note","")),"finalize",true)).andExpect(status().isConflict());}
 @Test void handoverRecoveryAndRefundAreAtomicScopedAndCannotBeRepeated()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);importAndConfirm(ct,"DEPOSIT",200);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();
  String req=save(lecturer,"AddEquipmentRequest",null,values("classId",cl,"studentId",student.id,"title","Bộ thiết bị thực hành","quantity",1,"requestType","INITIAL","equipmentGroupId",GROUP,"neededOn","2026-11-15","description","Dùng trong buổi thực hành")).path("id").asText();
  write(student,"AddEquipmentRequest",null,Map.of()).andExpect(status().isForbidden());action(it,"EquipmentRequestDetail_IT",req,"prepare",values("version",0));action(it,"EquipmentRequestDetail_IT",req,"ready",values("version",1));
  String asset=save(it,"AddAsset",null,values("code","TS-"+UUID.randomUUID().toString().substring(0,8),"name","Bộ thực hành","equipmentGroupId",GROUP,"serialNumber","SN1","purchasedOn","2026-11-01","recordedValue",500,"assetCondition","Tốt","storageLocationId",LOCATION,"accessories","Đầy đủ","status","AVAILABLE")).path("id").asText();
  String loan=save(it,"AssetHandover",req,values("version",2,"assets",List.of(values("id",asset)),"equipmentChecked",true,"studentChecked",true,"signedBy",student.fullName,"receiptEvidence","Biên bản ký","receivedAt","2026-11-07T01:00:00Z","returnDueAt","2027-02-15T01:00:00Z")).path("id").asText();
  read(student,"StudentAssetReceipt",loan).andExpect(status().isOk());read(otherStudent,"StudentAssetReceipt",loan).andExpect(status().isNotFound());
  write(accountant,"DepositRefund",ct,values("version",2)).andExpect(status().isConflict());
  action(it,"ListAssetDetail",loan,"confirm",values("version",0,"identityChecked",true,"receivedChecked",true,"returnedAt","2026-11-07T04:00:00Z","returnCondition","Đầy đủ và hoạt động tốt","returnEvidence","Biên bản thu hồi","hasIncident",false));
  var refund=values("version",version(accountant,"DepositRefund",ct),"recipient",student.fullName,"paymentMethod","TRANSFER","accountDetails","Tài khoản đã kiểm tra","bankReference",UUID.randomUUID().toString(),"evidence","Chứng từ hoàn","recipientVerified",true);save(accountant,"DepositRefund",ct,refund);refund.put("version",version(accountant,"DepositRefund",ct));write(accountant,"DepositRefund",ct,refund).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals("AVAILABLE",view(it,"AssetDetail",asset).path("record").path("status").asText());
 }
 @Test void notificationsAreScopedAndProposedSendRightsRemainDenied()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);save(manager,"SendNotification",null,values("title","Lịch học mới","content","Kiểm tra thời khóa biểu","recipients",List.of(values("id",student.id)),"scheduledAt",""));
  var rows=view(student,"ListNotification",null).path("items");assertTrue(rows.size()>0);String id=rows.get(0).path("id").asText();read(otherStudent,"ViewNotification",id).andExpect(status().isNotFound());
  action(student,"ViewNotification",id,"read",Map.of());assertEquals("READ",view(student,"ViewNotification",id).path("record").path("status").asText());
  write(lecturer,"SendNotification",null,Map.of()).andExpect(status().isForbidden());saveNotificationToOutsider();
 }
 void saveNotificationToOutsider()throws Exception{write(manager,"SendNotification",null,values("title","Không được gửi","content","Kiểm tra","recipients",List.of(values("id",otherStudent.id)),"scheduledAt","")).andExpect(status().isNotFound());}
 @Test void evidenceFilesAreAuthenticatedValidatedAndPrivateUntilLinkedToAScopedRecord()throws Exception{
  MockMultipartFile file=new MockMultipartFile("file","bien-ban.pdf","application/pdf","%PDF-1.4\nTest evidence".getBytes(StandardCharsets.US_ASCII));
  String id=body(mvc.perform(multipart("/api/workspace/files").file(file).param("screen","ContractSignature").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isOk()).andReturn()).path("id").asText();
  mvc.perform(get("/api/workspace/files/"+id)).andExpect(status().isUnauthorized());mvc.perform(get("/api/workspace/files/"+id).with(user(student.email).roles("STUDENT"))).andExpect(status().isNotFound());mvc.perform(get("/api/workspace/files/"+id).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
  String contract=createContract(createCourse());jdbc.update("UPDATE training_contracts SET signature_evidence=? WHERE id=?","file:"+id+":bien-ban.pdf",UUID.fromString(contract));
  mvc.perform(get("/api/workspace/files/"+id).with(user(student.email).roles("STUDENT"))).andExpect(status().isOk());mvc.perform(get("/api/workspace/files/"+id).with(user(otherStudent.email).roles("STUDENT"))).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","bad.html","text/html","<script>test</script>".getBytes())).param("screen","ContractSignature").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void screenRevocationAppliesToReadsAndWritesAndInvalidIdentifiersFailClearly()throws Exception{
  jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='AddCourse'");write(manager,"AddCourse",null,Map.of()).andExpect(status().isForbidden());read(manager,"AddCourse",null).andExpect(status().isForbidden());read(it,"AssetDetail","not-a-uuid").andExpect(status().isBadRequest());read(it,"UnknownScreen",null).andExpect(status().isNotFound());
 }
 Map<String,String> loanFixture()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);importAndConfirm(ct,"DEPOSIT",200);String cl=view(manager,"ListClass",null).path("items").get(0).path("id").asText();String en=view(manager,"AutoAssignmentReview",null).path("items").get(0).path("id").asText();
  String request=save(lecturer,"AddEquipmentRequest",null,values("classId",cl,"studentId",student.id,"title","Thiết bị học tập","quantity",1,"requestType","INITIAL","equipmentGroupId",GROUP,"neededOn","2026-11-15","description","Học thực hành")).path("id").asText();
  action(it,"EquipmentRequestDetail_IT",request,"prepare",values("version",0));action(it,"EquipmentRequestDetail_IT",request,"ready",values("version",1));
  String asset=save(it,"AddAsset",null,values("code","TS-"+UUID.randomUUID().toString().substring(0,8),"name","Bộ thực hành","equipmentGroupId",GROUP,"serialNumber","SN1","purchasedOn","2026-11-01","recordedValue",500,"assetCondition","Tốt","storageLocationId",LOCATION,"accessories","Đầy đủ","status","AVAILABLE")).path("id").asText();
  String loan=save(it,"AssetHandover",request,values("version",2,"assets",List.of(values("id",asset)),"equipmentChecked",true,"studentChecked",true,"signedBy",student.fullName,"receiptEvidence","Biên bản ký","receivedAt","2026-11-07T01:00:00Z","returnDueAt","2027-02-15T01:00:00Z")).path("id").asText();return Map.of("contract",ct,"class",cl,"enrollment",en,"asset",asset,"loan",loan);
 }
 @Test void approvedDamageOffsetsDepositOnceAndTechnicalAndFinancialDutiesStaySeparate()throws Exception{
  var f=loanFixture();String loan=f.get("loan"),ct=f.get("contract");String incident=action(it,"ListAssetDetail",loan,"confirm",values("version",0,"identityChecked",true,"receivedChecked",true,"returnedAt","2026-11-07T04:00:00Z","returnCondition","Thiếu phụ kiện","returnEvidence","Biên bản thu hồi","hasIncident",true)).path("id").asText();
  var technical=values("version",0,"description","Thiếu phụ kiện","technicalNote","Thiếu dây kết nối theo biên bản","compensationDue",50,"evidence","Minh chứng kỹ thuật");
  act(accountant,"AssetIncidentDetail",incident,"save",technical).andExpect(status().isForbidden());action(it,"AssetIncidentDetail",incident,"save",technical);
  write(accountant,"DepositRefund",ct,values("version",version(accountant,"DepositRefund",ct))).andExpect(status().isConflict());
  action(accountant,"AssetIncidentDetail_Accountant",incident,"settle",values("version",1,"approvedAmount",50,"approvalReference","Phê duyệt theo biên bản đã xác nhận","settlementMethod","DEPOSIT"));
  technical.put("version",2);act(it,"AssetIncidentDetail",incident,"save",technical).andExpect(status().isConflict());
  save(accountant,"DepositRefund",ct,values("version",version(accountant,"DepositRefund",ct),"recipient",student.fullName,"paymentMethod","TRANSFER","accountDetails","Đã xác minh","bankReference",UUID.randomUUID().toString(),"evidence","Chứng từ hoàn cọc","recipientVerified",true));
  assertEquals(150,jdbc.queryForObject("SELECT refund_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals(50,jdbc.queryForObject("SELECT deduction_amount FROM deposit_settlements WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals("RESOLVED",view(accountant,"AssetIncidentDetail_Accountant",incident).path("record").path("status").asText());
 }
 @Test void withdrawalIsNotCourseCompletionAndCannotCloseWithOutstandingEquipment()throws Exception{
  var f=loanFixture();String enrollment=f.get("enrollment");save(manager,"RecordWithdrawal",enrollment,values("version",0,"withdrawnOn","2026-11-07","withdrawalReason","Theo hồ sơ xác nhận","withdrawalEvidence","Biên bản nghỉ học"));
  var detail=view(manager,"TrainingClosureDetail_Withdrawal",enrollment);assertEquals("WITHDRAWN",detail.path("record").path("status").asText());assertFalse(detail.path("related").path("conditions").path("ready").asBoolean());assertEquals("RETURN_DUE",view(it,"Recovery_Withdrawal",f.get("loan")).path("record").path("returnStatus").asText());act(manager,"TrainingClosureDetail_Withdrawal",enrollment,"close",values("version",1)).andExpect(status().isConflict());
 }
 @Test void dashboardsApplyTheSameOwnershipAsBusinessScreens()throws Exception{
  String ct=createContract(createCourse());importAndConfirm(ct,"TUITION",1000);User otherAccountant=create("ACCOUNTANT");
  for(User u:List.of(otherManager,otherHr,otherAccountant)){
   var result=body(mvc.perform(get("/api/dashboard").with(user(u.email).roles(u.getRoleCode()))).andExpect(status().isOk()).andReturn());assertFalse(result.path("panels").toString().contains(student.fullName));
   for(JsonNode metric:result.path("stats"))assertEquals(0,metric.path("value").asDouble(),u.getRoleCode()+":"+metric.path("key").asText());
  }
 }
 @Test void signedContractsAndOwnedAdmissionsCannotBeEditedByOtherStaff()throws Exception{
  String ct=createContract(createCourse());String admission=view(hr,"TrainingContractDetail",ct).path("record").path("consultationId").asText();assertEquals("CONTRACTED",view(hr,"ListAdmissionApplicationDetail",admission).path("record").path("status").asText());read(otherHr,"ListAdmissionApplicationDetail",admission).andExpect(status().isNotFound());write(hr,"EditTrainingContract",ct,values("version",1)).andExpect(status().isConflict());
 }
 @Test void zeroTuitionRequiresAnAccountantConfirmationAndDoesNotInventAZeroAmountPayment()throws Exception{
  String ct=createContract(createCourse());jdbc.update("UPDATE training_contracts SET tuition_due=0 WHERE id=?",UUID.fromString(ct));
  act(hr,"TuitionDetail",ct,"activate-zero",values("version",1,"changeNote","Miễn phí")).andExpect(status().isForbidden());action(accountant,"TuitionDetail",ct,"activate-zero",values("version",1,"changeNote","Căn cứ hợp đồng miễn học phí đã ký"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,UUID.fromString(ct)));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE contract_id=?",Integer.class,UUID.fromString(ct)));
 }
}
