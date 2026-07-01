package com.example.healthmanager.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import com.example.healthmanager.data.remote.FoodRecognitionPromptBuilder
import com.example.healthmanager.data.remote.FoodRecognitionRemoteDataSource
import com.example.healthmanager.data.remote.FoodRecognitionResultMapper
import com.example.healthmanager.data.remote.RecognizedFoodItem
import com.example.healthmanager.data.remote.WeatherRemoteDataSource
import com.example.healthmanager.data.remote.WeatherStateMapper
import com.example.healthmanager.data.remote.WeatherUiState
import com.example.healthmanager.database.AppDatabase
import com.example.healthmanager.device.Stm32DemoPayloadFactory
import com.example.healthmanager.device.Stm32DeviceSession
import com.example.healthmanager.device.Stm32DevicePayload
import com.example.healthmanager.device.Stm32EndpointResolver
import com.example.healthmanager.device.Stm32WifiHotspotPolicy
import com.example.healthmanager.device.WifiAccessPoint
import com.example.healthmanager.domain.ExerciseSummaryCalculator
import com.example.healthmanager.domain.FoodStatsCalculator
import com.example.healthmanager.domain.HeartRateAlertPolicy
import com.example.healthmanager.domain.HeartRateAlertState
import com.example.healthmanager.domain.SleepEstimate
import com.example.healthmanager.domain.SleepEstimateCalculator
import com.example.healthmanager.domain.SleepHardwareDetails
import com.example.healthmanager.domain.SleepSignalSample
import com.example.healthmanager.domain.WeatherLocationCandidate
import com.example.healthmanager.model.User
import com.example.healthmanager.platform.AndroidWeatherLocationResolver
import com.example.healthmanager.platform.HealthVibrationController
import com.example.healthmanager.platform.WifiPlatformGateway
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import androidx.annotation.RequiresApi

