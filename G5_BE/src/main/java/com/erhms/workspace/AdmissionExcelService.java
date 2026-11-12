package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.consultation.dto.ConsultationDtos.Input;
import com.erhms.consultation.entity.ConsultationRegistration;
import com.erhms.consultation.repository.ConsultationRegistrationRepository;
import com.erhms.user.service.CurrentUser;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Imports only validated registration data; it does not create login accounts or enrollments. */
@Service
public class AdmissionExcelService {
    public static final String[] HEADERS = {
            "Họ và tên (*)", "Số điện thoại (*)", "Email", "Khóa quan tâm (*)",
            "Thời gian liên hệ", "Nội dung tư vấn"
    };
    private static final String[] FIELDS = {"fullName", "phone", "email", "programName", "contactTime", "description"};
    private static final int MAX_ROWS = 500;
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private final CurrentUser current;
    private final ScreenCatalog catalog;
    private final ScreenPermissionsService permissions;
    private final ConsultationRegistrationRepository registrations;
    private final WorkflowData data;
    private final Validator validator;

    public AdmissionExcelService(CurrentUser current, ScreenCatalog catalog, ScreenPermissionsService permissions,
                                 ConsultationRegistrationRepository registrations, WorkflowData data, Validator validator) {
        this.current = current;
        this.catalog = catalog;
        this.permissions = permissions;
        this.registrations = registrations;
        this.data = data;
        this.validator = validator;
    }

    private void requireAccess() {
        var user = current.get();
        var screen = catalog.get("ListAdmissionApplication");
        catalog.requireRole(screen, user.getRoleCode());
        permissions.require(screen.functionCode());
        WorkflowData.role(user, "HR");
    }

    public byte[] template() {
        requireAccess();
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hoc sinh");
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            textStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
            textStyle.setWrapText(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.cloneStyleFrom(textStyle);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(32);
            int[] widths = {28, 24, 32, 38, 24, 60};
            for (int column = 0; column < HEADERS.length; column++) {
                sheet.setColumnWidth(column, widths[column] * 256);
                sheet.setDefaultColumnStyle(column, textStyle);
                Cell cell = header.createCell(column, CellType.STRING);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
            }
            // Styled blank cells make Excel retain the leading zero even when data is pasted.
            for (int rowNumber = 1; rowNumber <= MAX_ROWS; rowNumber++) {
                Row row = sheet.createRow(rowNumber);
                for (int column = 0; column < HEADERS.length; column++) {
                    row.createCell(column, CellType.BLANK).setCellStyle(textStyle);
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, MAX_ROWS, 0, HEADERS.length - 1));
            var helper = sheet.getDataValidationHelper();
            var validation = helper.createValidation(helper.createExplicitListConstraint(
                    new String[]{"Bất kỳ", "Buổi sáng", "Buổi chiều"}), new CellRangeAddressList(1, MAX_ROWS, 4, 4));
            validation.setEmptyCellAllowed(true);
            validation.setShowErrorBox(true);
            validation.createErrorBox("Thời gian liên hệ", "Chọn Bất kỳ, Buổi sáng hoặc Buổi chiều.");
            sheet.addValidationData(validation);

            Sheet guide = workbook.createSheet("Huong dan");
            guide.setColumnWidth(0, 120 * 256);
            String[] instructions = {
                    "HƯỚNG DẪN NHẬP HỒ SƠ HỌC SINH",
                    "Điền dữ liệu ở sheet Hoc sinh, bắt đầu từ dòng 2. Giữ nguyên tên sheet và hàng tiêu đề.",
                    "Các cột có (*) bắt buộc: Họ và tên, Số điện thoại, Khóa quan tâm. Mỗi dòng là một hồ sơ.",
                    "Chỉ nhận file .xlsx, tối đa 2 MB và 500 hồ sơ mỗi lần. Bỏ qua dòng hoàn toàn trống.",
                    "Số điện thoại: giữ kiểu Text để không mất số 0 đầu; chấp nhận 0xxxxxxxxx, 84xxxxxxxxx hoặc +84xxxxxxxxx.",
                    "Họ và tên: 2–120 ký tự; Email nếu có phải hợp lệ, tối đa 190 ký tự; Khóa quan tâm: tối đa 255 ký tự.",
                    "Thời gian liên hệ: Bất kỳ / Buổi sáng / Buổi chiều (hoặc any / morning / afternoon); để trống là Bất kỳ.",
                    "Nội dung tư vấn: không bắt buộc, tối đa 2000 ký tự. Chỉ nhập văn bản, không dùng công thức.",
                    "Các dòng trùng họ tên, số điện thoại và khóa quan tâm trong file hoặc hệ thống sẽ được báo lỗi.",
                    "Nếu có lỗi, toàn bộ file chưa được lưu. Sửa các dòng được thông báo rồi nhập lại.",
                    "Hồ sơ nhập thành công có trạng thái Mới tiếp nhận. Chưa tạo tài khoản đăng nhập hoặc xếp lớp học.",
                    "Ví dụ dưới đây chỉ để tham khảo, không được nhập tự động:"
            };
            for (int index = 0; index < instructions.length; index++) {
                Row row = guide.createRow(index);
                row.setHeightInPoints(index == 0 ? 30 : 38);
                Cell cell = row.createCell(0);
                cell.setCellValue(instructions[index]);
                cell.setCellStyle(index == 0 ? headerStyle : textStyle);
            }
            String[] examples = {
                    "Họ và tên (*): Nguyễn Văn An", "Số điện thoại (*): 0901234567", "Email: an@example.com",
                    "Khóa quan tâm (*): May công nghiệp", "Thời gian liên hệ: Buổi sáng", "Nội dung tư vấn: Muốn tìm hiểu lịch học."
            };
            for (int index = 0; index < examples.length; index++) {
                Cell cell = guide.createRow(instructions.length + index).createCell(0);
                cell.setCellValue(examples[index]);
                cell.setCellStyle(textStyle);
            }
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không tạo được file Excel mẫu", exception);
        }
    }

