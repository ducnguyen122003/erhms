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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admission_dashboards;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdmissionDashboardsIntegrationTest {
    static final String INTAKE="ListAdmissionApplication",ENROLLMENT="ListAdmissionEnrollment";
    static final List<String> TYPES=List.of("IDENTITY_CARD","CURRICULUM_VITAE","BIRTH_CERTIFICATE","ID_PHOTO","APPLICATION_FORM");
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired RoleRepository roles;
    User hr,otherHr,manager,student;String term;
    User actor(String role){var user=new User();user.email=UUID.randomUUID()+"@dashboards.test";user.fullName="Kiểm thử "+role;user.passwordHash="fixture";user.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(user);}
    @BeforeEach void setup(){hr=actor("HR");otherHr=actor("HR");manager=actor("MANAGER");student=actor("STUDENT");term=UUID.randomUUID().toString();}
    UUID admission(User owner,String status,int count){
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,contact_time,created_date) VALUES (?,?,'0912345678',?,'Khóa điện','any',CURRENT_TIMESTAMP)",id,term+" Học viên",id+"@gmail.com");
        jdbc.update("INSERT INTO consultation_followups(id,consultation_id,assigned_to,status,profile_submitted_at) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,owner==null?null:owner.id,status,count>0?java.time.OffsetDateTime.now():null);
        for(int index=0;index<count;index++)document(id,TYPES.get(index%5));return id;
    }
    void document(UUID admission,String type){UUID file=UUID.randomUUID();byte[] bytes="%PDF-1.4".getBytes(StandardCharsets.US_ASCII);jdbc.update("INSERT INTO workflow_files(id,uploaded_by,filename,content_type,file_size,file_data) VALUES (?,?,'scan.pdf','application/pdf',?,?)",file,hr.id,bytes.length,bytes);jdbc.update("INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by) VALUES (?,?,?,?,?)",UUID.randomUUID(),admission,type,file,hr.id);}
    UUID contract(UUID admission,String status,boolean handedOver,int due,int paid){
        jdbc.update("UPDATE consultation_followups SET forwarded_at=CURRENT_TIMESTAMP,forwarded_by=? WHERE consultation_id=?",hr.id,admission);
        String email=jdbc.queryForObject("SELECT email FROM consultation_registrations WHERE id=?",String.class,admission);
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO training_contracts(id,code,consultation_id,student_email,applicant_name,status,manager_id,tuition_due,handed_over_at,student_id) VALUES (?,?,?,?,'Học viên',?,?,?,?,?)",id,"HD-"+id,admission,email,status,manager.id,due,handedOver?java.time.OffsetDateTime.now():null,Set.of("DRAFT","CANCELLED").contains(status)?null:student.id);
        if(paid>0)jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status) VALUES (?,?,'TUITION',?,'CONFIRMED')",UUID.randomUUID(),id,paid);
        return id;
    }
    ResultActions request(User actor,String code,String search,String status,int page)throws Exception{return mvc.perform(get("/api/workspace/screens/"+code).param("q",search).param("status",status).param("page",String.valueOf(page)).with(user(actor.email).roles(actor.getRoleCode())));}
    JsonNode list(String code,String status)throws Exception{return body(request(hr,code,term,status,0).andExpect(status().isOk()).andReturn());}
    JsonNode tracking(String status)throws Exception{return body(mvc.perform(get("/api/workspace/screens/"+ENROLLMENT).param("q",term).param("status",status).param("tab","forwarded").with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn());}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    Set<String> ids(JsonNode list){var values=new HashSet<String>();list.path("items").forEach(item->values.add(item.path("id").asText()));return values;}
    JsonNode row(String code,UUID id)throws Exception{for(var item:tracking("").path("items"))if(item.path("id").asText().equals(id.toString()))return item;throw new AssertionError("Missing "+id);}

    @Test void dashboardsPartitionByFiveDistinctDocumentTypesRespectOwnershipAndMoveAfterEdits()throws Exception{
        UUID fresh=admission(null,"NEW",0),counseling=admission(hr,"NEEDS_COUNSELING",0),appointment=admission(hr,"DOCUMENT_APPOINTMENT",0),partial=admission(hr,"PROFILE_PENDING",2),complete=admission(hr,"PROFILE_PENDING",5),stopped=admission(hr,"NOT_PROCEEDING",0);
        UUID foreign=admission(otherHr,"PROFILE_PENDING",5);admission(otherHr,"NEW",0);
        assertEquals(Set.of(fresh.toString(),counseling.toString(),appointment.toString(),partial.toString(),stopped.toString()),ids(list(INTAKE,"")));
        assertEquals(Set.of(complete.toString()),ids(list(ENROLLMENT,"")));assertFalse(ids(list(ENROLLMENT,"")).contains(foreign.toString()));
        var stats=list(INTAKE,"").path("stats");assertEquals(5,stats.get(0).path("value").asInt());assertEquals(2,stats.get(1).path("value").asInt());assertEquals(1,stats.get(2).path("value").asInt());assertEquals(1,stats.get(3).path("value").asInt());
        document(partial,"IDENTITY_CARD");assertEquals(1,list(ENROLLMENT,"").path("total").asInt());
        for(String type:TYPES.subList(2,5))document(partial,type);
        assertEquals(Set.of(partial.toString(),complete.toString()),ids(list(ENROLLMENT,"")));assertFalse(ids(list(INTAKE,"")).contains(partial.toString()));
        jdbc.update("DELETE FROM admission_profile_documents WHERE consultation_id=? AND document_type='ID_PHOTO'",partial);
        assertTrue(ids(list(INTAKE,"DOCUMENTS_INCOMPLETE")).contains(partial.toString()));assertEquals(Set.of(complete.toString()),ids(list(ENROLLMENT,"")));
    }

    @Test void enrollmentTracksContractsAccountsAndConfirmedTuitionWithoutPublishingUnhandedOverAmounts()throws Exception{
        UUID noContract=admission(hr,"PROFILE_PENDING",5),draft=admission(hr,"CONTRACTED",5),signed=admission(hr,"PROFILE_CREATED",5),partial=admission(hr,"PROFILE_CREATED",5),paid=admission(hr,"PROFILE_CREATED",5);
        jdbc.update("UPDATE consultation_followups SET forwarded_at=CURRENT_TIMESTAMP,forwarded_by=? WHERE consultation_id=?",hr.id,noContract);
        contract(draft,"DRAFT",false,9000,0);contract(signed,"SIGNED",false,5000,0);contract(partial,"ACTIVE",true,1000,400);contract(paid,"ACTIVE",true,1000,1000);
        for(UUID id:List.of(signed,partial,paid))jdbc.update("UPDATE consultation_followups SET profile_student_id=? WHERE consultation_id=?",student.id,id);
        UUID partialContract=jdbc.queryForObject("SELECT id FROM training_contracts WHERE consultation_id=?",UUID.class,partial);
        for(String state:List.of("PENDING","MISMATCH"))jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status) VALUES (?,?,'TUITION',9000,?)",UUID.randomUUID(),partialContract,state);
        jdbc.update("INSERT INTO financial_transactions(id,contract_id,kind,amount,status) VALUES (?,?,'DEPOSIT',5000,'CONFIRMED')",UUID.randomUUID(),partialContract);
        assertEquals("NO_CONTRACT",row(ENROLLMENT,noContract).path("contractProgress").asText());
        var draftRow=row(ENROLLMENT,draft);assertEquals("WAIT_SIGNATURE",draftRow.path("contractProgress").asText());assertEquals("WAIT_ACCOUNT",draftRow.path("accountProgress").asText());assertTrue(draftRow.path("tuitionDue").isNull());assertTrue(draftRow.path("tuitionRemaining").isNull());
        var signedRow=row(ENROLLMENT,signed);assertEquals("SIGNED",signedRow.path("contractProgress").asText());assertEquals("ACCOUNT_CREATED",signedRow.path("accountProgress").asText());assertEquals("WAIT_HANDOVER",signedRow.path("tuitionProgress").asText());
        var partialRow=row(ENROLLMENT,partial);assertEquals("PARTIALLY_PAID",partialRow.path("tuitionProgress").asText());assertEquals(600,partialRow.path("tuitionRemaining").asInt());assertEquals("PAID",row(ENROLLMENT,paid).path("tuitionProgress").asText());
        request(hr,"PaymentStatus_Admissions",partialRow.path("email").asText(),"",0).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        assertEquals(Set.of(draft.toString()),ids(tracking("WAIT_SIGNATURE")));assertEquals(Set.of(noContract.toString(),draft.toString()),ids(tracking("WAIT_ACCOUNT")));
        assertEquals(Set.of(paid.toString()),ids(tracking("PAID")));assertEquals(Set.of(partial.toString()),ids(tracking("PARTIALLY_PAID")));
        var stats=tracking("").path("stats");assertEquals(5,stats.get(0).path("value").asInt());assertEquals(2,stats.get(1).path("value").asInt());assertEquals(2,stats.get(2).path("value").asInt());assertEquals(4,stats.get(3).path("value").asInt());
    }

    @Test void multipleContractsNeverDuplicateApplicantsAndOverpaymentDoesNotCoverAnotherContract()throws Exception{
        UUID id=admission(hr,"CONTRACTED",5);contract(id,"CANCELLED",true,9999,0);contract(id,"DRAFT",false,10000,0);contract(id,"ACTIVE",true,1000,1300);contract(id,"ACTIVE",true,1000,0);
        var view=tracking("");assertEquals(1,view.path("total").asInt());assertEquals(1,view.path("items").size());
        var row=view.path("items").get(0);assertEquals(3,row.path("contractCount").asInt());assertEquals(1,row.path("unsignedContractCount").asInt());assertEquals(2000,row.path("tuitionDue").asInt());assertEquals(1000,row.path("tuitionRemaining").asInt());
        assertEquals(1,view.path("stats").get(0).path("value").asInt());
        UUID awaitingHandover=admission(hr,"CONTRACTED",5);contract(awaitingHandover,"ACTIVE",true,1000,1000);contract(awaitingHandover,"DRAFT",false,2000,0);
        assertEquals("WAIT_HANDOVER",row(ENROLLMENT,awaitingHandover).path("tuitionProgress").asText());
    }

    @Test void searchPaginationFiltersAndRevokedOrSpoofedGrantsStayWithinTheChosenDashboard()throws Exception{
        for(int index=0;index<27;index++)admission(hr,"PROFILE_PENDING",5);
        var first=body(request(hr,ENROLLMENT,term,"",0).andExpect(status().isOk()).andReturn());
        var second=body(request(hr,ENROLLMENT,term,"",1).andExpect(status().isOk()).andReturn());
        assertEquals(27,first.path("total").asInt());assertEquals(25,first.path("items").size());assertEquals(2,second.path("items").size());assertEquals(27,second.path("stats").get(0).path("value").asInt());
        assertTrue(Collections.disjoint(ids(first),ids(second)));request(hr,ENROLLMENT,"%", "",0).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        request(hr,ENROLLMENT,term,"DOCUMENTS_INCOMPLETE",0).andExpect(status().isBadRequest());request(hr,INTAKE,term,"WAIT_SIGNATURE",0).andExpect(status().isBadRequest());
        UUID incomplete=admission(hr,"NEEDS_COUNSELING",0);
        mvc.perform(get("/api/workspace/screens/"+ENROLLMENT+"/"+incomplete).with(user(hr.email).roles("HR"))).andExpect(status().isNotFound());
        for(User actor:List.of(manager,student)){
            jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,?)",actor.getRoleId(),ENROLLMENT);
            request(actor,ENROLLMENT,term,"",0).andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code=?",ENROLLMENT);
        request(hr,ENROLLMENT,term,"",0).andExpect(status().isForbidden());request(hr,INTAKE,term,"",0).andExpect(status().isOk());
    }
}
