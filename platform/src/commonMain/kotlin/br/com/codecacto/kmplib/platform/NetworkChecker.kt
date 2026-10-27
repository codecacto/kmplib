package br.com.codecacto.kmplib.platform

/**
 * Pergunta SÍNCRONA "tem rede agora?". Implementações: `AndroidNetworkChecker`
 * (`ConnectivityManager`, rede ativa com `NET_CAPABILITY_INTERNET`) e `IosNetworkChecker`
 * (`NWPathMonitor`, desde 2.329.0).
 *
 * Para código novo prefira `ConnectivityObserver` (`core.network`): estado observável
 * (`isOnline`/`networkType`), que a tela acompanha em vez de perguntar.
 */
interface NetworkChecker {
    fun isAvailable(): Boolean
}

/** Estado conhecido → resposta; desconhecido (`null`) = disponível — não se inventa "offline". */
internal fun networkAvailableFrom(known: Boolean?): Boolean = known ?: true
