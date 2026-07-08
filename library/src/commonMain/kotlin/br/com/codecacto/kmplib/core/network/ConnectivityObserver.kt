package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observador de conectividade multiplataforma.
 *
 * Expõe [isOnline] como [StateFlow] reativo — reflete o estado atual da rede e emite
 * automaticamente quando a conexão cai/volta (Android `ConnectivityManager.NetworkCallback`,
 * iOS `NWPathMonitor`).
 *
 * ## Ciclo de vida: `start()`/`stop()` são **contados por referência** (2.69.0)
 *
 * Um mesmo observer normalmente é **compartilhado** por vários consumidores: o
 * [ConnectivityGate][br.com.codecacto.kmplib.ui.components.ConnectivityGate] (UI),
 * o [rememberIsOnline][br.com.codecacto.kmplib.ui.components.rememberIsOnline] (lógica) e o
 * auto-sync (`RestCrudSyncEngine`/`SyncEngine.startAutoSync`). Por isso:
 *
 * - **[start] é idempotente**: o callback nativo é registrado **uma única vez**, na primeira
 *   chamada. Um segundo `start()` só incrementa a contagem — **não vaza `NetworkCallback`**.
 * - **[stop] não é global**: só desregistra o callback quando o **último** consumidor sai. O
 *   `onDispose` de uma tela nunca derruba o observer do auto-sync.
 * - **`stop()` sobrando é no-op**: a contagem nunca fica negativa; um `stop()` sem `start()`
 *   pareado não desliga nada.
 *
 * O contrato é o **pareamento**: cada componente que chama [start] deve chamar [stop] exatamente
 * uma vez ao sair de escopo (os helpers de UI da lib já fazem isso via `DisposableEffect`).
 * Um serviço de vida longa (auto-sync) chama [start] no bootstrap e simplesmente não solta —
 * mantendo o observer vivo enquanto o processo existir.
 *
 * ```kotlin
 * // Koin: um único observer para o app inteiro
 * single { ConnectivityObserver() }
 *
 * // auto-sync (vida longa) — start() sem stop()
 * RestCrudSyncEngine(participants, get()).start()
 *
 * // UI (vida curta) — start()/stop() pareados, sem matar o auto-sync
 * ConnectivityGate(observer = koinInject()) { AppNavHost() }
 * ```
 *
 * Todas as operações são **best-effort**: nada aqui lança nem derruba o app.
 */
class ConnectivityObserver {

    private val monitor = PlatformConnectivityMonitor()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val activation = ActivationRefCounter(
        onActivate = {
            // Semeia o valor corrente antes de assinar, para não exibir "offline" por um frame.
            monitor.currentStatus()?.let { _isOnline.value = it }
            monitor.start { online -> _isOnline.value = online }
        },
        onDeactivate = { monitor.stop() },
    )

    /** Quantos consumidores mantêm este observer ativo (diagnóstico/teste). */
    internal val activeConsumers: Int get() = activation.active

    /** `true` enquanto ao menos um consumidor mantiver o monitor nativo registrado. */
    val isObserving: Boolean get() = activation.isActive

    /**
     * Registra um consumidor e liga o monitor nativo **na primeira** chamada (idempotente).
     * Toda chamada a [start] deve ter um [stop] correspondente (exceto serviços de vida longa,
     * que intencionalmente seguram a referência pelo processo inteiro).
     */
    fun start() {
        activation.acquire()
    }

    /**
     * Libera um consumidor. O monitor nativo só é desregistrado quando o **último** consumidor
     * sai — chamar [stop] não interfere nos demais. Chamada extra/desemparelhada é no-op.
     */
    fun stop() {
        activation.release()
    }

    /**
     * Reavalia a conectividade **imediatamente**, atualizando [isOnline] com o estado corrente da
     * rede. Útil para o botão "Tentar novamente" — no fluxo reativo o valor já se atualiza sozinho
     * quando a rede volta, mas [refresh] força uma releitura na hora (best-effort; nunca lança).
     *
     * Se a plataforma não souber informar o estado (Android sem `Context`; iOS antes do primeiro
     * update do `NWPathMonitor`), o valor corrente é **preservado** — nunca se inventa "offline".
     */
    fun refresh() {
        monitor.currentStatus()?.let { _isOnline.value = it }
    }
}
