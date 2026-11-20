package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.onboarding.StudentAccountMailer;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.WorkflowException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:student_accounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentAccountsIntegrationTest {
    private static final String API="/api/workspace/student-accounts";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @MockitoBean StudentAccountMailer mailer;
    private Map<String,User> actors;
    private User manager;
    private String unique;

    @BeforeEach void setup() {
        reset(mailer);unique="onboard-"+UUID.randomUUID();actors=new LinkedHashMap<>();
        for(String code:List.of("ADMIN","MANAGER","HR","LECTURER","STUDENT","ACCOUNTANT","IT_SUPPORT"))actors.put(code,account(code,UUID.randomUUID()+"@actor.test"));
        manager=actors.get("MANAGER");
    }

    private User account(String code,String email) {
        var user=new User();user.email=email;user.fullName=unique+" "+code;user.passwordHash="existing-hash";user.role=roles.findByCode(code).orElseThrow();
        return users.saveAndFlush(user);
    }
    private String admission(String state,String email) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,contact_time,created_date,program_name) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?)",id,unique+" Học viên","0912345678",email,"morning",unique+" Khóa học");
        if(!state.equals("NEW"))jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,version) VALUES (?,?,?,?,0)",UUID.randomUUID(),id,actors.get("HR").id,state);
        if(state.equals("DOCUMENT_APPOINTMENT"))jdbc.update("UPDATE consultation_followups SET document_due_on=DATE '2026-12-15' WHERE consultation_id=?",id);
        if(List.of("PROFILE_PENDING","CONTRACTED","PROFILE_CREATED").contains(state))jdbc.update("UPDATE consultation_followups SET forwarded_at=CURRENT_TIMESTAMP,forwarded_by=? WHERE consultation_id=?",actors.get("HR").id,id);
        return id.toString();
    }
    private ResultActions create(User actor,String id,Map<String,Object> input)throws Exception {
        return mvc.perform(post(API+"/"+id).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    private String readyAdmission(String email) {
        String id=admission("CONTRACTED",email);UUID contract=UUID.randomUUID();
        jdbc.update("INSERT INTO training_contracts(id,code,consultation_id,student_email,applicant_name,status,created_by,manager_id,accountant_id,signature_approved_at) VALUES (?,?,?,?,?,'SIGNED',?,?,?,CURRENT_TIMESTAMP)",
                contract,"HD-"+contract.toString().substring(0,8),UUID.fromString(id),email==null?"":email.strip().toLowerCase(Locale.ROOT),unique+" Học viên",manager.id,manager.id,actors.get("ACCOUNTANT").id);
        return id;
    }
    private void approveContractFixture(String contract) {
        jdbc.update("UPDATE training_contracts SET status='SIGNED',signature_approved_at=CURRENT_TIMESTAMP,accountant_id=? WHERE id=?",actors.get("ACCOUNTANT").id,UUID.fromString(contract));
    }
    private String createContract(String admission)throws Exception {
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,?,3,20,1000,'Training','ACTIVE',?,20)",course,course.toString(),"Sửa chữa máy tính Laptop chuyên nghiệp","Nghề",manager.id);
        var input=ClassPlanTestFixtures.withClass(jdbc,manager.id,Map.of("consultationId",admission,"courseId",course.toString(),"startsOn","2026-12-15","paymentDueOn","2026-12-14","tuitionDue",1000,"depositDue",0,"terms","Điều khoản đào tạo"));
        return body(mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
    }
    private ResultActions create(String id,String email)throws Exception {return create(manager,id,Map.of("expectedEmail",email,"version",0));}
    private JsonNode body(MvcResult result)throws Exception {return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    private JsonNode list()throws Exception {return body(mvc.perform(get(API).param("q",unique).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    private JsonNode row(String id)throws Exception {for(JsonNode row:list().path("items"))if(row.path("id").asText().equals(id))return row;throw new AssertionError("Missing admission "+id);}

    private Map<String,Object> documentInput(long version,String fileId) {return Map.of("version",version,"documents",List.of(Map.of("type","IDENTITY_CARD","files",List.of(fileId))));}
    private ResultActions submitProfile(User actor,String id,Map<String,Object> input)throws Exception {
        return mvc.perform(post("/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/submit-profile")
                .with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    private JsonNode scan(User actor)throws Exception {
        return body(mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","ho-so.pdf","application/pdf","%PDF-1.4\nScanned application".getBytes(StandardCharsets.US_ASCII)))
                .param("screen","AdmissionConsultation").with(user(actor.email).roles(actor.getRoleCode())).with(csrf())).andExpect(status().isOk()).andReturn());
    }

    private ResultActions workspaceWrite(User actor,String screen,String id,Map<String,Object> values)throws Exception {
        if(screen.equals("AddTrainingContract")||screen.equals("EditTrainingContract"))values=ClassPlanTestFixtures.withClass(jdbc,actor.id,values);
        return mvc.perform(post("/api/workspace/screens/"+screen+(id==null?"":"/"+id)).with(user(actor.email).roles(actor.getRoleCode())).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(values)));
    }

    private ResultActions workspaceAction(User actor,String screen,String id,String action,Map<String,Object> values)throws Exception {
        return mvc.perform(post("/api/workspace/screens/"+screen+"/"+id+"/actions/"+action).with(user(actor.email).roles(actor.getRoleCode())).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(values)));
    }

    private JsonNode workspaceView(User actor,String screen,String id)throws Exception {
        return body(mvc.perform(get("/api/workspace/screens/"+screen+"/"+id).with(user(actor.email).roles(actor.getRoleCode()))).andExpect(status().isOk()).andReturn());
    }

    private Map<String,String> classForRegisteredProgram(String program)throws Exception {
        UUID course=UUID.randomUUID();
        jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',4,20,14200000,'Training fixture','ACTIVE',?,10)",course,course.toString(),program,manager.id);
        String classId=ClassPlanTestFixtures.withClass(jdbc,manager.id,Map.of("consultationId",UUID.randomUUID().toString(),"courseId",course.toString(),"startsOn","2026-12-15")).get("classId").toString();
        return Map.of("courseId",course.toString(),"classId",classId);
    }

    @Test void registeredProgramContractUsesItsPlannedClassThenRequiresAccountAndConfirmedPaymentBeforeEnrollment()throws Exception {
        String program="Sửa chữa máy tính Laptop chuyên nghiệp",email=unique+"-direct@student.test";
        String admission=admission("PROFILE_PENDING",email);
        jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?",program,UUID.fromString(admission));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_courses WHERE manager_id=?",Integer.class,manager.id));

        String contract=body(workspaceWrite(manager,"AddTrainingContract",null,Map.of("consultationId",admission,"startsOn","2026-12-15","paymentDueOn","2026-12-14","depositDue",0,"terms","Điều khoản theo hồ sơ tuyển sinh"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        UUID contractId=UUID.fromString(contract);
        var draft=workspaceView(manager,"TrainingContractDetail",contract).path("record");
        assertFalse(draft.path("courseId").isNull());assertFalse(draft.path("classId").isNull());assertTrue(draft.path("studentId").isNull());
        assertEquals(program,draft.path("courseName").asText());assertEquals(4,draft.path("durationMonths").asInt());
        assertEquals(14200000,draft.path("tuitionDue").asLong());assertEquals(manager.id.longValue(),draft.path("managerId").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_courses WHERE manager_id=?",Integer.class,manager.id));

        create(manager,admission,Map.of("expectedEmail",email,"version",row(admission).path("version").asLong())).andExpect(status().isConflict());
        approveContractFixture(contract);
        long student=body(create(manager,admission,Map.of("expectedEmail",email,"version",row(admission).path("version").asLong()))
                .andExpect(status().isOk()).andReturn()).path("accountId").asLong();
        assertEquals(student,jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE id=?",Long.class,contractId));
        verify(mailer).sendCredentials(eq(email),anyString(),anyString());
        assertNotNull(jdbc.queryForObject("SELECT handed_over_at FROM training_contracts WHERE id=?",Object.class,contractId));

        User accountant=actors.get("ACCOUNTANT");
        String payment=body(workspaceAction(accountant,"ListPayment","new","import",Map.of("contractId",contract,"kind","TUITION","amount",14200000,"bankReference",UUID.randomUUID().toString(),"payerName",unique+" Học viên","bankId","55000000-0000-0000-0000-000000000001","memo","Đối chiếu học phí hợp đồng","evidence","Chứng từ đã kiểm tra"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        workspaceAction(accountant,"PaymentDetail",payment,"confirm",Map.of("version",0,"identityVerified",true,"amountVerified",true,"referenceVerified",true,"changeNote","Đã khớp chứng từ"))
                .andExpect(status().isOk());
        var paid=workspaceView(manager,"TrainingContractDetail",contract).path("record");
        assertEquals("ACTIVE",paid.path("status").asText());assertEquals(draft.path("courseId"),paid.path("courseId"));assertEquals(14200000,paid.path("tuitionPaid").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE recipient_id=? AND target_screen='ClassDetail' AND target_id=?",Integer.class,manager.id,paid.path("classId").asText()));

        var wrongClass=classForRegisteredProgram("Sửa chữa máy tính PC & máy in");
        var wrongCandidates=workspaceView(manager,"ClassDetail",wrongClass.get("classId")).path("related").path("unassignedStudents");
        for(var candidate:wrongCandidates)assertNotEquals(student,candidate.path("id").asLong());
        workspaceAction(manager,"ClassDetail",wrongClass.get("classId"),"add-students",Map.of("version",0,"students",List.of(Map.of("id",student))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.students").exists());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=?",Integer.class,contractId));

        var matchingClass=Map.of("courseId",paid.path("courseId").asText(),"classId",paid.path("classId").asText());
        var candidates=workspaceView(manager,"ClassDetail",matchingClass.get("classId")).path("related").path("unassignedStudents");
        boolean listed=false;for(var candidate:candidates)if(candidate.path("id").asLong()==student)listed=true;assertTrue(listed);
        workspaceAction(manager,"ClassDetail",matchingClass.get("classId"),"add-students",Map.of("version",0,"students",List.of(Map.of("id",student))))
                .andExpect(status().isOk());
        var assigned=workspaceView(manager,"TrainingContractDetail",contract).path("record");
        assertEquals(matchingClass.get("courseId"),assigned.path("courseId").asText());
        assertEquals(program,assigned.path("registeredCourseName").asText());assertEquals(4,assigned.path("registeredDurationMonths").asInt());
        assertEquals(14200000,assigned.path("tuitionDue").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_enrollments WHERE contract_id=? AND class_id=? AND student_id=?",Integer.class,contractId,UUID.fromString(matchingClass.get("classId")),student));
    }

    @Test void scannedApplicationIsSentToManagerAndChangesStatusOnlyAfterProfileCreation()throws Exception {
        User hr=actors.get("HR");String email=unique+"@student.test",id=admission("DOCUMENT_APPOINTMENT"," "+email.toUpperCase(Locale.ROOT)+" ");
        assertEquals(0,list().path("total").asInt());create(id,email).andExpect(status().isConflict());
        JsonNode file=scan(hr);
        mvc.perform(get("/api/workspace/files/"+file.path("id").asText()).with(user(manager.email).roles("MANAGER"))).andExpect(status().isNotFound());
        submitProfile(hr,id,documentInput(0,file.path("id").asText())).andExpect(status().isOk());
        assertEquals(0,list().path("total").asInt());
        var documents=new ArrayList<Map<String,Object>>();documents.add(Map.of("type","IDENTITY_CARD","files",List.of(file.path("id").asText())));
        for(String type:List.of("CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM"))documents.add(Map.of("type",type,"files",List.of(scan(hr).path("id").asText())));
        mvc.perform(post("/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/update-profile").with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",1,"documents",documents)))).andExpect(status().isOk());
        jdbc.update("UPDATE consultation_registrations SET enrollment_course=? WHERE id=?","Sửa chữa máy tính Laptop chuyên nghiệp",UUID.fromString(id));
        mvc.perform(post("/api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles").with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("admissions",List.of(Map.of("id",id,"version",2)))))).andExpect(status().isOk());
        JsonNode pending=row(id);assertEquals("PROFILE_PENDING",pending.path("profileStatus").asText());assertFalse(pending.path("canCreate").asBoolean());
        assertEquals(file.path("reference").asText(),pending.path("profileEvidence").asText());assertFalse(pending.path("profileSubmittedAt").isNull());
        mvc.perform(get("/api/workspace/files/"+file.path("id").asText()).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/api/workspace/files/"+file.path("id").asText()).with(user(actors.get("STUDENT").email).roles("STUDENT"))).andExpect(status().isNotFound());
        submitProfile(hr,id,documentInput(1,file.path("id").asText())).andExpect(status().isConflict());
        mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.admissions[?(@.id == '"+id+"')]").isNotEmpty());
        create(manager,id,Map.of("expectedEmail",email,"version",0)).andExpect(status().isConflict());
        create(manager,id,Map.of("expectedEmail",email,"version",pending.path("version").asLong())).andExpect(status().isConflict());
        String contract=createContract(id);
        assertNull(jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE id=?",Long.class,UUID.fromString(contract)));
        assertEquals(email,jdbc.queryForObject("SELECT student_email FROM training_contracts WHERE id=?",String.class,UUID.fromString(contract)));
        mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+contract).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.studentName").value(unique+" Học viên")).andExpect(jsonPath("$.record.studentId").isEmpty());
        mvc.perform(post("/api/workspace/screens/ContractSignature/"+contract).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isConflict());
        create(manager,id,Map.of("expectedEmail",email,"version",row(id).path("version").asLong())).andExpect(status().isConflict());
        approveContractFixture(contract);
        create(manager,id,Map.of("expectedEmail",email,"version",row(id).path("version").asLong())).andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE id=?",Long.class,UUID.fromString(contract)));
        mvc.perform(post("/api/workspace/screens/EditTrainingContract/"+contract).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("version",0,"consultationId",id)))).andExpect(status().isConflict());
        mvc.perform(get("/api/workspace/screens/TuitionDetail_Student/"+contract).with(user(email).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.record.studentEmail").value(email));
        JsonNode completed=row(id);assertEquals("PROFILE_CREATED",completed.path("profileStatus").asText());assertFalse(completed.path("canCreate").asBoolean());assertFalse(completed.path("profileCreatedAt").isNull());
        mvc.perform(get("/api/workspace/screens/ListAdmissionEnrollment").param("q",unique).param("tab","forwarded").with(user(hr.email).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].profileEvidence").value(file.path("reference").asText()));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=?",Integer.class,UUID.fromString(id)));
    }

    @Test void scanSubmissionRequiresDocumentAppointmentAnOwnedFileCurrentVersionAndHrPermission()throws Exception {
        User hr=actors.get("HR"),otherHr=account("HR",UUID.randomUUID()+"@actor.test");String id=admission("DOCUMENT_APPOINTMENT",unique+"@student.test");
        JsonNode file=scan(hr),otherFile=scan(otherHr);Map<String,Object> valid=documentInput(0,file.path("id").asText());
        for(String reference:List.of("", "Bản giấy tại văn phòng", UUID.randomUUID().toString(),otherFile.path("id").asText()))
            submitProfile(hr,id,documentInput(0,reference)).andExpect(status().isBadRequest());
        submitProfile(otherHr,id,valid).andExpect(status().isNotFound());submitProfile(manager,id,valid).andExpect(status().isForbidden());
        submitProfile(hr,id,documentInput(1,file.path("id").asText())).andExpect(status().isConflict());
        String fresh=admission("NEW",unique+"-fresh@student.test");submitProfile(hr,fresh,valid).andExpect(status().isConflict());
        for(String state:List.of("PROFILE_PENDING","PROFILE_CREATED"))
            mvc.perform(post("/api/workspace/screens/AdmissionConsultation/"+id).with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("version",0,"channel","PHONE","status",state,"notes","Thử bỏ qua quy trình"))))
                    .andExpect(status().isBadRequest());
        mvc.perform(post("/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/submit-profile").with(user(hr.email).roles("HR"))
                .contentType("application/json").content(json.writeValueAsString(valid))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","bad.html","text/html","<html>file</html>".getBytes()))
                .param("screen","AdmissionConsultation").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","big.pdf","application/pdf",new byte[2097153]))
                .param("screen","AdmissionConsultation").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");
        try {
            submitProfile(hr,id,valid).andExpect(status().isForbidden());
            mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","scan.pdf","application/pdf","%PDF-1.4".getBytes()))
                    .param("screen","AdmissionConsultation").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isForbidden());
        } finally {jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (7,'AdmissionConsultation')");}
        assertEquals("DOCUMENT_APPOINTMENT",jdbc.queryForObject("SELECT status FROM consultation_followups WHERE consultation_id=?",String.class,UUID.fromString(id)));
    }

    @Test void existingStudentCanReceiveAProfileWithoutChangingCredentialsOrTakingAnotherManagersProfile()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);User existing=account("STUDENT",email);
        assertTrue(row(id).path("canCreate").asBoolean());create(id,email).andExpect(status().isOk());
        assertEquals("existing-hash",users.findById(existing.id).orElseThrow().passwordHash);assertEquals("PROFILE_CREATED",row(id).path("profileStatus").asText());
        verifyNoInteractions(mailer);
        String another=readyAdmission(email);User otherManager=account("MANAGER",UUID.randomUUID()+"@actor.test");
        create(otherManager,another,Map.of("expectedEmail",email,"version",0)).andExpect(status().isConflict());
        assertEquals(manager.id,jdbc.queryForObject("SELECT manager_id FROM student_profiles WHERE user_id=?",Long.class,existing.id));
    }

    @Test void newAndExistingStudentAccountsRequireApprovalOfTheSignedContract()throws Exception {
        for(boolean reuse:List.of(false,true)) {
            String email=unique+(reuse?"-existing":"-new")+"@student.test",id=readyAdmission(email);
            User existing=reuse?account("STUDENT",email):null;
            for(String state:List.of("DRAFT","SENT_FOR_SIGNATURE","SIGNATURE_REVIEW","SIGNED")) {
                jdbc.update("UPDATE training_contracts SET status=?,signature_approved_at=NULL WHERE consultation_id=?",state,UUID.fromString(id));
                JsonNode blocked=row(id);
                assertTrue(blocked.path("hasContract").asBoolean());assertFalse(blocked.path("hasApprovedContract").asBoolean());
                assertEquals(state,blocked.path("contractSigningStatus").asText());assertFalse(blocked.path("canCreate").asBoolean());
                assertTrue(blocked.path("blockedReason").asText().contains("xác nhận bản hợp đồng đã ký"));
                create(id,email).andExpect(status().isConflict());
                assertNull(jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE consultation_id=?",Long.class,UUID.fromString(id)));
                assertNull(jdbc.queryForObject("SELECT handed_over_at FROM training_contracts WHERE consultation_id=?",Object.class,UUID.fromString(id)));
                if(reuse) {
                    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles WHERE user_id=?",Long.class,existing.id));
                    assertEquals("existing-hash",users.findById(existing.id).orElseThrow().passwordHash);
                } else assertFalse(users.existsByEmailIgnoreCase(email));
            }
            jdbc.update("UPDATE training_contracts SET signature_approved_at=CURRENT_TIMESTAMP WHERE consultation_id=?",UUID.fromString(id));
            assertTrue(row(id).path("hasApprovedContract").asBoolean());assertTrue(row(id).path("canCreate").asBoolean());
        }
        verifyNoInteractions(mailer);
    }

    @Test void everyNoncancelledContractMustBeApprovedOwnedAndMatchTheEmailBeforeProvisioning()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);UUID admission=UUID.fromString(id),extra=UUID.randomUUID();
        jdbc.update("INSERT INTO training_contracts(id,code,consultation_id,student_email,applicant_name,status,manager_id) VALUES (?,?,?,?,?,'DRAFT',?)",extra,extra.toString(),admission,email,unique+" Học viên",manager.id);
        assertFalse(row(id).path("canCreate").asBoolean());create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET status='SIGNED',signature_approved_at=CURRENT_TIMESTAMP,manager_id=? WHERE id=?",actors.get("HR").id,extra);
        assertFalse(row(id).path("hasContract").asBoolean());create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET manager_id=?,student_email=? WHERE id=?",manager.id,unique+"-other@student.test",extra);
        assertFalse(row(id).path("hasApprovedContract").asBoolean());create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET status='CANCELLED' WHERE id=?",extra);
        assertTrue(row(id).path("canCreate").asBoolean());
        verifyNoInteractions(mailer);assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test void approvedContractIsHandedToFinanceOnlyWhenTheStudentProfileIsCreated()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);
        UUID contract=jdbc.queryForObject("SELECT id FROM training_contracts WHERE consultation_id=?",UUID.class,UUID.fromString(id));
        assertNull(jdbc.queryForObject("SELECT handed_over_at FROM training_contracts WHERE id=?",Object.class,contract));
        mvc.perform(get("/api/workspace/screens/TuitionDetail/"+contract).with(user(actors.get("ACCOUNTANT").email).roles("ACCOUNTANT"))).andExpect(status().isNotFound());
        create(id,email).andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("SELECT handed_over_at FROM training_contracts WHERE id=?",Object.class,contract));
        mvc.perform(get("/api/workspace/screens/TuitionDetail/"+contract).with(user(actors.get("ACCOUNTANT").email).roles("ACCOUNTANT"))).andExpect(status().isOk());
    }

    @Test void accountCreationRejectsMismatchedCancelledOrAnotherManagersContract()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);UUID admission=UUID.fromString(id);
        jdbc.update("UPDATE training_contracts SET student_email=? WHERE consultation_id=?",unique+"-different@student.test",admission);
        assertFalse(row(id).path("canCreate").asBoolean());create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET student_email=?,status='CANCELLED' WHERE consultation_id=?",email,admission);
        create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE training_contracts SET status='DRAFT',manager_id=? WHERE consultation_id=?",actors.get("HR").id,admission);
        create(id,email).andExpect(status().isConflict());
        verifyNoInteractions(mailer);assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test void hrCannotChangeTheEmailUsedOnAContract()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);User hr=actors.get("HR");
        var input=Map.of("version",0,"channel","PHONE","status","CONTRACTED","notes","Bổ sung tư vấn","email",unique+"-changed@student.test");
        mvc.perform(post("/api/workspace/screens/AdmissionConsultation/"+id).with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isConflict());
        assertEquals(email,jdbc.queryForObject("SELECT email FROM consultation_registrations WHERE id=?",String.class,UUID.fromString(id)));
    }

    @Test void onlyManagersWithTheScreenGrantCanListAndCreateEvenWithSpoofedOrAccidentalGrants()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        for(User actor:actors.values())if(actor!=manager) {
            jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,'ListStudentAccount')",actor.getRoleId());
            try {
                mvc.perform(get(API).with(user(actor.email).roles("MANAGER"))).andExpect(status().isForbidden());
                create(actor,id,Map.of("expectedEmail",email,"version",0)).andExpect(status().isForbidden());
            } finally {jdbc.update("DELETE FROM role_function WHERE role_id=? AND function_code='ListStudentAccount'",actor.getRoleId());}
        }
        jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='ListStudentAccount'");
        try {
            mvc.perform(get(API).with(user(manager.email).roles("MANAGER"))).andExpect(status().isForbidden());
            create(id,email).andExpect(status().isForbidden());
        } finally {jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (6,'ListStudentAccount')");}
        mvc.perform(post(API+"/"+id).with(user(manager.email).roles("MANAGER")).contentType("application/json").content(json.writeValueAsString(Map.of("expectedEmail",email,"version",0)))).andExpect(status().isForbidden());
        verifyNoInteractions(mailer);
    }

    @Test void permissionMatrixOnlyAllowsManagerToReceiveTheProvisioningGrant()throws Exception {
        User admin=actors.get("ADMIN");
        JsonNode matrix=body(mvc.perform(get("/api/admin/permissions").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());
        JsonNode target=null;
        for(JsonNode function:matrix.path("functions"))if(function.path("code").asText().equals("ListStudentAccount"))target=function;
        assertNotNull(target);assertEquals(json.valueToTree(List.of(6)),target.path("allowedRoles"));
        for(long roleId:List.of(1L,2L,3L,4L,5L,7L)) {
            var input=json.createObjectNode().put("version",matrix.path("version").asLong());
            var grants=matrix.path("grants").deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)grants).addObject().put("roleId",roleId).put("functionCode","ListStudentAccount");input.set("grants",grants);
            mvc.perform(put("/api/admin/permissions").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(input.toString())).andExpect(status().isBadRequest());
        }
    }

    @Test void queueContainsOnlySubmittedProfilesAndSupportsSearchAndStablePagination()throws Exception {
        String first=readyAdmission(unique+"-first@student.test"),second=readyAdmission(unique+"-second@student.test");
        for(String state:List.of("NEW","NEEDS_COUNSELING","DOCUMENT_APPOINTMENT","NOT_PROCEEDING"))admission(state,unique+"-"+state+"@student.test");
        assertEquals(2,list().path("total").asInt());
        JsonNode page0=body(mvc.perform(get(API).param("q",unique).param("size","1").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());
        JsonNode page1=body(mvc.perform(get(API).param("q",unique).param("size","1").param("page","1").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());
        assertEquals(2,page0.path("totalPages").asInt());assertNotEquals(page0.path("items").get(0).path("id"),page1.path("items").get(0).path("id"));
        mvc.perform(get(API).param("q",unique+"-FIRST").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(first));
        mvc.perform(get(API).param("q",unique+"%").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        assertTrue(row(second).path("canCreate").asBoolean());
        for(String invalid:List.of("0","101"))mvc.perform(get(API).param("size",invalid).with(user(manager.email).roles("MANAGER"))).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("page","-1").with(user(manager.email).roles("MANAGER"))).andExpect(status().isBadRequest());
    }

    @Test void listExplainsMissingInvalidExistingAndUnavailableAccounts()throws Exception {
        for(String email:new String[]{null,"","not-an-email"}) {
            String id=readyAdmission(email);JsonNode item=row(id);
            assertEquals("INVALID_EMAIL",item.path("accountStatus").asText());assertFalse(item.path("canCreate").asBoolean());assertFalse(item.path("blockedReason").asText().isBlank());
            create(id,email==null||email.isEmpty()?"missing@student.test":email).andExpect(status().is4xxClientError());
        }
        for(String state:List.of("OTHER_ROLE","DISABLED","LOCKED")) {
            String email=unique+"-"+state.toLowerCase(Locale.ROOT)+"@student.test";
            User existing=account(state.equals("OTHER_ROLE")?"HR":"STUDENT",email.toUpperCase(Locale.ROOT));
            if(state.equals("DISABLED"))jdbc.update("UPDATE m_user SET account_enabled=FALSE WHERE user_id=?",existing.id);
            if(state.equals("LOCKED"))jdbc.update("UPDATE m_user SET account_locked=TRUE WHERE user_id=?",existing.id);
            String id=readyAdmission(" "+email+" ");JsonNode item=row(id);
            assertEquals(state,item.path("accountStatus").asText());assertEquals(existing.id,item.path("accountId").asLong());assertFalse(item.path("canCreate").asBoolean());
            create(id,email).andExpect(status().isConflict());assertEquals("existing-hash",users.findById(existing.id).orElseThrow().passwordHash);
        }
        verifyNoInteractions(mailer);
    }

    @Test void creationUsesStoredProfileCreatesOnlyStudentAndSendsAWorkingSecretWithoutReturningOrAuditingIt()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(" "+email.toUpperCase(Locale.ROOT)+" ");
        MvcResult result=create(id,email).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        long accountId=body(result).path("accountId").asLong();User created=users.findWithRoleById(accountId).orElseThrow();
        assertEquals(created.getStudentCode(),body(result).path("studentCode").asText());
        assertEquals(created.getStudentCode(),row(id).path("studentCode").asText());
        var secret=ArgumentCaptor.forClass(String.class);verify(mailer).requireConfigured();verify(mailer).sendCredentials(eq(email),eq(unique+" Học viên"),secret.capture());
        String password=secret.getValue();assertTrue(password.length()>=16);assertTrue(passwords.matches(password,created.passwordHash));assertNotEquals(password,created.passwordHash);
        assertEquals(3L,created.getRoleId());assertEquals("STUDENT",created.getRoleCode());assertEquals("0912345678",created.phone);assertTrue(created.accountEnabled);assertFalse(created.accountLocked);
        assertEquals(manager.id,jdbc.queryForObject("SELECT manager_id FROM student_profiles WHERE user_id=?",Long.class,accountId));
        assertFalse(result.getResponse().getContentAsString().contains(password));
        String audit=json.writeValueAsString(jdbc.queryForList("SELECT * FROM system_audit_log WHERE action_code='STUDENT_ACCOUNT_CREATE' AND target_id=?",Long.toString(accountId)));
        assertFalse(audit.contains(password));assertFalse(audit.contains(created.passwordHash));
        MvcResult login=mvc.perform(post("/api/auth/login").with(csrf()).param("username",email).param("password",password)).andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/auth/me").session((MockHttpSession)login.getRequest().getSession(false))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        mvc.perform(get("/api/workspace/screens/ListStudent").param("q",email).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/workspace/screens/ListStudent").param("q",created.getStudentCode()).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].studentCode").value(created.getStudentCode()));
        assertEquals("STUDENT",row(id).path("accountStatus").asText());create(id,email).andExpect(status().isConflict());verify(mailer,times(1)).sendCredentials(anyString(),anyString(),anyString());
        JsonNode contractOptions=body(mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());
        assertFalse(contractOptions.path("options").has("students"));
        assertEquals(accountId,jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE consultation_id=?",Long.class,UUID.fromString(id)));
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,?,3,20,1000,'Training','ACTIVE',?,20)",course,course.toString(),unique+" Course","Nghề",manager.id);
        var contract=ClassPlanTestFixtures.withClass(jdbc,manager.id,Map.of("consultationId",id,"courseId",course.toString(),"studentId",accountId,"startsOn","2026-12-15","paymentDueOn","2026-12-14","tuitionDue",1000,"depositDue",0,"terms","Điều khoản đào tạo"));
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isOk());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND student_id=? AND manager_id=?",Integer.class,UUID.fromString(id),accountId,manager.id));
    }

    @Test void changedEmailVersionOrContactStateRequiresReloadAndArbitraryAccountFieldsAreRejected()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);
        jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",unique+"-updated@student.test",UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",email,UUID.fromString(id));
        jdbc.update("UPDATE consultation_followups SET version=1 WHERE consultation_id=?",UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        for(String state:List.of("NEEDS_COUNSELING","DOCUMENT_APPOINTMENT","NOT_PROCEEDING","PROFILE_PENDING","PROFILE_CREATED")) {
            jdbc.update("UPDATE consultation_followups SET version=0,status=? WHERE consultation_id=?",state,UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        }
        jdbc.update("UPDATE consultation_followups SET status='CONTRACTED' WHERE consultation_id=?",UUID.fromString(id));
        for(String field:List.of("roleId","password","recipient","email","fullName")) {
            var input=new LinkedHashMap<String,Object>(Map.of("expectedEmail",email,"version",0));input.put(field,"forged");
            create(manager,id,input).andExpect(status().isBadRequest());
        }
        create(UUID.randomUUID().toString(),email).andExpect(status().isNotFound());
        verifyNoInteractions(mailer);assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test void smtpFailureRollsBackAccountAndProfileAndAllowsANewAttempt()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);
        long profiles=jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Long.class);
        doThrow(new IllegalStateException("SECRET SMTP BODY AND CREDENTIALS")).when(mailer).sendCredentials(anyString(),anyString(),anyString());
        MvcResult failure=create(id,email).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED")).andReturn();
        assertFalse(failure.getResponse().getContentAsString().contains("SECRET"));assertFalse(users.existsByEmailIgnoreCase(email));
        assertEquals(profiles,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Long.class));assertTrue(row(id).path("canCreate").asBoolean());
        assertEquals("CONTRACTED",row(id).path("profileStatus").asText());assertTrue(row(id).path("profileCreatedAt").isNull());
        assertNull(jdbc.queryForObject("SELECT student_id FROM training_contracts WHERE consultation_id=?",Long.class,UUID.fromString(id)));
        assertNull(jdbc.queryForObject("SELECT handed_over_at FROM training_contracts WHERE consultation_id=?",Object.class,UUID.fromString(id)));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM training_contracts WHERE consultation_id=?",Long.class,UUID.fromString(id)));
        reset(mailer);create(id,email).andExpect(status().isOk());assertTrue(users.existsByEmailIgnoreCase(email));
    }

    @Test void missingMailConfigurationFailsBeforeCreatingAnAccount()throws Exception {
        String email=unique+"@student.test",id=readyAdmission(email);
        doThrow(new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE,"MAIL_NOT_CONFIGURED","Chưa cấu hình email",Map.of())).when(mailer).requireConfigured();
        create(id,email).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
        assertFalse(users.existsByEmailIgnoreCase(email));verify(mailer,never()).sendCredentials(anyString(),anyString(),anyString());
    }

    @Test void concurrentManagersCannotCreateOrEmailTheSameAccountTwice()throws Exception {
        String email=unique+"@student.test",first=readyAdmission(email),second=readyAdmission(email.toUpperCase(Locale.ROOT));
        User other=account("MANAGER",UUID.randomUUID()+"@actor.test");
        CountDownLatch sending=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(call->{sending.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return null;}).when(mailer).sendCredentials(eq(email),anyString(),anyString());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var created=pool.submit(()->create(first,email).andReturn().getResponse().getStatus());
            assertTrue(sending.await(10,TimeUnit.SECONDS));
            var duplicate=pool.submit(()->create(other,second,Map.of("expectedEmail",email,"version",0)).andReturn().getResponse().getStatus());
            release.countDown();assertEquals(200,created.get(15,TimeUnit.SECONDS));assertEquals(409,duplicate.get(15,TimeUnit.SECONDS));
            assertEquals(1,users.findAllByEmailIgnoreCase(email).size());verify(mailer,times(1)).sendCredentials(eq(email),anyString(),anyString());
        } finally {release.countDown();pool.shutdownNow();}
    }
}
