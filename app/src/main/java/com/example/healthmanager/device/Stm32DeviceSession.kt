package com.example.healthmanager.device

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.SocketFactory

data class Stm32DeviceSessionConfig(
    val connectTimeoutMs: Int = 2_500,
    val readTimeoutMs: Int = 1_200,
    val firstPacketTimeoutMs: Long = 20_000L,
    val idleReconnectMs: Long = 15_000L
)

class Stm32DeviceSession(
    private val httpClient: OkHttpClient,
    private val socketFactoryProvider: () -> SocketFactory? = { null },
    private val config: Stm32DeviceSessionConfig = Stm32DeviceSessionConfig()
) {
    private var activeSocket: Socket? = null
    private val socketWriteLock = Any()

    fun close() {
        runCatching { activeSocket?.close() }
        activeSocket = null
    }

    suspend fun listenPayloads(
        urls: List<String>,
        isConnected: () -> Boolean,
        onTcpWaiting: suspend () -> Unit,
        onPayload: suspend (Stm32DevicePayload) -> Unit
    ) {
        var lastError: Exception? = null
        val attemptedUrls = mutableListOf<String>()

        for (url in urls) {
            attemptedUrls += url
            try {
                if (url.startsWith("tcp://")) {
                    val uri = URI(url)
                    val host = uri.host ?: throw IllegalStateException("TCP 地址缺少 host: $url")
                    listenRawTcpPayloads(
                        host = host,
                        port = if (uri.port > 0) uri.port else Stm32EndpointResolver.DEFAULT_TCP_PORT,
                        isConnected = isConnected,
                        onTcpWaiting = onTcpWaiting,
                        onPayload = onPayload
                    )
                    return
                } else {
                    onPayload(readPayloadFromUrl(url))
                    return
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw IllegalStateException(
            "没有发现可返回 JSON 的设备接口，已尝试 ${attemptedUrls.take(12).joinToString()}",
            lastError
        )
    }

    suspend fun sendNoteJson(
        content: String,
        account: String,
        sentAt: Long
    ) {
        val socket = awaitWritableSocket()
        val noteJson = JSONObject().apply {
            put("type", "note")
            put("content", content)
            put("account", account)
            put("timestamp", sentAt)
        }.toString() + "\r\n"

        synchronized(socketWriteLock) {
            if (socket.isClosed || !socket.isConnected) {
                throw IllegalStateException("STM32 TCP 通道已断开")
            }
            val output = socket.getOutputStream()
            output.write(noteJson.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    private fun readPayloadFromUrl(url: String): Stm32DevicePayload {
        val request = Request.Builder()
            .url(url)
            .build()

        val clientForNetwork = socketFactoryProvider()?.let { socketFactory ->
            httpClient.newBuilder()
                .socketFactory(socketFactory)
                .build()
        } ?: httpClient

        clientForNetwork.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }

            if (body.isBlank()) {
                throw IllegalStateException("设备返回空数据")
            }

            val json = runCatching { JSONObject(body) }.getOrElse {
                throw IllegalStateException("设备接口未返回 JSON，地址: $url")
            }
            return Stm32PayloadParser.parse(json)
        }
    }

    private suspend fun listenRawTcpPayloads(
        host: String,
        port: Int,
        isConnected: () -> Boolean,
        onTcpWaiting: suspend () -> Unit,
        onPayload: suspend (Stm32DevicePayload) -> Unit
    ) {
        val rawSocket = socketFactoryProvider()?.createSocket() ?: Socket()
        activeSocket = rawSocket

        rawSocket.use { socket ->
            socket.connect(InetSocketAddress(host, port), config.connectTimeoutMs)
            socket.soTimeout = config.readTimeoutMs

            val input = socket.getInputStream()
            var hasReceivedPacket = false
            val firstPacketDeadline = System.currentTimeMillis() + config.firstPacketTimeoutMs
            var lastPacketAt = System.currentTimeMillis()

            onTcpWaiting()

            while (currentCoroutineContext().isActive && isConnected()) {
                val rawText = try {
                    readSocketText(input)
                } catch (e: SocketTimeoutException) {
                    if (!hasReceivedPacket && System.currentTimeMillis() > firstPacketDeadline) {
                        throw IllegalStateException("20 秒内没有收到手环推送的 JSON")
                    }
                    if (hasReceivedPacket && System.currentTimeMillis() - lastPacketAt > config.idleReconnectMs) {
                        throw IllegalStateException("连续 15 秒没有收到新的心率推送")
                    }
                    continue
                }

                val jsonObjects = Stm32JsonStreamExtractor.extractJsonObjects(rawText)
                if (jsonObjects.isEmpty()) {
                    continue
                }

                hasReceivedPacket = true
                lastPacketAt = System.currentTimeMillis()
                for (jsonText in jsonObjects) {
                    onPayload(Stm32PayloadParser.parse(jsonText))
                }
            }
        }
    }

    private fun readSocketText(input: InputStream): String {
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()

        while (true) {
            val length = try {
                input.read(buffer)
            } catch (e: SocketTimeoutException) {
                if (output.size() > 0) break else throw e
            }

            if (length <= 0) break

            output.write(buffer, 0, length)
            val text = output.toString(Charsets.UTF_8.name())
            if (Stm32JsonStreamExtractor.extractJsonObjects(text).isNotEmpty()) {
                return text
            }
        }

        if (output.size() <= 0) {
            throw IllegalStateException("TCP 未返回数据")
        }

        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun awaitWritableSocket(): Socket {
        repeat(20) {
            val socket = activeSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                return socket
            }
            kotlinx.coroutines.delay(100)
        }
        throw IllegalStateException("STM32 数据通道未建立，请先保持设备连接并等待 TCP 通道连上")
    }
}
