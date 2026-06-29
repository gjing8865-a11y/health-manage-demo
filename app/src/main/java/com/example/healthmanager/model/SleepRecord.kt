package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userAccount: String,   // 属于哪个账号
    val date: String,          // 日期，例如 2025-03-18
    val score: Int,            // 睡眠评分
    val dataPoints: String,    // 睡眠阶段数据，逗号拼接保存
    val updatedAt: Long = System.currentTimeMillis()
)