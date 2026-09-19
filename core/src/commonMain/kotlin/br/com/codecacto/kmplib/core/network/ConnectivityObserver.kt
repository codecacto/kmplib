package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
class ConnectivityObserver(
    /**
     * Quanto tempo a rede precisa ficar fora até o app AVISAR que está fora (2.209.0).
     *
     * ## O defeito que isto conserta
     * No iOS, o app que vai para o segundo plano perde o direito de usar a rede, e o `NWPathMonitor`
     * empurra `unsatisfied` — não porque o Wi-Fi caiu, mas porque o app saiu de cena. Ao voltar, o
     * último estado empurrado ainda é esse, e o [ConnectivityGate] aparecia na frente da tela em que
     * a pessoa estava (relatado no detalhe de um parceiro, 19/set/2026) até chegar o próximo update.
     * O mesmo acontece, nas duas plataformas, na troca de Wi-Fi para dados móveis: há um intervalo
     * de nenhuma das duas.
     *
     * ## Por que um atraso, e não um "ignore o segundo plano"
     * Porque o remédio serve aos dois casos e não depende de a lib saber o ciclo de vida da tela.
     * **Voltar a ficar online é imediato** (o atraso vale só para a queda), então nada fica preso
     * atrás dele: o pior caso é a tela de "sem internet" demorar [quedaConfirmadaApos] a aparecer
     * em quem está mesmo sem rede — e essa pessoa já não vai conseguir carregar nada nesse tempo.
     *
     * `Duration.ZERO` desliga o atraso (é o que os testes usam para exercitar o caminho cru).
     */
    private val quedaConfirmadaApos: Duration = QUEDA_CONFIRMADA_APOS,
    /** O escopo do atraso. Injetável para o teste rodar em tempo virtual. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val monitor = PlatformConnectivityMonitor()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** O que o sistema operacional está dizendo AGORA — antes do amortecimento de [isOnline]. */
    private val estadoDoSistema = MutableStateFlow(true)
    private var amortecedor: Job? = null

    private val activation = ActivationRefCounter(
        onActivate = {
            // Semeia o valor corrente antes de assinar, para não exibir "offline" por um frame.
            monitor.currentStatus()?.let {
                _isOnline.value = it
                estadoDoSistema.value = it
            }
            // `collectLatest`: o valor novo CANCELA a espera do anterior, que é exatamente o que
            // faz a queda momentânea nunca chegar ao `isOnline` — a volta chega antes do prazo.
            amortecedor = scope.launch {
                estadoDoSistema.collectLatest { online ->
                    if (online) {
                        _isOnline.value = true
                    } else {
                        delay(quedaConfirmadaApos)
                        _isOnline.value = false
                    }
                }
            }
            monitor.start { online -> estadoDoSistema.value = online }
        },
        onDeactivate = {
            amortecedor?.cancel()
            amortecedor = null
            monitor.stop()
        },
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
        // Direto, **sem** o atraso da queda: quem toca em "Tentar novamente" está olhando a tela de
        // offline e espera uma resposta agora — amortecer aqui seria o botão não fazer nada.
        monitor.currentStatus()?.let {
            _isOnline.value = it
            estadoDoSistema.value = it
        }
    }

    companion object {
        /**
         * **2 segundos** — o bastante para cobrir a volta do segundo plano e a troca de rede, e
         * curto o bastante para não parecer que o app travou em quem está mesmo sem internet.
         */
        val QUEDA_CONFIRMADA_APOS: Duration = 2.seconds
    }
}
