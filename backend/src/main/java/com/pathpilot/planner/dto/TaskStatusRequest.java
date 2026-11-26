package com.pathpilot.planner.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record TaskStatusRequest(
        @NotBlank(message = "Status is required.")
        String status,
        LocalDate rescheduleToDate
) {
}
