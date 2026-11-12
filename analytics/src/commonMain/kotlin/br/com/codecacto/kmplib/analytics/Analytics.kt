package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.storage.createBlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.core.util.isCanonicalUuid
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.install.InstallId
import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Configuração do analytics do app (2.361.0) — telas e eventos-chave no **Umami** da fábrica.
 *
 * @property websiteId o id do "website" do app no Umami: o uuid5 de `<slug>:app`, gerado por
 *   `Nexus/fabrica/garantir-umami.py <slug> --dominio <slug>.app --superficie app`. Chave PÚBLICA (vai
 *   no binário, como a DSN do GlitchTip): versionada no `gradle.properties` do app. `null`, vazio ou
 *   fora do formato UUID = **analytics desligado** (no-op, nada em disco, nada na rede).
 * @property projectSlug slug do projeto no Nexus. Vira o `hostname` (`<slug>.app`) — o mesmo website
 *   recebe só o app, e o site do produto tem o website dele.
 * @property hostUrl a instância do Umami. Default: a da fábrica.
 * @property httpClient `null` (default) = cliente PRÓPRIO do módulo, pelo `createHttpClient` da lib.
 *   Não reaproveite o cliente da API do app: ele pode carregar `Authorization`, cabeçalhos do domínio
 *   ou plugins que não têm nada que ir para outro servidor.
 * @property collectPath a coleta unitária (a fábrica renomeou o `/api/send` para `/api/m`).
 * @property batchPath o lote (`/api/batch`). `null` = só envio unitário.
 * @property sendInDebug `false` (default): build de depuração não envia (`BuildInfo.isDebug`). Ligar só
 *   para conferir a integração; o evento entra no painel como qualquer outro.
 * @property sendInAutomation `false` (default): build de teste (`AutomationMode` — dublê da loja,
 *   runner de QA, Test Lab da Play) não envia.
 * @property sendInstallId `true` (default): manda o id ANÔNIMO da instalação (`InstallId.forTelemetry`,
 *   UUID v4 aleatório, sem ligação com conta nem aparelho) como `id` do Umami — sem ele, usuários atrás
 *   do mesmo IP de operadora (CGNAT) com o mesmo sistema viram UMA pessoa. Em app infantil e em teste
 *   ele já sai `null` sozinho.
 * @property flushDelay janela do lote: o primeiro evento agenda o envio para daqui a este tempo, e o
 *   que chegar nesse meio vai junto.
 * @property maxBatchSize eventos por requisição (o Umami aceita até 500 no lote).
 * @property maxQueuedEvents teto da fila em disco; acima dele, o mais antigo sai.
 * @property maxEventAge evento mais velho que isto na fila (aparelho muito tempo sem rede) é
 *   descartado em vez de enviado.
 */
data class AnalyticsConfig(
    val websiteId: String?,
    val projectSlug: String,
    val hostUrl: String = DEFAULT_HOST_URL,
    val httpClient: HttpClient? = null,
    val collectPath: String = DEFAULT_COLLECT_PATH,
    val batchPath: String? = DEFAULT_BATCH_PATH,
    val sendInDebug: Boolean = false,
    val sendInAutomation: Boolean = false,
    val sendInstallId: Boolean = true,
    val flushDelay: Duration = DEFAULT_FLUSH_DELAY,
    val maxBatchSize: Int = DEFAULT_MAX_BATCH_SIZE,
    val maxQueuedEvents: Int = DEFAULT_MAX_QUEUED_EVENTS,
    val maxEventAge: Duration = DEFAULT_MAX_EVENT_AGE,
) {
    /** O `hostname` dos eventos: `<slug>.app`. */
    val hostname: String get() = projectSlug.trim().lowercase() + ".app"

    /** O id normalizado, ou `null` quando não é um UUID (= desligado). */
    val normalizedWebsiteId: String?
        get() = websiteId?.trim()?.lowercase()?.takeIf { isCanonicalUuid(it) }

    companion object {
        const val DEFAULT_HOST_URL: String = "https://metricas.codecacto.com.br"
        const val DEFAULT_COLLECT_PATH: String = "/api/m"
        const val DEFAULT_BATCH_PATH: String = "/api/batch"
        val DEFAULT_FLUSH_DELAY: Duration = 5.seconds
        const val DEFAULT_MAX_BATCH_SIZE: Int = 20
        const val DEFAULT_MAX_QUEUED_EVENTS: Int = 500
        val DEFAULT_MAX_EVENT_AGE: Duration = 7.days

        /** Primeira espera depois de uma falha de rede; dobra a cada falha até [MAX_RETRY_DELAY]. */
        val BASE_RETRY_DELAY: Duration = 30.seconds
        val MAX_RETRY_DELAY: Duration = 15.minutes
    }
}

