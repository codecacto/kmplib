package br.com.codecacto.kmplib.presence

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Configuração do sinal de presença diário (2.326.0).
 *
 * @param projectSlug slug do projeto no Nexus/apps-api (o `project` do corpo).
 * @param httpClient o `HttpClient` do app — idealmente o `createHttpClient` da lib (log de requisição
 *   padrão: método · host · caminho · status).
 * @param appsApiBaseUrl base pública do apps-api (sem barra final). Default de produção.
 * @param payingProvider "esta instalação é pagante?" — `true`/`false` quando o app sabe, `null` quando
 *   não sabe ou não vende. Para o app que vende pela loja:
 *   `{ MonetizationManager.awaitPayingOrNull() }` (`kmplib-monetization`). `null` (default) manda
 *   `"pagante": null`. Recebido como função para o `kmplib-central` não depender da monetização.
 * @param payingTimeout teto de espera do [payingProvider]. Passou → `"pagante": null` (nunca prende o
 *   envio; o sinal é de presença, o "pagante" é complemento).
 * @param sendInAutomation `false` (default): build de teste (`AutomationMode` — dublê da loja, Test
 *   Harness, marca do runner de QA) não conta como usuário. `true` só na suíte que testa o próprio sinal.
 */
data class AppPresenceConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val payingProvider: (suspend () -> Boolean?)? = null,
    val payingTimeout: Duration = DEFAULT_PAYING_TIMEOUT,
    val sendInAutomation: Boolean = false,
) {
    init {
        require(projectSlug.isNotBlank()) { "projectSlug do AppPresence não pode ser vazio." }
    }

    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"

        /** Teto default de espera pelo "pagante" — cobre a loja lendo o `CustomerInfo` sem cache. */
        val DEFAULT_PAYING_TIMEOUT: Duration = 5.seconds
    }
}

/** Resultado de uma tentativa ([AppPresenceClient.sendIfDue]). */
enum class AppPresenceResult {
    /** O servidor aceitou (2xx) — a data de hoje foi gravada. */
    SENT,

    /** Já enviado hoje (dia de calendário local) — nada foi feito. */
    ALREADY_SENT_TODAY,

    /** Falhou (rede, 429, 5xx…) — a data NÃO foi gravada; a próxima abertura tenta de novo. */
    FAILED,

    /** Outra tentativa já está em andamento neste processo. */
    IN_FLIGHT,

    /** Processo de teste automatizado e [AppPresenceConfig.sendInAutomation] desligado. */
    SKIPPED_AUTOMATION,
}

/**
 * Cliente do **sinal de presença**: `POST {appsApi}/public/presence` com
 * `{"project":"<slug>","platform":"android|ios","pagante":true|false|null}` — **no máximo uma vez por
 * dia de calendário local por instalação**. É com ele que o Nexus conta usuários ativos por cidade
 * (o servidor estima a cidade pelo IP da requisição e descarta o IP).
 *
 * **Não manda nada que identifique a pessoa ou o aparelho**: nem id de instalação, nem conta, nem
 * localização. Por isso vale igual em app de público infantil.
 *
 * Regras:
 * - a data do último envio bem-sucedido fica nas [AppPreferences] da lib (SharedPreferences /
 *   NSUserDefaults) — memória do aparelho, não da conta;
 * - falha (rede, HTTP fora de 2xx, inclusive o 429 do rate limit) **não grava a data** e não repete
 *   sozinha: a próxima volta ao primeiro plano tenta de novo;
 * - nunca lança (salvo cancelamento) — é best-effort e não pode derrubar a abertura do app.
 *
 * @param preferences onde guardar o dia do último envio (default: as da lib, lidas sob demanda — no
 *   Android exigem `initKmpLibCore`/`KmpLib.init` antes).
 * @param today o dia de calendário LOCAL de agora (troque em teste).
 * @param isAutomation o processo é teste automatizado? (troque em teste).
 */
