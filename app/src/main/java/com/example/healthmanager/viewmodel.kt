package com.example.healthmanager.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthmanager.BuildConfig
import com.example.healthmanager.data.repository.ExerciseRepository
import com.example.healthmanager.data.repository.FoodRepository
import com.example.healthmanager.data.repository.NoteRepository
import com.example.healthmanager.data.repository.SleepRepository
import com.example.healthmanager.data.repository.UserRepository
import com.example.healthmanager.data.repository.WeeklyStepRepository
import com.example.healthmanager.data.remote.FoodRecognitionRemoteDataSource
import com.example.healthmanager.data.remote.WeatherRemoteDataSource
import com.example.healthmanager.database.AppDatabase
import com.example.healthmanager.device.Stm32DemoPayloadFactory
import com.example.healthmanager.device.Stm32DevicePayload
import com.example.healthmanager.device.Stm32PayloadParser
import com.example.healthmanager.model.User
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import com.example.healthmanager.model.NoteRecord
import com.example.healthmanager.model.FoodRecord
import com.example.healthmanager.model.WeeklyStepRecord
import com.example.healthmanager.model.SleepRecord
import com.example.healthmanager.model.ExerciseRecord
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

private data class WeatherLocationCandidate(
    val displayCity: String,
    val queryCities: List<String>
)

private const val STM32_WIFI_CONNECT_TIMEOUT_MS = 30_000
private const val STM32_MAX_CANDIDATE_HOSTS = 4
private const val STM32_DEFAULT_HOST = "192.168.4.1"
private const val STM32_DEFAULT_ENDPOINT = "tcp://192.168.4.1:8080"
private const val STM32_DEFAULT_TCP_PORT = 8080
private const val STM32_TCP_CONNECT_TIMEOUT_MS = 2_500
private const val STM32_TCP_READ_TIMEOUT_MS = 1200
private const val STM32_TCP_FIRST_PACKET_TIMEOUT_MS = 20_000L
private const val STM32_TCP_IDLE_RECONNECT_MS = 15_000L
private const val SLEEP_SIGNAL_WINDOW_MS = 12 * 60 * 60 * 1000L
private const val SLEEP_ESTIMATE_SAVE_INTERVAL_MS = 60_000L
private const val SLEEP_ESTIMATE_MIN_SAMPLES = 3
private val stm32FallbackHosts = listOf(
    STM32_DEFAULT_HOST
)

private val municipalityWeatherNames = setOf("北京", "上海", "天津", "重庆")

private val provinceLevelWeatherNames = setOf(
    "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西",
    "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
    "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门"
)

private data class SleepSignalSample(
    val timestamp: Long,
    val heartRate: Int,
    val bloodOxygen: Int,
    val steps: Int,
    val hasSteps: Boolean
)

