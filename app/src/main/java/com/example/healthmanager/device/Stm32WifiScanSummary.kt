package com.example.healthmanager.device

object Stm32WifiScanSummary {
    fun countLikelyDeviceHotspots(accessPoints: List<WifiAccessPoint>): Int {
        return accessPoints.count { accessPoint ->
            Stm32WifiHotspotPolicy.isLikelyStm32HotspotName(accessPoint.ssid)
        }
    }

    fun buildMessage(
        accessPoints: List<WifiAccessPoint>,
        fromCache: Boolean
    ): String {
        val possibleDeviceCount = countLikelyDeviceHotspots(accessPoints)

        return when {
            accessPoints.isEmpty() -> "没有扫描到任何热点，请打开手机 Wi-Fi 和定位服务，并确认 STM32 已开启热点模式；如果 STM32 是隐藏热点，可以手动输入热点名连接。"
            possibleDeviceCount == 0 && fromCache -> "系统限制了本次主动扫描，已展示最近一次扫描结果。若 STM32 仍不在列表中，可直接手动输入热点名连接。"
            possibleDeviceCount == 0 -> "已扫描到 ${accessPoints.size} 个热点，但没有明显的 STM32 热点，请检查 STM32 热点名称，或直接手动输入热点名连接。"
            fromCache -> "系统限制了本次主动扫描，已展示最近一次扫描结果，并发现 $possibleDeviceCount 个疑似设备热点。"
            else -> "已发现 $possibleDeviceCount 个疑似设备热点，点击列表项即可尝试连接。"
        }
    }
}
