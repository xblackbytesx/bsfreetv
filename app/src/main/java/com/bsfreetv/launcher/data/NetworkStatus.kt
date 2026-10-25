package com.bsfreetv.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.bsfreetv.launcher.domain.status.IpInfo
import com.bsfreetv.launcher.domain.status.IpTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * VPN state and the on-demand public IP lookup. Nothing here runs unless the
 * VPN indicator is switched on: the callback exists only while collected, and
 * the lookup only happens when the user presses the pill.
 */
@Singleton
class NetworkStatus @Inject constructor(@ApplicationContext private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    /** True while the default network goes through a VPN. */
    val vpnActive: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
        val registered = runCatching { cm?.registerDefaultNetworkCallback(callback) }.onFailure { Log.w(TAG, "no network callback", it) }.isSuccess
        awaitClose { if (registered) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** One GET to Cloudflare's trace endpoint; no cookies, short timeouts, small reply. */
    suspend fun lookUpIp(): IpInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(IpTrace.URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                conn.instanceFollowRedirects = false
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                // readNBytes is API 33; read at most MAX_BYTES by hand.
                val bytes = conn.inputStream.use { input ->
                    val buf = ByteArray(IpTrace.MAX_BYTES)
                    var n = 0
                    while (n < buf.size) {
                        val r = input.read(buf, n, buf.size - n)
                        if (r < 0) break
                        n += r
                    }
                    buf.copyOf(n)
                }
                IpTrace.parse(String(bytes, Charsets.UTF_8))
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w(TAG, "ip lookup failed", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "NetworkStatus"
        const val TIMEOUT_MS = 4_000
    }
}
