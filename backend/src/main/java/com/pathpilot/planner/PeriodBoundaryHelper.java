package com.pathpilot.planner;

import com.pathpilot.user.UserProfile;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Pure date-boundary utility using the user's configured time zone.
 * Week = Monday to Sunday; Month = 1st to last day of calendar month.
 */
public final class PeriodBoundaryHelper {

    private PeriodBoundaryHelper() {
    }

    public static ZoneId safeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of(UserProfile.DEFAULT_TIME_ZONE);
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception ex) {
            return ZoneId.of(UserProfile.DEFAULT_TIME_ZONE);
        }
    }

    public static LocalDate localToday(Instant nowUtc, String timeZone) {
        return nowUtc.atZone(safeZone(timeZone)).toLocalDate();
    }

    public static DateRange weekRangeContaining(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        return new DateRange(monday, sunday);
    }

    public static DateRange currentWeekRange(Instant nowUtc, String timeZone) {
        return weekRangeContaining(localToday(nowUtc, timeZone));
    }

    public static DateRange monthRange(YearMonth month) {
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }
}
