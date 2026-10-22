package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException

/** Um pedido recebido pelo servidor falso. */
data class Received(val method: HttpMethod, val path: String, val query: String, val body: String, val headers: Headers)

/** Resposta programada; `offline = true` derruba o transporte. */
data class Reply(val status: Int = 200, val body: String = "{}", val offline: Boolean = false, val headers: Map<String, String> = emptyMap())

/**
 * Servidor falso do motor: responde por `MÉTODO caminho` (sem a base), grava cada pedido. Rota sem resposta
 * programada = 404.
 */
class FakeInspectionServer(private val basePath: String = "/mv/v1/inspection") {
    val received = mutableListOf<Received>()
    private val routes = mutableMapOf<String, ArrayDeque<Reply>>()
    private val sticky = mutableMapOf<String, Reply>()

    /** Resposta FIXA para a rota. */
    fun on(method: String, path: String, reply: Reply) {
        sticky["$method $path"] = reply
    }

    /** Respostas em SEQUÊNCIA (a última repete). */
    fun onSeq(method: String, path: String, vararg replies: Reply) {
        routes["$method $path"] = ArrayDeque(replies.toList())
    }

    fun receivedAt(method: String, path: String): List<Received> =
        received.filter { it.method.value == method && it.path == path }

    private fun next(key: String): Reply? {
        val fila = routes[key]
        if (fila != null && fila.isNotEmpty()) return if (fila.size > 1) fila.removeFirst() else fila.first()
        return sticky[key]
    }

    val engine = MockEngine { req ->
        val caminho = req.url.encodedPath.removePrefix(basePath)
        received += Received(req.method, caminho, req.url.encodedQuery, req.body.toByteArray().decodeToString(), req.headers)
        val r = next("${req.method.value} $caminho") ?: Reply(404, """{"message":"não achei","code":"NOT_FOUND"}""")
        if (r.offline) throw IOException("rede caiu")
        val h = (mapOf(HttpHeaders.ContentType to "application/json") + r.headers).map { (k, v) -> k to listOf(v) }
        respond(r.body, HttpStatusCode.fromValue(r.status), headersOf(*h.toTypedArray()))
    }

    fun client(): DomainApiClient {
        val http = HttpClient(engine) {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
        return DomainApiClient(http, DomainTokenProvider { "tok" }, "https://api.example.com")
    }

    fun api(deviceModel: String? = "Pixel 8"): InspectionApi =
        InspectionApi(client(), InspectionApiConfig(basePath = basePath, deviceModel = deviceModel))
}
