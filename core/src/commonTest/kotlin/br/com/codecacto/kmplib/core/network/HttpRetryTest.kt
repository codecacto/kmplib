package br.com.codecacto.kmplib.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.options
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpResponseData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Prova a política de nova tentativa do [createHttpClient] (2.252.0) sobre a configuração REAL do
 * factory — o mesmo `HttpTimeout`, a mesma ordem de plugins — trocando só o engine pelo MockEngine.
 */
class HttpRetryTest {

    /** Cada envio que chegou ao "servidor", na ordem. */
    private class Servidor(
        private val roteiro: suspend MockRequestHandleScope.(tentativa: Int, req: HttpRequestData) -> HttpResponseData,
    ) {
        var envios = 0
            private set
        val engine = MockEngine { req ->
            envios++
            roteiro(envios, req)
        }
    }

    private fun cliente(
        servidor: Servidor,
        options: HttpClientOptions = HttpClientOptions(enableLogging = false),
        esperas: MutableList<Long> = mutableListOf(),
    ): HttpClient = createHttpClient(
        engine = servidor.engine,
        options = options,
        retryRandom = Random(42),
        retryDelay = { esperas += it },
    )

    private fun MockRequestHandleScope.ok(corpo: String = "ok") = respond(corpo, HttpStatusCode.OK)

    // ---------------------------------------------------------------------------------------------
    // Default
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `nova tentativa vem LIGADA por default com 2 tentativas a mais`() {
        val padrao = HttpClientOptions().retry
        assertTrue(padrao.enabled)
        assertEquals(2, padrao.maxRetries)
        assertEquals(setOf(502, 503, 504), padrao.retryOnStatus)
        assertEquals(setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options), padrao.methods)

