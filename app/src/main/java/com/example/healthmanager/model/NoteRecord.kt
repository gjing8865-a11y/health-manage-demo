package com.example.healthmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userAccount: String,   // 属于哪个账号
    val content: String,       // 便签内容
    val createdAt: Long        // 创建时间戳
)