private data class SleepEstimate(
    val score: Int,
    val dataPoints: List<Float>,
    val details: SleepHardwareDetails,
    val advice: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val userRepository = UserRepository(database.userDao())
    private val noteRepository = NoteRepository(database.noteDao())
    private val foodRepository = FoodRepository(database.foodDao())
    private val weeklyStepRepository = WeeklyStepRepository(database.weeklyStepDao())
    private val sleepRepository = SleepRepository(database.sleepDao())
    private val exerciseRepository = ExerciseRepository(database.exerciseDao())
    private val client = OkHttpClient()
    private val foodRecognitionRemoteDataSource = FoodRecognitionRemoteDataSource(
        client = client,
        apiKey = BuildConfig.ZHIPU_API_KEY.orEmpty()
    )
    private val weatherRemoteDataSource = WeatherRemoteDataSource(
        client = client,
        apiKey = BuildConfig.WEATHER_API_KEY.orEmpty()
    )
    private val deviceClient = client.newBuilder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1800, TimeUnit.MILLISECONDS)
        .callTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val prefs =
        application.getSharedPreferences("health_manager_prefs", Context.MODE_PRIVATE)

    private val KEY_LOGGED_IN_ACCOUNT = "logged_in_account"

    // 当前登录账号
    private val _currentUserAccount = MutableStateFlow<String?>(null)
    val currentUserAccount = _currentUserAccount.asStateFlow()

    // region 1. 用户个人资料
    private val _userName = MutableStateFlow("Alex")
    val userName = _userName.asStateFlow()

    private val _userSignature = MutableStateFlow("保持活力!")
    val userSignature = _userSignature.asStateFlow()

    private val _avatarUri = MutableStateFlow<Uri?>(null)
    val avatarUri = _avatarUri.asStateFlow()

    fun updateUserName(newName: String) {
        _userName.value = newName
        val acc = requireLoggedInAccount() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.updateNickName(acc, newName)
        }
    }

    fun updateUserSignature(newSignature: String) {
        _userSignature.value = newSignature
        val acc = requireLoggedInAccount() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.updateSignature(acc, newSignature)
        }
    }

    fun updateAvatar(uri: Uri?) {
        if (uri == null) return
        _avatarUri.value = uri
        val acc = requireLoggedInAccount() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.updateAvatar(acc, uri.toString())
        }
    }

    fun registerUser(
        account: String,
        password: String,
        nickName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingUser = userRepository.getUserByAccount(account)
                if (existingUser != null) {
                    withContext(Dispatchers.Main) {
                        onError("该账号已存在")
                    }
                    return@launch
                }
                val user = User(
                    account = account,
                    password = password,
                    nickName = nickName,
                    avatarUri = "",
                    signature = "保持活力!"
                )
                userRepository.registerUser(user)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("注册失败，请稍后重试")
                }
            }
        }
    }

    fun loginUser(
        account: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = userRepository.loginAndGetUser(account, password)
                if (user == null) {
                    withContext(Dispatchers.Main) {
                        onError("账号或密码错误")
                    }
                    return@launch
                }

                _currentUserAccount.value = user.account
                prefs.edit().putString(KEY_LOGGED_IN_ACCOUNT, user.account).apply()

                loadUserProfile(user)
                loadNotesForCurrentUser(user.account)
                loadFoodRecordsForCurrentUser(user.account)
                loadWeeklyRecordForCurrentUser(user.account)
                loadSleepRecordForCurrentUser(user.account)
                loadLatestExerciseRecordForCurrentUser(user.account)
                loadWeeklyExerciseStatsForCurrentUser(user.account)


                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("登录失败，请稍后重试")
                }
            }
        }
    }

    fun tryAutoLogin() {
        val savedAccount = prefs.getString(KEY_LOGGED_IN_ACCOUNT, null) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = userRepository.getUserByAccount(savedAccount) ?: return@launch

                _currentUserAccount.value = user.account
                loadUserProfile(user)
                loadNotesForCurrentUser(user.account)
                loadFoodRecordsForCurrentUser(user.account)
                loadWeeklyRecordForCurrentUser(user.account)
                loadSleepRecordForCurrentUser(user.account)
                loadLatestExerciseRecordForCurrentUser(user.account)
                loadWeeklyExerciseStatsForCurrentUser(user.account)
            } catch (e: Exception) {
                Log.e("AUTO_LOGIN", "自动登录恢复失败: ${e.message}", e)
            }
        }
    }

    fun logout() {
        prefs.edit().remove(KEY_LOGGED_IN_ACCOUNT).apply()
        _currentUserAccount.value = null
        _userName.value = "Alex"
        _userSignature.value = "保持活力!"
        _avatarUri.value = null

        resetWeeklyReport()
        _foodList.value = emptyList()
        _totalKcal.value = 0
        _todayTotalKcal.value = 0
        _dailyKcalStats.value = emptyList()
        _todayCarbs.value = 0
        _todayProtein.value = 0
        _todayFat.value = 0

        _noteText.value = ""
        _noteHistory.value = emptyList()

        _sleepScore.value = 0
        _sleepData.value = emptyList()
        _sleepDetails.value = SleepHardwareDetails()
        _sleepTrend.value = emptyList()
        _sleepAdvice.value = "正在同步睡眠数据..."

        _latestExerciseDistance.value = 0f
        _latestExerciseDuration.value = 0
        _latestExerciseCalories.value = 0
        _latestExerciseType.value = "户外跑步"

        _weeklyExerciseDurationMinutes.value = 0
        _weeklyExerciseDistanceKm.value = 0f
        _weeklyExerciseCount.value = 0
    }

    private suspend fun loadUserProfile(user: User) {
        withContext(Dispatchers.Main) {
            _userName.value = user.nickName
            _userSignature.value = user.signature
            _avatarUri.value = if (user.avatarUri.isBlank()) null else Uri.parse(user.avatarUri)
        }
    }

    private fun requireLoggedInAccount(): String? {
        return _currentUserAccount.value
    }
    // endregion

    // region 2. 周报数据
    private val _hasSyncedWeeklyData = MutableStateFlow(false)
    val hasSyncedWeeklyData = _hasSyncedWeeklyData.asStateFlow()

    private val _weeklyStepData = MutableStateFlow(List(7) { 0 })
    val weeklyStepData = _weeklyStepData.asStateFlow()

    private val _latestExerciseDistance = MutableStateFlow(0f)
    val latestExerciseDistance = _latestExerciseDistance.asStateFlow()

    private val _latestExerciseDuration = MutableStateFlow(0)
    val latestExerciseDuration = _latestExerciseDuration.asStateFlow()

    private val _latestExerciseCalories = MutableStateFlow(0)
    val latestExerciseCalories = _latestExerciseCalories.asStateFlow()

    private val _latestExerciseType = MutableStateFlow("户外跑步")
    val latestExerciseType = _latestExerciseType.asStateFlow()

    private val _weeklyExerciseDurationMinutes = MutableStateFlow(0)
    val weeklyExerciseDurationMinutes = _weeklyExerciseDurationMinutes.asStateFlow()

    private val _weeklyExerciseDistanceKm = MutableStateFlow(0f)
    val weeklyExerciseDistanceKm = _weeklyExerciseDistanceKm.asStateFlow()

    private val _weeklyExerciseCount = MutableStateFlow(0)
    val weeklyExerciseCount = _weeklyExerciseCount.asStateFlow()

    private fun loadLatestExerciseRecordForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = exerciseRepository.getLatestExerciseRecordByUser(account)

            withContext(Dispatchers.Main) {
                if (record == null) {
                    _latestExerciseDistance.value = 0f
                    _latestExerciseDuration.value = 0
                    _latestExerciseCalories.value = 0
                    _latestExerciseType.value = "户外跑步"
                } else {
                    _latestExerciseDistance.value = record.distanceKm
                    _latestExerciseDuration.value = record.durationSeconds
                    _latestExerciseCalories.value = record.calories
                    _latestExerciseType.value = record.type
                }
            }
        }
    }

    private fun loadWeeklyExerciseStatsForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (startOfWeek, endOfWeek) = getCurrentWeekTimeRange()
            val records = exerciseRepository.getExerciseRecordsByUserInRange(
                account = account,
                startTime = startOfWeek,
                endTime = endOfWeek
            )

            val totalDurationMinutes = records.sumOf { it.durationSeconds } / 60
            val totalDistanceKm = records.sumOf { it.distanceKm.toDouble() }.toFloat()
            val totalCount = records.size

            withContext(Dispatchers.Main) {
                _weeklyExerciseDurationMinutes.value = totalDurationMinutes
                _weeklyExerciseDistanceKm.value = totalDistanceKm
                _weeklyExerciseCount.value = totalCount
            }
        }
    }

    fun saveExerciseRecord(
        type: String,
        distanceKm: Float,
        durationSeconds: Int,
        calories: Int
    ) {
        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val record = ExerciseRecord(
                userAccount = account,
                type = type,
                distanceKm = distanceKm,
                durationSeconds = durationSeconds,
                calories = calories
            )

            exerciseRepository.insertExerciseRecord(record)

            val (startOfWeek, endOfWeek) = getCurrentWeekTimeRange()
            val weeklyRecords = exerciseRepository.getExerciseRecordsByUserInRange(
                account = account,
                startTime = startOfWeek,
                endTime = endOfWeek
            )

            val totalDurationMinutes = weeklyRecords.sumOf { it.durationSeconds } / 60
            val totalDistanceKm = weeklyRecords.sumOf { it.distanceKm.toDouble() }.toFloat()
            val totalCount = weeklyRecords.size

            withContext(Dispatchers.Main) {
                _latestExerciseType.value = type
                _latestExerciseDistance.value = distanceKm
                _latestExerciseDuration.value = durationSeconds
                _latestExerciseCalories.value = calories

                _weeklyExerciseDurationMinutes.value = totalDurationMinutes
                _weeklyExerciseDistanceKm.value = totalDistanceKm
                _weeklyExerciseCount.value = totalCount
            }
        }
    }

    val totalSteps = _weeklyStepData
        .map { it.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val avgSteps = _weeklyStepData
        .map { if (it.isEmpty()) 0 else it.average().toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val activeDays = _weeklyStepData
        .map { it.count { step -> step >= 8000 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val totalCalories = totalSteps
        .map { it * 0.04f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)

    private fun loadWeeklyRecordForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = weeklyStepRepository.getLatestWeeklyRecordByUser(account)

            withContext(Dispatchers.Main) {
                if (record == null) {
                    _weeklyStepData.value = List(7) { 0 }
                    _hasSyncedWeeklyData.value = false
                } else {
                    _weeklyStepData.value = listOf(
                        record.day1,
                        record.day2,
                        record.day3,
                        record.day4,
                        record.day5,
                        record.day6,
                        record.day7
                    )
                    _hasSyncedWeeklyData.value = true
                }
            }
        }
    }

    fun updateWeeklyStepsFromDevice(data: List<Int>) {
        val account = _currentUserAccount.value ?: return

        if (!_isConnected.value) {
            resetWeeklyReport()
            return
        }

        if (data.size != 7) {
            resetWeeklyReport()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val record = WeeklyStepRecord(
                userAccount = account,
                weekRange = _weeklyRange.value,
                day1 = data[0],
                day2 = data[1],
                day3 = data[2],
                day4 = data[3],
                day5 = data[4],
                day6 = data[5],
                day7 = data[6]
            )

            weeklyStepRepository.insertWeeklyRecord(record)

            withContext(Dispatchers.Main) {
                _weeklyStepData.value = data
                _hasSyncedWeeklyData.value = true
            }
        }
    }

    fun resetWeeklyReport() {
        _weeklyStepData.value = List(7) { 0 }
        _hasSyncedWeeklyData.value = false
    }

    private fun restoreWeeklyReportForCurrentUser() {
        val account = _currentUserAccount.value
        if (account == null) {
            resetWeeklyReport()
        } else {
            loadWeeklyRecordForCurrentUser(account)
        }
    }

    fun loadMockWeeklyReport() {
        if (!_isConnected.value) {
            resetWeeklyReport()
            return
        }

        updateWeeklyStepsFromDevice(
            listOf(3200, 4500, 6100, 8300, 9200, 7600, 2100)
        )
    }

    fun syncLatestDeviceData() {
        if (_isDemoDeviceMode.value) {
            applyStm32DevicePayload(Stm32DemoPayloadFactory.build())
            return
        }

        if (!_isConnected.value && !refreshStm32WifiConnectionFromSystem()) {
            _deviceDataText.value = "请先连接 HRB_AP 热点；如果已在系统 Wi-Fi 里连上 HRB_AP，请回到 App 点刷新/重连数据"
            return
        }

        stopStm32DataListener()
        startStm32DataListener()
    }

    private fun startStm32DataListener() {
        if (!_isConnected.value && !refreshStm32WifiConnectionFromSystem()) {
            return
        }

        _isFetchingDeviceData.value = true
        _deviceDataText.value = "正在连接手环 TCP 数据通道 $STM32_DEFAULT_ENDPOINT，等待 8080 端口推送..."

        val listenerToken = ++stm32DataListenerToken
        stm32DataListenerJob = viewModelScope.launch(Dispatchers.IO) {
            val dataUrls = buildStm32DataUrls()
            try {
                while (currentCoroutineContext().isActive && _isConnected.value) {
                    try {
                        listenStm32DevicePayloads(dataUrls)
                        return@launch
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            return@launch
                        }
                        if (!currentCoroutineContext().isActive) {
                            return@launch
                        }
                        Log.w("STM32_DATA", "数据监听断开，准备重连: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            if (_isConnected.value) {
                                _deviceDataText.value = "手环数据通道暂时无新数据，正在自动重连...\n${formatStm32Error(e)}"
                            }
                        }
                        delay(900)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                Log.e("STM32_DATA", "获取设备数据失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    if (_isConnected.value) {
                        _deviceDataText.value = "已连上热点，但读取设备接口失败：${formatStm32Error(e)}\n已尝试: ${summarizeStm32Attempts(dataUrls)}"
                    }
                }
            } finally {
                if (stm32DataListenerToken == listenerToken) {
                    runCatching { stm32DataSocket?.close() }
                    stm32DataSocket = null
                    stm32DataListenerJob = null
                    withContext(Dispatchers.Main) {
                        _isFetchingDeviceData.value = false
                    }
                }
            }
        }
    }

    private fun buildStm32DataUrls(): List<String> {
        val manualEndpoint = normalizeStm32Endpoint(_stm32DataEndpoint.value)
        val linkProperties = currentNetwork
            ?.let { network -> connectivityManager.getLinkProperties(network) }

        val routeGateways = linkProperties
            ?.routes
            ?.mapNotNull { route -> route.gateway?.hostAddress }
            .orEmpty()

        val dhcpGateway = runCatching {
            val gateway = wifiManager.dhcpInfo?.gateway ?: 0
            gateway.takeIf { it != 0 }?.let(::formatIpv4Address)
        }.getOrNull()

        val hosts = (
            stm32FallbackHosts +
                routeGateways +
                listOfNotNull(dhcpGateway)
            )
            .map { it.trim() }
            .filter(::isUsableIpv4Host)
            .distinct()
            .take(STM32_MAX_CANDIDATE_HOSTS)

        val tcpEndpoints = hosts.map { host ->
            "tcp://$host:$STM32_DEFAULT_TCP_PORT"
        }

        return (listOfNotNull(manualEndpoint) + tcpEndpoints)
            .distinct()
    }

    private fun normalizeStm32Endpoint(rawEndpoint: String): String? {
        val endpoint = rawEndpoint.trim().trimEnd('/')
        if (endpoint.isBlank()) return null

        val lowerEndpoint = endpoint.lowercase(Locale.ROOT)
        val withScheme = when {
            lowerEndpoint.startsWith("tcp://") ||
                    lowerEndpoint.startsWith("http://") ||
                    lowerEndpoint.startsWith("https://") -> endpoint

            endpoint.contains("/") -> "http://$endpoint"
            else -> "tcp://$endpoint"
        }

        if (withScheme.startsWith("tcp://", ignoreCase = true)) {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else STM32_DEFAULT_TCP_PORT
            return "tcp://$host:$port"
        }

        return if (URI(withScheme).path.isNullOrBlank()) {
            "$withScheme/data"
        } else {
            withScheme
        }
    }

    private fun formatIpv4Address(address: Int): String {
        return listOf(
            address and 0xff,
            address shr 8 and 0xff,
            address shr 16 and 0xff,
            address shr 24 and 0xff
        ).joinToString(".")
    }

    private fun isUsableIpv4Host(host: String): Boolean {
        if (host == "0.0.0.0") return false

        val parts = host.split(".")
        return parts.size == 4 && parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private fun readStm32DevicePayload(urls: List<String>): Stm32DevicePayload {
        var lastError: Exception? = null
        val attemptedUrls = mutableListOf<String>()

        for (url in urls) {
            attemptedUrls += url
            try {
                if (url.startsWith("tcp://")) {
                    val uri = URI(url)
                    val host = uri.host ?: throw IllegalStateException("TCP 地址缺少 host: $url")
                    return readRawTcpStm32Payload(
                        host = host,
                        port = if (uri.port > 0) uri.port else STM32_DEFAULT_TCP_PORT
                    )
                } else {
                    return readStm32DevicePayloadFromUrl(url)
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("STM32_DATA", "读取 $url 失败: ${e.message}", e)
            }
        }

        throw IllegalStateException(
            "没有发现可返回 JSON 的设备接口，已尝试 ${attemptedUrls.take(12).joinToString()}",
            lastError
        )
    }

    private suspend fun listenStm32DevicePayloads(urls: List<String>) {
        var lastError: Exception? = null
        val attemptedUrls = mutableListOf<String>()

        for (url in urls) {
            attemptedUrls += url
            try {
                if (url.startsWith("tcp://")) {
                    val uri = URI(url)
                    val host = uri.host ?: throw IllegalStateException("TCP 地址缺少 host: $url")
                    listenRawTcpStm32Payloads(
                        host = host,
                        port = if (uri.port > 0) uri.port else STM32_DEFAULT_TCP_PORT
                    )
                    return
                } else {
                    val payload = readStm32DevicePayloadFromUrl(url)
                    withContext(Dispatchers.Main) {
                        applyStm32DevicePayload(payload)
                    }
                    return
                }
            } catch (e: Exception) {
                lastError = e
                Log.w("STM32_DATA", "监听 $url 失败: ${e.message}", e)
            }
        }

        throw IllegalStateException(
            "没有发现可返回 JSON 的设备接口，已尝试 ${attemptedUrls.take(12).joinToString()}",
            lastError
        )
    }

    private fun readStm32DevicePayloadFromUrl(url: String): Stm32DevicePayload {
        val request = Request.Builder()
            .url(url)
            .build()

        val clientForNetwork = currentNetwork?.socketFactory?.let { socketFactory ->
            deviceClient.newBuilder()
                .socketFactory(socketFactory)
                .build()
        } ?: deviceClient

        clientForNetwork.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }

            if (body.isBlank()) {
                throw IllegalStateException("设备返回空数据")
            }

            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IllegalStateException("设备接口未返回 JSON，地址: $url")
            }
            return Stm32PayloadParser.parse(json)
        }
    }

    private fun readRawTcpStm32Payload(host: String, port: Int): Stm32DevicePayload {
        val rawSocket = (currentNetwork?.socketFactory?.createSocket() as? Socket) ?: Socket()

        rawSocket.use { socket ->
            socket.connect(InetSocketAddress(host, port), STM32_TCP_CONNECT_TIMEOUT_MS)
            socket.soTimeout = STM32_TCP_READ_TIMEOUT_MS

            val input = socket.getInputStream()
            val deadline = System.currentTimeMillis() + STM32_TCP_FIRST_PACKET_TIMEOUT_MS
            var lastHeartPacket: Stm32DevicePayload? = null

            while (System.currentTimeMillis() < deadline) {
                val rawText = try {
                    readSocketText(input)
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                }

                for (jsonText in extractJsonObjects(rawText)) {
                    val payload = Stm32PayloadParser.parse(jsonText)
                    val hasValidHeartData = payload.heartRate > 0 && payload.bloodOxygen > 0
                    if (hasValidHeartData || !payload.isHeartPacket) {
                        return payload
                    }
                    lastHeartPacket = payload
                }
            }

            return lastHeartPacket ?: throw IllegalStateException("TCP $host:$port 未收到手环主动推送的 JSON 数据")
        }
    }

    private suspend fun listenRawTcpStm32Payloads(host: String, port: Int) {
        val rawSocket = (currentNetwork?.socketFactory?.createSocket() as? Socket) ?: Socket()

        stm32DataSocket = rawSocket
        rawSocket.use { socket ->
            socket.connect(InetSocketAddress(host, port), STM32_TCP_CONNECT_TIMEOUT_MS)
            socket.soTimeout = STM32_TCP_READ_TIMEOUT_MS

            val input = socket.getInputStream()
            var hasReceivedPacket = false
            val firstPacketDeadline = System.currentTimeMillis() + STM32_TCP_FIRST_PACKET_TIMEOUT_MS
            var lastPacketAt = System.currentTimeMillis()

            withContext(Dispatchers.Main) {
                _deviceDataText.value = "已连上 HRB_AP 数据端口，正在等待手环主动推送心率数据..."
            }

            while (currentCoroutineContext().isActive && _isConnected.value) {
                val rawText = try {
                    readSocketText(input)
                } catch (e: java.net.SocketTimeoutException) {
                    if (!hasReceivedPacket && System.currentTimeMillis() > firstPacketDeadline) {
                        throw IllegalStateException("20 秒内没有收到手环推送的 JSON")
                    }
                    if (hasReceivedPacket && System.currentTimeMillis() - lastPacketAt > STM32_TCP_IDLE_RECONNECT_MS) {
                        throw IllegalStateException("连续 15 秒没有收到新的心率推送")
                    }
                    continue
                }

                val jsonObjects = extractJsonObjects(rawText)
                if (jsonObjects.isEmpty()) {
                    continue
                }

                hasReceivedPacket = true
                lastPacketAt = System.currentTimeMillis()
                for (jsonText in jsonObjects) {
                    val payload = Stm32PayloadParser.parse(jsonText)
                    withContext(Dispatchers.Main) {
                        _isFetchingDeviceData.value = false
                        applyStm32DevicePayload(payload)
                    }
                }
            }
        }
    }

    private fun readSocketText(input: java.io.InputStream): String {
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        while (true) {
            val length = try {
                input.read(buffer)
            } catch (e: java.net.SocketTimeoutException) {
                if (output.size() > 0) break else throw e
            }

            if (length <= 0) break

            output.write(buffer, 0, length)
            val text = output.toString(Charsets.UTF_8.name())
            if (extractJsonObjects(text).isNotEmpty()) {
                return text
            }
        }

        if (output.size() <= 0) {
            throw IllegalStateException("TCP 未返回数据")
        }

        return output.toString(Charsets.UTF_8.name())
    }

    private fun extractJsonObject(text: String): String? {
        return extractJsonObjects(text).firstOrNull()
    }

    private fun extractJsonObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        var start = -1
        var depth = 0
        var inString = false
        var escaping = false

        text.forEachIndexed { index, char ->
            if (escaping) {
                escaping = false
                return@forEachIndexed
            }

            if (char == '\\' && inString) {
                escaping = true
                return@forEachIndexed
            }

            if (char == '"') {
                inString = !inString
                return@forEachIndexed
            }

            if (inString) return@forEachIndexed

            when (char) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }

                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            objects += text.substring(start, index + 1)
                            start = -1
                        }
                    }
                }
            }
        }

        return objects
    }

    private fun applyStm32DevicePayload(payload: Stm32DevicePayload) {
        val sleepPayload = payload.sleepPayload

        if (payload.isHeartPacket && (payload.heartRate <= 0 || payload.bloodOxygen <= 0)) {
            _deviceDataText.value = "已收到手环心率数据包，但 hr/spo2 还是 null。\n请在手环心率页点击 Survey，并把手指放到传感器上，等手环测出有效心率和血氧后再刷新。"
            return
        }

        if (payload.hasHeartRate) {
            _heartRate.value = payload.heartRate
        }
        if (payload.hasBloodOxygen) {
            _bloodOxygen.value = payload.bloodOxygen
        }
        if (payload.hasSteps) {
            _steps.value = payload.steps
        }
        payload.batteryLevel?.let { battery ->
            _batteryLevel.value = battery
        }

        if (payload.weeklySteps.size == 7) {
            updateWeeklyStepsFromDevice(payload.weeklySteps)
        }

        sleepPayload?.let {
            updateSleepDataFromHardware(
                newScore = it.score,
                newDataPoints = it.dataPoints,
                details = it.details
            )
        }

        if (sleepPayload == null) {
            updateSleepEstimateFromVitals(
                heartRate = _heartRate.value,
                bloodOxygen = _bloodOxygen.value,
                steps = _steps.value,
                hasSteps = payload.hasSteps
            )
        }

        _deviceDataText.value = buildString {
            val updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            appendLine("同步完成")
            appendLine("心率: ${_heartRate.value} bpm")
            appendLine("血氧: ${_bloodOxygen.value} %")
            appendLine("更新时间: $updatedAt")
            appendLine("步数: ${_steps.value}")
            _batteryLevel.value?.let { appendLine("电量: $it%") }
            if (sleepPayload != null) {
                appendLine("睡眠评分: ${sleepPayload.score}")
            } else {
                appendLine("睡眠数据: 已根据心率和步数自动估算")
            }
            if (payload.weeklySteps.size == 7) {
                append("周步数: ${payload.weeklySteps.joinToString()}")
            } else {
                append("周报数据: STM32 暂未返回完整的 7 天步数")
            }
        }.trim()
    }

    private fun formatStm32Error(error: Exception): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("CLEARTEXT", ignoreCase = true) -> "系统拦截了本地 HTTP，请安装新版后重试"
            message.contains("20 秒内没有收到手环推送", ignoreCase = true) -> "20 秒内没有收到手环推送的有效 JSON；请确认手环停留在心率页、已点击 Survey，并且 App 已连接 HRB_AP"
            message.contains("连续 15 秒没有收到新的心率推送", ignoreCase = true) -> "已收到过数据，但后续 15 秒没有新推送；App 正在重连数据通道"
            error is java.net.SocketTimeoutException -> "连接 ESP-01S TCP 数据端口超时，请确认单片机已启动 AT+CIPSERVER=1,8080"
            error.cause is java.net.SocketTimeoutException -> "热点连上了，但 ESP-01S 没有主动推送 JSON；请确认手环心率页正在 Survey 测量"
            message.contains("没有发现可返回 JSON", ignoreCase = true) -> message
            message.contains("ECONNREFUSED", ignoreCase = true) -> "热点连上了，但 ESP-01S 的 TCP 8080 数据端口没有打开"
            message.isNotBlank() -> message
            else -> error::class.java.simpleName
        }
    }

    private fun summarizeStm32Attempts(urls: List<String>): String {
        val visibleUrls = urls.take(10).joinToString()
        return if (urls.size > 10) {
            "$visibleUrls 等 ${urls.size} 个地址"
        } else {
            visibleUrls
        }
    }
