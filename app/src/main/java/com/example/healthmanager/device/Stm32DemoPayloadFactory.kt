package com.example.healthmanager.device

import com.example.healthmanager.viewmodel.SleepHardwareDetails

object Stm32DemoPayloadFactory {
    fun build(): Stm32DevicePayload =
        build(System.currentTimeMillis())

    fun build(nowMillis: Long): Stm32DevicePayload {
        val minuteSeed = ((nowMillis / 60_000) % 60).toInt()
        val heartRate = 72 + minuteSeed % 12
        val bloodOxygen = 97 + minuteSeed % 2
        val steps = 6_400 + minuteSeed * 37
        val weeklySteps = listOf(6840, 7920, 9560, 8120, 10340, 11880, steps)

        return Stm32DevicePayload(
            heartRate = heartRate,
            bloodOxygen = bloodOxygen,
            steps = steps,
            batteryLevel = 86 - minuteSeed % 9,
            weeklySteps = weeklySteps,
            sleepPayload = HardwareSleepPayload(
                score = 86,
                dataPoints = listOf(4f, 6f, 8f, 7f, 6f, 5f, 4f),
                details = SleepHardwareDetails(
                    bedTime = "23:18",
                    wakeTime = "07:06",
                    deepSleepMinutes = 124,
                    wakeCount = 2
                )
            ),
            hasHeartRate = true,
            hasBloodOxygen = true,
            hasSteps = true
        )
    }
}
