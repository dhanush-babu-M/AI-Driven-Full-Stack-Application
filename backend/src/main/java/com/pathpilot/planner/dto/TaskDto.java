package com.pathpilot.planner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskDto(
        Long id,
        String title,
        String description,
        LocalDate taskDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        String category,
        String colorGroup,
        String priority,
        Integer durationMinutes,
        String notes,
        Long carriedFromTaskId,
        LocalDate rescheduledToDate,
        Long rescheduledToTaskId,
        boolean reminderEnabled,
        LocalTime reminderTime,
        String dataSource,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}
