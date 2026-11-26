package com.pathpilot.planner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank(message = "Goal title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            String title,

            String category,

            @NotBlank(message = "Scope is required (WEEK or MONTH).")
            String scope,

            @NotNull(message = "Period start date is required.")
            LocalDate periodStart,

            @Min(value = 1, message = "Target count must be at least 1.")
            int targetCount,

            @Min(value = 0, message = "Done count cannot be negative.")
            int doneCount,

            String description,
            String status
    ) {
    }

    public record GoalCarryForwardRequest(
            @NotEmpty(message = "Select at least one goal to carry forward.")
            List<Long> goalIds,

            @NotNull(message = "Target period start date is required.")
            LocalDate targetPeriodStart
    ) {
    }

    public record GoalDto(
            Long id,
            String title,
            String category,
            String categoryLabel,
            String colorGroup,
            String scope,
            LocalDate periodStart,
            LocalDate periodEnd,
            int targetCount,
            int doneCount,
            String doneCountSource,
            String calculationNote,
            String description,
            String status,
            Long carriedFromGoalId
    ) {
    }

    public record WeeklyReviewRequest(
            @NotNull(message = "Week start date is required.")
            LocalDate weekStartDate,
            String whatWentWell,
            String carryForwardNotes
    ) {
    }

    public record WeeklyReviewDto(
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            String whatWentWell,
            String carryForwardNotes,
            Instant updatedAtUtc
    ) {
    }
}
