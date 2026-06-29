package com.example.healthmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.healthmanager.ui.theme.BgLight
import com.example.healthmanager.ui.theme.SurfaceWhite

// 通用卡片容器
@Composable
fun HealthCard(
    modifier: Modifier = Modifier,
    color: Color = SurfaceWhite,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// 圆形进度条（Canvas 绘制）
@Composable
fun CircularStepProgress(
    modifier: Modifier = Modifier,
    progress: Float, // 0.0 - 1.0
    color: Color,
    strokeWidth: Dp = 12.dp
) {
    Canvas(modifier = modifier) {
        // 背景圆环
        drawArc(
            color = BgLight,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx())
        )
        // 进度圆环
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}
