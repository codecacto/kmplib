package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `GET` condicional por ETag ([DomainApiClient.getJsonWithEtag]) — 2.272.0. */
class DomainApiClientEtagTest {

    private class Rec {
        val ifNoneMatch = mutableListOf<String?>()
        val auths = mutableListOf<String?>()
        val extras = mutableListOf<String?>()
    }

    private data class Resp(val status: HttpStatusCode, val body: String = "", val etag: String? = null)

    private fun cliente(rec: Rec, responder: (attempt: Int) -> Resp): DomainApiClient {
        var attempt = 0
        val engine = MockEngine { request ->
            attempt++
            rec.ifNoneMatch += request.headers.getAll(HttpHeaders.IfNoneMatch)?.joinToString("|")
            rec.auths += request.headers[HttpHeaders.Authorization]
            rec.extras += request.headers["X-Device-Id"]
            val r = responder(attempt)
            val headers = if (r.etag == null) {
                headersOf(HttpHeaders.ContentType, "application/json")
            } else {
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(r.etag))
            }
            respond(content = r.body, status = r.status, headers = headers)
        }
        val provider = DomainTokenProvider { force -> if (force) "t2" else "t1" }
        return DomainApiClient(HttpClient(engine), provider, "https://api.example.com")
    }

    @Test
    fun `primeira carga sem etag devolve corpo e o ETag da resposta`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { Resp(HttpStatusCode.OK, """{"v":1}""", "\"v1\"") }
            .getJsonWithEtag("/v1/aluno/program", etag = null)

        assertIs<DomainResult.Success<EtagResult<String>>>(r)
        assertEquals(EtagResult.Modified("""{"v":1}""", "\"v1\""), r.data)
        assertEquals(listOf<String?>(null), rec.ifNoneMatch, "sem condição na primeira carga")
    }

    @Test
    fun `envia If-None-Match verbatim e 304 vira NotModified`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { Resp(HttpStatusCode.NotModified, etag = "W/\"v1\"") }
            .getJsonWithEtag("/v1/aluno/program", etag = "W/\"v1\"")

        assertIs<DomainResult.Success<EtagResult<String>>>(r)
        assertEquals(EtagResult.NotModified("W/\"v1\""), r.data)
        assertEquals(listOf<String?>("W/\"v1\""), rec.ifNoneMatch)
    }

    @Test
    fun `304 sem ETag na resposta repete o enviado`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.NotModified) }.getJsonWithEtag("/v1/p", etag = "\"abc\"")
        assertEquals(EtagResult.NotModified("\"abc\""), (r as DomainResult.Success).data)
    }

    @Test
    fun `200 com ETag novo e Modified`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.OK, """{"v":2}""", "\"v2\"") }
            .getJsonWithEtag("/v1/p", etag = "\"v1\"")
        val dado = (r as DomainResult.Success).data
        assertIs<EtagResult.Modified<String>>(dado)
        assertEquals("""{"v":2}""", dado.body)
        assertEquals("\"v2\"", dado.etag)
    }

    @Test
    fun `200 com o mesmo ETag em comparacao fraca e NotModified`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.OK, """{"v":1}""", "\"v1\"") }
            .getJsonWithEtag("/v1/p", etag = "W/\"v1\"")
        assertEquals(EtagResult.NotModified("\"v1\""), (r as DomainResult.Success).data)
    }

    @Test
    fun `200 sem ETag e Modified com etag nulo`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.OK, "{}") }.getJsonWithEtag("/v1/p", etag = "\"v1\"")
        val dado = (r as DomainResult.Success).data
        assertIs<EtagResult.Modified<String>>(dado)
        assertNull(dado.etag)
    }

    @Test
    fun `304 sem condicao enviada e erro`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.NotModified) }.getJsonWithEtag("/v1/p", etag = "  ")
        assertIs<DomainResult.Error>(r)
        assertEquals(304, r.code)
    }

    @Test
    fun `getJson comum continua tratando 304 como erro`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.NotModified) }.getJson("/v1/p")
        assertIs<DomainResult.Error>(r)
    }

    @Test
    fun `401 renova o token e repete com o mesmo If-None-Match`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { attempt ->
            if (attempt == 1) Resp(HttpStatusCode.Unauthorized) else Resp(HttpStatusCode.NotModified, etag = "\"v1\"")
        }.getJsonWithEtag("/v1/p", etag = "\"v1\"")

        assertEquals(EtagResult.NotModified("\"v1\""), (r as DomainResult.Success).data)
        assertEquals(listOf<String?>("\"v1\"", "\"v1\""), rec.ifNoneMatch)
        assertEquals(listOf<String?>("Bearer t1", "Bearer t2"), rec.auths)
    }

    @Test
    fun `If-None-Match nos headers extras e ignorado e os demais vao`() = runTest {
        val rec = Rec()
        cliente(rec) { Resp(HttpStatusCode.NotModified) }.getJsonWithEtag(
            "/v1/p",
            etag = "\"v1\"",
            headers = mapOf("if-none-match" to "\"outro\"", "X-Device-Id" to "dev-1"),
        )
        assertEquals(listOf<String?>("\"v1\""), rec.ifNoneMatch)
        assertEquals(listOf<String?>("dev-1"), rec.extras)
    }

    @Test
    fun `erro do servidor atravessa como Error`() = runTest {
        val r = cliente(Rec()) { Resp(HttpStatusCode.InternalServerError, """{"message":"x"}""") }
            .getJsonWithEtag("/v1/p", etag = "\"v1\"")
        assertIs<DomainResult.Error>(r)
        assertEquals(500, r.code)
    }

    @Test
    fun `comparacao fraca de etag`() {
        assertTrue(etagWeakMatch("\"abc\"", "W/\"abc\""))
        assertTrue(etagWeakMatch("W/\"abc\"", "W/\"abc\""))
        assertFalse(etagWeakMatch("\"abc\"", "\"ABC\""))
        assertFalse(etagWeakMatch("\"abc\"", "\"abcd\""))
    }

    @Test
    fun `toString de Modified nao imprime o corpo`() {
        val texto = EtagResult.Modified("""{"carga":87.5}""", "\"v1\"").toString()
        assertFalse(texto.contains("carga"))
        assertTrue(texto.contains("\"v1\""))
    }
}
