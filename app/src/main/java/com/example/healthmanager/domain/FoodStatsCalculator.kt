package com.example.healthmanager.domain

import com.example.healthmanager.model.FoodRecord

data class DailyKcalSummary(
    val date: String,
    val totalKcal: Int
)

data class FoodNutritionSummary(
    val records: List<FoodRecord>,
    val totalKcal: Int,
    val todayTotalKcal: Int,
    val todayCarbs: Int,
    val todayProtein: Int,
    val todayFat: Int,
    val dailyKcalStats: List<DailyKcalSummary>
)

object FoodStatsCalculator {
    fun build(records: List<FoodRecord>, todayDateLabel: String): FoodNutritionSummary {
        val todayFoods = records.filter { it.date == todayDateLabel }

        return FoodNutritionSummary(
            records = records,
            totalKcal = records.sumOf { it.kcal },
            todayTotalKcal = todayFoods.sumOf { it.kcal },
            todayCarbs = todayFoods.sumOf { it.carbs },
            todayProtein = todayFoods.sumOf { it.protein },
            todayFat = todayFoods.sumOf { it.fat },
            dailyKcalStats = records
                .groupBy { it.date }
                .map { (date, foods) ->
                    DailyKcalSummary(
                        date = date,
                        totalKcal = foods.sumOf { it.kcal }
                    )
                }
                .sortedBy { parseMonthDayToSortableKey(it.date) }
                .takeLast(7)
        )
    }

    fun parseMonthDayToSortableKey(date: String): Int {
        val match = Regex("""(\d+)\D+(\d+)\D*""").find(date) ?: return 0
        val month = match.groupValues[1].toIntOrNull() ?: 0
        val day = match.groupValues[2].toIntOrNull() ?: 0
        return month * 100 + day
    }
}
