package br.com.codecacto.kmplib.ads.custom

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
import kotlinx.coroutines.launch

/**
 * Orquestrador singleton dos Custom Ads (anuncios proprios via Firestore).
 *
 * Funcionalidade NOVA e independente do AdMob/Firebase Ads (ver
 * [br.com.codecacto.kmplib.firebase.ads.AdManager]). Os dois podem coexistir.
 *
 * Uso:
 * ```kotlin
 * CustomAdManager.initialize(CustomAdConfig(placementId = "home_top"))
 *
 * // Em qualquer Composable:
 * CustomBannerAd(placementId = "home_top")
 * ```
 */
object CustomAdManager {
    private const val TAG = "CustomAdManager"

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerJob: Job? = null

    private var _config: CustomAdConfig? = null
    private var _source: CustomAdSource? = null

    private val _ads = MutableStateFlow<List<CustomAd>>(emptyList())
    private val _initialized = MutableStateFlow(false)

    /** Configuracao atual (null se nao inicializado). */
    val config: CustomAdConfig? get() = _config

    /** Anuncios ativos atualmente disponiveis (atualizado em tempo real). */
    val ads: StateFlow<List<CustomAd>> = _ads.asStateFlow()

    /** Se o manager ja foi inicializado. */
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    /**
     * Inicializa o manager e comeca a observar a colecao no Firestore.
     *
     * Pode ser chamado mais de uma vez para trocar [CustomAdConfig] — o observer
     * anterior e cancelado e um novo e iniciado.
     */
    fun initialize(
        config: CustomAdConfig = CustomAdConfig(),
        source: CustomAdSource = CustomAdRepository(collection = config.collection),
        scope: CoroutineScope? = null
    ) {
        observerJob?.cancel()
        scope?.let { this.scope = it }
        _config = config
        _source = source

        observerJob = source.observeAds(config.placementId, config.appId)
            .onEach { _ads.value = it }
            .launchIn(this.scope)

        _initialized.value = true
        AppLogger.d(TAG, "CustomAdManager inicializado (collection=${config.collection}, app=${config.appId}, placement=${config.placementId})")
    }

    /**
     * Forca uma busca one-shot e atualiza [ads]. Util para pull-to-refresh.
     */
    fun refresh() {
        val source = _source ?: run {
            AppLogger.w(TAG, "refresh() chamado antes de initialize()")
            return
        }
        val config = _config ?: return
        scope.launch {
            source.fetchAds(config.placementId, config.appId).onSuccess { _ads.value = it }
        }
    }

    /** Reseta o estado (util para testes). */
    fun reset() {
        observerJob?.cancel()
        observerJob = null
        _config = null
        _source = null
        _ads.value = emptyList()
        _initialized.value = false
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    internal fun notifyImpression(ad: CustomAd) {
        _config?.onImpression?.invoke(ad)
    }

    internal fun notifyClick(ad: CustomAd) {
        _config?.onClick?.invoke(ad)
    }
}
