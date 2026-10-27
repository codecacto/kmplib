package br.com.codecacto.kmplib.core.network

/**
 * Fonte "burra" do estado de rede por trás do [ConnectivityObserver]: só registra/desregistra o
 * observador nativo e reporta o [NetworkType] corrente. Existe como interface para o observer ser
 * testado com uma fonte falsa (a política — amortecimento, contagem de referência — mora no observer).
 *
 * Contratos que **toda** implementação deve honrar:
 * - [start] é idempotente: chamada com o monitor já ativo é no-op (nunca registra 2 callbacks);
 * - [stop] é idempotente: chamada com o monitor parado é no-op;
 * - nada lança (falha de plataforma vira log; conectividade nunca derruba o app);
 * - [currentType] devolve `null` quando o estado é desconhecido (sem `Context`, monitor nunca
 *   iniciado, etc.) — nesse caso o [ConnectivityObserver] **preserva** o valor corrente.
 */
internal interface NetworkTypeSource {

    /** Liga o monitor nativo. [onChange] é chamado a cada mudança de rede. */
    fun start(onChange: (NetworkType) -> Unit)

    /** Desliga o monitor nativo e libera o recurso (callback/monitor). */
    fun stop()

    /** Leitura imediata do tipo de rede, ou `null` se desconhecido. */
    fun currentType(): NetworkType?
}

/**
 * Monitor de rede **da plataforma** (`expect/actual`): `ConnectivityManager.NetworkCallback` no
 * Android, `NWPathMonitor` no iOS. Desde a 2.328.0 reporta o [NetworkType] (tarifada × não tarifada),
 * não só online/offline.
 */
internal expect class PlatformConnectivityMonitor() : NetworkTypeSource {
    override fun start(onChange: (NetworkType) -> Unit)
    override fun stop()
    override fun currentType(): NetworkType?
}
