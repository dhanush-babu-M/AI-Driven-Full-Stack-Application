package com.pathpilot.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record TaskRequest(
        @NotBlank(message = "Task title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        String description,

        @NotBlank(message = "Category is required.")
        String category,

        @NotNull(message = "Task date is required.")
        LocalDate taskDate,

        LocalTime startTime,
        LocalTime endTime,

        String priority,
        String status,
        String notes
) {
}
