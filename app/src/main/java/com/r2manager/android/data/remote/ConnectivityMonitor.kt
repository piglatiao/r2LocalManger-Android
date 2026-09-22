package com.r2manager.android.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 网络连通性监听（ConnectivityManager）。
 *
 * 用于把「无网络」从普通网络错误中区分出来（对应 `ErrorType.NO_CONNECTION`）。
 */
interface ConnectivityMonitor {
    /** 当前是否在线（有可用网络且具备互联网能力）。 */
    val isOnline: StateFlow<Boolean>

    fun start()
    fun stop()
}

/**
 * 基于 [ConnectivityManager.registerDefaultNetworkCallback] 的实现（API 24+）。
 *
 * 注意：需在 `AndroidManifest.xml` 声明 `android.permission.ACCESS_NETWORK_STATE`。
 */
class ConnectivityMonitorImpl(private val context: Context) : ConnectivityMonitor {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val mutableOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mutableOnline.value = currentlyOnline()
        }

        override fun onLost(network: Network) {
            mutableOnline.value = currentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            mutableOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        override fun onUnavailable() {
            mutableOnline.value = false
        }
    }

    override fun start() {
        val manager = connectivityManager ?: return
        if (registered) {
            return
        }
        mutableOnline.value = currentlyOnline()
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
            registered = true
        }
    }

    override fun stop() {
        val manager = connectivityManager ?: return
        if (!registered) {
            return
        }
        runCatching { manager.unregisterNetworkCallback(callback) }
        registered = false
    }

    private fun currentlyOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
