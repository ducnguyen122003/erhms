package com.erhms;

import com.erhms.consultation.repository.ConsultationRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsultationIntegrationTest {

    private static final String CONSULTATIONS = "/api/public/consultations";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ConsultationRegistrationRepository registrations;

    @BeforeEach
    void setup() {
        registrations.deleteAll();
    }

    @Test
    void anonymousRegistrationWithRealCsrfPersistsEveryInputAndReturnsReceipt() throws Exception {
        var data = input();
        data.put("fullName", "  Nguyễn Văn An  ");
        data.put("phone", " 0912.345-678 ");
        data.put("email", "  an@example.com  ");
        data.put("program_name", "  Điện công nghiệp nền tảng  ");
        data.put("description", "  Tôi muốn học điện công nghiệp.  ");
        var result = submit(data).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.created_date").isString())
                .andExpect(jsonPath("$.phone").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath())
                .andReturn();
        var receipt = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(3, receipt.size());
        assertEquals(1, registrations.count());
        var saved = registrations.findById(UUID.fromString(receipt.get("id").asText())).orElseThrow();
        assertEquals("Nguyễn Văn An", saved.fullName);
        assertEquals("0912345678", saved.phone);
        assertEquals("an@example.com", saved.email);
        assertEquals("Điện công nghiệp nền tảng", saved.programName);
        assertEquals("morning", saved.contactTime);
        assertEquals("Tôi muốn học điện công nghiệp.", saved.description);
        assertNotNull(saved.createdDate);
        assertTrue(Duration.between(saved.createdDate, Instant.parse(receipt.get("created_date").asText()))
                .abs().compareTo(Duration.ofSeconds(1)) < 0);
    }

    @Test
    void optionalFieldsCanBeEmpty() throws Exception {
        var data = input();
        data.put("email", "  ");
        data.remove("description");
        data.put("phone", "+84 (912) 345-678");
        submit(data).andExpect(status().isCreated());
        var saved = registrations.findAll().get(0);
        assertNull(saved.email);
        assertNull(saved.description);
        assertEquals("+84912345678", saved.phone);
    }

    @Test
    void csrfRequiredAndMissingTokenDoesNotInsert() throws Exception {
        mvc.perform(post(CONSULTATIONS).contentType("application/json")
                        .content(json.writeValueAsString(input())))
                .andExpect(status().isForbidden());
        assertEquals(0, registrations.count());
    }

    @Test
    void invalidRequiredFieldsAndChoicesNeverInsert() throws Exception {
        for (var invalid : List.of(
                Map.entry("fullName", " "),
                Map.entry("fullName", "A"),
                Map.entry("phone", "09123abc78"),
                Map.entry("phone", "+849123456789"),
                Map.entry("email", "invalid-email"),
                Map.entry("contactTime", "midnight"),
                Map.entry("fullName", "A".repeat(121)),
                Map.entry("email", "a".repeat(180) + "@example.com"),
                Map.entry("description", "A".repeat(2001)))) {
            var data = input();
            data.put(invalid.getKey(), invalid.getValue());
            submit(data).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + invalid.getKey()).exists());
            assertEquals(0, registrations.count(), invalid.getKey());
        }
        for (String field : List.of("fullName", "phone", "contactTime")) {
            var data = input();
            data.remove(field);
            submit(data).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields." + field).exists());
            assertEquals(0, registrations.count(), field);
        }
    }

    @Test
    void programNameRequiresDisplayTextAndAcceptsFullLengthUnicode() throws Exception {
        var data = input();
        data.put("program_name", null);
        submit(data).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.programName").exists());
        data.put("program_name", "  ");
        submit(data).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.programName").exists());
        data.put("program_name", "Đ".repeat(256));
        submit(data).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.programName").exists());
        data.remove("program_name");
        submit(data).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.programName").exists());
        assertEquals(0, registrations.count());

        String programName = "Chương trình mới: " + "Đ".repeat(255 - "Chương trình mới: ".length());
        data.put("program_name", programName);
        submit(data).andExpect(status().isCreated());
        assertEquals(1, registrations.count());
        assertEquals(programName, registrations.findAll().get(0).programName);
    }

    @Test
    void publicSubmitDoesNotExposeSubmissionsOrProtectedFeatures() throws Exception {
        mvc.perform(get(CONSULTATIONS)).andExpect(status().isUnauthorized());
        mvc.perform(get(CONSULTATIONS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recruitment-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private ResultActions submit(Map<String, Object> input) throws Exception {
        var csrfResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session);
        var csrf = json.readTree(csrfResult.getResponse().getContentAsString());
        return mvc.perform(post(CONSULTATIONS).session(session)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType("application/json").content(json.writeValueAsString(input)));
    }

    private Map<String, Object> input() {
        return new HashMap<>(Map.of(
                "fullName", "Nguyễn Văn An", "phone", "0912345678",
                "email", "an@example.com", "program_name", "Điện công nghiệp nền tảng", "contactTime", "morning",
                "description", "Tôi muốn học điện công nghiệp."));
    }
}
