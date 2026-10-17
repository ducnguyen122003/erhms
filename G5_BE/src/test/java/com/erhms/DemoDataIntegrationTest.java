package com.erhms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:erhms_demo_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoDataIntegrationTest {
    private static final String REQUESTS = "/api/recruitment-requests";
    private static final String USER_ONE_REQUEST = "00000000-0000-0000-0000-000000000101";
    private static final String USER_TWO_REQUEST = "00000000-0000-0000-0000-000000000104";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void firstDemoUserLogsInAndSeesOnlyThreeOwnRequests() throws Exception {
        var session = login("user1@erhms.local");
        var account = me(session, "user1@erhms.local", "USER");
        var page = list(session, 3);
        for (JsonNode request : page.get("items")) {
            assertEquals(account.get("id").asLong(), request.get("userId").asLong());
            assertEquals(account.get("fullName").asText(), request.get("userName").asText());
        }
        assertEquals(Set.of("DRAFT", "SUBMITTED", "REVISION_REQUIRED"), statuses(page));
        mvc.perform(get(REQUESTS + "/" + USER_ONE_REQUEST).session(session)).andExpect(status().isOk());
        mvc.perform(get(REQUESTS + "/" + USER_TWO_REQUEST).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(REQUESTS + "/" + USER_TWO_REQUEST + "/history").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void secondDemoUserLogsInAndSeesOnlyTwoOwnRequests() throws Exception {
        var session = login("user2@erhms.local");
        var account = me(session, "user2@erhms.local", "USER");
        var page = list(session, 2);
        for (JsonNode request : page.get("items")) {
            assertEquals(account.get("id").asLong(), request.get("userId").asLong());
        }
        assertEquals(Set.of("APPROVED", "CLOSED"), statuses(page));
        mvc.perform(get(REQUESTS + "/" + USER_TWO_REQUEST).session(session)).andExpect(status().isOk());
        mvc.perform(get(REQUESTS + "/" + USER_ONE_REQUEST).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void demoManagerLogsInAndSeesAllFiveRequestsAndTheirHistory() throws Exception {
        var session = login("manager@erhms.local");
        me(session, "manager@erhms.local", "SERVICE_MANAGER");
        var page = list(session, 5);
        assertEquals(Set.of("DRAFT", "SUBMITTED", "REVISION_REQUIRED", "APPROVED", "CLOSED"), statuses(page));
        for (JsonNode request : page.get("items")) {
            String path = REQUESTS + "/" + request.get("id").asText();
            mvc.perform(get(path).session(session)).andExpect(status().isOk());
            var history = json.readTree(mvc.perform(get(path + "/history").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertFalse(history.isEmpty());
            assertEquals("CREATED", history.get(0).get("action").asText());
        }
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", email).param("password", "Demo@123456"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private JsonNode me(MockHttpSession session, String email, String role) throws Exception {
        return json.readTree(mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roleId").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.employerId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(MockHttpSession session, int expectedTotal) throws Exception {
        return json.readTree(mvc.perform(get(REQUESTS).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(expectedTotal))
                .andExpect(jsonPath("$.items.length()").value(expectedTotal))
                .andReturn().getResponse().getContentAsString());
    }

    private Set<String> statuses(JsonNode page) {
        Set<String> statuses = new HashSet<>();
        for (JsonNode request : page.get("items")) statuses.add(request.get("status").asText());
        return statuses;
    }
}
