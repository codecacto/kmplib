package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.analytics.Analytics
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import br.com.codecacto.kmplib.core.storage.createBlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.core.util.isCanonicalUuid
import br.com.codecacto.kmplib.platform.install.InstallId
import br.com.codecacto.kmplib.push.action.PushAction
import br.com.codecacto.kmplib.push.action.PushActions
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Uma abertura à espera de envio. [installId] é capturado no toque (`InstallId.forTelemetry()`:
 * `null` em app infantil e em processo de teste).
 */
@Serializable
internal data class PendingPushOpen(
    val campaign: String,
    val platform: String,
    val installId: String? = null,
    val atMillis: Long,
)

/** O que fica no disco: campanhas já contadas (as mais recentes no fim) e a fila de envio. */
@Serializable
internal data class PushOpenLedger(
    val recorded: List<String> = emptyList(),
    val pending: List<PendingPushOpen> = emptyList(),
)

/** Resultado do envio de UMA abertura. */
internal enum class PushOpenSendResult {
    /** 2xx — conta como registrada. */
    SENT,

    /** Rede, 404 (rota ainda não publicada), 408, 429, 5xx — fica na fila e tenta de novo. */
    RETRY,

    /** Outro 4xx (corpo recusado) — sai da fila, sem repetir para sempre. */
    REJECTED,
}

/** Onde o livro das aberturas mora. Implementações nunca lançam. */
internal interface PushOpenLedgerStore {
    suspend fun load(): PushOpenLedger
    suspend fun save(ledger: PushOpenLedger): Boolean
}

private val openJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Livro em UM arquivo sobre o [BlobStore] da lib (fora do backup). Ilegível = recomeça vazio. */
internal class BlobPushOpenLedgerStore(private val store: BlobStore) : PushOpenLedgerStore {
    override suspend fun load(): PushOpenLedger = try {
        store.read(BLOB_ID)?.let { openJson.decodeFromString(PushOpenLedger.serializer(), it.decodeToString()) }
            ?: PushOpenLedger()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.w(PushOpenRecorder.TAG, "Registro de aberturas ilegível (${e::class.simpleName}) — recomeçando.")
        PushOpenLedger()
    }

    override suspend fun save(ledger: PushOpenLedger): Boolean = try {
        store.write(BLOB_ID, openJson.encodeToString(PushOpenLedger.serializer(), ledger).encodeToByteArray())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.w(PushOpenRecorder.TAG, "Não foi possível gravar o registro de aberturas (${e::class.simpleName}).")
        false
    }

    companion object {
        /** Diretório (área privada do app, fora do backup). */
        const val DIRECTORY: String = "kmplib_push_aberturas"
        const val BLOB_ID: String = "aberturas.json"
    }
}

/** Para onde a abertura vai: o apps-api da [PushRegistrationConfig] do processo. */
internal data class PushOpenTarget(val projectSlug: String, val appsApiBaseUrl: String)

/**
 * O contador de **quem abriu cada notificação de campanha** do Nexus (2.362.0), sem tela.
 *
 * A cada toque com `cc_campanha` (UUID): uma vez por campanha por aparelho, guarda a abertura numa
 * fila pequena em disco ([MAX_PENDING]) e envia `POST {appsApiBaseUrl}/public/push/aberturas`
 * `{campanha, projeto, plataforma, installId?}` (sem `Authorization`). Falha temporária (sem rede,
 * 404 enquanto a rota não está no ar, 429, 5xx) fica na fila e tenta de novo com espera crescente
 * (30 s → 15 min) e a cada [flush] (abertura do app, `PushRegistration.initialize`). Abertura com
 * mais de [MAX_AGE] sai sem envio. Se o `Analytics` estiver ligado, conta também
 * `push_aberto {acao}` — sem nenhum dado da pessoa.
 *
 * Nunca lança e nunca segura quem chamou: [onTap] só agenda.
 */
