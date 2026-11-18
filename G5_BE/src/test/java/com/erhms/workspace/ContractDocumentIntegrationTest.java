package com.erhms.workspace;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.annotation.Propagation;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:contract_document;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ContractDocumentIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired ContractTemplateData templates;
    User manager,otherManager,hr,student,admin;UUID admission;

    User actor(String role){var actor=new User();actor.email=UUID.randomUUID()+"@contract-document.test";actor.fullName="Kiểm thử "+role;actor.passwordHash="fixture";actor.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(actor);}
    UUID admission(String name){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,enrollment_course,contact_time,created_date) VALUES (?,?,'0912345678',?,'Sửa máy',?,'any',CURRENT_TIMESTAMP)",id,name,id+"@gmail.com",EnrollmentCourses.NAMES.get(0));
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,forwarded_at,forwarded_by) VALUES (?,?,?,'PROFILE_PENDING',CURRENT_TIMESTAMP,?)",UUID.randomUUID(),id,hr.id,hr.id);
        return id;
    }
    @BeforeEach void setup(){manager=actor("MANAGER");otherManager=actor("MANAGER");hr=actor("HR");student=actor("STUDENT");admin=actor("ADMIN");admission=admission("Nguyễn Anh Đức");}
    Map<String,Object> input(UUID source,Map<String,Object> details){return new LinkedHashMap<>(Map.of("consultationId",source.toString(),"startsOn","2026-12-01","paymentDueOn","2026-11-30","tuitionDue",13500000,"depositDue",500000,"terms","Điều khoản của mẫu hợp đồng","templateData",details));}
    ResultActions save(String code,String id,Map<String,Object> input)throws Exception{return mvc.perform(post("/api/workspace/screens/"+code+(id==null?"":"/"+id)).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    String create(Map<String,Object> details)throws Exception{return body(save("AddTrainingContract",null,input(admission,details)).andExpect(status().isOk()).andReturn()).path("id").asText();}
    JsonNode detail(String id)throws Exception{return body(mvc.perform(get("/api/workspace/screens/EditTrainingContract/"+id).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    ResultActions pdf(User actor,String id,boolean download)throws Exception{return mvc.perform(get("/api/workspace/contracts/"+id+"/pdf").param("download",Boolean.toString(download)).with(user(actor.email).roles(actor.getRoleCode())));}

    @Test void incompleteDocumentCanBeSavedAsADraftAndReportsExactlyWhichFieldsAreMissingForPdf()throws Exception{
        String id=create(Map.of("agreementPlace","Hà Nội"));
        var edit=detail(id);assertEquals("Hà Nội",edit.path("record").path("templateData").path("centerAddress").asText());
        assertEquals(5,edit.path("options").path("contractTemplate").path("sections").size());assertTrue(edit.path("canExportContract").asBoolean());
        pdf(manager,id,false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['templateData.centerTaxCode']").doesNotExist())
                .andExpect(jsonPath("$.fields['templateData.centerAddress']").doesNotExist()).andExpect(jsonPath("$.fields['templateData.studentDateOfBirth']").exists());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE id=?",Integer.class,UUID.fromString(id)));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void invalidDocumentInputRollsBackTheContractAndAdmissionState()throws Exception{
        save("AddTrainingContract",null,input(admission,Map.of("studentDateOfBirth","2026-02-30"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['templateData.studentDateOfBirth']").exists());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=?",Integer.class,admission));
        assertEquals("PROFILE_PENDING",jdbc.queryForObject("SELECT status FROM consultation_followups WHERE consultation_id=?",String.class,admission));
    }

    @Test void completedDocumentProducesAnInlinePdfAndADownloadWithTheSavedVietnameseValues()throws Exception{
        var details=ContractTemplateFixtures.completeDetails(templates);details.put("studentName","Tên do client giả mạo");details.put("initialTotal",1);
        details.put("centerTaxCode","FORGED-TAX-CODE");details.put("centerBankAccount","FORGED-BANK-ACCOUNT");
        String id=create(details);var stored=detail(id).path("record").path("templateData");
        assertEquals("Nguyễn Anh Đức",stored.path("studentName").asText());assertEquals(14000000,stored.path("initialTotal").asLong());
        jdbc.update("UPDATE training_contracts SET payment_schedule='LEGACY-SCHEDULE-MUST-NOT-APPEAR' WHERE id=?",UUID.fromString(id));
        var inline=pdf(manager,id,false).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline;"))).andExpect(header().string("Cache-Control","private, no-store")).andReturn();
        try(var document=Loader.loadPDF(inline.getResponse().getContentAsByteArray())){
            String text=new PDFTextStripper().getText(document);assertTrue(text.contains("Nguyễn Anh Đức"));assertTrue(text.contains("13.500.000"));
            assertFalse(text.replaceAll("\\s+","").contains("PHỤLỤC03TIẾPTHEO"));assertFalse(text.contains("Tên do client giả mạo"));assertFalse(text.contains("LEGACY-SCHEDULE-MUST-NOT-APPEAR"));
            assertFalse(text.contains("FORGED-TAX-CODE"));assertFalse(text.contains("FORGED-BANK-ACCOUNT"));
        }
        pdf(manager,id,true).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test void removedPaymentScheduleCannotBeEnteredAgainAndHistoricalNotesStayOutOfTheNewDocumentFlow()throws Exception{
        var input=input(admission,Map.of("agreementPlace","Hà Nội"));input.put("paymentSchedule","NEW-UNUSED-PAYMENT-NOTE");
        String id=body(save("AddTrainingContract",null,input).andExpect(status().isOk()).andReturn()).path("id").asText();
        assertEquals("",jdbc.queryForObject("SELECT payment_schedule FROM training_contracts WHERE id=?",String.class,UUID.fromString(id)));
        jdbc.update("UPDATE training_contracts SET payment_schedule='HISTORICAL-NOTE' WHERE id=?",UUID.fromString(id));
        input.put("version",0);save("EditTrainingContract",id,input).andExpect(status().isOk());
        assertEquals("HISTORICAL-NOTE",jdbc.queryForObject("SELECT payment_schedule FROM training_contracts WHERE id=?",String.class,UUID.fromString(id)));
    }

    @Test void exportEnforcesTheCurrentRoleGrantAndContractOwnershipBeforeReturningAnyMissingFields()throws Exception{
        String id=create(Map.of());
        pdf(otherManager,id,false).andExpect(status().isNotFound());pdf(student,id,false).andExpect(status().isNotFound());
        pdf(admin,id,false).andExpect(status().isForbidden());pdf(hr,id,false).andExpect(status().isNotFound());
        jdbc.update("UPDATE training_contracts SET student_id=? WHERE id=?",student.id,UUID.fromString(id));
        pdf(student,id,false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['templateData.studentDateOfBirth']").exists());
        jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='TrainingContractDetail'");
        // A reviewer still needs the original PDF while checking the signed scan.
        pdf(manager,id,false).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='ContractSignature'");
        pdf(manager,id,false).andExpect(status().isForbidden());
    }

    @Test void switchingTheSourceAdmissionClearsPreviousStudentDetailsAndStaleEditsCannotOverwriteTheNewSnapshot()throws Exception{
        String id=create(Map.of("centerAddress","Địa chỉ trung tâm","studentDateOfBirth","1999-02-03","studentIdentityNumber","OLD-STUDENT-IDENTITY"));
        UUID next=admission("Học viên mới");var input=input(next,Map.of("centerAddress","Địa chỉ trung tâm"));input.put("version",0);
        save("EditTrainingContract",id,input).andExpect(status().isOk());
        var record=detail(id).path("record");assertEquals("Học viên mới",record.path("templateData").path("studentName").asText());
        assertNotEquals("OLD-STUDENT-IDENTITY",record.path("templateData").path("studentIdentityNumber").asText());
        assertNotEquals("1999-02-03",record.path("templateData").path("studentDateOfBirth").asText());
        save("EditTrainingContract",id,input).andExpect(status().isConflict());
    }
}
