package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.StudentCode;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.workspace.FixedCourseCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:initial_equipment_requests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "logging.level.root=WARN", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EquipmentInitialRequestsIntegrationTest {
    private static final String API = "/api/workspace/equipment-initial-requests";
    private static final String SCREENS = "/api/workspace/screens/";
    private static final String TITLE = "Bộ thực hành cho khóa mới";
    private static final String DESCRIPTION = "Dùng trong các buổi thực hành đầu khóa";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;

    private User lecturer;
    private User otherLecturer;
    private User it;
    private UUID classId;
    private UUID otherClassId;
    private LocalDate neededOn;

    @BeforeEach
    void setup() {
        lecturer = actor("LECTURER", "Giảng viên lớp mới");
        otherLecturer = actor("LECTURER", "Giảng viên lớp khác");
        it = actor("IT_SUPPORT", "Bộ phận thiết bị");
        classId = classroom(lecturer, "PLANNED");
        otherClassId = classroom(otherLecturer, "ACTIVE");
        neededOn = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3);
    }

    @Test
    void rosterListsOnlyEligibleStudentsInTheChosenClassAndSupportsSearch() throws Exception {
        User eligible = student("An", classId, "ACTIVE");
        User retry = student("Bình", classId, "ACTIVE");
        User alreadyRequested = student("Chi", classId, "ACTIVE");
        User alreadyReceived = student("Duy", classId, "ACTIVE");
        User disabled = student("Giang", classId, "ACTIVE");
        User locked = student("Hà", classId, "ACTIVE");
        User closedEnrollment = student("Hùng", classId, "ACTIVE");
        User pendingEnrollment = student("Khánh", classId, "PENDING");
        student("Lan", otherClassId, "ACTIVE");
        classroom(lecturer, "COMPLETED");

        existingRequest(classId, retry, "INITIAL", "REJECTED");
        existingRequest(classId, alreadyRequested, "INITIAL", "PREPARING");
        existingRequest(classId, alreadyReceived, "INITIAL", "REJECTED");
        returnedEquipment(alreadyReceived, classId);
        returnedEquipment(eligible, otherClassId);
        jdbc.update("UPDATE m_user SET account_enabled=FALSE WHERE user_id=?", disabled.id);
        jdbc.update("UPDATE m_user SET account_locked=TRUE WHERE user_id=?", locked.id);
        jdbc.update("UPDATE training_enrollments SET closed_at=CURRENT_TIMESTAMP WHERE class_id=? AND student_id=?", classId, closedEnrollment.id);
        existingRequest(classId, pendingEnrollment, "SUPPLEMENT", "REJECTED");

        read(lecturer, null).andExpect(jsonPath("$.classes.length()").value(1))
                .andExpect(jsonPath("$.classes[0].id").value(classId.toString()))
                .andExpect(jsonPath("$.items.length()").value(0));

        JsonNode data = body(read(lecturer, classId));
        assertEquals(Set.of(eligible.id, retry.id), studentIds(data));
        assertEquals(2, data.path("total").asInt());
        assertEquals(25, data.path("size").asInt());
        assertEquals(1, data.path("totalPages").asInt());

        mvc.perform(asActor(get(API).param("classId", classId.toString()).param("q", StudentCode.fromId(retry.id)), lecturer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].studentCode").value(StudentCode.fromId(retry.id)))
                .andExpect(jsonPath("$.items[0].name").value(retry.fullName));
    }

    @Test
    void batchCreatesAnInitialRequestPerStudentAndHidesItFromTheRoster() throws Exception {
        User first = student("An", classId, "ACTIVE");
        User retry = student("Bình", classId, "ACTIVE");
        User single = student("Chi", classId, "ACTIVE");
        existingRequest(classId, retry, "INITIAL", "REJECTED");

        submit(lecturer, input(classId, List.of(first.id, retry.id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(2));
        assertEquals(Set.of(single.id), studentIds(body(read(lecturer, classId))));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM equipment_requests WHERE class_id=? AND requested_by=?
                AND status='NEW' AND request_type='INITIAL' AND quantity=1 AND title=? AND needed_on=? AND description=?
                """, Integer.class, classId, lecturer.id, TITLE, neededOn, DESCRIPTION));
        assertEquals(Set.of(first.id, retry.id), new HashSet<>(jdbc.queryForList("""
                SELECT student_id FROM equipment_requests WHERE class_id=? AND status='NEW'
                """, Long.class, classId)));
        for (UUID requestId : jdbc.queryForList("""
                SELECT id FROM equipment_requests WHERE class_id=? AND status='NEW'
                """, UUID.class, classId)) {
            mvc.perform(asActor(get(SCREENS + "EquipmentRequestDetail_IT/" + requestId), it))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.record.requestType").value("INITIAL"))
                    .andExpect(jsonPath("$.record.quantity").value(1));
        }

        submit(lecturer, input(classId, List.of(first.id, retry.id))).andExpect(status().isConflict());
        submitSingle(first, "INITIAL").andExpect(status().isConflict());
        submitSingle(single, "INITIAL").andExpect(status().isOk());
        submit(lecturer, input(classId, List.of(single.id))).andExpect(status().isConflict());
        submitSingle(first, "REPLACEMENT").andExpect(status().isOk());
        assertTrue(studentIds(body(read(lecturer, classId))).isEmpty());
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM equipment_requests WHERE class_id=? AND status='NEW' AND request_type='INITIAL' AND quantity=1
                """, Integer.class, classId));
        assertEquals(2, jdbc.queryForObject("SELECT quantity FROM equipment_requests WHERE class_id=? AND student_id=? AND request_type='REPLACEMENT'",
                Integer.class, classId, first.id));
    }

    @Test
    void initialRequestsDoNotRequireAQuantityInThePayload() throws Exception {
        User batchStudent = student("An", classId, "ACTIVE");
        User singleStudent = student("Bình", classId, "ACTIVE");

        var batchInput = new LinkedHashMap<>(input(classId, List.of(batchStudent.id)));
        batchInput.remove("quantity");
        submit(lecturer, batchInput).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(1));

        var singleInput = new LinkedHashMap<>(batchInput);
        singleInput.remove("studentIds");
        singleInput.put("studentId", singleStudent.id);
        singleInput.put("requestType", "INITIAL");
        mvc.perform(asActor(post(SCREENS + "AddEquipmentRequest"), lecturer).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(singleInput))).andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM equipment_requests WHERE class_id=? AND quantity=1 AND request_type='INITIAL'",
                Integer.class, classId));
    }

    @Test
    void initialRequestsRequireTomorrowOrLaterInTheTrainingCentersTimeZone() throws Exception {
        User batchStudent = student("An", classId, "ACTIVE");
        User singleStudent = student("Bình", classId, "ACTIVE");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var batchInput = new LinkedHashMap<>(input(classId, List.of(batchStudent.id)));

        for (LocalDate invalidDate : List.of(today.minusDays(1), today)) {
            batchInput.put("neededOn", invalidDate.toString());
            submit(lecturer, batchInput).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.neededOn").exists());
            submitSingle(singleStudent, "INITIAL", invalidDate).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.neededOn").exists());
        }
        assertEquals(0, requestCount());

        LocalDate tomorrow = today.plusDays(1);
        batchInput.put("neededOn", tomorrow.toString());
        submit(lecturer, batchInput).andExpect(status().isOk());
        submitSingle(singleStudent, "INITIAL", tomorrow).andExpect(status().isOk());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM equipment_requests WHERE class_id=? AND needed_on=?",
                Integer.class, classId, tomorrow));
        submitSingle(singleStudent, "REPLACEMENT", today).andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidOrOutOfScopeBatchesWithoutCreatingAnyRequests() throws Exception {
        User eligible = student("An", classId, "ACTIVE");
        User taken = student("Bình", classId, "ACTIVE");
        User otherStudent = student("Chi", otherClassId, "ACTIVE");
        existingRequest(classId, taken, "INITIAL", "NEW");
        assertEquals(1, requestCount());

        submit(lecturer, input(classId, List.of(eligible.id, taken.id))).andExpect(status().isConflict());
        submit(lecturer, input(classId, List.of(eligible.id, otherStudent.id))).andExpect(status().isConflict());
        submit(lecturer, input(classId, List.of(eligible.id, eligible.id))).andExpect(status().isBadRequest());
        submit(lecturer, input(classId, List.of())).andExpect(status().isBadRequest());
        submit(lecturer, input(classId, LongStream.rangeClosed(1, 51).boxed().toList())).andExpect(status().isBadRequest());

        mvc.perform(asActor(get(API).param("classId", otherClassId.toString()), lecturer)).andExpect(status().isNotFound());
        submit(lecturer, input(otherClassId, List.of(otherStudent.id))).andExpect(status().isNotFound());
        mvc.perform(asActor(get(API), it)).andExpect(status().isForbidden());
        submit(it, input(classId, List.of(eligible.id))).andExpect(status().isForbidden());

        jdbc.update("DELETE FROM role_function WHERE role_id=? AND function_code='AddEquipmentRequest'", lecturer.getRoleId());
        mvc.perform(asActor(get(API), lecturer)).andExpect(status().isForbidden());
        submit(lecturer, input(classId, List.of(eligible.id))).andExpect(status().isForbidden());
        assertEquals(1, requestCount());
    }

    private User actor(String role, String name) {
        var account = new User();
        account.email = UUID.randomUUID() + "@equipment-initial.test";
        account.fullName = name;
        account.passwordHash = "test-only";
        account.role = roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(account);
    }

    private UUID classroom(User teacher, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_classes(id,code,name,lecturer_id,status,course_id) VALUES (?,?,?,?,?,?)",
                id, id.toString(), "Lớp thực hành", teacher.id, status, FixedCourseCatalog.IDS.get(0));
        return id;
    }

    private User student(String name, UUID classroom, String status) {
        User student = actor("STUDENT", name);
        jdbc.update("INSERT INTO training_enrollments(id,class_id,student_id,status) VALUES (?,?,?,?)",
                UUID.randomUUID(), classroom, student.id, status);
        return student;
    }

    private void existingRequest(UUID classroom, User student, String type, String status) {
        jdbc.update("""
                INSERT INTO equipment_requests(id,requested_by,class_id,title,quantity,status,student_id,request_type)
                VALUES (?,?,?,'Yêu cầu cũ',1,?,?,?)
                """, UUID.randomUUID(), lecturer.id, classroom, status, student.id, type);
    }

    private void returnedEquipment(User student, UUID classroom) {
        UUID asset = UUID.randomUUID();
        jdbc.update("INSERT INTO equipment_assets(id,code,name,status) VALUES (?,?,'Thiết bị đã trả','AVAILABLE')",
                asset, asset.toString());
        jdbc.update("""
                INSERT INTO equipment_loans(id,asset_id,student_id,class_id,received_at,returned_at,return_status)
                VALUES (?,?,?,?,?,?,'RETURNED')
                """, UUID.randomUUID(), asset, student.id, classroom, OffsetDateTime.now().minusDays(2), OffsetDateTime.now().minusDays(1));
    }

    private ResultActions read(User actor, UUID classroom) throws Exception {
        var request = get(API);
        if (classroom != null) request.param("classId", classroom.toString());
        return mvc.perform(asActor(request, actor)).andExpect(status().isOk());
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private Set<Long> studentIds(JsonNode data) {
        Set<Long> ids = new HashSet<>();
        data.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private Map<String, Object> input(UUID classroom, List<Long> students) {
        return Map.of("classId", classroom.toString(), "studentIds", students, "title", TITLE,
                "quantity", 2, "neededOn", neededOn.toString(), "description", DESCRIPTION);
    }

    private ResultActions submit(User actor, Map<String, Object> input) throws Exception {
        return mvc.perform(asActor(post(API), actor).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(input)));
    }

    private ResultActions submitSingle(User student, String type) throws Exception {
        return submitSingle(student, type, neededOn);
    }

    private ResultActions submitSingle(User student, String type, LocalDate requestedDate) throws Exception {
        var data = new LinkedHashMap<>(input(classId, List.of(student.id)));
        data.remove("studentIds");
        data.put("studentId", student.id);
        data.put("requestType", type);
        data.put("neededOn", requestedDate.toString());
        return mvc.perform(asActor(post(SCREENS + "AddEquipmentRequest"), lecturer).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(data)));
    }

    private MockHttpServletRequestBuilder asActor(MockHttpServletRequestBuilder request, User actor) {
        return request.with(user(actor.email).roles(actor.getRoleCode()));
    }

    private int requestCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM equipment_requests WHERE class_id=?", Integer.class, classId);
    }
}
