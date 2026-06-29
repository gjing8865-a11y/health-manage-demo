package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val account: String,              // 账号唯一

    val password: String,             // 密码

    val nickName: String = "新用户",   // 昵称

    val avatarUri: String = "",       // 头像本地 Uri / 路径

    val signature: String = "保持活力!" // 个性签名
)