class AppPresenceClient(
    val config: AppPresenceConfig,
    preferences: AppPreferences? = null,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val isAutomation: () -> Boolean = { runCatching { AutomationMode.isActive }.getOrDefault(false) },
    private val platform: String = currentPlatform,
) {
    private val preferencesOverride = preferences
    private val mutex = Mutex()

    /** Lê e grava aqui; resolvido na primeira tentativa (no Android exige a lib inicializada). */
    private fun prefs(): AppPreferences = preferencesOverride ?: appPreferences()

    /**
     * Envia o sinal se ainda não foi enviado hoje. Chamadas simultâneas: só uma vai à rede
     * ([AppPresenceResult.IN_FLIGHT] para as outras).
     */
    suspend fun sendIfDue(): AppPresenceResult {
        if (!config.sendInAutomation && isAutomation()) return AppPresenceResult.SKIPPED_AUTOMATION
        if (!mutex.tryLock()) return AppPresenceResult.IN_FLIGHT
        try {
            val day = today().toString()
            val store = runCatching { prefs() }.getOrElse {
                AppLogger.w(TAG, "Preferências indisponíveis — sinal de presença adiado.")
                return AppPresenceResult.FAILED
            }
            val last = runCatching { store.getString(KEY_LAST_SENT_DAY, "") }.getOrDefault("")
            if (last == day) return AppPresenceResult.ALREADY_SENT_TODAY

            val paying = resolvePaying()
            if (!post(paying)) return AppPresenceResult.FAILED

            runCatching { store.setString(KEY_LAST_SENT_DAY, day) }
                .onFailure { AppLogger.w(TAG, "Não foi possível gravar o dia do sinal de presença.") }
            return AppPresenceResult.SENT
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun resolvePaying(): Boolean? {
        val provider = config.payingProvider ?: return null
        return try {
            withTimeoutOrNull(config.payingTimeout) { provider() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun post(paying: Boolean?): Boolean {
        val url = config.appsApiBaseUrl.trimEnd('/') + PATH
        val body = buildJsonObject {
            put("project", JsonPrimitive(config.projectSlug))
            put("platform", JsonPrimitive(platform))
            put("pagante", paying?.let(::JsonPrimitive) ?: JsonNull)
        }.toString()
        val result: ApiResult<Unit> = handleApiCall {
            config.httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            Unit
        }
        return when (result) {
            is ApiResult.Success -> true
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Sinal de presença não enviado (code=${result.code}) — tenta na próxima abertura.")
                false
            }
            ApiResult.Loading -> false
        }
    }

    companion object {
        /** Caminho no apps-api. */
        const val PATH: String = "/public/presence"

        /** Chave nas preferências (dia ISO `AAAA-MM-DD` do último envio aceito). Estável. */
        const val KEY_LAST_SENT_DAY: String = "kmplib.presence.lastSentDay"

        private const val TAG = "AppPresence"
    }
}

/**
 * O sinal de presença do processo. O [br.com.codecacto.kmplib.core.central.CentralServices] já o liga
 * (`CentralServicesConfig.enablePresence`, default `true`): a cada vez que o app vem para o primeiro
 * plano — `ProcessLifecycleOwner` `ON_START` no Android, `UIApplicationDidBecomeActiveNotification` no
 * iOS, e na própria abertura — chama [AppPresenceClient.sendIfDue], que vai à rede no máximo uma vez
 * por dia. O app normalmente não chama nada aqui.
 */
object AppPresence {

    @Volatile
    private var _client: AppPresenceClient? = null

    @Volatile
    private var listening = false

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> AppLogger.w("AppPresence", "Sinal de presença falhou: ${e::class.simpleName}") },
    )

    /** O cliente do processo (`null` antes de [initialize]). */
    val client: AppPresenceClient? get() = _client

    val isInitialized: Boolean get() = _client != null

    /**
     * Liga o sinal: guarda o cliente e passa a escutar a vinda ao primeiro plano (uma vez por processo;
     * chamar de novo só troca o cliente).
     *
     * @param listenToForeground `false` só em teste — não registra o observador da plataforma.
     */
    fun initialize(config: AppPresenceConfig, listenToForeground: Boolean = true) {
        initialize(AppPresenceClient(config), listenToForeground)
    }

    /** Variante com o cliente pronto (teste, ou preferências próprias). */
    fun initialize(client: AppPresenceClient, listenToForeground: Boolean = true) {
        _client = client
        if (listenToForeground && !listening) {
            listening = true
            val ok = runCatching { registerAppForegroundListener { onForeground() } }.getOrDefault(false)
            if (!ok) {
                AppLogger.w("AppPresence", "Observador de primeiro plano indisponível — envio só na abertura.")
                onForeground()
            }
        }
    }

    /** O app veio para o primeiro plano: tenta o envio do dia, fora da thread principal. */
    fun onForeground() {
        val c = _client ?: return
        scope.launch { c.sendIfDue() }
    }

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _client = null
    }
}

/**
 * Registra [onForeground] para cada vinda do app ao primeiro plano, **inclusive a atual** se o app já
 * está nele. Devolve `false` se a plataforma não oferece o sinal (o chamador envia uma vez na hora).
 */
internal expect fun registerAppForegroundListener(onForeground: () -> Unit): Boolean
