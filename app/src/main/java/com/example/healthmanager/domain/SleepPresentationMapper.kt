package com.example.healthmanager.domain

import com.example.healthmanager.model.SleepRecord

data class SleepTrendPoint(
    val label: String,
    val score: Int
)

object SleepPresentationMapper {
    const val EMPTY_SYNC_ADVICE = "正在同步睡眠数据..."
    const val COLLECTING_SIGNAL_ADVICE = "正在根据心率、血氧和步数积累睡眠判断样本..."

    fun parseStagePoints(dataPoints: String): List<Float> {
        if (dataPoints.isBlank()) return emptyList()

        return dataPoints
            .split(",")
            .mapNotNull { value -> value.trim().toFloatOrNull() }
    }

    fun adviceForScore(score: Int): String {
        return when {
            score >= 90 -> "睡眠质量极佳，继续保持！"
            score >= 70 -> "睡得还不错，建议早点休息。"
            else -> "睡眠较浅，睡前试试放下手机。"
        }
    }

    fun toTrend(records: List<SleepRecord>): List<SleepTrendPoint> {
        return records
            .sortedBy { it.updatedAt }
            .map { record ->
                SleepTrendPoint(
                    label = record.date.takeLast(5).replace("-", "/"),
                    score = record.score
                )
            }
    }
}
