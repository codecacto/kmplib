package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/**
 * Implementacao de [EntitlementRepository] que consulta o admin-api central via **Ktor core puro**.
 *
 * Faz `get(...).bodyAsText()` e decodifica com kotlinx-json proprio — **nao exige
 * ContentNegotiation** no [httpClient] do consumidor, evitando acoplamento de plugins.
 *
 * Suporta **dois modos** de roteamento, controlados por [pathPrefix]:
 *
 * 1. **Modo legado (service-token)** — [pathPrefix] = null (default). Monta o prefixo fixo
 *    `{baseUrl}/v1/{slug}`. Usado por chamadas server-to-server autenticadas por service-token.
 *    Rotas:
 *    - `GET /v1/{slug}/entitlement`        -> [Entitlement]
 *    - `GET /v1/{slug}/usage/{feature}`    -> [UsageSnapshot]
 *    - `GET /v1/{slug}/plans`              -> `List<Plan>`
 *
 * 2. **Modo user-auth (`/me`)** — [pathPrefix] informado, ex.: `"/v1/projects/{slug}/me"`. Monta o
 *    prefixo `{baseUrl}{pathPrefix}`. Usado quando o **proprio app** le o entitlement do usuario
 *    logado; nesse caso o [tokenProvider] (ou [authToken]) deve devolver o **Firebase ID token** do
 *    usuario, que o admin-api valida para resolver o dono. Rotas:
 *    - `GET /v1/projects/{slug}/me/entitlement`        -> [Entitlement]
 *    - `GET /v1/projects/{slug}/me/usage/{feature}`    -> [UsageSnapshot]
 *    - `GET /v1/projects/{slug}/me/plans`              -> `List<Plan>`
 *
 *    Use o factory [forUserAuth] para montar esse modo sem repetir a string do prefixo.
 *
 * Em ambos os modos, quando ha token resolvido (via [tokenProvider] ou [authToken]), envia
 * `Authorization: Bearer <token>`. As 3 leituras, a desserializacao, o cache de 60s e a regra
 * "nunca conceder offline" sao identicas nos dois modos — so muda o prefixo de caminho.
 *
 * **Cache curto em memoria (~60s)** apenas para leitura degradada (evitar refazer a chamada em
 * navegacoes rapidas). NUNCA concede cota offline: se nao houver cache valido e a rede falhar,
 * propaga o erro — o app trata como "sem informacao", nunca como "liberado".
 *
 * @param httpClient Cliente Ktor (pode ser o mesmo do app; nao precisa de ContentNegotiation).
 * @param baseUrl Base do admin-api SEM barra final (ex.: "https://admin.codecacto.com.br").
 * @param projectSlug Slug do projeto no admin (ex.: "super8"). So usado no modo legado; no modo
 *  user-auth o slug ja vem embutido em [pathPrefix].
 * @param authToken Token Bearer (opcional). No modo user-auth, e o Firebase ID token do usuario.
 *  Pode ser dinamico via [tokenProvider].
 * @param tokenProvider Provedor dinamico de token; tem precedencia sobre [authToken] quando nao-nulo.
 *  No modo user-auth deve devolver o **Firebase ID token** atual (ex.: `user.getIdToken()`).
 * @param cacheTtlMillis TTL do cache em memoria (default 60_000 ms). Use 0 para desabilitar.
 * @param pathPrefix Prefixo de caminho APOS [baseUrl] (com barra inicial, ex.: `"/v1/projects/super8/me"`).
 *  Quando null, usa o prefixo legado `"/v1/{slug}"` (service-token).
 */
