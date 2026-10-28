package com.erhms.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminAccountInput(
        @NotBlank(message = "Nhập họ và tên")
        @Size(min = 2, max = 120, message = "Họ và tên phải từ 2 đến 120 ký tự") String fullName,
        @NotBlank(message = "Nhập email đăng nhập")
        @Email(message = "Email không hợp lệ")
        @Size(max = 190, message = "Email tối đa 190 ký tự") String email,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        @Pattern(regexp = "(?:0[0-9]{9}|\\+?84[0-9]{9})", message = "Số điện thoại không hợp lệ") String phone,
        @NotNull(message = "Chọn quyền hạn")
        @Min(value = 1, message = "Vai trò không hợp lệ")
        @Max(value = 7, message = "Vai trò không hợp lệ") Long roleId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
    public AdminAccountInput {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.replaceAll("(?U)[\\s().-]", "");
        if (phone != null && phone.isEmpty()) phone = null;
    }

    @Override
    public String toString() { return "AdminAccountInput[REDACTED]"; }
}
