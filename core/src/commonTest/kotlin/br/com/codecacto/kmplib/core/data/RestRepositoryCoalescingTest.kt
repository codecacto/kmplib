package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class Item(val id: String = "", val nome: String = "")

/**
 * 2.252.0: o app que abre e pede a MESMA página em 2–3 ViewModels ao mesmo tempo (LocAki, out/2026)
 * faz UM GET, não três.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestRepositoryCoalescingTest {

    private class Api {
        @Volatile var envios = 0
        val entrou = CompletableDeferred<Unit>()
        val libera = CompletableDeferred<Unit>()
        val queries = mutableListOf<String>()
        val auths = mutableListOf<String?>()

        val engine = MockEngine { req ->
            envios++
            queries += req.url.encodedPath + "?" + req.url.encodedQuery
            auths += req.headers["Authorization"]
            entrou.complete(Unit)
            libera.await()
            val corpo = if (req.url.encodedPath.endsWith("/itens")) {
                """{"data":[{"id":"1","nome":"Betoneira"}],"page":1,"pageSize":20,"total":1,"totalPages":1}"""
            } else {
                """{"id":"1","nome":"Betoneira"}"""
            }
            respond(corpo, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
    }

    private fun repo(api: Api, token: () -> String? = { "t1" }) = RestRepository<Item, String>(
        config = RestConfig(
            httpClient = HttpClient(api.engine),
            baseUrl = "https://api.codecacto.com.br",
            tokenProvider = { token() },
            cacheTtlMillis = 0L, // sem cache: prova a coalescência, não o TTL
        ),
        pathPrefix = "/locaki/v1/itens",
        entitySerializer = Item.serializer(),
    )

    @Test
    fun `tres list iguais simultaneos fazem UM GET e recebem o mesmo resultado`() = runTest {
        val api = Api()
        val r = repo(api)
        val chamadas = List(3) { async { r.list(filters = mapOf("status" to "ativo"), page = 1, pageSize = 20) } }
        withContext(Dispatchers.Default) { api.entrou.await() }
        api.libera.complete(Unit)
        val resultados = chamadas.awaitAll()

        assertEquals(1, api.envios)
        assertEquals<List<String?>>(listOf("Bearer t1"), api.auths)
        resultados.forEach { res ->
            assertTrue(res is ApiResult.Success && res.data.data.single().nome == "Betoneira")
        }
    }

    @Test
    fun `paginas diferentes NAO sao coalescidas`() = runTest {
        val api = Api()
        val r = repo(api)
        api.libera.complete(Unit)
        val chamadas = listOf(async { r.list(page = 1) }, async { r.list(page = 2) })
        chamadas.awaitAll()
        assertEquals(2, api.envios)
    }

    @Test
    fun `getById igual simultaneo faz UM GET`() = runTest {
        val api = Api()
        val r = repo(api)
        val chamadas = List(2) { async { r.getById("1") } }
        withContext(Dispatchers.Default) { api.entrou.await() }
        api.libera.complete(Unit)
        chamadas.awaitAll().forEach { assertTrue(it is ApiResult.Success) }
        assertEquals(1, api.envios)
    }

    @Test
    fun `sessoes diferentes nunca compartilham resposta`() = runTest {
        val api = Api()
        var token = "conta-a"
        val r = repo(api) { token }
        val a = async { r.list(page = 1) }
        withContext(Dispatchers.Default) { api.entrou.await() }
        token = "conta-b"
        val b = async { r.list(page = 1) }
        api.libera.complete(Unit)
        listOf(a, b).awaitAll()
        assertEquals(2, api.envios)
        assertEquals<Set<String?>>(setOf("Bearer conta-a", "Bearer conta-b"), api.auths.toSet())
    }

    @Test
    fun `terminada a leitura sem cache a proxima vai ao servidor de novo`() = runTest {
        val api = Api()
        val r = repo(api)
        api.libera.complete(Unit)
        r.list(page = 1)
        r.list(page = 1)
        assertEquals(2, api.envios)
    }

    @Test
    fun `tela que pediu primeiro cancela a carga e quem pegou carona recebe a lista`() = runTest {
        val api = Api()
        val r = repo(api)
        val loadJobDosClientes = async { r.list(page = 2) }      // ClientesViewModel (líder)
        withContext(Dispatchers.Default) { api.entrou.await() }
        val locacoes = async { r.list(page = 2) }                // LocacoesViewModel (carona)
        advanceUntilIdle()
        loadJobDosClientes.cancel()                              // `loadJob?.cancel()` + recomeçar
        api.libera.complete(Unit)

        val res = locacoes.await()
        assertTrue(res is ApiResult.Success && res.data.data.single().nome == "Betoneira", "veio $res")
        assertEquals(1, api.envios, "uma requisição só")
    }
}