        val http = createHttpClient(HttpClientOptions(enableLogging = false))
        try {
            assertNotNull(http.pluginOrNull(HttpRequestRetry))
        } finally {
            http.close()
        }
    }

    @Test
    fun `Disabled nao instala o plugin e a falha volta na primeira`() = runTest {
        val servidor = Servidor { _, _ -> throw IOException("Connection reset") }
        val http = cliente(servidor, HttpClientOptions(enableLogging = false, retry = HttpRetryPolicy.Disabled))
        assertNull(http.pluginOrNull(HttpRequestRetry))
        assertFailsWith<IOException> { http.get("https://api.example.com/x") }
        assertEquals(1, servidor.envios)
    }

    // ---------------------------------------------------------------------------------------------
    // O que repete
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `GET com Connection reset na 1a tentativa recupera na 2a`() = runTest {
        val esperas = mutableListOf<Long>()
        val servidor = Servidor { n, _ -> if (n == 1) throw IOException("Connection reset") else ok("dados") }
        val resposta = cliente(servidor, esperas = esperas).get("https://api.example.com/v1/equipamentos")

        assertEquals(HttpStatusCode.OK, resposta.status)
        assertEquals("dados", resposta.bodyAsText())
        assertEquals(2, servidor.envios)
        assertEquals(1, esperas.size)
        assertTrue(esperas.single() in 200L..400L, "1a espera entre 200 e 400 ms, foi ${esperas.single()}")
    }

    @Test
    fun `HEAD e OPTIONS tambem repetem`() = runTest {
        val head = Servidor { n, _ -> if (n == 1) throw IOException("unexpected end of stream") else ok() }
        assertEquals(HttpStatusCode.OK, cliente(head).head("https://api.example.com/x").status)
        assertEquals(2, head.envios)

        val options = Servidor { n, _ -> if (n == 1) throw IOException("reset") else ok() }
        assertEquals(HttpStatusCode.OK, cliente(options).options("https://api.example.com/x").status)
        assertEquals(2, options.envios)
    }

    @Test
    fun `503 repete ate o teto e devolve a ultima resposta`() = runTest {
        val esperas = mutableListOf<Long>()
        val servidor = Servidor { _, _ -> respond("fora", HttpStatusCode.ServiceUnavailable) }
        val resposta = cliente(servidor, esperas = esperas).get("https://api.example.com/x")

        assertEquals(HttpStatusCode.ServiceUnavailable, resposta.status)
        assertEquals(3, servidor.envios, "1 envio + 2 novas tentativas")
        assertEquals(2, esperas.size)
        assertTrue(esperas[1] in 400L..800L, "2a espera entre 400 e 800 ms, foi ${esperas[1]}")
    }

    @Test
    fun `502 e 504 repetem e recuperam`() = runTest {
        for (status in listOf(HttpStatusCode.BadGateway, HttpStatusCode.GatewayTimeout)) {
            val servidor = Servidor { n, _ -> if (n == 1) respond("", status) else ok() }
            assertEquals(HttpStatusCode.OK, cliente(servidor).get("https://api.example.com/x").status)
            assertEquals(2, servidor.envios, "status $status")
        }
    }

    @Test
    fun `com expectSuccess o 503 seguido de 200 tambem recupera`() = runTest {
        val servidor = Servidor { n, _ -> if (n == 1) respond("", HttpStatusCode.ServiceUnavailable) else ok("x") }
        val resposta = cliente(servidor).get("https://api.example.com/x") { expectSuccess = true }
        assertEquals("x", resposta.bodyAsText())
        assertEquals(2, servidor.envios)
    }

    @Test
    fun `timeout de socket e de conexao repetem`() = runTest {
        val servidor = Servidor { n, req ->
            when (n) {
                1 -> throw io.ktor.client.network.sockets.ConnectTimeoutException("connect timeout")
                2 -> throw io.ktor.client.network.sockets.SocketTimeoutException("read timeout")
                else -> ok()
            }
        }
        assertEquals(HttpStatusCode.OK, cliente(servidor).get("https://api.example.com/x").status)
        assertEquals(3, servidor.envios)
    }

    // ---------------------------------------------------------------------------------------------
    // O que NÃO repete
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `POST com IOException NAO repete`() = runTest {
        val servidor = Servidor { _, _ -> throw IOException("Connection reset") }
        assertFailsWith<IOException> {
            cliente(servidor).post("https://api.example.com/v1/cobrancas") { setBody("{}") }
        }
        assertEquals(1, servidor.envios, "POST repetido duplicaria a cobrança")
    }

    @Test
    fun `POST e PATCH com 503 NAO repetem`() = runTest {
        val post = Servidor { _, _ -> respond("", HttpStatusCode.ServiceUnavailable) }
        cliente(post).post("https://api.example.com/x")
        assertEquals(1, post.envios)

        val patch = Servidor { _, _ -> respond("", HttpStatusCode.ServiceUnavailable) }
        cliente(patch).patch("https://api.example.com/x")
        assertEquals(1, patch.envios)
    }

    @Test
    fun `PUT e DELETE ficam fora do default e entram por opcao`() = runTest {
        val put = Servidor { _, _ -> throw IOException("reset") }
        assertFailsWith<IOException> { cliente(put).put("https://api.example.com/x") }
        assertEquals(1, put.envios)

        val del = Servidor { _, _ -> throw IOException("reset") }
        assertFailsWith<IOException> { cliente(del).delete("https://api.example.com/x") }
        assertEquals(1, del.envios)

        val comPut = Servidor { n, _ -> if (n == 1) throw IOException("reset") else ok() }
        val opcoes = HttpClientOptions(
            enableLogging = false,
            retry = HttpRetryPolicy(methods = HttpRetryPolicy.DEFAULT_METHODS + HttpMethod.Put),
        )
        assertEquals(HttpStatusCode.OK, cliente(comPut, opcoes).put("https://api.example.com/x").status)
        assertEquals(2, comPut.envios)
    }

    @Test
    fun `4xx NAO repete`() = runTest {
        for (status in listOf(400, 401, 403, 404, 409, 429)) {
            val servidor = Servidor { _, _ -> respond("", HttpStatusCode.fromValue(status)) }
            val resposta = cliente(servidor).get("https://api.example.com/x")
            assertEquals(status, resposta.status.value)
            assertEquals(1, servidor.envios, "status $status não pode repetir")
        }
    }

    @Test
    fun `4xx com expectSuccess vira excecao sem repetir`() = runTest {
        val servidor = Servidor { _, _ -> respond("", HttpStatusCode.NotFound) }
        assertFailsWith<ResponseException> {
            cliente(servidor).get("https://api.example.com/x") { expectSuccess = true }
        }
        assertEquals(1, servidor.envios)
    }

    @Test
    fun `500 NAO repete — defeito do servidor nao melhora repetindo`() = runTest {
        val servidor = Servidor { _, _ -> respond("", HttpStatusCode.InternalServerError) }
        assertEquals(500, cliente(servidor).get("https://api.example.com/x").status.value)
        assertEquals(1, servidor.envios)
    }

    @Test
    fun `503 com Retry-After maior que o teto NAO repete`() = runTest {
        val servidor = Servidor { _, _ ->
            respond("", HttpStatusCode.ServiceUnavailable, headersOf("Retry-After", "120"))
        }
        cliente(servidor).get("https://api.example.com/x")
        assertEquals(1, servidor.envios)
    }

    @Test
    fun `503 com Retry-After curto repete esperando pelo menos o pedido`() = runTest {
        val esperas = mutableListOf<Long>()
        val servidor = Servidor { n, _ ->
            if (n == 1) respond("", HttpStatusCode.ServiceUnavailable, headersOf("Retry-After", "1")) else ok()
        }
        assertEquals(HttpStatusCode.OK, cliente(servidor, esperas = esperas).get("https://api.example.com/x").status)
        assertEquals(listOf(1_000L), esperas)
    }

    @Test
    fun `cancelamento lancado pelo envio NAO repete`() = runTest {
        val servidor = Servidor { _, _ -> throw CancellationException("tela saiu") }
        assertFailsWith<CancellationException> { cliente(servidor).get("https://api.example.com/x") }
        assertEquals(1, servidor.envios)
    }

    @Test
    fun `cancelar quem chamou NAO dispara nova tentativa`() = runTest {
        val entrou = CompletableDeferred<Unit>()
        val servidor = Servidor { _, _ ->
            entrou.complete(Unit)
            delay(60_000)
            ok()
        }
        val http = cliente(servidor)
        val chamada = async(Dispatchers.Default) { http.get("https://api.example.com/x") }
        withContext(Dispatchers.Default) { entrou.await() }
        chamada.cancel()
        assertFailsWith<CancellationException> { chamada.await() }
        withContext(Dispatchers.Default) { delay(200) }
        assertEquals(1, servidor.envios)
    }

    @Test
    fun `o teto da requisicao vale para a chamada INTEIRA com as novas tentativas`() = runTest {
        val servidor = Servidor { n, _ ->
            if (n == 1) throw IOException("Connection reset")
            delay(10_000) // a 2a tentativa trava
            ok()
        }
        val http = createHttpClient(
            engine = servidor.engine,
            options = HttpClientOptions(enableLogging = false, requestTimeoutMillis = 400),
            retryDelay = { },
        )
        val inicio = TimeSource.Monotonic.markNow()
        val erro = withContext(Dispatchers.Default) {
            runCatching { http.get("https://api.example.com/x") }.exceptionOrNull()
        }
        val decorrido = inicio.elapsedNow().inWholeMilliseconds

        assertNotNull(erro)
        assertTrue(
            erro is HttpRequestTimeoutException || erro.cause is HttpRequestTimeoutException,
            "esperava o timeout da requisição, veio $erro",
        )
        assertEquals(2, servidor.envios, "timeout da requisição não abre a 3a tentativa")
        assertTrue(decorrido < 3_000, "o teto não pode multiplicar com as tentativas (levou $decorrido ms)")
    }

    // ---------------------------------------------------------------------------------------------
    // Regras puras
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `politica recusa POST PATCH e status fora de 5xx`() {
        assertFailsWith<IllegalArgumentException> { HttpRetryPolicy(methods = setOf(HttpMethod.Get, HttpMethod.Post)) }
        assertFailsWith<IllegalArgumentException> { HttpRetryPolicy(methods = setOf(HttpMethod.Patch)) }
        assertFailsWith<IllegalArgumentException> { HttpRetryPolicy(retryOnStatus = setOf(429)) }
        assertFailsWith<IllegalArgumentException> { HttpRetryPolicy(maxRetries = -1) }
        assertFailsWith<IllegalArgumentException> { HttpRetryPolicy(baseDelayMillis = 500, maxDelayMillis = 100) }
    }

    @Test
    fun `espera exponencial com jitter e teto`() {
        val p = HttpRetryPolicy()
        repeat(200) { semente ->
            val r = Random(semente)
            assertTrue(p.retryDelayMillis(1, null, r) in 200L..400L)
            assertTrue(p.retryDelayMillis(2, null, r) in 400L..800L)
            assertTrue(p.retryDelayMillis(3, null, r) in 800L..1_600L)
            assertTrue(p.retryDelayMillis(10, null, r) in 1_000L..2_000L)
            assertTrue(p.retryDelayMillis(1_000, null, r) in 1_000L..2_000L)
        }
        // Sementes diferentes espalham — é o que impede 15 GETs de voltarem no mesmo milissegundo.
        val sorteados = (0 until 50).map { p.retryDelayMillis(1, null, Random(it)) }.toSet()
        assertTrue(sorteados.size > 10)
        // Retry-After aceito é piso; data HTTP é ignorada.
        assertTrue(p.retryDelayMillis(1, "2", Random(1)) >= 2_000)
        assertTrue(p.retryDelayMillis(1, "Wed, 21 Oct 2026 07:28:00 GMT", Random(1)) in 200L..400L)
    }

    @Test
    fun `classificacao das excecoes`() {
        assertTrue(isTransientTransportFailure(IOException("Connection reset")))
        assertFalse(isTransientTransportFailure(CancellationException("cancelado")))
        assertFalse(
            isTransientTransportFailure(
                CancellationException("timeout", HttpRequestTimeoutException("u", 1)),
            ),
        )
        assertFalse(isTransientTransportFailure(HttpRequestTimeoutException("u", 1)))
        assertFalse(isTransientTransportFailure(IllegalStateException("bug")))
        assertFalse(isTransientTransportFailure(kotlinx.serialization.SerializationException("json")))
    }

    @Test
    fun `resposta decide por metodo status e Retry-After`() {
        val p = HttpRetryPolicy()
        assertTrue(p.shouldRetryResponse(HttpMethod.Get, 503, null))
        assertTrue(p.shouldRetryResponse(HttpMethod.Get, 503, "2"))
        assertFalse(p.shouldRetryResponse(HttpMethod.Get, 503, "3"))
        assertFalse(p.shouldRetryResponse(HttpMethod.Get, 500, null))
        assertFalse(p.shouldRetryResponse(HttpMethod.Get, 404, null))
        assertFalse(p.shouldRetryResponse(HttpMethod.Post, 503, null))
        assertFalse(p.shouldRetryException(HttpMethod.Post, IOException("reset")))
        assertTrue(p.shouldRetryException(HttpMethod.Get, IOException("reset")))
    }
}
