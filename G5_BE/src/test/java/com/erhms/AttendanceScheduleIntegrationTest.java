package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:attendance_schedule;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "logging.level.root=WARN", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceScheduleIntegrationTest {
    private static final String ENDPOINT = "/api/workspace/attendance";
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;

    @Test void weekAndClassFiltersShowOnlyScopedSessionsAndDistinguishBacklog() throws Exception {
        var teacher = actor("LECTURER");
        var manager = actor("MANAGER");
        var anotherTeacher = actor("LECTURER");
        UUID firstClass = classroom("Buổi điện", teacher, manager, "ACTIVE");
        UUID secondClass = classroom("Buổi máy in", teacher, manager, "PLANNED");
        UUID foreignClass = classroom("Lớp khác", anotherTeacher, actor("MANAGER"), "ACTIVE");
        UUID cancelledClass = classroom("Lớp hủy", teacher, manager, "CANCELLED");
        LocalDate currentWeek = LocalDate.now(TIME_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeek = currentWeek.minusWeeks(1);
        LocalDate nextWeek = currentWeek.plusWeeks(1);
        UUID overdue = session(firstClass, previousWeek.atStartOfDay(TIME_ZONE).toOffsetDateTime(), false);
        UUID finalized = session(firstClass, previousWeek.plusDays(2).atStartOfDay(TIME_ZONE).toOffsetDateTime(), true);
        session(secondClass, previousWeek.plusDays(1).atStartOfDay(TIME_ZONE).toOffsetDateTime(), false);
        session(foreignClass, previousWeek.atStartOfDay(TIME_ZONE).toOffsetDateTime(), false);
        session(cancelledClass, previousWeek.atStartOfDay(TIME_ZONE).toOffsetDateTime(), false);
        session(firstClass, nextWeek.atStartOfDay(TIME_ZONE).toOffsetDateTime(), false);

        read(teacher, get(ENDPOINT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.week").value(currentWeek.toString()))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.classes.length()").value(2))
                .andExpect(jsonPath("$.counts.backlog").value(2));
        read(teacher, get(ENDPOINT).param("week", previousWeek.plusDays(3).toString()).param("classId", firstClass.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.week").value(previousWeek.toString()))
                .andExpect(jsonPath("$.counts.total").value(2)).andExpect(jsonPath("$.counts.pending").value(1))
                .andExpect(jsonPath("$.items[0].id").value(overdue.toString())).andExpect(jsonPath("$.items[1].id").value(finalized.toString()));
        read(manager, get(ENDPOINT).param("status", "BACKLOG").param("classId", firstClass.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].attendanceStatus").value("PENDING"));
        read(teacher, get(ENDPOINT).param("status", "UPCOMING").param("week", nextWeek.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].attendanceStatus").value("UPCOMING"));
        read(teacher, get(ENDPOINT).param("classId", foreignClass.toString())).andExpect(status().isNotFound());
        read(teacher, get(ENDPOINT).param("status", "INVALID")).andExpect(status().isBadRequest());
        read(actor("STUDENT"), get(ENDPOINT)).andExpect(status().isForbidden());
    }

    private ResultActions read(User actor, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(user(actor.email).roles(actor.getRoleCode())));
    }

    private User actor(String role) {
        User actor = new User();
        actor.email = UUID.randomUUID() + "@attendance.test";
        actor.fullName = "Kiểm thử " + role;
        actor.passwordHash = "fixture";
        actor.role = roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(actor);
    }

    private UUID classroom(String name, User teacher, User manager, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_classes(id,code,name,lecturer_id,manager_id,status,capacity) VALUES (?,?,?,?,?,?,20)",
                id, id.toString(), name, teacher.id, manager.id, status);
        return id;
    }

    private UUID session(UUID classId, OffsetDateTime starts, boolean finalized) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions(id,class_id,title,starts_at,ends_at,attendance_finalized) VALUES (?,?,?,?,?,?)",
                id, classId, "Buổi học", starts, starts.plusHours(2), finalized);
        return id;
    }
}
