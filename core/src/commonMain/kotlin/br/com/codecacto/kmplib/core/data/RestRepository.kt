package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.PaginatedResponse
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/**
 * Implementação REST **online-first** de [Repository], seguindo o mesmo estilo do
 * `AdminApiEntitlementRepository`: Ktor `HttpClient` puro, serialização própria a partir do texto
 * cru (não exige ContentNegotiation no cliente do consumidor), `Authorization: Bearer <token>`,
 * cache curto em memória e normalização de erros via `handleApiCall`.
 *
 * É **genérica via serializers passados no construtor** (não usa `reified`, pois `T` é um type
 * parameter da classe). Use a factory [RestRepositoryFactory] para criar instâncias por entidade
 * sem repetir boilerplate.
 *
 * Endpoints (com [pathPrefix] = `"/meu-advogado/v1/requests"`):
 * - `GET    {base}{pathPrefix}?page=&pageSize=&<filtros>`  -> `PaginatedResponse<T>`
 * - `GET    {base}{pathPrefix}/{id}`                       -> `T`
 * - `POST   {base}{pathPrefix}`           (body = `T`)     -> `T`
 * - `PUT    {base}{pathPrefix}/{id}`      (body = `T`)     -> `T`
 * - `DELETE {base}{pathPrefix}/{id}`                       -> `Unit`
 *
 * `expectSuccess = true` faz o Ktor lançar `ResponseException` em 4xx/5xx, que o `handleApiCall`
 * mapeia preservando o status (`ApiResult.Error(code = 402/404/500, ...)`). Quando o code é **401**,
 * [RestConfig.onUnauthorized] é invocado (best-effort) antes de o erro ser devolvido.
 *
 * **Cache curto em memória** (TTL de [RestConfig.cacheTtlMillis]) aplicado a `getById`/`list`.
 * NUNCA é offline: se não houver cache válido e a rede falhar, o erro é propagado — o app trata
 * como "sem informação", nunca como dado válido. Qualquer mutação (`create`/`update`/`delete`) e
 * o [refresh] limpam todo o cache desta entidade. Erros nunca são cacheados. Leitura que começou
 * ANTES de uma mutação e terminou depois dela não entra no cache nem serve de carona (2.252.1).
 *
 * **Leituras idênticas simultâneas viram uma requisição só** (2.252.0): `list` com os mesmos
 * filtros/página e `getById` com o mesmo id, pedidos enquanto o primeiro ainda está em voo, esperam
 * por ele e recebem o mesmo resultado. Mutação nunca é coalescida.
 *
 * @param config Configuração compartilhada do backend (cliente, baseUrl, token, 401, cache TTL).
 * @param pathPrefix Caminho do recurso APÓS a baseUrl, COM barra inicial e SEM barra final
 *  (ex.: `"/meu-advogado/v1/requests"`).
 * @param entitySerializer Serializer da entidade [T].
 * @param idToPath Converte o [ID] em segmento de caminho (default: `toString()`).
 */
