package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Desfecho de um envio ao Umami. */
internal sealed interface UmamiSendResult {
    /**
     * Aceito (2xx). Os eventos saem da fila.
     * @property botDiscarded o Umami respondeu sem `cache` (ou `{"beep":"boop"}`): leu o User-Agent como
     *   robô e NÃO gravou. Não adianta repetir — é diagnóstico de configuração.
     * @property errors itens do lote recusados um a um (website inexistente, campo inválido).
     * @property firstError a mensagem do primeiro recusado, para o log.
     */
    data class Sent(val botDiscarded: Boolean, val errors: Int = 0, val firstError: String? = null) : UmamiSendResult

    /** 404/405 no `/api/batch`: o servidor não tem (ou bloqueou) o lote — passar a enviar um a um. */
    data object BatchUnsupported : UmamiSendResult

    /** 4xx (menos 429): o corpo é inválido. Repetir não adianta; no lote, isolar um a um. */
    data class Rejected(val status: Int) : UmamiSendResult

    /** Sem rede, 429 ou 5xx: fica na fila e tenta depois. */
    data class RetryLater(val status: Int?) : UmamiSendResult
}

/**
 * O envio HTTP ao Umami. Duas rotas, as mesmas que o script do site usa:
 * - `POST {host}{batchPath}` com um ARRAY de `{type, payload}` (até 500; mandamos ≤ 50) — uma
 *   requisição por lote. O servidor repassa os cabeçalhos (User-Agent, IP) a cada item.
 * - `POST {host}{collectPath}` com UM `{type, payload}` — a coleta, que na fábrica se chama `/api/m`
 *   (renomeada pela `COLLECT_API_ENDPOINT`; o Umami 3.4.1 reescreve só esse caminho para o `/api/send`
 *   e deixa o `/api/batch` no nome original). Recuo do lote.
 *
 * Nunca lança: falha de transporte vira [UmamiSendResult.RetryLater].
 */
internal class UmamiTransport(
    private val httpClient: HttpClient,
    hostUrl: String,
    private val collectPath: String,
    private val batchPath: String?,
) {
    private val host = hostUrl.trimEnd('/')

    suspend fun sendBatch(bodies: List<JsonObject>, userAgent: String): UmamiSendResult {
        val path = batchPath ?: return UmamiSendResult.BatchUnsupported
        val (status, text) = post(path, JsonArray(bodies).toString(), userAgent) ?: return UmamiSendResult.RetryLater(null)
        return when {
            status in 200..299 -> parseBatch(text, bodies.size)
            status == 404 || status == 405 -> UmamiSendResult.BatchUnsupported
            status == 429 || status >= 500 -> UmamiSendResult.RetryLater(status)
            else -> UmamiSendResult.Rejected(status)
        }
    }

    suspend fun sendOne(body: JsonObject, userAgent: String): UmamiSendResult {
        val (status, text) = post(collectPath, body.toString(), userAgent) ?: return UmamiSendResult.RetryLater(null)
        return when {
            status in 200..299 -> {
                val json = parse(text) as? JsonObject
                UmamiSendResult.Sent(botDiscarded = json == null || json["cache"] == null)
            }
            status == 429 || status >= 500 -> UmamiSendResult.RetryLater(status)
            else -> UmamiSendResult.Rejected(status)
        }
    }

    private suspend fun post(path: String, body: String, userAgent: String): Pair<Int, String>? = try {
        val response = httpClient.post(host + path) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, userAgent)
            setBody(body)
        }
        response.status.value to runCatching { response.bodyAsText() }.getOrDefault("")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppLogger.w(TAG, "Envio ao Umami falhou (${e::class.simpleName}) — fica na fila.")
        null
    }

    private fun parseBatch(text: String, sent: Int): UmamiSendResult.Sent {
        val json = parse(text) as? JsonObject ?: return UmamiSendResult.Sent(botDiscarded = true)
        val errors = json["errors"]?.jsonPrimitive?.intOrNull ?: 0
        val processed = json["processed"]?.jsonPrimitive?.intOrNull ?: (sent - errors)
        val firstError = runCatching {
            json["details"]?.jsonArray?.firstOrNull()?.jsonObject?.get("response")?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        // O `cache` volta do primeiro item gravado. Processou e não veio = todos lidos como robô.
        return UmamiSendResult.Sent(botDiscarded = processed > 0 && json["cache"] == null, errors = errors, firstError = firstError)
    }

    private fun parse(text: String): JsonElement? = runCatching { analyticsJson.parseToJsonElement(text) }.getOrNull()

    private companion object {
        const val TAG = "Analytics"
    }
}
