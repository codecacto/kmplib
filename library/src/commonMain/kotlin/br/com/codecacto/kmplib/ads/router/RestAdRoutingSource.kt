package br.com.codecacto.kmplib.ads.router

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Implementacao de [AdRoutingSource] que le a config de roteamento de ads do backend central
 * **apps-api** via REST — substitui o [AdRoutingRepository] (Firestore) como fonte padrao.
 *
 * A partir da kmplib 2.37.0 a decisao de qual provider mostrar por formato NAO vem mais do
 * Firestore `app_ad_configs/{appId}`. Ela vem de um GET PUBLICO:
 *
 * ```
 * GET {appsApiBaseUrl}/public/ad-config/{appId}
 *   -> { "banner": "admob"|"custom"|"off", "interstitial": "...", "appOpen": "..." }
 * ```
 *
 * Os valores casam com os `@SerialName` de [AdProvider] (lowercase), entao o proprio
 * [AdRouting.serializer] desserializa o corpo. Campos ausentes caem no default de [AdRouting]
 * (`OFF`) — postura conservadora.
 *
 * Mesmo padrao de `DeveloperInfoService`/`FeedbackService`: Ktor core puro + `handleApiCall`/
 * `ApiResult` de `core/network` + desserializacao kotlinx (sem ContentNegotiation obrigatorio).
 *
 * **Best-effort (regra de ouro):** qualquer erro (rede/4xx/5xx/corpo invalido) cai no `default`
 * passado pelo [AdRouter], NUNCA lanca. Como nao ha snapshot/real-time no REST, [observeRouting]
 * emite uma unica vez (busca one-shot) e completa.
 *
 * @param httpClient `HttpClient` (Ktor) do app — o mesmo de feedback/developer/custom ads.
 * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 */
class RestAdRoutingSource(
    private val httpClient: HttpClient,
    private val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
) : AdRoutingSource {
    companion object {
        private const val TAG = "RestAdRoutingSource"
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Emite uma unica vez o resultado da busca REST e completa. */
    override fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting> = flow {
        emit(fetchRouting(appId, default).getOrDefault(default))
    }

    /** Busca one-shot da config de roteamento. Best-effort: erro -> [default]. */
    override suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting> {
        val url = appsApiBaseUrl.trimEnd('/') + "/public/ad-config/" + appId

        val result: ApiResult<AdRouting> = handleApiCall {
            val raw = httpClient.get(url) {
                expectSuccess = true
            }.bodyAsText()
            json.decodeFromString(AdRouting.serializer(), raw)
        }

        return when (result) {
            is ApiResult.Success -> Result.success(result.data)
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Falha ao buscar ad-config de $appId (code=${result.code}): ${result.message}, usando default")
                Result.success(default)
            }
            ApiResult.Loading -> Result.success(default)
        }
    }
}

/**
 * Fonte vazia — usada como fallback quando [AdRouter] e inicializado sem `httpClient` e sem
 * `source` explicito. Sempre devolve o `default` (best-effort); ads jamais derrubam o app.
 */
internal object EmptyAdRoutingSource : AdRoutingSource {
    override fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting> =
        flow { emit(default) }

    override suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting> =
        Result.success(default)
}
