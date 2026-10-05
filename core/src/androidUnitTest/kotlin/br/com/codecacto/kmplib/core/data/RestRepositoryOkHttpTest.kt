package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.createHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@Serializable
private data class ClienteReal(val id: String = "", val nome: String = "", val obs: String = "")

/**
 * Reprodução fiel do LocAki em aparelho: engine OkHttp REAL (o `createHttpClient` da lib, com
 * retry/gzip/timeout) sobre um servidor HTTP real (JDK `HttpServer`), corpo de ~30 KB escrito em
 * pedaços depois dos headers, mutação seguida de leituras iguais concorrentes com um chamador
 * desistindo no meio.
 */
class RestRepositoryOkHttpTest {

    private lateinit var server: HttpServer
    private val gets = AtomicInteger()
    private val cortes = AtomicInteger()

    @Volatile
    private var excluido = false

    private fun pagina(n: Int): String {
        val obs = (1..200).map { ((it * 31 + n) % 26 + 97).toChar() }.joinToString("")
        val itens = (1..n).joinToString(",") { """{"id":"$it","nome":"Cliente $it","obs":"$obs"}""" }
        return """{"data":[$itens],"page":1,"pageSize":100,"total":$n,"totalPages":1}"""
    }

    @BeforeTest
    fun subir() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        // 1ª tentativa de cada leitura: 200 + metade do corpo e a conexão cai (o que a rede móvel / um GOAWAY fazem).
        server.createContext("/locaki/v1/corta") { ex ->
            val n = cortes.incrementAndGet()
            val b = pagina(100).toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong()) // Content-Length: o corte é "unexpected end of stream"
            if (n % 2 == 1) { // toda 1ª tentativa de cada leitura
                ex.responseBody.write(b, 0, b.size / 2)
                ex.responseBody.flush()
                (ex as com.sun.net.httpserver.HttpExchange).close()
            } else {
                ex.responseBody.use { it.write(b) }
            }
        }
        server.createContext("/locaki/v1/clientes") { ex ->
            when (ex.requestMethod) {
                "DELETE" -> {
                    excluido = true
                    ex.sendResponseHeaders(204, -1)
                    ex.close()
                }
                "POST" -> {
                    val b = """{"id":"999","nome":"Novo"}""".toByteArray()
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(201, b.size.toLong())
                    ex.responseBody.use { it.write(b) }
                }
                else -> {
                    gets.incrementAndGet()
                    val cru = pagina(if (excluido) 99 else 100).toByteArray()
                    // Como o Traefik/Cloudflare: gzip quando o cliente pede (o `createHttpClient` pede).
                    val gzip = ex.requestHeaders.getFirst("Accept-Encoding")?.contains("gzip") == true
                    val b = if (gzip) {
                        java.io.ByteArrayOutputStream().also { bos ->
                            java.util.zip.GZIPOutputStream(bos).use { it.write(cru) }
                        }.toByteArray()
                    } else cru
                    ex.responseHeaders.add("Content-Type", "application/json")
                    if (gzip) ex.responseHeaders.add("Content-Encoding", "gzip")
                    ex.sendResponseHeaders(200, 0) // chunked: os headers saem antes do corpo
                    try {
                        ex.responseBody.use { out ->
                            var i = 0
                            while (i < b.size) {
                                val fim = minOf(i + 512, b.size)
                                out.write(b, i, fim - i)
                                out.flush()
                                Thread.sleep(10)
                                i = fim
                            }
                        }
                    } catch (_: Exception) {
                        // cliente desistiu no meio
                    }
                }
            }
        }
        server.start()
    }

    @AfterTest
    fun descer() = server.stop(0)

    private fun repo(): RestRepository<ClienteReal, String> = RestRepository(
        RestConfig(
            httpClient = createHttpClient(HttpClientOptions(enableLogging = true)),
            baseUrl = "http://127.0.0.1:${server.address.port}",
        ),
        "/locaki/v1/clientes",
        ClienteReal.serializer(),
    )

    @Test
    fun `mutacao seguida de leituras iguais concorrentes com um chamador desistindo no meio`() = runBlocking {
        val r = repo()
        repeat(15) { rodada ->
            if (rodada % 2 == 0) r.delete("1") else r.create(ClienteReal(nome = "Novo"))
            val chamadas = List(3) { async { r.list(page = 1, pageSize = 100) } }
            val desistente = async { r.list(page = 1, pageSize = 100) }
            delay((rodada * 7L) % 60) // antes, durante os headers, no meio do corpo
            desistente.cancel()
            chamadas.awaitAll().forEach { res ->
                assertTrue(res is ApiResult.Success, "rodada $rodada: veio $res")
            }
        }
    }

    @Test
    fun `todos de uma rodada desistem e as leituras seguintes funcionam`() = runBlocking {
        val r = repo()
        repeat(10) { rodada ->
            r.delete("1")
            val desistentes = List(2) { async { r.list(page = 1, pageSize = 100) } }
            delay(5L + rodada * 6L)
            desistentes.forEach { it.cancel() }
            List(2) { async { r.list(page = 1, pageSize = 100) } }.awaitAll().forEach {
                assertTrue(it is ApiResult.Success, "rodada $rodada: veio $it")
            }
        }
    }

    @Test
    fun `recarga do ViewModel - cancela a carga e pede de novo na hora`() = runBlocking {
        val r = repo()
        repeat(15) { rodada ->
            r.delete("1")
            var carga = async { r.list(page = 1, pageSize = 100) }
            val outraTela = async { r.list(page = 1, pageSize = 100) }
            delay((rodada * 5L) % 50)
            carga.cancel() // loadJob?.cancel()
            carga = async { r.list(page = 1, pageSize = 100) } // ...e recomeça
            listOf(carga, outraTela).awaitAll().forEach {
                assertTrue(it is ApiResult.Success, "rodada $rodada: veio $it")
            }
        }
    }

    @Test
    fun `estresse - telas recarregando sem parar enquanto ha cadastro e exclusao`() = runBlocking {
        val r = repo()
        val erros = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val rnd = kotlin.random.Random(7)
        val telas = List(6) { tela ->
            launch(kotlinx.coroutines.Dispatchers.Default) {
                repeat(25) {
                    val carga = async { r.list(page = 1, pageSize = 100) }
                    delay(rnd.nextLong(0, 40))
                    if (rnd.nextInt(3) == 0) {
                        carga.cancel() // recarga: desiste e pede de novo
                    } else {
                        val res = carga.await()
                        if (res !is ApiResult.Success) erros += "tela $tela: $res"
                    }
                }
            }
        }
        val mutacoes = launch(kotlinx.coroutines.Dispatchers.Default) {
            repeat(20) {
                if (it % 2 == 0) r.delete("1") else r.create(ClienteReal(nome = "Novo"))
                delay(rnd.nextLong(5, 50))
            }
        }
        telas.joinAll()
        mutacoes.join()
        assertTrue(erros.isEmpty(), "leituras que viraram erro: ${erros.take(5)}")
    }

    @Test
    fun `200 com o corpo cortado no meio e repetido e a lista chega`() = runBlocking {
        val r = RestRepository<ClienteReal, String>(
            RestConfig(
                httpClient = createHttpClient(HttpClientOptions(enableLogging = false)),
                baseUrl = "http://127.0.0.1:${server.address.port}",
                cacheTtlMillis = 0L,
            ),
            "/locaki/v1/corta",
            ClienteReal.serializer(),
        )
        // Até a 2.252.3 isto oscilava: quando o corte era notado DEPOIS de o retry decidir pelos
        // cabeçalhos, a leitura virava Error(-1, "unexpected end of stream") com o 200 no log.
        repeat(30) { rodada ->
            val res = r.list(page = 1, pageSize = 100)
            assertTrue(res is ApiResult.Success, "rodada $rodada: veio $res")
        }
        assertTrue(cortes.get() == 60, "cada leitura = 1 corte + 1 nova tentativa; foram ${cortes.get()}")
    }
}
