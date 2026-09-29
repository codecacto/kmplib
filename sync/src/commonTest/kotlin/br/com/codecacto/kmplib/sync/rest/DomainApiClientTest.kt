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
    fun `o code do envelope de erro chega na tela`() = runTest {
        // Dois 409 diferentes na mesma rota é o caso que motivou o campo: o status sozinho não
        // distingue "ainda está sendo preparado" de "já foi enviado", e a tela precisa dizer coisas
        // opostas em cada um.
        val (api, _) = client {
            HttpStatusCode.Conflict to """{"message":"Sendo preparado.","code":"RESULTADO_EM_PREPARACAO"}"""
        }
        val r = api.getJson("/v1/x")
        assertTrue(r is DomainResult.Error)
        assertEquals(409, (r as DomainResult.Error).code)
        assertEquals("RESULTADO_EM_PREPARACAO", r.serverCode)
    }

    @Test
    fun `corpo que nao e JSON nao derruba o tratamento do erro`() = runTest {
        // Um proxy no meio devolve HTML em 502. Tentar ler `code` ali não pode virar um segundo
        // erro dentro do tratamento do primeiro.
        val (api, _) = client { HttpStatusCode.BadGateway to "<html><body>502</body></html>" }
        val r = api.getJson("/v1/x")
        assertTrue(r is DomainResult.Error)
        assertEquals(502, (r as DomainResult.Error).code)
        assertNull(r.serverCode)
    }

    @Test
    fun `JSON de erro sem code devolve serverCode nulo`() = runTest {
        val (api, _) = client { HttpStatusCode.BadRequest to """{"message":"Campo obrigatório."}""" }
        val r = api.getJson("/v1/x")
        assertNull((r as DomainResult.Error).serverCode)
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
    fun `deleteJson com corpo envia o corpo e o Bearer`() = runTest {
        var contentType: String? = null
        var enviado: String? = null
        val engine = MockEngine { request ->
            contentType = request.body.contentType?.toString()
            // `setBody(String)` com Content-Type vira TextContent (um ByteArrayContent), não o
            // WriteChannelContent do multipart — daí os dois ramos.
            enviado = when (val corpo = request.body) {
                is OutgoingContent.ByteArrayContent -> corpo.bytes().decodeToString()
                is OutgoingContent.WriteChannelContent -> readBody(corpo)
                else -> null
            }
            respond(content = "", status = HttpStatusCode.NoContent, headers = jsonHeader)
        }
        val provider = DomainTokenProvider { "tok-1" }
        val api = DomainApiClient(HttpClient(engine), provider, "https://api.example.com")

        val r = api.deleteJson("/v1/dispositivos", """{"token":"abc"}""")

        assertTrue(r is DomainResult.Success)
        // 204 sem corpo é sucesso legítimo aqui — quem desregistra um aparelho não recebe nada de volta.
        assertEquals("", (r as DomainResult.Success).data)
        assertEquals("""{"token":"abc"}""", enviado)
        assertTrue(contentType?.startsWith("application/json") == true, "corpo saiu sem Content-Type JSON")
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

    @Test
    fun `postMultipartParts com formFields envia os campos de texto antes do arquivo`() = runTest {
        var body = ""
        val engine = MockEngine { request ->
            body = readBody(request.body as OutgoingContent.WriteChannelContent)
            respond("""{"id":"p1"}""", HttpStatusCode.Created, jsonHeader)
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")

        val r = api.postMultipartParts(
            "/v1/lesoes/1/fotos",
            listOf(MultipartPart("file", "lesao.jpg", byteArrayOf(1, 2), "image/jpeg")),
            linkedMapOf("regiao" to "antebraco", "" to "ignorado"),
        )

        assertTrue(r is DomainResult.Success)
        assertTrue(body.contains("name=regiao"))
        assertTrue(body.contains("antebraco"))
        assertTrue(!body.contains("ignorado"), "campo sem nome não pode viajar")
        assertTrue(body.indexOf("name=regiao") < body.indexOf("lesao.jpg"))
    }

    @Test
    fun `put e parte unica com formFields tambem levam os campos`() = runTest {
        val corpos = mutableListOf<String>()
        val metodos = mutableListOf<String>()
        val engine = MockEngine { request ->
            metodos += request.method.value
            corpos += readBody(request.body as OutgoingContent.WriteChannelContent)
            respond("{}", HttpStatusCode.OK, jsonHeader)
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")

        api.putMultipartParts("/v1/a", listOf(MultipartPart("file", "a.jpg", byteArrayOf(1), "image/jpeg")), mapOf("k" to "v1"))
        api.postMultipart("/v1/b", byteArrayOf(1), "b.jpg", "image/jpeg", "foto", mapOf("k" to "v2"))

        assertEquals(listOf("PUT", "POST"), metodos)
        assertTrue(corpos[0].contains("v1"))
        assertTrue(corpos[1].contains("v2") && corpos[1].contains("name=foto"))
    }

    @Test
    fun `400 da backlib traz message e details por campo`() = runTest {
        val corpo = """{"message":"Revise os campos destacados","code":"VALIDATION_ERROR",
            "details":{"regiao":"Informe a região","capturadaEm":"A data não pode ser futura"},"traceId":"t"}"""
        val (api, _) = client { HttpStatusCode.BadRequest to corpo }

        val r = api.postJson("/v1/lesoes", "{}")

        assertTrue(r is DomainResult.Error)
        r as DomainResult.Error
        assertEquals(400, r.code)
        assertEquals("VALIDATION_ERROR", r.serverCode)
        assertEquals("Revise os campos destacados", r.serverMessage)
        assertEquals("Informe a região", r.fieldError("regiao"))
        assertEquals("A data não pode ser futura", r.fieldError("capturadaEm"))
        assertNull(r.fieldError("outro"))
        assertTrue(r.hasFieldErrors)
        assertEquals(listOf("regiao", "capturadaEm"), r.fieldErrors.keys.toList(), "ordem do servidor = ordem do foco")
        assertEquals("Revise os campos destacados", r.userMessage)
    }

    @Test
    fun `userMessage usa o texto local em 5xx e 401 e o do servidor em 4xx`() = runTest {
        val (api500, _) = client { HttpStatusCode.InternalServerError to """{"message":"NPE em X","code":"INTERNAL"}""" }
        val e500 = api500.getJson("/v1/x") as DomainResult.Error
        assertEquals("NPE em X", e500.serverMessage)
        assertEquals("Erro do servidor (500).", e500.userMessage, "5xx nunca expõe a frase técnica")

        val (api422, _) = client { HttpStatusCode.UnprocessableEntity to """{"message":"Foto sem metadado removível","code":"IMAGE_METADATA_NOT_REMOVABLE"}""" }
        val e422 = api422.getJson("/v1/x") as DomainResult.Error
        assertEquals("Foto sem metadado removível", e422.userMessage)
        assertTrue(e422.details.isEmpty())
    }

    @Test
    fun `envelope aninhado e corpo nao JSON`() {
        val aninhado = parseServerErrorEnvelope("""{"ok":false,"error":{"code":"C","message":"m","details":{"a":"b","n":{"x":1}}}}""")
        assertEquals("C", aninhado?.code)
        assertEquals(mapOf("a" to "b"), aninhado?.details, "valor não primitivo é ignorado")
        assertNull(parseServerErrorEnvelope("<html>502</html>"))
        assertNull(parseServerErrorEnvelope(null))
        assertNull(parseServerErrorEnvelope("{quebrado"))
    }

    @Test
    fun `postJsonForBytes envia o JSON com Bearer e devolve os bytes crus`() = runTest {
        var metodo: String? = null
        var tipo: String? = null
        var corpo: String? = null
        var auth: String? = null
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0xFF.toByte())
        val engine = MockEngine { request ->
            metodo = request.method.value
            auth = request.headers["Authorization"]
            val c = request.body
            tipo = c.contentType?.toString()
            corpo = when (c) {
                is OutgoingContent.ByteArrayContent -> c.bytes().decodeToString()
                is OutgoingContent.WriteChannelContent -> readBody(c)
                else -> null
            }
            respond(content = pdf, status = HttpStatusCode.OK, headers = headersOf("Content-Type", "application/pdf"))
        }
        val api = DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok-1" }, "https://api.example.com")
        val r = api.postJsonForBytes("/v1/etiquetas/pdf", """{"ids":["a","b"],"formato":"A4_24"}""")
        assertTrue(r is DomainResult.Success)
        assertTrue(pdf.contentEquals((r as DomainResult.Success).data))
        assertEquals("POST", metodo)
        assertEquals("Bearer tok-1", auth)
        assertTrue(tipo.orEmpty().startsWith("application/json"))
        assertEquals("""{"ids":["a","b"],"formato":"A4_24"}""", corpo)
    }

    @Test
    fun `postJsonForBytes traz o code do envelope de erro e o 402 como Quota`() = runTest {
        val erro = """{"ok":false,"error":{"code":"TOO_MANY_LABELS","message":"No máximo 500 etiquetas por folha","details":{"ids":"No máximo 500"}}}"""
        val (api, _) = client { HttpStatusCode.BadRequest to erro }
        val r = api.postJsonForBytes("/v1/etiquetas/pdf", "{}")
        assertTrue(r is DomainResult.Error)
        assertEquals(400, (r as DomainResult.Error).code)
        assertEquals("TOO_MANY_LABELS", r.serverCode)
        assertEquals("No máximo 500", r.fieldError("ids"))

        val quota = """{"ok":false,"error":{"code":"QUOTA_EXCEEDED","message":"x","details":{"feature":"etiquetas","limite":"0","contagem":"0"}}}"""
        val (api2, _) = client { HttpStatusCode.PaymentRequired to quota }
        assertTrue(api2.postJsonForBytes("/v1/etiquetas/pdf", "{}") is DomainResult.Quota)
    }

    @Test
    fun `postJsonForBytes com 401 renova o token e repete uma vez`() = runTest {
        val (api, cap) = client { attempt -> if (attempt == 1) HttpStatusCode.Unauthorized to "" else HttpStatusCode.OK to "%PDF" }
        val r = api.postJsonForBytes("/v1/etiquetas/pdf", "{}")
        assertTrue(r is DomainResult.Success)
        assertEquals<List<String?>>(listOf("Bearer tok-1", "Bearer fresh"), cap.auths)
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
