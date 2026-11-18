package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:consultation_outcomes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","logging.level.root=WARN","debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdmissionConsultationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ObjectMapper json;
    User hr;
    UUID admission;

    User actor(String role) {
        var user=new User();user.email=UUID.randomUUID()+"@outcomes.test";user.fullName="Kiểm thử tư vấn";user.passwordHash="fixture";user.role=roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        hr=actor("HR");admission=UUID.randomUUID();
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,program_name,contact_time,created_date) VALUES (?,'Học viên thử nghiệm','0912345678',?,'Nghề điện','any',CURRENT_TIMESTAMP)",admission,UUID.randomUUID()+"@gmail.com");
    }
    Map<String,Object> input(String outcome) {
        var input=new LinkedHashMap<String,Object>();input.put("version",0);input.put("channel","PHONE");input.put("status",outcome);input.put("notes","");input.put("appointmentAt","");input.put("documentDueOn","");input.put("discontinuationReason","");return input;
    }
    ResultActions save(User actor,Map<String,Object> input)throws Exception {
        return mvc.perform(post("/api/workspace/screens/AdmissionConsultation/"+admission).with(user(actor.email).roles(actor.getRoleCode())).with(csrf()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    JsonNode detail()throws Exception {
        return json.readTree(mvc.perform(get("/api/workspace/screens/ListAdmissionApplicationDetail/"+admission).with(user(hr.email).roles("HR"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test void furtherCounsellingRequiresContentAndSupportsAnOptionalCallback()throws Exception {
        var input=input("NEEDS_COUNSELING");save(hr,input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.notes").exists());
        input.put("notes","Cần giải đáp thêm về học phí");save(hr,input).andExpect(status().isOk());
        assertEquals("NEEDS_COUNSELING",detail().path("record").path("status").asText());assertTrue(detail().path("record").path("appointmentAt").isNull());
        input.put("appointmentAt","2026-12-15T01:30:00Z");input.put("documentDueOn","2026-12-20");input.put("discontinuationReason","Nội dung cũ");
        save(hr,input).andExpect(status().isOk());var record=detail().path("record");
        assertEquals("2026-12-15T01:30:00Z",record.path("appointmentAt").asText());assertTrue(record.path("documentDueOn").isNull());assertTrue(record.path("discontinuationReason").isNull());
        assertFalse(detail().path("canSubmitProfile").asBoolean());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='NEEDS_COUNSELING'",Integer.class,admission));
    }

    @Test void documentAppointmentRequiresADateAndDoesNotRequireConsultationNotes()throws Exception {
        var input=input("DOCUMENT_APPOINTMENT");input.put("appointmentAt","2026-12-15T01:30:00Z");
        save(hr,input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.documentDueOn").exists());
        input.put("documentDueOn","invalid-date");save(hr,input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.documentDueOn").exists());
        input.put("documentDueOn","2026-12-20");save(hr,input).andExpect(status().isOk());
        var view=detail();assertTrue(view.path("canSubmitProfile").asBoolean());assertEquals("2026-12-20",view.path("record").path("documentDueOn").asText());
        assertTrue(view.path("record").path("appointmentAt").isNull());assertTrue(view.path("record").path("notes").isNull());
        assertEquals("2026-12-20",view.path("related").path("history").get(0).path("documentDueOn").asText());
        assertTrue(view.path("related").path("history").get(0).path("content").asText().contains("20/12/2026"));
    }

    @Test void discontinuationRequiresItsOwnReasonAndPreservesTheEarlierConsultationHistory()throws Exception {
        var first=input("NEEDS_COUNSELING");first.put("notes","Đã tư vấn ngành học");first.put("appointmentAt","2026-12-15T01:30:00Z");save(hr,first).andExpect(status().isOk());
        var stopped=input("NOT_PROCEEDING");stopped.put("notes","Nội dung tư vấn không thay thế lý do");
        save(hr,stopped).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.discontinuationReason").exists());
        stopped.put("discontinuationReason","Đã chọn chương trình học khác");stopped.put("documentDueOn","2026-12-20");stopped.put("appointmentAt","2026-12-15T01:30:00Z");
        save(hr,stopped).andExpect(status().isOk());var view=detail();
        assertEquals("NOT_PROCEEDING",view.path("record").path("status").asText());assertEquals(stopped.get("discontinuationReason"),view.path("record").path("discontinuationReason").asText());
        assertTrue(view.path("record").path("notes").isNull());assertTrue(view.path("record").path("appointmentAt").isNull());assertTrue(view.path("record").path("documentDueOn").isNull());assertFalse(view.path("canSubmitProfile").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=? AND outcome='NEEDS_COUNSELING' AND appointment_at IS NOT NULL",Integer.class,admission));
        assertEquals(stopped.get("discontinuationReason"),jdbc.queryForObject("SELECT content FROM consultation_notes WHERE consultation_id=? AND outcome='NOT_PROCEEDING'",String.class,admission));
    }

    @Test void permissionsVersionsAndAutomaticStagesRemainProtected()throws Exception {
        var input=input("NEEDS_COUNSELING");input.put("notes","Cần tư vấn thêm về lịch học");save(hr,input).andExpect(status().isOk());
        save(actor("HR"),input).andExpect(status().isNotFound());save(actor("MANAGER"),input).andExpect(status().isForbidden());
        input.put("version",1);save(hr,input).andExpect(status().isConflict());input.put("version",0);
        for(String outcome:new String[]{"CONTACTED","APPOINTMENT","CLOSED","PROFILE_PENDING","PROFILE_CREATED","CONTRACTED"}) {
            input.put("status",outcome);save(hr,input).andExpect(status().isBadRequest());
        }
        input.put("status","NEEDS_COUNSELING");
        mvc.perform(post("/api/workspace/screens/AdmissionConsultation/"+admission).with(user(hr.email).roles("HR")).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
    }

    @Test void profilesAlreadySentToTheManagerCannotRecordMoreConsultations()throws Exception {
        var appointment=input("DOCUMENT_APPOINTMENT");appointment.put("documentDueOn","2026-12-20");save(hr,appointment).andExpect(status().isOk());
        jdbc.update("UPDATE consultation_followups SET status='PROFILE_PENDING' WHERE consultation_id=?",admission);
        var notes=input("PROFILE_PENDING");notes.put("notes","Đã nhắc học viên bổ sung giấy tờ");save(hr,notes).andExpect(status().isConflict());
        assertFalse(detail().path("canConsult").asBoolean());assertTrue(detail().path("canEditProfile").asBoolean());
        assertEquals("2026-12-20",detail().path("record").path("documentDueOn").asText());
        notes.put("version",1);notes.put("status","NEEDS_COUNSELING");save(hr,notes).andExpect(status().isConflict());
    }

    @Test void emptyEmailOnANewConsultationPreservesTheProfileEmailAndNewNotesAreSavedSeparately()throws Exception {
        String originalEmail=jdbc.queryForObject("SELECT email FROM consultation_registrations WHERE id=?",String.class,admission);
        var first=input("NEEDS_COUNSELING");first.put("notes","Lượt một: cần hỏi gia đình");first.put("appointmentAt","2026-12-15T01:30:00Z");save(hr,first).andExpect(status().isOk());
        var next=input("DOCUMENT_APPOINTMENT");next.put("email","   ");next.put("documentDueOn","2026-12-20");save(hr,next).andExpect(status().isOk());
        var view=detail();assertEquals(originalEmail,view.path("record").path("email").asText());
        assertTrue(view.path("record").path("notes").isNull());assertTrue(view.path("record").path("appointmentAt").isNull());
        assertEquals("DOCUMENT_APPOINTMENT",view.path("record").path("status").asText());assertTrue(view.path("canConsult").asBoolean());
        assertEquals(2,view.path("related").path("history").size());
        assertEquals("Lượt một: cần hỏi gia đình",jdbc.queryForObject("SELECT content FROM consultation_notes WHERE consultation_id=? AND outcome='NEEDS_COUNSELING'",String.class,admission));
    }

    @Test void discontinuedAdmissionRejectsEveryFurtherConsultationAndTheDirectFormIsReadOnly()throws Exception {
        var first=input("NEEDS_COUNSELING");first.put("notes","Đã tư vấn lịch học");save(hr,first).andExpect(status().isOk());
        var stopped=input("NOT_PROCEEDING");stopped.put("discontinuationReason","Chưa có nhu cầu học");save(hr,stopped).andExpect(status().isOk());
        var before=detail();assertFalse(before.path("canConsult").asBoolean());assertFalse(before.path("canSubmitProfile").asBoolean());
        mvc.perform(get("/api/workspace/screens/AdmissionConsultation/"+admission).with(user(hr.email).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canWrite").value(false)).andExpect(jsonPath("$.canConsult").value(false));
        for(String outcome:new String[]{"NEEDS_COUNSELING","DOCUMENT_APPOINTMENT","NOT_PROCEEDING"}) {
            var next=input(outcome);next.put("version",before.path("record").path("version").asLong());
            next.put("notes","Không được ghi thêm");next.put("documentDueOn","2026-12-20");next.put("discontinuationReason","Không được sửa lý do");next.put("email","changed@gmail.com");
            save(hr,next).andExpect(status().isConflict());
        }
        assertEquals(before,detail());
        save(hr,first).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM consultation_notes WHERE consultation_id=?",Integer.class,admission));
    }
}
