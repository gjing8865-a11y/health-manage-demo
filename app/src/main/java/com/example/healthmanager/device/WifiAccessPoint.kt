package com.example.healthmanager.device

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val level: Int,
    val capabilities: String
)