/** Por que o analytics está (ou não) ligado. */
enum class AnalyticsStatus {
    /** Envia. */
    ENABLED,

    /** Sem `websiteId` válido — o app ainda não tem website no Umami. */
    NO_WEBSITE_ID,

    /** Build de depuração (e [AnalyticsConfig.sendInDebug] desligado). */
    DEBUG_BUILD,

    /** Projeto sem slug. */
    NO_PROJECT_SLUG,
}

/**
 * O coletor de um processo (testável, fora do singleton [Analytics]).
 *
 * - [trackScreen]/[trackEvent] **nunca lançam nem bloqueiam**: gravam o evento na fila (disco) num
 *   escopo próprio e agendam o envio.
 * - Envio em LOTE (`/api/batch`, recuo para `/api/m`), com o `timestamp` do momento do evento.
 * - Sem rede/429/5xx: fica na fila, nova tentativa com espera dobrando (30 s → 15 min), na próxima
 *   vinda ao primeiro plano e no próximo evento. Ao ir para o segundo plano, tenta esvaziar.
 * - Corpo recusado (4xx) sai da fila — repetir não conserta.
 */
class AnalyticsClient internal constructor(
    val config: AnalyticsConfig,
    private val queueFactory: () -> AnalyticsQueue,
    private val transportFactory: () -> UmamiTransport,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val isDebugBuild: () -> Boolean,
    private val isAutomation: () -> Boolean,
    private val installId: suspend () -> String?,
    private val device: () -> AnalyticsDevice,
    private val appVersion: () -> String?,
    private val language: () -> String?,
    registerLifecycle: ((onForeground: () -> Unit, onBackground: () -> Unit) -> Boolean)?,
) {
    /** Cliente de produção: fila em disco, cliente HTTP próprio, ciclo de vida do app. */
    constructor(config: AnalyticsConfig) : this(
        config = config,
        queueFactory = { defaultQueue() },
        transportFactory = {
            UmamiTransport(
                httpClient = config.httpClient ?: createHttpClient(HttpClientOptions()),
                hostUrl = config.hostUrl,
                collectPath = config.collectPath,
                batchPath = config.batchPath,
            )
        },
        scope = defaultScope(),
        clock = { currentTimeMillis() },
        isDebugBuild = { kotlin.runCatching { BuildInfo.isDebug }.getOrDefault(false) },
        isAutomation = { kotlin.runCatching { AutomationMode.isActive }.getOrDefault(false) },
        installId = { InstallId.forTelemetry() },
        device = { currentAnalyticsDevice() },
        appVersion = { kotlin.runCatching { BuildInfo.appVersionName }.getOrNull() },
        language = { kotlin.runCatching { appLanguageTag() }.getOrNull() },
        registerLifecycle = { fg, bg -> registerAnalyticsLifecycle(fg, bg) },
    )

    /** Ligado ou o motivo de não estar. Decidido na criação. */
    val status: AnalyticsStatus = when {
        config.projectSlug.isBlank() -> AnalyticsStatus.NO_PROJECT_SLUG
        config.normalizedWebsiteId == null -> AnalyticsStatus.NO_WEBSITE_ID
        !config.sendInDebug && runCatching { isDebugBuild() }.getOrDefault(false) -> AnalyticsStatus.DEBUG_BUILD
        else -> AnalyticsStatus.ENABLED
    }

    /** `true` quando os eventos de fato saem (fora de teste e com id). */
    val isEnabled: Boolean
        get() = status == AnalyticsStatus.ENABLED && (config.sendInAutomation || !automation())

    private val queue: AnalyticsQueue by lazy(queueFactory)
    private val transport: UmamiTransport by lazy(transportFactory)
    private val queueMutex = Mutex()
    private val flushMutex = Mutex()

    @Volatile private var scheduled: Job? = null
    @Volatile private var retryJob: Job? = null
    @Volatile private var failures: Int = 0
    @Volatile private var useBatch: Boolean = config.batchPath != null
    @Volatile private var warnedBot: Boolean = false
    @Volatile private var warnedRejected: Boolean = false

    init {
        when (status) {
            AnalyticsStatus.ENABLED -> {
                registerLifecycle?.let { register ->
                    runCatching {
                        register({ requestFlush(immediate = true) }, { requestFlush(immediate = true) })
                    }
                }
            }
            AnalyticsStatus.NO_WEBSITE_ID ->
                AppLogger.i(TAG, "Analytics desligado: sem websiteId do Umami (garantir-umami.py <slug> --superficie app).")
            AnalyticsStatus.DEBUG_BUILD -> AppLogger.i(TAG, "Analytics desligado em build de depuração.")
            AnalyticsStatus.NO_PROJECT_SLUG -> AppLogger.w(TAG, "Analytics desligado: projectSlug vazio.")
        }
    }

    /**
     * Uma visita de tela: `url` = `/app/<rota>` ([analyticsScreenPath] — sem query, ids viram `:id`),
     * `title` = [title] ou o próprio caminho. Nunca passe dado da pessoa na rota.
     */
    fun trackScreen(route: String, title: String? = null) {
        if (!isEnabled) return
        val path = analyticsScreenPath(route)
        val titulo = title?.trim()?.take(ANALYTICS_MAX_STRING_VALUE)?.ifEmpty { null }
            ?: path.removePrefix("$ANALYTICS_PATH_PREFIX/").ifEmpty { "app" }
        enqueue(url = path, title = titulo, name = null, data = null)
    }

    /**
     * Um evento-chave ([name], até 50 caracteres) com propriedades opcionais — texto, número, booleano
     * ou enum, saneadas por [sanitizeEventData] (dado pessoal sai). [screen] = a tela onde aconteceu
     * (vira o `url`); sem ela, `/app`.
     */
    fun trackEvent(name: String, data: Map<String, Any?> = emptyMap(), screen: String? = null) {
        if (!isEnabled) return
        val nome = sanitizeEventName(name) ?: run {
            AppLogger.w(TAG, "Evento sem nome — descartado.")
            return
        }
        val dados = runCatching { sanitizeEventData(data) }.getOrNull()
        enqueue(url = analyticsScreenPath(screen.orEmpty()), title = null, name = nome, data = dados)
    }

    /** Eventos aguardando envio (diagnóstico/teste). */
    suspend fun pendingCount(): Int = if (status == AnalyticsStatus.ENABLED) queue.size() else 0

    /**
     * Envia agora o que está na fila. Devolve `true` se a fila ficou vazia, `false` se algo ficou para
     * depois (sem rede). Nunca lança.
     */
    suspend fun flush(): Boolean {
        if (status != AnalyticsStatus.ENABLED) return true
        return try {
            flushMutex.withLock { drain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.w(TAG, "Falha ao enviar analytics (${e::class.simpleName}) — fica na fila.")
            false
        }
    }

    private fun automation(): Boolean = runCatching { isAutomation() }.getOrDefault(false)

    private fun enqueue(url: String, title: String?, name: String?, data: JsonObject?) {
        val now = clock()
        val event = QueuedAnalyticsEvent(
            id = newAnalyticsEventId(now),
            url = url,
            title = title,
            name = name,
            data = data,
            timestamp = now / 1000,
            language = runCatching { language() }.getOrNull()?.trim()?.take(35)?.ifEmpty { null },
        )
        scope.launch {
            val size = queueMutex.withLock {
                queue.append(event)
                val total = queue.size()
                val excesso = total - config.maxQueuedEvents
                if (excesso > 0) {
                    queue.remove(queue.oldest(excesso).map { it.id })
                    AppLogger.w(TAG, "Fila de analytics cheia — $excesso evento(s) mais antigo(s) descartado(s).")
                }
                minOf(total, config.maxQueuedEvents)
            }
            requestFlush(immediate = size >= config.maxBatchSize)
        }
    }

    /** Agenda o envio: já ([immediate]) ou ao fim da janela do lote. Um agendamento por vez. */
    private fun requestFlush(immediate: Boolean) {
        if (status != AnalyticsStatus.ENABLED) return
        if (immediate) {
            scope.launch { flush() }
            return
        }
        if (scheduled?.isActive == true) return
        scheduled = scope.launch {
            delay(config.flushDelay)
            flush()
        }
    }

    /** Esvazia a fila em lotes. `false` = parou por falha temporária (nova tentativa agendada). */
    private suspend fun drain(): Boolean {
        val website = config.normalizedWebsiteId ?: return true
        while (true) {
            val lote = queueMutex.withLock { queue.oldest(config.maxBatchSize) }
            if (lote.isEmpty()) break
            val limite = clock() / 1000 - config.maxEventAge.inWholeSeconds
            val (velhos, validos) = lote.partition { it.timestamp < limite }
            if (velhos.isNotEmpty()) {
                queueMutex.withLock { queue.remove(velhos.map { it.id }) }
                AppLogger.w(TAG, "${velhos.size} evento(s) de analytics velhos demais — descartados.")
            }
            if (validos.isEmpty()) continue

            val ua = analyticsUserAgent(device(), appVersion())
            val id = if (config.sendInstallId) runCatching { installId() }.getOrNull() else null
            val bodies = validos.map { umamiBody(it, website, id) }

            if (useBatch) {
                when (val r = transport.sendBatch(bodies, ua)) {
                    is UmamiSendResult.Sent -> {
                        report(r)
                        queueMutex.withLock { queue.remove(validos.map { it.id }) }
                        continue
                    }
                    UmamiSendResult.BatchUnsupported -> {
                        AppLogger.w(TAG, "O Umami não aceitou o lote — enviando um a um por ${config.collectPath}.")
                        useBatch = false
                    }
                    is UmamiSendResult.Rejected -> Unit // isola um a um abaixo
                    is UmamiSendResult.RetryLater -> return scheduleRetry()
                }
            }
            for ((event, body) in validos.zip(bodies)) {
                when (val r = transport.sendOne(body, ua)) {
                    is UmamiSendResult.Sent -> report(r)
                    is UmamiSendResult.Rejected -> if (!warnedRejected) {
                        warnedRejected = true
                        AppLogger.w(TAG, "O Umami recusou um evento (HTTP ${r.status}) — descartado.")
                    }
                    is UmamiSendResult.RetryLater -> return scheduleRetry()
                    UmamiSendResult.BatchUnsupported -> Unit
                }
                queueMutex.withLock { queue.remove(listOf(event.id)) }
            }
        }
        failures = 0
        retryJob?.cancel()
        retryJob = null
        return true
    }

    private fun report(r: UmamiSendResult.Sent) {
        if (r.botDiscarded && !warnedBot) {
            warnedBot = true
            AppLogger.w(TAG, "O Umami leu o envio como robô e não gravou (User-Agent). Confira analyticsUserAgent.")
        }
        if (r.errors > 0) {
            AppLogger.w(TAG, "O Umami recusou ${r.errors} evento(s) do lote: ${r.firstError ?: "sem mensagem"}.")
        }
    }

    private fun scheduleRetry(): Boolean {
        failures += 1
        val multiplicador = 1L shl minOf(failures - 1, 10)
        val espera = minOf(AnalyticsConfig.BASE_RETRY_DELAY * multiplicador.toInt(), AnalyticsConfig.MAX_RETRY_DELAY)
        if (retryJob?.isActive != true) {
            retryJob = scope.launch {
                delay(espera)
                retryJob = null
                flush()
            }
        }
        return false
    }

    private fun umamiBody(event: QueuedAnalyticsEvent, website: String, distinctId: String?): JsonObject {
        val d = runCatching { device() }.getOrNull()
        val versao = runCatching { appVersion() }.getOrNull()?.trim()?.replace(Regex("^[=+\\-@]+"), "")?.take(50)
        return buildJsonObject {
            put("type", "event")
            put(
                "payload",
                buildJsonObject {
                    put("website", website)
                    put("hostname", config.hostname)
                    put("url", event.url)
                    event.title?.let { put("title", it) }
                    event.language?.let { put("language", it) }
                    d?.screen?.let { put("screen", it) }
                    event.name?.let { put("name", it) }
                    event.data?.let { put("data", it) }
                    put("timestamp", event.timestamp)
                    distinctId?.let { put("id", it) }
                    if (d != null) {
                        put("os", d.os)
                        put("device", d.deviceType)
                    }
                    put("browser", BROWSER)
                    versao?.ifEmpty { null }?.let { put("tag", it) }
                },
            )
        }
    }

    internal companion object {
        private const val TAG = "Analytics"

        /** O `browser` do Umami para todo evento de app — o painel separa app de site por ele. */
        const val BROWSER: String = "app"

        private fun defaultQueue(): AnalyticsQueue = try {
            BlobAnalyticsQueue(createBlobStore(BlobAnalyticsQueue.DIRECTORY))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.w(TAG, "Disco indisponível para a fila de analytics (${e::class.simpleName}) — fila em memória.")
            InMemoryAnalyticsQueue()
        }

        private fun defaultScope() = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> AppLogger.w(TAG, "Analytics: ${e::class.simpleName}") },
        )
    }
}

