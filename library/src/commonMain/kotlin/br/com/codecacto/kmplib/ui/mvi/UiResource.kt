package br.com.codecacto.kmplib.ui.mvi

import br.com.codecacto.kmplib.core.network.ApiResult

/**
 * Estado de um recurso assíncrono consumido pela UI.
 *
 * Diferente de [ApiResult], modela explicitamente o estado [Idle] (ainda não
 * iniciado) além de [Loading], [Success] e [Error].
 *
 * @param T Tipo do dado em caso de sucesso
 */
sealed class UiResource<out T> {
    data object Idle : UiResource<Nothing>()
    data object Loading : UiResource<Nothing>()
    data class Success<T>(val data: T) : UiResource<T>()
    data class Error(val message: String) : UiResource<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message
}

/**
 * Converte um [ApiResult] em [UiResource].
 *
 * [ApiResult.Loading] é mapeado para [UiResource.Loading].
 */
fun <T> ApiResult<T>.toUiResource(): UiResource<T> = when (this) {
    is ApiResult.Success -> UiResource.Success(data)
    is ApiResult.Error -> UiResource.Error(message)
    is ApiResult.Loading -> UiResource.Loading
}

/**
 * Executa [block] e converte o [ApiResult] resultante em [UiResource].
 *
 * Extensão genérica sobre [BaseViewModel] — não acopla ao State concreto.
 *
 * Uso:
 * ```kotlin
 * val resource = asyncLoad { repository.fetch() }
 * setState { copy(resource = resource) }
 * ```
 */
suspend fun <S, A, E, T> BaseViewModel<S, A, E>.asyncLoad(
    block: suspend () -> ApiResult<T>
): UiResource<T> = block().toUiResource()
