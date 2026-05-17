package br.com.codecacto.kmplib.core.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int = -1, val message: String) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String) -> Unit): ApiResult<T> {
        if (this is Error) action(message)
        return this
    }
}
