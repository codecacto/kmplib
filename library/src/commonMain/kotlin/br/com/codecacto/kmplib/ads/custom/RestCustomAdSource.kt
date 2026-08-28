package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Implementacao de [CustomAdSource] que le os house ads do backend central **apps-api** via REST,
 * por **projeto + superficie**.
 *
 * ```
 * GET {appsApiBaseUrl}/public/ads?project={projectSlug}&surface={surface}
 *   -> { "ads": [ { id, imageUrl, targetUrl, title, ctaLabel, format } ] }
 * ```
 *
 * O servidor ja entrega APENAS anuncios ativos e segmentados para o projeto/superficie pedidos. A lib
 * NAO re-filtra por `active`/janela/app — so confia no que o endpoint devolve.
 *
 * Mesmo padrao do [br.com.codecacto.kmplib.developer.DeveloperInfoService] e do
 * [br.com.codecacto.kmplib.feedback.FeedbackService]: Ktor core puro + `handleApiCall`/`ApiResult`
 * de `core/network` + desserializacao kotlinx (sem ContentNegotiation obrigatorio).
 *
 * **Best-effort (regra de ouro):** qualquer erro (rede/4xx/5xx/corpo invalido) cai em lista vazia,
 * NUNCA lanca nem derruba o app. Como nao ha snapshot/real-time no REST, [observeAds] emite uma
 * unica vez (busca one-shot) e completa — o refresh manual fica por conta de [CustomAdManager.refresh].
 *
 * @param httpClient `HttpClient` (Ktor) do app — o mesmo ja usado por feedback/developer.
 * @param projectSlug slug do projeto no catalogo central (vai como `?project=`). OBRIGATORIO.
 * @param surface superficie onde os anuncios aparecem (vai como `?surface=`). Default "app".
 * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 */
class RestCustomAdSource(
    private val httpClient: HttpClient,
    private val projectSlug: String,
    private val surface: String = CustomAdConfig.DEFAULT_SURFACE,
    private val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
) : CustomAdSource {
    companion object {
        private const val TAG = "RestCustomAdSource"
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Emite uma unica vez o resultado da busca REST e completa. */
    override fun observeAds(): Flow<List<CustomAd>> = flow {
        emit(fetchAds().getOrDefault(emptyList()))
    }

    /** Busca one-shot dos house ads do projeto/superficie. Best-effort: erro -> lista vazia. */
    override suspend fun fetchAds(): Result<List<CustomAd>> {
        val url = appsApiBaseUrl.trimEnd('/') + "/public/ads"

        val result: ApiResult<List<CustomAd>> = handleApiCall {
            val raw = httpClient.get(url) {
                expectSuccess = true
                parameter("project", projectSlug)
                parameter("surface", surface)
                // Plataforma p/ o servidor resolver o destino (app -> Play/App Store)
                // quando o anúncio não tem URL própria.
                parameter("platform", currentPlatform)
            }.bodyAsText()
            json.decodeFromString(CustomAdsResponse.serializer(), raw)
                .ads
                .map { it.toCustomAd() }
        }

        return when (result) {
            is ApiResult.Success -> Result.success(result.data)
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Falha ao buscar custom ads (code=${result.code}): ${result.message}")
                Result.success(emptyList())
            }
            ApiResult.Loading -> Result.success(emptyList())
        }
    }
}

/**
 * Fonte vazia — usada como fallback quando [CustomAdManager] e inicializado sem `httpClient`/
 * `projectSlug` e sem `source` explicito. Nunca traz anuncios; garante que ads jamais derrubem o app.
 */
internal object EmptyCustomAdSource : CustomAdSource {
    override fun observeAds(): Flow<List<CustomAd>> = flow { emit(emptyList()) }
    override suspend fun fetchAds(): Result<List<CustomAd>> = Result.success(emptyList())
}

/** Resposta do `GET /public/ads`. */
@Serializable
internal data class CustomAdsResponse(
    val ads: List<CustomAdDto> = emptyList(),
)

/**
 * DTO de fio de um house ad vindo do apps-api. O servidor ja resolveu `active`/janela/projeto — por
 * isso o DTO so traz o que o composable precisa renderizar. `imageUrl` e a imagem do APP anunciado.
 */
@Serializable
internal data class CustomAdDto(
    val id: String = "",
    val imageUrl: String = "",
    val targetUrl: String = "",
    val title: String = "",
    val ctaLabel: String = "",
    /**
     * Formato opcional ("banner"/"interstitial"). O contrato atual do apps-api pode nao enviar este
     * campo (a superficie/slot decide onde o anuncio aparece); quando ausente/vazio, o [CustomAd]
     * resultante fica com `format` em branco e casa com qualquer formato pedido no [selectAd].
     */
    val format: String = "",
) {
    fun toCustomAd(): CustomAd = CustomAd(
        id = id,
        imageUrl = imageUrl,
        targetUrl = targetUrl,
        title = title,
        ctaLabel = ctaLabel,
        format = format,
    )
}
