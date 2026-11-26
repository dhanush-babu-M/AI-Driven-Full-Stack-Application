package com.pathpilot.planner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Pure java.time calculator for UTC reminder timestamps and quiet-hour checks.
 */
public final class ReminderTimeCalculator {

    private ReminderTimeCalculator() {
    }

    /**
     * Computes UTC reminder instant = (taskDate + startTime in userTimeZone) - leadMinutes.
     * Returns null when taskDate or startTime is missing (no start time -> no reminder).
     */
    public static Instant computeReminderAtUtc(
            LocalDate taskDate,
            LocalTime startTime,
            int leadMinutes,
            String timeZone
    ) {
        if (taskDate == null || startTime == null) {
            return null;
        }
        ZoneId zone = PeriodBoundaryHelper.safeZone(timeZone);
        ZonedDateTime startZdt = ZonedDateTime.of(taskDate, startTime, zone);
        return startZdt.minusMinutes(Math.max(0, leadMinutes)).toInstant();
    }

    public static boolean isWithinQuietHours(
            Instant timestampUtc,
            String timeZone,
            LocalTime quietStart,
            LocalTime quietEnd
    ) {
        if (timestampUtc == null || quietStart == null || quietEnd == null || quietStart.equals(quietEnd)) {
            return false;
        }
        ZoneId zone = PeriodBoundaryHelper.safeZone(timeZone);
        LocalTime localTime = timestampUtc.atZone(zone).toLocalTime();

        if (quietStart.isBefore(quietEnd)) {
            return !localTime.isBefore(quietStart) && localTime.isBefore(quietEnd);
        }
        // Overnight window (e.g. 22:00 to 07:00)
        return !localTime.isBefore(quietStart) || localTime.isBefore(quietEnd);
    }
}
