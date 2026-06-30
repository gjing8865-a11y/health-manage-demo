package com.example.healthmanager.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.healthmanager.device.Stm32EndpointResolver
import com.example.healthmanager.device.Stm32WifiHotspotPolicy
import com.example.healthmanager.device.WifiAccessPoint

class WifiPlatformGateway(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiEnabled: Boolean
        get() = wifiManager.isWifiEnabled

    fun linkGatewayHosts(network: Network?): List<String> {
        return network
            ?.let { connectivityManager.getLinkProperties(it) }
            ?.routes
            ?.mapNotNull { route -> route.gateway?.hostAddress }
            .orEmpty()
    }

    @Suppress("DEPRECATION")
    fun dhcpGatewayHost(): String? {
        val gateway = wifiManager.dhcpInfo?.gateway ?: 0
        return gateway.takeIf { it != 0 }?.let(Stm32EndpointResolver::formatIpv4Address)
    }

    @Suppress("DEPRECATION")
    fun currentSsid(): String {
        return Stm32WifiHotspotPolicy.normalizeSsid(wifiManager.connectionInfo?.ssid)
    }

    @Suppress("DEPRECATION")
    fun activeWifiNetwork(): Network? {
        return connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    fun bindProcessToNetwork(network: Network?) {
        connectivityManager.bindProcessToNetwork(network)
    }

    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(callback)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun requestNetwork(
        request: NetworkRequest,
        callback: ConnectivityManager.NetworkCallback,
        timeoutMillis: Int
    ) {
        connectivityManager.requestNetwork(request, callback, timeoutMillis)
    }

    fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyWifiGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocationGranted && nearbyWifiGranted
    }

    fun isLocationServiceEnabled(): Boolean {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        return runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun cachedAccessPoints(): List<WifiAccessPoint> {
        return buildAccessPoints(wifiManager.scanResults.orEmpty())
    }

    fun buildAccessPoints(results: List<ScanResult>): List<WifiAccessPoint> {
        return results
            .mapNotNull { scanResult ->
                val ssid = readScanResultSsid(scanResult)
                    .takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    ?: return@mapNotNull null
                WifiAccessPoint(
                    ssid = ssid,
                    bssid = scanResult.BSSID ?: "",
                    level = scanResult.level,
                    capabilities = scanResult.capabilities ?: ""
                )
            }
            .distinctBy { it.ssid }
            .sortedByDescending { it.level }
    }

    fun registerScanReceiver(receiver: BroadcastReceiver) {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterScanReceiver(receiver: BroadcastReceiver) {
        appContext.unregisterReceiver(receiver)
    }

    @Suppress("DEPRECATION")
    fun requestScan(): Boolean {
        return wifiManager.startScan()
    }

    @Suppress("DEPRECATION")
    private fun readScanResultSsid(scanResult: ScanResult): String {
        return scanResult.SSID
    }
}
