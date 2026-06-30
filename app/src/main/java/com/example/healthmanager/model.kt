package com.example.healthmanager.model

/** Legacy UI food summary used by early Compose screens. */
data class FoodItem(
    val id: Int,
    val name: String,
    val kcal: Int,
    val time: String,
    val icon: String,
    val description: String = ""
)

/** Legacy UI sleep summary used before Room-backed records were introduced. */
data class SleepSession(
    val date: String,
    val score: Int,
    val totalDuration: Int,
    val deepSleep: Int,
    val lightSleep: Int,
    val awake: Int,
    val stageTrend: List<Int>
)

/** Dashboard health snapshot. */
data class DailyHealthStat(
    val steps: Int,
    val targetSteps: Int,
    val heartRate: Int,
    val spO2: Int,
    val caloriesBurned: Int
)

/** Bluetooth device summary for device-management UI. */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isConnected: Boolean = false,
    val batteryLevel: Int = 0
)

/** User profile settings for local health calculations. */
data class UserProfile(
    val name: String,
    val age: Int,
    val height: Float,
    val weight: Float,
    val gender: String
)
