package br.com.codecacto.kmplib.modalevents

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.install.InstallId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.concurrent.Volatile
import kotlin.time.Duration
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * ─────────────── Estatística dos MODAIS (comunicado + avaliação) — 2.335.0 ───────────────
 *
 * Contrato (fonte única dos três lados — kmplib, apps-api, Nexus):
 * `docs/tarefas/2026-10-29-estatistica-dos-modais.md` §2. Modelo seguido: o `AdStats` dos house ads
 * (fire-and-forget, `debug` de build de depuração, servidor descarta o que não é produção).
 */

/** Superfície do evento — campo `surface` do contrato. */
enum class ModalSurface(val wireName: String) {
    ANNOUNCEMENT("announcement"),
    REVIEW("review"),
}

/** Evento — campo `event` do contrato. Cada um pertence a UMA superfície ([surface]). */
enum class ModalEventType(val wireName: String, val surface: ModalSurface) {
    /** Comunicado apareceu (uma vez por exibição). */
    ANNOUNCEMENT_SHOWN("shown", ModalSurface.ANNOUNCEMENT),

    /** Tocou no botão de ação do comunicado. */
    ANNOUNCEMENT_ACTION_CLICK("action_click", ModalSurface.ANNOUNCEMENT),

    /** Fechou pelo "Entendi". */
    ANNOUNCEMENT_DISMISS_GOTIT("dismiss_gotit", ModalSurface.ANNOUNCEMENT),

    /** Fechou pelo X, voltar ou toque fora. */
    ANNOUNCEMENT_DISMISS_CLOSE("dismiss_close", ModalSurface.ANNOUNCEMENT),

    /** Pedido de avaliação apareceu. */
    REVIEW_SHOWN("shown", ModalSurface.REVIEW),

    /** "Sim" → loja. */
    REVIEW_POSITIVE("positive", ModalSurface.REVIEW),

    /** "Não". */
    REVIEW_NEGATIVE("negative", ModalSurface.REVIEW),

    /** Enviou o formulário do "Não". */
    REVIEW_FEEDBACK_SENT("feedback_sent", ModalSurface.REVIEW),

    /** Fechou sem responder. */
    REVIEW_DISMISSED("dismissed", ModalSurface.REVIEW),
}

/** Um evento no lote (item de `events[]`). `refId` e `trigger` saem como `null` explícito quando ausentes. */
@Serializable
data class ModalEventPayload(
    val surface: String,
    val event: String,
    val refId: String? = null,
    val trigger: String? = null,
    val occurredAt: Long,
)

/** Corpo do `POST /public/modal-events`. */
@Serializable
data class ModalEventBatch(
    val project: String,
    val platform: String,
    val appVersion: String? = null,
    val installId: String? = null,
    val debug: Boolean = false,
    val events: List<ModalEventPayload>,
)

/** Envia um lote. Injetável (teste). `true` = aceito; falha é logada e o lote descartado (fire-and-forget). */
fun interface ModalEventsRecorder {
    suspend fun send(batch: ModalEventBatch): Boolean
}

/**
 * O recorder REST padrão — `POST {appsApiBaseUrl}/public/modal-events` (sem auth; o servidor responde
 * sempre 202). Erro de rede/4xx/5xx é logado e ignorado: estatística NUNCA quebra o app.
 */
class RestModalEventsRecorder(
    private val httpClient: HttpClient,
    private val appsApiBaseUrl: String = ModalEventsConfig.DEFAULT_APPS_API_BASE_URL,
) : ModalEventsRecorder {
    override suspend fun send(batch: ModalEventBatch): Boolean {
        val url = appsApiBaseUrl.trimEnd('/') + PATH
        val body = modalEventsJson.encodeToString(ModalEventBatch.serializer(), batch)
        val result: ApiResult<Unit> = handleApiCall {
            httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            Unit
        }
        if (result is ApiResult.Error) {
            AppLogger.w(TAG, "Eventos de modal não enviados (code=${result.code}, best-effort, descartados).")
            return false
        }
        return true
    }

    companion object {
        /** Caminho no apps-api. */
        const val PATH: String = "/public/modal-events"
        private const val TAG = "ModalEvents"
    }
}

internal val modalEventsJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Configuração do [ModalEvents]. O `CentralServices.initialize` monta a partir da dele.
 *
 * @property projectSlug slug do projeto no Nexus (`project` do contrato).
 * @property appVersion versão do app; `null` = a do sistema ([BuildInfo.appVersionName]).
 * @property flushDelay janela do lote: o primeiro evento agenda o envio para daqui a este tempo, e o
 *   que chegar nesse meio vai junto (o comunicado mostrado e fechado em 2 s vira UM POST).
 * @property sendInAutomation `true` só para a suíte que testa o próprio envio — teste nunca conta.
 */
data class ModalEventsConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val appVersion: String? = null,
    val flushDelay: Duration = DEFAULT_FLUSH_DELAY,
    val sendInAutomation: Boolean = false,
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
        val DEFAULT_FLUSH_DELAY: Duration = 3.seconds

        /** Teto de eventos por POST (contrato: 1..50). */
        const val MAX_EVENTS_PER_BATCH: Int = 50
    }
}

