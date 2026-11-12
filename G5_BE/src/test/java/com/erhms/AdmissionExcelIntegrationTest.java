package com.erhms;

import com.erhms.consultation.repository.ConsultationRegistrationRepository;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admission_excel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "logging.level.root=WARN", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdmissionExcelIntegrationTest {
    private static final String IMPORT = "/api/workspace/admissions/import";
    private static final String TEMPLATE = "/api/workspace/admissions/import-template";
    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {"Họ và tên (*)", "Số điện thoại (*)", "Email",
            "Khóa quan tâm (*)", "Thời gian liên hệ", "Nội dung tư vấn"};

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ConsultationRegistrationRepository registrations;
    @Autowired JdbcTemplate jdbc;
    private User hr;

    @BeforeEach
    void setup() {
        hr = actor("HR");
    }

    @Test
    void templateIsAnActualBlankWorkbookWithInstructionsAndTextPhoneFormatting() throws Exception {
        var response = mvc.perform(get(TEMPLATE).with(user(hr.email).roles("HR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MIME))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mau-nhap-hoc-sinh.xlsx")))
                .andReturn().getResponse();
        Files.write(Path.of("target", "mau-nhap-hoc-sinh.xlsx"), response.getContentAsByteArray());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Hoc sinh", sheet.getSheetName());
            assertNotNull(workbook.getSheet("Huong dan"));
            for (int column = 0; column < HEADERS.length; column++) {
                assertEquals(HEADERS[column], sheet.getRow(0).getCell(column).getStringCellValue());
            }
            assertEquals("@", sheet.getColumnStyle(1).getDataFormatString());
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                for (var cell : row) {
                    assertTrue(cell.getCellType() == CellType.BLANK
                            || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()),
                            "The template must not accidentally import example students");
                }
            }
            assertFalse(sheet.getDataValidations().isEmpty(), "Contact time should offer choices");
        }
    }

    @Test
    void completedTemplatePreservesLeadingZeroAndAppearsAsNewAdmission() throws Exception {
        UUID olderId = UUID.randomUUID();
        jdbc.update("INSERT INTO consultation_registrations(id,full_name,phone,contact_time,program_name,created_date) VALUES (?,?,?,?,?,?)",
                olderId, "Aaa Hồ sơ cũ", "0901111111", "any", "Khóa cũ", java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        byte[] template = mvc.perform(get(TEMPLATE).with(user(hr.email).roles("HR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            put(workbook.getSheetAt(0), 1, "  Nguyễn Văn An  ", " 0912.345-678 ",
                    " an@example.com ", " Điện công nghiệp ", "Buổi sáng", " Cần tư vấn học phí. ");
            put(workbook.getSheetAt(0), 3, "Trần Thị Bình", "+84987654321", "", "May công nghiệp", "", "");
            upload(workbook).andExpect(status().isOk())
                    .andExpect(jsonPath("$.importedCount").value(2))
                    .andExpect(jsonPath("$.notice").isString());
        }
        assertEquals(3, registrations.count());
        var saved = registrations.findAll().stream().filter(row -> row.fullName.equals("Nguyễn Văn An")).findFirst().orElseThrow();
        assertEquals("0912345678", saved.phone);
        assertEquals("an@example.com", saved.email);
        assertEquals("Điện công nghiệp", saved.programName);
        assertEquals("morning", saved.contactTime);
        assertEquals("Cần tư vấn học phí.", saved.description);
        assertNotNull(saved.createdDate);
        var optional = registrations.findAll().stream().filter(row -> row.fullName.equals("Trần Thị Bình")).findFirst().orElseThrow();
        assertEquals("any", optional.contactTime);
        assertNull(optional.email);
        assertNull(optional.description);
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").param("q", "Nguyễn Văn An")
                        .with(user(hr.email).roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(saved.id.toString()))
                .andExpect(jsonPath("$.items[0].status").value("NEW"));
        mvc.perform(get("/api/workspace/screens/ListAdmissionApplication").with(user(hr.email).roles("HR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[2].id").value(olderId.toString()));
    }

    @Test
    void invalidLaterRowRejectsTheWholeFileAndReportsTheActualExcelRow() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            put(workbook.getSheetAt(0), 4, "Trần Thị Bình", "invalid-phone", "", "May công nghiệp", "", "");
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EXCEL_VALIDATION"))
                    .andExpect(jsonPath("$.fields['row.5.phone']").isString());
        }
        assertEquals(0, registrations.count());
    }

    @Test
    void beanValidationRulesAlsoApplyToImportedNamesEmailsProgramsAndContactTimes() throws Exception {
        for (var invalid : List.of(Map.entry(0, "A"), Map.entry(0, "A".repeat(121)),
                Map.entry(1, "09123abc78"), Map.entry(2, "invalid-email"), Map.entry(3, ""),
                Map.entry(3, "Đ".repeat(256)), Map.entry(4, "midnight"), Map.entry(5, "A".repeat(2001)))) {
            try (var workbook = workbook()) {
                valid(workbook.getSheetAt(0), 1);
                workbook.getSheetAt(0).getRow(1).getCell(invalid.getKey()).setCellValue(invalid.getValue());
                upload(workbook).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("EXCEL_VALIDATION"));
            }
            assertEquals(0, registrations.count(), "Invalid column " + invalid.getKey());
        }
    }

    @Test
    void duplicateRowsUseCanonicalPhoneAndNamesAndNeverInsert() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            put(workbook.getSheetAt(0), 2, " nguyễn văn an ", "+84912345678", "new@example.com",
                    " điện công nghiệp ", "Buổi chiều", "Khác nội dung");
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['row.3.phone']").isString());
        }
        assertEquals(0, registrations.count());
    }

    @Test
    void importingAnExistingRegistrationAgainDoesNotDuplicateOrOverwriteIt() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            upload(workbook).andExpect(status().isOk());
            var row = workbook.getSheetAt(0).getRow(1);
            row.getCell(1).setCellValue("84912345678");
            row.getCell(2).setCellValue("changed@example.com");
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['row.2.phone']").isString());
        }
        assertEquals(1, registrations.count());
        assertEquals("an@example.com", registrations.findAll().get(0).email);
    }

    @Test
    void formulasAndNumericPhoneCellsCannotSilentlyCorruptStudentInformation() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            workbook.getSheetAt(0).getRow(1).getCell(0).setCellFormula("\"Nguyễn Văn An\"");
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['row.2.fullName']").isString());
        }
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            workbook.getSheetAt(0).getRow(1).getCell(1).setCellValue(912345678d);
            upload(workbook).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['row.2.phone']").isString());
        }
        assertEquals(0, registrations.count());
    }

    @Test
    void emptyMalformedAndUnexpectedHeadersAreUsefulValidationErrors() throws Exception {
        upload("broken.xlsx", "not a workbook".getBytes(StandardCharsets.UTF_8), hr)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        try (var workbook = workbook()) {
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
            valid(workbook.getSheetAt(0), 1);
            workbook.getSheetAt(0).getRow(0).getCell(1).setCellValue("Wrong header");
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        }
        assertEquals(0, registrations.count());
    }

    @Test
    void oldExcelAndFilesOverTwoMegabytesAreRejected() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            upload("students.xls", bytes(workbook), hr).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.file").isString());
        }
        upload("students.xlsx", new byte[2 * 1024 * 1024 + 1], hr).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.file").isString());
        assertEquals(0, registrations.count());
    }

    @Test
    void moreThanFiveHundredRowsIsRejectedBeforeWritingAnything() throws Exception {
        try (var workbook = workbook()) {
            for (int index = 1; index <= 501; index++) {
                put(workbook.getSheetAt(0), index, "Học sinh " + index, "0912345678", "", "Điện công nghiệp", "", "");
            }
            upload(workbook).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.file").isString());
        }
        assertEquals(0, registrations.count());
    }

    @Test
    void loginRoleAndScreenGrantAreRequiredForBothEndpoints() throws Exception {
        byte[] content;
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            content = bytes(workbook);
        }
        mvc.perform(get(TEMPLATE)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(IMPORT).file(file("students.xlsx", content)).with(csrf()))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "MANAGER", "STUDENT", "LECTURER", "ACCOUNTANT", "IT_SUPPORT")) {
            User other = actor(role);
            mvc.perform(get(TEMPLATE).with(user(other.email).roles(role))).andExpect(status().isForbidden());
            upload("students.xlsx", content, other).andExpect(status().isForbidden());
        }
        jdbc.update("DELETE FROM role_function WHERE role_id=7 AND function_code='ListAdmissionApplication'");
        mvc.perform(get(TEMPLATE).with(user(hr.email).roles("HR"))).andExpect(status().isForbidden());
        upload("students.xlsx", content, hr).andExpect(status().isForbidden());
        assertEquals(0, registrations.count());
    }

    @Test
    void aMissingCsrfTokenPreventsTheImport() throws Exception {
        try (var workbook = workbook()) {
            valid(workbook.getSheetAt(0), 1);
            mvc.perform(multipart(IMPORT).file(file("students.xlsx", bytes(workbook)))
                            .with(user(hr.email).roles("HR")))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, registrations.count());
    }

    private User actor(String role) {
        var actor = new User();
        actor.email = UUID.randomUUID() + "@excel.test";
        actor.fullName = "Kiểm thử " + role;
        actor.passwordHash = "not-a-login-fixture";
        actor.role = roles.findByCode(role).orElseThrow();
        return users.saveAndFlush(actor);
    }

    private XSSFWorkbook workbook() {
        var workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Hoc sinh");
        put(sheet, 0, HEADERS);
        return workbook;
    }

    private void valid(Sheet sheet, int row) {
        put(sheet, row, "Nguyễn Văn An", "0912345678", "an@example.com", "Điện công nghiệp", "Bất kỳ", "Cần tư vấn");
    }

    private void put(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]);
    }

    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, MIME, content);
    }

    private ResultActions upload(XSSFWorkbook workbook) throws Exception {
        return upload("students.xlsx", bytes(workbook), hr);
    }

    private ResultActions upload(String name, byte[] content, User actor) throws Exception {
        return mvc.perform(multipart(IMPORT).file(file(name, content))
                .with(user(actor.email).roles(actor.getRoleCode())).with(csrf()));
    }
}
