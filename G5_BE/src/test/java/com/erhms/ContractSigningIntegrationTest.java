package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.onboarding.StudentAccountMailer;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.ContractPdfRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:contract_signing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ContractSigningIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired RoleRepository roles;
    @MockitoBean ContractPdfRenderer renderer; @MockitoBean StudentAccountMailer mailer;
    User manager,otherManager,hr,otherHr,accountant,student,admin;
    byte[] printable;

    @BeforeEach void setup()throws Exception{
        manager=actor("MANAGER");otherManager=actor("MANAGER");hr=actor("HR");otherHr=actor("HR");accountant=actor("ACCOUNTANT");student=actor("STUDENT");admin=actor("ADMIN");
        printable=pdfBytes();reset(renderer,mailer);when(renderer.pdf(anyMap())).thenReturn(printable);
    }
    User actor(String role){var u=new User();u.email=UUID.randomUUID()+"@signing.test";u.fullName="Kiểm thử "+role;u.passwordHash="fixture";u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);}
    byte[] pdfBytes()throws Exception{try(var document=new PDDocument();var out=new ByteArrayOutputStream()){document.addPage(new PDPage());document.save(out);return out.toByteArray();}}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    ResultActions read(User u,String code,String id)throws Exception{return mvc.perform(get("/api/workspace/screens/"+code+(id==null?"":"/"+id)).with(user(u.email).roles(u.getRoleCode())));}
    ResultActions write(User u,String code,String id,Map<String,Object> input)throws Exception{return mvc.perform(post("/api/workspace/screens/"+code+(id==null?"":"/"+id)).with(user(u.email).roles(u.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    ResultActions action(User u,String code,String id,String action,Map<String,Object> input)throws Exception{return mvc.perform(post("/api/workspace/screens/"+code+"/"+id+"/actions/"+action).with(user(u.email).roles(u.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode detail(String id)throws Exception{return body(read(manager,"TrainingContractDetail",id).andExpect(status().isOk()).andReturn()).path("record");}
    long version(String id)throws Exception{return detail(id).path("version").asLong();}
    Map<String,Object> documentDetails(){
        var d=new LinkedHashMap<String,Object>();
        d.put("agreementDate","2026-11-01");d.put("agreementPlace","Hà Nội");d.put("effectiveOn","2026-12-01");d.put("studentDateOfBirth","2000-01-02");d.put("studentIdentityNumber","001200000001");d.put("studentAddress","Hà Nội");d.put("studentExperience","Chưa có kinh nghiệm");d.put("emergencyContactName","Nguyễn Văn A");d.put("emergencyRelationship","Bố");d.put("emergencyContactPhone","0987654321");d.put("endsOn","2027-04-01");d.put("latestOpeningOn","2026-12-01");d.put("academicContact","Học vụ");d.put("accountingContact","Kế toán");d.put("equipmentContact","Thiết bị");return d;
    }
    String draft()throws Exception{return draft(documentDetails());}
    String draft(Map<String,Object> details)throws Exception{
        UUID application=UUID.randomUUID();String email=application+"@learner.test";
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,enrollment_course,contact_time,created_date) VALUES (?,'Nguyễn Anh Đức','0912345678',?,'Sửa máy','Sửa chữa máy tính Laptop chuyên nghiệp','any',CURRENT_TIMESTAMP)",application,email);
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,forwarded_at,forwarded_by) VALUES (?,?,?,'PROFILE_PENDING',CURRENT_TIMESTAMP,?)",UUID.randomUUID(),application,hr.id,hr.id);
        var input=Map.of("consultationId",application.toString(),"startsOn","2026-12-01","paymentDueOn","2026-11-30","tuitionDue",14200000,"depositDue",500000,"terms","Theo mẫu hợp đồng","templateData",details);
        return body(write(manager,"AddTrainingContract",null,input).andExpect(status().isOk()).andReturn()).path("id").asText();
    }
    void send(String id)throws Exception{action(manager,"TrainingContractDetail",id,"send-for-signature",Map.of("version",version(id))).andExpect(status().isOk());}
    String scan(User u)throws Exception{return body(mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","hop-dong-da-ky.pdf","application/pdf",pdfBytes())).param("screen","AdmissionContractSigning").with(user(u.email).roles(u.getRoleCode())).with(csrf())).andExpect(status().isOk()).andReturn()).path("id").asText();}
    ResultActions submit(User u,String id,String file,long version)throws Exception{return action(u,"AdmissionContractSigning",id,"submit-scan",Map.of("version",version,"signedOn",LocalDate.now().toString(),"signedScanId",file));}
    void submit(String id)throws Exception{submit(hr,id,scan(hr),version(id)).andExpect(status().isOk());}
    Map<String,Object> approval(String id)throws Exception{return Map.of("version",version(id),"decision","APPROVE","reviewNote","Đã đối chiếu đầy đủ chữ ký","accountantId",accountant.id,"handoverNote","Chuyển kế toán sau khi cấp tài khoản");}
    ResultActions createAccount(String id)throws Exception{
        var record=detail(id);UUID application=UUID.fromString(record.path("consultationId").asText());
        long version=jdbc.queryForObject("SELECT version FROM consultation_followups WHERE consultation_id=?",Long.class,application);
        return mvc.perform(post("/api/workspace/student-accounts/"+application).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("expectedEmail",record.path("studentEmail").asText(),"version",version))));
    }
    ResultActions pdf(User u,String id)throws Exception{return mvc.perform(get("/api/workspace/contracts/"+id+"/pdf").param("download","false").with(user(u.email).roles(u.getRoleCode())));}

    @Test void managerSendsExactPdfAdmissionsUploadsAndApprovalUnlocksProvisioningAndFinance()throws Exception{
        String id=draft();assertTrue(detail(id).path("studentId").isNull());createAccount(id).andExpect(status().isConflict());
        send(id);assertEquals("SENT_FOR_SIGNATURE",detail(id).path("status").asText());createAccount(id).andExpect(status().isConflict());
        assertTrue(body(read(hr,"AdmissionContractSigning",null).andExpect(status().isOk()).andReturn()).path("items").toString().contains(id));
        read(hr,"AdmissionContractSigning",id).andExpect(status().isOk());
        assertArrayEquals(printable,pdf(hr,id).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        submit(id);assertEquals("SIGNATURE_REVIEW",detail(id).path("status").asText());createAccount(id).andExpect(status().isConflict());
        String scan=detail(id).path("signedScanFileId").asText();
        mvc.perform(get("/api/workspace/files/"+scan).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk());
        write(manager,"ContractSignature",id,approval(id)).andExpect(status().isOk());
        var approved=detail(id);assertEquals("SIGNED",approved.path("status").asText());assertFalse(approved.path("signatureApprovedAt").isNull());assertTrue(approved.path("studentId").isNull());assertTrue(approved.path("handedOverAt").isNull());
        read(accountant,"TuitionDetail",id).andExpect(status().isNotFound());
        var account=body(createAccount(id).andExpect(status().isOk()).andReturn());assertTrue(account.path("accountId").asLong()>0);
        var linked=detail(id);assertEquals(account.path("accountId").asLong(),linked.path("studentId").asLong());assertFalse(linked.path("handedOverAt").isNull());
        read(accountant,"TuitionDetail",id).andExpect(status().isOk());
    }

    @Test void assignedAdmissionsOnlyAndOwnerOnlyControlsCannotBeBypassedWithDirectRequests()throws Exception{
        String id=draft();read(hr,"AdmissionContractSigning",id).andExpect(status().isNotFound());
        action(otherManager,"TrainingContractDetail",id,"send-for-signature",Map.of("version",version(id))).andExpect(status().isNotFound());
        action(hr,"TrainingContractDetail",id,"send-for-signature",Map.of("version",version(id))).andExpect(status().isForbidden());
        write(manager,"ContractSignature",id,approval(id)).andExpect(status().isConflict());
        send(id);read(otherHr,"AdmissionContractSigning",id).andExpect(status().isNotFound());pdf(otherHr,id).andExpect(status().isNotFound());pdf(otherManager,id).andExpect(status().isNotFound());
        for(User forbidden:List.of(manager,accountant,student,admin))read(forbidden,"AdmissionContractSigning",null).andExpect(status().isForbidden());
        assertFalse(body(read(otherHr,"AdmissionContractSigning",null).andExpect(status().isOk()).andReturn()).path("items").toString().contains(id));
        submit(otherHr,id,scan(otherHr),version(id)).andExpect(status().isNotFound());
        write(hr,"ContractSignature",id,approval(id)).andExpect(status().isForbidden());
        var edit=Map.<String,Object>of("version",version(id));write(manager,"EditTrainingContract",id,edit).andExpect(status().isConflict());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionContractSigning'");read(hr,"AdmissionContractSigning",id).andExpect(status().isForbidden());pdf(hr,id).andExpect(status().isNotFound());
    }

    @Test void incompleteDraftCannotBeSentAndOldSignatureFormCannotSkipAdmissionsScan()throws Exception{
        String id=draft(Map.of("agreementPlace","Hà Nội"));
        action(manager,"TrainingContractDetail",id,"send-for-signature",Map.of("version",version(id))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['templateData.studentDateOfBirth']").exists());
        assertEquals("DRAFT",detail(id).path("status").asText());assertTrue(detail(id).path("printFileId").isNull());verifyNoInteractions(renderer);
        write(manager,"ContractSignature",id,Map.of("version",version(id),"signedOn","2026-11-01","signatureMethod","PAPER","signatureEvidence","Tự khai đã ký","accountantId",accountant.id)).andExpect(status().isConflict());
    }

    @Test void originalPrintablePdfRemainsFrozenAfterSending()throws Exception{
        String id=draft();send(id);String printFile=detail(id).path("printFileId").asText();assertFalse(printFile.isBlank());
        when(renderer.pdf(anyMap())).thenReturn("CHANGED-PDF".getBytes(StandardCharsets.US_ASCII));
        jdbc.update("UPDATE training_contracts SET terms='A later document edit must not alter the sent original' WHERE id=?",UUID.fromString(id));
        assertArrayEquals(printable,pdf(hr,id).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertArrayEquals(printable,pdf(manager,id).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        verify(renderer,times(1)).pdf(anyMap());
        mvc.perform(get("/api/workspace/files/"+printFile).with(user(otherHr.email).roles("HR"))).andExpect(status().isNotFound());
    }

    @Test void scanMustBeOwnedPdfAndCannotBeReusedAcrossContracts()throws Exception{
        String first=draft(),second=draft();send(first);send(second);
        submit(hr,first,scan(otherHr),version(first)).andExpect(status().isBadRequest());
        submit(hr,first,UUID.randomUUID().toString(),version(first)).andExpect(status().isBadRequest());
        String admissionPdf=body(mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","giay-to-ho-so.pdf","application/pdf",pdfBytes())).param("screen","AdmissionConsultation").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isOk()).andReturn()).path("id").asText();
        submit(hr,first,admissionPdf,version(first)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","image.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10})).param("screen","AdmissionContractSigning").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isBadRequest());
        String file=scan(hr);submit(hr,first,file,version(first)).andExpect(status().isOk());
        submit(hr,second,file,version(second)).andExpect(status().isBadRequest());
        assertEquals("SENT_FOR_SIGNATURE",detail(second).path("status").asText());
    }

    @Test void sendingAndSubmittingScansRequireCsrfTokens()throws Exception{
        String id=draft();
        mvc.perform(post("/api/workspace/screens/TrainingContractDetail/"+id+"/actions/send-for-signature").with(user(manager.email).roles("MANAGER")).contentType("application/json").content(json.writeValueAsString(Map.of("version",version(id))))).andExpect(status().isForbidden());
        assertEquals("DRAFT",detail(id).path("status").asText());send(id);
        mvc.perform(post("/api/workspace/screens/AdmissionContractSigning/"+id+"/actions/submit-scan").with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(Map.of("version",version(id),"signedOn",LocalDate.now().toString(),"signedScanId",scan(hr))))).andExpect(status().isForbidden());
        assertEquals("SENT_FOR_SIGNATURE",detail(id).path("status").asText());
    }

    @Test void multipageSignedPdfCanExceedOldEvidenceLimitButNeverExceedTenMegabytes()throws Exception{
        byte[] large=Arrays.copyOf(pdfBytes(),3*1024*1024);
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","scan.pdf","application/pdf",large)).param("screen","AdmissionContractSigning").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isOk());
        byte[] oversized=Arrays.copyOf(large,10*1024*1024+1);
        mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file","scan.pdf","application/pdf",oversized)).param("screen","AdmissionContractSigning").with(user(hr.email).roles("HR")).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test void staleActionsAndRejectedScansRequireReviewOfTheNewSubmission()throws Exception{
        String id=draft();long initial=version(id);send(id);
        action(manager,"TrainingContractDetail",id,"send-for-signature",Map.of("version",initial)).andExpect(status().isConflict());
        String first=scan(hr);long sent=version(id);submit(hr,id,first,sent).andExpect(status().isOk());
        submit(hr,id,first,sent).andExpect(status().isConflict());
        write(manager,"ContractSignature",id,Map.of("version",version(id),"decision","REJECT","reviewNote","")).andExpect(status().isBadRequest());
        long pending=version(id);write(manager,"ContractSignature",id,Map.of("version",pending,"decision","REJECT","reviewNote","Trang cuối thiếu chữ ký học viên")).andExpect(status().isOk());
        assertEquals("SENT_FOR_SIGNATURE",detail(id).path("status").asText());assertEquals("Trang cuối thiếu chữ ký học viên",detail(id).path("signatureReviewNote").asText());createAccount(id).andExpect(status().isConflict());
        submit(id);var stale=approval(id);stale=new LinkedHashMap<>(stale);stale.put("version",pending);write(manager,"ContractSignature",id,stale).andExpect(status().isConflict());
        write(manager,"ContractSignature",id,approval(id)).andExpect(status().isOk());assertEquals("SIGNED",detail(id).path("status").asText());
        assertNotEquals(first,detail(id).path("signedScanFileId").asText());
        assertTrue(body(read(manager,"TrainingContractDetail",id).andExpect(status().isOk()).andReturn()).path("related").path("signingHistory").size()>=5);
    }
}
