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

        // ⚠️ Callback da rede PADRÃO, nunca de todas as redes (2.203.0).
        //
        // Com `registerNetworkCallback(request)` o callback recebe eventos de CADA rede que casa com
        // o pedido — Wi-Fi e 4G ao mesmo tempo, no aparelho que mantém os dois. O `onLost` do Wi-Fi
        // chegava e gravava `false` enquanto o 4G seguia funcionando; como nenhuma outra rede
        // "aparecia" depois, nada voltava a `true`, e o `ConnectivityGate` em tela cheia ficava
        // travado em "sem internet" com o app online. O que o app quer saber é se a rede que ELE vai
        // usar tem internet — a padrão —, e é exatamente o que `registerDefaultNetworkCallback`
        // entrega (API 24, o minSdk da lib): troca de Wi-Fi para 4G chega como `onAvailable` da
        // nova rede, e `onLost` só chega quando não sobra rede padrão nenhuma.
        //
        // E mesmo aí o estado é RELIDO do `ConnectivityManager`, nunca gravado às cegas: um `false`
        // escrito por evento é o que não tem caminho de volta se o evento seguinte não vier.
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // A rede padrão acabou de mudar para esta. As capacidades dela chegam logo em seguida
                // por `onCapabilitiesChanged`; até lá, a releitura responde pela rede ativa.
                onStatusChange(currentStatus() ?: true)
            }

            override fun onLost(network: Network) {
                onStatusChange(currentStatus() ?: false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Aqui `network` É a rede padrão (callback padrão), então as capacidades recebidas
                // são o estado real dela — inclusive a perda de INTERNET sem perder a rede.
                onStatusChange(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
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
