package br.com.codecacto.kmplib.ads.router

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
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
 * A partir da kmplib 2.37.0 a config vem do backend central **apps-api** (REST,
 * [RestAdRoutingSource]) — nao mais do Firestore. Passe o `httpClient` no [initialize] e o router
 * constroi a fonte REST sozinho.
 *
 * Uso:
 * ```kotlin
 * // Boot do app:
 * AdRouter.initialize("meu-app", httpClient = appHttpClient, defaults = AdRouting.ALL_CUSTOM)
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
     * @param appId identificador do app (slug no catalogo central; vai no path
     *   `/public/ad-config/{appId}`)
     * @param httpClient `HttpClient` (Ktor) do app, usado pela fonte REST padrao. Quando `null` e
     *   sem `source` explicito, cai num source vazio que sempre devolve [defaults] (best-effort).
     * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
     * @param defaults routing usado quando o servidor nao publicou config ou ha erro/corpo
     *   invalido. Default: [AdRouting.OFF] (nada aparece ate o admin habilitar).
     * @param source fonte de dados. Quando `null` (default), o router constroi um
     *   [RestAdRoutingSource] a partir do `httpClient` (apps-api). Passe um source explicito para
     *   testes (fake) ou para usar o legado [AdRoutingRepository] (Firestore).
     * @param scope coroutine scope para o observer — default `Dispatchers.Default`.
     *   Tests passam o scope do `runTest`.
     */
    fun initialize(
        appId: String,
        httpClient: HttpClient? = null,
        appsApiBaseUrl: String = RestAdRoutingSource.DEFAULT_APPS_API_BASE_URL,
        defaults: AdRouting = AdRouting.OFF,
        source: AdRoutingSource? = null,
        scope: CoroutineScope? = null,
    ) {
        observerJob?.cancel()
        scope?.let { this.scope = it }
        _appId = appId

        val resolvedSource = source ?: if (httpClient != null) {
            RestAdRoutingSource(httpClient = httpClient, appsApiBaseUrl = appsApiBaseUrl)
        } else {
            AppLogger.w(TAG, "AdRouter sem httpClient e sem source — usando apenas defaults.")
            EmptyAdRoutingSource
        }
        _source = resolvedSource
        _routing.value = defaults

        observerJob = resolvedSource.observeRouting(appId, defaults)
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
