package br.com.codecacto.kmplib.core.network

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * POST com resposta 200 em GZIP (chunked, como o apps-api chega pela Cloudflare), pelo `createHttpClient`
 * REAL (OkHttp + HttpRequestRetry + ContentEncoding + Logging). LocAki 1.8.0, 25/nov/2026: no Android o
 * `cancelar` da locação logava `RESPONSE: 200` e, 1 ms depois, "Channel was cancelled" — o app mostrava
 * "sem conexão" com a locação cancelada no servidor.
 */
class PostGzipOkHttpTest {

    private lateinit var server: HttpServer
    private val cortes = AtomicInteger()

    private val corpo: String = buildString {
        append("""{"id":"loc-1","itens":[""")
        append((1..120).joinToString(",") { """{"n":$it,"nome":"Escora metálica $it","valor":"10.00"}""" })
        append("]}")
    }

    @BeforeTest
    fun subir() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/v2/locacoes/1/cancelar") { ex ->
            ex.requestBody.readBytes()
            val gz = ex.requestHeaders.getFirst("Accept-Encoding")?.contains("gzip") == true
            ex.responseHeaders.add("Content-Type", "application/json")
            if (gz) ex.responseHeaders.add("Content-Encoding", "gzip")
            ex.sendResponseHeaders(200, 0) // chunked
            ex.responseBody.use { out ->
                if (gz) GZIPOutputStream(out).use { it.write(corpo.toByteArray()) } else out.write(corpo.toByteArray())
            }
        }
        // 1ª tentativa de cada POST: 200 + metade do corpo e a conexão cai (o corte do celular, 25/nov).
        server.createContext("/v2/locacoes/simulacao") { ex ->
            ex.requestBody.readBytes()
            val n = cortes.incrementAndGet()
            val b = corpo.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            if (n % 2 == 1) {
                ex.responseBody.write(b, 0, b.size / 2); ex.responseBody.flush(); ex.close()
            } else {
                ex.responseBody.use { it.write(b) }
            }
        }
        server.start()
    }

    @AfterTest
    fun descer() = server.stop(0)

    @Test
    fun `POST com resposta gzip le o corpo inteiro, sempre`() = runBlocking {
        val client = createHttpClient(HttpClientOptions())
        val url = "http://127.0.0.1:${server.address.port}/v2/locacoes/1/cancelar"
        var falhas = 0
        val mensagens = mutableSetOf<String>()
        repeat(300) {
            try {
                val texto = client.post(url) { contentType(ContentType.Application.Json); setBody("""{"motivo":"x"}""") }.bodyAsText()
                if (texto != corpo) { falhas++; mensagens += "corpo diferente (${texto.length})" }
            } catch (e: Exception) {
                falhas++; mensagens += "${e::class.simpleName}: ${e.message}"
            }
        }
        client.close()
        assertEquals(0, falhas, "falhas em 300: $mensagens")
    }


    @Test
    fun `POST marcado idempotent se recupera do corpo cortado depois do 200`() = runBlocking {
        val client = createHttpClient(HttpClientOptions())
        val url = "http://127.0.0.1:${server.address.port}/v2/locacoes/simulacao"
        repeat(20) {
            val texto = client.post(url) { idempotent(); contentType(ContentType.Application.Json); setBody("{}") }.bodyAsText()
            assertEquals(corpo, texto)
        }
        client.close()
    }

    @Test
    fun `POST sem a marca continua sem repetir (nao duplica gravacao)`() = runBlocking {
        val client = createHttpClient(HttpClientOptions())
        val url = "http://127.0.0.1:${server.address.port}/v2/locacoes/simulacao"
        cortes.set(0) // a próxima é a 1ª: corta
        assertFailsWith<Exception> {
            client.post(url) { contentType(ContentType.Application.Json); setBody("{}") }.bodyAsText()
        }
        assertEquals(1, cortes.get(), "POST sem marca não pode ter sido repetido")
        client.close()
    }
}
