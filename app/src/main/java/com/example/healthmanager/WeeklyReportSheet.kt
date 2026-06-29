package com.example.healthmanager.ui.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.healthmanager.ui.theme.PrimaryTeal
import com.example.healthmanager.ui.theme.TextPrimary
import com.example.healthmanager.ui.theme.TextSecondary
import com.example.healthmanager.viewmodel.MainViewModel

@Composable
fun WeeklyReportSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val weeklyRange by viewModel.weeklyRange.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val hasSyncedWeeklyData by viewModel.hasSyncedWeeklyData.collectAsState()
    val isFetchingDeviceData by viewModel.isFetchingDeviceData.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()

    val rawStepsData by viewModel.weeklyStepData.collectAsState()
    val rawTotalSteps by viewModel.totalSteps.collectAsState()
    val rawAvgSteps by viewModel.avgSteps.collectAsState()
    val rawActiveDays by viewModel.activeDays.collectAsState()
    val rawCalories by viewModel.totalCalories.collectAsState()

    val weeklyExerciseDurationMinutes by viewModel.weeklyExerciseDurationMinutes.collectAsState()
    val weeklyExerciseDistanceKm by viewModel.weeklyExerciseDistanceKm.collectAsState()
    val weeklyExerciseCount by viewModel.weeklyExerciseCount.collectAsState()

    val showRealData = hasSyncedWeeklyData
    val stepsData = if (showRealData) rawStepsData else emptyList()
    val totalSteps = if (showRealData) rawTotalSteps else 0
    val avgSteps = if (showRealData) rawAvgSteps else 0
    val activeDays = if (showRealData) rawActiveDays else 0
    val calories = if (showRealData) rawCalories else 0f

    val weeklyBadge = when {
        showRealData -> "已同步"
        isFetchingDeviceData -> "同步中"
        isConnected -> "已连接"
        else -> "未连接"
    }
    val weeklyStatus = when {
        showRealData -> "本周数据已经和当前账号同步，可以直接查看真实步数图表。"
        isFetchingDeviceData -> "已连接 STM32，正在同步本周步数和报告内容。"
        isConnected -> "设备已连接，等待 STM32 返回完整的本周步数。"
        else -> "连接 STM32 并同步后，这里会生成属于当前账号的本周报告。"
    }
    val weeklyHeadline = when {
        showRealData && avgSteps >= 8000 && activeDays >= 4 -> "这周状态不错，已经稳定接近目标。"
        showRealData && totalSteps > 0 -> "本周已经有同步记录，继续保持就很好。"
        isFetchingDeviceData -> "STM32 正在回传数据，周报内容马上更新。"
        isConnected -> "设备已经连接成功，等待周报数据回传。"
        else -> "连接设备后，你的本周运动画像会显示在这里。"
    }
    val weeklyKeyword = when {
        showRealData && avgSteps >= 8000 && activeDays >= 4 -> "持续\n达标"
        showRealData && totalSteps >= 30000 -> "稳步\n提升"
        showRealData && totalSteps > 0 -> "继续\n加油"
        isFetchingDeviceData -> "同步\n进行中"
        isConnected -> "已连\n待同步"
        else -> "等待\n同步"
    }
    val heroIcon = if (showRealData) Icons.AutoMirrored.Rounded.DirectionsRun else Icons.Rounded.Sync
    val heroTint = if (showRealData) PrimaryTeal.copy(alpha = 0.15f) else Color(0xFF94A3B8).copy(alpha = 0.18f)

    val avgStepsText = if (showRealData) "$avgSteps" else "--"
    val activeDaysText = if (showRealData) "$activeDays 天" else "--"
    val totalStepsText = if (showRealData) "$totalSteps" else "--"
    val distanceText = if (showRealData) String.format("%.1f", totalSteps * 0.0007f) else "--"
    val caloriesText = if (showRealData) String.format("%.1f", calories) else "--"
    val fatText = if (showRealData) String.format("%.2f", calories / 7700f) else "--"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7FF))
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text("运动健康周报", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                Icon(Icons.Rounded.History, null, Modifier.padding(end = 16.dp))
                Icon(Icons.Rounded.Share, null)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 20.dp)
        ) {
            Column(Modifier.align(Alignment.TopStart)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = weeklyRange.ifBlank { "本周范围待生成" },
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Surface(
                            color = if (showRealData) PrimaryTeal.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = weeklyBadge,
                                fontSize = 11.sp,
                                color = if (showRealData) PrimaryTeal else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("本周关键词", fontSize = 16.sp, color = TextPrimary)
                Text(
                    text = weeklyKeyword,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 54.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = weeklyStatus,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
            Icon(
                heroIcon,
                null,
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 28.dp, y = 10.dp),
                tint = heroTint
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("本周步数情况", fontSize = 14.sp, color = TextSecondary)

                Text(
                    text = weeklyHeadline,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = TextPrimary,
                    lineHeight = 28.sp
                )

                Text(
                    text = weeklyStatus,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 18.dp),
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WeeklyStatItem(
                        label = "平均步数",
                        value = avgStepsText,
                        subText = if (showRealData) "目标 8000 步" else "等待同步",
                        trendUp = if (showRealData) avgSteps >= 8000 else null
                    )
                    WeeklyStatItem(
                        label = "达标天数",
                        value = activeDaysText,
                        subText = if (showRealData) "本周活跃反馈" else "等待同步",
                        trendUp = if (showRealData) activeDays >= 4 else null
                    )
                }

                Spacer(Modifier.height(28.dp))

                if (showRealData) {
                    Text("目标：8000 步/天", fontSize = 12.sp, color = TextSecondary)
                    StepBarChartWithGoal(stepsData = stepsData, goal = 8000f)
                } else {
                    ReportPlaceholderPanel(
                        message = if (isFetchingDeviceData) {
                            "STM32 正在回传本周步数，图表同步完成后会出现在这里。"
                        } else if (isConnected) {
                            "设备已连接，等待 STM32 返回完整的 7 天步数。"
                        } else {
                            "连接 STM32 后，这里会显示当前账号对应的周步数图表。"
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = Color.LightGray.copy(alpha = 0.3f)
                )

                Text("总步数", fontSize = 14.sp, color = TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(totalStepsText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    if (showRealData) {
                        Text("步", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        LabelValueItem("约等于", distanceText, "公里")
                        LabelValueItem("共消耗", caloriesText, "千卡")
                        LabelValueItem("减少脂肪", fatText, "公斤")
                    }
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(Color(0xFFF0F2F8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (showRealData) Icons.Rounded.Landscape else Icons.Rounded.Sync,
                            null,
                            modifier = Modifier.size(70.dp),
                            tint = if (showRealData) PrimaryTeal.copy(alpha = 0.6f) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        ExerciseSuggestionCard(
            weeklyExerciseDurationMinutes = weeklyExerciseDurationMinutes,
            weeklyExerciseDistanceKm = weeklyExerciseDistanceKm,
            weeklyExerciseCount = weeklyExerciseCount
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReportPlaceholderPanel(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF7F9FC)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun StepBarChartWithGoal(stepsData: List<Int>, goal: Float) {
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(top = 12.dp)
        ) {
            val maxSteps = 12000f
            val barWidth = size.width / (stepsData.size * 2.5f)
            val spacing = barWidth * 0.8f

            val goalY = size.height - (goal / maxSteps) * size.height
            drawLine(
                color = Color(0xFFFF7043).copy(alpha = 0.6f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f))
            )

            stepsData.forEachIndexed { index, value ->
                val barHeight = (value / maxSteps) * size.height
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        if (value >= goal) listOf(PrimaryTeal, Color(0xFF80CBC4))
                        else listOf(Color(0xFFFFB74D), Color(0xFFFF7043))
                    ),
                    topLeft = Offset(index * (barWidth + spacing) + spacing, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach {
                Text(it, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun WeeklyStatItem(
    label: String,
    value: String,
    subText: String,
    trendUp: Boolean? = null
) {
    Column {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            trendUp?.let {
                Icon(
                    if (it) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    null,
                    tint = if (it) Color(0xFF4CAF50) else Color(0xFFFFA500),
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }
        }
        Text(subText, fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
    }
}

@Composable
fun LabelValueItem(label: String, value: String, unit: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (value != "--") {
                Text(
                    unit,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun ExerciseSuggestionCard(
    weeklyExerciseDurationMinutes: Int,
    weeklyExerciseDistanceKm: Float,
    weeklyExerciseCount: Int
) {
    val hasExercise = weeklyExerciseCount > 0 || weeklyExerciseDistanceKm > 0.01f || weeklyExerciseDurationMinutes > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("本周运动情况", fontSize = 14.sp, color = TextSecondary)

            Text(
                text = if (hasExercise) "继续保持" else "动起来吧",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = if (hasExercise) {
                    "这周已经有运动记录，继续坚持很棒。"
                } else {
                    "当前账号这周还没有运动记录，可以从一次短距离步行开始。"
                },
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    LabelValueItem("运动总时长", "$weeklyExerciseDurationMinutes", "分钟")
                    Text(
                        if (hasExercise) "本周累计运动表现" else "还没有运动记录",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(16.dp))

                    LabelValueItem(
                        "运动距离",
                        String.format("%.2f", weeklyExerciseDistanceKm),
                        "公里"
                    )

                    Spacer(Modifier.height(16.dp))

                    LabelValueItem("运动次数", "$weeklyExerciseCount", "次")
                }

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(if (hasExercise) Color(0xFFF0FFF7) else Color(0xFFFFF4F4)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (hasExercise) Icons.AutoMirrored.Rounded.DirectionsRun else Icons.Rounded.Fastfood,
                            null,
                            Modifier.size(70.dp),
                            tint = if (hasExercise) PrimaryTeal.copy(alpha = 0.7f) else Color(0xFFFF7043).copy(alpha = 0.7f)
                        )
                        Text(
                            if (hasExercise) "继续保持" else "多动一动",
                            fontSize = 10.sp,
                            color = if (hasExercise) PrimaryTeal else Color(0xFFFF7043)
                        )
                    }
                }
            }
        }
    }
}