/**
 * O coletor de um processo, fora do singleton (testável). Junta os eventos em lote curto
 * ([ModalEventsConfig.flushDelay]; 50 eventos disparam na hora) e manda pelo [recorder] num escopo
 * próprio, sem bloquear quem registra.
 *
 * - Teste ([AutomationMode]) não registra nada (salvo [ModalEventsConfig.sendInAutomation]).
 * - `installId` = [InstallId.forTelemetry]; `debug` = build de depuração (o servidor descarta).
 * - Comunicado sem `refId` é descartado (o contrato exige); `refId`/`trigger` em branco viram `null`.
 * - Falha de envio descarta o lote: estatística é melhor-esforço, como a dos anúncios.
 */
class ModalEventsClient(
    val config: ModalEventsConfig,
    private val recorder: ModalEventsRecorder = RestModalEventsRecorder(config.httpClient, config.appsApiBaseUrl),
    private val scope: CoroutineScope = defaultScope(),
    private val installId: suspend () -> String? = { InstallId.forTelemetry() },
    private val isAutomation: () -> Boolean = { runCatching { AutomationMode.isActive }.getOrDefault(false) },
    private val isDebugBuild: () -> Boolean = { BuildInfo.isDebug },
    private val clock: () -> Long = { currentTimeMillis() },
    private val platform: String = currentPlatform,
) {
    private val mutex = Mutex()
    private val buffer = ArrayList<ModalEventPayload>()
    private var scheduled: Job? = null

    /** Eventos aguardando envio (teste/diagnóstico). */
    val pendingCount: Int get() = buffer.size

    /** Registra [event]. Nunca bloqueia nem lança. */
    fun record(event: ModalEventType, refId: String? = null, trigger: String? = null) {
        if (!config.sendInAutomation && isAutomation()) return
        val ref = refId?.trim()?.ifEmpty { null }
        if (event.surface == ModalSurface.ANNOUNCEMENT && ref == null) {
            AppLogger.w(TAG, "Evento de comunicado sem refId — descartado.")
            return
        }
        val payload = ModalEventPayload(
            surface = event.surface.wireName,
            event = event.wireName,
            refId = if (event.surface == ModalSurface.REVIEW) null else ref,
            trigger = if (event.surface == ModalSurface.REVIEW) trigger?.trim()?.ifEmpty { null } else null,
            occurredAt = clock(),
        )
        scope.launch {
            val sendNow = mutex.withLock {
                if (buffer.size >= MAX_BUFFER) buffer.removeAt(0)
                buffer += payload
                if (buffer.size >= ModalEventsConfig.MAX_EVENTS_PER_BATCH) {
                    true
                } else {
                    if (scheduled?.isActive != true) {
                        scheduled = scope.launch {
                            delay(config.flushDelay)
                            flush()
                        }
                    }
                    false
                }
            }
            if (sendNow) flush()
        }
    }

    /** Envia agora tudo o que está no lote (em POSTs de até 50). */
    suspend fun flush() {
        while (true) {
            val chunk = mutex.withLock {
                if (buffer.isEmpty()) return
                val n = minOf(buffer.size, ModalEventsConfig.MAX_EVENTS_PER_BATCH)
                val taken = buffer.subList(0, n).toList()
                buffer.subList(0, n).clear()
                taken
            }
            val batch = ModalEventBatch(
                project = config.projectSlug,
                platform = platform,
                appVersion = (config.appVersion ?: runCatching { BuildInfo.appVersionName }.getOrNull())
                    ?.ifBlank { null },
                installId = runCatching { installId() }.getOrNull(),
                debug = runCatching { isDebugBuild() }.getOrDefault(false),
                events = chunk,
            )
            try {
                recorder.send(batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.w(TAG, "Falha ao enviar eventos de modal (silencioso): ${e::class.simpleName}")
            }
        }
    }

    companion object {
        private const val TAG = "ModalEvents"

        /** Teto do lote em memória — sem rede por muito tempo, o mais velho sai. */
        internal const val MAX_BUFFER: Int = 200

        private fun defaultScope() = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> AppLogger.w(TAG, "Eventos de modal: ${e::class.simpleName}") },
        )
    }
}

/**
 * **Estatística dos modais** (2.335.0) — o singleton que o comunicado (`RemoteAnnouncementHost`) e o
 * pedido de avaliação (`AppReviewDialog`) usam. Sem [initialize], tudo é no-op.
 *
 * ```kotlin
 * // o CentralServices.initialize(...) já faz; app que inicializa os serviços um a um:
 * ModalEvents.initialize(ModalEventsConfig(projectSlug = "meu-app", httpClient = http))
 * ```
 */
object ModalEvents {

    @Volatile
    private var _client: ModalEventsClient? = null

    /** O coletor do processo (`null` antes de [initialize]). */
    val client: ModalEventsClient? get() = _client

    val isInitialized: Boolean get() = _client != null

    /** Liga o coletor (chamar de novo troca a config). */
    fun initialize(config: ModalEventsConfig) {
        _client = ModalEventsClient(config)
    }

    /** Variante com o coletor pronto (teste, recorder próprio). */
    fun initialize(client: ModalEventsClient) {
        _client = client
    }

    /** Registra [event] (no-op sem [initialize]). */
    fun record(event: ModalEventType, refId: String? = null, trigger: String? = null) {
        _client?.record(event, refId, trigger)
    }

    /** Envia já o lote pendente (ex.: antes de um fluxo que encerra o processo). */
    suspend fun flush() {
        _client?.flush()
    }

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _client = null
    }
}
