package br.com.codecacto.kmplib.core.error

/**
 * Sistema de tratamento de erros genérico
 *
 * Fornece classes e utilitários para lidar com erros de forma consistente
 * em toda a aplicação.
 */

/**
 * Resultado de uma operação que pode falhar
 *
 * Wrapper type-safe para operações que retornam sucesso ou erro
 */
sealed class AppResult<out T> {
    /**
     * Operação bem-sucedida
     */
    data class Success<T>(val data: T) : AppResult<T>()

    /**
     * Operação falhou
     */
    data class Error(val error: AppError) : AppResult<Nothing>()

    /**
     * Verifica se é sucesso
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Verifica se é erro
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Obtém os dados ou null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * Obtém os dados ou um valor padrão
     */
    fun getOrElse(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    /**
     * Mapeia o resultado de sucesso
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /**
     * Executa ação em caso de sucesso
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) {
            action(data)
        }
        return this
    }

    /**
     * Executa ação em caso de erro
     */
    inline fun onError(action: (AppError) -> Unit): AppResult<T> {
        if (this is Error) {
            action(error)
        }
        return this
    }
}

/**
 * Tipos de erro da aplicação
 */
sealed class AppError {
    /**
     * Mensagem de erro amigável ao usuário
     */
    abstract val message: String

    /**
     * Exceção original (opcional)
     */
    abstract val throwable: Throwable?

    /**
     * Erro de rede (sem conexão, timeout, etc)
     */
    data class Network(
        override val message: String = "Erro de conexão. Verifique sua internet.",
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro do servidor (5xx)
     */
    data class Server(
        override val message: String = "Erro no servidor. Tente novamente mais tarde.",
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro de autenticação (401)
     */
    data class Unauthorized(
        override val message: String = "Sessão expirada. Faça login novamente.",
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro de permissão (403)
     */
    data class Forbidden(
        override val message: String = "Você não tem permissão para acessar este recurso.",
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Recurso não encontrado (404)
     */
    data class NotFound(
        override val message: String = "Recurso não encontrado.",
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro de validação (campos inválidos, etc)
     */
    data class Validation(
        override val message: String,
        val fields: Map<String, String> = emptyMap(),
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro de negócio (regras de negócio violadas)
     */
    data class Business(
        override val message: String,
        override val throwable: Throwable? = null
    ) : AppError()

    /**
     * Erro desconhecido
     */
    data class Unknown(
        override val message: String = "Erro desconhecido. Tente novamente.",
        override val throwable: Throwable? = null
    ) : AppError()
}

/**
 * Converte Exception para AppError
 */
fun Throwable.toAppError(): AppError {
    return when {
        message?.contains("network", ignoreCase = true) == true ||
        message?.contains("connection", ignoreCase = true) == true ||
        message?.contains("timeout", ignoreCase = true) == true -> {
            AppError.Network(
                message = "Erro de conexão. Verifique sua internet.",
                throwable = this
            )
        }
        message?.contains("unauthorized", ignoreCase = true) == true ||
        message?.contains("401") == true -> {
            AppError.Unauthorized(throwable = this)
        }
        message?.contains("forbidden", ignoreCase = true) == true ||
        message?.contains("403") == true -> {
            AppError.Forbidden(throwable = this)
        }
        message?.contains("not found", ignoreCase = true) == true ||
        message?.contains("404") == true -> {
            AppError.NotFound(throwable = this)
        }
        message?.contains("500", ignoreCase = true) == true ||
        message?.contains("server", ignoreCase = true) == true -> {
            AppError.Server(throwable = this)
        }
        else -> {
            AppError.Unknown(
                message = message ?: "Erro desconhecido",
                throwable = this
            )
        }
    }
}

/**
 * Converte Result<T> do Kotlin para AppResult<T>
 */
fun <T> Result<T>.toAppResult(): AppResult<T> {
    return fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it.toAppError()) }
    )
}

/**
 * Executa uma operação capturando exceções e convertendo para AppResult
 */
inline fun <T> runCatchingAppResult(block: () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: Exception) {
        AppResult.Error(e.toAppError())
    }
}

/**
 * Executa uma operação assíncrona capturando exceções e convertendo para AppResult
 */
suspend inline fun <T> runCatchingAppResultAsync(crossinline block: suspend () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: Exception) {
        AppResult.Error(e.toAppError())
    }
}
