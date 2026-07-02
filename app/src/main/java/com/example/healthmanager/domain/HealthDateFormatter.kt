package com.example.healthmanager.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object HealthDateFormatter {
    fun deviceSyncTime(
        timestampMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = format("HH:mm:ss", timestampMillis, locale, timeZone)

    fun shortTime(
        timestampMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = format("HH:mm", timestampMillis, locale, timeZone)

    fun foodTime(
        timestampMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = shortTime(timestampMillis, locale, timeZone)

    fun foodDate(
        timestampMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.CHINA,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = format("M月d日", timestampMillis, locale, timeZone)

    fun sleepRecordDate(
        timestampMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = format("yyyy-MM-dd", timestampMillis, locale, timeZone)

    private fun format(
        pattern: String,
        timestampMillis: Long,
        locale: Locale,
        timeZone: TimeZone
    ): String {
        return SimpleDateFormat(pattern, locale)
            .apply { this.timeZone = timeZone }
            .format(Date(timestampMillis))
    }
}
