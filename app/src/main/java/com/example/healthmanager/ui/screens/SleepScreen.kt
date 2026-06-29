package com.example.healthmanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthmanager.ui.components.HealthCard
import com.example.healthmanager.ui.theme.AccentOrange
import com.example.healthmanager.ui.theme.BgLight
import com.example.healthmanager.ui.theme.SleepIndigo
import com.example.healthmanager.ui.theme.TextPrimary
import com.example.healthmanager.ui.theme.TextSecondary
import com.example.healthmanager.viewmodel.MainViewModel
import kotlin.math.max

@Composable
fun SleepScreen(viewModel: MainViewModel) {
    val sleepScore by viewModel.sleepScore.collectAsState()
    val sleepData by viewModel.sleepData.collectAsState()
    val sleepAdvice by viewModel.sleepAdvice.collectAsState()
    val sleepDetails by viewModel.sleepDetails.collectAsState()
    val sleepTrend by viewModel.sleepTrend.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isFetchingDeviceData by viewModel.isFetchingDeviceData.collectAsState()

    val hasSleepData = sleepScore > 0 || sleepData.isNotEmpty() || sleepTrend.isNotEmpty()
    val scoreText = if (sleepScore > 0) "$sleepScore" else "--"
    val scoreSummary = when {
        sleepScore >= 90 -> "昨晚恢复状态很好，继续保持。"
        sleepScore >= 70 -> "睡眠质量还不错，今晚可以再早点休息。"
        hasSleepData -> "已根据心率和步数生成睡眠估算。"
        isFetchingDeviceData -> "正在收集心率和步数样本..."
        isConnected -> "设备已连接，正在等待足够的心率和步数样本。"
        else -> "连接 STM32 后，这里会根据心率和步数自动估算睡眠。"
    }
    val stageHint = when {
        isFetchingDeviceData -> "正在根据心率稳定度和步数变化生成睡眠阶段..."
        isConnected -> "继续佩戴手环，收到更多样本后会自动生成阶段图。"
        else -> "连接 STM32 并持续佩戴后，这里会展示睡眠阶段。"
    }
    val trendHint = when {
        isFetchingDeviceData -> "正在保存本次睡眠估算，后续会形成 7 天趋势。"
        isConnected -> "设备已连接，生成睡眠估算后会在这里形成趋势。"
        else -> "连接 STM32 后，这里会显示账号对应的睡眠估算趋势。"
    }
    val adviceTitle = if (hasSleepData) "睡眠建议" else "同步提示"
    val adviceText = if (hasSleepData) {
        sleepAdvice
    } else {
        when {
            isFetchingDeviceData -> "App 正在根据心率、血氧和步数实时估算睡眠，样本越连续越准确。"
            isConnected -> "现在已经连上设备，保持手环佩戴并持续接收心率数据即可自动更新。"
            else -> "先去设备页连接 STM32 热点，App 会根据心率和步数自动估算睡眠。"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("睡眠分析", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SleepIndigo),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box(Modifier.padding(24.dp)) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-20).dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            scoreText,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (sleepScore > 0) "分" else "",
                            fontSize = 20.sp,
                            color = Color.White.copy(0.9f),
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }

                    Text(
                        text = scoreSummary,
                        color = Color.White.copy(alpha = if (hasSleepData) 0.78f else 0.62f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        HealthCard {
            Text("睡眠指标", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "入睡时间",
                        value = sleepDetails.bedTime ?: "--:--"
                    )
                    SleepMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "起床时间",
                        value = sleepDetails.wakeTime ?: "--:--"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "深睡时长",
                        value = sleepDetails.deepSleepMinutes?.let(::formatMinutes) ?: "--"
                    )
                    SleepMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "清醒次数",
                        value = sleepDetails.wakeCount?.let { "$it 次" } ?: "--"
                    )
                }
            }
        }

        HealthCard {
            Text("睡眠阶段", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(24.dp))

            if (sleepData.isEmpty()) {
                PlaceholderPanel(message = stageHint)
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val count = max(sleepData.size, 1)
                    val barWidth = size.width / (count * 1.5f)
                    val spacing = barWidth / 2
                    val maxBarHeight = size.height

                    sleepData.forEachIndexed { index, value ->
                        val barColor = when {
                            value > 6 -> SleepIndigo
                            value > 3 -> SleepIndigo.copy(alpha = 0.55f)
                            else -> AccentOrange.copy(alpha = 0.65f)
                        }

                        val barHeight = (value / 10f).coerceIn(0f, 1f) * maxBarHeight

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(
                                x = index * (barWidth + spacing),
                                y = maxBarHeight - barHeight
                            ),
                            size = Size(width = barWidth, height = barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sleepDetails.bedTime ?: "开始", fontSize = 10.sp, color = TextSecondary)
                    Text("睡眠中", fontSize = 10.sp, color = TextSecondary)
                    Text(sleepDetails.wakeTime ?: "结束", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }

        HealthCard {
            Text("最近 7 天趋势", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(20.dp))

            if (sleepTrend.isEmpty()) {
                PlaceholderPanel(message = trendHint, height = 120.dp)
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val count = max(sleepTrend.size, 1)
                    val barWidth = size.width / (count * 1.6f)
                    val spacing = barWidth * 0.6f
                    val maxBarHeight = size.height

                    sleepTrend.forEachIndexed { index, point ->
                        val barHeight = (point.score / 100f).coerceIn(0f, 1f) * maxBarHeight
                        drawRoundRect(
                            color = SleepIndigo.copy(alpha = if (index == sleepTrend.lastIndex) 1f else 0.55f),
                            topLeft = Offset(
                                x = index * (barWidth + spacing),
                                y = maxBarHeight - barHeight
                            ),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    sleepTrend.forEach { point ->
                        Text(point.label, fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Rounded.Warning, null, tint = AccentOrange)
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        adviceTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9A3412),
                        fontSize = 14.sp
                    )
                    Text(
                        adviceText,
                        color = Color(0xFFC2410C),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepMetricItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgLight.copy(alpha = 0.8f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun PlaceholderPanel(
    message: String,
    height: androidx.compose.ui.unit.Dp = 150.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .background(BgLight.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remainMinutes = minutes % 60
    return when {
        hours > 0 && remainMinutes > 0 -> "${hours}h ${remainMinutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
