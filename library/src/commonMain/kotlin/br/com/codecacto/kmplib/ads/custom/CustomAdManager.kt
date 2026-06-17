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
 * A partir da kmplib 2.37.0 a fonte padrao e o backend central **apps-api** (REST,
 * [RestCustomAdSource]) — nao mais o Firestore. Basta passar `httpClient` + `appId` no
 * [CustomAdConfig]; o manager constroi o source REST sozinho.
 *
 * Uso:
 * ```kotlin
 * CustomAdManager.initialize(
 *     CustomAdConfig(
 *         appId = "meu-app",
 *         httpClient = appHttpClient,   // o mesmo de feedback/developer
 *         placementId = "home_top",
 *     )
 * )
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
     * Inicializa o manager e comeca a buscar os house ads.
     *
     * Pode ser chamado mais de uma vez para trocar [CustomAdConfig] — o observer
     * anterior e cancelado e um novo e iniciado.
     *
     * @param config configuracao. Para a fonte REST padrao, traga `httpClient` + `appId`.
     * @param source fonte de dados. Quando `null` (default), o manager constroi um
     *   [RestCustomAdSource] a partir do `config` (apps-api). Passe um source explicito para
     *   testes (fake) ou para usar o legado [CustomAdRepository] (Firestore).
     * @param scope coroutine scope do observer.
     */
    fun initialize(
        config: CustomAdConfig = CustomAdConfig(),
        source: CustomAdSource? = null,
        scope: CoroutineScope? = null
    ) {
        observerJob?.cancel()
        scope?.let { this.scope = it }
        _config = config

        val resolvedSource = source ?: resolveDefaultSource(config)
        _source = resolvedSource

        observerJob = resolvedSource.observeAds(config.placementId, config.appId)
            .onEach { _ads.value = it }
            .launchIn(this.scope)

        _initialized.value = true
        AppLogger.d(TAG, "CustomAdManager inicializado (source=REST, app=${config.appId}, placement=${config.placementId})")
    }

    /**
     * Resolve a fonte padrao a partir do [config]: REST (apps-api) quando ha `httpClient` + `appId`.
     * Sem `httpClient`/`appId` e sem `source` explicito, cai num source vazio (best-effort, sem
     * anuncios) em vez de lancar — mantem a regra de ouro de nunca derrubar o app por causa de ads.
     */
    private fun resolveDefaultSource(config: CustomAdConfig): CustomAdSource {
        val client = config.httpClient
        val appId = config.appId
        return if (client != null && !appId.isNullOrBlank()) {
            RestCustomAdSource(
                httpClient = client,
                appId = appId,
                appsApiBaseUrl = config.appsApiBaseUrl,
            )
        } else {
            AppLogger.w(TAG, "CustomAdConfig sem httpClient/appId e sem source — nenhum anuncio sera carregado.")
            EmptyCustomAdSource
        }
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
