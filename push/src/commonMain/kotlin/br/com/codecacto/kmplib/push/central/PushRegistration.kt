package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.core.central.CentralServicesConfig
import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.firebase.auth.User
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.push.PushEvents
import br.com.codecacto.kmplib.push.PushNotificationListener
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Configuração do **registro central do aparelho** para as notificações do Nexus (2.360.0).
 *
 * @param projectSlug slug do projeto no Nexus (o `projeto` do cadastro). O projeto precisa existir lá.
 * @param httpClient o `HttpClient` do app — idealmente o `createHttpClient` da lib.
 * @param appsApiBaseUrl base pública do apps-api (sem barra final). Default de produção.
 * @param appId package Android / bundle id iOS (o `app` — no iOS é o tópico do APNs). `null`
 *   (default) = o do próprio binário, [BuildInfo.appId]; informe só se o app publica com outro.
 * @param appVersion versão mostrada no Nexus. `null` (default) = [BuildInfo.appVersionName].
 * @param registerInAutomation `false` (default): build de teste (`AutomationMode` — Test Lab da Play,
 *   runner de QA, dublê da loja, Test Harness) não cadastra aparelho (token de laboratório só vira
 *   lixo e falha de envio). `true` só na suíte que testa o próprio registro.
 */
data class PushRegistrationConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val appId: String? = null,
    val appVersion: String? = null,
    val registerInAutomation: Boolean = false,
) {
    init {
        require(projectSlug.isNotBlank()) { "projectSlug do PushRegistration não pode ser vazio." }
    }

    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"

        /** O mesmo projeto, cliente HTTP e apps-api do `CentralServices`. */
        fun from(central: CentralServicesConfig): PushRegistrationConfig = PushRegistrationConfig(
            projectSlug = central.projectSlug,
            httpClient = central.httpClient,
            appsApiBaseUrl = central.appsApiBaseUrl,
            appVersion = central.appVersion.ifBlank { null },
        )
    }
}

/** Resultado de uma tentativa de [PushRegistrationClient.register]/[PushRegistrationClient.unregister]. */
enum class PushRegistrationOutcome {
    /** O servidor aceitou o cadastro (2xx). */
    REGISTERED,

    /** O servidor aceitou a remoção (2xx). */
    REMOVED,

    /** Cadastro idêntico ao último aceito neste processo — nada foi enviado. */
    UNCHANGED,

    /** O aparelho ainda não tem token (FCM não subiu / APNs ainda não respondeu). O token novo
     *  ([PushNotificationListener.onNewToken]) refaz o registro sozinho, se houver [PushRegistrationClient.bind]. */
    NO_TOKEN,

    /** Sem package/bundle id ([BuildInfo.appId] nulo e [PushRegistrationConfig.appId] ausente). */
    NO_APP_ID,

    /** Processo de teste automatizado e [PushRegistrationConfig.registerInAutomation] desligado. */
    SKIPPED_AUTOMATION,

    /** [PushRegistration] não inicializado. */
    NOT_INITIALIZED,

    /** Rede, 4xx/5xx, 429 do rate limit — logado; a próxima abertura/troca tenta de novo. */
    FAILED,
}

/**
 * Cliente do cadastro central de aparelhos do apps-api (`POST /push/v1/aparelhos` e
 * `POST /push/v1/aparelhos/remover`) — é por ele que o **Nexus → Divulgação → Notificações** acha o
 * aparelho, no idioma dele, com a conta dele, e respeitando o interruptor de publicidade.
 *
 * Corpo do cadastro (contrato `docs/tarefas/2026-11-12-notificacoes-push-pelo-nexus.md`):
 * `{projeto, app, token, plataforma, provedor, projetoFcm?, conta?, idioma, versaoApp, publicidade}`.
 * - `idioma` = [appLanguageTag] (o idioma do aparelho casado com os que o app traduz — o idioma em
 *   que a pessoa lê o app; o servidor cai para pt-BR sem tradução);
 * - `conta` = o MESMO id que o app passa à identidade da loja (`MonetizationManager.bindIdentity` —
 *   `User.id` por default; a organização no multi-tenant) — o servidor resolve "assinante" por ele;
 * - `publicidade` = [PushMarketingPreference.enabled]; **app infantil (`AppAudience.KIDS`) manda
 *   sempre `false`** — criança não recebe publicidade, nem por push.
 *
 * Nunca lança (salvo cancelamento): falha de rede só vai ao log e o app segue. Chamadas são
 * serializadas; o mesmo cadastro já aceito neste processo não é reenviado.
 */
