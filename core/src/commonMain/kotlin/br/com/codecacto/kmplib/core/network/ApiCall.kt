package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.util.AppLogger
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
    logApiCallFailure(e)
    ApiResult.Error(code = -1, message = "Resposta inválida do servidor")
} catch (e: ConnectTimeoutException) {
    logApiCallFailure(e)
    ApiResult.Error(code = -1, message = "Não foi possível falar com o servidor. Tente novamente.")
} catch (e: HttpRequestTimeoutException) {
    logApiCallFailure(e)
    ApiResult.Error(code = -1, message = "Servidor demorou para responder. Tente novamente.")
} catch (e: Throwable) {
    logApiCallFailure(e)
    ApiResult.Error(code = -1, message = mapGenericNetworkMessage(e))
}

/**
 * Registra a causa REAL de uma falha sem resposta HTTP (2.252.4). Antes, o `catch (Throwable)` virava
 * uma frase genérica para a tela e a exceção sumia: no LocAki, `list()` voltava "Não foi possível
 * falar com o servidor" logo depois de um `RESPONSE: 200` no log, e não havia como saber por quê.
 *
 * Sai no `AppLogger` (tag [API_CALL_LOG_TAG], aviso): classe + mensagem da exceção e de até 3 causas.
 * Nada de cabeçalho nem corpo (não estão na exceção), e a query de qualquer URL que a mensagem traga
 * é cortada — ela pode carregar filtro com dado pessoal.
 */
@PublishedApi
internal fun logApiCallFailure(error: Throwable) {
    AppLogger.w(API_CALL_LOG_TAG, "Falha sem resposta HTTP: ${describeFailureChain(error)}")
}

/** "Tipo: mensagem ← causa: Tipo: mensagem …", sem query de URL, no máximo 3 causas. */
internal fun describeFailureChain(error: Throwable): String = buildString {
    var atual: Throwable? = error
    var nivel = 0
    while (atual != null && nivel <= MAX_LOGGED_CAUSES) {
        if (nivel > 0) append(" ← causa: ")
        append(atual::class.simpleName ?: "Throwable")
        atual.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(redactUrlQueries(it).take(200)) }
        if (atual.cause === atual) break
        atual = atual.cause
        nivel++
    }
}

private val URL_QUERY = Regex("""(https?://[^\s?#\]]+)\?[^\s\]]*""")

internal fun redactUrlQueries(text: String): String = URL_QUERY.replace(text) { "${it.groupValues[1]}?…" }

const val API_CALL_LOG_TAG: String = "ApiCall"
private const val MAX_LOGGED_CAUSES = 3

fun defaultHttpErrorMessage(statusCode: Int, fallback: String?): String {
    return when (statusCode) {
        401 -> "Sessão expirada. Faça login novamente."
        403 -> "Você não tem permissão para esta ação."
        404 -> "Recurso não encontrado."
        // ⚠️ A frase fala do SISTEMA, não da pessoa (28/ago/2026). "Muitas requisições" era lida
        // como acusação — o fundador do Cidade Conectada bateu no limite navegando sozinho e
        // respondeu: "a mensagem não está certa, não só eu estou usando". Quem estourou um teto de
        // rate limit não fez nada de errado: ou o app pediu demais, ou o teto está apertado. Os
        // dois são problema nosso, e a frase tem de dizer isso.
        429 -> "O aplicativo está indo rápido demais. Tente de novo em instantes."
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
