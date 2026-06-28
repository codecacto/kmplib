package br.com.codecacto.kmplib.developer

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Serviço de leitura do catálogo "Desenvolvido por CodeCacto" via backend central **apps-api**.
 *
 * A partir da kmplib 2.36.0 o catálogo (contato + apps) NÃO é mais lido do Firestore `code-cacto`
 * (a API key hardcoded saiu). Ele é buscado num **único GET PÚBLICO**
 * (`GET {appsApiBaseUrl}/public/developer`) — mesmo princípio do
 * [br.com.codecacto.kmplib.feedback.FeedbackService] e do
 * [br.com.codecacto.kmplib.appupdate.AppUpdateService] (Ktor core puro + desserialização kotlinx,
 * sem ContentNegotiation obrigatório).
 *
 * **Robustez (regra de ouro):** é best-effort e tolerante a falha. Erro de rede / 4xx / 5xx / corpo
 * inválido / serviço não inicializado → fallback (contato padrão + lista vazia), NUNCA lança e nunca
 * quebra a tela. A resposta com sucesso é cacheada em memória, então a [DeveloperScreen] dispara um
 * único request mesmo chamando `fetchContact()` e `fetchApps()` em sequência.
 *
 * ## Inicialização
 *
 * ```kotlin
 * // No Application.onCreate() / ponto de entrada do app (junto do FeedbackService)
 * DeveloperInfoService.initialize(
 *     DeveloperConfig(
 *         httpClient = appHttpClient,            // Ktor core puro
 *         // appsApiBaseUrl = "https://apps-api.codecacto.com.br"  // default; configurável
 *     )
 * )
 * ```
 *
 * Se o serviço não for inicializado, `fetchContact()`/`fetchApps()` apenas devolvem o fallback.
 */
object DeveloperInfoService {

    private const val TAG = "DeveloperInfoService"
    private const val PATH = "/public/developer"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var _config: DeveloperConfig? = null
    val config: DeveloperConfig? get() = _config

    // Cache em memória da resposta bem-sucedida (1 request por sessão da tela).
    private var cached: DeveloperInfoResponse? = null

    /** Inicializa o serviço com o [HttpClient] do app e a base URL do apps-api. */
    fun initialize(config: DeveloperConfig) {
        _config = config
        cached = null
        AppLogger.d(TAG, "Inicializado base='${config.appsApiBaseUrl}'")
    }

    /** Busca as informações de contato. Sempre retorna sucesso (fallback no erro). */
    suspend fun fetchContact(): Result<DeveloperContact> {
        val info = fetchInfo() ?: return Result.success(DeveloperContact())
        val fallback = DeveloperContact()
        return Result.success(
            DeveloperContact(
                whatsapp = info.contact.whatsapp?.takeIf { it.isNotBlank() } ?: fallback.whatsapp,
                email = info.contact.email?.takeIf { it.isNotBlank() } ?: fallback.email,
                site = info.contact.site.orEmpty(),
            )
        )
    }

    /** Busca os apps, ordenados por `order`. Sempre retorna sucesso (lista vazia no erro). */
    suspend fun fetchApps(): Result<List<DeveloperApp>> {
        val info = fetchInfo() ?: return Result.success(emptyList())
        val apps = info.apps
            .map { dto ->
                DeveloperApp(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description,
                    logoUrl = dto.logoUrl,
                    storeUrl = dto.storeUrl,
                    playStoreUrl = dto.playStoreUrl,
                    appStoreUrl = dto.appStoreUrl,
                    active = true,
                    order = dto.order,
                )
            }
            .sortedBy { it.order }
        return Result.success(apps)
    }

    /**
     * Faz (ou reusa o cache de) o único GET ao apps-api. Best-effort: devolve `null` em qualquer
     * falha (rede/4xx/5xx/corpo inválido/serviço não inicializado), nunca lança.
     */
    private suspend fun fetchInfo(): DeveloperInfoResponse? {
        cached?.let { return it }

        val cfg = _config
        if (cfg == null) {
            AppLogger.w(TAG, "DeveloperInfoService não inicializado — usando fallback.")
            return null
        }

        val url = cfg.appsApiBaseUrl.trimEnd('/') + PATH

        val result: ApiResult<DeveloperInfoResponse> = handleApiCall {
            val raw = cfg.httpClient.get(url) {
                expectSuccess = true
            }.bodyAsText()
            json.decodeFromString(DeveloperInfoResponse.serializer(), raw)
        }

        return when (result) {
            is ApiResult.Success -> result.data.also { cached = it }
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Falha ao buscar developer (code=${result.code}): ${result.message}")
                null
            }
            ApiResult.Loading -> null
        }
    }
}