class PushRegistrationClient(
    val config: PushRegistrationConfig,
    val marketing: PushMarketingPreference,
    private val device: PushDeviceSource = platformPushDeviceSource(),
    private val isAutomation: () -> Boolean = { runCatching { AutomationMode.isActive }.getOrDefault(false) },
    private val isKids: () -> Boolean = { KmpLibAudience.isKids },
    private val languageTag: () -> String = { appLanguageTag() },
) {
    private val mutex = Mutex()

    @Volatile
    private var lastSent: JsonObject? = null

    @Volatile
    private var lastToken: String? = null

    /**
     * Cadastra (ou atualiza) este aparelho com a [account] dada (`null` = sem conta: app sem login).
     * Ao mudar o interruptor de publicidade, use [PushMarketingPreference.setEnabled] — com [bind]
     * ativo o re-registro é sozinho.
     */
    suspend fun register(account: String?): PushRegistrationOutcome = mutex.withLock {
        if (!config.registerInAutomation && isAutomation()) return@withLock PushRegistrationOutcome.SKIPPED_AUTOMATION
        val appId = (config.appId ?: BuildInfo.appId)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withLock PushRegistrationOutcome.NO_APP_ID.also {
                AppLogger.w(TAG, "Sem package/bundle id — aparelho não cadastrado.")
            }
        val token = device.token() ?: return@withLock PushRegistrationOutcome.NO_TOKEN
        marketing.awaitLoaded()
        val body = buildRegistrationBody(
            projectSlug = config.projectSlug,
            appId = appId,
            token = token,
            platform = device.platform,
            provider = device.provider,
            fcmProjectId = device.fcmProjectId(),
            account = account?.trim()?.takeIf { it.isNotEmpty() },
            language = languageTag(),
            appVersion = (config.appVersion ?: BuildInfo.appVersionName)?.trim()?.takeIf { it.isNotEmpty() }
                ?: UNKNOWN_VERSION,
            marketing = marketing.enabled.value && !isKids(),
        )
        if (body == lastSent) return@withLock PushRegistrationOutcome.UNCHANGED
        if (post(REGISTER_PATH, body)) {
            lastSent = body
            lastToken = token
            PushRegistrationOutcome.REGISTERED
        } else {
            PushRegistrationOutcome.FAILED
        }
    }

    /**
     * Tira este aparelho do cadastro (logout). Usa o último token cadastrado neste processo ou, sem
     * ele, o token atual. Depois disto, o próximo [register] envia de novo mesmo se igual.
     */
    suspend fun unregister(): PushRegistrationOutcome = mutex.withLock {
        if (!config.registerInAutomation && isAutomation()) return@withLock PushRegistrationOutcome.SKIPPED_AUTOMATION
        val token = lastToken ?: device.token() ?: return@withLock PushRegistrationOutcome.NO_TOKEN
        lastSent = null
        lastToken = null
        val body = buildJsonObject {
            put("projeto", config.projectSlug)
            put("token", token)
        }
        if (post(REMOVE_PATH, body)) PushRegistrationOutcome.REMOVED else PushRegistrationOutcome.FAILED
    }

    /**
     * **Amarra o cadastro à sessão — chame uma vez, na raiz do app**, em `LaunchedEffect(Unit)`
     * (suspende enquanto [accountIds] emitir):
     * - conta logada (inclusive a sessão restaurada na abertura) → [register] com ela;
     * - passou de logada para `null` (logout, sessão expirada) → [unregister];
     * - token novo do push ([PushEvents] `onNewToken`) ou troca do interruptor de publicidade →
     *   [register] de novo com a conta corrente.
     *
     * @param accountIds o id de quem assina (o mesmo de `MonetizationManager.bindIdentity`), `null` =
     *   ninguém logado.
     * @param registerSignedOut `true` = cadastra também sem conta (app SEM login — arquétipo A). Com
     *   `false` (default, app com login), aparelho sem sessão não fica no cadastro.
     *
     * O ouvinte de token entra no [PushEvents] com `receiveTaps = false`: não consome o toque que
     * abriu o app (esse é do [br.com.codecacto.kmplib.push.ui.PushTapHost]).
     */
    suspend fun bind(accountIds: Flow<String?>, registerSignedOut: Boolean = false) {
        val tokenTicks = MutableStateFlow(0L)
        val tokenListener = object : PushNotificationListener {
            override fun onNewToken(token: String) {
                tokenTicks.update { it + 1 }
            }
        }
        val registration = PushEvents.addListener(tokenListener, receivePendingTap = false, receiveTaps = false)
        try {
            marketing.awaitLoaded()
            var signedIn = false
            combine(accountIds.distinctUntilChanged(), marketing.enabled, tokenTicks) { account, _, _ -> account }
                .collect { account ->
                    when {
                        account != null -> {
                            signedIn = true
                            register(account)
                        }
                        registerSignedOut -> {
                            signedIn = false
                            register(null)
                        }
                        signedIn -> {
                            signedIn = false
                            unregister()
                        }
                    }
                }
        } finally {
            registration.remove()
        }
    }

    private suspend fun post(path: String, body: JsonObject): Boolean {
        val url = config.appsApiBaseUrl.trimEnd('/') + path
        val result: ApiResult<Unit> = handleApiCall {
            config.httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            Unit
        }
        return when (result) {
            is ApiResult.Success -> true
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Cadastro de push recusado ou sem rede (code=${result.code}, $path).")
                false
            }
            ApiResult.Loading -> false
        }
    }

    internal companion object {
        const val TAG = "PushRegistration"
        const val REGISTER_PATH = "/push/v1/aparelhos"
        const val REMOVE_PATH = "/push/v1/aparelhos/remover"
        const val UNKNOWN_VERSION = "0"
    }
}

