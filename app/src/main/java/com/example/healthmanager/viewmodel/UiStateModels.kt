package com.example.healthmanager.viewmodel

data class PendingFoodItem(
    val id: Long = System.nanoTime(),
    val name: String,
    val kcal: Int,
    val icon: String,
    val carbs: Int,
    val protein: Int,
    val fat: Int
)

data class DailyKcalStat(
    val date: String,
    val totalKcal: Int
)

data class SleepTrendPoint(
    val label: String,
    val score: Int
)