/**
 * **Analytics do app** (2.361.0) — telas e eventos-chave no Umami da fábrica
 * (`metricas.codecacto.com.br`), lido pelo **Nexus → Analytics**. Sem [initialize] (ou sem
 * `websiteId`), tudo é no-op.
 *
 * ```kotlin
 * // bootstrap, depois do initKmpLibCore (Android: a fila mora no disco da lib)
 * Analytics.initialize(AnalyticsConfig(websiteId = BuildConfig.UMAMI_WEBSITE_ID, projectSlug = "meu-app"))
 *
 * // raiz da navegação: uma visita por tela, sozinho
 * TrackNavigationScreens(navController)
 *
 * // evento-chave
 * Analytics.trackEvent("assinou", mapOf("plano" to "anual", "origem" to "paywall"))
 * ```
 *
 * **Dado pessoal NUNCA vai:** só o id anônimo da instalação. Rota e propriedades passam pelo
 * saneamento ([analyticsScreenPath], [sanitizeEventData]), mas a regra é de quem chama: nome, e-mail,
 * telefone, CPF, endereço e texto digitado não entram em evento.
 */
object Analytics {

    @Volatile
    private var _client: AnalyticsClient? = null

    /** O coletor do processo (`null` antes de [initialize]). */
    val client: AnalyticsClient? get() = _client

    /** `true` quando os eventos saem. */
    val isEnabled: Boolean get() = _client?.isEnabled == true

    /** Liga o analytics (chamar de novo troca a config). Nunca lança. */
    fun initialize(config: AnalyticsConfig) {
        _client = try {
            AnalyticsClient(config)
        } catch (e: Throwable) {
            AppLogger.w("Analytics", "Analytics não inicializou (${e::class.simpleName}).")
            null
        }
    }

    /** Variante com o coletor pronto (teste). */
    fun initialize(client: AnalyticsClient) {
        _client = client
    }

    /** Visita de tela (`/app/<rota>`). No-op sem [initialize]. */
    fun trackScreen(route: String, title: String? = null) {
        _client?.trackScreen(route, title)
    }

    /** Evento-chave. No-op sem [initialize]. */
    fun trackEvent(name: String, data: Map<String, Any?> = emptyMap(), screen: String? = null) {
        _client?.trackEvent(name, data, screen)
    }

    /** Envia já o que está na fila. `true` = fila vazia. */
    suspend fun flush(): Boolean = _client?.flush() ?: true

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _client = null
    }
}
