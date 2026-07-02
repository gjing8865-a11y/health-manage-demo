package com.example.healthmanager.device

import com.example.healthmanager.domain.HealthDateFormatter

object Stm32DeviceSyncSummary {
    fun build(
        heartRate: Int,
        bloodOxygen: Int,
        steps: Int,
        batteryLevel: Int?,
        sleepScore: Int?,
        weeklySteps: List<Int>,
        updatedAt: String = HealthDateFormatter.deviceSyncTime()
    ): String {
        return buildString {
            appendLine("同步完成")
            appendLine("心率: $heartRate bpm")
            appendLine("血氧: $bloodOxygen %")
            appendLine("更新时间: $updatedAt")
            appendLine("步数: $steps")
            batteryLevel?.let { appendLine("电量: $it%") }
            if (sleepScore != null) {
                appendLine("睡眠评分: $sleepScore")
            } else {
                appendLine("睡眠数据: 已根据心率和步数自动估算")
            }
            if (weeklySteps.size == 7) {
                append("周步数: ${weeklySteps.joinToString()}")
            } else {
                append("周报数据: STM32 暂未返回完整的 7 天步数")
            }
        }.trim()
    }
}
