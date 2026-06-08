package br.com.codecacto.kmplib.ui.mvi

/**
 * Estado de UI para listas paginadas com carregamento incremental.
 *
 * @param T Tipo dos itens da lista
 * @property items Itens já carregados
 * @property currentPage Página atual carregada (base 1)
 * @property isLoading Se há um carregamento em andamento
 * @property hasMore Se existem mais páginas a carregar
 * @property error Mensagem de erro, ou null se não houver
 */
data class PaginatedState<T>(
    val items: List<T> = emptyList(),
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)
