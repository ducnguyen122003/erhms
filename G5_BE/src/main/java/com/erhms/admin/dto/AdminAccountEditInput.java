package com.erhms.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AdminAccountEditInput(@NotNull @Valid AdminAccountInput account,
        @NotNull @Pattern(regexp="ACTIVE|DISABLED|LOCKED|DISABLED_LOCKED") String status,
        @Size(max=1000) String changeNote,@NotNull Long version) {
}
