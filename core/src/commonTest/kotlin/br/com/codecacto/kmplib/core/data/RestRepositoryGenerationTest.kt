package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class Cliente(val id: String = "", val nome: String = "")

/**
 * 2.252.1: leitura que saiu ANTES de uma mutação e voltou DEPOIS dela não pode nem servir de carona
 * à releitura nem ficar no cache. Caso real (LocAki, iOS): DELETE 204, GET 200 no mesmo segundo e o
 * cliente excluído ainda na lista por 30 s.
 */
class RestRepositoryGenerationTest {

    private val antes = """{"data":[{"id":"1","nome":"Joana"}],"page":1,"pageSize":20,"total":1,"totalPages":1}"""
    private val depois = """{"data":[],"page":1,"pageSize":20,"total":0,"totalPages":0}"""

    /** O 1º GET fica preso em [libera] e devolve a lista de ANTES; os seguintes, a de DEPOIS. */
    private class Servidor(private val antes: String, private val depois: String) {
        @Volatile var gets = 0
        @Volatile var deletes = 0
        @Volatile var excluido = false
        val primeiroGetEntrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()

        val engine = MockEngine { req ->
            val json = headersOf("Content-Type", "application/json")
            when (req.method) {
                HttpMethod.Delete -> {
                    deletes++
                    excluido = true
                    respond("", HttpStatusCode.NoContent)
                }
                else -> {
                    gets++
                    if (gets == 1) {
                        primeiroGetEntrou.complete(Unit)
                        libera.await()
                        respond(antes, HttpStatusCode.OK, json) // leu antes da exclusão
                    } else {
                        respond(if (excluido) depois else antes, HttpStatusCode.OK, json)
                    }
                }
            }
        }
    }

    private fun repo(s: Servidor, ttl: Long = 30_000L) = RestRepository<Cliente, String>(
        config = RestConfig(
            httpClient = HttpClient(s.engine),
            baseUrl = "https://api.codecacto.com.br",
            tokenProvider = { "t" },
            cacheTtlMillis = ttl,
        ),
        pathPrefix = "/locaki/v1/clientes",
        entitySerializer = Cliente.serializer(),
    )

    private fun ApiResult<PaginatedResponseOfCliente>.nomes(): List<String> =
        (this as ApiResult.Success).data.data.map { it.nome }

    @Test
    fun `releitura depois do delete vai a rede e ve o dado novo mesmo com leitura antiga em voo`() = runTest {
        val s = Servidor(antes, depois)
        val r = repo(s)

        val antiga = async { r.list() }                       // recarga do ON_RESUME, por exemplo
        withContext(Dispatchers.Default) { s.primeiroGetEntrou.await() }

        assertEquals(ApiResult.Success(Unit), r.delete("1"))  // exclusão conclui no meio
        val releitura = r.list()                              // NÃO pode pegar carona na antiga

        assertEquals(emptyList(), releitura.nomes())
        assertEquals(2, s.gets, "a releitura pós-exclusão foi à rede")

        s.libera.complete(Unit)
        assertEquals(listOf("Joana"), antiga.await().nomes()) // quem pediu antes recebe o que pediu
    }

    @Test
    fun `resultado da leitura antiga nao fica no cache`() = runTest {
        val s = Servidor(antes, depois)
        val r = repo(s)

        val antiga = async { r.list() }
        withContext(Dispatchers.Default) { s.primeiroGetEntrou.await() }
        r.delete("1")
        s.libera.complete(Unit)
        antiga.await()                                        // termina DEPOIS da exclusão

        val seguinte = r.list()
        assertEquals(emptyList(), seguinte.nomes(), "o cache não pode ter a lista de antes da exclusão")
        assertEquals(2, s.gets)

        // E o que a releitura pós-exclusão gravou continua valendo como cache.
        assertEquals(emptyList(), r.list().nomes())
        assertEquals(2, s.gets)
    }

    @Test
    fun `refresh no meio de uma leitura tambem a descarta do cache`() = runTest {
        val s = Servidor(antes, antes)
        val r = repo(s)
        val antiga = async { r.list() }
        withContext(Dispatchers.Default) { s.primeiroGetEntrou.await() }
        r.refresh()
        s.libera.complete(Unit)
        antiga.await()
        r.list()
        assertEquals(2, s.gets, "a leitura de antes do refresh não foi gravada")
    }

    @Test
    fun `coalescencia continua entre leituras da MESMA geracao`() = runTest {
        val s = Servidor(antes, depois)
        val r = repo(s)
        val chamadas = List(3) { async { r.list() } }
        withContext(Dispatchers.Default) { s.primeiroGetEntrou.await() }
        s.libera.complete(Unit)
        chamadas.awaitAll().forEach { assertEquals(listOf("Joana"), it.nomes()) }
        assertEquals(1, s.gets)

        // Mesma geração e dentro do TTL: cache, sem rede.
        assertEquals(listOf("Joana"), r.list().nomes())
        assertEquals(1, s.gets)
    }

    @Test
    fun `getById tambem nao grava o dado de antes da mutacao`() = runTest {
        var gets = 0
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val engine = MockEngine { req ->
            val json = headersOf("Content-Type", "application/json")
            if (req.method == HttpMethod.Put) return@MockEngine respond("""{"id":"1","nome":"Nova"}""", HttpStatusCode.OK, json)
            gets++
            if (gets == 1) {
                entrou.complete(Unit)
                libera.await()
                respond("""{"id":"1","nome":"Velha"}""", HttpStatusCode.OK, json)
            } else {
                respond("""{"id":"1","nome":"Nova"}""", HttpStatusCode.OK, json)
            }
        }
        val r = RestRepository<Cliente, String>(
            RestConfig(HttpClient(engine), "https://x", cacheTtlMillis = 30_000L),
            "/c",
            Cliente.serializer(),
        )
        val antiga = async { r.getById("1") }
        withContext(Dispatchers.Default) { entrou.await() }
        r.update("1", Cliente("1", "Nova"))
        assertEquals("Nova", (r.getById("1") as ApiResult.Success).data.nome)
        libera.complete(Unit)
        antiga.await()
        assertEquals("Nova", (r.getById("1") as ApiResult.Success).data.nome)
        assertEquals(2, gets)
    }
}

private typealias PaginatedResponseOfCliente = br.com.codecacto.kmplib.core.network.PaginatedResponse<Cliente>
