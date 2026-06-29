package com.example.healthmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.example.healthmanager.ui.components.WeeklyReportSheet
import com.example.healthmanager.ui.screens.DashboardScreen
import com.example.healthmanager.ui.screens.DeviceScreen
import com.example.healthmanager.ui.screens.DietScreen
import com.example.healthmanager.ui.screens.ExerciseCountdownScreen
import com.example.healthmanager.ui.screens.ExerciseResultScreen
import com.example.healthmanager.ui.screens.ExerciseRunningScreen
import com.example.healthmanager.ui.screens.LoginScreen
import com.example.healthmanager.ui.screens.RegisterScreen
import com.example.healthmanager.ui.screens.SleepScreen
import com.example.healthmanager.ui.theme.PrimaryTeal
import com.example.healthmanager.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 高德隐私与初始化
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        viewModel.tryAutoLogin()

        setContent {
            HealthAppMain(viewModel)
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "登录", Icons.AutoMirrored.Rounded.Login)
    object Register : Screen("register", "注册", Icons.Rounded.PersonAdd)
    object Dashboard : Screen("dashboard", "概览", Icons.Rounded.Home)
    object Diet : Screen("diet", "饮食", Icons.Rounded.Restaurant)
    object Sleep : Screen("sleep", "睡眠", Icons.Rounded.Bedtime)
    object Device : Screen("device", "设备", Icons.Rounded.Watch)
    object ExerciseCountdown : Screen("exercise_countdown", "运动倒计时", Icons.Rounded.Home)
    object ExerciseRunning : Screen("exercise_running", "运动中", Icons.Rounded.Home)
    object ExerciseResult : Screen("exercise_result", "运动结果", Icons.Rounded.Home)
}

@Composable
fun HeartRateAlertHandler(viewModel: MainViewModel) {
    val showAlert by viewModel.showHeartRateAlert.collectAsState()

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHeartRateAlert() },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.Red) },
            title = {
                Text(
                    "心率异常预警",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("检测到您的心率已持续 10 分钟超过 120bpm，请立即停止剧烈运动并原地休息。如果感到不适，请及时就医。")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissHeartRateAlert() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("我知道了", color = Color.White)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthAppMain(viewModel: MainViewModel) {
    val navController = rememberNavController()
    HeartRateAlertHandler(viewModel)

    val currentUserAccount by viewModel.currentUserAccount.collectAsState()

    val mainScreens = listOf(Screen.Dashboard, Screen.Diet, Screen.Sleep, Screen.Device)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shouldShowBottomBar = currentRoute in mainScreens.map { it.route }

    val startDestination = if (currentUserAccount != null) {
        Screen.Dashboard.route
    } else {
        Screen.Login.route
    }

    var showWeeklyReport by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var exerciseDistance by remember { mutableStateOf(0f) }
    var exerciseDuration by remember { mutableStateOf(0) }
    var exerciseCalories by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = PrimaryTeal
                ) {
                    mainScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryTeal,
                                selectedTextColor = PrimaryTeal,
                                indicatorColor = PrimaryTeal.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register")
                    }
                )
            }

            composable("register") {
                RegisterScreen(
                    viewModel = viewModel,
                    onRegisterSuccess = {
                        navController.popBackStack()
                    },
                    onBackToLogin = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onOpenWeeklyReport = { showWeeklyReport = true },
                    onStartExercise = {
                        navController.navigate(Screen.ExerciseCountdown.route)
                    }
                )
            }

            composable(Screen.ExerciseCountdown.route) {
                ExerciseCountdownScreen(
                    onCountdownFinished = {
                        navController.navigate(Screen.ExerciseRunning.route) {
                            popUpTo(Screen.ExerciseCountdown.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ExerciseRunning.route) {
                ExerciseRunningScreen(
                    onFinishExercise = { distanceKm, durationSeconds, calories ->
                        exerciseDistance = distanceKm
                        exerciseDuration = durationSeconds
                        exerciseCalories = calories

                        viewModel.saveExerciseRecord(
                            type = "户外跑步",
                            distanceKm = distanceKm,
                            durationSeconds = durationSeconds,
                            calories = calories
                        )

                        navController.navigate(Screen.ExerciseResult.route) {
                            popUpTo(Screen.ExerciseRunning.route) { inclusive = true }
                        }
                    },
                    onExitExercise = {
                        navController.popBackStack(Screen.Dashboard.route, false)
                    }
                )
            }

            composable(Screen.ExerciseResult.route) {
                ExerciseResultScreen(
                    distanceKm = exerciseDistance,
                    durationSeconds = exerciseDuration,
                    calories = exerciseCalories,
                    onBackToDashboard = {
                        navController.popBackStack(Screen.Dashboard.route, false)
                    }
                )
            }

            composable(Screen.Diet.route) {
                DietScreen(viewModel)
            }

            composable(Screen.Sleep.route) {
                SleepScreen(viewModel)
            }

            composable(Screen.Device.route) {
                DeviceScreen(
                    viewModel = viewModel,
                    onLogout = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        if (showWeeklyReport) {
            ModalBottomSheet(
                onDismissRequest = { showWeeklyReport = false },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                WeeklyReportSheet(
                    viewModel = viewModel,
                    onDismiss = { showWeeklyReport = false }
                )
            }
        }
    }
}