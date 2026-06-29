package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_records")
data class ExerciseRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userAccount: String,
    val type: String,              // 例如：户外跑步
    val distanceKm: Float,
    val durationSeconds: Int,
    val calories: Int,
    val createdAt: Long = System.currentTimeMillis()
)