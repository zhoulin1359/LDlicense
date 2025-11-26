package com.lingdong.android.ldlicense.util

import android.content.Context
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

class WifiConnector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 保存已连接的 network
    private var connectedNetwork: Network? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun connectSuspend(
        ssid: String,
        password: String? = null,
        timeoutMs: Long = 15_000
    ): Boolean = withContext(Dispatchers.Main) {

        // 校验密码 ASCII
        val asciiPassword = password?.filter { it.code in 32..126 }
        if (password.isNullOrEmpty()) {
            throw IllegalArgumentException("WiFi 密码必须为 ASCII 字符")
        }

        // 超时包装
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->

                // 解绑上一次 callback
               /* networkCallback?.let {
                    try {
                        connectivityManager.unregisterNetworkCallback(it)
                    } catch (_: Exception) {
                    }
                }*/

                // 构建 WiFi 连接请求
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .apply {
                        if (!asciiPassword.isNullOrEmpty()) {
                            setWpa2Passphrase(asciiPassword)
                        }
                    }
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                var callbackCalled = false

                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (callbackCalled) return
                        callbackCalled = true
                        connectedNetwork = network
                        if (cont.isActive) cont.resumeWith(Result.success(true))
                    }

                    override fun onUnavailable() {
                        if (callbackCalled) return
                        callbackCalled = true
                        if (cont.isActive) cont.resumeWith(Result.success(false))
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        // 已连接后断开不影响当前流程
                    }
                }

                networkCallback = cb
                connectivityManager.requestNetwork(request, cb)

                // 当协程取消时，释放 callback
                cont.invokeOnCancellation {
                    try {
                        connectivityManager.unregisterNetworkCallback(cb)
                    } catch (_: Exception) {
                    }
                }
            } ?: false
        } ?: false
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    fun connect(
        ssid: String,
        password: String? = null,
        onConnected: (success: Boolean, msg: String?) -> Unit
    ) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply {
                if (!password.isNullOrEmpty()) {
                    setWpa2Passphrase(password)
                }
            }
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectedNetwork = network    // <-- 关键：保存 network
                onConnected(true, "Connected")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                onConnected(false, "连接失败，请重试或者检查密码是否正确")
            }
        }

        connectivityManager.requestNetwork(request, networkCallback!!)
    }


    suspend fun sendRequestSuspend(
        url: String,
        method: String = "GET",
        timeout: Int = 6000
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {

        val network = connectedNetwork
        if (network == null) {
            return@withContext false to "没有连接到WiFi网络"
        }

        return@withContext try {
            val conn = network.openConnection(URL(url)) as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = method

            conn.connect()

            val statusCode = conn.responseCode

            val body = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                "错误状态码$statusCode: ${conn.responseMessage}"
            }

            (statusCode <= 299) to body

        } catch (e: Exception) {
            false to e.message
        }
    }


    fun sendRequestSync(
        url: String,
        method: String = "GET",
        timeout: Int = 6000
    ): Pair<Boolean, String?> {

        val network = connectedNetwork
            ?: return false to "没有连接到WiFi网络"

        return try {
            Log.d("WifiConnector", "sendRequestSync: $url")

            val conn = network.openConnection(URL(url)) as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = method
            conn.connect()

            val statusCode = conn.responseCode

            val resp = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                "错误状态码$statusCode: ${conn.responseMessage}"
            }

            (statusCode in 200..299) to resp

        } catch (e: Exception) {
            Log.e("WifiConnector", "sendRequestSync error", e)
            false to e.message
        }
    }


    /**
     * 复用 network 发送多次请求
     */
    fun sendRequest(
        url: String,
        method: String = "GET",
        timeout: Int = 6000,
        onResult: (success: Boolean, result: String?) -> Unit
    ) {
        val network = connectedNetwork
        if (network == null) {
            onResult(false, "没有连接到WiFi网络")
            return
        }
        Log.d("WifiConnector", "sendRequest: $url")

        Thread {
            try {
                val conn = network.openConnection(URL(url)) as HttpURLConnection
                conn.connectTimeout = timeout
                conn.readTimeout = timeout
                conn.requestMethod = method

                conn.connect()  // 建议手动触发连接

                val statusCode = conn.responseCode     // <-- 这里拿状态码

                val resp = if (statusCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    //conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    "错误状态码$statusCode: ${conn.responseMessage}"
                }

                onResult(statusCode <= 299, resp)
            } catch (e: Exception) {
                Log.e("WifiConnector", "sendRequest: ${e.printStackTrace()}")
                onResult(false, e.message)
            }
        }.start()
    }

    fun release() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        connectedNetwork = null
    }
}

