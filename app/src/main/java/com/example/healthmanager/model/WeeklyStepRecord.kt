package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_step_records")
data class WeeklyStepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userAccount: String,   // 属于哪个账号
    val weekRange: String,     // 例如 "3月11日 - 3月17日"

    val day1: Int,
    val day2: Int,
    val day3: Int,
    val day4: Int,
    val day5: Int,
    val day6: Int,
    val day7: Int,

    val updatedAt: Long = System.currentTimeMillis()
)