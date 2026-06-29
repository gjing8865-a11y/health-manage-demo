package com.example.healthmanager.Manage

import android.util.Log
import com.example.healthmanager.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiDeviceManager(
    private val viewModel: MainViewModel
) {

    var onConnectionChanged: ((Boolean) -> Unit)? = null
    var onDataReceived: ((ByteArray) -> Unit)? = null

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(host: String, port: Int) {
        disconnect()

        scope.launch {
            try {
                val newSocket = Socket()
                newSocket.connect(InetSocketAddress(host, port), 5000)

                socket = newSocket
                inputStream = newSocket.getInputStream()

                Log.d("WIFI", "连接成功: $host:$port")
                onConnectionChanged?.invoke(true)

                startReading()
            } catch (e: Exception) {
                Log.e("WIFI", "连接失败: ${e.message}", e)
                onConnectionChanged?.invoke(false)
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val buffer = ByteArray(1024)
                while (isActive) {
                    val len = inputStream?.read(buffer) ?: -1
                    if (len <= 0) {
                        Log.d("WIFI", "连接已断开或无数据")
                        break
                    }

                    val data = buffer.copyOf(len)
                    Log.d("WIFI", "收到数据: ${data.joinToString(" ") { "%02X".format(it) }}")
                    onDataReceived?.invoke(data)
                }
            } catch (e: Exception) {
                Log.e("WIFI", "读取数据异常: ${e.message}", e)
            } finally {
                onConnectionChanged?.invoke(false)
                disconnectInternal()
            }
        }
    }

    fun send(data: ByteArray) {
        scope.launch {
            try {
                socket?.getOutputStream()?.write(data)
                socket?.getOutputStream()?.flush()
                Log.d("WIFI", "发送数据成功")
            } catch (e: Exception) {
                Log.e("WIFI", "发送数据失败: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            onConnectionChanged?.invoke(false)
        }
    }

    private fun disconnectInternal() {
        try {
            readJob?.cancel()
            readJob = null

            inputStream?.close()
            inputStream = null

            socket?.close()
            socket = null

            Log.d("WIFI", "已断开连接")
        } catch (e: Exception) {
            Log.e("WIFI", "断开连接异常: ${e.message}", e)
        }
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}