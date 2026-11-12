package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.onboarding.StudentAccountMailer;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.WorkflowException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
        return id.toString();
    }
    private ResultActions create(User actor,String id,Map<String,Object> input)throws Exception {
        return mvc.perform(post(API+"/"+id).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    private ResultActions create(String id,String email)throws Exception {return create(manager,id,Map.of("expectedEmail",email,"version",0));}
    private JsonNode body(MvcResult result)throws Exception {return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    private JsonNode list()throws Exception {return body(mvc.perform(get(API).param("q",unique).with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());}
    private JsonNode row(String id)throws Exception {for(JsonNode row:list().path("items"))if(row.path("id").asText().equals(id))return row;throw new AssertionError("Missing admission "+id);}

    @Test void onlyManagersWithTheScreenGrantCanListAndCreateEvenWithSpoofedOrAccidentalGrants()throws Exception {
        String email=unique+"@student.test",id=admission("CONTACTED",email);
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

    @Test void queueContainsOnlyContactedProfilesAndSupportsSearchAndStablePagination()throws Exception {
        String first=admission("CONTACTED",unique+"-first@student.test"),second=admission("CONTACTED",unique+"-second@student.test");
        for(String state:List.of("NEW","APPOINTMENT","CLOSED","CONTRACTED"))admission(state,unique+"-"+state+"@student.test");
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
            String id=admission("CONTACTED",email);JsonNode item=row(id);
            assertEquals("INVALID_EMAIL",item.path("accountStatus").asText());assertFalse(item.path("canCreate").asBoolean());assertFalse(item.path("blockedReason").asText().isBlank());
            create(id,email==null||email.isEmpty()?"missing@student.test":email).andExpect(status().is4xxClientError());
        }
        for(String state:List.of("STUDENT","OTHER_ROLE","DISABLED","LOCKED")) {
            String email=unique+"-"+state.toLowerCase(Locale.ROOT)+"@student.test";
            User existing=account(state.equals("OTHER_ROLE")?"HR":"STUDENT",email.toUpperCase(Locale.ROOT));
            if(state.equals("DISABLED"))jdbc.update("UPDATE m_user SET account_enabled=FALSE WHERE user_id=?",existing.id);
            if(state.equals("LOCKED"))jdbc.update("UPDATE m_user SET account_locked=TRUE WHERE user_id=?",existing.id);
            String id=admission("CONTACTED"," "+email+" ");JsonNode item=row(id);
            assertEquals(state,item.path("accountStatus").asText());assertEquals(existing.id,item.path("accountId").asLong());assertFalse(item.path("canCreate").asBoolean());
            create(id,email).andExpect(status().isConflict());assertEquals("existing-hash",users.findById(existing.id).orElseThrow().passwordHash);
        }
        verifyNoInteractions(mailer);
    }

    @Test void creationUsesStoredProfileCreatesOnlyStudentAndSendsAWorkingSecretWithoutReturningOrAuditingIt()throws Exception {
        String email=unique+"@student.test",id=admission("CONTACTED"," "+email.toUpperCase(Locale.ROOT)+" ");
        MvcResult result=create(id,email).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        long accountId=body(result).path("accountId").asLong();User created=users.findWithRoleById(accountId).orElseThrow();
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
        assertEquals("STUDENT",row(id).path("accountStatus").asText());create(id,email).andExpect(status().isConflict());verify(mailer,times(1)).sendCredentials(anyString(),anyString(),anyString());
        JsonNode contractOptions=body(mvc.perform(get("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER"))).andExpect(status().isOk()).andReturn());
        boolean selectable=false;for(JsonNode option:contractOptions.path("options").path("students"))if(option.path("id").asLong()==accountId)selectable=true;
        assertTrue(selectable,"Normalized provisioned email must match the original admission in contract options");
        UUID course=UUID.randomUUID();jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,?,3,20,1000,'Training','ACTIVE',?,20)",course,course.toString(),unique+" Course","Nghề",manager.id);
        var contract=Map.of("consultationId",id,"courseId",course.toString(),"studentId",accountId,"startsOn","2026-12-15","paymentDueOn","2026-12-14","tuitionDue",1000,"depositDue",0,"terms","Điều khoản đào tạo");
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(contract))).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND student_id=? AND manager_id=?",Integer.class,UUID.fromString(id),accountId,manager.id));
    }

    @Test void changedEmailVersionOrContactStateRequiresReloadAndArbitraryAccountFieldsAreRejected()throws Exception {
        String email=unique+"@student.test",id=admission("CONTACTED",email);
        jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",unique+"-updated@student.test",UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        jdbc.update("UPDATE consultation_registrations SET email=? WHERE id=?",email,UUID.fromString(id));
        jdbc.update("UPDATE consultation_followups SET version=1 WHERE consultation_id=?",UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        for(String state:List.of("APPOINTMENT","CLOSED","CONTRACTED")) {
            jdbc.update("UPDATE consultation_followups SET version=0,status=? WHERE consultation_id=?",state,UUID.fromString(id));create(id,email).andExpect(status().isConflict());
        }
        jdbc.update("UPDATE consultation_followups SET status='CONTACTED' WHERE consultation_id=?",UUID.fromString(id));
        for(String field:List.of("roleId","password","recipient","email","fullName")) {
            var input=new LinkedHashMap<String,Object>(Map.of("expectedEmail",email,"version",0));input.put(field,"forged");
            create(manager,id,input).andExpect(status().isBadRequest());
        }
        create(UUID.randomUUID().toString(),email).andExpect(status().isNotFound());
        verifyNoInteractions(mailer);assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test void smtpFailureRollsBackAccountAndProfileAndAllowsANewAttempt()throws Exception {
        String email=unique+"@student.test",id=admission("CONTACTED",email);
        long profiles=jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Long.class);
        doThrow(new IllegalStateException("SECRET SMTP BODY AND CREDENTIALS")).when(mailer).sendCredentials(anyString(),anyString(),anyString());
        MvcResult failure=create(id,email).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("MAIL_DELIVERY_FAILED")).andReturn();
        assertFalse(failure.getResponse().getContentAsString().contains("SECRET"));assertFalse(users.existsByEmailIgnoreCase(email));
        assertEquals(profiles,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Long.class));assertTrue(row(id).path("canCreate").asBoolean());
        reset(mailer);create(id,email).andExpect(status().isOk());assertTrue(users.existsByEmailIgnoreCase(email));
    }

    @Test void missingMailConfigurationFailsBeforeCreatingAnAccount()throws Exception {
        String email=unique+"@student.test",id=admission("CONTACTED",email);
        doThrow(new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE,"MAIL_NOT_CONFIGURED","Chưa cấu hình email",Map.of())).when(mailer).requireConfigured();
        create(id,email).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
        assertFalse(users.existsByEmailIgnoreCase(email));verify(mailer,never()).sendCredentials(anyString(),anyString(),anyString());
    }

    @Test void concurrentManagersCannotCreateOrEmailTheSameAccountTwice()throws Exception {
        String email=unique+"@student.test",first=admission("CONTACTED",email),second=admission("CONTACTED",email.toUpperCase(Locale.ROOT));
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
