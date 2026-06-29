package com.example.healthmanager.ui.screens

import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.healthmanager.model.FoodRecord
import com.example.healthmanager.ui.components.HealthCard
import com.example.healthmanager.ui.theme.*
import com.example.healthmanager.viewmodel.DailyKcalStat
import com.example.healthmanager.viewmodel.MainViewModel
import androidx.compose.foundation.combinedClickable
import java.io.File

private const val DEFAULT_DAILY_KCAL_TARGET = 1800
private const val DEFAULT_CARBS_TARGET = 225
private const val DEFAULT_PROTEIN_TARGET = 90
private const val DEFAULT_FAT_TARGET = 60

@Composable
fun DietScreen(viewModel: MainViewModel) {
    val foods by viewModel.foodList.collectAsState()
    val todayTotalKcal by viewModel.todayTotalKcal.collectAsState()
    val dailyKcalStats by viewModel.dailyKcalStats.collectAsState()
    val todayCarbs by viewModel.todayCarbs.collectAsState()
    val todayProtein by viewModel.todayProtein.collectAsState()
    val todayFat by viewModel.todayFat.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val showErrorDialog by viewModel.showErrorDialog.collectAsState()
    val selectedMealType by viewModel.selectedMealType.collectAsState()
    val pendingFoods by viewModel.pendingFoods.collectAsState()
    val showRecognitionConfirmDialog by viewModel.showRecognitionConfirmDialog.collectAsState()
    val isDietDeleteMode by viewModel.isDietDeleteMode.collectAsState()

    val context = LocalContext.current
    var showSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                runCatching { decodeBitmapFromUri(context, uri) }
                    .onSuccess(viewModel::analyzeFoodImage)
                    .onFailure { it.printStackTrace() }
            }
        }
        pendingCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    decodeBitmapFromUri(context, it)
                }
                viewModel.analyzeFoodImage(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    val groupedFoods = remember(foods) {
        foods.groupBy { it.date }
            .toList()
            .sortedByDescending { parseMonthDayToSortableKey(it.first) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 100.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "饮食管理",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isDietDeleteMode) {
                    TextButton(onClick = { viewModel.exitDietDeleteMode() }) {
                        Text("完成", color = PrimaryTeal)
                    }
                }
            }

            HealthCard(color = PrimaryTeal) {
                Text("今日已摄入", color = Color.White.copy(0.8f), fontSize = 14.sp)
                Text(
                    "$todayTotalKcal kcal",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "距离通用上限还剩 ${(DEFAULT_DAILY_KCAL_TARGET - todayTotalKcal).coerceAtLeast(0)} kcal",
                    color = Color.White.copy(0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            HealthCard {
                Text("近 7 天热量趋势", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))

                if (dailyKcalStats.isEmpty()) {
                    Text("暂无历史热量数据", color = TextSecondary, fontSize = 13.sp)
                } else {
                    DailyKcalBarChart(dailyKcalStats)
                }
            }

            Spacer(Modifier.height(16.dp))

            HealthCard {
                Text("营养分布", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(16.dp))

                NutritionRow("碳水", todayCarbs, DEFAULT_CARBS_TARGET, Color(0xFF60A5FA))
                Spacer(Modifier.height(12.dp))
                NutritionRow("蛋白质", todayProtein, DEFAULT_PROTEIN_TARGET, Color(0xFF34D399))
                Spacer(Modifier.height(12.dp))
                NutritionRow("脂肪", todayFat, DEFAULT_FAT_TARGET, Color(0xFFF59E0B))
            }

            Spacer(Modifier.height(16.dp))

            HealthCard {
                Text("快捷记录", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前记录餐次：$selectedMealType",
                    fontSize = 12.sp,
                    color = PrimaryTeal,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickMealChip(
                        text = "早餐",
                        selected = selectedMealType == "早餐",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectMealType("早餐") }
                    )
                    QuickMealChip(
                        text = "午餐",
                        selected = selectedMealType == "午餐",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectMealType("午餐") }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickMealChip(
                        text = "晚餐",
                        selected = selectedMealType == "晚餐",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectMealType("晚餐") }
                    )
                    QuickMealChip(
                        text = "加餐",
                        selected = selectedMealType == "加餐",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectMealType("加餐") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("饮食记录", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            if (foods.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☕", fontSize = 60.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("今天还没有记录呢", color = TextSecondary, fontSize = 16.sp)
                        Text(
                            "先点击上方餐次，再点右下角相机识别美食",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                groupedFoods.forEach { (date, dateFoods) ->
                    Text(
                        text = date,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val mealOrder = listOf("早餐", "午餐", "晚餐", "加餐")

                    mealOrder.forEach { mealType ->
                        val mealFoods = dateFoods.filter { it.mealType == mealType }
                        if (mealFoods.isNotEmpty()) {
                            MealSectionCard(
                                mealType = mealType,
                                foods = mealFoods,
                                isDeleteMode = isDietDeleteMode,
                                onLongPress = { viewModel.enterDietDeleteMode() },
                                onDeleteFood = { food -> viewModel.deleteFoodRecord(food) }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (isAnalyzing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.3f)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("AI 正在努力识别食物...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                title = { Text("识别失败") },
                text = { Text("AI 没有识别清楚这份食物，要再试一次吗？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.retryAnalysis() }) {
                        Text("重试", color = PrimaryTeal)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        if (showRecognitionConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.clearPendingFoods() },
                title = { Text("确认识别结果") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "以下是本次识别结果，请确认后再保存：",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        pendingFoods.forEach { item ->
                            PendingFoodRow(
                                item = item,
                                onRemove = { viewModel.removePendingFood(item.id) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        if (pendingFoods.isEmpty()) {
                            Text(
                                text = "没有可保存的识别结果",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.confirmSavePendingFoods() },
                        enabled = pendingFoods.isNotEmpty()
                    ) {
                        Text("确认保存", color = PrimaryTeal)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearPendingFoods() }) {
                        Text("取消", color = TextSecondary)
                    }
                }
            )
        }

        FloatingActionButton(
            onClick = {
                if (!isAnalyzing) {
                    showSourceDialog = true
                }
            },
            containerColor = TextPrimary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(64.dp)
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = "识别食物",
                modifier = Modifier.size(28.dp)
            )
        }

        if (showSourceDialog) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("识别美食", fontWeight = FontWeight.Bold) },
                text = { Text("当前将记录到：$selectedMealType\n请选择获取图片的方式") },
                confirmButton = {
                    TextButton(onClick = {
                        showSourceDialog = false
                        pendingCameraUri = createTempCameraUri(context)
                        pendingCameraUri?.let(cameraLauncher::launch)
                    }) {
                        Text("现在拍摄", color = PrimaryTeal)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("从相册选择", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun DailyKcalBarChart(stats: List<DailyKcalStat>) {
    val maxKcal = (stats.maxOfOrNull { it.totalKcal } ?: 1).coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEach { stat ->
            val ratio = (stat.totalKcal.toFloat() / maxKcal).coerceIn(0f, 1f)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "${stat.totalKcal}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((ratio * 100).coerceAtLeast(8f).dp)
                        .background(
                            PrimaryTeal.copy(alpha = 0.85f),
                            RoundedCornerShape(8.dp)
                        )
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stat.date,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NutritionRow(
    label: String,
    value: Int,
    target: Int,
    barColor: Color
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp)
            Text("$value / $target g", color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { if (target == 0) 0f else (value.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = BgLight
        )
    }
}

@Composable
private fun QuickMealChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PrimaryTeal.copy(alpha = 0.14f) else BgLight
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) PrimaryTeal else TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MealSectionCard(
    mealType: String,
    foods: List<FoodRecord>,
    isDeleteMode: Boolean,
    onLongPress: () -> Unit,
    onDeleteFood: (FoodRecord) -> Unit
) {
    HealthCard(
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = onLongPress
        )
    ) {
        Text(
            text = mealType,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(12.dp))

        foods.forEachIndexed { index, food ->
            FoodRecordRow(
                food = food,
                isDeleteMode = isDeleteMode,
                onDelete = { onDeleteFood(food) }
            )
            if (index != foods.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FoodRecordRow(
    food: FoodRecord,
    isDeleteMode: Boolean,
    onDelete: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FoodIconBadge(name = food.name, rawIcon = food.icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "${food.time} · ${food.mealType}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                if (food.carbs > 0 || food.protein > 0 || food.fat > 0) {
                    Text(
                        "碳水 ${food.carbs}g  蛋白 ${food.protein}g  脂肪 ${food.fat}g",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Text("+${food.kcal}", fontWeight = FontWeight.Bold, color = PrimaryTeal)
        }

        if (isDeleteMode) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("✕", color = Color.Red)
            }
        }
    }
}

@Composable
private fun PendingFoodRow(
    item: com.example.healthmanager.viewmodel.PendingFoodItem,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgLight),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FoodIconBadge(name = item.name, rawIcon = item.icon)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "热量 ${item.kcal} kcal",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    "碳水 ${item.carbs}g  蛋白 ${item.protein}g  脂肪 ${item.fat}g",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            TextButton(onClick = onRemove) {
                Text("删除", color = Color.Red)
            }
        }
    }
}

@Composable
private fun FoodIconBadge(name: String, rawIcon: String) {
    val visual = remember(name, rawIcon) { foodVisualStyle(name, rawIcon) }

    Surface(
        shape = CircleShape,
        color = visual.background,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private data class FoodVisualStyle(
    val icon: ImageVector,
    val tint: Color,
    val background: Color
)

private fun foodVisualStyle(name: String, rawIcon: String): FoodVisualStyle {
    val text = "$name $rawIcon"
    return when {
        listOf("寿司", "手卷", "紫菜包饭", "饭团", "卷").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.LunchDining, Color(0xFF2563EB), Color(0xFFDBEAFE))

        listOf("拉面", "面", "粉", "米线").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.RamenDining, Color(0xFFEA580C), Color(0xFFFFEDD5))

        listOf("咖啡", "拿铁", "美式").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.LocalCafe, Color(0xFF92400E), Color(0xFFFEF3C7))

        listOf("奶茶", "茶饮", "果汁", "饮料", "汽水", "可乐").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.LocalDrink, Color(0xFF0284C7), Color(0xFFE0F2FE))

        listOf("蛋", "煎蛋", "荷包蛋").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.EggAlt, Color(0xFFF59E0B), Color(0xFFFEF3C7))

        listOf("蛋糕", "面包", "华夫", "甜甜圈", "糕点").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.BakeryDining, Color(0xFFD97706), Color(0xFFFEF3C7))

        listOf("汉堡", "薯条", "炸鸡", "披萨", "快餐", "热狗", "香肠", "玉米狗").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.Fastfood, Color(0xFFDC2626), Color(0xFFFEE2E2))

        listOf("饭", "丼", "盖饭", "拌饭", "便当", "牛肉饭").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.LunchDining, Color(0xFF059669), Color(0xFFD1FAE5))

        listOf("蟹", "虾", "鱼", "扇贝", "生蚝", "蛤蜊", "青口", "海鲜").any { it in text } ->
            FoodVisualStyle(Icons.Rounded.Restaurant, Color(0xFFE11D48), Color(0xFFFFE4E6))

        else ->
            FoodVisualStyle(Icons.Rounded.Restaurant, TextPrimary, BgLight)
    }
}

private fun createTempCameraUri(context: android.content.Context): Uri? {
    return runCatching {
        val directory = File(context.cacheDir, "food_camera").apply { mkdirs() }
        val imageFile = File.createTempFile("food_capture_", ".jpg", directory)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }.getOrNull()
}

private fun decodeBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT < 28) {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    } else {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}

private fun parseMonthDayToSortableKey(date: String): Int {
    val regex = Regex("""(\d+)月(\d+)日""")
    val match = regex.find(date) ?: return 0
    val month = match.groupValues[1].toIntOrNull() ?: 0
    val day = match.groupValues[2].toIntOrNull() ?: 0
    return month * 100 + day
}