    @Transactional
    public int importFile(MultipartFile file) {
        requireAccess();
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalidFile("Chọn file Excel có dữ liệu, dung lượng không quá 2 MB.");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw invalidFile("Chỉ hỗ trợ file Excel .xlsx. Hãy tải và điền file mẫu.");
        }
        List<ImportRow> rows;
        try {
            byte[] bytes = file.getBytes();
            checkArchive(bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                if (workbook.getWorkbookType() != XSSFWorkbookType.XLSX) throw invalidFile("Chỉ nhận file .xlsx không có macro.");
                rows = readRows(workbook);
            }
        } catch (WorkflowException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidFile("Không đọc được file Excel. File có thể bị hỏng, được đặt mật khẩu hoặc sai định dạng. Hãy dùng file mẫu .xlsx.");
        }

        // Serialize Excel imports across tabs/staff/server instances until transaction commit.
        // Public consultation registration intentionally still allows repeat enquiries.
        data.one("SELECT role_id FROM m_role WHERE role_id=7 FOR UPDATE");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<DuplicateKey, Integer> seen = new HashMap<>();
        for (ImportRow row : rows) {
            Input input = row.input();
            DuplicateKey key = key(input.fullName(), input.phone(), input.programName());
            Integer previous = seen.putIfAbsent(key, row.number());
            if (previous != null) {
                addError(errors, row.number(), "phone", "Trùng họ tên, số điện thoại và khóa quan tâm với dòng " + previous + ".");
            } else {
                // Match name/course in SQL, then normalize phone in Java for +84, 84 and 0 formats.
                var matches = data.rows("SELECT phone FROM consultation_registrations WHERE LOWER(TRIM(full_name))=? AND LOWER(TRIM(program_name))=?",
                        key.name(), key.program());
                if (matches.stream().anyMatch(existing -> key.phone().equals(normalizePhone(existing.get("phone").toString())))) {
                    addError(errors, row.number(), "phone", "Hồ sơ có cùng họ tên, số điện thoại và khóa quan tâm đã tồn tại trong hệ thống.");
                }
            }
        }
        rejectErrors(errors);
        Instant created = Instant.now();
        List<ConsultationRegistration> entities = new ArrayList<>();
        for (ImportRow row : rows) {
            Input input = row.input();
            var registration = new ConsultationRegistration();
            registration.id = UUID.randomUUID();
            registration.fullName = input.fullName();
            registration.phone = input.phone();
            registration.email = input.email();
            registration.programName = input.programName();
            registration.contactTime = input.contactTime();
            registration.description = input.description();
            registration.createdDate = created;
            entities.add(registration);
        }
        registrations.saveAllAndFlush(entities);
        return entities.size();
    }

    private List<ImportRow> readRows(XSSFWorkbook workbook) {
        Sheet sheet = workbook.getSheet("Hoc sinh");
        if (sheet == null) throw invalidFile("Thiếu sheet Hoc sinh. Hãy giữ nguyên tên sheet trong file mẫu.");
        if (sheet.getLastRowNum() > 5000) throw invalidFile("Sheet có quá nhiều dòng. Hãy chuyển tối đa 500 hồ sơ sang file mẫu mới.");
        Row header = sheet.getRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = header == null ? null : header.getCell(column);
            if (cell == null || cell.getCellType() != CellType.STRING || !HEADERS[column].equals(cell.getStringCellValue().strip())) {
                throw invalidFile("Tiêu đề cột " + (column + 1) + " phải là “" + HEADERS[column] + "”. Hãy dùng file mẫu và giữ nguyên thứ tự cột.");
            }
        }
        if (hasExtraValues(header)) throw invalidFile("File mẫu chỉ có 6 cột từ A đến F. Hãy bỏ các cột bổ sung có dữ liệu.");
        List<ImportRow> rows = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        int count = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0 || empty(row)) continue;
            int number = row.getRowNum() + 1;
            if (++count > MAX_ROWS) throw invalidFile("Mỗi file chỉ được nhập tối đa 500 hồ sơ. Hãy chia thành nhiều file.");
            if (hasExtraValues(row)) addError(errors, number, "file", "Có dữ liệu ngoài 6 cột của file mẫu (A–F).");
            String[] values = new String[HEADERS.length];
            for (int column = 0; column < values.length; column++) {
                Cell cell = row.getCell(column);
                if (cell == null || cell.getCellType() == CellType.BLANK) values[column] = "";
                else if (cell.getCellType() != CellType.STRING) {
                    addError(errors, number, FIELDS[column], cell.getCellType() == CellType.FORMULA
                            ? "Không dùng công thức. Hãy dán giá trị dạng văn bản."
                            : column == 1 ? "Số điện thoại phải có kiểu Text để giữ số 0 đầu. Hãy định dạng Text rồi nhập lại số đầy đủ."
                            : "Ô dữ liệu phải là văn bản (Text).");
                    values[column] = "";
                } else values[column] = cell.getStringCellValue();
            }
            Input input = new Input(values[0], values[1], values[2], contactTime(values[4]), values[5], values[3]);
            validator.validate(input).stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString() + violation.getMessage()))
                    .forEach(violation -> addError(errors, number, violation.getPropertyPath().toString(), violation.getMessage()));
            rows.add(new ImportRow(number, input));
        }
        if (count == 0) throw invalidFile("File chưa có dữ liệu. Điền hồ sơ vào sheet Hoc sinh từ dòng 2 rồi nhập lại.");
        rejectErrors(errors);
        return rows;
    }

    private static boolean empty(Row row) {
        for (Cell cell : row) if (hasValue(cell)) return false;
        return true;
    }

    private static boolean hasExtraValues(Row row) {
        for (Cell cell : row) if (cell.getColumnIndex() >= HEADERS.length && hasValue(cell)) return true;
        return false;
    }

    private static boolean hasValue(Cell cell) {
        return cell.getCellType() != CellType.BLANK
                && !(cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    private static String contactTime(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "", "any", "bất kỳ" -> "any";
            case "morning", "buổi sáng" -> "morning";
            case "afternoon", "buổi chiều" -> "afternoon";
            default -> value.strip();
        };
    }

    private static DuplicateKey key(String name, String phone, String program) {
        return new DuplicateKey(name.strip().toLowerCase(Locale.ROOT), normalizePhone(phone), program.strip().toLowerCase(Locale.ROOT));
    }

    private static String normalizePhone(String phone) {
        String value = phone.replaceAll("(?U)[\\s().-]", "");
        if (value.startsWith("+84")) return "0" + value.substring(3);
        if (value.startsWith("84")) return "0" + value.substring(2);
        return value;
    }

    /** Bound expanded XML before POI allocates its object model; no archive content is extracted. */
    private static void checkArchive(byte[] bytes) throws IOException {
        long expanded = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 200) throw invalidFile("File Excel quá phức tạp. Hãy sao chép dữ liệu vào file mẫu mới.");
                int length;
                while ((length = zip.read(buffer)) != -1) {
                    expanded += length;
                    if (expanded > 24L * 1024 * 1024) throw invalidFile("Nội dung Excel vượt giới hạn xử lý. Hãy chia nhỏ dữ liệu và dùng file mẫu mới.");
                }
            }
        }
        if (entries == 0) throw invalidFile("File không phải Excel .xlsx hợp lệ. Hãy dùng file mẫu.");
    }

    private static void addError(Map<String, String> errors, int row, String field, String message) {
        String label = field;
        for (int index = 0; index < FIELDS.length; index++) if (FIELDS[index].equals(field)) label = HEADERS[index].replace(" (*)", "");
        if (field.equals("file")) label = "Dữ liệu";
        errors.putIfAbsent("row." + row + "." + field, "Dòng " + row + " · " + label + ": " + message);
    }

    private static void rejectErrors(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new WorkflowException(HttpStatus.BAD_REQUEST, "EXCEL_VALIDATION",
                "Chưa nhập dữ liệu. Hãy sửa các lỗi trong file Excel rồi thử lại.", errors);
    }

    private static WorkflowException invalidFile(String message) {
        return new WorkflowException(HttpStatus.BAD_REQUEST, "EXCEL_VALIDATION", message, Map.of("file", message));
    }

    private record ImportRow(int number, Input input) {}
    private record DuplicateKey(String name, String phone, String program) {}
}
