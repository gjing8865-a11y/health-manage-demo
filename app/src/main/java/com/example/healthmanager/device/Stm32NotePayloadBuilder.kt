package com.example.healthmanager.device

import org.json.JSONObject

object Stm32NotePayloadBuilder {
    fun build(
        content: String,
        account: String,
        sentAt: Long
    ): String {
        return JSONObject().apply {
            put("type", "note")
            put("content", content)
            put("account", account)
            put("timestamp", sentAt)
        }.toString() + "\r\n"
    }
}