/** O corpo do `POST /push/v1/aparelhos` — puro (testado). Opcionais nulos não vão no JSON. */
internal fun buildRegistrationBody(
    projectSlug: String,
    appId: String,
    token: String,
    platform: String,
    provider: String,
    fcmProjectId: String?,
    account: String?,
    language: String,
    appVersion: String,
    marketing: Boolean,
): JsonObject = buildJsonObject {
    put("projeto", projectSlug)
    put("app", appId)
    put("token", token)
    put("plataforma", platform)
    put("provedor", provider)
    if (fcmProjectId != null) put("projetoFcm", fcmProjectId)
    if (account != null) put("conta", account)
    put("idioma", language)
    put("versaoApp", appVersion)
    put("publicidade", marketing)
}

/**
 * O registro central do aparelho **do processo** (2.360.0). Três linhas no app:
 *
 * ```kotlin
 * // 1) Bootstrap (depois do CentralServices):
 * PushRegistration.initialize(PushRegistrationConfig.from(centralConfig))
 *
 * // 2) Raiz do app — login/abertura com sessão registram, logout remove, token novo re-registra:
 * LaunchedEffect(Unit) { PushRegistration.bind(authRepository) }        // app com login
 * LaunchedEffect(Unit) { PushRegistration.bindWithoutAccount() }        // app sem login
 *
 * // 3) Configurações: NotificationSettingsSection()  (o interruptor "Notificações de publicidade")
 * ```
 *
 * [marketing] existe sempre (mesmo antes de [initialize]), para a tela de Configurações funcionar
 * em qualquer ordem.
 *
 * **O cadastro não basta para o toque (2.363.0).** O toque nas campanhas é do
 * [br.com.codecacto.kmplib.push.ui.PushTapHost]; no **Android** ele só chega com
 * `PushActivityHooks.onCreate(this, savedInstanceState)` no `onCreate` e
 * `PushActivityHooks.onNewIntent(this, intent)` no `onNewIntent` da MainActivity (o KMPNotifier só
 * dispara o clique por `NotifierManager.onCreateOrOnNewIntent`). O serviço de push próprio do app
 * (token para o backend do produto) é ouvinte **só de token**: `createPushNotificationService(ouvinte,
 * receiveTaps = false)` quando o toque operacional vai pelo `PushTapHost(onOtherTap = …)`.
 */
object PushRegistration {

    /** O interruptor "Notificações de publicidade" do aparelho. */
    val marketing: PushMarketingPreference by lazy { PushMarketingPreference() }

    @Volatile
    private var _client: PushRegistrationClient? = null

    /** O cliente do processo (`null` antes de [initialize]). */
    val client: PushRegistrationClient? get() = _client

    val isInitialized: Boolean get() = _client != null

    /** Inicializa (ou troca) o cliente do processo. */
    fun initialize(config: PushRegistrationConfig) {
        _client = PushRegistrationClient(config, marketing)
        AppLogger.d(PushRegistrationClient.TAG, "Inicializado projeto='${config.projectSlug}'")
        // 2.362.0: as aberturas guardadas (toque a frio antes do initialize, ou sem rede) saem agora.
        PushOpenings.flush()
    }

    /** Ver [PushRegistrationClient.register]. */
    suspend fun register(account: String?): PushRegistrationOutcome =
        _client?.register(account) ?: PushRegistrationOutcome.NOT_INITIALIZED

    /** Ver [PushRegistrationClient.unregister]. */
    suspend fun unregister(): PushRegistrationOutcome =
        _client?.unregister() ?: PushRegistrationOutcome.NOT_INITIALIZED

    /** Ver [PushRegistrationClient.bind]. Sem [initialize], só registra o aviso e retorna. */
    suspend fun bind(accountIds: Flow<String?>, registerSignedOut: Boolean = false) {
        val c = _client ?: run {
            AppLogger.w(PushRegistrationClient.TAG, "bind sem PushRegistration.initialize — aparelho não cadastrado.")
            return
        }
        c.bind(accountIds, registerSignedOut)
    }

    /**
     * [bind] pela sessão do [auth] — o mesmo sujeito do `MonetizationManager.bindIdentity(auth)`
     * ([accountOf] = `User.id` por default; mude junto se o app mudou lá).
     */
    suspend fun bind(auth: IAuthRepository, accountOf: (User) -> String? = { it.id }) =
        bind(auth.currentUser.map { user -> user?.let(accountOf) })

    /** App SEM login: cadastra o aparelho sem conta (público "todos" das campanhas). */
    suspend fun bindWithoutAccount() = bind(flowOf(null), registerSignedOut = true)

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _client = null
    }
}
