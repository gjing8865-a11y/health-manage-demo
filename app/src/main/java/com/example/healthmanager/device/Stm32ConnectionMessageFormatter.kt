package com.example.healthmanager.device

import java.net.SocketTimeoutException

object Stm32ConnectionMessageFormatter {
    fun formatError(error: Exception): String {
        val message = error.message.orEmpty()

        return when {
            message.contains("CLEARTEXT", ignoreCase = true) -> "系统拦截了本地 HTTP，请安装新版后重试"
            message.contains("20 秒内没有收到手环推送", ignoreCase = true) -> "20 秒内没有收到手环推送的有效 JSON；请确认手环停留在心率页、已点击 Survey，并且 App 已连接 HRB_AP"
            message.contains("连续 15 秒没有收到新的心率推送", ignoreCase = true) -> "已收到过数据，但后续 15 秒没有新推送；App 正在重连数据通道"
            error is SocketTimeoutException -> "连接 ESP-01S TCP 数据端口超时，请确认单片机已启动 AT+CIPSERVER=1,8080"
            error.cause is SocketTimeoutException -> "热点连上了，但 ESP-01S 没有主动推送 JSON；请确认手环心率页正在 Survey 测量"
            message.contains("没有发现可返回 JSON", ignoreCase = true) -> message
            message.contains("ECONNREFUSED", ignoreCase = true) -> "热点连上了，但 ESP-01S 的 TCP 8080 数据端口没有打开"
            message.isNotBlank() -> message
            else -> error::class.java.simpleName
        }
    }

    fun summarizeAttempts(urls: List<String>, limit: Int = 10): String {
        val visibleUrls = urls.take(limit).joinToString()
        return if (urls.size > limit) {
            "$visibleUrls 等 ${urls.size} 个地址"
        } else {
            visibleUrls
        }
    }
}
