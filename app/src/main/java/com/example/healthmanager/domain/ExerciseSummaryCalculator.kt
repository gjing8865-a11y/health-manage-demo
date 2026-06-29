package com.example.healthmanager.domain

import com.example.healthmanager.model.ExerciseRecord

data class LatestExerciseSummary(
    val type: String,
    val distanceKm: Float,
    val durationSeconds: Int,
    val calories: Int
)

data class WeeklyExerciseSummary(
    val durationMinutes: Int,
    val distanceKm: Float,
    val count: Int
)

object ExerciseSummaryCalculator {
    fun latest(record: ExerciseRecord?, defaultType: String): LatestExerciseSummary {
        return if (record == null) {
            LatestExerciseSummary(
                type = defaultType,
                distanceKm = 0f,
                durationSeconds = 0,
                calories = 0
            )
        } else {
            LatestExerciseSummary(
                type = record.type,
                distanceKm = record.distanceKm,
                durationSeconds = record.durationSeconds,
                calories = record.calories
            )
        }
    }

    fun weekly(records: List<ExerciseRecord>): WeeklyExerciseSummary {
        return WeeklyExerciseSummary(
            durationMinutes = records.sumOf { it.durationSeconds } / 60,
            distanceKm = records.sumOf { it.distanceKm.toDouble() }.toFloat(),
            count = records.size
        )
    }
}
