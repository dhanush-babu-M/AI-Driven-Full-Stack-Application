package com.pathpilot.progress;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProgressCalculatorTest {

    @Test
    void noTasks_returnsNullNotZero() {
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(List.of());
        assertNull(summary.completionRate());
        assertEquals(0, summary.plannedCount());
        assertNull(ProgressCalculator.calculateTaskCompletionRate(null));
    }

    @Test
    void allComplete_returnsHundred() {
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(
                List.of("COMPLETED", "COMPLETED", "COMPLETED")
        );
        assertEquals(3, summary.plannedCount());
        assertEquals(3, summary.completedCount());
        assertEquals(100.0, summary.completionRate());
    }

    @Test
    void mixedStatuses_calculatesExactRate() {
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(
                List.of("COMPLETED", "COMPLETED", "PENDING", "SKIPPED")
        );
        assertEquals(4, summary.plannedCount());
        assertEquals(2, summary.completedCount());
        assertEquals(1, summary.pendingCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(50.0, summary.completionRate());
    }

    @Test
    void rescheduledTasks_excludedFromBothSidesOfFormula() {
        ProgressCalculator.TaskSummary mixed = ProgressCalculator.summarizeTasks(
                List.of("COMPLETED", "PENDING", "RESCHEDULED", "RESCHEDULED")
        );
        // Only 1 COMPLETED and 1 PENDING count => 1 / 2 * 100 = 50.0%
        assertEquals(2, mixed.plannedCount());
        assertEquals(1, mixed.completedCount());
        assertEquals(2, mixed.rescheduledCount());
        assertEquals(50.0, mixed.completionRate());

        // If every task was rescheduled, planned = 0 => null (not 0%)
        ProgressCalculator.TaskSummary allRescheduled = ProgressCalculator.summarizeTasks(
                List.of("RESCHEDULED", "RESCHEDULED")
        );
        assertEquals(0, allRescheduled.plannedCount());
        assertNull(allRescheduled.completionRate());
    }

    @Test
    void projectProgress_handlesEmptyAndMixedMilestones() {
        assertNull(ProgressCalculator.calculateProjectProgress(List.of()));
        assertEquals(75.0, ProgressCalculator.calculateProjectProgress(
                List.of("COMPLETED", "COMPLETED", "COMPLETED", "PENDING")
        ));
    }

    @Test
    void honestCodingStreak_countsConsecutiveDaysWithoutPenalizingUnrecordedToday() {
        LocalDate today = LocalDate.of(2026, 11, 26);

        assertEquals(0, ProgressCalculator.calculateCodingStreak(List.of(), today));
        assertEquals(3, ProgressCalculator.calculateCodingStreak(
                List.of(today, today.minusDays(1), today.minusDays(2)),
                today
        ));
        // Today unrecorded yet, yesterday + day before recorded => 2 (unrecorded today is not failure)
        assertEquals(2, ProgressCalculator.calculateCodingStreak(
                List.of(today.minusDays(1), today.minusDays(2), today.minusDays(4)),
                today
        ));
    }
}
