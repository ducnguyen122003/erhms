package com.erhms.recruitment.dto;

import com.erhms.recruitment.enums.RequestStatus;
import com.erhms.recruitment.enums.WorkShift;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RequestDtos {

    private RequestDtos() {
    }

    public record Input(
            @NotBlank(message = "Nhập vị trí tuyển dụng") @Size(max = 160) String title,
            @Min(value = 1, message = "Số lượng tối thiểu là 1")
            @Max(value = 10000, message = "Số lượng tối đa là 10.000") int quantity,
            @NotNull(message = "Chọn ca làm việc") WorkShift shift,
            @NotBlank(message = "Nhập yêu cầu kỹ năng") @Size(max = 2000) String skills,
            @NotBlank(message = "Nhập địa điểm làm việc") @Size(max = 200) String location,
            @NotNull(message = "Chọn ngày cần nhân sự") LocalDate neededBy,
            @PositiveOrZero long version) {
    }

    public record Command(@PositiveOrZero long version, @Size(max = 1000) String note) {
    }

    public record View(
            UUID id,
            Long userId,
            String userName,
            String title,
            int quantity,
            WorkShift shift,
            String skills,
            String location,
            LocalDate neededBy,
            RequestStatus status,
            String reviewNote,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record PageResult(List<View> items, long total, int page, int size, int totalPages) {
    }

    public record EventView(String action, String note, Instant occurredAt) {
    }
}
