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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admission_documents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdmissionDocumentsIntegrationTest {
    static final byte[] PDF="%PDF-1.4\nScanned document".getBytes(StandardCharsets.US_ASCII);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    User hr,otherHr,manager,student;
    UUID admission;
    String email;

    User actor(String role) {
        var user=new User();user.email=UUID.randomUUID()+"@documents.test";user.fullName="Kiểm thử "+role;user.passwordHash="fixture";user.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        hr=actor("HR");otherHr=actor("HR");manager=actor("MANAGER");student=actor("STUDENT");admission=UUID.randomUUID();email=UUID.randomUUID()+"@gmail.com";
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,contact_time,created_date) VALUES (?,'Học viên hồ sơ','0912345678',?,'Nghề điện','any',CURRENT_TIMESTAMP)",admission,email);
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,document_due_on) VALUES (?,?,?,'DOCUMENT_APPOINTMENT',DATE '2026-12-20')",UUID.randomUUID(),admission,hr.id);
    }
    String actionPath(){return "/api/workspace/screens/ListAdmissionApplicationDetail/"+admission+"/actions/submit-profile";}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode pdf(User actor,String name)throws Exception {
        return body(upload(actor,name,PDF,"application/pdf").andExpect(status().isOk()).andReturn());
    }
    ResultActions upload(User actor,String name,byte[] bytes,String type)throws Exception {
        return mvc.perform(multipart("/api/workspace/files").file(new MockMultipartFile("file",name,type,bytes)).param("screen","AdmissionConsultation").with(user(actor.email).roles(actor.getRoleCode())).with(csrf()));
    }
    Map<String,Object> group(String type,String...files){return Map.of("type",type,"files",List.of(files));}
    Map<String,Object> input(List<Map<String,Object>> documents){return new LinkedHashMap<>(Map.of("version",0,"documents",documents,"note","Đã đối chiếu bản gốc"));}
    ResultActions submit(User actor,Map<String,Object> input)throws Exception {
        return mvc.perform(post(actionPath()).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    ResultActions update(User actor,Map<String,Object> input)throws Exception {
        return mvc.perform(post(actionPath().replace("submit-profile","update-profile")).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    JsonNode detail()throws Exception {
        return body(mvc.perform(get("/api/workspace/screens/ListAdmissionApplicationDetail/"+admission).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());
    }
    Map<String,Object> revision(List<Map<String,Object>> documents)throws Exception {
        var value=input(documents);value.put("version",detail().path("record").path("version").asLong());return value;
    }
    void completeAndForward()throws Exception {
        var groups=new LinkedHashMap<String,List<String>>();
        for(var document:detail().path("related").path("documents"))groups.computeIfAbsent(document.path("documentType").asText(),key->new ArrayList<>()).add(document.path("fileId").asText());
        boolean supplemented=false;
        for(String type:List.of("IDENTITY_CARD","CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM"))if(!groups.containsKey(type)){groups.put(type,List.of(pdf(hr,type+".pdf").path("id").asText()));supplemented=true;}
        if(supplemented){var documents=new ArrayList<Map<String,Object>>();for(var group:groups.entrySet())documents.add(Map.of("type",group.getKey(),"files",group.getValue()));update(hr,revision(documents)).andExpect(status().isOk());}
        mvc.perform(post("/api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles").with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("admissions",List.of(Map.of("id",admission.toString(),"version",detail().path("record").path("version").asLong())))))).andExpect(status().isOk());
    }
    ResultActions download(User actor,String file)throws Exception {
        return mvc.perform(get("/api/workspace/files/"+file).with(user(actor.email).roles(actor.getRoleCode())));
    }

    @Test void severalPdfsPerSelectedTypeRemainCategorizedAndManagersCanDownloadEveryFile()throws Exception {
        String front=pdf(hr,"cccd-truoc.pdf").path("id").asText(),back=pdf(hr,"cccd-sau.pdf").path("id").asText(),birth=pdf(hr,"khai-sinh.pdf").path("id").asText();
        download(manager,back).andExpect(status().isNotFound());
        submit(hr,input(List.of(group("IDENTITY_CARD",front,back),group("BIRTH_CERTIFICATE",birth)))).andExpect(status().isOk());
        JsonNode detail=body(mvc.perform(get("/api/workspace/screens/ListAdmissionApplicationDetail/"+admission).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());
        assertEquals("PROFILE_PENDING",detail.path("record").path("status").asText());assertEquals(3,detail.path("related").path("documents").size());
        assertEquals("DOCUMENTS_INCOMPLETE",detail.path("record").path("documentStatus").asText());assertFalse(detail.path("canConsult").asBoolean());assertTrue(detail.path("canEditProfile").asBoolean());
        assertEquals("IDENTITY_CARD",detail.path("related").path("documents").get(1).path("documentType").asText());
        assertEquals(back,detail.path("related").path("documents").get(1).path("fileId").asText());
        assertEquals("BIRTH_CERTIFICATE",detail.path("related").path("documents").get(2).path("documentType").asText());
        assertTrue(detail.path("related").path("history").get(0).path("content").asText().contains("3 tệp PDF"));
        assertTrue(detail.path("related").path("history").get(0).path("content").asText().contains("Đã đối chiếu bản gốc"));
        completeAndForward();
        mvc.perform(get("/api/workspace/student-accounts").param("q",email).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].profileDocuments.length()").value(6));
        mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options.admissions[0].profileDocuments.length()").value(6));
        for(String file:List.of(front,back,birth))download(manager,file).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        download(student,back).andExpect(status().isNotFound());download(otherHr,birth).andExpect(status().isNotFound());
        submit(hr,input(List.of(group("IDENTITY_CARD",front)))).andExpect(status().isConflict());
    }

    @Test void emptyCategoriesUnknownTypesRepeatedTypesAndRepeatedFilesAreRejectedAtomically()throws Exception {
        String file=pdf(hr,"cccd.pdf").path("id").asText();
        List<List<Map<String,Object>>> invalid=List.of(List.of(),List.of(group("IDENTITY_CARD")),List.of(group("UNKNOWN",file)),List.of(group("LEGACY_SCAN",file)),
                List.of(group("IDENTITY_CARD",file),group("IDENTITY_CARD",file)),List.of(group("IDENTITY_CARD",file,file)),List.of(group("IDENTITY_CARD",file),group("ID_PHOTO",file)),
                List.of(group("IDENTITY_CARD",Collections.nCopies(51,file).toArray(String[]::new))));
        for(var documents:invalid)submit(hr,input(documents)).andExpect(status().isBadRequest());
        submit(hr,Map.of("version",0,"profileEvidence","file:"+file+":cccd.pdf")).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM admission_profile_documents",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=?",Integer.class,admission));
        assertEquals("DOCUMENT_APPOINTMENT",jdbc.queryForObject("SELECT status FROM consultation_followups WHERE consultation_id=?",String.class,admission));
    }

    @Test void everyFileMustExistBelongToTheSenderAndBeAPdfBeforeAnyDocumentIsStored()throws Exception {
        String own=pdf(hr,"own.pdf").path("id").asText(),foreign=pdf(otherHr,"foreign.pdf").path("id").asText();
        UUID image=UUID.randomUUID();byte[] png={(byte)137,80,78,71,13,10,26,10};
        jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,?,'image/png',?,?)",image,hr.id,"old.png",png.length,png);
        for(String file:List.of("not-an-id",UUID.randomUUID().toString(),foreign,image.toString()))
            submit(hr,input(List.of(group("IDENTITY_CARD",own),group("ID_PHOTO",file)))).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM admission_profile_documents",Integer.class));
        assertNull(jdbc.queryForObject("SELECT scan_file_id FROM consultation_followups WHERE consultation_id=?",UUID.class,admission));
    }

    @Test void admissionUploadsOnlyAcceptPdfsAndKeepTheExistingPerFileLimit()throws Exception {
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        upload(hr,"photo.pdf",png,"application/pdf").andExpect(status().isBadRequest());
        upload(hr,"empty.pdf",new byte[0],"application/pdf").andExpect(status().isBadRequest());
        upload(hr,"large.pdf",new byte[2097153],"application/pdf").andExpect(status().isBadRequest());
        upload(manager,"cccd.pdf",PDF,"application/pdf").andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM workflow_files",Integer.class));
    }

    @Test void permissionsCsrfCurrentVersionAndDocumentAppointmentAreRequired()throws Exception {
        String file=pdf(hr,"cccd.pdf").path("id").asText();var input=input(List.of(group("IDENTITY_CARD",file)));
        submit(otherHr,input).andExpect(status().isNotFound());submit(manager,input).andExpect(status().isForbidden());
        mvc.perform(post(actionPath()).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        input.put("version",1);submit(hr,input).andExpect(status().isConflict());input.put("version",0);
        jdbc.update("UPDATE consultation_followups SET status='NEEDS_COUNSELING' WHERE consultation_id=?",admission);submit(hr,input).andExpect(status().isConflict());
        jdbc.update("UPDATE consultation_followups SET status='DOCUMENT_APPOINTMENT' WHERE consultation_id=?",admission);
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");
        submit(hr,input).andExpect(status().isForbidden());upload(hr,"more.pdf",PDF,"application/pdf").andExpect(status().isForbidden());
    }

    @Test void allCategorizedFilesRemainAvailableToTheContractOwnerAfterContractCreation()throws Exception {
        String first=pdf(hr,"ly-lich-1.pdf").path("id").asText(),second=pdf(hr,"ly-lich-2.pdf").path("id").asText();
        submit(hr,input(List.of(group("CURRICULUM_VITAE",first,second)))).andExpect(status().isOk());
        completeAndForward();
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',3,20,1000,'Training','OPEN',?,20)",course,course.toString(),"Khóa hồ sơ",manager.id);
        var contractInput=Map.of("consultationId",admission.toString(),"courseId",course.toString(),"startsOn","2026-12-20","paymentDueOn","2026-12-19","tuitionDue",1000,"depositDue",0,"terms","Điều khoản");
        String contract=body(mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contractInput))).andExpect(status().isOk()).andReturn()).path("id").asText();
        mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+contract).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.related.documents.length()").value(6));
        download(manager,second).andExpect(status().isOk());download(actor("MANAGER"),second).andExpect(status().isNotFound());
        String supplement=pdf(hr,"bo-sung.pdf").path("id").asText();
        update(hr,revision(List.of(group("CURRICULUM_VITAE",first,second),group("BIRTH_CERTIFICATE",supplement)))).andExpect(status().isOk());
        assertEquals("CONTRACTED",detail().path("record").path("status").asText());
        mvc.perform(get("/api/workspace/screens/TrainingContractDetail/"+contract).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andExpect(jsonPath("$.related.documents.length()").value(3));
        download(manager,supplement).andExpect(status().isOk());
    }

    @Test void incompleteProfilesCanBeCompletedEditedAndMadeIncompleteAgainWithAccurateFiltersAndHistory()throws Exception {
        String identity=pdf(hr,"cccd.pdf").path("id").asText();
        submit(hr,input(List.of(group("IDENTITY_CARD",identity)))).andExpect(status().isOk());
        String submitted=detail().path("record").path("profileSubmittedAt").asText();
        var groups=new ArrayList<Map<String,Object>>();groups.add(group("IDENTITY_CARD",identity));
        for(String type:List.of("CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM"))groups.add(group(type,pdf(hr,type+".pdf").path("id").asText()));
        update(hr,revision(groups)).andExpect(status().isOk());
        assertEquals("DOCUMENTS_COMPLETE",detail().path("record").path("documentStatus").asText());
        assertFalse(detail().path("canConsult").asBoolean());assertTrue(detail().path("canEditProfile").asBoolean());
        mvc.perform(get("/api/workspace/screens/ListAdmissionEnrollment").param("q",email).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q",email).param("status","DOCUMENTS_INCOMPLETE").with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        completeAndForward();
        String replacement=pdf(hr,"cccd-moi.pdf").path("id").asText();groups.set(0,group("IDENTITY_CARD",replacement));
        update(hr,revision(groups)).andExpect(status().isOk());assertEquals(5,detail().path("related").path("documents").size());
        download(manager,replacement).andExpect(status().isOk());download(manager,identity).andExpect(status().isNotFound());
        groups.removeIf(group->group.get("type").equals("ID_PHOTO"));
        update(hr,revision(groups)).andExpect(status().isOk());assertEquals("DOCUMENTS_INCOMPLETE",detail().path("record").path("documentStatus").asText());
        update(hr,revision(List.of())).andExpect(status().isOk());
        JsonNode cleared=detail();assertEquals(0,cleared.path("related").path("documents").size());assertTrue(cleared.path("record").path("profileEvidence").isNull());
        assertEquals(submitted,cleared.path("record").path("profileSubmittedAt").asText());assertTrue(cleared.path("canEditProfile").asBoolean());assertFalse(cleared.path("canConsult").asBoolean());
        assertEquals(6,cleared.path("related").path("history").size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='DOCUMENTS_INCOMPLETE' AND content LIKE '%0 tệp PDF%'",Integer.class,admission));
    }

    @Test void editsValidateTheWholeSnapshotScopeVersionOwnershipAndCsrfBeforeChangingAnything()throws Exception {
        String own=pdf(hr,"own.pdf").path("id").asText(),foreign=pdf(otherHr,"foreign.pdf").path("id").asText();
        update(hr,input(List.of(group("IDENTITY_CARD",own)))).andExpect(status().isConflict());
        submit(hr,input(List.of(group("IDENTITY_CARD",own)))).andExpect(status().isOk());
        update(hr,input(List.of(group("IDENTITY_CARD",own)))).andExpect(status().isConflict());
        var current=revision(List.of(group("IDENTITY_CARD",own)));
        update(otherHr,current).andExpect(status().isNotFound());update(manager,current).andExpect(status().isForbidden());
        mvc.perform(post(actionPath().replace("submit-profile","update-profile")).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(current))).andExpect(status().isForbidden());
        update(hr,Map.of("version",current.get("version"))).andExpect(status().isBadRequest());
        for(var invalid:List.of(List.of(group("IDENTITY_CARD",own),group("ID_PHOTO",foreign)),List.of(group("IDENTITY_CARD",own,own)),List.of(group("UNKNOWN",own)),List.of(group("LEGACY_SCAN",own))))
            update(hr,revision(invalid)).andExpect(status().isBadRequest());
        assertEquals(1,detail().path("related").path("documents").size());assertEquals(1,detail().path("record").path("version").asLong());
        assertEquals(1,detail().path("related").path("history").size());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");
        update(hr,current).andExpect(status().isForbidden());
    }

    @Test void previouslySubmittedLegacyImagesArePreservedDuringEditsWithoutCountingAsARequiredDocumentType()throws Exception {
        UUID old=UUID.randomUUID();byte[] png={(byte)137,80,78,71,13,10,26,10};
        jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,?,'image/png',?,?)",old,hr.id,"old.png",png.length,png);
        jdbc.update("INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by) VALUES (?,?,'LEGACY_SCAN',?,?)",UUID.randomUUID(),admission,old,hr.id);
        jdbc.update("UPDATE consultation_followups SET status='PROFILE_PENDING',scan_file_id=?,profile_submitted_at=CURRENT_TIMESTAMP WHERE consultation_id=?",old,admission);
        String pdf=pdf(hr,"cccd.pdf").path("id").asText();
        update(hr,revision(List.of(group("IDENTITY_CARD",pdf),group("LEGACY_SCAN",old.toString())))).andExpect(status().isOk());
        assertEquals("DOCUMENTS_INCOMPLETE",detail().path("record").path("documentStatus").asText());assertEquals(2,detail().path("related").path("documents").size());
        completeAndForward();
        download(manager,old.toString()).andExpect(status().isOk()).andExpect(content().bytes(png));
    }

    @Test void editingPdfsAfterAccountCreationPreservesTheAccountLinkAndProcessingStage()throws Exception {
        String original=pdf(hr,"original.pdf").path("id").asText();submit(hr,input(List.of(group("IDENTITY_CARD",original)))).andExpect(status().isOk());
        jdbc.update("UPDATE consultation_followups SET status='PROFILE_CREATED',profile_student_id=?,profile_created_at=CURRENT_TIMESTAMP WHERE consultation_id=?",student.id,admission);
        String replacement=pdf(hr,"replacement.pdf").path("id").asText();
        update(hr,revision(List.of(group("IDENTITY_CARD",replacement)))).andExpect(status().isOk());
        var view=detail();assertEquals("PROFILE_CREATED",view.path("record").path("status").asText());
        assertEquals(student.id.longValue(),view.path("record").path("profileStudentId").asLong());assertTrue(users.findById(student.id).isPresent());
        assertEquals("DOCUMENTS_INCOMPLETE",view.path("record").path("documentStatus").asText());assertFalse(view.path("canConsult").asBoolean());
    }
}
