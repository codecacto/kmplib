package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Teto de tempo por chamada nos verbos JSON (`timeoutMillis: Long? = null`) — 2.337.0. */
class DomainApiClientCallTimeoutTest {

    private class Rec {
        var metodo: HttpMethod? = null
        var timeout: Long? = null
        var socket: Long? = null
        var temCapacidade = false
        var chamadas = 0
    }

    private fun cliente(rec: Rec, comPlugin: Boolean = true, primeiro401: Boolean = false): DomainApiClient {
        val engine = MockEngine { request ->
            rec.chamadas++
            val cfg = request.getCapabilityOrNull(HttpTimeoutCapability)
            rec.metodo = request.method
            rec.temCapacidade = cfg != null
            rec.timeout = cfg?.requestTimeoutMillis
            rec.socket = cfg?.socketTimeoutMillis
            val status = if (primeiro401 && rec.chamadas == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK
            respond("""{"ok":true}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            if (comPlugin) install(HttpTimeout) { requestTimeoutMillis = 30_000; socketTimeoutMillis = 30_000 }
        }
        return DomainApiClient(http, DomainTokenProvider { "t" }, "https://api.example.com")
    }

    @Test
    fun `postJson leva o teto proprio da chamada`() = runTest {
        val rec = Rec()
        val r = cliente(rec).postJson("/v1/diagnosticos/1/recalcular", "{}", timeoutMillis = 90_000)
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(HttpMethod.Post, rec.metodo)
        assertEquals(90_000L, rec.timeout)
        assertEquals(90_000L, rec.socket)
    }

    @Test
    fun `sem timeoutMillis a chamada fica com os tetos do cliente`() = runTest {
        val rec = Rec()
        val r = cliente(rec).postJson("/v1/x", "{}")
        assertIs<DomainResult.Success<String>>(r)
        // Sem `timeout { }` na chamada, a capacidade do request não é sobrescrita pelo DomainApiClient:
        // vale o default do plugin (30 s), nunca um teto próprio.
        assertTrue(rec.timeout == null || rec.timeout == 30_000L)
    }

    @Test
    fun `timeoutMillis com headers posicionais continua compilando e valendo`() = runTest {
        val rec = Rec()
        val r = cliente(rec).postJson("/v1/x", "{}", mapOf("X-Idempotency-Key" to "k1"), 45_000)
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(45_000L, rec.timeout)
    }

    @Test
    fun `todos os verbos JSON aceitam o teto`() = runTest {
        val casos: List<Pair<HttpMethod, suspend (DomainApiClient) -> DomainResult<*>>> = listOf(
            HttpMethod.Get to { c -> c.getJson("/v1/x", timeoutMillis = 61_000) },
            HttpMethod.Get to { c -> c.getJsonWithEtag("/v1/x", etag = null, timeoutMillis = 61_000) },
            HttpMethod.Put to { c -> c.putJson("/v1/x", "{}", timeoutMillis = 61_000) },
            HttpMethod.Patch to { c -> c.patchJson("/v1/x", "{}", timeoutMillis = 61_000) },
            HttpMethod.Delete to { c -> c.delete("/v1/x", timeoutMillis = 61_000) },
            HttpMethod.Delete to { c -> c.deleteJson("/v1/x", timeoutMillis = 61_000) },
            HttpMethod.Delete to { c -> c.deleteJson("/v1/x", "{}", timeoutMillis = 61_000) },
            HttpMethod.Post to { c -> c.postJsonForBytes("/v1/x", "{}", timeoutMillis = 61_000) },
        )
        casos.forEach { (metodo, chamada) ->
            val rec = Rec()
            val r = chamada(cliente(rec))
            assertIs<DomainResult.Success<*>>(r, "verbo $metodo")
            assertEquals(metodo, rec.metodo)
            assertEquals(61_000L, rec.timeout, "verbo $metodo")
            assertEquals(61_000L, rec.socket, "verbo $metodo")
        }
    }

    @Test
    fun `repeticao depois do 401 mantem o teto`() = runTest {
        val rec = Rec()
        val r = cliente(rec, primeiro401 = true).postJson("/v1/x", "{}", timeoutMillis = 90_000)
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(2, rec.chamadas)
        assertEquals(90_000L, rec.timeout)
    }

    @Test
    fun `sem o plugin HttpTimeout o teto e ignorado e a chamada segue`() = runTest {
        val rec = Rec()
        val r = cliente(rec, comPlugin = false).postJson("/v1/x", "{}", timeoutMillis = 90_000)
        assertIs<DomainResult.Success<String>>(r)
        assertTrue(!rec.temCapacidade)
        assertNull(rec.timeout)
    }

    @Test
    fun `teto zero ou negativo e ignorado`() = runTest {
        val rec = Rec()
        val r = cliente(rec).postJson("/v1/x", "{}", timeoutMillis = 0)
        assertIs<DomainResult.Success<String>>(r)
        assertTrue(rec.timeout == null || rec.timeout == 30_000L)
    }
}
