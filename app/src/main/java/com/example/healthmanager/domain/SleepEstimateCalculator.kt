package com.example.healthmanager.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class SleepSignalSample(
    val timestamp: Long,
    val heartRate: Int,
    val bloodOxygen: Int,
    val steps: Int,
    val hasSteps: Boolean
)

data class SleepEstimate(
    val score: Int,
    val dataPoints: List<Float>,
    val details: SleepHardwareDetails,
    val advice: String
)

object SleepEstimateCalculator {
    fun build(samples: List<SleepSignalSample>): SleepEstimate {
        require(samples.isNotEmpty()) { "Sleep samples must not be empty" }

        val orderedSamples = samples.sortedBy { it.timestamp }
        val heartRates = orderedSamples.map { it.heartRate }
        val avgHeartRate = heartRates.average()
        val heartStability = heartRates
            .zipWithNext { previous, current -> abs(current - previous) }
            .let { changes -> if (changes.isEmpty()) 0.0 else changes.average() }
        val validOxygen = orderedSamples
            .map { it.bloodOxygen }
            .filter { it > 0 }
        val avgBloodOxygen = if (validOxygen.isEmpty()) 0.0 else validOxygen.average()
        val hasStepSignal = orderedSamples.any { it.hasSteps }
        val stepDelta = if (hasStepSignal) {
            (orderedSamples.last().steps - orderedSamples.first().steps).coerceAtLeast(0)
        } else {
            0
        }
        val observedMinutes = ((orderedSamples.last().timestamp - orderedSamples.first().timestamp) / 60_000L)
            .coerceAtLeast(1L)

        val stepScore = when {
            !hasStepSignal -> 24
            stepDelta <= 5 -> 35
            stepDelta <= 30 -> 28
            stepDelta <= 100 -> 18
            else -> 8
        }
        val heartScore = when {
            avgHeartRate <= 0 -> 14
            avgHeartRate <= 62 -> 30
            avgHeartRate <= 72 -> 24
            avgHeartRate <= 85 -> 15
            else -> 7
        }
        val stabilityScore = when {
            heartStability <= 2.0 -> 20
            heartStability <= 5.0 -> 16
            heartStability <= 9.0 -> 10
            else -> 5
        }
        val oxygenScore = when {
            avgBloodOxygen <= 0.0 -> 8
            avgBloodOxygen >= 95.0 -> 15
            avgBloodOxygen >= 90.0 -> 10
            else -> 4
        }
        val confidencePenalty = when {
            orderedSamples.size < 6 -> 10
            observedMinutes < 10 -> 6
            else -> 0
        }
        val score = (stepScore + heartScore + stabilityScore + oxygenScore - confidencePenalty)
            .coerceIn(45, 96)

        val recentSamples = orderedSamples.takeLast(24)
        val stagePoints = recentSamples.mapIndexed { index, sample ->
            val previousSteps = recentSamples.getOrNull(index - 1)?.steps ?: sample.steps
            val stepIncrease = (sample.steps - previousSteps).coerceAtLeast(0)
            when {
                hasStepSignal && stepIncrease > 10 -> 2f
                sample.heartRate <= avgHeartRate - 4 && (!hasStepSignal || stepIncrease <= 2) -> 8f
                sample.heartRate <= avgHeartRate + 5 && (!hasStepSignal || stepIncrease <= 2) -> 6f
                else -> 4f
            }
        }.withMinimumSleepPoints()

        val deepCount = stagePoints.count { it >= 7f }
        val deepSleepMinutes = if (deepCount == 0) {
            0
        } else {
            ((observedMinutes * deepCount) / stagePoints.size)
                .toInt()
                .coerceAtLeast(1)
        }
        val wakeCount = stagePoints
            .zipWithNext()
            .count { (previous, current) -> previous > 3f && current <= 3f }

        val details = SleepHardwareDetails(
            bedTime = formatShortTime(orderedSamples.first().timestamp),
            wakeTime = "监测中",
            deepSleepMinutes = deepSleepMinutes,
            wakeCount = wakeCount
        )
        val advice = when {
            !hasStepSignal -> "已根据心率和血氧自动估算睡眠；当前单片机未回传步数，连续佩戴后结果会更准。"
            score >= 85 -> "心率稳定且活动很少，睡眠恢复状态较好。"
            score >= 70 -> "睡眠状态较平稳，建议继续保持规律作息。"
            else -> "心率波动或活动偏多，睡眠可能较浅。"
        }

        return SleepEstimate(
            score = score,
            dataPoints = stagePoints,
            details = details,
            advice = advice
        )
    }

    private fun List<Float>.withMinimumSleepPoints(): List<Float> {
        if (isEmpty()) return listOf(4f, 5f, 6f, 6f, 5f, 4f, 3f)

        val points = toMutableList()
        while (points.size < 7) {
            points += points.last()
        }
        return points
    }

    private fun formatShortTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
