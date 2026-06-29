package com.example.healthmanager.device

import java.net.URI
import java.util.Locale

object Stm32EndpointResolver {
    const val MAX_CANDIDATE_HOSTS = 4
    const val DEFAULT_HOST = "192.168.4.1"
    const val DEFAULT_ENDPOINT = "tcp://192.168.4.1:8080"
    const val DEFAULT_TCP_PORT = 8080

    private val fallbackHosts = listOf(DEFAULT_HOST)

    fun buildDataUrls(
        rawManualEndpoint: String,
        discoveredHosts: List<String>
    ): List<String> {
        val manualEndpoint = normalizeEndpoint(rawManualEndpoint)
        val tcpEndpoints = (fallbackHosts + discoveredHosts)
            .map { it.trim() }
            .filter(::isUsableIpv4Host)
            .distinct()
            .take(MAX_CANDIDATE_HOSTS)
            .map { host -> "tcp://$host:$DEFAULT_TCP_PORT" }

        return (listOfNotNull(manualEndpoint) + tcpEndpoints)
            .distinct()
    }

    fun normalizeEndpoint(rawEndpoint: String): String? {
        val endpoint = rawEndpoint.trim().trimEnd('/')
        if (endpoint.isBlank()) return null

        val lowerEndpoint = endpoint.lowercase(Locale.ROOT)
        val withScheme = when {
            lowerEndpoint.startsWith("tcp://") ||
                lowerEndpoint.startsWith("http://") ||
                lowerEndpoint.startsWith("https://") -> endpoint

            endpoint.contains("/") -> "http://$endpoint"
            else -> "tcp://$endpoint"
        }

        if (withScheme.startsWith("tcp://", ignoreCase = true)) {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else DEFAULT_TCP_PORT
            return "tcp://$host:$port"
        }

        return if (URI(withScheme).path.isNullOrBlank()) {
            "$withScheme/data"
        } else {
            withScheme
        }
    }

    fun formatIpv4Address(address: Int): String {
        return listOf(
            address and 0xff,
            address shr 8 and 0xff,
            address shr 16 and 0xff,
            address shr 24 and 0xff
        ).joinToString(".")
    }

    fun isUsableIpv4Host(host: String): Boolean {
        if (host == "0.0.0.0") return false

        val parts = host.split(".")
        return parts.size == 4 && parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
