package com.example.healthmanager.model

/**
 * 1. 饮食记录模型
 * 用于存储每一餐的详细信息
 */
data class FoodItem(
    val id: Int,
    val name: String,      // 食物名称，如 "燕麦牛奶"
    val kcal: Int,         // 卡路里
    val time: String,      // 进食时间，如 "08:30"
    val icon: String,      // 图标 (Emoji) 或 图片 URL
    val description: String = "" // AI 识别后的简短描述
)

/**
 * 2. 睡眠数据模型
 * 用于存储单日的睡眠分析结果
 */
data class SleepSession(
    val date: String,       // 日期 "2023-10-27"
    val score: Int,         // 睡眠评分 0-100
    val totalDuration: Int, // 总时长 (分钟)
    val deepSleep: Int,     // 深睡时长 (分钟)
    val lightSleep: Int,    // 浅睡时长 (分钟)
    val awake: Int,         // 清醒时长 (分钟)
    val stageTrend: List<Int> // 睡眠阶段趋势数组 (用于画柱状图 0-10)
)

/**
 * 3. 每日健康统计模型
 * 用于首页仪表盘展示
 */
data class DailyHealthStat(
    val steps: Int,
    val targetSteps: Int,
    val heartRate: Int,     // 最新一次心率
    val spO2: Int,          // 最新一次血氧
    val caloriesBurned: Int // 已消耗卡路里
)

/**
 * 4. 蓝牙设备信息模型
 * 用于设备管理页
 */
data class BleDevice(
    val name: String,
    val address: String,    // MAC 地址
    val rssi: Int,          // 信号强度
    val isConnected: Boolean = false,
    val batteryLevel: Int = 0
)

/**
 * 5. 用户设置模型
 * 用于存储用户目标和身体参数
 */
data class UserProfile(
    val name: String,
    val age: Int,
    val height: Float,      // cm
    val weight: Float,      // kg
    val gender: String      // "Male", "Female"
)