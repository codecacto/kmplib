package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainApiClientTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private data class Cap(
        val urls: MutableList<String> = mutableListOf(),
        val auths: MutableList<String?> = mutableListOf(),
    )

    private fun client(
        cap: Cap = Cap(),
        token: String? = "tok-1",
        responder: (attempt: Int) -> Pair<HttpStatusCode, String>,
    ): Pair<DomainApiClient, Cap> {
        var attempt = 0
        val engine = MockEngine { request ->
            attempt++
            cap.urls += request.url.toString()
            cap.auths += request.headers["Authorization"]
            val (status, body) = responder(attempt)
            respond(content = body, status = status, headers = jsonHeader)
        }
        val provider = DomainTokenProvider { forceRefresh -> if (forceRefresh) "fresh" else token }
        val api = DomainApiClient(HttpClient(engine), provider, "https://api.example.com")
        return api to cap
    }

    @Test
    fun `getJson envia Bearer e devolve corpo`() = runTest {
        val (api, cap) = client { HttpStatusCode.OK to """{"ok":true}""" }
        val r = api.getJson("/v1/empresas")
        assertTrue(r is DomainResult.Success)
        assertEquals("""{"ok":true}""", (r as DomainResult.Success).data)
        assertEquals("https://api.example.com/v1/empresas", cap.urls.first())
        assertEquals("Bearer tok-1", cap.auths.first())
    }

    @Test
    fun `401 forca refresh e faz 1 retry com token novo`() = runTest {
        val (api, cap) = client {
            attempt -> if (attempt == 1) HttpStatusCode.Unauthorized to "" else HttpStatusCode.OK to """{"ok":1}"""
        }
        val r = api.getJson("/v1/x")
        assertTrue(r is DomainResult.Success)
        assertEquals(2, cap.auths.size)
        assertEquals("Bearer tok-1", cap.auths[0])
        assertEquals("Bearer fresh", cap.auths[1])
    }

    @Test
    fun `402 com envelope de quota vira DomainResult Quota`() = runTest {
        val body = """
            {"ok":false,"error":{"code":"QUOTA_EXCEEDED","message":"x",
             "details":{"feature":"lancamentos_mes","limite":"5","contagem":"5","upgradeUrl":"https://u"}}}
        """.trimIndent()
        val (api, _) = client { HttpStatusCode.PaymentRequired to body }
        val r = api.postJson("/v1/lancamentos", "{}")
        assertTrue(r is DomainResult.Quota)
        assertEquals("lancamentos_mes", (r as DomainResult.Quota).quota.feature)
        assertEquals(5, r.quota.limite)
    }

    @Test
    fun `429 vira Error de rate-limit e nao Quota`() = runTest {
        val (api, _) = client { HttpStatusCode.TooManyRequests to "" }
        val r = api.getJson("/v1/x")
        assertTrue(r is DomainResult.Error)
        assertEquals(429, (r as DomainResult.Error).code)
    }

    @Test
    fun `sem token nao envia header Authorization`() = runTest {
        val (api, cap) = client(token = null) { HttpStatusCode.OK to "{}" }
        api.getJson("/v1/x")
        assertNull(cap.auths.first())
    }

    @Test
    fun `path de outro host nao recebe token e vira offline`() = runTest {
        val (api, cap) = client { HttpStatusCode.OK to "{}" }
        val r = api.getJson("https://evil.example.org/steal")
        assertTrue(r is DomainResult.Error)
        assertTrue((r as DomainResult.Error).isOffline)
        assertTrue(cap.urls.isEmpty()) // requisição nem sai
    }

    @Test
    fun `delete devolve Unit em 2xx`() = runTest {
        val (api, _) = client { HttpStatusCode.NoContent to "" }
        val r = api.delete("/v1/empresas/1")
        assertTrue(r is DomainResult.Success)
    }

    @Test
    fun `postMultipart de parte unica envia multipart form-data com o campo`() = runTest {
        var contentType: String? = null
        var body = ""
        val engine = MockEngine { request ->
            contentType = request.body.contentType?.toString()
            body = readBody(request.body as OutgoingContent.WriteChannelContent)
            respond("""{"id":"a1"}""", HttpStatusCode.Created, jsonHeader)
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")

        val r = api.postMultipart("/v1/anexos", byteArrayOf(9, 8, 7), "doc.pdf", "application/pdf")

        assertTrue(r is DomainResult.Success)
        assertTrue(contentType?.startsWith("multipart/form-data") == true)
        assertTrue(body.contains("name=file"))
        assertTrue(body.contains("doc.pdf"))
    }

    @Test
    fun `postMultipartParts envia varias partes nomeadas num unico request`() = runTest {
        var contentType: String? = null
        var body = ""
        val engine = MockEngine { request ->
            contentType = request.body.contentType?.toString()
            body = readBody(request.body as OutgoingContent.WriteChannelContent)
            respond("""{"id":"p1"}""", HttpStatusCode.Created, jsonHeader)
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")

        val r = api.postMultipartParts(
            "/v1/inspections/9/photos",
            listOf(
                MultipartPart("full", "full.jpg", byteArrayOf(1, 2, 3), "image/jpeg"),
                MultipartPart("thumb", "thumb.jpg", byteArrayOf(4, 5), "image/jpeg"),
            ),
        )

        assertTrue(r is DomainResult.Success)
        assertTrue(contentType?.startsWith("multipart/form-data") == true)
        // as duas partes nomeadas viajam no MESMO request multipart
        assertTrue(body.contains("name=full"), "faltou a parte 'full' no corpo")
        assertTrue(body.contains("name=thumb"), "faltou a parte 'thumb' no corpo")
        assertTrue(body.contains("full.jpg"))
        assertTrue(body.contains("thumb.jpg"))
        assertTrue(body.contains("image/jpeg"))
    }

    /** Drena o corpo de um [OutgoingContent.WriteChannelContent] (multipart) para texto. */
    private suspend fun readBody(content: OutgoingContent.WriteChannelContent): String = coroutineScope {
        val channel = ByteChannel(autoFlush = true)
        launch {
            content.writeTo(channel)
            channel.flushAndClose()
        }
        channel.readRemaining().readString()
    }
}
