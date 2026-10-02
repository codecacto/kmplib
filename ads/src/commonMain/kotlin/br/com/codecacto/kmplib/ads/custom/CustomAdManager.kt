package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.ads.AdDefaults
import br.com.codecacto.kmplib.ads.AdLoadState
import br.com.codecacto.kmplib.ads.awaitSettled
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration

/**
 * Orquestrador singleton dos house ads (anuncios proprios do app).
 *
 * A fonte e o backend central **apps-api** (REST, [RestCustomAdSource]), por **projeto + superficie**.
 * Basta passar `projectSlug` (+ `surface`, default "app") + `httpClient` no [CustomAdConfig]; o manager
 * constroi o source REST sozinho.
 *
 * Uso:
 * ```kotlin
 * CustomAdManager.initialize(
 *     CustomAdConfig(
 *         projectSlug = "meu-app",
 *         surface = "app",
 *         httpClient = appHttpClient,   // o mesmo de feedback/developer
 *     )
 * )
 *
 * // Em qualquer Composable:
 * CustomBannerAd()
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
    private val _loadState = MutableStateFlow(AdLoadState.IDLE)

    /** Conta as chamadas de [initialize]: o observer antigo, ao terminar, nao mexe no estado do novo. */
    @Volatile
    private var generation = 0

    /** Configuracao atual (null se nao inicializado). */
    val config: CustomAdConfig? get() = _config

    /** Anuncios ativos atualmente disponiveis. */
    val ads: StateFlow<List<CustomAd>> = _ads.asStateFlow()

    /** Se o manager ja foi inicializado. */
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    /**
     * Situacao da PRIMEIRA carga de [ads] (2.236.0). Com [ads] vazio, e isto que diz se nao ha
     * anuncio ([AdLoadState.READY]) ou se a resposta do apps-api ainda nao voltou
     * ([AdLoadState.LOADING]). Volta a [AdLoadState.LOADING] a cada [initialize].
     */
    val loadState: StateFlow<AdLoadState> = _loadState.asStateFlow()

    /**
     * Suspende ate a primeira carga de [ads] se resolver ou ate [timeout] (2.236.0). Devolve `true`
     * quando a carga se resolveu (com ou sem anuncio), `false` quando o tempo acabou ou a fonte falhou.
     *
     * Os composables de intersticial ja esperam sozinhos; isto e para quem decide por conta propria.
     */
    suspend fun awaitFirstLoad(timeout: Duration = AdDefaults.INTERSTITIAL_FIRST_LOAD_TIMEOUT): Boolean =
        awaitSettled(_loadState, timeout) == AdLoadState.READY

    /**
     * Inicializa o manager e comeca a buscar os house ads.
     *
     * Pode ser chamado mais de uma vez para trocar [CustomAdConfig] — o observer
     * anterior e cancelado e um novo e iniciado.
     *
     * @param config configuracao. Para a fonte REST padrao, traga `projectSlug` + `httpClient`.
     * @param source fonte de dados. Quando `null` (default), o manager constroi um
     *   [RestCustomAdSource] a partir do `config` (apps-api). Passe um source explicito para
     *   testes (fake).
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

        val myGeneration = ++generation
        _loadState.value = AdLoadState.LOADING

        observerJob = resolvedSource.observeAds()
            .onEach {
                _ads.value = it
                if (myGeneration == generation) _loadState.value = AdLoadState.READY
            }
            // Regra de ouro: anuncio nunca derruba o app. Uma fonte que lanca (sem `catch`, a excecao
            // subiria ao handler do scope e encerraria o processo) vira carga FALHA.
            .catch { e ->
                AppLogger.w(TAG, "Falha na fonte de house ads: ${e.message}")
                if (myGeneration == generation) _loadState.value = AdLoadState.FAILED
            }
            // Fonte que termina sem emitir nada: nao ha o que esperar.
            .onCompletion { cause ->
                if (cause == null && myGeneration == generation && _loadState.value == AdLoadState.LOADING) {
                    _loadState.value = AdLoadState.FAILED
                }
            }
            .launchIn(this.scope)

        _initialized.value = true
        AppLogger.d(TAG, "CustomAdManager inicializado (project=${config.projectSlug}, surface=${config.surface})")
    }

    /**
     * Resolve a fonte padrao a partir do [config]: REST (apps-api) quando ha `httpClient` +
     * `projectSlug`. Sem eles e sem `source` explicito, cai num source vazio (best-effort, sem
     * anuncios) em vez de lancar — mantem a regra de ouro de nunca derrubar o app por causa de ads.
     */
    private fun resolveDefaultSource(config: CustomAdConfig): CustomAdSource {
        val client = config.httpClient
        val slug = config.projectSlug
        return if (client != null && !slug.isNullOrBlank()) {
            RestCustomAdSource(
                httpClient = client,
                projectSlug = slug,
                surface = config.surface,
                appsApiBaseUrl = config.appsApiBaseUrl,
            )
        } else {
            AppLogger.w(TAG, "CustomAdConfig sem httpClient/projectSlug e sem source — nenhum anuncio sera carregado.")
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
        scope.launch {
            source.fetchAds().onSuccess { _ads.value = it }
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
        _loadState.value = AdLoadState.IDLE
        generation++
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    internal fun notifyImpression(ad: CustomAd) {
        _config?.onImpression?.invoke(ad)
    }

    internal fun notifyClick(ad: CustomAd) {
        _config?.onClick?.invoke(ad)
    }
}
