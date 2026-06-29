package com.example.healthmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.healthmanager.ui.components.CircularStepProgress
import com.example.healthmanager.ui.components.HealthCard
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.MainViewModel
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenWeeklyReport: () -> Unit,
    onStartExercise: () -> Unit
) {
    val steps by viewModel.steps.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val bloodOxygen by viewModel.bloodOxygen.collectAsState()
    val weeklyRange by viewModel.weeklyRange.collectAsState()

    val userName by viewModel.userName.collectAsState()
    val userSignature by viewModel.userSignature.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditSignatureDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.updateAvatar(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .padding(20.dp)
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 顶部信息区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(0.2f))
                    .clickable { launcher.launch(arrayOf("image/*")) },
                contentAlignment = Alignment.Center
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
                        modifier = Modifier.size(56.dp),
                        tint = Color.Gray
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = "你好, $userName",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.clickable { showEditNameDialog = true }
                )
                Text(
                    text = userSignature,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable { showEditSignatureDialog = true }
                )
            }

            Surface(
                color = if (isConnected) Color(0xFFE0F2F1) else Color.Gray.copy(0.1f),
                shape = CircleShape
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) PrimaryTeal else Color.Gray)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "已连接" else "未连接",
                        color = if (isConnected) PrimaryTeal else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 健康周报入口
        Card(
            colors = CardDefaults.cardColors(containerColor = TextPrimary),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clickable { onOpenWeeklyReport() }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("查看健康周报", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(weeklyRange, color = Color.White.copy(0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "看看本周运动与恢复情况",
                        color = Color.White.copy(0.55f),
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
            }
        }

        // 步数进度圆环
        HealthCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "今日活动",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularStepProgress(
                        modifier = Modifier.size(180.dp),
                        progress = (steps / 10000f).coerceIn(0f, 1f),
                        color = PrimaryTeal
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, tint = PrimaryTeal)
                        Text(
                            "$steps",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text("目标 10,000", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (steps > 0) "距离目标还差 ${10000 - steps} 步" else "还没有开始今天的活动记录",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // 今日摘要
        HealthCard {
            Text(
                "今日摘要",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "消耗",
                    value = "${(steps * 0.04f).toInt()}",
                    unit = "kcal"
                )
                SummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "活动",
                    value = "${(steps / 100).coerceAtLeast(0)}",
                    unit = "分钟"
                )
                SummaryItem(
                    modifier = Modifier.weight(1f),
                    label = "进度",
                    value = "${((steps / 10000f) * 100).toInt().coerceAtMost(100)}",
                    unit = "%"
                )
            }
        }

        // 运动入口
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFF8A3D), Color(0xFFFF6A00))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "开始运动",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "户外跑步 / 步行记录",
                                color = Color.White.copy(0.85f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "记录距离、时长与热量消耗",
                                color = Color.White.copy(0.7f),
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onStartExercise() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "立即开始",
                            color = Color(0xFFFF6A00),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 心率与血氧
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HealthCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, tint = HeartRed)
                    Spacer(Modifier.width(8.dp))
                    Text("心率", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "$heartRate bpm",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    if (heartRate > 0) "实时监测中" else "暂无同步数据",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            HealthCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = SkyBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("血氧", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "$bloodOxygen %",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    if (bloodOxygen > 0) "状态稳定" else "暂无同步数据",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showEditNameDialog) {
        EditInfoDialog(
            title = "修改昵称",
            initialValue = userName,
            onDismiss = { showEditNameDialog = false },
            onConfirm = {
                viewModel.updateUserName(it)
                showEditNameDialog = false
            }
        )
    }

    if (showEditSignatureDialog) {
        EditInfoDialog(
            title = "修改个性签名",
            initialValue = userSignature,
            onDismiss = { showEditSignatureDialog = false },
            onConfirm = {
                viewModel.updateUserSignature(it)
                showEditSignatureDialog = false
            }
        )
    }
}

@Composable
private fun SummaryItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgLight.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun EditInfoDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        }
    )
}
