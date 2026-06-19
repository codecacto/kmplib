package br.com.codecacto.kmplib.ads.stats

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Recorder que envia metricas de publicidade ao backend central **apps-api** via REST.
 *
 * Cada evento vira um POST que incrementa o contador no servidor (atomico/best-effort no banco
 * central):
 *
 * ```
 * POST {appsApiBaseUrl}/public/ad-stats
 *   { "provider": "custom", "appId": "...", "format": "banner"|"interstitial",
 *     "adId": "...", "impressions": 0|1, "clicks": 0|1 }
 * ```
 *
 * Um unico evento por chamada: uma IMPRESSION envia `impressions=1, clicks=0`; um CLICK envia
 * `impressions=0, clicks=1`. O servidor agrega por dia (UTC) no central — a lib nao monta mais a
 * chave de bucket por dia (isso virou responsabilidade do backend).
 *
 * Mesmo padrao de `FeedbackService`: Ktor core puro + `handleApiCall`/`ApiResult` de
 * `core/network` + serializacao kotlinx (sem ContentNegotiation obrigatorio).
 *
 * **Best-effort / fire-and-forget (regra de ouro):** chamado dentro do scope interno do [AdStats];
 * qualquer erro (rede/4xx/5xx) e logado e ignorado — estatistica de ad NUNCA pode quebrar o app.
 *
 * @param httpClient `HttpClient` (Ktor) do app — o mesmo de feedback/developer/custom ads.
 * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 */
class RestAdStatsRecorder(
    private val httpClient: HttpClient,
    private val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
) : AdStatsRecorder {
    companion object {
        private const val TAG = "RestAdStatsRecorder"
        private const val PATH = "/public/ad-stats"
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun record(
        provider: AdProviderTag,
        appId: String,
        format: AdFormat,
        adId: String?,
        type: AdEventType,
    ) {
        val body = AdStatsRequest(
            provider = provider.name.lowercase(),
            appId = appId,
            format = format.name.lowercase(),
            adId = adId?.ifBlank { null } ?: "all",
            impressions = if (type == AdEventType.IMPRESSION) 1 else 0,
            clicks = if (type == AdEventType.CLICK) 1 else 0,
        )

        val url = appsApiBaseUrl.trimEnd('/') + PATH

        val result: ApiResult<Unit> = handleApiCall {
            httpClient.post(url) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AdStatsRequest.serializer(), body))
            }.bodyAsText() // corpo ignorado (fire-and-forget)
            Unit
        }

        if (result is ApiResult.Error) {
            AppLogger.w(TAG, "Falha ao enviar ad-stats (code=${result.code}, best-effort, ignorado): ${result.message}")
        }
    }
}

/** Corpo do `POST /public/ad-stats`. */
@Serializable
internal data class AdStatsRequest(
    val provider: String,
    val appId: String,
    val format: String,
    val adId: String,
    val impressions: Int,
    val clicks: Int,
)
