package com.erhms.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileInput(
        @NotBlank(message = "Nhập họ và tên")
        @Size(min = 2, max = 120, message = "Họ và tên phải từ 2 đến 120 ký tự") String fullName,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        @Pattern(regexp = "(?:0[0-9]{9}|\\+?84[0-9]{9})", message = "Số điện thoại không hợp lệ") String phone) {

    public ProfileInput {
        fullName = fullName == null ? null : fullName.strip();
        phone = phone == null ? null : phone.replaceAll("(?U)[\\s().-]", "");
        if (phone != null && phone.isEmpty()) {
            phone = null;
        }
    }
}