private const val STM32_WIFI_CONNECT_TIMEOUT_MS = 30_000
private const val SLEEP_SIGNAL_WINDOW_MS = 12 * 60 * 60 * 1000L
private const val SLEEP_ESTIMATE_SAVE_INTERVAL_MS = 60_000L
private const val SLEEP_ESTIMATE_MIN_SAMPLES = 3

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
    private val wifiPlatformGateway = WifiPlatformGateway(application.applicationContext)
    private val vibrationController = HealthVibrationController(application.applicationContext)
    private val weatherLocationResolver = AndroidWeatherLocationResolver(application.applicationContext)
    private val deviceClient = client.newBuilder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1800, TimeUnit.MILLISECONDS)
        .callTimeout(2500, TimeUnit.MILLISECONDS)
        .build()
    private val stm32DeviceSession = Stm32DeviceSession(
        httpClient = deviceClient,
        socketFactoryProvider = { currentNetwork?.socketFactory }
    )

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
            val summary = ExerciseSummaryCalculator.latest(record, "户外跑步")

            withContext(Dispatchers.Main) {
                _latestExerciseDistance.value = summary.distanceKm
                _latestExerciseDuration.value = summary.durationSeconds
                _latestExerciseCalories.value = summary.calories
                _latestExerciseType.value = summary.type
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

            val summary = ExerciseSummaryCalculator.weekly(records)

            withContext(Dispatchers.Main) {
                _weeklyExerciseDurationMinutes.value = summary.durationMinutes
                _weeklyExerciseDistanceKm.value = summary.distanceKm
                _weeklyExerciseCount.value = summary.count
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

            val latestSummary = ExerciseSummaryCalculator.latest(record, "户外跑步")
            val weeklySummary = ExerciseSummaryCalculator.weekly(weeklyRecords)

            withContext(Dispatchers.Main) {
                _latestExerciseType.value = latestSummary.type
                _latestExerciseDistance.value = latestSummary.distanceKm
                _latestExerciseDuration.value = latestSummary.durationSeconds
                _latestExerciseCalories.value = latestSummary.calories

                _weeklyExerciseDurationMinutes.value = weeklySummary.durationMinutes
                _weeklyExerciseDistanceKm.value = weeklySummary.distanceKm
                _weeklyExerciseCount.value = weeklySummary.count
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
        _deviceDataText.value = "正在连接手环 TCP 数据通道 ${Stm32EndpointResolver.DEFAULT_ENDPOINT}，等待 8080 端口推送..."

        val listenerToken = ++stm32DataListenerToken
        stm32DataListenerJob = viewModelScope.launch(Dispatchers.IO) {
            val dataUrls = buildStm32DataUrls()
            try {
                while (currentCoroutineContext().isActive && _isConnected.value) {
                    try {
                        stm32DeviceSession.listenPayloads(
                            urls = dataUrls,
                            isConnected = { _isConnected.value },
                            onTcpWaiting = {
                                withContext(Dispatchers.Main) {
                                    _deviceDataText.value = "已连上 HRB_AP 数据端口，正在等待手环主动推送心率数据..."
                                }
                            },
                            onPayload = { payload ->
                                withContext(Dispatchers.Main) {
                                    _isFetchingDeviceData.value = false
                                    applyStm32DevicePayload(payload)
                                }
                            }
                        )
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
                                _deviceDataText.value = "手环数据通道暂无新数据，正在自动重连...\n${formatStm32Error(e)}"
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
                    stm32DeviceSession.close()
                    stm32DataListenerJob = null
                    withContext(Dispatchers.Main) {
                        _isFetchingDeviceData.value = false
                    }
                }
            }
        }
    }

    private fun buildStm32DataUrls(): List<String> {
        val routeGateways = wifiPlatformGateway.linkGatewayHosts(currentNetwork)
        val dhcpGateway = wifiPlatformGateway.dhcpGatewayHost()

        return Stm32EndpointResolver.buildDataUrls(
            rawManualEndpoint = _stm32DataEndpoint.value,
            discoveredHosts = routeGateways + listOfNotNull(dhcpGateway)
        )
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
    private var currentNetwork: Network? = null
    private var currentNetworkCallback: NetworkCallback? = null
    private var pendingConnectionSsid: String? = null
    private var stm32DataListenerJob: Job? = null
    private var stm32DataListenerToken = 0

    private fun stopStm32DataListener() {
        stm32DataListenerToken++
        stm32DataListenerJob?.cancel()
        stm32DataListenerJob = null
        stm32DeviceSession.close()
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

    private val _stm32DataEndpoint = MutableStateFlow(Stm32EndpointResolver.DEFAULT_ENDPOINT)
    val stm32DataEndpoint = _stm32DataEndpoint.asStateFlow()

    fun updateStm32DataEndpoint(endpoint: String) {
        _stm32DataEndpoint.value = endpoint
    }

    fun startDemoDeviceMode() {
        stopStm32DataListener()
        currentNetworkCallback?.let {
            runCatching { wifiPlatformGateway.unregisterNetworkCallback(it) }
        }
        runCatching { wifiPlatformGateway.bindProcessToNetwork(null) }

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

        val ssid = wifiPlatformGateway.currentSsid()
        if (!Stm32WifiHotspotPolicy.isLikelyStm32HotspotName(ssid)) {
            return false
        }

        val wifiNetwork = wifiPlatformGateway.activeWifiNetwork()

        if (wifiNetwork != null) {
            currentNetwork = wifiNetwork
            runCatching { wifiPlatformGateway.bindProcessToNetwork(wifiNetwork) }
        }

        pendingConnectionSsid = null
        _connectedSsid.value = ssid
        _isConnected.value = true
        return true
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
        if (!wifiPlatformGateway.hasRequiredPermissions()) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先授予定位和附近 Wi-Fi 权限，再扫描 STM32 热点。"
            return
        }

        if (!wifiPlatformGateway.isWifiEnabled) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先打开手机 Wi-Fi，再扫描 STM32 热点。"
            return
        }

        if (!wifiPlatformGateway.isLocationServiceEnabled()) {
            _isScanningWifi.value = false
            _wifiList.value = emptyList()
            _deviceDataText.value = "请先打开系统定位服务，Android 扫描 Wi-Fi 热点需要定位开关开启。"
            return
        }

        _isScanningWifi.value = true
        _wifiList.value = emptyList()
        _deviceDataText.value = "正在扫描附近热点，请确认 STM32 已开启 Wi-Fi 热点。"
        val cachedAccessPoints = wifiPlatformGateway.cachedAccessPoints()
        if (cachedAccessPoints.isNotEmpty()) {
            _wifiList.value = cachedAccessPoints
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val accessPoints = wifiPlatformGateway.cachedAccessPoints()
                    updateWifiScanSummary(accessPoints)
                } catch (e: Exception) {
                    Log.e("WIFI_SCAN", "扫描失败: ${e.message}", e)
                    _deviceDataText.value = "扫描失败: ${e.message}"
                } finally {
                    _isScanningWifi.value = false
                    runCatching {
                        wifiPlatformGateway.unregisterScanReceiver(this)
                    }
                }
            }
        }

        wifiPlatformGateway.registerScanReceiver(receiver)

        val started = wifiPlatformGateway.requestScan()
        if (!started) {
            _isScanningWifi.value = false
            runCatching {
                wifiPlatformGateway.unregisterScanReceiver(receiver)
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
                runCatching { wifiPlatformGateway.unregisterNetworkCallback(it) }
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

            val callback = object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    currentNetwork = network
                    pendingConnectionSsid = null
                    wifiPlatformGateway.bindProcessToNetwork(network)
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
                    wifiPlatformGateway.bindProcessToNetwork(null)
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
            wifiPlatformGateway.requestNetwork(request, callback, STM32_WIFI_CONNECT_TIMEOUT_MS)
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
                runCatching { wifiPlatformGateway.unregisterNetworkCallback(it) }
            }
            wifiPlatformGateway.bindProcessToNetwork(null)
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

    private var heartRateAlertState = HeartRateAlertState()
    private val _showHeartRateAlert = MutableStateFlow(false)
    val showHeartRateAlert = _showHeartRateAlert.asStateFlow()

    fun onHeartRateReceived(rate: Int) {
        _heartRate.value = rate
        val decision = HeartRateAlertPolicy.onSample(
            rateBpm = rate,
            nowMillis = System.currentTimeMillis(),
            currentState = heartRateAlertState
        )
        heartRateAlertState = decision.state
        _showHeartRateAlert.value = decision.state.visible

        if (decision.shouldVibrate) {
            vibrationController.vibrateHeartRateAlert()
        }
    }

    fun dismissHeartRateAlert() {
        heartRateAlertState = HeartRateAlertPolicy.dismiss(
            currentState = heartRateAlertState,
            nowMillis = System.currentTimeMillis()
        )
        _showHeartRateAlert.value = heartRateAlertState.visible
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
        val summary = FoodStatsCalculator.build(records, today)

        _foodList.value = summary.records
        _totalKcal.value = summary.totalKcal
        _todayTotalKcal.value = summary.todayTotalKcal
        _todayCarbs.value = summary.todayCarbs
        _todayProtein.value = summary.todayProtein
        _todayFat.value = summary.todayFat

        _dailyKcalStats.value = summary.dailyKcalStats
            .map { stat ->
                DailyKcalStat(
                    date = stat.date,
                    totalKcal = stat.totalKcal
                )
            }
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
                val initialSceneType = FoodRecognitionResultMapper.extractSceneType(initialResult)
                val initialFoods = FoodRecognitionResultMapper.sanitize(
                    FoodRecognitionResultMapper.parseItems(initialResult)
                )
                if (FoodRecognitionResultMapper.shouldAcceptInitialRecognition(initialFoods, initialSceneType)) {
                    withContext(Dispatchers.Main) {
                        _pendingFoods.value = initialFoods.toPendingFoodItems()
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
                val reviewedSceneType = FoodRecognitionResultMapper.extractSceneType(reviewedResult).ifBlank { initialSceneType }

                val reviewedFoods = FoodRecognitionResultMapper.sanitize(
                    FoodRecognitionResultMapper.parseItems(reviewedResult)
                )
                val sanitizedFoods = if (FoodRecognitionResultMapper.needsCoverageReview(reviewedFoods, reviewedSceneType)) {
                    val coverageFoods = runCatching {
                        FoodRecognitionResultMapper.sanitize(
                            FoodRecognitionResultMapper.parseItems(
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

                    if (FoodRecognitionResultMapper.shouldPreferCandidateFoods(reviewedFoods, coverageFoods)) {
                        coverageFoods
                    } else {
                        reviewedFoods
                    }
                } else {
                    reviewedFoods
                }
                val completedFoods = if (FoodRecognitionResultMapper.shouldRunDrinkCheck(sanitizedFoods, reviewedSceneType)) {
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
                    _pendingFoods.value = completedFoods.toPendingFoodItems()
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

    private fun buildFoodRecognitionPrompt(): String =
        FoodRecognitionPromptBuilder.recognitionPrompt()

    private fun buildFoodReviewPrompt(initialResult: JSONObject): String =
        FoodRecognitionPromptBuilder.reviewPrompt(initialResult)

    private fun buildFoodCoveragePrompt(currentFoods: List<RecognizedFoodItem>): String =
        FoodRecognitionPromptBuilder.coveragePrompt(currentFoods.map { it.name })

    private fun buildDrinkOnlyPrompt(currentFoods: List<RecognizedFoodItem>): String =
        FoodRecognitionPromptBuilder.drinkOnlyPrompt(currentFoods.map { it.name })

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

    private fun mergeMissingDrinkFoods(
        baseFoods: List<RecognizedFoodItem>,
        imageBase64: String,
        imageDataUrl: String
    ): List<RecognizedFoodItem> {
        val drinkFoods = runCatching {
            FoodRecognitionResultMapper.sanitize(
                FoodRecognitionResultMapper.parseItems(
                    requestFoodRecognitionJson(
                        imageBase64 = imageBase64,
                        imageDataUrl = imageDataUrl,
                        promptText = buildDrinkOnlyPrompt(baseFoods)
                    )
                )
            ).filter { FoodRecognitionResultMapper.isDrinkFoodName(it.name) }
        }.getOrElse { error ->
            Log.w("FOOD_AI", "饮料补漏失败: ${error.message}")
            emptyList()
        }

        if (drinkFoods.isEmpty()) return baseFoods

        return (baseFoods + drinkFoods)
            .distinctBy { it.name.lowercase(Locale.getDefault()) }
            .take(6)
    }

    private fun List<RecognizedFoodItem>.toPendingFoodItems(): List<PendingFoodItem> {
        return map { item ->
            PendingFoodItem(
                name = item.name,
                kcal = item.kcal,
                icon = item.icon,
                carbs = item.carbs,
                protein = item.protein,
                fat = item.fat
            )
        }
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

        val estimate = SleepEstimateCalculator.build(sleepSignalSamples)
        _sleepScore.value = estimate.score
        _sleepData.value = estimate.dataPoints
        _sleepDetails.value = estimate.details
        _sleepAdvice.value = estimate.advice

        if (now - lastSleepEstimateSavedAt >= SLEEP_ESTIMATE_SAVE_INTERVAL_MS) {
            lastSleepEstimateSavedAt = now
            persistSleepEstimate(estimate, now)
        }
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
                        handleResolvedWeatherLocation(lastLocation.latitude, lastLocation.longitude)
                    } else {
                        val tokenSource = CancellationTokenSource()
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            tokenSource.token
                        ).addOnSuccessListener { currentLocation ->
                            if (currentLocation != null) {
                                handleResolvedWeatherLocation(
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

    private fun handleResolvedWeatherLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val weatherLocation = try {
                weatherLocationResolver.resolve(latitude, longitude)
            } catch (e: Exception) {
                Log.e("LOCATION_ERROR", "地理反编码失败: ${e.message}", e)
                null
            }
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
                _currentWeather.value = "天气 Key 未配置"
                _isWeatherSyncing.value = false
                return@launch
            }

            try {
                var resolvedState: WeatherUiState? = null
                var lastError: Exception? = null

                for (queryCity in location.queryCities) {
                    try {
                        val json = weatherRemoteDataSource.fetchWeatherJson(queryCity)
                        resolvedState = WeatherStateMapper.parse(
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
        stm32DeviceSession.sendNoteJson(
            content = content,
            account = account,
            sentAt = sentAt
        )
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
