package com.pathpilot.planner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ReminderDtos {

    private ReminderDtos() {
    }

    public record NotificationPreferencesRequest(
            boolean browserEnabled,

            @Min(value = 0, message = "Lead minutes cannot be negative.")
            @Max(value = 120, message = "Lead minutes cannot exceed 120.")
            int leadMinutes,

            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
    }

    public record NotificationPreferencesDto(
            boolean browserEnabled,
            int leadMinutes,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
    }

    public record UpcomingReminderDto(
            Long taskId,
            String title,
            String category,
            LocalDate taskDate,
            LocalTime startTime,
            LocalTime endTime,
            Instant reminderAtUtc,
            String status,
            boolean inQuietHours,
            String itemType,
            Long followupId,
            Long jobApplicationId,
            String linkPath
    ) {
    }
}
