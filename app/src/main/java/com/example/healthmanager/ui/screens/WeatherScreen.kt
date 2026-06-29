package com.example.healthmanager.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.MainViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val weatherState by viewModel.weatherUiState.collectAsState()
    val isSyncing by viewModel.isWeatherSyncing.collectAsState()
    val cityByLocation by viewModel.currentLocation.collectAsState()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.getLocationAndFetchWeather(context)
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val forecastList = remember(weatherState.forecastJson) {
        parseJsonArray(weatherState.forecastJson)
    }

    val hourlyList = remember(weatherState.hourlyForecastJson) {
        parseJsonArray(weatherState.hourlyForecastJson)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                        .clickable { viewModel.getLocationAndFetchWeather(context) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (weatherState.city.isNotBlank()) weatherState.city else cityByLocation,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(onClick = { viewModel.getLocationAndFetchWeather(context) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1C2551),
                                    Color(0xFF24366D),
                                    Color(0xFF314C88)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WeatherMainIcon(weatherState.weather)

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = if (weatherState.city.isNotBlank()) weatherState.city else cityByLocation,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "${weatherState.temperature}°",
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Thin,
                            color = Color.White
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "最高 ${weatherState.tempMax}° 最低 ${weatherState.tempMin}°",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "${weatherState.aqiCategory} · ${weatherState.weather}",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (hourlyList.isNotEmpty()) {
            item {
                WeatherSectionCard(title = "逐小时天气") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        hourlyList.take(8).forEach { hourly ->
                            HourlyWeatherItem(hourly)
                        }
                    }
                }
            }
        }

        if (forecastList.isNotEmpty()) {
            item {
                WeatherSectionCard(title = "近7日天气") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        forecastList.take(7).forEach { daily ->
                            DailyForecastItem(daily)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "空气质量",
                    value = weatherState.aqiCategory,
                    sub = "AQI ${weatherState.aqi}"
                )
                InfoMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "紫外线",
                    value = if (weatherState.uv == 0.0) "弱" else weatherState.uv.roundToInt().toString(),
                    sub = "指数 ${weatherState.uv}"
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "体感温度",
                    value = "${weatherState.feelsLike.roundToInt()}°",
                    sub = "实际气温 ${weatherState.temperature}°"
                )
                InfoMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "风",
                    value = weatherState.windDirection.ifBlank { "--" },
                    sub = weatherState.windPower.ifBlank { "暂无数据" }
                )
            }
        }

        item {
            WeatherSectionCard(title = "更多信息") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WeatherDetailRow("湿度", "${weatherState.humidity}%")
                    WeatherDetailRow("能见度", "${weatherState.visibility} km")
                    WeatherDetailRow("气压", "${weatherState.pressure} hPa")
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.getLocationAndFetchWeather(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(Icons.Rounded.BluetoothConnected, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("同步至 STM32 手环", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Text(
                "提示：同步后，手环将实时显示当前城市的温度与天气状态。",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeatherSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun WeatherMainIcon(weather: String) {
    val icon = when {
        weather.contains("晴") -> Icons.Rounded.WbSunny
        weather.contains("云") -> Icons.Rounded.Cloud
        weather.contains("雨") -> Icons.Rounded.Umbrella
        weather.contains("雾") -> Icons.Rounded.Dehaze
        weather.contains("雪") -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.WbCloudy
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(72.dp)
    )
}

@Composable
private fun HourlyWeatherItem(hourly: JSONObject) {
    val timeRaw = hourly.optString("time", "--")
    val displayTime = if (timeRaw.contains("T")) {
        timeRaw.substringAfter("T").take(5)
    } else {
        timeRaw.takeLast(5)
    }

    val temperature = hourly.optInt("temperature", 0)
    val weather = hourly.optString("weather", "未知")

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BgLight),
        modifier = Modifier.width(74.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(displayTime, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            WeatherSmallIcon(weather)
            Spacer(Modifier.height(8.dp))
            Text("${temperature}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun WeatherSmallIcon(weather: String) {
    val icon = when {
        weather.contains("晴") -> Icons.Rounded.WbSunny
        weather.contains("云") -> Icons.Rounded.Cloud
        weather.contains("雨") -> Icons.Rounded.Umbrella
        weather.contains("雾") -> Icons.Rounded.Dehaze
        weather.contains("雪") -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.WbCloudy
    }

    Icon(
        icon,
        contentDescription = null,
        tint = PrimaryTeal,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun DailyForecastItem(daily: JSONObject) {
    val week = daily.optString("week", "--")
    val weatherDay = daily.optString("weather_day", "--")
    val tempMax = daily.optInt("temp_max", 0)
    val tempMin = daily.optInt("temp_min", 0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = week,
            modifier = Modifier.width(64.dp),
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )

        WeatherSmallIcon(weatherDay)

        Spacer(Modifier.width(8.dp))

        Text(
            text = weatherDay,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = TextSecondary
        )

        Text(
            text = "$tempMin° / $tempMax°",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun InfoMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(sub, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun WeatherDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun parseJsonArray(json: String): List<JSONObject> {
    return try {
        val array = JSONArray(json)
        List(array.length()) { index -> array.getJSONObject(index) }
    } catch (e: Exception) {
        emptyList()
    }
}
