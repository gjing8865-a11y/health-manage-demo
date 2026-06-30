package com.example.healthmanager.data.remote

import org.json.JSONObject
import java.util.Locale

data class RecognizedFoodItem(
    val name: String,
    val kcal: Int,
    val icon: String,
    val carbs: Int,
    val protein: Int,
    val fat: Int
)

object FoodRecognitionResultMapper {
    fun extractSceneType(result: JSONObject): String {
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

    fun parseItems(result: JSONObject): List<RecognizedFoodItem> {
        val foodsToSave = mutableListOf<RecognizedFoodItem>()
        val foodsArray = result.optJSONArray("foods")

        if (foodsArray != null && foodsArray.length() > 0) {
            for (i in 0 until foodsArray.length()) {
                val item = foodsArray.getJSONObject(i)
                foodsToSave.add(
                    RecognizedFoodItem(
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
                    RecognizedFoodItem(
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

    fun sanitize(items: List<RecognizedFoodItem>): List<RecognizedFoodItem> {
        val invalidNames = setOf("未知食物", "食物", "菜品", "项目", "食物名称", "待确认")

        return items.mapNotNull { item ->
            val normalizedName = normalizeRecognizedFoodName(item.name)
            if (normalizedName.isBlank() || normalizedName in invalidNames) {
                return@mapNotNull null
            }

            enrichEstimate(
                RecognizedFoodItem(
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

    fun needsCoverageReview(items: List<RecognizedFoodItem>, sceneType: String): Boolean {
        if (items.isEmpty()) return true
        if (sceneType == "mixed_bowl" || sceneType == "single_dish") return false

        val hasDrink = items.any { isDrinkFoodName(it.name) }
        val hasMainFood = items.any { !isDrinkFoodName(it.name) }

        return items.size <= 2 || (hasMainFood && !hasDrink)
    }

    fun shouldRunDrinkCheck(items: List<RecognizedFoodItem>, sceneType: String): Boolean {
        if (items.any { isDrinkFoodName(it.name) }) return false
        if (sceneType == "mixed_bowl" || sceneType == "single_dish") return false
        return true
    }

    fun shouldAcceptInitialRecognition(
        items: List<RecognizedFoodItem>,
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

    fun shouldPreferCandidateFoods(
        current: List<RecognizedFoodItem>,
        candidate: List<RecognizedFoodItem>
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

    fun isDrinkFoodName(name: String): Boolean {
        val lowerName = name.lowercase(Locale.getDefault())
        return listOf("奶茶", "茶饮", "果茶", "果汁", "饮料", "汽水", "可乐", "咖啡", "柠檬茶")
            .any { it in name } || listOf("tea", "coffee", "cola", "drink", "juice").any { it in lowerName }
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

    private fun enrichEstimate(item: RecognizedFoodItem): RecognizedFoodItem {
        val name = item.name

        fun withFallback(
            kcal: Int,
            carbs: Int,
            protein: Int,
            fat: Int
        ): RecognizedFoodItem {
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

    private fun isGenericFoodName(name: String): Boolean {
        return name in setOf("饮料", "食物", "菜品", "主食", "小吃", "酱料")
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
}
