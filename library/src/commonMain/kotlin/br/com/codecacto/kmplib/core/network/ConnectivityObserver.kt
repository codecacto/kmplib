package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Observador de conectividade multiplataforma.
 *
 * Expõe [isOnline] como [StateFlow] reativo — reflete o estado atual da rede e emite
 * automaticamente quando a conexão cai/volta (Android `ConnectivityManager.NetworkCallback`,
 * iOS `NWPathMonitor`). Chame [start] ao entrar em composição/escopo e [stop] ao sair
 * (o helper de UI `ConnectivityGate`/`rememberIsOnline` já faz esse ciclo).
 */
expect class ConnectivityObserver() {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()

    /**
     * Reavalia a conectividade **imediatamente**, atualizando [isOnline] com o estado
     * corrente da rede. Útil para o botão "Tentar novamente" — no fluxo reativo o valor
     * já se atualiza sozinho quando a rede volta, mas [refresh] força uma releitura na hora
     * (best-effort; nunca lança). No iOS é no-op: o `NWPathMonitor` mantém [isOnline] sempre vivo.
     */
    fun refresh()
}
