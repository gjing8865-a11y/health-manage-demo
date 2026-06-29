package com.example.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ExerciseCountdownScreen(
    onCountdownFinished: () -> Unit
) {
    var count by remember { mutableStateOf(3) }

    LaunchedEffect(Unit) {
        while (count > 1) {
            delay(1000)
            count--
        }
        delay(1000)
        onCountdownFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF6A00)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color(0xFFFFC199),
            fontSize = 96.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}