class RestRepository<T, ID>(
    private val config: RestConfig,
    pathPrefix: String,
    private val entitySerializer: KSerializer<T>,
    private val idToPath: (ID) -> String = { it.toString() },
) : Repository<T, ID> {

    private val resourcePath: String = config.baseUrl + pathPrefix.ensureLeadingSlash().trimEnd('/')

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val pageSerializer = PaginatedResponse.serializer(entitySerializer)
    private val listSerializer = ListSerializer(entitySerializer)

    private val timeSource = TimeSource.Monotonic

    private data class CacheEntry<V>(val value: V, val mark: TimeSource.Monotonic.ValueTimeMark)

    /**
     * Estado do cache como valor IMUTÁVEL trocado por compare-and-set (2.252.1). A [generation] avança
     * a cada mutação/[refresh]; uma leitura só grava no cache se a geração ainda for a de quando ela
     * começou, e só pega carona numa leitura em voo da MESMA geração.
     *
     * Por quê: uma `list()` que sai ANTES de um `delete` (recarga do `ON_RESUME`, por exemplo) e volta
     * DEPOIS dele trazia a lista antiga — gravava-a no cache recém-limpo (30 s mostrando o registro
     * excluído) e, com a coalescência da 2.252.0, a releitura pós-exclusão entrava de carona nela.
     * Achado no LocAki (iOS): DELETE 204, GET 200 no mesmo segundo, cliente ainda na lista.
     */
    private class CacheState<T>(
        val generation: Long,
        val byId: Map<String, CacheEntry<T>>,
        val lists: Map<String, CacheEntry<PaginatedResponse<T>>>,
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val cache = AtomicReference(CacheState<T>(0L, emptyMap(), emptyMap()))

    @OptIn(ExperimentalAtomicApi::class)
    private inline fun updateCache(transform: (CacheState<T>) -> CacheState<T>?) {
        while (true) {
            val atual = cache.load()
            val novo = transform(atual) ?: return
            if (cache.compareAndSet(atual, novo)) return
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun cacheSnapshot(): CacheState<T> = cache.load()

    /** Grava só se nenhuma mutação aconteceu desde [startedAt] — senão o dado já nasceu velho. */
    private fun storeList(startedAt: Long, key: String, value: PaginatedResponse<T>) = updateCache { atual ->
        if (atual.generation != startedAt) null
        else CacheState(atual.generation, atual.byId, atual.lists + (key to CacheEntry(value, timeSource.markNow())))
    }

    private fun storeById(startedAt: Long, key: String, value: T) = updateCache { atual ->
        if (atual.generation != startedAt) null
        else CacheState(atual.generation, atual.byId + (key to CacheEntry(value, timeSource.markNow())), atual.lists)
    }

    /**
     * Leitura idêntica em voo vira UMA requisição (2.252.0) — o app que abre e pede a mesma página
     * em 2–3 telas/ViewModels ao mesmo tempo não multiplica o GET (nem a chance de uma piscada de
     * rede derrubar um deles). A chave leva o TOKEN: duas sessões diferentes nunca compartilham
     * resposta. E leva a GERAÇÃO do cache (2.252.1): leitura pedida depois de uma mutação nunca
     * reaproveita a que saiu antes dela. Não é cache — a chave sai do mapa quando a leitura termina.
     */
    private data class InFlightKey(val token: String?, val generation: Long, val request: String)

    private val listInFlight = InFlightRequests<InFlightKey, ApiResult<PaginatedResponse<T>>>()
    private val byIdInFlight = InFlightRequests<InFlightKey, ApiResult<T>>()

    private fun <V> CacheEntry<V>?.takeFresh(): V? {
        val entry = this ?: return null
        if (config.cacheTtlMillis <= 0L) return null
        return if (entry.mark.elapsedNow().inWholeMilliseconds <= config.cacheTtlMillis) entry.value else null
    }

    // -----------------------------------------------------------------------------------------
    // Leitura
    // -----------------------------------------------------------------------------------------

    override suspend fun list(
        filters: Map<String, String>,
        page: Int,
        pageSize: Int,
    ): ApiResult<PaginatedResponse<T>> {
        val cacheKey = listCacheKey(filters, page, pageSize)
        val snapshot = cacheSnapshot()
        snapshot.lists[cacheKey].takeFresh()?.let { return ApiResult.Success(it) }
        val generation = snapshot.generation

        val token = currentToken()
        return listInFlight.run(InFlightKey(token, generation, cacheKey)) {
            fetch {
                config.httpClient.get(resourcePath) {
                    expectSuccess = true
                    applyAuth(token)
                    parameter("page", page)
                    parameter("pageSize", pageSize)
                    filters.forEach { (k, v) -> parameter(k, v) }
                }.bodyAsText()
            }.map { json.decodeFromString(pageSerializer, it) }
                .also { if (it is ApiResult.Success) storeList(generation, cacheKey, it.data) }
        }
    }

    override suspend fun getById(id: ID): ApiResult<T> {
        val key = idToPath(id)
        val snapshot = cacheSnapshot()
        snapshot.byId[key].takeFresh()?.let { return ApiResult.Success(it) }
        val generation = snapshot.generation

        val token = currentToken()
        return byIdInFlight.run(InFlightKey(token, generation, key)) {
            fetch {
                config.httpClient.get("$resourcePath/$key") {
                    expectSuccess = true
                    applyAuth(token)
                }.bodyAsText()
            }.map { json.decodeFromString(entitySerializer, it) }
                .also { if (it is ApiResult.Success) storeById(generation, key, it.data) }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Mutação (invalida o cache desta entidade)
    // -----------------------------------------------------------------------------------------

    override suspend fun create(body: T): ApiResult<T> =
        fetch {
            config.httpClient.post(resourcePath) {
                expectSuccess = true
                applyAuth()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(entitySerializer, body))
            }.bodyAsText()
        }.map { json.decodeFromString(entitySerializer, it) }
            .also { if (it is ApiResult.Success) clearCache() }

    override suspend fun update(id: ID, body: T): ApiResult<T> =
        fetch {
            config.httpClient.put("$resourcePath/${idToPath(id)}") {
                expectSuccess = true
                applyAuth()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(entitySerializer, body))
            }.bodyAsText()
        }.map { json.decodeFromString(entitySerializer, it) }
            .also { if (it is ApiResult.Success) clearCache() }

    override suspend fun delete(id: ID): ApiResult<Unit> =
        fetch {
            config.httpClient.delete("$resourcePath/${idToPath(id)}") {
                expectSuccess = true
                applyAuth()
            }
            Unit
        }.also { if (it is ApiResult.Success) clearCache() }

    override fun refresh() = clearCache()

    // -----------------------------------------------------------------------------------------
    // Internos
    // -----------------------------------------------------------------------------------------

    /**
     * Wrapper sobre `handleApiCall` que, em caso de erro 401, dispara [RestConfig.onUnauthorized]
     * (best-effort) sem mascarar o erro — o `ApiResult.Error(code = 401, ...)` é devolvido normalmente.
     */
    private suspend fun <R> fetch(block: suspend () -> R): ApiResult<R> {
        val result = handleApiCall(block)
        if (result is ApiResult.Error && result.code == UNAUTHORIZED) {
            config.onUnauthorized?.invoke()
        }
        return result
    }

    private suspend fun HttpRequestBuilder.applyAuth() = applyAuth(currentToken())

    private fun HttpRequestBuilder.applyAuth(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun currentToken(): String? = config.tokenProvider?.invoke()

    /** Esvazia o cache e avança a geração: toda leitura que começou antes deixa de valer. */
    private fun clearCache() = updateCache { atual ->
        CacheState(atual.generation + 1, emptyMap(), emptyMap())
    }

    private fun listCacheKey(filters: Map<String, String>, page: Int, pageSize: Int): String =
        buildString {
            append("p=").append(page).append("&ps=").append(pageSize)
            filters.entries.sortedBy { it.key }.forEach { (k, v) ->
                append('&').append(k).append('=').append(v)
            }
        }

    /** Disponível para usos futuros (endpoints que devolvem lista não paginada). */
    @Suppress("unused")
    internal fun unpaginatedListSerializer(): KSerializer<List<T>> = listSerializer

    private companion object {
        const val UNAUTHORIZED = 401
        fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"
    }
}
