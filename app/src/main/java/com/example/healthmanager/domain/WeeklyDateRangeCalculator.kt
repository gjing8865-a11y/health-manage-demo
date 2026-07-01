package com.example.healthmanager.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class WeekTimeRange(
    val startMillis: Long,
    val endMillis: Long
)

object WeeklyDateRangeCalculator {
    fun displayRange(
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.CHINESE,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val calendar = startOfWeekCalendar(nowMillis, locale, timeZone)
        val dateFormat = SimpleDateFormat("M\u6708d\u65e5", locale).apply {
            this.timeZone = timeZone
        }
        val start = dateFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 6)

        return "$start - ${dateFormat.format(calendar.time)}"
    }

    fun currentWeekTimeRange(
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.CHINESE,
        timeZone: TimeZone = TimeZone.getDefault()
    ): WeekTimeRange {
        val calendar = startOfWeekCalendar(nowMillis, locale, timeZone)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)

        return WeekTimeRange(
            startMillis = startOfWeek,
            endMillis = calendar.timeInMillis
        )
    }

    private fun startOfWeekCalendar(
        nowMillis: Long,
        locale: Locale,
        timeZone: TimeZone
    ): Calendar {
        return Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            firstDayOfWeek = Calendar.MONDAY
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
