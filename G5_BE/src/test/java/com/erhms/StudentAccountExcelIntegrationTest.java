package com.erhms;

import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.onboarding.StudentAccountMailer;
import com.erhms.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:student_account_excel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
        "logging.level.root=WARN", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentAccountExcelIntegrationTest {
    private static final String API = "/api/workspace/student-accounts";
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"Họ và tên (*)", "Số điện thoại (*)", "Email (*)",
            "Khóa quan tâm (*)", "Thời gian liên hệ", "Nội dung tư vấn"};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @MockitoBean StudentAccountMailer mailer;
    private User manager;
    private String unique;

    @BeforeEach void setup() {
        reset(mailer);
        unique = "excel-" + UUID.randomUUID();
        manager = actor("MANAGER");
    }

    @Test void templateIsBlankAndUsesRequiredEmailAndTextPhoneWithInstructions() throws Exception {
        var response = mvc.perform(get(API + "/import-template").with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().contentType(MIME))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-nhap-hoc-vien.xlsx")))
                .andReturn().getResponse();
        Files.write(Path.of("target", "mau-nhap-hoc-vien.xlsx"), response.getContentAsByteArray());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Hoc vien", sheet.getSheetName());
            assertNotNull(workbook.getSheet("Huong dan"));
            for (int column = 0; column < HEADERS.length; column++) {
                assertEquals(HEADERS[column], sheet.getRow(0).getCell(column).getStringCellValue());
            }
            assertEquals("@", sheet.getColumnStyle(1).getDataFormatString());
            assertFalse(sheet.getDataValidations().isEmpty());
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                for (var cell : row) assertTrue(cell.getCellType() == CellType.BLANK
                        || cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank(),
                        "Example learners must stay in the instruction sheet, outside imported data");
            }
        }
    }

    @Test void completedDownloadedTemplateAppearsImmediatelyInAccountQueueWithoutSendingEmail() throws Exception {
        byte[] template = mvc.perform(get(API + "/import-template").with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            put(workbook.getSheetAt(0), 1, "  " + unique + " Nguyễn An  ", " 0912.345-678 ",
                    "  " + email(1).toUpperCase(Locale.ROOT) + "  ", " Điện công nghiệp ", "Buổi sáng", " Cần học buổi sáng. ");
            put(workbook.getSheetAt(0), 3, unique + " Trần Bình", "+84987654321", email(2), "May công nghiệp", "", "");
            upload(workbook).andExpect(status().isOk()).andExpect(jsonPath("$.importedCount").value(2))
                    .andExpect(jsonPath("$.notice").isString());
        }
        JsonNode listed = list();
        assertEquals(2, listed.path("total").asInt());
        JsonNode first = row(email(1));
        assertEquals(unique + " Nguyễn An", first.path("fullName").asText());
        assertEquals("0912345678", first.path("phone").asText());
        assertEquals("Điện công nghiệp", first.path("programName").asText());
        assertEquals("morning", first.path("contactTime").asText());
        assertEquals("EXCEL", first.path("source").asText());
        assertEquals("NONE", first.path("accountStatus").asText());
        assertTrue(first.path("canCreate").asBoolean());
        assertEquals("any", row(email(2)).path("contactTime").asText());
        assertFalse(users.existsByEmailIgnoreCase(email(1)));
        assertFalse(users.existsByEmailIgnoreCase(email(2)));
        verifyNoInteractions(mailer);

        User hr = actor("HR");
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q", unique)
                        .with(user(hr.email).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void importedLearnerCanReceiveAnAccountAndContinueToTrainingContract() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            upload(workbook).andExpect(status().isOk());
        }
        verifyNoInteractions(mailer);
        JsonNode imported = row(email(1));
        String id = imported.path("id").asText();
        JsonNode account = body(mvc.perform(post(API + "/" + id).with(user(manager.email).roles("MANAGER"))
                        .with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("expectedEmail", email(1), "version", imported.path("version").asLong()))))
                .andExpect(status().isOk()).andReturn());
        long accountId = account.path("accountId").asLong();
        assertEquals("STUDENT", users.findWithRoleById(accountId).orElseThrow().getRoleCode());
        assertEquals(manager.id, jdbc.queryForObject("SELECT manager_id FROM student_profiles WHERE user_id=?", Long.class, accountId));
        verify(mailer).requireConfigured();
        verify(mailer).sendCredentials(eq(email(1)), eq(unique + " Học viên 1"), anyString());
        assertEquals("STUDENT", row(email(1)).path("accountStatus").asText());

        UUID course = UUID.randomUUID();
        jdbc.update("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity) VALUES (?,?,?,?,3,20,1000,'Training','ACTIVE',?,20)",
                course, course.toString(), unique + " Course", "Nghề", manager.id);
        var contract = Map.of("consultationId", id, "courseId", course.toString(), "studentId", accountId,
                "startsOn", "2026-12-15", "paymentDueOn", "2026-12-14", "tuitionDue", 1000, "depositDue", 0, "terms", "Điều khoản đào tạo");
        mvc.perform(post("/api/workspace/screens/AddTrainingContract").with(user(manager.email).roles("MANAGER"))
                        .with(csrf()).contentType("application/json").content(json.writeValueAsString(contract)))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND student_id=?", Integer.class, UUID.fromString(id), accountId));
    }

    @Test void ordinaryPublicRegistrationStillBelongsToRecruitmentAfterSourceMigration() throws Exception {
        var input = Map.of("fullName", unique + " Đăng ký tư vấn", "phone", "0912345678", "email", email(1),
                "program_name", "Điện công nghiệp", "contactTime", "any");
        JsonNode receipt = body(mvc.perform(post("/api/public/consultations").with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn());
        UUID id = UUID.fromString(receipt.path("id").asText());
        assertEquals("ADMISSION", jdbc.queryForObject("SELECT intake_source FROM consultation_registrations WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT imported_by FROM consultation_registrations WHERE id=?", Long.class, id));
        User hr = actor("HR");
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q", unique)
                        .with(user(hr.email).roles("HR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()));
        assertEquals(0, list().path("total").asInt());
        verifyNoInteractions(mailer);
    }

    @Test void missingEmailInLaterRowRejectsWholeFileWithActualExcelRowNumber() throws Exception {
        long before = registrationsCount();
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            valid(workbook.getSheetAt(0), 4, 2);
            workbook.getSheetAt(0).getRow(4).getCell(2).setCellValue("  ");
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EXCEL_VALIDATION"))
                    .andExpect(jsonPath("$.fields['row.5.email']").isString());
        }
        assertEquals(before, registrationsCount());
        assertEquals(0, list().path("total").asInt());
        verifyNoInteractions(mailer);
    }

    @Test void invalidProfileValuesAndUnsupportedCellTypesNeverSavePartialInformation() throws Exception {
        long before = registrationsCount();
        for (var invalid : List.of(Map.entry(0, "A"), Map.entry(0, "A".repeat(121)), Map.entry(1, "invalid-phone"),
                Map.entry(2, "invalid-email"), Map.entry(3, ""), Map.entry(3, "A".repeat(256)),
                Map.entry(4, "midnight"), Map.entry(5, "A".repeat(2001)))) {
            try (var workbook = workbook()) {
                valid(workbook.getSheetAt(0), 1, 1);
                workbook.getSheetAt(0).getRow(1).getCell(invalid.getKey()).setCellValue(invalid.getValue());
                upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXCEL_VALIDATION"));
            }
        }
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            workbook.getSheetAt(0).getRow(1).getCell(0).setCellFormula("\"Nguyễn Văn An\"");
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.2.fullName']").isString());
        }
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            workbook.getSheetAt(0).getRow(1).getCell(1).setCellValue(912345678d);
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.2.phone']").isString());
        }
        assertEquals(before, registrationsCount());
    }

    @Test void duplicateEmailWithinFileIsCaseInsensitiveAndRejectsAllRows() throws Exception {
        long before = registrationsCount();
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            valid(workbook.getSheetAt(0), 2, 2);
            workbook.getSheetAt(0).getRow(2).getCell(2).setCellValue(" " + email(1).toUpperCase(Locale.ROOT) + " ");
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.3.email']").isString());
        }
        assertEquals(before, registrationsCount());
    }

    @Test void repeatedFileDoesNotDuplicateOrOverwriteImportedInformation() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            upload(workbook).andExpect(status().isOk());
            long afterFirst = registrationsCount();
            workbook.getSheetAt(0).getRow(1).getCell(0).setCellValue(unique + " Changed name");
            workbook.getSheetAt(0).getRow(1).getCell(2).setCellValue(email(1).toUpperCase(Locale.ROOT));
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.2.email']").isString());
            assertEquals(afterFirst, registrationsCount());
        }
        assertEquals(unique + " Học viên 1", row(email(1)).path("fullName").asText());
    }

    @Test void equivalentPhoneAndSameNameAndCourseCannotBypassDuplicatesWithAnotherEmail() throws Exception {
        long before = registrationsCount();
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            put(workbook.getSheetAt(0), 2, " " + (unique + " Học viên 1").toLowerCase(Locale.ROOT) + " ",
                    "+84912345678", email(2), " điện công nghiệp ", "", "");
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.3.phone']").isString());
            assertEquals(before, registrationsCount());
            workbook.getSheetAt(0).removeRow(workbook.getSheetAt(0).getRow(2));
            upload(workbook).andExpect(status().isOk());
            workbook.getSheetAt(0).getRow(1).getCell(1).setCellValue("84912345678");
            workbook.getSheetAt(0).getRow(1).getCell(2).setCellValue(email(2));
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.2.phone']").isString());
        }
        assertEquals(before + 1, registrationsCount());
    }

    @Test void emailsAlreadyUsedByAccountsOrAdmissionsCannotBeImportedAgain() throws Exception {
        User existing = actor("HR");
        UUID admission = UUID.randomUUID();
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,email,contact_time,created_date,program_name) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?)",
                admission, unique + " Existing", "0912345678", email(2).toUpperCase(Locale.ROOT), "any", "Điện công nghiệp");
        long before = registrationsCount();
        for (String email : List.of(existing.email.toUpperCase(Locale.ROOT), email(2))) {
            try (var workbook = workbook()) {
                valid(workbook.getSheetAt(0), 1, 1);
                workbook.getSheetAt(0).getRow(1).getCell(2).setCellValue(email);
                upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['row.2.email']").isString());
            }
        }
        assertEquals(before, registrationsCount());
        assertEquals("not-a-login-fixture", users.findById(existing.id).orElseThrow().passwordHash);
        verifyNoInteractions(mailer);
    }

    @Test void fileShapeSizeAndRowLimitsAreValidatedBeforeSaving() throws Exception {
        long before = registrationsCount();
        upload("broken.xlsx", "not a workbook".getBytes(StandardCharsets.UTF_8), manager)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        upload("students.xlsx", new byte[2 * 1024 * 1024 + 1], manager)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        try (var workbook = workbook()) {
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
            valid(workbook.getSheetAt(0), 1, 1);
            upload("students.xls", bytes(workbook), manager).andExpect(status().isBadRequest());
            workbook.getSheetAt(0).getRow(0).getCell(2).setCellValue("Email");
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        }
        try (var workbook = workbook()) {
            for (int index = 1; index <= 501; index++) valid(workbook.getSheetAt(0), index, index);
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        }
        mvc.perform(multipart(API + "/import").with(user(manager.email).roles("MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        assertEquals(before, registrationsCount());
    }

    @Test void bothEndpointsRequireManagerRoleAndScreenPermissionAndImportRequiresCsrf() throws Exception {
        byte[] content;
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1, 1);
            content = bytes(workbook);
        }
        long before = registrationsCount();
        mvc.perform(get(API + "/import-template")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(API + "/import").file(file("students.xlsx", content)).with(csrf()))
                .andExpect(status().isUnauthorized());
        for (String code : List.of("ADMIN", "HR", "STUDENT", "LECTURER", "ACCOUNTANT", "IT_SUPPORT")) {
            User other = actor(code);
            jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,'ListStudentAccount')", other.getRoleId());
            try {
                mvc.perform(get(API + "/import-template").with(user(other.email).roles("MANAGER")))
                        .andExpect(status().isForbidden());
                upload("students.xlsx", content, other).andExpect(status().isForbidden());
            } finally {
                jdbc.update("DELETE FROM role_function WHERE role_id=? AND function_code='ListStudentAccount'", other.getRoleId());
            }
        }
        jdbc.update("DELETE FROM role_function WHERE role_id=6 AND function_code='ListStudentAccount'");
        try {
            mvc.perform(get(API + "/import-template").with(user(manager.email).roles("MANAGER")))
                    .andExpect(status().isForbidden());
            upload("students.xlsx", content, manager).andExpect(status().isForbidden());
        } finally {
            jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (6,'ListStudentAccount')");
        }
        mvc.perform(multipart(API + "/import").file(file("students.xlsx", content))
                        .with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isForbidden());
        assertEquals(before, registrationsCount());
        verifyNoInteractions(mailer);
    }

    private User actor(String role) {
        var actor = new User();
        actor.email = UUID.randomUUID() + "@excel-actor.test";
        actor.fullName = "Kiểm thử " + role;
        actor.passwordHash = "not-a-login-fixture";
        actor.role = roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(actor);
    }

    private String email(int index) { return unique + "-" + index + "@student.test"; }
    private long registrationsCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM consultation_registrations", Long.class); }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    private JsonNode list() throws Exception {
        return body(mvc.perform(get(API).param("q", unique).with(user(manager.email).roles("MANAGER")))
                .andExpect(status().isOk()).andReturn());
    }
    private JsonNode row(String email) throws Exception {
        for (JsonNode row : list().path("items")) if (email.equals(row.path("email").asText())) return row;
        throw new AssertionError("Imported learner missing from account queue: " + email);
    }
    private XSSFWorkbook workbook() {
        var workbook = new XSSFWorkbook();
        put(workbook.createSheet("Hoc vien"), 0, HEADERS);
        return workbook;
    }
    private void valid(Sheet sheet, int row, int learner) {
        put(sheet, row, unique + " Học viên " + learner, "0912345678", email(learner), "Điện công nghiệp", "Bất kỳ", "Cần tư vấn");
    }
    private void put(Sheet sheet, int index, String... values) {
        Row row = sheet.getRow(index);
        if (row == null) row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) {
            var cell = row.getCell(column);
            if (cell == null) cell = row.createCell(column);
            cell.setCellValue(values[column]);
        }
    }
    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
    private MockMultipartFile file(String name, byte[] content) { return new MockMultipartFile("file", name, MIME, content); }
    private ResultActions upload(XSSFWorkbook workbook) throws Exception { return upload("students.xlsx", bytes(workbook), manager); }
    private ResultActions upload(String name, byte[] content, User actor) throws Exception {
        return mvc.perform(multipart(API + "/import").file(file(name, content))
                .with(user(actor.email).roles(actor.getRoleCode())).with(csrf()));
    }
}
