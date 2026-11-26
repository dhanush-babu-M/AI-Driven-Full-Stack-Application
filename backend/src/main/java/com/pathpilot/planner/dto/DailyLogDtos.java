package com.pathpilot.planner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class DailyLogDtos {

    private DailyLogDtos() {
    }

    public record DailyLogRequest(
            String reflectionNote
    ) {
    }

    public record RestDayRequest(
            String note
    ) {
    }

    public record LearningRecordRequest(
            @NotNull(message = "Record date is required.")
            LocalDate recordDate,

            @NotBlank(message = "Topic is required.")
            @Size(max = 200, message = "Topic must be 200 characters or fewer.")
            String topic,

            @Min(value = 1, message = "Minutes spent must be at least 1.")
            @Max(value = 1440, message = "Minutes spent cannot exceed 1440.")
            int minutesSpent,

            String notes
    ) {
    }

    public record LearningRecordDto(
            Long id,
            LocalDate recordDate,
            String topic,
            int minutesSpent,
            String notes,
            String dataSource
    ) {
    }

    public record DailyLogResponse(
            LocalDate date,
            String dayStatus,
            String reflectionNote,
            List<LearningRecordDto> learningRecords,
            int totalLearningMinutes
    ) {
    }
}
