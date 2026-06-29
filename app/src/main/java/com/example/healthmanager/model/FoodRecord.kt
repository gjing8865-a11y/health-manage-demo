package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_records")
data class FoodRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userAccount: String,   // 属于哪个账号
    val name: String,          // 食物名称
    val kcal: Int,             // 热量
    val icon: String,          // emoji / 图标
    val time: String,          // 时间，如 "08:30"
    val date: String,          // 日期，如 "3月22日"
    val mealType: String,      // 早餐 / 午餐 / 晚餐 / 加餐

    val carbs: Int = 0,        // 碳水(g)
    val protein: Int = 0,      // 蛋白质(g)
    val fat: Int = 0           // 脂肪(g)
)