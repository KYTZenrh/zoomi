package com.zoomi.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * True when at least one network has validated internet access (not just "connected to Wi-Fi").
 *
 * Tracks networks individually from the callbacks. Re-reading `activeNetwork` inside `onLost` is
 * unreliable (it can still return the network being lost), which made airplane mode look online.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val validated = ConcurrentHashMap.newKeySet<Network>()
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private fun usable(c: NetworkCapabilities) =
        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun publish() { _online.value = validated.isNotEmpty() }

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (usable(caps)) validated.add(network) else validated.remove(network)
                publish()
            }
            override fun onLost(network: Network) {
                validated.remove(network)
                publish()
            }
        })
    }
}
