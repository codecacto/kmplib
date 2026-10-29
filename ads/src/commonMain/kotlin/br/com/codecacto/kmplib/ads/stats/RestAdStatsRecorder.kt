package br.com.codecacto.kmplib.ads.stats

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.platform.install.InstallId
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
 *     "adId": "...", "impressions": 0|1, "clicks": 0|1, "debug": true|false }
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
 * **`debug` (2.293.0):** build de debug manda `debug=true` e o apps-api DESCARTA o evento — a metrica
 * do painel e so de quem usa o app publicado (fundador, 12/out/2026). O servidor tambem ignora app
 * cujo projeto nao esta em Producao no Nexus; o `debug` cobre o teste de um app que ja esta na loja.
 *
 * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 * @param isDebugBuild lido a cada evento (no Android o contexto so existe depois do init da lib).
 * @param installIdProvider (2.335.0) o identificador anonimo da instalacao ([InstallId.forTelemetry]) —
 *   o servidor conta PESSOAS unicas por dia/app/anuncio. `null` (teste, preferencias indisponiveis)
 *   omite o campo, como fazia o cliente antigo.
 */
class RestAdStatsRecorder(
    private val httpClient: HttpClient,
    private val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    private val isDebugBuild: () -> Boolean = { BuildInfo.isDebug },
    private val installIdProvider: suspend () -> String? = { InstallId.forTelemetry() },
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
        // installId ausente sai do corpo (nao vira `"installId": null`).
        explicitNulls = false
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
            debug = isDebugBuild(),
            installId = runCatching { installIdProvider() }.getOrNull(),
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
    val debug: Boolean = false,
    val installId: String? = null,
)
