package com.example.healthmanager.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun DeviceScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit
) {
    var currentSubPage by remember { mutableStateOf("main") }

    BackHandler(enabled = currentSubPage != "main") {
        currentSubPage = "main"
    }

    when (currentSubPage) {
        "notes" -> {
            NotesScreen(
                viewModel = viewModel,
                onBack = { currentSubPage = "main" }
            )
        }

        "weather" -> {
            WeatherScreen(
                viewModel = viewModel,
                onBack = { currentSubPage = "main" }
            )
        }

        else -> {
            DeviceMainContent(
                viewModel = viewModel,
                onNavigateToNotes = { currentSubPage = "notes" },
                onNavigateToWeather = { currentSubPage = "weather" },
                onLogout = onLogout
            )
        }
    }
}

@Composable
fun DeviceMainContent(
    viewModel: MainViewModel,
    onNavigateToNotes: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    val isConnected by viewModel.isConnected.collectAsState()
    val isDemoDeviceMode by viewModel.isDemoDeviceMode.collectAsState()
    val isScanningWifi by viewModel.isScanningWifi.collectAsState()
    val wifiList by viewModel.wifiList.collectAsState()
    val connectedSsid by viewModel.connectedSsid.collectAsState()

    val deviceDataText by viewModel.deviceDataText.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val bloodOxygen by viewModel.bloodOxygen.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isFetchingDeviceData by viewModel.isFetchingDeviceData.collectAsState()

    val isEnabled by viewModel.isSedentaryEnabled.collectAsState()
    val interval by viewModel.sedentaryInterval.collectAsState()
    val batteryPercent = batteryLevel

    val batteryIndicatorColor = when {
        !isConnected || batteryPercent == null -> Color.Gray.copy(alpha = 0.5f)
        batteryPercent <= 20 -> Color(0xFFFF6B6B)
        batteryPercent <= 50 -> Color(0xFFFFC857)
        else -> Color(0xFF34C759)
    }
    val batteryText = if (isConnected && batteryPercent != null) " ${batteryPercent}%" else " --"

    var showSettingDialog by remember { mutableStateOf(false) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf("HRB_AP") }
    var wifiPassword by remember { mutableStateOf("12345678") }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    fun hasWifiPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        if (allGranted) {
            showWifiDialog = true
            viewModel.scanWifiHotspotsWithFallback()
        } else {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要 Wi-Fi 权限") },
            text = {
                Text("扫描附近热点需要位置权限；Android 13 及以上还需要附近 Wi-Fi 设备权限。若已授权但仍扫不到，请确认系统定位服务已开启。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        permissionLauncher.launch(requiredPermissions)
                    }
                ) {
                    Text("重新授权", color = PrimaryTeal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                ) {
                    Text("去开定位", color = TextSecondary)
                }
            }
        )
    }

    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { showWifiDialog = false },
            title = { Text("选择 STM32 热点", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("热点名称") },
                        placeholder = { Text("例如 HRB_AP / STM32SmartBand") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = { Text("热点密码（开放热点可留空）") },
                        placeholder = { Text("加密热点填密码，开放热点留空") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "你的手环会在心率页点击 Survey 后开启 HRB_AP，并通过 8080 端口主动推送心率 JSON，App 会自动监听，不需要手动输入 IP。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val ssid = wifiSsid.trim()
                            if (ssid.isNotEmpty()) {
                                val scannedAp = wifiList.firstOrNull { it.ssid == ssid }
                                val passwordForConnection = passwordForWifiNetwork(
                                    ssid = ssid,
                                    capabilities = scannedAp?.capabilities,
                                    enteredPassword = wifiPassword
                                )
                                viewModel.connectToStm32Wifi(
                                    ssid = ssid,
                                    password = passwordForConnection
                                )
                                showWifiDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = wifiSsid.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        Text("直接连接输入的热点")
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isScanningWifi && wifiList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryTeal)
                        }
                    } else {
                        val manualDeviceAp = com.example.healthmanager.viewmodel.WifiAccessPoint(
                            ssid = "HRB_AP",
                            bssid = "manual",
                            level = -45,
                            capabilities = "[WPA2-PSK-CCMP][ESS]"
                        )
                        val displayList = (listOf(manualDeviceAp) + wifiList.filterNot {
                            it.ssid.equals(manualDeviceAp.ssid, ignoreCase = true)
                        })
                        val sortedList = displayList.sortedWith(
                            compareByDescending<com.example.healthmanager.viewmodel.WifiAccessPoint> {
                                isLikelyDeviceHotspot(it.ssid)
                            }.thenByDescending { it.level }
                        )

                        if (sortedList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                "没有扫描到 HRB_AP/STM32/SmartBand 热点，可直接输入上方名称连接",
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text(
                            "如果系统扫描不到，也可以点 HRB_AP 手动连接；名称包含 HRB / STM32 / SmartBand / ESP 的会优先排在前面",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(sortedList) { ap ->
                                    WifiItemRow(
                                        ssid = ap.ssid,
                                        level = ap.level,
                                        capabilities = ap.capabilities,
                                        isRecommended = isLikelyDeviceHotspot(ap.ssid),
                                        onClick = {
                                            wifiSsid = ap.ssid
                                            val passwordForConnection = passwordForWifiNetwork(
                                                ssid = ap.ssid,
                                                capabilities = ap.capabilities,
                                                enteredPassword = wifiPassword
                                            )
                                            wifiPassword = passwordForConnection
                                            viewModel.connectToStm32Wifi(
                                                ssid = ap.ssid,
                                                password = passwordForConnection
                                            )
                                            showWifiDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (hasWifiPermissions()) {
                            viewModel.scanWifiHotspotsWithFallback()
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }
                ) {
                    Text("重新扫描", color = PrimaryTeal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWifiDialog = false }
                ) {
                    Text("关闭", color = TextSecondary)
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录", fontWeight = FontWeight.Bold) },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("退出", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
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
        Text("设备管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TextPrimary),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color.White.copy(0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Watch, null, tint = PrimaryTeal)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "STM32 SmartBand",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isConnected) PrimaryTeal else Color.Gray,
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                val connectionText = when {
                                    !isConnected -> "未连接"
                                    isDemoDeviceMode -> "Demo 模式：$connectedSsid"
                                    else -> "已连接：$connectedSsid"
                                }
                                Text(
                                    connectionText,
                                    color = if (isConnected) PrimaryTeal else Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(16.dp, 8.dp)
                                .background(batteryIndicatorColor, RoundedCornerShape(2.dp))
                        )
                        Text(batteryText, color = Color.White.copy(0.7f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "通过热点连接 STM32 设备",
                    color = Color.White.copy(0.8f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (hasWifiPermissions()) {
                                showWifiDialog = true
                                viewModel.scanWifiHotspotsWithFallback()
                            } else {
                                permissionLauncher.launch(requiredPermissions)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.1f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isScanningWifi) "扫描中..." else "扫描并连接",
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { viewModel.disconnectDevice() },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.1f),
                            disabledContainerColor = Color.White.copy(0.05f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "断开连接",
                            color = if (isConnected) Color(0xFFFF8A80) else Color.White.copy(0.3f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = { viewModel.syncLatestDeviceData() },
                    enabled = !isFetchingDeviceData
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isFetchingDeviceData) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryTeal
                            )
                        }
                        Text(
                            text = if (isFetchingDeviceData) "同步中..." else "刷新/重连数据",
                            color = PrimaryTeal
                        )
                    }
                }

                TextButton(
                    onClick = {
                        if (isDemoDeviceMode) {
                            viewModel.disconnectDevice()
                        } else {
                            viewModel.startDemoDeviceMode()
                        }
                    }
                ) {
                    Text(
                        text = if (isDemoDeviceMode) "退出 Demo 数据模式" else "无硬件体验 Demo 数据",
                        color = if (isDemoDeviceMode) Color(0xFFFF8A80) else PrimaryTeal
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeviceMetricCard(
                        title = "心率",
                        value = if (heartRate > 0) "$heartRate bpm" else "--",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceMetricCard(
                        title = "血氧",
                        value = if (bloodOxygen > 0) "$bloodOxygen %" else "--",
                        modifier = Modifier.weight(1f)
                    )
                    DeviceMetricCard(
                        title = "步数",
                        value = if (steps > 0) "$steps" else "--",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "设备返回数据",
                            color = Color.White.copy(0.75f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = deviceDataText,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Text("设备应用", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceFeatureItem(
                title = "天气同步",
                subtitle = "自动同步当地气温",
                icon = Icons.Rounded.Cloud,
                iconColor = SkyBlue,
                onClick = onNavigateToWeather
            )

            DeviceFeatureItem(
                title = "便签管理",
                subtitle = "编辑并发送备忘录",
                icon = Icons.AutoMirrored.Rounded.Notes,
                iconColor = Color(0xFFEAB308),
                onClick = onNavigateToNotes
            )

            DeviceFeatureItem(
                title = "久坐提醒",
                subtitle = if (isEnabled) "每 $interval 分钟提醒一次" else "已关闭",
                icon = Icons.Rounded.AirlineSeatReclineNormal,
                iconColor = Color(0xFFFF9800),
                onClick = { showSettingDialog = true }
            )
        }

        Text("账号操作", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color.Red.copy(0.08f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = Color.Red)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("退出登录", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("返回登录页并清除当前账号状态", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
            }
        }

        if (showSettingDialog) {
            AlertDialog(
                onDismissRequest = { showSettingDialog = false },
                title = { Text("久坐提醒设置", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("开启提醒")
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    viewModel.updateSedentaryReminder(it, interval)
                                }
                            )
                        }

                        if (isEnabled) {
                            Spacer(Modifier.height(16.dp))
                            Text("提醒间隔（分钟）: $interval")
                            Slider(
                                value = interval.toFloat(),
                                onValueChange = { newValue ->
                                    viewModel.updateSedentaryReminder(
                                        isEnabled,
                                        newValue.roundToInt()
                                    )
                                },
                                valueRange = 30f..180f,
                                steps = 4
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingDialog = false }) {
                        Text("完成", color = PrimaryTeal)
                    }
                }
            )
        }
    }
}

private fun isLikelyDeviceHotspot(ssid: String): Boolean {
    return ssid.contains("STM32", ignoreCase = true) ||
            ssid.contains("SmartBand", ignoreCase = true) ||
            ssid.contains("AI-THINKER", ignoreCase = true) ||
            ssid.contains("HRB", ignoreCase = true) ||
            ssid.contains("ESP", ignoreCase = true)
}

private fun passwordForWifiNetwork(
    ssid: String,
    capabilities: String?,
    enteredPassword: String
): String {
    return when {
        capabilities != null && isOpenWifiNetwork(capabilities) -> ""
        ssid.contains("AI-THINKER", ignoreCase = true) && enteredPassword == "12345678" -> ""
        else -> enteredPassword
    }
}

private fun isOpenWifiNetwork(capabilities: String): Boolean {
    val normalized = capabilities.uppercase()
    if (normalized.isBlank()) return true

    return listOf("WEP", "WPA", "PSK", "SAE", "EAP").none { security ->
        normalized.contains(security)
    }
}

@Composable
fun WifiItemRow(
    ssid: String,
    level: Int,
    capabilities: String,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Wifi, null, tint = PrimaryTeal)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ssid, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (isRecommended) {
                Text("推荐设备热点", fontSize = 11.sp, color = PrimaryTeal)
            }
            Text(
                "信号: $level    ${if (isOpenWifiNetwork(capabilities)) "开放网络（无需密码）" else capabilities}",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
    }
}

@Composable
fun DeviceMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White.copy(0.7f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun DeviceFeatureItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(iconColor.copy(0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
        }
    }
}