class AdminApiEntitlementRepository(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val projectSlug: String,
    private val authToken: String? = null,
    private val tokenProvider: (() -> String?)? = null,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    pathPrefix: String? = null,
) : EntitlementRepository {

    private val base: String = baseUrl.trimEnd('/')
    private val prefix: String =
        if (pathPrefix != null) "$base${pathPrefix.ensureLeadingSlash().trimEnd('/')}"
        else "$base/v1/$projectSlug"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val timeSource = TimeSource.Monotonic

    private data class CacheEntry<T>(val value: T, val mark: TimeSource.Monotonic.ValueTimeMark)

    private var entitlementCache: CacheEntry<Entitlement>? = null
    private var plansCache: CacheEntry<List<Plan>>? = null
    private val usageCache: MutableMap<String, CacheEntry<UsageSnapshot>> = mutableMapOf()

    private fun <T> CacheEntry<T>?.takeFresh(): T? {
        val entry = this ?: return null
        if (cacheTtlMillis <= 0L) return null
        return if (entry.mark.elapsedNow().inWholeMilliseconds <= cacheTtlMillis) entry.value else null
    }

    private fun resolveToken(): String? = tokenProvider?.invoke() ?: authToken

    private suspend inline fun <reified T> fetch(url: String, crossinline deserialize: (String) -> T): ApiResult<T> =
        handleApiCall {
            // expectSuccess=true faz o Ktor lancar ResponseException em 4xx/5xx, que o handleApiCall
            // mapeia preservando o status code (402/429/500). Decodificamos do texto cru (sem exigir
            // ContentNegotiation no cliente do consumidor).
            val body = httpClient.get(url) {
                expectSuccess = true
                resolveToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }.bodyAsText()
            deserialize(body)
        }

    override suspend fun getEntitlement(): ApiResult<Entitlement> {
        entitlementCache.takeFresh()?.let { return ApiResult.Success(it) }
        return fetch("$prefix/entitlement") { json.decodeFromString<Entitlement>(it) }
            .also { if (it is ApiResult.Success) entitlementCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> {
        usageCache[feature].takeFresh()?.let { return ApiResult.Success(it) }
        return fetch("$prefix/usage/$feature") { json.decodeFromString<UsageSnapshot>(it) }
            .also { if (it is ApiResult.Success) usageCache[feature] = CacheEntry(it.data, timeSource.markNow()) }
    }

    override suspend fun getPlans(): ApiResult<List<Plan>> {
        plansCache.takeFresh()?.let { return ApiResult.Success(it) }
        return fetch("$prefix/plans") { json.decodeFromString<List<Plan>>(it) }
            .also { if (it is ApiResult.Success) plansCache = CacheEntry(it.data, timeSource.markNow()) }
    }

    /** Limpa o cache em memoria (ex.: apos compra/restore para forcar releitura). */
    fun invalidateCache() {
        entitlementCache = null
        plansCache = null
        usageCache.clear()
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MILLIS: Long = 60_000L

        /**
         * Monta o repositorio no **modo user-auth (`/me`)**: o proprio app le o entitlement do
         * usuario logado usando seu **Firebase ID token**.
         *
         * Equivale a construir com `pathPrefix = "/v1/projects/$projectSlug/me"`. O [tokenProvider]
         * deve devolver o Firebase ID token atual (ex.: `auth.currentUser?.getIdToken(false)`); o
         * admin-api valida o token e resolve o dono. Rotas resultantes:
         * - `GET {baseUrl}/v1/projects/{slug}/me/entitlement`
         * - `GET {baseUrl}/v1/projects/{slug}/me/usage/{feature}`
         * - `GET {baseUrl}/v1/projects/{slug}/me/plans`
         *
         * @param tokenProvider Provedor do Firebase ID token (suspend-free; chame `getIdToken` antes
         *  e cacheie no app, ou exponha um provider sincrono que leia o ultimo token valido).
         */
        fun forUserAuth(
            httpClient: HttpClient,
            baseUrl: String,
            projectSlug: String,
            tokenProvider: () -> String?,
            cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
        ): AdminApiEntitlementRepository =
            AdminApiEntitlementRepository(
                httpClient = httpClient,
                baseUrl = baseUrl,
                projectSlug = projectSlug,
                tokenProvider = tokenProvider,
                cacheTtlMillis = cacheTtlMillis,
                pathPrefix = "/v1/projects/$projectSlug/me",
            )

        private fun String.ensureLeadingSlash(): String =
            if (startsWith('/')) this else "/$this"
    }
}
