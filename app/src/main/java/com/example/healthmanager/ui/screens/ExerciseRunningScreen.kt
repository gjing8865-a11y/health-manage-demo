package com.example.healthmanager.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun ExerciseRunningScreen(
    onFinishExercise: (distanceKm: Float, durationSeconds: Int, calories: Int) -> Unit,
    onExitExercise: () -> Unit = {}
) {
    val context = LocalContext.current

    var elapsedSeconds by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    fun hasAnyLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission())
    }

    var gpsStatusText by remember { mutableStateOf("GPS 未授权") }
    var totalDistanceMeters by remember { mutableStateOf(0f) }
    var currentSpeedMps by remember { mutableStateOf<Float?>(null) }

    var lastLocation by remember { mutableStateOf<Location?>(null) }
    val trackPoints = remember { mutableStateListOf<LatLng>() }

    val mapView = remember { MapView(context) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var locationClient by remember { mutableStateOf<AMapLocationClient?>(null) }
    var hasMovedCamera by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun isSystemLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun stopLocationClient() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }

    @SuppressLint("MissingPermission")
    fun startLocationIfReady(map: AMap? = aMap) {
        if (!hasLocationPermission || map == null || locationClient != null) {
            return
        }
        if (!isSystemLocationEnabled()) {
            gpsStatusText = "请打开系统定位"
            return
        }

        gpsStatusText = "GPS 搜索中..."

        try {
            map.isMyLocationEnabled = true

            val client = AMapLocationClient(context.applicationContext)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isNeedAddress = false
                interval = 2000
                isMockEnable = false
                isOnceLocation = false
                isLocationCacheEnable = true
            }

            client.setLocationOption(option)
            client.setLocationListener(object : AMapLocationListener {
                override fun onLocationChanged(location: AMapLocation?) {
                    if (location == null) {
                        gpsStatusText = "GPS 定位失败：无结果"
                        Log.w("ExerciseLocation", "AMap returned null location")
                        return
                    }

                    if (location.errorCode != 0) {
                        gpsStatusText = "GPS 定位失败：${location.errorCode}"
                        Log.w(
                            "ExerciseLocation",
                            "AMap location failed, code=${location.errorCode}, " +
                                    "info=${location.errorInfo}, detail=${location.locationDetail}"
                        )
                        return
                    }

                    val latLng = LatLng(location.latitude, location.longitude)
                    currentSpeedMps = if (location.speed >= 0) location.speed else null
                    gpsStatusText = if (isPaused) "GPS 已定位（已暂停）" else "GPS 已定位"

                    if (!hasMovedCamera) {
                        map.moveCamera(CameraUpdateFactory.zoomTo(17f))
                        map.moveCamera(CameraUpdateFactory.changeLatLng(latLng))
                        hasMovedCamera = true
                    }

                    val currentAndroidLocation = Location("amap").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }

                    if (!isPaused) {
                        val previous = lastLocation
                        if (previous != null) {
                            val delta = previous.distanceTo(currentAndroidLocation)
                            if (delta in 1f..80f) {
                                totalDistanceMeters += delta
                                trackPoints.add(latLng)

                                map.clear()
                                map.isMyLocationEnabled = true
                                map.addPolyline(
                                    PolylineOptions()
                                        .addAll(trackPoints)
                                        .width(18f)
                                        .color(android.graphics.Color.parseColor("#FF6A00"))
                                )
                            }
                        } else {
                            trackPoints.add(latLng)
                        }

                        lastLocation = currentAndroidLocation
                    }
                }
            })

            locationClient = client
            client.startLocation()
        } catch (e: SecurityException) {
            gpsStatusText = "GPS 未授权"
            stopLocationClient()
            Log.e("ExerciseLocation", "Location permission was revoked before starting AMap.", e)
        } catch (e: Exception) {
            gpsStatusText = "GPS 启动失败"
            stopLocationClient()
            Log.e("ExerciseLocation", "Failed to start AMap location.", e)
        }
    }

    DisposableEffect(mapView) {
        mapView.onCreate(Bundle())
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onResume()
        }

        onDispose {
            stopLocationClient()
            mapView.onDestroy()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    hasLocationPermission = hasAnyLocationPermission()
                    startLocationIfReady()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        gpsStatusText = if (hasLocationPermission) "GPS 搜索中..." else "GPS 未授权"
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(hasLocationPermission, aMap) {
        if (hasLocationPermission) {
            startLocationIfReady()
        }
    }

    LaunchedEffect(isPaused) {
        while (!isPaused) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    val distanceKm = totalDistanceMeters / 1000f
    val calories = (distanceKm * 60).toInt().coerceAtLeast(if (elapsedSeconds > 0) 1 else 0)

    val paceText = if (distanceKm > 0.01f) {
        val totalMinutes = elapsedSeconds / 60f
        val minPerKm = totalMinutes / distanceKm
        val paceMin = minPerKm.toInt()
        val paceSec = ((minPerKm - paceMin) * 60).toInt()
        String.format("%02d'%02d\"", paceMin, paceSec)
    } else {
        "--"
    }

    val heartRateText = "--"
    val speedText = currentSpeedMps?.let { String.format("%.1f m/s", it) } ?: "--"

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val map = mapView.map
                    aMap = map

                    map.uiSettings.isZoomControlsEnabled = false
                    map.uiSettings.isMyLocationButtonEnabled = false

                    val myLocationStyle = MyLocationStyle().apply {
                        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                        interval(2000)
                        showMyLocation(true)
                    }
                    map.myLocationStyle = myLocationStyle
                    map.isMyLocationEnabled = hasLocationPermission

                    startLocationIfReady(map)

                    mapView
                },
                update = {
                    aMap?.isMyLocationEnabled = hasLocationPermission
                    startLocationIfReady()
                }
            )

            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                color = Color.White,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = gpsStatusText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = if (gpsStatusText.contains("已定位")) Color(0xFF16A34A) else Color(0xFF374151),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f)
                .offset(y = (-12).dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = String.format("%.2f", distanceKm),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "公里",
                        fontSize = 18.sp,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ExerciseMetricItem(label = "热量", value = "$calories", unit = "千卡")
                        ExerciseMetricItem(label = "用时", value = timeText, unit = "")
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ExerciseMetricItem(label = "配速", value = paceText, unit = "分钟/公里")
                        ExerciseMetricItem(label = "心率", value = heartRateText, unit = "次/分钟")
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "当前速度：$speedText",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallCircleAction(
                            enabled = true,
                            icon = {
                                Icon(
                                    imageVector = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF374151)
                                )
                            },
                            onClick = { isLocked = !isLocked }
                        )

                        FloatingActionButton(
                            onClick = {
                                if (!isLocked) isPaused = !isPaused
                            },
                            containerColor = Color(0xFFFF6A00),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(92.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        SmallCircleAction(
                            enabled = !isLocked,
                            icon = {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = null,
                                    tint = if (isLocked) Color.LightGray else Color(0xFF374151)
                                )
                            },
                            onClick = {
                                if (!isLocked) showSettingsDialog = true
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isLocked) {
                        Text(
                            text = "已锁定操作，点击左侧按钮解锁",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            if (!isLocked) {
                                onFinishExercise(distanceKm, elapsedSeconds, calories)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocked) Color.Gray else Color(0xFF111827)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLocked
                    ) {
                        Text("结束运动")
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("运动设置", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            isPaused = false
                            showSettingsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续运动", color = Color(0xFFFF6A00))
                    }

                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            onFinishExercise(distanceKm, elapsedSeconds, calories)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("结束并保存", color = Color(0xFF111827))
                    }

                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            onExitExercise()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("放弃本次并退出", color = Color.Red)
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ExerciseMetricItem(
    label: String,
    value: String,
    unit: String
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun SmallCircleAction(
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFF3F4F6)
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
