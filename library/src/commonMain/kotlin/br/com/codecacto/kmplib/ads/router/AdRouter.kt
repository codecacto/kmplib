package br.com.codecacto.kmplib.ads.router

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Decide em runtime qual provider de publicidade usar pra cada formato,
 * baseado em config remota no Firestore (`app_ad_configs/{appId}`).
 *
 * O app inicializa uma vez no boot, passando o [appId] e um [defaults] caso
 * o admin nao tenha publicado nenhuma config ainda. Depois, os composables
 * [ManagedBannerAd] / [ManagedInterstitialAd] consomem [routing] e decidem
 * sozinhos qual implementacao chamar.
 *
 * Uso:
 * ```kotlin
 * // Boot do app:
 * AdRouter.initialize("meu-app", defaults = AdRouting.ALL_CUSTOM)
 *
 * // Numa tela:
 * ManagedBannerAd(placementId = "home_top")
 * ```
 */
object AdRouter {
    private const val TAG = "AdRouter"

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerJob: Job? = null

    private var _appId: String? = null
    private var _source: AdRoutingSource? = null

    private val _routing = MutableStateFlow(AdRouting.OFF)
    private val _initialized = MutableStateFlow(false)

    /** Routing atual (em tempo real). */
    val routing: StateFlow<AdRouting> = _routing.asStateFlow()

    /** Se o router ja foi inicializado. */
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    /** AppId em uso (null se nao inicializado). */
    val appId: String? get() = _appId

    /**
     * Inicializa o router. Pode ser chamado de novo pra trocar appId — o
     * observer anterior e cancelado.
     *
     * @param appId identificador do app (mesmo valor que vai no doc do Firestore)
     * @param defaults routing usado quando o doc `app_ad_configs/{appId}` nao existe
     *   ou nao pode ser desserializado. Default: [AdRouting.OFF] (nada aparece
     *   ate o admin habilitar).
     * @param source fonte de dados — default usa Firestore.
     * @param scope coroutine scope para o observer — default `Dispatchers.Default`.
     *   Tests passam o scope do `runTest`.
     */
    fun initialize(
        appId: String,
        defaults: AdRouting = AdRouting.OFF,
        source: AdRoutingSource = AdRoutingRepository(),
        scope: CoroutineScope? = null,
    ) {
        observerJob?.cancel()
        scope?.let { this.scope = it }
        _appId = appId
        _source = source
        _routing.value = defaults

        observerJob = source.observeRouting(appId, defaults)
            .onEach { _routing.value = it }
            .launchIn(this.scope)

        _initialized.value = true
        AppLogger.d(TAG, "AdRouter inicializado para appId=$appId (defaults=$defaults)")
    }

    /** Reseta o estado (util para testes). */
    fun reset() {
        observerJob?.cancel()
        observerJob = null
        _appId = null
        _source = null
        _routing.value = AdRouting.OFF
        _initialized.value = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
