package com.pathpilot.progress.dto;

import com.pathpilot.planner.dto.MonthDaySummaryDto;
import com.pathpilot.planner.dto.TaskDto;
import com.pathpilot.progress.dto.DashboardResponse.CategoryRatesDto;

import java.time.LocalDate;
import java.util.List;

public final class PeriodProgressDtos {

    private PeriodProgressDtos() {
    }

    public record DayTaskCountDto(
            LocalDate date,
            int completedCount,
            int pendingCount,
            int skippedCount,
            int rescheduledCount,
            boolean hasTasks
    ) {
    }

    public record WeeklyProgressResponse(
            LocalDate weekStart,
            LocalDate weekEnd,
            String timeZone,
            int plannedCount,
            int completedCount,
            int pendingCount,
            int skippedCount,
            int rescheduledCount,
            Double completionRate,
            CategoryRatesDto categoryRates,
            List<DayTaskCountDto> days,
            List<TaskDto> tasks
    ) {
    }

    public record CategoryChangeDto(
            Double learning,
            Double practice,
            Double projects,
            Double applications
    ) {
    }

    public record MonthMilestoneDto(
            Long id,
            Long projectId,
            String title,
            LocalDate dueDate,
            String status,
            String dataSource
    ) {
    }

    public record MonthlyProgressResponse(
            String month,
            LocalDate monthStart,
            LocalDate monthEnd,
            String timeZone,
            int plannedCount,
            int completedCount,
            int pendingCount,
            int skippedCount,
            int rescheduledCount,
            Double completionRate,
            Double changeVsPreviousMonth,
            CategoryRatesDto categoryRates,
            CategoryChangeDto categoryChanges,
            List<MonthDaySummaryDto> daySummaries,
            List<MonthMilestoneDto> milestones
    ) {
    }
}
