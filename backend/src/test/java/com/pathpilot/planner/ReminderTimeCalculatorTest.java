package com.pathpilot.planner;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderTimeCalculatorTest {

    @Test
    void noStartTime_createsNoReminder() {
        assertNull(ReminderTimeCalculator.computeReminderAtUtc(
                LocalDate.of(2026, 11, 26),
                null,
                10,
                "Asia/Kolkata"
        ));
    }

    @Test
    void reminderCalculation_respectsTimeZoneDifference() {
        LocalDate date = LocalDate.of(2026, 11, 26);
        LocalTime start = LocalTime.of(9, 0);

        // 09:00 IST (+05:30) minus 10 min = 08:50 IST = 03:20 UTC
        Instant istReminder = ReminderTimeCalculator.computeReminderAtUtc(date, start, 10, "Asia/Kolkata");
        assertEquals(Instant.parse("2026-11-26T03:20:00Z"), istReminder);

        // 09:00 EST (-05:00 in Nov) minus 10 min = 08:50 EST = 13:50 UTC
        Instant nyReminder = ReminderTimeCalculator.computeReminderAtUtc(date, start, 10, "America/New_York");
        assertEquals(Instant.parse("2026-11-26T13:50:00Z"), nyReminder);
    }

    @Test
    void reminderCalculation_crossesMidnightCleanly() {
        // 00:05 on Nov 26 in UTC minus 15 minutes = 23:50 on Nov 25 in UTC
        Instant utcMidnightCross = ReminderTimeCalculator.computeReminderAtUtc(
                LocalDate.of(2026, 11, 26),
                LocalTime.of(0, 5),
                15,
                "UTC"
        );
        assertEquals(Instant.parse("2026-11-25T23:50:00Z"), utcMidnightCross);

        // 00:10 on Nov 26 in Asia/Kolkata (+05:30) minus 30 min = 23:40 on Nov 25 IST = 18:10 UTC Nov 25
        Instant istMidnightCross = ReminderTimeCalculator.computeReminderAtUtc(
                LocalDate.of(2026, 11, 26),
                LocalTime.of(0, 10),
                30,
                "Asia/Kolkata"
        );
        assertEquals(Instant.parse("2026-11-25T18:10:00Z"), istMidnightCross);
    }

    @Test
    void quietHours_checksOvernightAndSameDayWindows() {
        LocalTime nightStart = LocalTime.of(22, 0);
        LocalTime nightEnd = LocalTime.of(7, 0);

        // 23:30 IST = 18:00 UTC -> inside quiet hours
        assertTrue(ReminderTimeCalculator.isWithinQuietHours(
                Instant.parse("2026-11-26T18:00:00Z"),
                "Asia/Kolkata",
                nightStart,
                nightEnd
        ));

        // 08:30 IST = 03:00 UTC -> outside quiet hours
        assertFalse(ReminderTimeCalculator.isWithinQuietHours(
                Instant.parse("2026-11-26T03:00:00Z"),
                "Asia/Kolkata",
                nightStart,
                nightEnd
        ));
    }
}
