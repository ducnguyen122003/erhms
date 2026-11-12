package com.erhms.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workspace/admissions")
public class AdmissionExcelController {
    private final AdmissionExcelService service;

    public AdmissionExcelController(AdmissionExcelService service) {
        this.service = service;
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("mau-nhap-hoc-sinh.xlsx").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(service.template());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importFile(@RequestParam(value = "file", required = false) MultipartFile file, HttpServletRequest request) {
        request.setAttribute("workflow.action", "WORKFLOW_ADMISSIONS_IMPORT");
        request.setAttribute("workflow.label", "Nhập hồ sơ tư vấn từ Excel");
        int count = service.importFile(file);
        request.setAttribute("admin.changeNote", "Đã nhập " + count + " hồ sơ từ Excel");
        return Map.of("importedCount", count, "notice",
                "Đã nhập " + count + " hồ sơ học sinh từ Excel. Trạng thái: Mới tiếp nhận.");
    }
}
