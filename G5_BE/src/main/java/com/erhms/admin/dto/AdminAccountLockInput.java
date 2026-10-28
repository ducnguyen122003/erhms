package com.erhms.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public record AdminAccountLockInput(
        @NotNull(message = "Chọn trạng thái khóa của tài khoản")
        @JsonDeserialize(using = AdminAccountStatusInput.StrictBooleanDeserializer.class) Boolean accountLocked) {
}
