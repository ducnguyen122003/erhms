package com.erhms.consultation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ConsultationDtos {

    private ConsultationDtos() {
    }

    public record Input(
            @NotBlank(message = "Nhập họ và tên")
            @Size(min = 2, max = 120, message = "Họ và tên phải từ 2 đến 120 ký tự") String fullName,
            @NotBlank(message = "Nhập số điện thoại")
            @Size(max = 20)
            @Pattern(regexp = "(?:0[0-9]{9}|\\+?84[0-9]{9})",
                    message = "Số điện thoại không hợp lệ") String phone,
            @Email(message = "Email không hợp lệ")
            @Size(max = 190, message = "Email tối đa 190 ký tự") String email,
            @NotBlank(message = "Chọn thời gian")
            @Pattern(regexp = "any|morning|afternoon", message = "Thời gian liên hệ không hợp lệ") String contactTime,
            @Size(max = 2000, message = "Nội dung tư vấn tối đa 2000 ký tự") String description,
            @JsonProperty("program_name")
            @NotBlank(message = "Chọn ngành nghề hoặc khóa học quan tâm")
            @Size(max = 255, message = "Tên ngành nghề hoặc khóa học tối đa 255 ký tự") String programName) {

        public Input {
            fullName = trim(fullName);
            phone = phone == null ? null : phone.replaceAll("(?U)[\\s().-]", "");
            email = optional(email);
            contactTime = trim(contactTime);
            description = optional(description);
            programName = trim(programName);
        }
    }

    public record Receipt(UUID id, String fullName, @JsonProperty("created_date") Instant createdDate) {
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }

    private static String optional(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
