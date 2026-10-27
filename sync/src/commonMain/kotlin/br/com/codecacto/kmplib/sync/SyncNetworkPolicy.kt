package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.network.NetworkType

/**
 * **Política de rede da sincronização** (2.328.0) — a escolha que toda tela "Sincronização" de app
 * offline da fábrica oferece. Persista pelo [wire] (estável) e leia de volta por [fromWire].
 *
 * ## A regra da fábrica (estudo offline-first de 27/out/2026, §7.1)
 * **Dado de texto sobe em QUALQUER rede nos dois modos automáticos** — são poucos KB, e segurá-lo até
 * o Wi-Fi é o que faz a pessoa achar que "não salvou" (e o que deixa o dado só no aparelho que pode
 * ser perdido). **A política segura só MÍDIA** (foto, vídeo, áudio): em [AUTOMATIC_MEDIA_ON_UNMETERED]
 * ela espera rede não tarifada.
 *
 * - [AUTOMATIC_MEDIA_ON_UNMETERED] (default) — dados sobem sozinhos em qualquer rede; mídia espera o
 *   Wi-Fi.
 * - [AUTOMATIC_ANY_NETWORK] — tudo sobe sozinho em qualquer rede, inclusive no pacote de dados.
 * - [MANUAL_ONLY] — nada sai do aparelho sem a pessoa tocar em "Sincronizar agora" (o envio manual
 *   ignora a política: é ordem explícita).
 *
 * Como ligar nas peças da lib: [allowsAutomaticData] decide se o agendador/auto-sync roda
 * (`BackgroundSyncScheduler.apply(policy)` já não agenda nada em manual); [mediaOnlyOnUnmetered] vira o
 * `wifiOnly` de cada envio de mídia (`RestUploadOutbox.enqueue(…, wifiOnly = …)`,
 * `DirectUploadRequest(wifiOnly = …)`). Os textos da tela são do app.
 */
enum class SyncNetworkPolicy(val wire: String) {
    AUTOMATIC_MEDIA_ON_UNMETERED("auto_media_unmetered"),
    AUTOMATIC_ANY_NETWORK("auto_any_network"),
    MANUAL_ONLY("manual"),
    ;

    /** A política sincroniza sozinha (agendamento, reconexão, 2º plano)? */
    val isAutomatic: Boolean get() = this != MANUAL_ONLY

    /**
     * A sincronização AUTOMÁTICA de **dados** (texto) pode rodar em [network]? Nos dois modos
     * automáticos, em qualquer rede conectada; em [MANUAL_ONLY], nunca.
     */
    fun allowsAutomaticData(network: NetworkType): Boolean = isAutomatic && network.isConnected

    /** A **mídia** só sobe em rede não tarifada? (É o `wifiOnly` das filas de envio.) */
    val mediaOnlyOnUnmetered: Boolean get() = this == AUTOMATIC_MEDIA_ON_UNMETERED

    /** O envio AUTOMÁTICO de **mídia** pode rodar em [network]? */
    fun allowsAutomaticMedia(network: NetworkType): Boolean = when (this) {
        AUTOMATIC_MEDIA_ON_UNMETERED -> network.isUnmetered
        AUTOMATIC_ANY_NETWORK -> network.isConnected
        MANUAL_ONLY -> false
    }

    companion object {
        /** O default da fábrica. */
        val DEFAULT: SyncNetworkPolicy = AUTOMATIC_MEDIA_ON_UNMETERED

        /** Lê o valor persistido; desconhecido/nulo cai no [DEFAULT] (também aceita o `name`). */
        fun fromWire(value: String?): SyncNetworkPolicy =
            entries.firstOrNull { it.wire == value || it.name == value } ?: DEFAULT
    }
}
