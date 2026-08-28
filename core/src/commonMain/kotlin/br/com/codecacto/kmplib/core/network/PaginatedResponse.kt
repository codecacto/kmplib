package br.com.codecacto.kmplib.core.network

import kotlinx.serialization.Serializable

/**
 * Resposta paginada genérica para APIs REST.
 *
 * @param T Tipo dos itens da página
 * @property data Itens da página atual
 * @property page Página atual (base 1)
 * @property pageSize Quantidade de itens por página
 * @property total Total de itens disponíveis
 * @property totalPages Total de páginas disponíveis
 */
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
) {
    val hasNextPage: Boolean get() = page < totalPages
}
