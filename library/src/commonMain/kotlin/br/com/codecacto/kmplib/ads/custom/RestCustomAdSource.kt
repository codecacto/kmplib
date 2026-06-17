package br.com.codecacto.kmplib.ads.custom

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
 * Implementacao de [CustomAdSource] que le os house ads do backend central **apps-api**
 * via REST — substitui o [CustomAdRepository] (Firestore) como fonte padrao.
 *
 * A partir da kmplib 2.37.0 os anuncios proprios NAO sao mais lidos do Firestore `custom_ads`.
 * Eles vem de um GET PUBLICO:
 *
 * ```
 * GET {appsApiBaseUrl}/public/ads/app/{appId}?placement={placementId}
 *   -> { "ads": [ { id, imageUrl, targetUrl, title, ctaLabel, placementId, priority, weight } ] }
 * ```
 *
 * O servidor ja entrega APENAS anuncios ativos, dentro da janela de tempo e que casam com o
 * `appId` (segmentacao N:N feita server-side). A lib nao re-filtra por `active`/janela/app — so
 * confia no que o endpoint devolve. O filtro por `placement` vai como query param quando informado.
 *
 * Mesmo padrao do [br.com.codecacto.kmplib.developer.DeveloperInfoService] e do
 * [br.com.codecacto.kmplib.feedback.FeedbackService]: Ktor core puro + `handleApiCall`/`ApiResult`
 * de `core/network` + desserializacao kotlinx (sem ContentNegotiation obrigatorio).
 *
 * **Best-effort (regra de ouro):** qualquer erro (rede/4xx/5xx/corpo invalido) cai em lista vazia,
 * NUNCA lanca nem derruba o app. Como nao ha snapshot/real-time no REST, [observeAds] emite uma
 * unica vez (busca one-shot) e completa — os composables continuam consumindo `CustomAdManager.ads`
 * normalmente; o refresh manual fica por conta de [CustomAdManager.refresh].
 *
 * @param httpClient `HttpClient` (Ktor) do app — o mesmo ja usado por feedback/developer.
 * @param appId slug do app no catalogo central (vai no path `/public/ads/app/{appId}`).
 *   OBRIGATORIO: e o que casa com a lista N:N no servidor.
 * @param appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 */
class RestCustomAdSource(
    private val httpClient: HttpClient,
    private val appId: String,
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

    /**
     * Emite uma unica vez o resultado da busca REST e completa.
     *
     * O parametro [appId] do contrato e ignorado (a fonte REST ja foi configurada com o appId no
     * construtor — o servidor faz a segmentacao). Mantido na assinatura por compatibilidade.
     */
    override fun observeAds(placementId: String?, appId: String?): Flow<List<CustomAd>> = flow {
        emit(fetchAds(placementId, appId).getOrDefault(emptyList()))
    }

    /**
     * Busca one-shot dos house ads do app. Best-effort: erro -> lista vazia.
     *
     * [appId] do parametro e ignorado (usa o do construtor). [placementId], se nao-nulo, vai como
     * query `?placement=`.
     */
    override suspend fun fetchAds(placementId: String?, appId: String?): Result<List<CustomAd>> {
        val url = appsApiBaseUrl.trimEnd('/') + "/public/ads/app/" + this.appId

        val result: ApiResult<List<CustomAd>> = handleApiCall {
            val raw = httpClient.get(url) {
                expectSuccess = true
                if (!placementId.isNullOrBlank()) parameter("placement", placementId)
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
 * Fonte vazia — usada como fallback quando [CustomAdManager] e inicializado sem `httpClient`/`appId`
 * e sem `source` explicito. Nunca traz anuncios; garante que ads jamais derrubem o app.
 */
internal object EmptyCustomAdSource : CustomAdSource {
    override fun observeAds(placementId: String?, appId: String?): Flow<List<CustomAd>> =
        flow { emit(emptyList()) }

    override suspend fun fetchAds(placementId: String?, appId: String?): Result<List<CustomAd>> =
        Result.success(emptyList())
}

/** Resposta do `GET /public/ads/app/{appId}`. */
@Serializable
internal data class CustomAdsResponse(
    val ads: List<CustomAdDto> = emptyList(),
)

/**
 * DTO de fio de um house ad vindo do apps-api. O servidor ja resolveu `active`/janela/app — por
 * isso o DTO so traz o que o composable precisa renderizar. `imageUrl` e a imagem do APP anunciado.
 */
@Serializable
internal data class CustomAdDto(
    val id: String = "",
    val imageUrl: String = "",
    val targetUrl: String = "",
    val title: String = "",
    val ctaLabel: String = "",
    val placementId: String = "default",
    val priority: Int = 0,
    val weight: Int = 100,
    /**
     * Formato opcional ("banner"/"interstitial"). O contrato atual do apps-api NAO envia este
     * campo (o slot/placement decide onde o anuncio aparece); se um dia o servidor passar a
     * enviar, e respeitado. Quando ausente/vazio, o [CustomAd] resultante fica com `format`
     * em branco e casa com qualquer formato pedido no [selectAd].
     */
    val format: String = "",
) {
    fun toCustomAd(): CustomAd = CustomAd(
        id = id,
        imageUrl = imageUrl,
        targetUrl = targetUrl,
        title = title,
        ctaLabel = ctaLabel,
        placementId = placementId,
        priority = priority,
        weight = weight,
        // O endpoint ja filtrou active+janela; valores neutros para os campos nao enviados.
        // `format` vazio (quando o servidor nao envia) = casa com banner e interstitial.
        active = true,
        format = format,
    )
}
