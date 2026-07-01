package com.example.healthmanager.domain;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class WeeklyDateRangeCalculatorTest {
    private static final TimeZone CHINA_TIME = TimeZone.getTimeZone("Asia/Shanghai");

    @Test
    public void displayRangeUsesMondayToSundayWeekAcrossMonths() {
        long wednesday = millis(2026, Calendar.JULY, 1, 10, 30, 0, 0);

        String range = WeeklyDateRangeCalculator.INSTANCE.displayRange(
                wednesday,
                Locale.CHINESE,
                CHINA_TIME
        );

        assertEquals("6\u670829\u65e5 - 7\u67085\u65e5", range);
    }

    @Test
    public void currentWeekTimeRangeStartsMondayAndEndsSunday() {
        long wednesday = millis(2026, Calendar.JULY, 1, 10, 30, 0, 0);

        WeekTimeRange range = WeeklyDateRangeCalculator.INSTANCE.currentWeekTimeRange(
                wednesday,
                Locale.CHINESE,
                CHINA_TIME
        );

        assertEquals(millis(2026, Calendar.JUNE, 29, 0, 0, 0, 0), range.getStartMillis());
        assertEquals(millis(2026, Calendar.JULY, 5, 23, 59, 59, 999), range.getEndMillis());
    }

    @Test
    public void sundayStillBelongsToTheSameMondayStartedWeek() {
        long sunday = millis(2026, Calendar.JULY, 5, 12, 0, 0, 0);

        WeekTimeRange range = WeeklyDateRangeCalculator.INSTANCE.currentWeekTimeRange(
                sunday,
                Locale.CHINESE,
                CHINA_TIME
        );

        assertEquals(millis(2026, Calendar.JUNE, 29, 0, 0, 0, 0), range.getStartMillis());
        assertEquals(millis(2026, Calendar.JULY, 5, 23, 59, 59, 999), range.getEndMillis());
    }

    @Test
    public void mondayStartsANewWeek() {
        long monday = millis(2026, Calendar.JULY, 6, 8, 0, 0, 0);

        String range = WeeklyDateRangeCalculator.INSTANCE.displayRange(
                monday,
                Locale.CHINESE,
                CHINA_TIME
        );

        assertEquals("7\u67086\u65e5 - 7\u670812\u65e5", range);
    }

    private static long millis(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second,
            int millisecond
    ) {
        Calendar calendar = Calendar.getInstance(CHINA_TIME, Locale.CHINESE);
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, millisecond);
        return calendar.getTimeInMillis();
    }
}
