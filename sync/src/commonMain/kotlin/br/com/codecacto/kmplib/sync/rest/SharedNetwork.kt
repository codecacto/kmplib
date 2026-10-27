package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.core.network.NetworkType

/**
 * Observador de rede compartilhado do módulo (2.328.0) — a leitura default do "só no Wi-Fi" da
 * [RestUploadOutbox]. Ligado na primeira consulta e mantido pelo processo (serviço de vida longa, ver
 * o contrato de `start()` do [ConnectivityObserver]): um callback nativo só.
 */
private val sharedObserver: ConnectivityObserver by lazy { ConnectivityObserver().also { it.start() } }

/** `false` = não tarifada; `true` = tarifada; `null` = sem rede (não sei se pode gastar dados). */
internal fun sharedActiveNetworkIsMetered(): Boolean? = when (sharedObserver.networkType.value) {
    NetworkType.UNMETERED -> false
    NetworkType.METERED -> true
    NetworkType.NONE -> null
}
