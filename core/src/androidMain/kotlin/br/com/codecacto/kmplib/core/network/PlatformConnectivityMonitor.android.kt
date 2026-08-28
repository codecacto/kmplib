package br.com.codecacto.kmplib.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.context.AndroidAppContext

private const val TAG = "ConnectivityObserver"

/**
 * Android: `ConnectivityManager.NetworkCallback` (API oficial de conectividade).
 *
 * **Idempotente por construção:** [start] com o callback já registrado é no-op — o `NetworkCallback`
 * é registrado exatamente uma vez e desregistrado exatamente uma vez, mesmo que a política de
 * contagem do [ConnectivityObserver] fosse burlada. Isso elimina o vazamento de callback (e o
 * `TooManyRequestsException` do `registerNetworkCallback` ao estourar o limite do sistema).
 */
internal actual class PlatformConnectivityMonitor {

    private var callback: ConnectivityManager.NetworkCallback? = null

    private fun connectivityManager(): ConnectivityManager? {
        val context = AndroidAppContext.get() ?: return null
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    actual fun start(onStatusChange: (Boolean) -> Unit) {
        if (callback != null) return // já registrado: nunca registra um segundo callback
        val connectivityManager = connectivityManager() ?: return

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onStatusChange(true)
            }

            override fun onLost(network: Network) {
                onStatusChange(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                onStatusChange(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            callback = networkCallback
        } catch (t: Throwable) {
            // best-effort: conectividade nunca derruba o app
            AppLogger.w(TAG, "Falha ao registrar NetworkCallback", t)
        }
    }

    actual fun stop() {
        val networkCallback = callback ?: return // idempotente
        callback = null
        val connectivityManager = connectivityManager() ?: return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Falha ao desregistrar NetworkCallback", t)
        }
    }

    actual fun currentStatus(): Boolean? {
        val connectivityManager = connectivityManager() ?: return null
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Falha ao consultar o ConnectivityManager", t)
            null
        }
    }
}