// endregion

    // region 3. 设备连接管理（Wi-Fi / STM32）
    private val wifiManager =
        application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var currentNetwork: Network? = null
    private var currentNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingConnectionSsid: String? = null
    private var stm32DataListenerJob: Job? = null
    private var stm32DataSocket: Socket? = null
    private var stm32DataListenerToken = 0
    private val stm32SocketWriteLock = Any()

    private fun stopStm32DataListener() {
        stm32DataListenerToken++
        stm32DataListenerJob?.cancel()
        stm32DataListenerJob = null
        runCatching { stm32DataSocket?.close() }
        stm32DataSocket = null
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _isDemoDeviceMode = MutableStateFlow(false)
    val isDemoDeviceMode = _isDemoDeviceMode.asStateFlow()

    private val _isScanningWifi = MutableStateFlow(false)
    val isScanningWifi = _isScanningWifi.asStateFlow()

    private val _wifiList = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val wifiList = _wifiList.asStateFlow()

    private val _connectedSsid = MutableStateFlow("")
    val connectedSsid = _connectedSsid.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _isFetchingDeviceData = MutableStateFlow(false)
    val isFetchingDeviceData = _isFetchingDeviceData.asStateFlow()

    private val _deviceDataText = MutableStateFlow("暂无设备数据")
    val deviceDataText = _deviceDataText.asStateFlow()

    private val _stm32DataEndpoint = MutableStateFlow(STM32_DEFAULT_ENDPOINT)
    val stm32DataEndpoint = _stm32DataEndpoint.asStateFlow()

    fun updateStm32DataEndpoint(endpoint: String) {
        _stm32DataEndpoint.value = endpoint
    }

    fun startDemoDeviceMode() {
        stopStm32DataListener()
        currentNetworkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        runCatching { connectivityManager.bindProcessToNetwork(null) }

        currentNetwork = null
        currentNetworkCallback = null
        pendingConnectionSsid = null
        _isDemoDeviceMode.value = true
        _isConnected.value = true
        _isFetchingDeviceData.value = false
        _connectedSsid.value = "Demo STM32"

        applyStm32DevicePayload(Stm32DemoPayloadFactory.build())
    }

    @SuppressLint("MissingPermission")
    private fun refreshStm32WifiConnectionFromSystem(): Boolean {
        if (_isConnected.value) {
            return true
        }

        val ssid = normalizeWifiSsid(wifiManager.connectionInfo?.ssid)
        if (!isLikelyStm32HotspotName(ssid)) {
            return false
        }

        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        if (wifiNetwork != null) {
            currentNetwork = wifiNetwork
            runCatching { connectivityManager.bindProcessToNetwork(wifiNetwork) }
        }

        pendingConnectionSsid = null
        _connectedSsid.value = ssid
        _isConnected.value = true
        return true
    }

    private fun normalizeWifiSsid(rawSsid: String?): String {
        return rawSsid
            .orEmpty()
            .trim()
            .trim('"')
            .takeUnless { it.isBlank() || it == "<unknown ssid>" }
            .orEmpty()
    }

    private fun isLikelyStm32HotspotName(ssid: String): Boolean {
        return ssid.contains("HRB", ignoreCase = true) ||
                ssid.contains("STM32", ignoreCase = true) ||
                ssid.contains("SmartBand", ignoreCase = true) ||
                ssid.contains("AI-THINKER", ignoreCase = true) ||
                ssid.contains("ESP", ignoreCase = true)
    }

    fun hasRequiredWifiPermissions(context: Context): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyWifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocationGranted && nearbyWifiGranted
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getApplication<Application>()
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        return runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    private fun buildWifiAccessPoints(results: List<ScanResult>): List<WifiAccessPoint> {
        return results
            .filter { it.SSID.isNotBlank() && it.SSID != "<unknown ssid>" }
            .map {
                WifiAccessPoint(
                    ssid = it.SSID,
                    bssid = it.BSSID ?: "",
                    level = it.level,
                    capabilities = it.capabilities ?: ""
                )
            }
            .distinctBy { it.ssid }
            .sortedByDescending { it.level }
    }

    private fun updateWifiScanSummary(
        accessPoints: List<WifiAccessPoint>,
        fromCache: Boolean = false
    ) {
        _wifiList.value = accessPoints

        val possibleDeviceCount = accessPoints.count { hotspot ->
            hotspot.ssid.contains("STM32", ignoreCase = true) ||
                    hotspot.ssid.contains("SmartBand", ignoreCase = true) ||
                    hotspot.ssid.contains("AI-THINKER", ignoreCase = true) ||
                    hotspot.ssid.contains("HRB", ignoreCase = true) ||
                    hotspot.ssid.contains("ESP", ignoreCase = true)
        }

        if (_isConnected.value || _isFetchingDeviceData.value) {
            return
        }

        _deviceDataText.value = when {
            accessPoints.isEmpty() -> "没有扫描到任何热点，请打开手机 Wi-Fi 和定位服务，并确认 STM32 已开启热点模式；如果 STM32 是隐藏热点，可以手动输入热点名连接。"
            possibleDeviceCount == 0 && fromCache -> "系统限制了本次主动扫描，已展示最近一次扫描结果。若 STM32 仍不在列表中，可直接手动输入热点名连接。"
            possibleDeviceCount == 0 -> "已扫描到 ${accessPoints.size} 个热点，但没有明显的 STM32 热点，请检查 STM32 热点名称，或直接手动输入热点名连接。"
            fromCache -> "系统限制了本次主动扫描，已展示最近一次扫描结果，并发现 $possibleDeviceCount 个疑似设备热点。"
            else -> "已发现 $possibleDeviceCount 个疑似设备热点，点击列表项即可尝试连接。"
        }
    }

    @SuppressLint("MissingPermission")
    fun scanWifiHotspotsWithFallback() {
        if (!hasRequiredWifiPermissions(getApplication())) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先授予定位和附近 Wi-Fi 权限，再扫描 STM32 热点。"
            return
        }

        if (!wifiManager.isWifiEnabled) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先打开手机 Wi-Fi，再扫描 STM32 热点。"
            return
        }

        if (!isLocationServiceEnabled()) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先打开系统定位服务，Android 扫描 Wi-Fi 热点需要定位开关开启。"
            return
        }

        _isScanningWifi.value = true
        _wifiList.value = emptyList()
        _deviceDataText.value = "正在扫描附近热点，请确认 STM32 已开启 Wi-Fi 热点。"
        val cachedAccessPoints = buildWifiAccessPoints(wifiManager.scanResults.orEmpty())
        if (cachedAccessPoints.isNotEmpty()) {
            _wifiList.value = cachedAccessPoints
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val accessPoints = buildWifiAccessPoints(wifiManager.scanResults.orEmpty())
                    updateWifiScanSummary(accessPoints)
                } catch (e: Exception) {
                    Log.e("WIFI_SCAN", "扫描失败: ${e.message}", e)
                    _deviceDataText.value = "扫描失败: ${e.message}"
                } finally {
                    _isScanningWifi.value = false
                    runCatching {
                        context?.unregisterReceiver(this)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            getApplication(),
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val started = wifiManager.startScan()
        if (!started) {
            _isScanningWifi.value = false
            runCatching {
                getApplication<Application>().unregisterReceiver(receiver)
            }
            if (cachedAccessPoints.isNotEmpty()) {
                updateWifiScanSummary(cachedAccessPoints, fromCache = true)
            } else {
                _deviceDataText.value = "Wi-Fi 扫描启动失败，可能是系统限制了主动扫描。请保持 Wi-Fi 和定位开启，或直接手动输入 STM32 热点名连接。"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun scanWifiHotspots() {
        scanWifiHotspotsWithFallback()
    }

    fun connectToStm32Wifi(
        ssid: String,
        password: String
    ) {
        _isDemoDeviceMode.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToStm32WifiAndroid10Plus(ssid, password)
        } else {
            _deviceDataText.value = "当前设备系统低于 Android 10，暂不支持该连接方式"
            _isConnected.value = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToStm32WifiAndroid10Plus(
        ssid: String,
        password: String
    ) {
        try {
            val normalizedSsid = ssid.trim()
            if (normalizedSsid.isBlank()) {
                _deviceDataText.value = "热点名称不能为空"
                _isConnected.value = false
                return
            }

            if (
                currentNetworkCallback != null &&
                pendingConnectionSsid == normalizedSsid &&
                !_isConnected.value
            ) {
                _deviceDataText.value = "正在连接 $normalizedSsid，请先处理系统 Wi-Fi 弹窗，不要重复点击。"
                return
            }

            if (password.isNotBlank() && password.length !in 8..63) {
                _deviceDataText.value = "热点密码长度应为 8-63 位；如果 $normalizedSsid 是开放热点，请清空密码后连接。"
                _isConnected.value = false
                return
            }

            currentNetworkCallback?.let {
                runCatching { connectivityManager.unregisterNetworkCallback(it) }
            }
            stopStm32DataListener()
            currentNetworkCallback = null
            pendingConnectionSsid = normalizedSsid

            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(normalizedSsid)

            if (password.isNotBlank()) {
                specifierBuilder.setWpa2Passphrase(password)
            }
            val specifier = specifierBuilder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val networkTypeText = if (password.isBlank()) "开放热点" else "加密热点"
            _deviceDataText.value = "正在以${networkTypeText}方式连接 $normalizedSsid，请在系统 Wi-Fi 弹窗中选择连接。"

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    currentNetwork = network
                    pendingConnectionSsid = null
                    connectivityManager.bindProcessToNetwork(network)
                    _isConnected.value = true
                    _connectedSsid.value = normalizedSsid
                    _deviceDataText.value = "已连接到 $normalizedSsid，正在读取设备数据..."
                    syncLatestDeviceData()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    currentNetworkCallback = null
                    pendingConnectionSsid = null
                    stopStm32DataListener()

                    _isConnected.value = false
                    _isFetchingDeviceData.value = false
                    _connectedSsid.value = ""
                    _batteryLevel.value = null
                    _heartRate.value = 0
                    _bloodOxygen.value = 0
                    _steps.value = 0
                    restoreWeeklyReportForCurrentUser()
                    _deviceDataText.value = if (password.isBlank()) {
                        "连接未完成：正在按开放热点连接 $normalizedSsid。若仍提示找不到设备，请确认热点仍开启，或给该热点输入密码后重试。"
                    } else {
                        "连接未完成：正在按加密热点连接 $normalizedSsid。请确认密码正确，并在系统 Wi-Fi 弹窗里点“连接”。"
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    connectivityManager.bindProcessToNetwork(null)
                    currentNetwork = null
                    pendingConnectionSsid = null
                    stopStm32DataListener()
                    _isConnected.value = false
                    _isFetchingDeviceData.value = false
                    _connectedSsid.value = ""
                    _deviceDataText.value = "设备热点已断开"

                    _batteryLevel.value = null
                    _heartRate.value = 0
                    _bloodOxygen.value = 0
                    _steps.value = 0
                    restoreWeeklyReportForCurrentUser()
                }
            }

            currentNetworkCallback = callback
            connectivityManager.requestNetwork(request, callback, STM32_WIFI_CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e("WIFI_CONNECT", "连接 STM32 热点失败: ${e.message}", e)
            pendingConnectionSsid = null
            _deviceDataText.value = "连接异常: ${e.message}"
            _isConnected.value = false
        }
        _batteryLevel.value = null
    }

    fun disconnectDevice() {
        try {
            stopStm32DataListener()
            currentNetworkCallback?.let {
                runCatching { connectivityManager.unregisterNetworkCallback(it) }
            }
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            Log.e("WIFI_DISCONNECT", "断开连接失败: ${e.message}", e)
        }

        currentNetwork = null
        currentNetworkCallback = null
        pendingConnectionSsid = null
        _isDemoDeviceMode.value = false
        _isConnected.value = false
        _isFetchingDeviceData.value = false
        _connectedSsid.value = ""
        _deviceDataText.value = "已断开连接"

        _batteryLevel.value = null
        _heartRate.value = 0
        _bloodOxygen.value = 0
        _steps.value = 0
        restoreWeeklyReportForCurrentUser()
    }

    fun fetchStm32Data() {
        syncLatestDeviceData()
    }
// endregion

    // region 4. 健康监控数据
    private val _steps = MutableStateFlow(0)
    val steps = _steps.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate = _heartRate.asStateFlow()

    private val _bloodOxygen = MutableStateFlow(0)
    val bloodOxygen = _bloodOxygen.asStateFlow()

    private val _weeklyRange = MutableStateFlow("")
    val weeklyRange = _weeklyRange.asStateFlow()

    private var heartRateAlertStartTime = 0L
    private val _showHeartRateAlert = MutableStateFlow(false)
    val showHeartRateAlert = _showHeartRateAlert.asStateFlow()

    fun onHeartRateReceived(rate: Int, context: Context) {
        _heartRate.value = rate
        if (rate > 120) {
            if (heartRateAlertStartTime == 0L) {
                heartRateAlertStartTime = System.currentTimeMillis()
            }
            if (System.currentTimeMillis() - heartRateAlertStartTime > 600000) {
                if (!_showHeartRateAlert.value) {
                    _showHeartRateAlert.value = true
                    triggerVibration(context)
                }
            }
        } else {
            heartRateAlertStartTime = 0L
            _showHeartRateAlert.value = false
        }
    }

    private fun triggerVibration(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
        } else {
            vibrator.vibrate(1000)
        }
    }

    fun dismissHeartRateAlert() {
        _showHeartRateAlert.value = false
        heartRateAlertStartTime = System.currentTimeMillis()
    }
    // endregion

    // region 5. 饮食管理与 AI 识别
    private val _foodList = MutableStateFlow<List<FoodRecord>>(emptyList())
    val foodList = _foodList.asStateFlow()

    private val _totalKcal = MutableStateFlow(0)
    val totalKcal = _totalKcal.asStateFlow()

    private val _todayTotalKcal = MutableStateFlow(0)
    val todayTotalKcal = _todayTotalKcal.asStateFlow()

    private val _dailyKcalStats = MutableStateFlow<List<DailyKcalStat>>(emptyList())
    val dailyKcalStats = _dailyKcalStats.asStateFlow()

    private val _todayCarbs = MutableStateFlow(0)
    val todayCarbs = _todayCarbs.asStateFlow()

    private val _todayProtein = MutableStateFlow(0)
    val todayProtein = _todayProtein.asStateFlow()

    private val _todayFat = MutableStateFlow(0)
    val todayFat = _todayFat.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog = _showErrorDialog.asStateFlow()

    private val _pendingFoods = MutableStateFlow<List<PendingFoodItem>>(emptyList())
    val pendingFoods = _pendingFoods.asStateFlow()

    private val _showRecognitionConfirmDialog = MutableStateFlow(false)
    val showRecognitionConfirmDialog = _showRecognitionConfirmDialog.asStateFlow()

    private val _selectedMealType = MutableStateFlow("早餐")
    val selectedMealType = _selectedMealType.asStateFlow()

    fun selectMealType(mealType: String) {
        _selectedMealType.value = mealType
    }

    private val _isDietDeleteMode = MutableStateFlow(false)
    val isDietDeleteMode = _isDietDeleteMode.asStateFlow()

    fun enterDietDeleteMode() {
        _isDietDeleteMode.value = true
    }

    fun exitDietDeleteMode() {
        _isDietDeleteMode.value = false
    }

    private var lastAttemptBitmap: Bitmap? = null

    private var lastSavedFoodFingerprint: String? = null
    private var lastSavedFoodTimeMillis: Long = 0L



    private fun loadFoodRecordsForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = foodRepository.getFoodRecordsByUser(account)
            withContext(Dispatchers.Main) {
                refreshFoodStats(records)
            }
        }
    }

    private fun refreshFoodStats(records: List<FoodRecord>) {
        val today = SimpleDateFormat("M月d日", Locale.CHINA).format(Date())

        val todayFoods = records.filter { it.date == today }

        _foodList.value = records
        _totalKcal.value = records.sumOf { it.kcal }
        _todayTotalKcal.value = todayFoods.sumOf { it.kcal }
        _todayCarbs.value = todayFoods.sumOf { it.carbs }
        _todayProtein.value = todayFoods.sumOf { it.protein }
        _todayFat.value = todayFoods.sumOf { it.fat }

        _dailyKcalStats.value = records
            .groupBy { it.date }
            .map { (date, foods) ->
                DailyKcalStat(
                    date = date,
                    totalKcal = foods.sumOf { it.kcal }
                )
            }
            .sortedBy { parseMonthDayToSortableKey(it.date) }
            .takeLast(7)
    }

    private fun parseMonthDayToSortableKey(date: String): Int {
        val regex = Regex("""(\d+)月(\d+)日""")
        val match = regex.find(date) ?: return 0
        val month = match.groupValues[1].toIntOrNull() ?: 0
        val day = match.groupValues[2].toIntOrNull() ?: 0
        return month * 100 + day
    }

    fun analyzeFoodImage(bitmap: Bitmap) {
        lastAttemptBitmap = bitmap
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true

            if (!foodRecognitionRemoteDataSource.hasApiKey) {
                Log.e("FOOD_AI", "ZHIPU_API_KEY 为空")
                withContext(Dispatchers.Main) {
                    _showErrorDialog.value = true
                }
                _isAnalyzing.value = false
                return@launch
            }

            try {
                val scaled = prepareFoodRecognitionBitmap(bitmap)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)

                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                val imageDataUrl = "data:image/jpeg;base64,$base64"
                val initialResult = requestFoodRecognitionJson(
                    imageBase64 = base64,
                    imageDataUrl = imageDataUrl,
                    promptText = buildFoodRecognitionPrompt()
                )
                val initialSceneType = extractSceneType(initialResult)
                val initialFoods = sanitizeRecognizedFoods(parsePendingFoods(initialResult))
                if (shouldAcceptInitialRecognition(initialFoods, initialSceneType)) {
                    withContext(Dispatchers.Main) {
                        _pendingFoods.value = initialFoods
                        _showRecognitionConfirmDialog.value = true
                    }
                    return@launch
                }

                val reviewedResult = runCatching {
                    requestFoodRecognitionJson(
                        imageBase64 = base64,
                        imageDataUrl = imageDataUrl,
                        promptText = buildFoodReviewPrompt(initialResult)
                    )
                }.getOrElse { error ->
                    Log.w("FOOD_AI", "复核识别失败，回退初次结果: ${error.message}")
                    initialResult
                }
                val reviewedSceneType = extractSceneType(reviewedResult).ifBlank { initialSceneType }

                val reviewedFoods = sanitizeRecognizedFoods(parsePendingFoods(reviewedResult))
                val sanitizedFoods = if (needsFoodCoverageReview(reviewedFoods, reviewedSceneType)) {
                    val coverageFoods = runCatching {
                        sanitizeRecognizedFoods(
                            parsePendingFoods(
                                requestFoodRecognitionJson(
                                    imageBase64 = base64,
                                    imageDataUrl = imageDataUrl,
                                    promptText = buildFoodCoveragePrompt(reviewedFoods)
                                )
                            )
                        )
                    }.getOrElse { error ->
                        Log.w("FOOD_AI", "套餐覆盖复核失败，保留复核结果: ${error.message}")
                        emptyList()
                    }

                    if (shouldPreferCandidateFoods(reviewedFoods, coverageFoods)) {
                        coverageFoods
                    } else {
                        reviewedFoods
                    }
                } else {
                    reviewedFoods
                }
                val completedFoods = if (shouldRunDrinkCheck(sanitizedFoods, reviewedSceneType)) {
                    mergeMissingDrinkFoods(
                        baseFoods = sanitizedFoods,
                        imageBase64 = base64,
                        imageDataUrl = imageDataUrl
                    )
                } else {
                    sanitizedFoods
                }

                if (completedFoods.isEmpty()) {
                    throw IllegalStateException("没有识别到可保存的食物数据")
                }

                withContext(Dispatchers.Main) {
                    _pendingFoods.value = completedFoods
                    _showRecognitionConfirmDialog.value = true
                }
            } catch (e: Exception) {
                Log.e("FOOD_AI", "识别失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _showErrorDialog.value = true
                }
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun buildFoodRecognitionPrompt(): String {
        return """
            你是一名专业的餐食识别和营养估算助手。
            目标：只识别图片里真正能看到、准备食用或正在食用的食物和饮料，并给出准确、保守的中文菜名。

            === 识别决策流程（必须按顺序进行） ===
            步骤 A：先做"大类判别"，从画面整体特征确定主体属于哪一大类，不要直接跳到具体菜名。
                - 海鲜/水产：是否能看到外壳、螯钳、虾尾、鱼鳍、贝壳、整只动物形态、海鲜常见的橙红色硬壳？
                - 肉类菜肴：是否是切块/切片/丝/末的肉，且没有外壳，呈现炖、烧、炒、煎、烤的形态？
                - 主食：米饭、面条、饺子、包子、馒头、粉、面包等？
                - 蔬菜/沙拉：是否以叶菜、根茎菜、豆类为主？
                - 汤/羹/粥
                - 小吃/快餐：汉堡、薯条、炸鸡、披萨、热狗、寿司？
                - 甜点/烘焙：蛋糕、面包、甜甜圈？
                - 饮品：奶茶、果茶、咖啡、汽水、果汁？
            步骤 B：在大类内部找最匹配的具体菜名，命名要简短、自然、中文、可直接展示。
            步骤 C：若不能 100% 确定具体菜名，必须回退到更稳妥的"大类名+做法"，宁可命名保守，也不要硬猜成视觉相似的另一道菜。

            === 严格防误判规则（重点） ===
            1. 看到完整外壳、橙红/青黑色硬壳、明显的螯钳或八条腿 → 一定是螃蟹类（大闸蟹、梭子蟹、面包蟹），绝不能写成"红烧肉""卤肉""糖醋里脊"。
            2. 看到完整虾身、虾头、虾尾、红色虾壳、长触须 → 一定是虾类（白灼虾、油焖大虾、小龙虾、皮皮虾），不要写成肉类。
            3. 看到完整鱼形、鱼鳍、鱼眼、鱼尾 → 一定是鱼类（清蒸鱼、红烧鱼、烤鱼、酸菜鱼），不要写成肉块。
            4. 看到贝壳类（扇贝、生蚝、蛤蜊、青口） → 必须按贝类命名，不要混到肉类。
            5. "红烧肉"必须满足：方块状或片状的猪肉块、酱油色油亮酱汁、看不到任何外壳和螯钳腿。如果画面里能看到一丝甲壳/腿/螯，绝不能写成红烧肉。
            6. 颜色相似不代表菜相同：螃蟹外壳是橙红色，红烧肉酱汁也是红色，必须靠"是否有壳/腿/螯"来区分。
            7. 这类图片可能是家常菜、餐厅堂食、连锁餐饮、方便面、小吃、奶茶、果茶、咖啡、汽水，不只限家常菜。
            8. 先判断场景类型 scene_type，只能从以下值中选择一个：single_dish、combo_meal、mixed_bowl、packaged_food、drink_only。
            9. 如果是一碗或一盘里混合了很多食材、都裹着酱汁或辣油，优先归为 mixed_bowl，并把它识别成"整道菜"，不要拆成一堆配料。
            10. 如果是托盘套餐、桌餐、多盘小吃场景，归为 combo_meal，并按独立容器拆分：杯、碗、盘、纸盒、篮子、锅都算独立食物载体。
            11. 看到杯装饮品、奶茶杯、咖啡杯、带吸管饮料，必须把饮品单独作为一个 item 返回。
            12. 看到韩式辣拌面、火鸡面、方便面样式的干拌面时，优先返回"火鸡面"或"韩式拌面"，不要误写成"意面"。
            13. 对于辣油重、食材混在一起的大碗混合菜，如果无法 100% 确定地域菜名，优先返回保守但贴近的整道菜名称，例如"辣炒米粉""新疆炒米粉""麻辣拌""冒菜""麻辣香锅"，不要拆成豆腐、面条、鸡蛋等零散项。
            14. 看到热狗、香肠、玉米热狗、芝士热狗时，不要误判成烤鱼或别的无关菜。
            15. 如果无法确定非常具体的品牌或口味，可以返回更稳妥的名称，例如"奶茶""杯装饮料""寿司卷""牛肉饭""韩式拌面""热狗""清蒸蟹""清蒸鱼"。
            16. 严格只返回 JSON，不要解释，不要代码块，不要 Markdown。

            === 常见水产/海鲜命名参考 ===
            - 大闸蟹（青背、白肚、绑绳、个头较小，多为清蒸）
            - 梭子蟹 / 面包蟹（壳形较扁或厚实，可能红烧、葱油、蒜蓉）
            - 清蒸鱼 / 红烧鱼 / 烤鱼 / 酸菜鱼
            - 白灼虾 / 油焖大虾 / 蒜蓉虾 / 小龙虾 / 麻辣小龙虾 / 皮皮虾
            - 蒜蓉粉丝扇贝 / 烤生蚝 / 辣炒蛤蜊 / 青口贝

            返回格式：
            {
              "scene_type": "single_dish",
              "foods": [
                {
                  "name": "食物名称",
                  "kcal": 180,
                  "icon": "🍱",
                  "carbs": 25,
                  "protein": 4,
                  "fat": 7
                }
              ]
            }

            要求：
            1. foods 必须是数组
            2. 每个 food 只包含 name、kcal、icon、carbs、protein、fat
            3. kcal、carbs、protein、fat 都必须是单个整数
            4. name 必须是简短、自然、可直接展示给用户的中文食物名
            5. 绝不要返回与画面明显不相干的食物
            6. 一旦看到外壳/螯/虾尾/鱼鳍等水产特征，绝不能输出任何"肉""红烧肉""卤肉""扣肉""里脊"等纯肉类菜名
        """.trimIndent()
    }

    private fun buildFoodReviewPrompt(initialResult: JSONObject): String {
        return """
            你正在复核同一张餐食图片的识别结果，请重新对照原图检查是否漏识别、错分类或命名不自然。

            初次识别结果：
            ${initialResult.toString()}

            === 复核必须先做的视觉检查 ===
            A. 画面里是否能看到外壳、螯钳、八条腿、虾尾、虾头、鱼鳍、鱼眼、贝壳？
               - 如果能看到，主体一定是水产/海鲜（蟹、虾、鱼、贝），绝不允许写成"红烧肉""卤肉""扣肉""里脊"等纯肉类菜名。
            B. 大闸蟹的典型特征：青壳白肚、个头偏小、常用绳绑住、清蒸为主，整只摆盘。看到这些特征必须命名为"大闸蟹"或"清蒸大闸蟹"。
            C. 红烧肉的必要条件：方块/片状猪肉、酱油色油亮酱汁、不含任何外壳/螯/腿。颜色红亮但有外壳的菜，不是红烧肉。

            复核规则：
            1. 先确认 scene_type 是否正确：single_dish、combo_meal、mixed_bowl、packaged_food、drink_only 只能选一个。
            2. 如果初次结果把"带壳水产"误判成了"红烧肉/卤肉/肉块"等，必须改正为对应的蟹/虾/鱼/贝类菜名。
            3. 如果图片里能看到多盘主食和饮料，要把每一项都返回。
            4. 重新检查每个独立容器：杯、碗、盘、纸盒、篮子、锅，不能漏掉明显存在的那一份。
            5. 如果是一整碗混合菜、重酱汁拌匀、很多食材混在一起，则优先归为 mixed_bowl，并识别成整道菜，不要强行拆成配料。
            6. 带吸管或杯盖的饮品必须单独返回一个 item，例如奶茶、果茶、杯装饮料、咖啡。
            7. 韩式辣拌面、火鸡面、方便面式干拌面不要误判成意面。
            8. 热狗、香肠、玉米热狗不要误判成烤鱼。
            9. 如果不能百分百确认品牌或口味，优先返回合理泛称，例如"奶茶""火鸡面""韩式拌面""杯装饮料""热狗""辣炒米粉""清蒸蟹""清蒸鱼"。
            10. 删除任何与图片明显不相干的食物。
            11. 只返回最终 JSON，不要解释。

            返回格式仍然是：
            {
              "scene_type": "single_dish",
              "foods": [
                {
                  "name": "食物名称",
                  "kcal": 180,
                  "icon": "🍱",
                  "carbs": 25,
                  "protein": 4,
                  "fat": 7
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildFoodCoveragePrompt(currentFoods: List<PendingFoodItem>): String {
        return """
            你正在做最后一次漏项检查，请只关注“有没有漏掉独立的一份食物或饮料”。

            当前结果：
            ${currentFoods.joinToString(prefix = "[", postfix = "]") { it.name }}

            检查规则：
            1. 按独立容器逐个检查：杯、碗、盘、纸盒、篮子、锅都算一个独立容器。
            2. 每个独立容器至少对应一个 item；如果同一个容器里有两种明显不同的小吃，也可以拆成两项。
            3. 带杯盖或吸管的饮品绝不能漏掉。
            4. 长条面包/香肠/玉米粒/酱料组合优先识别为热狗、芝士热狗、玉米热狗，不要写成烤鱼。
            5. 面条配酱汁、芝士片、煎蛋时优先识别为拌面、火鸡面、炒面、芝士拌面，不要写成意面。
            6. 炸鸡块、鸡翅、鸡柳、芝士炸鸡要按炸鸡类返回，不要写成无关菜名。
            7. 只返回最终 JSON，不要解释。

            返回格式：
            {
              "foods": [
                {
                  "name": "食物名称",
                  "kcal": 180,
                  "icon": "🍱",
                  "carbs": 25,
                  "protein": 4,
                  "fat": 7
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildDrinkOnlyPrompt(currentFoods: List<PendingFoodItem>): String {
        return """
            你现在只做一件事：检查图片里是否还有漏掉的饮料。

            当前已经识别到的食物：
            ${currentFoods.joinToString(prefix = "[", postfix = "]") { it.name }}

            规则：
            1. 只关注饮料，不要重复返回已经识别出的主食、小吃、酱料。
            2. 如果图片里能明显看到杯子、杯盖、吸管、透明杯、纸杯、瓶装饮品，请返回一个饮料 item。
            3. 如果无法确认具体口味，可以返回“饮料”“果茶”“奶茶”“柠檬茶”“汽水”等更稳妥名称。
            4. 如果图片里没有清晰可见的饮料，返回 {"foods": []}。
            5. 严格只返回 JSON。

            返回格式：
            {
              "foods": [
                {
                  "name": "饮料",
                  "kcal": 120,
                  "icon": "🥤",
                  "carbs": 30,
                  "protein": 0,
                  "fat": 0
                }
              ]
            }
        """.trimIndent()
    }

    private fun extractSceneType(result: JSONObject): String {
        val rawType = result.optString("scene_type")
            .ifBlank { result.optString("sceneType") }
            .trim()
            .lowercase(Locale.getDefault())

        return when {
            rawType.contains("combo") -> "combo_meal"
            rawType.contains("mixed") || rawType.contains("bowl") -> "mixed_bowl"
            rawType.contains("package") -> "packaged_food"
            rawType.contains("drink") -> "drink_only"
            rawType.contains("single") -> "single_dish"
            else -> ""
        }
    }

    private fun requestFoodRecognitionJson(
        imageBase64: String,
        imageDataUrl: String,
        promptText: String
    ): JSONObject {
        return foodRecognitionRemoteDataSource.recognizeFoodJson(
            imageBase64 = imageBase64,
            imageDataUrl = imageDataUrl,
            promptText = promptText
        )
    }

    private fun parsePendingFoods(result: JSONObject): List<PendingFoodItem> {
        val foodsToSave = mutableListOf<PendingFoodItem>()
        val foodsArray = result.optJSONArray("foods")

        if (foodsArray != null && foodsArray.length() > 0) {
            for (i in 0 until foodsArray.length()) {
                val item = foodsArray.getJSONObject(i)
                foodsToSave.add(
                    PendingFoodItem(
                        name = item.optString("name", "未知食物"),
                        kcal = parseIntValue(item.opt("kcal")),
                        icon = item.optString("icon", "🍽️"),
                        carbs = parseIntValue(item.opt("carbs")),
                        protein = parseIntValue(item.opt("protein")),
                        fat = parseIntValue(item.opt("fat"))
                    )
                )
            }
        } else {
            val name = result.optString("name", "")
            if (name.isNotBlank()) {
                foodsToSave.add(
                    PendingFoodItem(
                        name = name,
                        kcal = parseIntValue(result.opt("kcal")),
                        icon = result.optString("icon", "🍽️"),
                        carbs = parseIntValue(result.opt("carbs")),
                        protein = parseIntValue(result.opt("protein")),
                        fat = parseIntValue(result.opt("fat"))
                    )
                )
            }
        }

        return foodsToSave
    }

    private fun mergeMissingDrinkFoods(
        baseFoods: List<PendingFoodItem>,
        imageBase64: String,
        imageDataUrl: String
    ): List<PendingFoodItem> {
        val drinkFoods = runCatching {
            sanitizeRecognizedFoods(
                parsePendingFoods(
                    requestFoodRecognitionJson(
                        imageBase64 = imageBase64,
                        imageDataUrl = imageDataUrl,
                        promptText = buildDrinkOnlyPrompt(baseFoods)
                    )
                )
            ).filter { isDrinkFoodName(it.name) }
        }.getOrElse { error ->
            Log.w("FOOD_AI", "饮料补漏失败: ${error.message}")
            emptyList()
        }

        if (drinkFoods.isEmpty()) return baseFoods

        return (baseFoods + drinkFoods)
            .distinctBy { it.name.lowercase(Locale.getDefault()) }
            .take(6)
    }

    private fun prepareFoodRecognitionBitmap(bitmap: Bitmap, maxEdge: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longestEdge = maxOf(width, height)

        if (longestEdge <= maxEdge) return bitmap

        val scale = maxEdge.toFloat() / longestEdge.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun normalizeRecognizedFoodName(rawName: String): String {
        val cleaned = rawName
            .trim()
            .replace(Regex("""^[\d一二三四五六七八九十]+[.、:：)\]]\s*"""), "")
            .replace(Regex("""^(食物|菜品|项目)\s*\d+\s*[:：-]?\s*"""), "")
            .replace(Regex("""^(一份|一盘|一碗|一杯|一盒)\s*"""), "")
            .replace(Regex("""[，,。.;；:：]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '"', '“', '”')

        return when {
            cleaned.contains("意面") &&
                listOf("意大利", "肉酱", "番茄", "奶油", "培根", "海鲜", "焗").none { it in cleaned } ->
                "韩式拌面"

            cleaned.contains("烤鱼堡") || cleaned.contains("烤鱼热狗") -> "热狗"
            cleaned == "烤鱼" -> "热狗"
            cleaned.contains("奶茶饮料") || cleaned.contains("杯装奶茶") -> "奶茶"
            cleaned.contains("蜂蜜芥末") -> "蜂蜜芥末酱"
            cleaned.contains("柠檬") && cleaned.contains("茶") -> "柠檬茶"
            cleaned.contains("杯装饮品") || cleaned.contains("杯装饮料") -> "饮料"
            else -> cleaned
        }
    }

    private fun normalizeFoodEmoji(name: String, rawIcon: String): String {
        val cleanedIcon = rawIcon.trim()
        val genericIcons = setOf("", "🍽", "🍽️", "🍴", "🍴🍽️")

        if (cleanedIcon.isNotEmpty() && cleanedIcon !in genericIcons) {
            return cleanedIcon
        }

        val lowerName = name.lowercase(Locale.getDefault())
        return when {
            listOf("寿司", "手卷", "紫菜包饭", "饭团", "卷").any { it in name } -> "🍣"
            listOf("拉面", "面", "粉", "米线").any { it in name } -> "🍜"
            listOf("咖啡", "拿铁", "美式").any { it in name } || "coffee" in lowerName -> "☕"
            listOf("奶茶", "茶饮", "果汁", "饮料", "汽水", "可乐").any { it in name } -> "🥤"
            listOf("蛋", "煎蛋", "荷包蛋").any { it in name } -> "🍳"
            listOf("蛋糕", "面包", "华夫", "甜甜圈", "糕点").any { it in name } -> "🍞"
            listOf("汉堡", "薯条", "炸鸡", "披萨", "快餐").any { it in name } -> "🍔"
            listOf("米饭", "盖饭", "拌饭", "丼", "饭").any { it in name } -> "🍚"
            "蟹" in name -> "🦀"
            "虾" in name -> "🦐"
            "鱼" in name -> "🐟"
            listOf("扇贝", "生蚝", "蛤蜊", "青口", "贝").any { it in name } -> "🦪"
            else -> "🍽️"
        }
    }

    private fun sanitizeRecognizedFoods(items: List<PendingFoodItem>): List<PendingFoodItem> {
        val invalidNames = setOf("未知食物", "食物", "菜品", "项目", "食物名称", "待确认")

        return items.mapNotNull { item ->
            val normalizedName = normalizeRecognizedFoodName(item.name)
            if (normalizedName.isBlank() || normalizedName in invalidNames) {
                return@mapNotNull null
            }

            enrichPendingFoodEstimate(
                PendingFoodItem(
                name = normalizedName,
                kcal = item.kcal.coerceAtLeast(0),
                icon = normalizeFoodEmoji(normalizedName, item.icon),
                carbs = item.carbs.coerceAtLeast(0),
                protein = item.protein.coerceAtLeast(0),
                fat = item.fat.coerceAtLeast(0)
                )
            )
        }
            .distinctBy { it.name.lowercase(Locale.getDefault()) }
            .take(6)
    }

    private fun enrichPendingFoodEstimate(item: PendingFoodItem): PendingFoodItem {
        val name = item.name

        fun withFallback(
            kcal: Int,
            carbs: Int,
            protein: Int,
            fat: Int
        ): PendingFoodItem {
            return item.copy(
                kcal = if (item.kcal > 0) item.kcal else kcal,
                carbs = if (item.carbs > 0) item.carbs else carbs,
                protein = if (item.protein > 0) item.protein else protein,
                fat = if (item.fat > 0) item.fat else fat
            )
        }

        return when {
            "蜂蜜芥末酱" in name -> withFallback(kcal = 90, carbs = 8, protein = 1, fat = 6)
            name.endsWith("酱") -> withFallback(kcal = 60, carbs = 5, protein = 0, fat = 4)
            "奶茶" in name -> withFallback(kcal = 180, carbs = 30, protein = 2, fat = 5)
            "果茶" in name || "柠檬茶" in name || "茶饮" in name -> withFallback(kcal = 120, carbs = 28, protein = 0, fat = 0)
            "可乐" in name || "汽水" in name -> withFallback(kcal = 140, carbs = 35, protein = 0, fat = 0)
            name == "饮料" -> withFallback(kcal = 110, carbs = 26, protein = 0, fat = 0)
            "芝士热狗" in name -> withFallback(kcal = 320, carbs = 28, protein = 11, fat = 18)
            "热狗" in name -> withFallback(kcal = 280, carbs = 25, protein = 10, fat = 15)
            "拌面" in name || "火鸡面" in name || "韩式拌面" in name -> withFallback(kcal = 420, carbs = 60, protein = 10, fat = 16)
            "大闸蟹" in name -> withFallback(kcal = 180, carbs = 4, protein = 22, fat = 7)
            "梭子蟹" in name || "面包蟹" in name -> withFallback(kcal = 200, carbs = 5, protein = 24, fat = 8)
            "蟹" in name -> withFallback(kcal = 190, carbs = 4, protein = 22, fat = 8)
            "小龙虾" in name -> withFallback(kcal = 230, carbs = 6, protein = 25, fat = 10)
            "白灼虾" in name -> withFallback(kcal = 130, carbs = 1, protein = 24, fat = 3)
            "皮皮虾" in name -> withFallback(kcal = 150, carbs = 2, protein = 26, fat = 4)
            "虾" in name -> withFallback(kcal = 170, carbs = 3, protein = 22, fat = 6)
            "清蒸鱼" in name -> withFallback(kcal = 220, carbs = 1, protein = 28, fat = 10)
            "红烧鱼" in name -> withFallback(kcal = 280, carbs = 6, protein = 26, fat = 14)
            "酸菜鱼" in name -> withFallback(kcal = 320, carbs = 8, protein = 28, fat = 18)
            "烤鱼" in name -> withFallback(kcal = 320, carbs = 5, protein = 30, fat = 18)
            "扇贝" in name || "生蚝" in name || "蛤蜊" in name || "青口" in name ->
                withFallback(kcal = 160, carbs = 6, protein = 18, fat = 6)
            else -> item
        }
    }

    private fun needsFoodCoverageReview(items: List<PendingFoodItem>, sceneType: String): Boolean {
        if (items.isEmpty()) return true
        if (sceneType == "mixed_bowl" || sceneType == "single_dish") return false

        val hasDrink = items.any { isDrinkFoodName(it.name) }
        val hasMainFood = items.any { !isDrinkFoodName(it.name) }

        return items.size <= 2 || (hasMainFood && !hasDrink)
    }

    private fun shouldRunDrinkCheck(items: List<PendingFoodItem>, sceneType: String): Boolean {
        if (items.any { isDrinkFoodName(it.name) }) return false
        if (sceneType == "mixed_bowl" || sceneType == "single_dish") return false
        return true
    }

    private fun shouldAcceptInitialRecognition(
        items: List<PendingFoodItem>,
        sceneType: String
    ): Boolean {
        if (items.isEmpty()) return false

        val hasDrink = items.any { isDrinkFoodName(it.name) }
        val hasSpecificFood = items.any { !isGenericFoodName(it.name) }

        return when (sceneType) {
            "single_dish", "mixed_bowl" -> items.size <= 2 && hasSpecificFood
            "drink_only" -> hasDrink
            "packaged_food" -> hasSpecificFood
            "combo_meal" -> items.size >= 3 && hasDrink
            else -> false
        }
    }

    private fun isGenericFoodName(name: String): Boolean {
        return name in setOf("饮料", "食物", "菜品", "主食", "小吃", "酱料")
    }

    private fun shouldPreferCandidateFoods(
        current: List<PendingFoodItem>,
        candidate: List<PendingFoodItem>
    ): Boolean {
        if (candidate.isEmpty()) return false

        val currentHasDrink = current.any { isDrinkFoodName(it.name) }
        val candidateHasDrink = candidate.any { isDrinkFoodName(it.name) }

        return when {
            candidateHasDrink && !currentHasDrink -> true
            candidate.size >= current.size + 2 -> true
            candidate.size > current.size && candidate.any { isFastFoodOrSnackName(it.name) } -> true
            else -> false
        }
    }

    private fun isDrinkFoodName(name: String): Boolean {
        val lowerName = name.lowercase(Locale.getDefault())
        return listOf("奶茶", "茶饮", "果茶", "果汁", "饮料", "汽水", "可乐", "咖啡", "柠檬茶")
            .any { it in name } || listOf("tea", "coffee", "cola", "drink", "juice").any { it in lowerName }
    }

    private fun isFastFoodOrSnackName(name: String): Boolean {
        return listOf(
            "炸鸡", "鸡翅", "鸡柳", "鸡排", "热狗", "香肠", "玉米狗", "薯条",
            "拌面", "火鸡面", "炒面", "芝士", "奶茶", "饮料"
        ).any { it in name }
    }

    private fun parseIntValue(raw: Any?): Int {
        return when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is Double -> raw.toInt()
            is String -> {
                val text = raw.trim()
                val rangeRegex = Regex("""(\d+)\s*[-~到]\s*(\d+)""")
                val rangeMatch = rangeRegex.find(text)
                if (rangeMatch != null) {
                    val start = rangeMatch.groupValues[1].toIntOrNull() ?: 0
                    val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
                    return (start + end) / 2
                }
                Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: 0
            }
            else -> 0
        }
    }

    fun removePendingFood(id: Long) {
        _pendingFoods.value = _pendingFoods.value.filterNot { it.id == id }
        if (_pendingFoods.value.isEmpty()) {
            _showRecognitionConfirmDialog.value = false
        }
    }

    fun clearPendingFoods() {
        _pendingFoods.value = emptyList()
        _showRecognitionConfirmDialog.value = false
    }

    fun confirmSavePendingFoods() {
        val pendingList = _pendingFoods.value
        if (pendingList.isEmpty()) {
            _showRecognitionConfirmDialog.value = false
            return
        }

        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val dateString = SimpleDateFormat("M月d日", Locale.CHINA).format(Date())

            for (item in pendingList) {
                val fingerprint = "${_selectedMealType.value}|${item.name}|${item.kcal}"
                val now = System.currentTimeMillis()

                if (lastSavedFoodFingerprint == fingerprint &&
                    now - lastSavedFoodTimeMillis < 2 * 60 * 1000
                ) {
                    continue
                }

                val record = FoodRecord(
                    userAccount = account,
                    name = item.name,
                    kcal = item.kcal,
                    icon = item.icon,
                    time = timeString,
                    date = dateString,
                    mealType = _selectedMealType.value,
                    carbs = item.carbs,
                    protein = item.protein,
                    fat = item.fat
                )

                foodRepository.insertFoodRecord(record)

                lastSavedFoodFingerprint = fingerprint
                lastSavedFoodTimeMillis = now
            }

            val records = foodRepository.getFoodRecordsByUser(account)

            withContext(Dispatchers.Main) {
                refreshFoodStats(records)
                _pendingFoods.value = emptyList()
                _showRecognitionConfirmDialog.value = false
            }
        }
    }

    fun addScannedFood(
        name: String,
        kcal: Int,
        icon: String,
        carbs: Int = 0,
        protein: Int = 0,
        fat: Int = 0
    ) {
        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val dateString = SimpleDateFormat("M月d日", Locale.CHINA).format(Date())

            val record = FoodRecord(
                userAccount = account,
                name = name,
                kcal = kcal,
                icon = icon,
                time = timeString,
                date = dateString,
                mealType = _selectedMealType.value,
                carbs = carbs,
                protein = protein,
                fat = fat
            )

            foodRepository.insertFoodRecord(record)

            val records = foodRepository.getFoodRecordsByUser(account)
            withContext(Dispatchers.Main) {
                refreshFoodStats(records)
            }
        }
    }

    fun retryAnalysis() {
        lastAttemptBitmap?.let { analyzeFoodImage(it) }
    }

    fun dismissError() {
        _showErrorDialog.value = false
    }

    fun deleteFoodRecord(record: FoodRecord) {
        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            foodRepository.deleteFoodRecord(record)
            val records = foodRepository.getFoodRecordsByUser(account)

            withContext(Dispatchers.Main) {
                refreshFoodStats(records)
                if (records.isEmpty()) {
                    _isDietDeleteMode.value = false
                }
            }
        }
    }
// endregion

    // region 6. 睡眠、天气与久坐提醒
    private val _sleepAdvice = MutableStateFlow("正在同步睡眠数据...")
    val sleepAdvice = _sleepAdvice.asStateFlow()

    private val _sleepScore = MutableStateFlow(0)
    val sleepScore = _sleepScore.asStateFlow()

    private val _sleepData = MutableStateFlow<List<Float>>(emptyList())
    val sleepData = _sleepData.asStateFlow()

    private val _sleepDetails = MutableStateFlow(SleepHardwareDetails())
    val sleepDetails = _sleepDetails.asStateFlow()

    private val _sleepTrend = MutableStateFlow<List<SleepTrendPoint>>(emptyList())
    val sleepTrend = _sleepTrend.asStateFlow()

    private val sleepSignalSamples = mutableListOf<SleepSignalSample>()
    private var lastSleepEstimateSavedAt = 0L

    private val _currentLocation = MutableStateFlow("正在定位...")
    val currentLocation = _currentLocation.asStateFlow()

    private val _currentWeather = MutableStateFlow("尚未同步")
    val currentWeather = _currentWeather.asStateFlow()

    private val _isWeatherSyncing = MutableStateFlow(false)
    val isWeatherSyncing = _isWeatherSyncing.asStateFlow()

    private val _isSedentaryEnabled = MutableStateFlow(false)
    val isSedentaryEnabled = _isSedentaryEnabled.asStateFlow()

    private val _sedentaryInterval = MutableStateFlow(60)
    val sedentaryInterval = _sedentaryInterval.asStateFlow()

    private val _weatherUiState = MutableStateFlow(WeatherUiState())
    val weatherUiState = _weatherUiState.asStateFlow()

    private fun List<SleepRecord>.toSleepTrend(): List<SleepTrendPoint> {
        return this
            .sortedBy { it.updatedAt }
            .map { record ->
                SleepTrendPoint(
                    label = record.date.takeLast(5).replace("-", "/"),
                    score = record.score
                )
            }
    }

    private fun loadSleepRecordForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = sleepRepository.getLatestSleepRecordByUser(account)
            val trend = sleepRepository.getRecentSleepRecordsByUser(account, 7).toSleepTrend()

            withContext(Dispatchers.Main) {
                _sleepTrend.value = trend
                if (record == null) {
                    _sleepScore.value = 0
                    _sleepData.value = emptyList()
                    _sleepDetails.value = SleepHardwareDetails()
                    _sleepAdvice.value = "正在同步睡眠数据..."
                } else {
                    val parsedData = if (record.dataPoints.isBlank()) {
                        emptyList()
                    } else {
                        record.dataPoints.split(",").mapNotNull { it.toFloatOrNull() }
                    }

                    _sleepScore.value = record.score
                    _sleepData.value = parsedData
                    _sleepDetails.value = SleepHardwareDetails()
                    _sleepAdvice.value = when {
                        record.score >= 90 -> "睡眠质量极佳，继续保持！"
                        record.score >= 70 -> "睡得还不错，建议早点休息。"
                        else -> "睡眠较浅，睡前试试放下手机。"
                    }
                }
            }
        }
    }

    private fun updateSleepEstimateFromVitals(
        heartRate: Int,
        bloodOxygen: Int,
        steps: Int,
        hasSteps: Boolean
    ) {
        if (heartRate <= 0) return

        val now = System.currentTimeMillis()
        sleepSignalSamples += SleepSignalSample(
            timestamp = now,
            heartRate = heartRate,
            bloodOxygen = bloodOxygen,
            steps = steps,
            hasSteps = hasSteps
        )
        sleepSignalSamples.removeAll { sample ->
            sample.timestamp < now - SLEEP_SIGNAL_WINDOW_MS
        }

        if (sleepSignalSamples.size < SLEEP_ESTIMATE_MIN_SAMPLES) {
            if (_sleepScore.value == 0) {
                _sleepAdvice.value = "正在根据心率、血氧和步数积累睡眠判断样本..."
            }
            return
        }

        val estimate = buildSleepEstimateFromSignals(sleepSignalSamples)
        _sleepScore.value = estimate.score
        _sleepData.value = estimate.dataPoints
        _sleepDetails.value = estimate.details
        _sleepAdvice.value = estimate.advice

        if (now - lastSleepEstimateSavedAt >= SLEEP_ESTIMATE_SAVE_INTERVAL_MS) {
            lastSleepEstimateSavedAt = now
            persistSleepEstimate(estimate, now)
        }
    }

    private fun buildSleepEstimateFromSignals(samples: List<SleepSignalSample>): SleepEstimate {
        val orderedSamples = samples.sortedBy { it.timestamp }
        val heartRates = orderedSamples.map { it.heartRate }
        val avgHeartRate = heartRates.average()
        val heartStability = heartRates
            .zipWithNext { previous, current -> kotlin.math.abs(current - previous) }
            .let { changes -> if (changes.isEmpty()) 0.0 else changes.average() }
        val validOxygen = orderedSamples
            .map { it.bloodOxygen }
            .filter { it > 0 }
        val avgBloodOxygen = if (validOxygen.isEmpty()) 0.0 else validOxygen.average()
        val hasStepSignal = orderedSamples.any { it.hasSteps }
        val stepDelta = if (hasStepSignal) {
            (orderedSamples.last().steps - orderedSamples.first().steps).coerceAtLeast(0)
        } else {
            0
        }
        val observedMinutes = ((orderedSamples.last().timestamp - orderedSamples.first().timestamp) / 60_000L)
            .coerceAtLeast(1L)

        val stepScore = when {
            !hasStepSignal -> 24
            stepDelta <= 5 -> 35
            stepDelta <= 30 -> 28
            stepDelta <= 100 -> 18
            else -> 8
        }
        val heartScore = when {
            avgHeartRate <= 0 -> 14
            avgHeartRate <= 62 -> 30
            avgHeartRate <= 72 -> 24
            avgHeartRate <= 85 -> 15
            else -> 7
        }
        val stabilityScore = when {
            heartStability <= 2.0 -> 20
            heartStability <= 5.0 -> 16
            heartStability <= 9.0 -> 10
            else -> 5
        }
        val oxygenScore = when {
            avgBloodOxygen <= 0.0 -> 8
            avgBloodOxygen >= 95.0 -> 15
            avgBloodOxygen >= 90.0 -> 10
            else -> 4
        }
        val confidencePenalty = when {
            orderedSamples.size < 6 -> 10
            observedMinutes < 10 -> 6
            else -> 0
        }
        val score = (stepScore + heartScore + stabilityScore + oxygenScore - confidencePenalty)
            .coerceIn(45, 96)

        val recentSamples = orderedSamples.takeLast(24)
        val stagePoints = recentSamples.mapIndexed { index, sample ->
            val previousSteps = recentSamples.getOrNull(index - 1)?.steps ?: sample.steps
            val stepIncrease = (sample.steps - previousSteps).coerceAtLeast(0)
            when {
                hasStepSignal && stepIncrease > 10 -> 2f
                sample.heartRate <= avgHeartRate - 4 && (!hasStepSignal || stepIncrease <= 2) -> 8f
                sample.heartRate <= avgHeartRate + 5 && (!hasStepSignal || stepIncrease <= 2) -> 6f
                else -> 4f
            }
        }.withMinimumSleepPoints()

        val deepCount = stagePoints.count { it >= 7f }
        val deepSleepMinutes = if (deepCount == 0) {
            0
        } else {
            ((observedMinutes * deepCount) / stagePoints.size)
                .toInt()
                .coerceAtLeast(1)
        }
        val wakeCount = stagePoints
            .zipWithNext()
            .count { (previous, current) -> previous > 3f && current <= 3f }

        val details = SleepHardwareDetails(
            bedTime = formatShortTime(orderedSamples.first().timestamp),
            wakeTime = "监测中",
            deepSleepMinutes = deepSleepMinutes,
            wakeCount = wakeCount
        )
        val advice = when {
            !hasStepSignal -> "已根据心率和血氧自动估算睡眠；当前单片机未回传步数，连续佩戴后结果会更准。"
            score >= 85 -> "心率稳定且活动很少，睡眠恢复状态较好。"
            score >= 70 -> "睡眠状态较平稳，建议继续保持规律作息。"
            else -> "心率波动或活动偏多，睡眠可能较浅。"
        }

        return SleepEstimate(
            score = score,
            dataPoints = stagePoints,
            details = details,
            advice = advice
        )
    }

    private fun List<Float>.withMinimumSleepPoints(): List<Float> {
        if (isEmpty()) return listOf(4f, 5f, 6f, 6f, 5f, 4f, 3f)

        val points = toMutableList()
        while (points.size < 7) {
            points += points.last()
        }
        return points
    }

    private fun formatShortTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun persistSleepEstimate(estimate: SleepEstimate, timestamp: Long) {
        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
            val record = SleepRecord(
                userAccount = account,
                date = date,
                score = estimate.score,
                dataPoints = estimate.dataPoints.joinToString(","),
                updatedAt = timestamp
            )

            sleepRepository.insertSleepRecord(record)
            val trend = sleepRepository.getRecentSleepRecordsByUser(account, 7).toSleepTrend()

            withContext(Dispatchers.Main) {
                _sleepTrend.value = trend
            }
        }
    }

    fun updateSleepDataFromHardware(
        newScore: Int,
        newDataPoints: List<Float>,
        details: SleepHardwareDetails = SleepHardwareDetails()
    ) {
        val account = _currentUserAccount.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val packedData = newDataPoints.joinToString(",")

            val record = SleepRecord(
                userAccount = account,
                date = today,
                score = newScore,
                dataPoints = packedData
            )

            sleepRepository.insertSleepRecord(record)
            val trend = sleepRepository.getRecentSleepRecordsByUser(account, 7).toSleepTrend()

            withContext(Dispatchers.Main) {
                _sleepScore.value = newScore
                _sleepData.value = newDataPoints
                _sleepDetails.value = details
                _sleepTrend.value = trend
                _sleepAdvice.value = when {
                    newScore >= 90 -> "睡眠质量极佳，继续保持！"
                    newScore >= 70 -> "睡得还不错，建议早点休息。"
                    else -> "睡眠较浅，睡前试试放下手机。"
                }
            }
        }
    }

    fun getLocationAndFetchWeather(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        _currentLocation.value = "正在定位..."

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLocation ->
                    if (lastLocation != null) {
                        handleResolvedWeatherLocation(context, lastLocation.latitude, lastLocation.longitude)
                    } else {
                        val tokenSource = CancellationTokenSource()
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            tokenSource.token
                        ).addOnSuccessListener { currentLocation ->
                            if (currentLocation != null) {
                                handleResolvedWeatherLocation(
                                    context,
                                    currentLocation.latitude,
                                    currentLocation.longitude
                                )
                            } else {
                                markWeatherUnavailable("定位失败，请点右上角重试")
                            }
                        }.addOnFailureListener { error ->
                            Log.e("LOCATION_ERROR", "获取当前位置失败: ${error.message}", error)
                            markWeatherUnavailable("定位失败，请点右上角重试")
                        }
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("LOCATION_ERROR", "读取上次定位失败: ${error.message}", error)
                    markWeatherUnavailable("定位失败，请点右上角重试")
                }
        } catch (e: SecurityException) {
            Log.e("LOCATION_ERROR", "没有定位权限", e)
            markWeatherUnavailable("未授予定位权限")
        }
    }

    private fun handleResolvedWeatherLocation(context: Context, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val weatherLocation = resolveWeatherLocation(context, latitude, longitude)
            withContext(Dispatchers.Main) {
                if (weatherLocation == null) {
                    markWeatherUnavailable("定位失败，请点右上角重试")
                } else {
                    _currentLocation.value = weatherLocation.displayCity
                    fetchRealWeather(weatherLocation)
                }
            }
        }
    }

    private fun resolveWeatherLocation(context: Context, latitude: Double, longitude: Double): WeatherLocationCandidate? {
        return try {
            val geocoder = android.location.Geocoder(context, Locale.CHINA)
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull()
            val localCity = normalizeWeatherCityName(address?.locality)
            val subAdminCity = normalizeWeatherCityName(address?.subAdminArea)
            val province = normalizeWeatherCityName(address?.adminArea)
            val countyOrDistrict = normalizeWeatherCityName(address?.subLocality)

            val displayCity = listOf(countyOrDistrict, localCity, subAdminCity, province)
                .firstOrNull { !it.isNullOrBlank() }
                ?: return null

            val queryCities = listOf(
                localCity?.takeUnless { it == countyOrDistrict },
                subAdminCity?.takeUnless { it == countyOrDistrict },
                localCity,
                subAdminCity,
                countyOrDistrict,
                province
            )
                .mapNotNull { it?.takeIf { city -> city.isNotBlank() } }
                .distinct()
                .filterNot(::isProvinceLevelWeatherName)

            if (queryCities.isEmpty()) return null

            WeatherLocationCandidate(
                displayCity = displayCity,
                queryCities = queryCities
            )
        } catch (e: Exception) {
            Log.e("LOCATION_ERROR", "地理反编码失败: ${e.message}", e)
            null
        }
    }

    private fun normalizeWeatherCityName(rawName: String?): String? {
        val name = rawName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return name
            .removeSuffix("特别行政区")
            .removeSuffix("蒙古自治州")
            .removeSuffix("藏族自治州")
            .removeSuffix("回族自治州")
            .removeSuffix("哈萨克自治州")
            .removeSuffix("自治州")
            .removeSuffix("地区")
            .removeSuffix("盟")
            .removeSuffix("市")
            .removeSuffix("县")
            .removeSuffix("区")
            .removeSuffix("省")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun isProvinceLevelWeatherName(name: String): Boolean {
        return name !in municipalityWeatherNames && name in provinceLevelWeatherNames
    }

    private fun markWeatherUnavailable(statusText: String) {
        _currentLocation.value = statusText
        _currentWeather.value = "天气暂不可用"
        _isWeatherSyncing.value = false
    }

    private fun fetchRealWeather(location: WeatherLocationCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            _isWeatherSyncing.value = true

            if (!weatherRemoteDataSource.hasApiKey) {
                Log.e("WEATHER", "weatherKey 为空")
                _currentWeather.value = "天气Key未配置"
                _isWeatherSyncing.value = false
                return@launch
            }

            try {
                var resolvedState: WeatherUiState? = null
                var lastError: Exception? = null

                for (queryCity in location.queryCities) {
                    try {
                        val json = weatherRemoteDataSource.fetchWeatherJson(queryCity)
                        resolvedState = parseWeatherState(
                            json = json,
                            displayCity = location.displayCity,
                            queryCity = queryCity
                        )

                        if (resolvedState != null) break
                    } catch (e: Exception) {
                        lastError = e
                        Log.w("WEATHER", "天气查询失败，city=$queryCity, reason=${e.message}")
                    }
                }

                val finalState = resolvedState
                    ?: throw lastError
                    ?: IllegalStateException("天气接口没有返回有效数据")

                withContext(Dispatchers.Main) {
                    _currentLocation.value = location.displayCity
                    _currentWeather.value = "${finalState.temperature}°C, ${finalState.weather}"
                    _weatherUiState.value = finalState
                }
            } catch (e: Exception) {
                Log.e("WEATHER", "天气同步失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _currentWeather.value = "同步失败"
                }
            } finally {
                _isWeatherSyncing.value = false
            }
        }
    }

    private fun parseWeatherState(
        json: JSONObject,
        displayCity: String,
        queryCity: String
    ): WeatherUiState? {
        val weather = json.optString("weather", "").trim()
            .takeIf { it.isNotBlank() && it !in setOf("未知天气", "未知", "--") }
            ?: return null

        if (!json.has("temperature")) return null

        val temperature = json.optInt("temperature", 0)
        val tempMax = json.optInt("temp_max", temperature)
        val tempMin = json.optInt("temp_min", temperature)
        val aqi = json.optInt("aqi", 0)
        val aqiCategory = json.optString("aqi_category", "未知").trim().ifBlank { "未知" }
        val humidity = json.optInt("humidity", 0)
        val windDirection = json.optString("wind_direction", "").trim()
        val windPower = json.optString("wind_power", "").trim()
        val responseCity = normalizeWeatherCityName(json.optString("city", queryCity))

        if (responseCity != null && isProvinceLevelWeatherName(responseCity)) return null

        val looksLikeEmptyPayload = temperature == 0 &&
            tempMax == 0 &&
            tempMin == 0 &&
            aqi == 0 &&
            humidity == 0 &&
            aqiCategory in setOf("未知", "--") &&
            windDirection.isBlank() &&
            windPower.isBlank()

        if (looksLikeEmptyPayload) return null

        val forecastArray = json.optJSONArray("forecast")
        val hourlyArray = json.optJSONArray("hourly_forecast")

        return WeatherUiState(
            city = displayCity,
            weather = weather,
            weatherIcon = json.optString("weather_icon", ""),
            temperature = temperature,
            tempMax = tempMax,
            tempMin = tempMin,
            aqi = aqi,
            aqiCategory = aqiCategory,
            feelsLike = json.optDouble("feels_like", temperature.toDouble()),
            visibility = json.optDouble("visibility", 0.0),
            pressure = json.optDouble("pressure", 0.0),
            uv = json.optDouble("uv", 0.0),
            humidity = humidity,
            windDirection = windDirection,
            windPower = windPower,
            hourlyForecastJson = hourlyArray?.toString() ?: "[]",
            forecastJson = forecastArray?.toString() ?: "[]"
        )
    }

    fun updateSedentaryReminder(enabled: Boolean, interval: Int) {
        _isSedentaryEnabled.value = enabled
        _sedentaryInterval.value = interval
        Log.d("DEVICE_DEBUG", "指令下发: SED:$enabled,$interval")
    }
    // endregion

    // region 7. 便签与历史
    private val _noteText = MutableStateFlow("")
    val noteText = _noteText.asStateFlow()

    private val _noteHistory = MutableStateFlow<List<String>>(emptyList())
    val noteHistory = _noteHistory.asStateFlow()

    fun updateNoteText(newText: String) {
        _noteText.value = newText
    }

    private fun loadNotesForCurrentUser(account: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notes = noteRepository.getNotesByUserAccount(account)
            withContext(Dispatchers.Main) {
                _noteHistory.value = notes.map { it.content }
            }
        }
    }

    fun sendNoteToDevice(content: String) {
        val account = _currentUserAccount.value ?: return
        if (content.isBlank()) return
        if (!_isConnected.value) {
            _deviceDataText.value = "请先连接 STM32 热点，再同步便签"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sentAt = System.currentTimeMillis()
                sendNoteJsonToStm32(
                    content = content,
                    account = account,
                    sentAt = sentAt
                )

                val note = NoteRecord(
                    userAccount = account,
                    content = content,
                    createdAt = sentAt
                )
                noteRepository.insertNote(note)

                val notes = noteRepository.getNotesByUserAccount(account)
                withContext(Dispatchers.Main) {
                    _noteHistory.value = notes.map { it.content }
                    _noteText.value = ""
                    _deviceDataText.value = "便签已通过 TCP 同步到 STM32"
                }

                Log.d("DEVICE_DEBUG", "便签已发送到设备: $content")
            } catch (e: Exception) {
                Log.e("DEVICE_DEBUG", "发送便签到设备失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _deviceDataText.value = "便签发送失败: ${e.message ?: "STM32 数据通道未就绪"}"
                }
            }
        }
    }

    private suspend fun sendNoteJsonToStm32(
        content: String,
        account: String,
        sentAt: Long
    ) {
        val socket = awaitWritableStm32Socket()
        val noteJson = JSONObject().apply {
            put("type", "note")
            put("content", content)
            put("account", account)
            put("timestamp", sentAt)
        }.toString() + "\r\n"

        synchronized(stm32SocketWriteLock) {
            if (socket.isClosed || !socket.isConnected) {
                throw IllegalStateException("STM32 TCP 通道已断开")
            }
            val output = socket.getOutputStream()
            output.write(noteJson.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    private suspend fun awaitWritableStm32Socket(): Socket {
        repeat(20) {
            val socket = stm32DataSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                return socket
            }
            delay(100)
        }
        throw IllegalStateException("STM32 数据通道未建立，请先保持设备连接并等待 TCP 通道连上")
    }
// endregion

    init {
        calculateCurrentWeekRange()
    }

    private fun calculateCurrentWeekRange() {
        val calendar = Calendar.getInstance(Locale.CHINESE)
        calendar.firstDayOfWeek = Calendar.MONDAY
        val dateFormat = SimpleDateFormat("M月d日", Locale.CHINESE)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val start = dateFormat.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        _weeklyRange.value = "$start - ${dateFormat.format(calendar.time)}"
    }

    private fun getCurrentWeekTimeRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance(Locale.CHINESE)
        calendar.firstDayOfWeek = Calendar.MONDAY

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        return startOfWeek to endOfWeek
    }
}
