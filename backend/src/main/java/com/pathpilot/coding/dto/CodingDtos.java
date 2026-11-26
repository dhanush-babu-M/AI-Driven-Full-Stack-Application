package com.pathpilot.coding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CodingDtos {

    private CodingDtos() {
    }

    public record CodingActivityRequest(
            @NotNull(message = "Practice date is required.")
            LocalDate activityDate,

            @NotBlank(message = "Platform is required.")
            @Size(max = 60, message = "Platform must be 60 characters or fewer.")
            String platform,

            @NotBlank(message = "Problem title is required.")
            @Size(max = 200, message = "Problem title must be 200 characters or fewer.")
            String problemTitle,

            @Size(max = 80, message = "Topic must be 80 characters or fewer.")
            String topic,

            @Size(max = 20, message = "Difficulty must be 20 characters or fewer.")
            String difficulty,

            @NotBlank(message = "Result is required.")
            @Size(max = 20, message = "Result must be 20 characters or fewer.")
            String result,

            @Min(value = 0, message = "Problems solved cannot be negative.")
            @Max(value = 100, message = "Problems solved must be 100 or fewer.")
            Integer problemsSolved,

            @Min(value = 0, message = "Minutes spent cannot be negative.")
            @Max(value = 1440, message = "Minutes spent must be 1440 or fewer.")
            Integer minutesSpent,

            @Size(max = 500, message = "Reference URL must be 500 characters or fewer.")
            String referenceUrl,

            @Size(max = 4000, message = "Notes must be 4000 characters or fewer.")
            String notes
    ) {
    }

    public record CodingActivityDto(
            Long id,
            LocalDate activityDate,
            String platform,
            String problemTitle,
            String topic,
            String difficulty,
            String result,
            int problemsSolved,
            int minutesSpent,
            String referenceUrl,
            String notes,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc
    ) {
    }

    public record CodingPageResponse(
            List<CodingActivityDto> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            List<String> availableTopics
    ) {
    }

    public record HeatmapDayDto(
            LocalDate date,
            int githubCount,
            int githubCommitCount,
            int manualCount,
            int manualSolvedCount,
            int totalCount,
            int level,
            String combinedCategory
    ) {
    }

    public record HeatmapTotalsDto(
            int githubEvents,
            int githubCommits,
            int manualEntries,
            int manualSolved,
            int activeDays
    ) {
    }

    public record HeatmapResponse(
            LocalDate from,
            LocalDate to,
            String source,
            List<HeatmapDayDto> days,
            HeatmapTotalsDto totals
    ) {
    }

    public record DayGithubEventDto(
            Long id,
            String externalEventId,
            String eventType,
            String repoName,
            int commitCount,
            String payloadSummary,
            String htmlUrl,
            Instant occurredAtUtc,
            String dataSource
    ) {
    }

    public record DayLearningRecordDto(
            Long id,
            String topic,
            int minutesSpent,
            String notes,
            String dataSource
    ) {
    }

    public record DayActivityDetailResponse(
            LocalDate date,
            List<DayGithubEventDto> githubEvents,
            List<CodingActivityDto> codingActivities,
            List<DayLearningRecordDto> learningRecords
    ) {
    }

    public record InactivePeriodDto(
            LocalDate startDate,
            LocalDate endDate,
            int days,
            String label
    ) {
    }

    public record ChartPointDto(
            String label,
            LocalDate date,
            int solved,
            int attempted,
            int practiced,
            int total
    ) {
    }

    public record RuleBannerDto(
            String message,
            String source
    ) {
    }

    public record CodingStreakSummaryResponse(
            LocalDate from,
            LocalDate to,
            int daysInPeriod,
            int totalEntries,
            int totalSolved,
            int totalAttempted,
            int totalPracticed,
            int practiceDays,
            Double practiceActivityPercent,
            int currentStreak,
            int longestStreak,
            LocalDate lastPracticeDate,
            List<InactivePeriodDto> inactivePeriods,
            List<ChartPointDto> chartSeries,
            RuleBannerDto banner
    ) {
    }
}
