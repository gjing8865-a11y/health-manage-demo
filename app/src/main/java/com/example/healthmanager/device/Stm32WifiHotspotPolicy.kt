package com.example.healthmanager.device

object Stm32WifiHotspotPolicy {
    @JvmStatic
    fun normalizeSsid(rawSsid: String?): String {
        return rawSsid
            .orEmpty()
            .trim()
            .trim('"')
            .takeUnless { it.isBlank() || it == "<unknown ssid>" }
            .orEmpty()
    }

    @JvmStatic
    fun isLikelyStm32HotspotName(ssid: String): Boolean {
        return ssid.contains("HRB", ignoreCase = true) ||
                ssid.contains("STM32", ignoreCase = true) ||
                ssid.contains("SmartBand", ignoreCase = true) ||
                ssid.contains("AI-THINKER", ignoreCase = true) ||
                ssid.contains("ESP", ignoreCase = true)
    }
}
