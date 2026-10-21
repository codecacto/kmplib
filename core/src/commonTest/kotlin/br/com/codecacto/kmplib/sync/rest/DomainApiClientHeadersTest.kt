package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Headers
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

/** Cabeçalhos extras por requisição em todos os verbos do [DomainApiClient] — 2.313.0. */
class DomainApiClientHeadersTest {

    private class Rec {
        val metodos = mutableListOf<HttpMethod>()
        val cabecalhos = mutableListOf<Headers>()
        val tiposDeCorpo = mutableListOf<String?>()
    }

    private fun cliente(
        rec: Rec,
        status: (tentativa: Int) -> HttpStatusCode = { HttpStatusCode.OK },
    ): DomainApiClient {
        val engine = MockEngine { request ->
            rec.metodos += request.method
            rec.cabecalhos += request.headers
            rec.tiposDeCorpo += request.body.contentType?.withoutParameters()?.toString()
            respond("{}", status(rec.metodos.size), headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) { install(HttpTimeout) { requestTimeoutMillis = 30_000 } }
        return DomainApiClient(http, DomainTokenProvider { force -> if (force) "t2" else "t1" }, "https://api.example.com")
    }

    private val extra = mapOf("X-Monotonico-Ms" to "123456")
    private val parte = MultipartPart("arquivo", "a.pdf", byteArrayOf(1), "application/pdf")

    @Test
    fun `todos os verbos levam o cabecalho extra junto do Bearer`() = runTest {
        val rec = Rec()
        val api = cliente(rec)
        val chamadas: List<suspend () -> DomainResult<*>> = listOf(
            { api.getJson("/v1/a", extra) },
            { api.getJsonWithEtag("/v1/a", null, extra) },
            { api.postJson("/v1/a", "{}", extra) },
            { api.putJson("/v1/a", "{}", extra) },
            { api.patchJson("/v1/a", "{}", extra) },
            { api.delete("/v1/a", extra) },
            { api.deleteJson("/v1/a", extra) },
            { api.deleteJson("/v1/a", "{}", extra) },
            { api.getBytes("/v1/a", extra) },
            { api.getBytes("/v1/a", 60_000L, extra) },
            { api.postJsonForBytes("/v1/a", "{}", extra) },
            { api.postMultipartParts("/v1/a", listOf(parte), emptyMap(), extra) },
            { api.postMultipartParts("/v1/a", listOf(parte), emptyMap(), 60_000L, extra) },
            { api.putMultipartParts("/v1/a", listOf(parte), emptyMap(), extra) },
        )
        chamadas.forEach { assertIs<DomainResult.Success<*>>(it()) }

        assertEquals(chamadas.size, rec.cabecalhos.size)
        rec.cabecalhos.forEachIndexed { i, h ->
            assertEquals("123456", h["X-Monotonico-Ms"], "chamada $i (${rec.metodos[i]})")
            assertEquals("Bearer t1", h[HttpHeaders.Authorization], "chamada $i")
        }
        assertEquals(
            listOf(
                HttpMethod.Get, HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch,
                HttpMethod.Delete, HttpMethod.Delete, HttpMethod.Delete, HttpMethod.Get, HttpMethod.Get,
                HttpMethod.Post, HttpMethod.Post, HttpMethod.Post, HttpMethod.Put,
            ),
            rec.metodos,
        )
    }

    @Test
    fun `chamada por posicao sem headers continua igual`() = runTest {
        val rec = Rec()
        val api = cliente(rec)
        assertIs<DomainResult.Success<String>>(api.postJson("/v1/a", "{}"))
        assertIs<DomainResult.Success<String>>(api.deleteJson("/v1/a", "{}"))
        assertIs<DomainResult.Success<Unit>>(api.delete("/v1/a"))
        assertEquals("application/json", rec.tiposDeCorpo[0])
        assertNull(rec.cabecalhos[0]["X-Monotonico-Ms"])
    }

    @Test
    fun `Authorization do app e ignorado e o Bearer da lib vale inclusive no retry do 401`() = runTest {
        val rec = Rec()
        val api = cliente(rec) { n -> if (n == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK }
        val r = api.postJson(
            "/v1/sessoes",
            "{}",
            mapOf("authorization" to "Bearer roubado", "X-Monotonico-Ms" to "42"),
        )
        assertIs<DomainResult.Success<String>>(r)
        assertEquals(2, rec.cabecalhos.size)
        assertEquals(listOf("Bearer t1"), rec.cabecalhos[0].getAll(HttpHeaders.Authorization))
        assertEquals(listOf("Bearer t2"), rec.cabecalhos[1].getAll(HttpHeaders.Authorization))
        assertEquals("42", rec.cabecalhos[1]["X-Monotonico-Ms"], "o retry repete os extras")
    }

    @Test
    fun `Content-Type e If-None-Match do app nao trocam o que o verbo decide`() = runTest {
        val rec = Rec()
        val api = cliente(rec)
        api.postJson("/v1/a", "{}", mapOf("Content-Type" to "text/plain"))
        api.getJsonWithEtag("/v1/a", "\"v1\"", mapOf("If-None-Match" to "\"outro\""))
        assertEquals("application/json", rec.tiposDeCorpo[0])
        assertNull(rec.cabecalhos[0][HttpHeaders.ContentType], "o text/plain do app não entrou")
        assertEquals(listOf("\"v1\""), rec.cabecalhos[1].getAll(HttpHeaders.IfNoneMatch))
    }

    @Test
    fun `cabecalho malformado e ignorado e a chamada nao vira falha de rede`() = runTest {
        val rec = Rec()
        val r = cliente(rec).postJson(
            "/v1/a",
            "{}",
            mapOf("X Ruim" to "1", "X-Injeta" to "a\r\nSet-Cookie: x", "X-Bom" to "ok"),
        )
        assertIs<DomainResult.Success<String>>(r)
        val h = rec.cabecalhos.single()
        assertEquals("ok", h["X-Bom"])
        assertNull(h["X-Injeta"])
        assertNull(h["Set-Cookie"])
    }

    @Test
    fun `triagem pura separa aceitos e ignorados sem expor valor`() {
        val t = screenExtraHeaders(
            linkedMapOf(
                "X-Monotonico-Ms" to " 99 ",
                "AUTHORIZATION" to "segredo",
                "Host" to "outro",
                "Transfer-Encoding" to "chunked",
                "" to "x",
                "X-Valor" to "a\nb",
                "Accept-Language" to "en",
            ),
        )
        assertEquals(listOf("X-Monotonico-Ms" to "99", "Accept-Language" to "en"), t.accepted)
        assertEquals(listOf("AUTHORIZATION", "Host", "Transfer-Encoding", "(vazio)", "X-Valor"), t.ignored)
        assertTrue(t.ignored.none { "segredo" in it })
    }

    @Test
    fun `lista de reservados cobre credencial, corpo e conexao`() {
        listOf("authorization", "content-type", "content-length", "if-none-match", "host", "transfer-encoding")
            .forEach { assertTrue(it in DomainApiClient.RESERVED_HEADERS, it) }
        assertTrue("accept-language" !in DomainApiClient.RESERVED_HEADERS)
    }
}