internal class PushOpenRecorder(
    private val store: suspend () -> PushOpenLedgerStore,
    private val target: () -> PushOpenTarget?,
    private val send: suspend (url: String, body: String) -> PushOpenSendResult,
    private val platform: () -> String,
    private val installId: suspend () -> String?,
    private val trackEvent: (name: String, data: Map<String, Any?>) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { currentTimeMillis() },
    private val retryDelay: (attempt: Int) -> Duration = ::defaultRetryDelay,
) {
    private val mutex = Mutex()
    private val flushMutex = Mutex()

    @Volatile private var failures = 0
    @Volatile private var retryJob: Job? = null

    /** Toque numa notificação: agenda a contagem. Retorna o job (teste); nunca lança. */
    fun onTap(data: Map<String, String>): Job? {
        val campaign = data[PushActions.KEY_CAMPAIGN]?.trim()?.lowercase()?.takeIf(::isCanonicalUuid) ?: return null
        val acao = actionLabel(PushActions.parse(data))
        return scope.launch {
            if (record(campaign)) {
                runCatching { trackEvent(EVENT_NAME, mapOf("acao" to acao)) }
                flush()
            }
        }
    }

    /** Põe a abertura na fila se a campanha ainda não foi contada neste aparelho. `true` = nova. */
    internal suspend fun record(campaign: String): Boolean = safe(false) {
        val plataforma = runCatching { platform() }.getOrDefault(UNKNOWN_PLATFORM)
        val id = runCatching { installId() }.getOrNull()
        mutex.withLock {
            val s = store()
            val ledger = s.load()
            if (campaign in ledger.recorded || ledger.pending.any { it.campaign == campaign }) return@withLock false
            val novo = PendingPushOpen(campaign, plataforma, id, clock())
            s.save(ledger.copy(pending = (ledger.pending + novo).takeLast(MAX_PENDING)))
            true
        }
    }

    /**
     * Envia o que está na fila. `true` = fila vazia; `false` = algo ficou (sem destino ainda — sem
     * `PushRegistration.initialize` — ou falha temporária, que reagenda). Nunca lança.
     */
    suspend fun flush(): Boolean = safe(false) {
        flushMutex.withLock { drain() }
    }

    private suspend fun drain(): Boolean {
        val destino = target() ?: return mutex.withLock { store().load().pending.isEmpty() }
        val agora = clock()
        val fila = mutex.withLock {
            val s = store()
            val ledger = s.load()
            val (validas, velhas) = ledger.pending.partition { agora - it.atMillis <= MAX_AGE.inWholeMilliseconds }
            if (velhas.isNotEmpty()) {
                s.save(ledger.copy(pending = validas))
                AppLogger.w(TAG, "${velhas.size} abertura(s) de push velhas demais — descartadas.")
            }
            validas
        }
        val url = destino.appsApiBaseUrl.trimEnd('/') + PATH
        for (open in fila) {
            val body = openBody(open, destino.projectSlug)
            when (runCatching { send(url, body) }.getOrDefault(PushOpenSendResult.RETRY)) {
                PushOpenSendResult.SENT -> settle(open.campaign, recorded = true)
                PushOpenSendResult.REJECTED -> {
                    AppLogger.w(TAG, "Abertura de push recusada pelo servidor — descartada.")
                    settle(open.campaign, recorded = true)
                }
                PushOpenSendResult.RETRY -> {
                    scheduleRetry()
                    return false
                }
            }
        }
        failures = 0
        return true
    }

    private suspend fun settle(campaign: String, recorded: Boolean) = mutex.withLock {
        val s = store()
        val ledger = s.load()
        val enviadas = if (recorded) (ledger.recorded.filterNot { it == campaign } + campaign).takeLast(MAX_RECORDED) else ledger.recorded
        s.save(PushOpenLedger(recorded = enviadas, pending = ledger.pending.filterNot { it.campaign == campaign }))
    }

    private fun scheduleRetry() {
        failures += 1
        if (retryJob?.isActive == true) return
        val espera = retryDelay(failures)
        retryJob = scope.launch {
            delay(espera)
            retryJob = null
            flush()
        }
    }

    private suspend inline fun <T> safe(fallback: T, crossinline block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.w(TAG, "Contagem de abertura de push falhou (${e::class.simpleName}).")
        fallback
    }

    internal companion object {
        const val TAG = "PushOpenings"
        const val PATH = "/public/push/aberturas"
        const val EVENT_NAME = "push_aberto"
        const val UNKNOWN_PLATFORM = "android"

        /** Fila de envio: no máximo isto (as mais antigas saem). */
        const val MAX_PENDING = 20

        /** Campanhas lembradas como já contadas (FIFO). */
        const val MAX_RECORDED = 200

        val MAX_AGE: Duration = 7.days

        fun defaultRetryDelay(attempt: Int): Duration {
            val mult = 1L shl (attempt - 1).coerceIn(0, 10)
            return minOf(30.seconds * mult.toInt(), 15.minutes)
        }

        /** A ação para o evento de analytics — só o tipo, nunca a URL nem o id do comunicado. */
        fun actionLabel(action: PushAction?): String = when (action) {
            is PushAction.OpenPremium -> PushActions.ACTION_PREMIUM
            is PushAction.OpenAnnouncement -> PushActions.ACTION_ANNOUNCEMENT
            is PushAction.OpenLink -> PushActions.ACTION_LINK
            else -> PushActions.ACTION_APP
        }

        /** O corpo do `POST /public/push/aberturas` — puro (testado). */
        fun openBody(open: PendingPushOpen, projectSlug: String): String = buildJsonObject {
            put("campanha", open.campaign)
            put("projeto", projectSlug)
            put("plataforma", open.platform)
            open.installId?.let { put("installId", it) }
        }.toString()

        /** 2xx enviada; 404/408/429/5xx (e rede) tenta de novo; outro 4xx desiste. */
        fun classify(status: Int?): PushOpenSendResult = when {
            status == null -> PushOpenSendResult.RETRY
            status in 200..299 -> PushOpenSendResult.SENT
            status == 404 || status == 408 || status == 429 || status >= 500 -> PushOpenSendResult.RETRY
            status in 400..499 -> PushOpenSendResult.REJECTED
            else -> PushOpenSendResult.RETRY
        }
    }
}

