package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerErrorDetailsTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private val pdf503 =
        """{"message":"PDF indisponível","code":"PDF_RENDER_UNAVAILABLE","details":{"documentIds":["d-1","d-2"]}}"""

    @Serializable
    private data class Ref(val id: String, val kind: String)

    private fun domainClient(status: HttpStatusCode, body: String): DomainApiClient {
        val engine = MockEngine { respond(content = body, status = status, headers = jsonHeader) }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
    }

    // ====== parseServerErrorEnvelope ======

    @Test
    fun `envelope preserva lista no detailsJson e mantem o mapa primitivo`() {
        val env = parseServerErrorEnvelope(
            """{"code":"X","details":{"campo":"frase","documentIds":["a","b"],"meta":{"n":1}}}""",
        )!!
        assertEquals(mapOf("campo" to "frase"), env.details, "o mapa continua só com primitivos")
        assertEquals(listOf("a", "b"), env.detailList("documentIds"))
        assertEquals(JsonPrimitive(1), (env.detail("meta") as kotlinx.serialization.json.JsonObject)["n"])
        assertEquals(3, env.detailsJson.size)
    }

    @Test
    fun `envelope aninhado do 402 tambem leva o detailsJson`() {
        val env = parseServerErrorEnvelope("""{"ok":false,"error":{"code":"C","details":{"ids":[1,2]}}}""")!!
        assertEquals(listOf("1", "2"), env.detailList("ids"))
    }

    @Test
    fun `envelope sem details ou com details nao objeto fica vazio`() {
        assertTrue(parseServerErrorEnvelope("""{"code":"X"}""")!!.detailsJson.isEmpty())
        val lista = parseServerErrorEnvelope("""{"code":"X","details":["a"]}""")!!
        assertTrue(lista.detailsJson.isEmpty())
        assertTrue(lista.details.isEmpty())
        assertNull(lista.detailList("a"))
    }

    // ====== ServerErrorDetails (regras de leitura) ======

    @Test
    fun `detailList - regras de forma`() {
        val d = parseServerErrorEnvelope(
            """{"details":{
                "vazia":[],
                "mista":["a",2,true,null,"  ","b "],
                "objetos":[{"id":"x"}],
                "sozinho":"id-1",
                "virgula":"a,b",
                "branco":"  ",
                "nulo":null,
                "obj":{"k":"v"}
            }}""",
        )!!
        assertEquals(emptyList(), d.detailList("vazia"))
        assertEquals(listOf("a", "2", "true", "b"), d.detailList("mista"), "null e branco saem, valor é aparado")
        assertNull(d.detailList("objetos"), "lista de objetos não é lista de textos")
        assertEquals(listOf("id-1"), d.detailList("sozinho"))
        assertEquals(listOf("a,b"), d.detailList("virgula"), "vírgula não é separador")
        assertNull(d.detailList("branco"))
        assertNull(d.detailList("nulo"))
        assertNull(d.detailList("obj"))
        assertNull(d.detailList("ausente"))
        assertNull(d.detail("nulo"), "null do JSON = ausente")
        assertNull(d.detail("ausente"))
    }

    @Test
    fun `obj e decode`() {
        val d = parseServerErrorEnvelope(
            """{"details":{"ref":{"id":"r1","kind":"RX","extra":true},"refs":[{"id":"a","kind":"K"}],"txt":"x"}}""",
        )!!.detailsJson
        assertEquals("r1", ServerErrorDetails.obj(d, "ref")!!["id"]!!.jsonPrimitive.content)
        assertNull(ServerErrorDetails.obj(d, "txt"))
        assertEquals(Ref("r1", "RX"), ServerErrorDetails.decode<Ref>(d, "ref"), "campo a mais é ignorado")
        assertEquals(listOf(Ref("a", "K")), ServerErrorDetails.decode<List<Ref>>(d, "refs"))
        assertNull(ServerErrorDetails.decode<Ref>(d, "txt"), "forma errada = null, nunca exceção")
        assertNull(ServerErrorDetails.decode<Ref>(d, "ausente"))
    }

    // ====== DomainApiClient ======

    @Test
    fun `DomainResult Error do 503 traz documentIds`() = runTest {
        val e = domainClient(HttpStatusCode.ServiceUnavailable, pdf503).getJson("/v1/x") as DomainResult.Error
        assertEquals(503, e.code)
        assertEquals("PDF_RENDER_UNAVAILABLE", e.serverCode)
        assertEquals(listOf("d-1", "d-2"), e.detailList("documentIds"))
        assertTrue(e.details.isEmpty(), "lista não entra no mapa de erros de campo")
        assertTrue(!e.hasFieldErrors)
        assertIs<JsonArray>(e.detail("documentIds"))
        assertEquals(listOf("d-1", "d-2"), e.decodeDetail<List<String>>("documentIds"))
        assertNull(e.detailObject("documentIds"))
    }

    @Test
    fun `DomainResult Error do 422 mistura campo e lista`() = runTest {
        val body = """{"code":"ALERTS_NOT_ACKNOWLEDGED","message":"m","details":{"alertIds":["a1"],"nota":"frase","ctx":{"k":"v"}}}"""
        val e = domainClient(HttpStatusCode.UnprocessableEntity, body).postJson("/v1/x", "{}") as DomainResult.Error
        assertEquals(listOf("a1"), e.detailList("alertIds"))
        assertEquals("frase", e.fieldError("nota"))
        assertEquals("v", e.detailObject("ctx")!!["k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DomainResult Error sem corpo JSON tem detailsJson vazio`() = runTest {
        val e = domainClient(HttpStatusCode.BadGateway, "<html>502</html>").getJson("/v1/x") as DomainResult.Error
        assertTrue(e.detailsJson.isEmpty())
        assertNull(e.detailList("documentIds"))
        assertTrue(DomainResult.Error(500, "m").detailsJson.isEmpty(), "default do construtor")
    }

    // ====== handleApiCall / ApiResult ======

    @Test
    fun `ApiResult Error traz o detailsJson`() = runTest {
        val client = HttpClient(MockEngine {
            respond(content = pdf503, status = HttpStatusCode.ServiceUnavailable, headers = jsonHeader)
        }) { expectSuccess = true }
        val r = handleApiCall { client.get("/v1/x").bodyAsText() }
        assertIs<ApiResult.Error>(r)
        assertEquals(listOf("d-1", "d-2"), r.detailList("documentIds"))
        assertEquals(listOf("d-1", "d-2"), r.decodeDetail<List<String>>("documentIds"))
        assertIs<JsonArray>(r.detail("documentIds"))
        assertNull(r.detailObject("documentIds"))
        assertTrue(r.details.isEmpty())
        assertTrue(ApiResult.Error(message = "m").detailsJson.isEmpty(), "default do construtor")
    }
}
