package br.com.codecacto.kmplib.core.network

/**
 * **Tipo da rede que o app vai usar AGORA** (2.328.0) — a pergunta que "online/offline" não responde:
 * **pode gastar o pacote de dados da pessoa?**
 *
 * - [NONE] — sem rede com internet.
 * - [UNMETERED] — rede **não tarifada** (Wi-Fi comum, cabo): subir foto e vídeo à vontade.
 * - [METERED] — rede **tarifada** (dados móveis, ponto de acesso do celular, Wi-Fi marcado como
 *   limitado) **ou** com economia de dados ligada pela pessoa (Modo Dados Reduzidos do iOS).
 *
 * Como cada plataforma decide (APIs oficiais, as mesmas que o WorkManager e o `URLSession` usam):
 * - **Android:** capacidades da rede **padrão** (`registerDefaultNetworkCallback`):
 *   sem `NET_CAPABILITY_INTERNET` → [NONE]; `NET_CAPABILITY_NOT_METERED` (ou, no Android 11+,
 *   `NET_CAPABILITY_TEMPORARILY_NOT_METERED` — o mesmo critério do `isActiveNetworkMetered`) →
 *   [UNMETERED]; senão [METERED]. Wi-Fi que a pessoa marcou como "limitado" vira [METERED], como deve.
 * - **iOS:** `NWPathMonitor` — caminho não satisfeito → [NONE]; `nw_path_is_expensive` (celular,
 *   hotspot) **ou** `nw_path_is_constrained` (Modo Dados Reduzidos) → [METERED]; senão [UNMETERED].
 *
 * Leia por [ConnectivityObserver.networkType]. O vocabulário de política de sincronização (o que sobe
 * em cada tipo) é do `kmplib-sync` (`SyncNetworkPolicy`).
 */
enum class NetworkType {
    NONE,
    UNMETERED,
    METERED,
    ;

    /** Há rede com internet (tarifada ou não). */
    val isConnected: Boolean get() = this != NONE

    /** Rede não tarifada — onde mídia "só no Wi-Fi" pode subir. */
    val isUnmetered: Boolean get() = this == UNMETERED
}

/**
 * Classificação pura do caminho de rede do iOS (e regra única de "o que é tarifado"): exposta para
 * teste. `satisfied` = há caminho com internet; `expensive` = celular/hotspot; `constrained` = Modo
 * Dados Reduzidos.
 */
internal fun classifyNetworkPath(satisfied: Boolean, expensive: Boolean, constrained: Boolean): NetworkType = when {
    !satisfied -> NetworkType.NONE
    expensive || constrained -> NetworkType.METERED
    else -> NetworkType.UNMETERED
}

/** Classificação pura das capacidades da rede padrão do Android (exposta para teste). */
internal fun classifyNetworkCapabilities(hasInternet: Boolean, notMetered: Boolean, temporarilyNotMetered: Boolean): NetworkType = when {
    !hasInternet -> NetworkType.NONE
    notMetered || temporarilyNotMetered -> NetworkType.UNMETERED
    else -> NetworkType.METERED
}
