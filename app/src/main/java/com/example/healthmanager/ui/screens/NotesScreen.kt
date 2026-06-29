package com.example.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.MainViewModel

@Composable
fun NotesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val text by viewModel.noteText.collectAsState()
    val history by viewModel.noteHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text("便签管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { viewModel.updateNoteText(it) },
                    placeholder = { Text("输入要发送到设备的文字...", color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryTeal,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            viewModel.sendNoteToDevice(text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("同步到设备")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("历史发送内容", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (history.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无发送记录", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                items(history) { note ->
                    HistoryItem(note)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(PrimaryTeal, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = content,
                fontSize = 14.sp,
                color = TextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}