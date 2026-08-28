package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.PaginatedResponse

/**
 * Contrato genérico de um repositório de dados REST **online-first** (sem real-time, sem offline).
 *
 * Modela o CRUD de uma entidade [T] identificada por [ID] contra um backend REST central
 * (`apps-api`), no modelo decidido pelo fundador: o app **sempre busca do servidor** e faz refetch
 * quando precisa de dados frescos — não há observação de snapshots nem cache persistente offline.
 *
 * É a camada de dados oficial dos apps: substitui de vez o Firestore-como-banco (removido da lib).
 * Firebase fica restrito a Auth (crashes via observability/Sentry).
 *
 * Todas as operações devolvem [ApiResult] (Success/Error/Loading) — erros de rede/HTTP já vêm
 * normalizados (status code + mensagem) por `handleApiCall`, então o consumidor nunca trata
 * exceções de rede diretamente.
 *
 * Injete o **contrato** (esta interface) nos ViewModels para poder testar com fakes; bindei a impl
 * [RestRepository] no Koin.
 *
 * @param T Tipo da entidade (serializável via kotlinx.serialization).
 * @param ID Tipo do identificador da entidade (tipicamente `String`).
 */
interface Repository<T, ID> {

    /**
     * Lista entidades de forma paginada.
     *
     * @param filters Filtros opcionais enviados como query params (ex.: `"status" to "open"`).
     * @param page Página desejada (base 1).
     * @param pageSize Itens por página.
     * @return [PaginatedResponse] com os itens da página e metadados de paginação.
     */
    suspend fun list(
        filters: Map<String, String> = emptyMap(),
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<PaginatedResponse<T>>

    /** Busca uma entidade pelo seu [id]. */
    suspend fun getById(id: ID): ApiResult<T>

    /** Cria uma nova entidade a partir de [body]. Devolve a entidade criada (com id do servidor). */
    suspend fun create(body: T): ApiResult<T>

    /** Atualiza a entidade [id] com [body]. Devolve a entidade atualizada. */
    suspend fun update(id: ID, body: T): ApiResult<T>

    /** Remove a entidade [id]. */
    suspend fun delete(id: ID): ApiResult<Unit>

    /**
     * Invalida o cache curto em memória, forçando que as próximas leituras refaçam a chamada de
     * rede. Use após mutações externas ou quando a tela precisa de dados garantidamente frescos
     * (pull-to-refresh). Não dispara rede sozinho — apenas limpa o cache.
     */
    fun refresh()

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 20
    }
}
