package com.example.healthmanager.data.remote

import com.example.healthmanager.domain.WeatherLocationResolver
import org.json.JSONObject

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

object WeatherStateMapper {
    fun parse(
        json: JSONObject,
        displayCity: String,
        queryCity: String
    ): WeatherUiState? {
        val weather = json.optString("weather", "").trim()
            .takeIf { it.isNotBlank() && it !in INVALID_WEATHER_VALUES }
            ?: return null

        if (!json.has("temperature")) return null

        val temperature = json.optInt("temperature", 0)
        val tempMax = json.optInt("temp_max", temperature)
        val tempMin = json.optInt("temp_min", temperature)
        val aqi = json.optInt("aqi", 0)
        val aqiCategory = json.optString("aqi_category", "未知").trim().ifBlank { "未知" }
        val humidity = json.optInt("humidity", 0)
        val windDirection = json.optString("wind_direction", "").trim()
        val windPower = json.optString("wind_power", "").trim()
        val responseCity = WeatherLocationResolver.normalizeCityName(json.optString("city", queryCity))

        if (responseCity != null && WeatherLocationResolver.isProvinceLevelName(responseCity)) return null

        val looksLikeEmptyPayload = temperature == 0 &&
            tempMax == 0 &&
            tempMin == 0 &&
            aqi == 0 &&
            humidity == 0 &&
            aqiCategory in EMPTY_AQI_VALUES &&
            windDirection.isBlank() &&
            windPower.isBlank()

        if (looksLikeEmptyPayload) return null

        val forecastArray = json.optJSONArray("forecast")
        val hourlyArray = json.optJSONArray("hourly_forecast")

        return WeatherUiState(
            city = displayCity,
            weather = weather,
            weatherIcon = json.optString("weather_icon", ""),
            temperature = temperature,
            tempMax = tempMax,
            tempMin = tempMin,
            aqi = aqi,
            aqiCategory = aqiCategory,
            feelsLike = json.optDouble("feels_like", temperature.toDouble()),
            visibility = json.optDouble("visibility", 0.0),
            pressure = json.optDouble("pressure", 0.0),
            uv = json.optDouble("uv", 0.0),
            humidity = humidity,
            windDirection = windDirection,
            windPower = windPower,
            hourlyForecastJson = hourlyArray?.toString() ?: "[]",
            forecastJson = forecastArray?.toString() ?: "[]"
        )
    }

    private val INVALID_WEATHER_VALUES = setOf("未知天气", "未知", "--")
    private val EMPTY_AQI_VALUES = setOf("未知", "--")
}
