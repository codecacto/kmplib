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
    ApiResult.Error(code = -1, message = "Não foi possível falar com o servidor. Tente novamente.")
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

/**
 * Traduz a exceção de rede numa frase para o usuário.
 *
 * ⚠️ **Nenhuma destas frases AFIRMA que o aparelho está sem internet** — e é de propósito. Falha de
 * DNS ("Unable to resolve host") acontece nos dois casos: aparelho offline **e** endereço que não
 * existe (host errado no build, domínio novo que ainda não propagou, subdomínio de nível a mais que
 * o curinga não cobre). Dizer "sem conexão com a internet" nesses casos manda a pessoa conferir o
 * wi-fi enquanto o problema está no app — foi assim no NeuroCoreX (`api.neurocorex…` em vez de
 * `api-neurocorex…`) e de novo no Mirassol Conectado, com o celular online e o servidor de pé.
 *
 * Quem sabe de verdade se há internet é o `ConnectivityObserver`, e quem avisa é o
 * `ConnectivityGate` — que já cobre a tela quando o aparelho está offline. Se o gate não está
 * aparecendo, contradizê-lo aqui é o erro.
 */
fun mapGenericNetworkMessage(error: Throwable): String {
    val message = error.message
    return when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            "Não foi possível encontrar o servidor. Verifique sua conexão e tente novamente."
        message?.contains("Connection refused", ignoreCase = true) == true ->
            "Servidor indisponível. Tente novamente mais tarde."
        message?.contains("timeout", ignoreCase = true) == true ->
            "Tempo de conexão esgotado. Verifique sua internet."
        else -> message ?: "Falha de conexão"
    }
}
