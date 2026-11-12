package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admin_workspace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.mail.username=","spring.mail.password="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminWorkspaceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    User admin,student;
    final String password="WorkspacePassword@2026";

    @BeforeEach void setup() { admin=createUser("ADMIN");student=createUser("STUDENT"); }
    User createUser(String role) {
        User u=new User();u.email=UUID.randomUUID()+"@workspace.test";u.fullName="Kiểm thử "+UUID.randomUUID();u.passwordHash=encoder.encode(password);u.role=roles.findByCode(role).orElseThrow();return users.saveAndFlush(u);
    }
    JsonNode body(MvcResult response) throws Exception { return json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    JsonNode matrix() throws Exception { return body(mvc.perform(get("/api/admin/permissions").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn()); }
    ResultActions saveMatrix(JsonNode matrix) throws Exception { return mvc.perform(put("/api/admin/permissions").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(matrix.toString())); }
    ObjectNode matrixInput() throws Exception { JsonNode source=matrix();return json.createObjectNode().put("version",source.path("version").asLong()).set("grants",source.path("grants").deepCopy()); }
    Map<String,Object> info(User target) { return new LinkedHashMap<>(Map.of("fullName",target.fullName,"email",target.email,"phone","0912345678","roleId",target.getRoleId())); }
    Map<String,Object> editInput(User target,String state,String name) {
        var account=info(target);account.put("fullName",name);
        return new LinkedHashMap<>(Map.of("account",account,"status",state,"changeNote","Cập nhật theo phân công","version",target.adminVersion));
    }
    ResultActions editAccount(Long id,Object input) throws Exception { return mvc.perform(put("/api/admin/users/"+id+"/details").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input))); }

    @Test void userProfileIsReadOnlyAndDoesNotExposeCredentialsOrInventDates() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log",Long.class);
        var result=mvc.perform(get("/api/admin/users/"+student.id+"/profile").with(user(admin.email).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.id").value(student.id))
                .andExpect(jsonPath("$.createdAt").isEmpty()).andExpect(jsonPath("$.lastLoginAt").isEmpty()).andReturn();
        String raw=result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for(String secret:List.of(password,student.passwordHash,"passwordHash","credentialsVersion","resetToken","codeHash"))assertFalse(raw.contains(secret));
        assertEquals(12,body(result).path("functions").size());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log",Long.class));
        User unchanged=users.findById(student.id).orElseThrow();assertEquals(student.fullName,unchanged.fullName);assertEquals(0,unchanged.adminVersion);assertEquals(0,unchanged.credentialsVersion);assertEquals(student.passwordHash,unchanged.passwordHash);
    }

    private void profileEvent(User actor,String target,String action,String note,Instant when) {
        jdbc.update("""
                INSERT INTO system_audit_log(id,actor_id,actor_name,actor_role,action_code,action_label,target_id,request_method,request_path,result,status_code,change_note,occurred_at)
                VALUES (?,?,?,?,?,?,?,'PUT','/api/admin/users','SUCCESS',200,?,?)
                """,UUID.randomUUID(),actor.id,actor.fullName,actor.getRoleCode(),action,action,target,note,Timestamp.from(when));
    }

    @Test void profileHistoryStaysScopedToTheSelectedAccountAndUsesRecordedTimestamps() throws Exception {
        Instant created=Instant.parse("2026-11-01T08:00:00Z"),updated=created.plusSeconds(60),login=created.plusSeconds(120);
        profileEvent(admin,student.id.toString(),"ACCOUNT_CREATE","Tạo tài khoản",created);
        profileEvent(admin,student.id.toString(),"ACCOUNT_UPDATE","Cập nhật hồ sơ đúng đối tượng",updated);
        profileEvent(student,null,"LOGIN",null,login);
        profileEvent(student,admin.id.toString(),"ACCOUNT_UPDATE","UNRELATED_ACCOUNT_NOTE",login.plusSeconds(1));
        profileEvent(student,null,"SETTINGS_UPDATE","UNRELATED_SYSTEM_CONFIG",login.plusSeconds(2));
        jdbc.update("INSERT INTO user_login_events(id,user_id,occurred_at) VALUES (?,?,?)",UUID.randomUUID(),student.id,Timestamp.from(login));
        var result=mvc.perform(get("/api/admin/users/"+student.id+"/profile").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        JsonNode profile=body(result);assertEquals(3,profile.path("history").size());assertEquals(created.toString(),profile.path("createdAt").asText());assertEquals(updated.toString(),profile.path("updatedAt").asText());assertEquals(login.toString(),profile.path("lastLoginAt").asText());
        assertEquals("LOGIN",profile.path("history").get(0).path("actionCode").asText());
        assertFalse(result.getResponse().getContentAsString().contains("UNRELATED_"));
    }

    @Test void userProfileUsesAccountScreenPermissionWithoutExposingWholeSystemAudit() throws Exception {
        String path="/api/admin/users/"+admin.id+"/profile";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user(student.email).roles("ADMIN"))).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (3,'USER_ACCOUNTS')");
        mvc.perform(get(path).with(user(student.email).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit-logs").with(user(student.email).roles("STUDENT"))).andExpect(status().isForbidden());
    }

    @Test void profileOfNewAccountHasCreationEventBoundToItsId() throws Exception {
        var input=info(student);input.put("email",UUID.randomUUID()+"@profile.test");input.put("password",password);input.put("confirmPassword",password);
        JsonNode created=body(mvc.perform(post("/api/admin/users").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isCreated()).andReturn());
        long id=created.path("id").asLong();
        JsonNode profile=body(mvc.perform(get("/api/admin/users/"+id+"/profile").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());
        assertFalse(profile.path("createdAt").isNull());assertEquals("ACCOUNT_CREATE",profile.path("history").get(0).path("actionCode").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log WHERE target_id=? AND action_code='ACCOUNT_CREATE'",Integer.class,Long.toString(id)));
    }

    @Test void profileReflectsCurrentRoleGrantsAndMissingUsersReturn404() throws Exception {
        jdbc.update("DELETE FROM role_function WHERE role_id=3 AND function_code='PROFILE'");
        JsonNode result=body(mvc.perform(get("/api/admin/users/"+student.id+"/profile").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());
        assertEquals(11,result.path("functions").size());assertEquals("DASHBOARD",result.path("functions").get(0).path("code").asText());
        mvc.perform(get("/api/admin/users/"+Long.MAX_VALUE+"/profile").with(user(admin.email).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void migratedFunctionsAndDefaultGrantsMatchSevenRoles() throws Exception {
        JsonNode source=matrix();assertEquals(7,source.path("roles").size());assertEquals(100,source.path("functions").size());
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM role_function WHERE function_code='PROFILE'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM role_function WHERE function_code='USER_ACCOUNTS'",Integer.class));
        mvc.perform(get("/api/admin/settings").with(user(student.email).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs").with(user(student.email).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void trainingCataloguePermissionsAreExclusiveToAdminAndCannotBeGrantedBack() throws Exception {
        var codes=Set.of("ListProfession","AddProfession","EditProfession","ListCourse","AddCourse","EditCourse","CourseDetail");
        JsonNode source=matrix();
        for(JsonNode function:source.path("functions"))if(codes.contains(function.path("code").asText()))assertEquals(json.valueToTree(List.of(1)),function.path("allowedRoles"));
        for(String code:codes){
            assertEquals(List.of(1L),jdbc.queryForList("SELECT role_id FROM role_function WHERE function_code=?",Long.class,code));
            for(long roleId:List.of(2L,3L,4L,5L,6L,7L)){
                var input=matrixInput();((com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants")).addObject().put("roleId",roleId).put("functionCode",code);
                saveMatrix(input).andExpect(status().isBadRequest());
            }
        }
    }
    @Test void revokingAndRestoringDashboardAppliesToExistingSessionImmediately() throws Exception {
        var login=mvc.perform(post("/api/auth/login").with(csrf()).param("username",student.email).param("password",password)).andExpect(status().isNoContent()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        var input=matrixInput();var grants=(com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants");
        for(int i=grants.size()-1;i>=0;i--)if(grants.get(i).path("roleId").asLong()==3&&grants.get(i).path("functionCode").asText().equals("DASHBOARD"))grants.remove(i);
        saveMatrix(input).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/student").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        input=matrixInput();((com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants")).addObject().put("roleId",3).put("functionCode","DASHBOARD");
        saveMatrix(input).andExpect(status().isOk());mvc.perform(get("/api/dashboard/student").session(session)).andExpect(status().isOk());
    }
    @Test void grantingAccountScreenActuallyGrantsItsApiWithoutRelyingOnClaimedAuthorities() throws Exception {
        var input=matrixInput();((com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants")).addObject().put("roleId",3).put("functionCode","USER_ACCOUNTS");saveMatrix(input).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(user(student.email).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/permissions").with(user(student.email).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void permissionsRejectLockoutDuplicateInvalidGrantsAndStaleVersion() throws Exception {
        var input=matrixInput();var grants=(com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants");
        for(int i=grants.size()-1;i>=0;i--)if(grants.get(i).path("roleId").asLong()==1&&grants.get(i).path("functionCode").asText().equals("ROLE_PERMISSIONS"))grants.remove(i);
        saveMatrix(input).andExpect(status().isConflict());
        input=matrixInput();grants=(com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants");grants.add(grants.get(0).deepCopy());saveMatrix(input).andExpect(status().isBadRequest());
        input=matrixInput();((com.fasterxml.jackson.databind.node.ArrayNode)input.get("grants")).addObject().put("roleId",3).put("functionCode","RECRUITMENT");saveMatrix(input).andExpect(status().isBadRequest());
        input=matrixInput();saveMatrix(input).andExpect(status().isOk());saveMatrix(input).andExpect(status().isConflict());
    }
    @Test void permissionsSaveRequiresCsrfAndDoesNotChangeOnInvalidBody() throws Exception {
        var input=matrixInput();mvc.perform(put("/api/admin/permissions").with(user(admin.email).roles("ADMIN")).contentType("application/json").content(input.toString())).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/permissions").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        assertEquals(input.path("version").asLong(),matrix().path("version").asLong());
    }
    @Test void editSavesFieldsStatusNoteAndVersionAtomically() throws Exception {
        JsonNode result=body(editAccount(student.id,editInput(student,"LOCKED","Tên đã cập nhật")).andExpect(status().isOk()).andExpect(jsonPath("$.accountLocked").value(true)).andReturn());
        assertEquals(1,result.path("version").asLong());
        var saved=users.findWithRoleById(student.id).orElseThrow();assertTrue(saved.accountLocked);assertEquals("Tên đã cập nhật",saved.fullName);assertEquals(1,saved.credentialsVersion);
        assertEquals("Cập nhật theo phân công",jdbc.queryForObject("SELECT change_note FROM system_audit_log WHERE action_code='ACCOUNT_UPDATE' AND target_id=? ORDER BY occurred_at DESC LIMIT 1",String.class,student.id.toString()));
        editAccount(student.id,editInput(student,"ACTIVE","Ghi đè cũ")).andExpect(status().isConflict());
        assertEquals("Tên đã cập nhật",users.findById(student.id).orElseThrow().fullName);
    }
    @Test void invalidAndSelfRestrictingEditsNeverPartiallyUpdateData() throws Exception {
        editAccount(admin.id,editInput(admin,"DISABLED","Không được lưu")).andExpect(status().isConflict());
        assertEquals(admin.fullName,users.findById(admin.id).orElseThrow().fullName);
        var invalid=editInput(student,"LOCKED","x");editAccount(student.id,invalid).andExpect(status().isBadRequest());
        assertFalse(users.findById(student.id).orElseThrow().accountLocked);
    }
    @Test void listFiltersStatusAndAccountCodeAndReturnsDatabaseCounters() throws Exception {
        mvc.perform(patch("/api/admin/users/"+student.id+"/status").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"accountEnabled\":false}")).andExpect(status().isOk());
        var result=body(mvc.perform(get("/api/admin/users").param("q","TK-"+String.format("%04d",student.id)).param("status","DISABLED").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());
        assertEquals(1,result.path("total").asLong());assertEquals(student.id.longValue(),result.path("items").get(0).path("id").asLong());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM m_user WHERE account_enabled=FALSE",Long.class).longValue(),result.path("stats").path("disabled").asLong());
        mvc.perform(get("/api/admin/users").param("status","FAKE").with(user(admin.email).roles("ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void auditCapturesSuccessAndFailureMetadataWithoutSecretsAndSupportsFilters() throws Exception {
        var payload=info(student);payload.put("email",UUID.randomUUID()+"@created.test");payload.put("password","NeverLogThisSecret@2026");payload.put("confirmPassword","NeverLogThisSecret@2026");
        mvc.perform(post("/api/admin/users").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isCreated());
        mvc.perform(patch("/api/admin/users/"+admin.id+"/lock").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"accountLocked\":true}")).andExpect(status().isConflict());
        var result=mvc.perform(get("/api/admin/audit-logs").param("q",admin.fullName).with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        String raw=result.getResponse().getContentAsString(StandardCharsets.UTF_8);assertFalse(raw.contains("NeverLogThisSecret"));assertFalse(raw.contains("passwordHash"));
        JsonNode logs=body(result);assertTrue(logs.path("items").size()>=2);assertTrue(logs.path("stats").has("total"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log WHERE actor_id=? AND result='FAILURE'",Long.class,admin.id)>0);
        mvc.perform(get("/api/admin/audit-logs").param("date","not-a-date").with(user(admin.email).roles("ADMIN"))).andExpect(status().isBadRequest());
        String id=logs.path("items").get(0).path("id").asText();mvc.perform(get("/api/admin/audit-logs/"+id).with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk());
    }
    @Test void auditHistoryRemainsAfterItsActorIsDeleted() throws Exception {
        mvc.perform(put("/api/auth/me").with(user(student.email).roles("STUDENT")).with(csrf()).contentType("application/json").content("{\"fullName\":\"Hồ sơ cập nhật\",\"phone\":\"0912345678\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/"+student.id).with(user(admin.email).roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_log WHERE action_code='PROFILE_UPDATE' AND actor_id IS NULL AND actor_name='Hồ sơ cập nhật'",Long.class)>0);
    }
    @Test void settingsPersistBrandingRejectStaleEditsAndNeverExposeSmtpSecrets() throws Exception {
        var first=body(mvc.perform(get("/api/admin/settings").with(user(admin.email).roles("ADMIN"))).andExpect(status().isOk()).andReturn());
        var input=Map.of("centerName","Trung tâm kiểm thử","notificationEmail","support@example.test","defaultLanguage","vi","version",first.path("version").asLong());
        String content=json.writeValueAsString(input);
        mvc.perform(put("/api/admin/settings").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(content)).andExpect(status().isOk()).andExpect(jsonPath("$.centerName").value("Trung tâm kiểm thử"));
        mvc.perform(put("/api/admin/settings").with(user(admin.email).roles("ADMIN")).with(csrf()).contentType("application/json").content(content)).andExpect(status().isConflict());
        var branding=mvc.perform(get("/api/system/branding")).andExpect(status().isOk()).andExpect(jsonPath("$.notificationEmail").value("support@example.test")).andReturn();
        assertFalse(branding.getResponse().getContentAsString().contains("password"));
        mvc.perform(post("/api/admin/settings/test-email").with(user(admin.email).roles("ADMIN")).with(csrf())).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/admin/settings").with(user(student.email).roles("STUDENT"))).andExpect(status().isForbidden());
    }
}
