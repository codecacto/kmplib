package br.com.codecacto.kmplib.core.network

/**
 * Monitor de rede **da plataforma** — a peça `expect/actual` "burra" por trás do
 * [ConnectivityObserver]. Só registra/desregistra o observador nativo e reporta mudanças.
 *
 * A política de ciclo de vida (idempotência, contagem de referência, `StateFlow`) fica toda em
 * `commonMain` no [ConnectivityObserver] — aqui não há contagem nem estado observável, o que evita
 * duplicar a regra (e o bug) em cada `actual`.
 *
 * Contratos que **todo** `actual` deve honrar:
 * - [start] é idempotente: chamada com o monitor já ativo é no-op (nunca registra 2 callbacks);
 * - [stop] é idempotente: chamada com o monitor parado é no-op;
 * - nada lança (falha de plataforma vira log; conectividade nunca derruba o app);
 * - [currentStatus] devolve `null` quando o estado é desconhecido (sem `Context`, monitor nunca
 *   iniciado, etc.) — nesse caso o [ConnectivityObserver] **preserva** o valor corrente.
 */
internal expect class PlatformConnectivityMonitor() {

    /** Liga o monitor nativo. [onStatusChange] é chamado a cada mudança de conectividade. */
    fun start(onStatusChange: (Boolean) -> Unit)

    /** Desliga o monitor nativo e libera o recurso (callback/monitor). */
    fun stop()

    /** Leitura imediata do estado da rede, ou `null` se desconhecido. */
    fun currentStatus(): Boolean?
}
