package com.pathpilot.planner;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodBoundaryHelperTest {

    @Test
    void sundayNightUtc_resolvesToNextMondayInAsiaKolkata() {
        // 2026-11-29 is Sunday. 20:00 UTC is Monday 2026-11-30 01:30 AM in Asia/Kolkata (+05:30).
        Instant sundayNightUtc = Instant.parse("2026-11-29T20:00:00Z");

        PeriodBoundaryHelper.DateRange utcWeek = PeriodBoundaryHelper.currentWeekRange(sundayNightUtc, "UTC");
        assertEquals(LocalDate.of(2026, 11, 23), utcWeek.start());
        assertEquals(LocalDate.of(2026, 11, 29), utcWeek.end());

        PeriodBoundaryHelper.DateRange istWeek = PeriodBoundaryHelper.currentWeekRange(sundayNightUtc, "Asia/Kolkata");
        assertEquals(LocalDate.of(2026, 11, 30), istWeek.start());
        assertEquals(LocalDate.of(2026, 12, 6), istWeek.end());
    }

    @Test
    void monthChangeAcrossWeek_keepsMondayToSundayContiguous() {
        // 2026-10-29 (Thursday) spans October 26 (Mon) to November 1 (Sun)
        PeriodBoundaryHelper.DateRange range = PeriodBoundaryHelper.weekRangeContaining(LocalDate.of(2026, 10, 29));
        assertEquals(LocalDate.of(2026, 10, 26), range.start());
        assertEquals(LocalDate.of(2026, 11, 1), range.end());
    }

    @Test
    void yearChangeAcrossWeek_keepsMondayToSundayContiguous() {
        // 2027-01-01 (Friday) belongs to week 2026-12-28 (Mon) to 2027-01-03 (Sun)
        PeriodBoundaryHelper.DateRange range = PeriodBoundaryHelper.weekRangeContaining(LocalDate.of(2027, 1, 1));
        assertEquals(LocalDate.of(2026, 12, 28), range.start());
        assertEquals(LocalDate.of(2027, 1, 3), range.end());
    }
}
