package com.pathpilot.planner.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CarryForwardRequest(
        @NotEmpty(message = "Select at least one task to carry forward.")
        List<Long> taskIds,

        @NotNull(message = "Target date is required.")
        LocalDate targetDate
) {
}
