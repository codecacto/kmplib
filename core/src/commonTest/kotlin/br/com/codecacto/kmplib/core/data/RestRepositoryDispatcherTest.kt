package br.com.codecacto.kmplib.core.data

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.PaginatedResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Registro(val id: String = "", val nome: String = "")

/**
 * 2.252.3: `RestConfig.requestDispatcher` — o consumidor injeta o dispatcher do `runTest`, e a leitura
 * compartilhada passa a correr no agendador do teste (o `advanceUntilIdle()` a vê). E nenhuma falha
 * de leitura compartilhada vai ao tratador de exceções não tratadas — o `runTest` reprovaria se fosse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestRepositoryDispatcherTest {

    private val pagina = """{"data":[{"id":"1","nome":"a"}],"page":1,"pageSize":20,"total":1,"totalPages":1}"""

    @Test
    fun `default do requestDispatcher e o Default`() {
        val cfg = RestConfig(HttpClient(MockEngine { respond("") }), "https://x")
        assertEquals(kotlinx.coroutines.Dispatchers.Default, cfg.requestDispatcher)
    }

    @Test
    fun `com o dispatcher do teste injetado o advanceUntilIdle ve a resposta`() = runTest {
        var gets = 0
        var deletes = 0
        val testDispatcher = StandardTestDispatcher(testScheduler)
        // O MockEngine também precisa do dispatcher do teste (`MockEngine.dispatcher`, senão roda no IO).
        val engine = MockEngine.create {
          dispatcher = testDispatcher
          addHandler { req ->
            if (req.method == HttpMethod.Delete) {
                deletes++
                respond("", HttpStatusCode.NoContent)
            } else {
                gets++
                respond(if (deletes == 0) pagina else """{"data":[],"page":1,"pageSize":20,"total":0,"totalPages":0}""",
                    HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
          }
        }
        val r = RestRepository<Registro, String>(
            RestConfig(
                HttpClient(engine),
                "https://api.codecacto.com.br",
                requestDispatcher = testDispatcher,
            ),
            "/x",
            Registro.serializer(),
        )

        var lida: ApiResult<PaginatedResponse<Registro>>? = null
        launch { lida = r.list() } // como o ViewModel faz
        advanceUntilIdle()
        assertTrue(lida is ApiResult.Success, "a resposta precisa estar pronta depois do advanceUntilIdle, veio $lida")
        assertEquals(listOf("a"), (lida as ApiResult.Success).data.data.map { it.nome })

        // O fluxo do app: exclui e relê, tudo no agendador do teste.
        var depois: ApiResult<PaginatedResponse<Registro>>? = null
        launch {
            r.delete("1")
            depois = r.list()
        }
        advanceUntilIdle()
        assertEquals(emptyList(), (depois as ApiResult.Success).data.data)
        assertEquals(2, gets)
    }

    @Test
    fun `UnconfinedTestDispatcher tambem serve`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = MockEngine.create {
            dispatcher = testDispatcher
            addHandler { respond(pagina, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
        }
        val r = RestRepository<Registro, String>(
            RestConfig(HttpClient(engine), "https://x", requestDispatcher = testDispatcher),
            "/x",
            Registro.serializer(),
        )
        var lida: ApiResult<PaginatedResponse<Registro>>? = null
        launch { lida = r.list() }
        advanceUntilIdle()
        assertNotNull(lida)
    }

    @Test
    fun `leitura compartilhada que falha DEPOIS de todos desistirem nao vira excecao nao tratada`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val voo = InFlightRequests<String, Int>(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher))
        val entrou = CompletableDeferred<Unit>()
        val bloco: suspend () -> Int = {
            entrou.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                throw IOException("conexão caiu durante o cancelamento")
            }
        }
        val chamador = launch { voo.run("k", bloco) }
        runCurrent()
        assertTrue(entrou.isCompleted)
        chamador.cancel() // último interessado sai → a leitura é cancelada e falha no finally
        advanceUntilIdle()
        assertEquals(0, voo.inFlightCount())
        // Se a IOException tivesse ido ao tratador de não tratadas, o runTest reprovaria aqui.
    }

    @Test
    fun `leitura compartilhada que falha com chamador aguardando entrega a falha so a ele`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val voo = InFlightRequests<String, Int>(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + dispatcher))
        var erro: Throwable? = null
        launch { erro = runCatching { voo.run("k") { throw IOException("reset") } }.exceptionOrNull() }
        advanceUntilIdle()
        assertTrue(erro is IOException)
        assertNull(erro?.cause?.let { it as? kotlinx.coroutines.CancellationException })
    }
}
