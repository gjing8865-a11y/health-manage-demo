package com.example.healthmanager.domain;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class HealthDateFormatterTest {
    private static final TimeZone CHINA_TIME = TimeZone.getTimeZone("Asia/Shanghai");
    private static final Locale CHINA_LOCALE = Locale.CHINA;

    @Test
    public void formatsDeviceFoodAndSleepLabelsForChinaTimezone() {
        long timestamp = millisInChinaTime(2026, 7, 2, 8, 5, 9);

        assertEquals(
                "08:05:09",
                HealthDateFormatter.INSTANCE.deviceSyncTime(timestamp, CHINA_LOCALE, CHINA_TIME)
        );
        assertEquals(
                "08:05",
                HealthDateFormatter.INSTANCE.shortTime(timestamp, CHINA_LOCALE, CHINA_TIME)
        );
        assertEquals(
                "08:05",
                HealthDateFormatter.INSTANCE.foodTime(timestamp, CHINA_LOCALE, CHINA_TIME)
        );
        assertEquals(
                "7月2日",
                HealthDateFormatter.INSTANCE.foodDate(timestamp, CHINA_LOCALE, CHINA_TIME)
        );
        assertEquals(
                "2026-07-02",
                HealthDateFormatter.INSTANCE.sleepRecordDate(timestamp, CHINA_LOCALE, CHINA_TIME)
        );
    }

    @Test
    public void honorsTimezoneWhenCalendarDateChanges() {
        long timestamp = millisInUtc(2026, 1, 1, 23, 30, 0);

        assertEquals(
                "2026-01-01",
                HealthDateFormatter.INSTANCE.sleepRecordDate(
                        timestamp,
                        Locale.US,
                        TimeZone.getTimeZone("UTC")
                )
        );
        assertEquals(
                "2026-01-02",
                HealthDateFormatter.INSTANCE.sleepRecordDate(
                        timestamp,
                        CHINA_LOCALE,
                        CHINA_TIME
                )
        );
    }

    private static long millisInChinaTime(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second
    ) {
        Calendar calendar = Calendar.getInstance(CHINA_TIME, CHINA_LOCALE);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }

    private static long millisInUtc(
            int year,
            int month,
            int day,
            int hour,
            int minute,
            int second
    ) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }
}
