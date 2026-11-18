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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admission_forwarding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdmissionForwardingIntegrationTest {
    static final String PATH="/api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles";
    static final List<String> TYPES=List.of("IDENTITY_CARD","CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM");
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;
    User hr,otherHr,manager;String term;
    User actor(String role){var value=new User();value.email=UUID.randomUUID()+"@forward.test";value.fullName="Kiểm thử "+role;value.passwordHash="fixture";value.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(value);}
    @BeforeEach void setup(){hr=actor("HR");otherHr=actor("HR");manager=actor("MANAGER");term=UUID.randomUUID().toString();}
    UUID admission(User owner,int count){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,contact_time,created_date) VALUES (?,?,'0912345678',?,'Điện','any',CURRENT_TIMESTAMP)",id,term+" Học viên",id+"@gmail.com");
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,profile_submitted_at) VALUES (?,?,?,'PROFILE_PENDING',CURRENT_TIMESTAMP)",UUID.randomUUID(),id,owner.id);
        for(int index=0;index<count;index++){
            UUID file=UUID.randomUUID();byte[] bytes="%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
            jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,'scan.pdf','application/pdf',?,?)",file,owner.id,bytes.length,bytes);
            jdbc.update("INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,TYPES.get(index),file,owner.id);
        }
        return id;
    }
    Map<String,Object> item(UUID id,long version){return Map.of("id",id.toString(),"version",version);}
    Map<String,Object> input(Map<String,Object>...items){return Map.of("admissions",List.of(items));}
    ResultActions forward(User actor,Map<String,Object> input)throws Exception{return mvc.perform(post(PATH).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode page(String tab)throws Exception{return body(mvc.perform(get("/api/workspace/screens/ListAdmissionEnrollment").param("q",term).param("tab",tab).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());}
    JsonNode queue()throws Exception{return body(mvc.perform(get("/api/workspace/student-accounts").param("q",term).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    JsonNode detail(UUID id)throws Exception{return body(mvc.perform(get("/api/workspace/screens/ListAdmissionApplicationDetail/"+id).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());}
    Map<String,Object> contract(UUID admission){
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,'Điện',3,20,1000,'Training','OPEN',?,20)",course,course.toString(),term,manager.id);
        return Map.of("consultationId",admission.toString(),"courseId",course.toString(),"startsOn","2026-12-20","paymentDueOn","2026-12-19","tuitionDue",1000,"depositDue",0,"terms","Điều khoản");
    }

    @Test void completeProfilesWaitForHrBeforeAcademicQueueDocumentsContractsAndAccountsBecomeAvailable()throws Exception{
        UUID id=admission(hr,5);String file=detail(id).path("related").path("documents").get(0).path("fileId").asText();
        assertEquals(1,page("waiting").path("total").asInt());assertEquals(0,page("forwarded").path("total").asInt());assertEquals(0,queue().path("total").asInt());
        mvc.perform(get("/api/workspace/files/"+file).with(user(manager.email).roles("MANAGER"))).andExpect(status().isNotFound());
        var contract=contract(id);
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.consultationId").exists());
        mvc.perform(post("/api/workspace/student-accounts/"+id).with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("expectedEmail",id+"@gmail.com","version",0)))).andExpect(status().isConflict());
        forward(hr,input(item(id,0))).andExpect(status().isOk()).andExpect(jsonPath("$.forwardedCount").value(1));
        var record=detail(id).path("record");assertFalse(record.path("forwardedAt").isNull());assertEquals(hr.id.longValue(),record.path("forwardedBy").asLong());assertEquals(1,record.path("version").asLong());
        assertEquals(0,page("waiting").path("total").asInt());assertEquals(1,page("forwarded").path("total").asInt());assertEquals(1,queue().path("total").asInt());
        mvc.perform(get("/api/workspace/files/"+file).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk());
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='FORWARDED'",Integer.class,id));
    }

    @Test void batchForwardingIsAtomicAndRejectsIncompleteStaleDuplicateForeignAndRepeatedRecords()throws Exception{
        UUID first=admission(hr,5),second=admission(hr,5),partial=admission(hr,4),foreign=admission(otherHr,5);
        forward(hr,input(item(first,0),item(second,1))).andExpect(status().isConflict());
        forward(hr,input(item(first,0),item(partial,0))).andExpect(status().isConflict());
        forward(hr,input(item(first,0),item(foreign,0))).andExpect(status().isNotFound());
        forward(hr,input(item(first,0),item(first,0))).andExpect(status().isBadRequest());forward(hr,input()).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_followups WHERE forwarded_at IS NOT NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE outcome='FORWARDED'",Integer.class));
        forward(hr,input(item(first,0),item(second,0))).andExpect(status().isOk()).andExpect(jsonPath("$.forwardedCount").value(2));
        forward(hr,input(item(first,1))).andExpect(status().isConflict());assertEquals(2,queue().path("total").asInt());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE outcome='FORWARDED'",Integer.class));
    }

    @Test void forwardingRequiresBothHrPermissionsCsrfAndTheAssignedScope()throws Exception{
        UUID id=admission(hr,5);var input=input(item(id,0));
        forward(otherHr,input).andExpect(status().isNotFound());forward(manager,input).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='AdmissionConsultation'");forward(hr,input).andExpect(status().isForbidden());assertFalse(page("waiting").path("canForward").asBoolean());
        jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (7,'AdmissionConsultation')");
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='ListAdmissionEnrollment'");forward(hr,input).andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("SELECT forwarded_at FROM consultation_followups WHERE consultation_id=?",Object.class,id));
    }

    @Test void forwardedCasesKeepTheirTrackingPageAndAuditWhenPdfsAreLaterEdited()throws Exception{
        UUID id=admission(hr,5);forward(hr,input(item(id,0))).andExpect(status().isOk());String date=detail(id).path("record").path("forwardedAt").asText();
        var documents=new ArrayList<Map<String,Object>>();
        for(var document:detail(id).path("related").path("documents"))if(!document.path("documentType").asText().equals("ID_PHOTO"))documents.add(Map.of("type",document.path("documentType").asText(),"files",List.of(document.path("fileId").asText())));
        mvc.perform(post("/api/workspace/screens/ListAdmissionApplicationDetail/"+id+"/actions/update-profile").with(user(hr.email).roles("HR")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",1,"documents",documents)))).andExpect(status().isOk());
        assertEquals(date,detail(id).path("record").path("forwardedAt").asText());assertEquals("DOCUMENTS_INCOMPLETE",detail(id).path("record").path("documentStatus").asText());
        assertEquals(1,page("forwarded").path("total").asInt());assertEquals(0,page("waiting").path("total").asInt());assertEquals(1,queue().path("total").asInt());
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q",term).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
}
