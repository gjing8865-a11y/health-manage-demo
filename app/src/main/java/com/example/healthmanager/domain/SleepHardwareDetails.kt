package com.example.healthmanager.domain

data class SleepHardwareDetails(
    val bedTime: String? = null,
    val wakeTime: String? = null,
    val deepSleepMinutes: Int? = null,
    val wakeCount: Int? = null
)
