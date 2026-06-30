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

data class WeatherUiState(
    val city: String = "",
    val weather: String = "",
    val weatherIcon: String = "",
    val temperature: Int = 0,
    val tempMax: Int = 0,
    val tempMin: Int = 0,
    val aqi: Int = 0,
    val aqiCategory: String = "",
    val feelsLike: Double = 0.0,
    val visibility: Double = 0.0,
    val pressure: Double = 0.0,
    val uv: Double = 0.0,
    val humidity: Int = 0,
    val windDirection: String = "",
    val windPower: String = "",
    val hourlyForecastJson: String = "[]",
    val forecastJson: String = "[]"
)

data class SleepTrendPoint(
    val label: String,
    val score: Int
)
