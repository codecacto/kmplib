package br.com.codecacto.kmplib.core.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend inline fun <T> handleApiCall(
    crossinline block: suspend () -> T
): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ResponseException) {
    val statusCode = e.response.status.value
    val backendMessage = runCatching {
        val body = e.response.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        json["message"]?.jsonPrimitive?.contentOrNull
            ?: json["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    ApiResult.Error(
        code = statusCode,
        message = backendMessage ?: defaultHttpErrorMessage(statusCode, e.message)
    )
} catch (e: SerializationException) {
    ApiResult.Error(code = -1, message = "Resposta inválida do servidor")
} catch (e: ConnectTimeoutException) {
    ApiResult.Error(code = -1, message = "Sem conexão com o servidor. Verifique sua internet.")
} catch (e: HttpRequestTimeoutException) {
    ApiResult.Error(code = -1, message = "Servidor demorou para responder. Tente novamente.")
} catch (e: Throwable) {
    ApiResult.Error(code = -1, message = mapGenericNetworkMessage(e))
}

fun defaultHttpErrorMessage(statusCode: Int, fallback: String?): String {
    return when (statusCode) {
        401 -> "Sessão expirada. Faça login novamente."
        403 -> "Você não tem permissão para esta ação."
        404 -> "Recurso não encontrado."
        429 -> "Muitas requisições. Aguarde um momento."
        in 500..599 -> "Servidor temporariamente indisponível. Tente novamente."
        else -> fallback ?: "Erro na requisição"
    }
}

fun mapGenericNetworkMessage(error: Throwable): String {
    val message = error.message
    return when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            "Sem conexão com a internet."
        message?.contains("Connection refused", ignoreCase = true) == true ->
            "Servidor indisponível. Tente novamente mais tarde."
        message?.contains("timeout", ignoreCase = true) == true ->
            "Tempo de conexão esgotado. Verifique sua internet."
        else -> message ?: "Falha de conexão"
    }
}
