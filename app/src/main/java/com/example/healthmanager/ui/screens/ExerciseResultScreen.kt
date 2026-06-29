package com.example.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExerciseResultScreen(
    distanceKm: Float,
    durationSeconds: Int,
    calories: Int,
    onBackToDashboard: () -> Unit
) {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    val durationText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    val paceText = if (distanceKm > 0.01f) {
        val totalMinutes = durationSeconds / 60f
        val minutesPerKm = totalMinutes / distanceKm
        val paceMin = minutesPerKm.toInt()
        val paceSec = ((minutesPerKm - paceMin) * 60).toInt()
        String.format("%02d'%02d\"", paceMin, paceSec)
    } else {
        "--"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = CircleShape,
            color = Color(0xFFE8FFF2)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier
                    .padding(20.dp)
                    .size(52.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "运动完成",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "今天也完成了一次很棒的训练",
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFF8A3D), Color(0xFFFF6A00))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = String.format("%.2f", distanceKm),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "公里",
                        fontSize = 16.sp,
                        color = Color.White.copy(0.9f)
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ResultMetric("时长", durationText)
                        ResultMetric("热量", "$calories kcal")
                        ResultMetric("配速", if (paceText == "--") "--" else "$paceText/km")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "训练总结",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "本次运动已记录完成。继续保持规律锻炼，会让你的步数、心率恢复和整体健康状态越来越好。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onBackToDashboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
        ) {
            Text("返回概览", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultMetric(
    label: String,
    value: String
) {
    Column {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(0.75f)
        )
    }
}