/**
 * **Quem abriu cada notificação** (2.362.0) — ligado sozinho, nada a fazer no app.
 *
 * Todo toque que chega ao hub do push ([br.com.codecacto.kmplib.push.PushEvents]) — inclusive o que
 * abriu o app a frio, no Android e no iOS, antes de qualquer tela montar — passa por aqui: se tem
 * `cc_campanha`, a abertura é guardada em disco e enviada ao apps-api do [PushRegistration]
 * (`POST /public/push/aberturas`), uma vez por campanha por aparelho. Antes do
 * `PushRegistration.initialize` ela só espera na fila; o `initialize` envia (app que monta o próprio
 * [PushRegistrationClient]: [initialize] com a mesma config). O toque segue para o
 * `PushTapHost` sem esperar nada disto.
 */
object PushOpenings {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> AppLogger.w(PushOpenRecorder.TAG, "Abertura de push: ${e::class.simpleName}") },
    )

    private val defaultStore: PushOpenLedgerStore by lazy {
        try {
            BlobPushOpenLedgerStore(createBlobStore(BlobPushOpenLedgerStore.DIRECTORY))
        } catch (e: Throwable) {
            AppLogger.w(PushOpenRecorder.TAG, "Disco indisponível para as aberturas de push (${e::class.simpleName}) — em memória.")
            BlobPushOpenLedgerStore(InMemoryBlobStore())
        }
    }

    /** Cliente próprio, sem a sessão do app (rota pública, sem `Authorization`). */
    private val httpClient: HttpClient by lazy { createHttpClient(HttpClientOptions()) }

    private val platformName: String by lazy {
        runCatching { platformPushDeviceSource().platform }.getOrDefault(PushOpenRecorder.UNKNOWN_PLATFORM)
    }

    @Volatile
    private var explicitTarget: PushOpenTarget? = null

    @Volatile
    internal var recorder: PushOpenRecorder = defaultRecorder()

    /**
     * Destino explícito — só para o app que monta o próprio [PushRegistrationClient] em vez de
     * [PushRegistration.initialize] (ex.: token FCM no iOS). Usa o projeto e o apps-api de [config] (o
     * `httpClient` dela NÃO é usado: a rota é pública). Envia o que estiver na fila.
     */
    fun initialize(config: PushRegistrationConfig) {
        explicitTarget = PushOpenTarget(config.projectSlug, config.appsApiBaseUrl)
        flush()
    }

    private fun defaultRecorder() = PushOpenRecorder(
        store = { defaultStore },
        target = {
            explicitTarget
                ?: PushRegistration.client?.config?.let { PushOpenTarget(it.projectSlug, it.appsApiBaseUrl) }
        },
        send = { url, body -> sendOpen(url, body) },
        platform = { platformName },
        installId = { InstallId.forTelemetry() },
        trackEvent = { name, data -> Analytics.trackEvent(name, data) },
        scope = scope,
    )

    private suspend fun sendOpen(url: String, body: String): PushOpenSendResult = try {
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        PushOpenRecorder.classify(response.status.value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        PushOpenRecorder.classify(null)
    }

    /** Chamado pelo hub do push a cada toque. Só agenda; nunca lança. */
    internal fun onTap(data: Map<String, String>) {
        runCatching { recorder.onTap(data) }
    }

    /** Envia as aberturas guardadas (o `PushRegistration.initialize` chama). Nunca lança. */
    fun flush() {
        scope.launch { recorder.flush() }
    }

    /** Apenas para testes. */
    internal fun resetForTesting() {
        explicitTarget = null
        recorder = defaultRecorder()
    }
}
