package com.pathpilot.progress;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure calculation engine for all PathPilot progress metrics.
 * Contains zero database access so every screen and test uses identical math.
 *
 * Formulas (per PROJECT_CONTEXT.md):
 * - Task completion rate = completed tasks / planned tasks * 100
 *   (RESCHEDULED tasks are excluded from both numerator and denominator so moving a task
 *    does not count as failure or inflate completion across two days.)
 * - Coding practice activity = days with recorded coding practice / days in period * 100
 * - Project progress = completed milestones / planned milestones * 100 (null if 0 milestones)
 *
 * All rate methods return null when the denominator is 0, never 0%, so UI shows an honest empty state.
 */
public final class ProgressCalculator {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_RESCHEDULED = "RESCHEDULED";

    private ProgressCalculator() {
    }

    public static TaskSummary summarizeTasks(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return new TaskSummary(0, 0, 0, 0, 0, null);
        }

        int completed = 0;
        int pending = 0;
        int skipped = 0;
        int rescheduled = 0;

        for (String raw : statuses) {
            String status = raw == null ? STATUS_PENDING : raw.trim().toUpperCase(Locale.ROOT);
            switch (status) {
                case STATUS_COMPLETED -> completed++;
                case STATUS_SKIPPED -> skipped++;
                case STATUS_RESCHEDULED -> rescheduled++;
                default -> pending++;
            }
        }

        // Exclude RESCHEDULED from both sides of the formula.
        int planned = completed + pending + skipped;
        Double completionRate = planned == 0 ? null : roundOneDecimal((completed * 100.0) / planned);
        return new TaskSummary(planned, completed, pending, skipped, rescheduled, completionRate);
    }

    public static Double calculateTaskCompletionRate(List<String> statuses) {
        return summarizeTasks(statuses).completionRate();
    }

    public static Double calculateProjectProgress(List<String> milestoneStatuses) {
        if (milestoneStatuses == null || milestoneStatuses.isEmpty()) {
            return null;
        }
        int planned = 0;
        int completed = 0;
        for (String raw : milestoneStatuses) {
            String status = raw == null ? STATUS_PENDING : raw.trim().toUpperCase(Locale.ROOT);
            if (STATUS_RESCHEDULED.equals(status)) {
                continue;
            }
            planned++;
            if (STATUS_COMPLETED.equals(status)) {
                completed++;
            }
        }
        if (planned == 0) {
            return null;
        }
        return roundOneDecimal((completed * 100.0) / planned);
    }

    /**
     * Honest streak: counts consecutive days with a recorded coding entry ending on targetDate
     * (or yesterday if today is still unrecorded, because an unrecorded day is not a failure).
     */
    public static int calculateCodingStreak(Collection<LocalDate> recordedDates, LocalDate targetDate) {
        if (recordedDates == null || recordedDates.isEmpty() || targetDate == null) {
            return 0;
        }
        Set<LocalDate> dateSet = new HashSet<>(recordedDates);
        LocalDate cursor = dateSet.contains(targetDate) ? targetDate : targetDate.minusDays(1);
        int streak = 0;
        while (dateSet.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public static Double calculateCodingActivityRate(Collection<LocalDate> recordedDates, int daysInPeriod) {
        if (daysInPeriod <= 0) {
            return null;
        }
        int uniqueDays = recordedDates == null ? 0 : new HashSet<>(recordedDates).size();
        return roundOneDecimal((uniqueDays * 100.0) / daysInPeriod);
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record TaskSummary(
            int plannedCount,
            int completedCount,
            int pendingCount,
            int skippedCount,
            int rescheduledCount,
            Double completionRate
    ) {
    }
}
