package com.pathpilot.planner.dto;

import java.time.LocalDate;

public record MonthDaySummaryDto(
        LocalDate date,
        int completedCount,
        int pendingCount,
        int skippedCount,
        String dayStatus
) {
}
