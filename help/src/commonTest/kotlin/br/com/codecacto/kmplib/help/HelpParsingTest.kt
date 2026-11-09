package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpParsingTest {

    private val catalog = """
        { "ativo": true,
          "fluxos": [
            { "id":"f1","slug":"cancelar","titulo":"Cancelar agendamento","resumo":"Como cancelar",
              "passos":["Abra a agenda"," ","Toque em cancelar"],"texto":"Pronto.",
              "video":{"id":"v1","tipo":"fluxo","titulo":"Cancelar","formato":"horizontal","url":"https://x.y/a.mp4","posterUrl":"http://x.y/p.jpg","duracaoSeg":30},
              "campoNovo": 1 },
            { "id":"f2","slug":"sem-video","titulo":"Reagendar","passos":[],"texto":"Texto","video":null },
            { "id":"f3","slug":"video-ruim","titulo":"Vídeo ruim","video":{"id":"x","url":"http://inseguro/a.mp4"} },
            { "slug":"sem-id","titulo":"Descartado" }
          ],
          "videos": [
            { "id":"v1","fluxoId":"f1","tipo":"fluxo","titulo":"Cancelar","descricao":"Rápido","formato":"vertical","url":"https://x.y/a.mp4","posterUrl":null,"duracaoSeg":30 },
            { "id":"v2","titulo":"Sem url" }
          ] }
    """.trimIndent()

    @Test
    fun `le o contrato completo e descarta so o item malformado`() {
        val parsed = assertNotNull(parseHelpCatalog(catalog))
        assertTrue(parsed.active)
        assertEquals(listOf("f1", "f2", "f3"), parsed.flows.map { it.id })
        val flow = parsed.flows.first()
        assertEquals(listOf("Abra a agenda", "Toque em cancelar"), flow.steps)
        val video = assertNotNull(flow.video)
        assertEquals("f1", video.flowId)
        assertEquals(HelpVideoFormat.HORIZONTAL, video.format)
        assertNull(video.posterUrl, "capa fora de https não entra")
        assertEquals(30, video.durationSeconds)
        assertNull(parsed.flows[1].video)
        assertNull(parsed.flows[2].video, "vídeo fora de https não entra")
        assertEquals(listOf("v1"), parsed.videos.map { it.id })
        assertEquals(HelpVideoFormat.VERTICAL, parsed.videos.first().format)
    }

    @Test
    fun `projeto desligado e distinto de resposta invalida`() {
        val off = assertNotNull(parseHelpCatalog("""{"ativo":false,"fluxos":[{"id":"a","titulo":"b"}],"videos":[]}"""))
        assertFalse(off.active)
        assertTrue(off.flows.isEmpty())
        assertNull(parseHelpCatalog("""{"fluxos":[]}"""))
        assertNull(parseHelpCatalog("""[1,2]"""))
        assertNull(parseHelpCatalog("<html>"))
    }

    @Test
    fun `resposta da pergunta nos tres resultados`() {
        val fluxo = """{"id":"f","slug":"s","titulo":"T","passos":["p"],"texto":"t","video":null}"""
        assertEquals("f", assertIs<HelpAnswer.Found>(parseHelpAnswer("""{"resultado":"texto","fluxo":$fluxo}""")).flow.id)
        assertIs<HelpAnswer.Found>(parseHelpAnswer("""{"resultado":"video","fluxo":$fluxo}"""))
        assertEquals(HelpAnswer.NotFound, parseHelpAnswer("""{"resultado":"sem_resposta","fluxo":null}"""))
    }

    @Test
    fun `resposta sem fluxo vira sem resposta e valor novo do servidor nao apaga o fluxo`() {
        assertEquals(HelpAnswer.NotFound, parseHelpAnswer("""{"resultado":"texto","fluxo":null}"""))
        val fluxo = """{"id":"f","slug":"s","titulo":"T"}"""
        assertIs<HelpAnswer.Found>(parseHelpAnswer("""{"resultado":"novo","fluxo":$fluxo}"""))
        assertNull(parseHelpAnswer("""{"fluxo":$fluxo}"""))
        assertNull(parseHelpAnswer("nao e json"))
    }

    @Test
    fun `toString nao expoe pergunta nem assinatura da url`() {
        val request = HelpAskRequest("p", null, "app", "pt-BR", "meu cpf é 123")
        assertFalse(request.toString().contains("123"))
        val video = assertNotNull(parseHelpCatalog(catalog)).videos.first().copy(url = "https://x.y/a.mp4?token=segredo")
        assertFalse(video.toString().contains("segredo"))
        assertFalse(HelpState(question = "meu cpf é 123").toString().contains("123"))
    }

    private fun config(engine: MockEngine, perfil: String? = "cliente") = HelpConfig(
        projectSlug = "suzy",
        httpClient = HttpClient(engine),
        perfil = perfil,
        appsApiBaseUrl = "https://api.exemplo/",
        language = "pt-BR",
    )

    private val jsonHeader = headersOf("Content-Type", "application/json")

    @Test
    fun `GET manda project perfil surface app e lang`() = runTest {
        var url: io.ktor.http.Url? = null
        val engine = MockEngine { request ->
            url = request.url
            respond("""{"ativo":true,"fluxos":[],"videos":[]}""", HttpStatusCode.OK, jsonHeader)
        }
        assertIs<ApiResult.Success<HelpCatalog>>(RestHelpSource(config(engine)).loadCatalog())
        val called = assertNotNull(url)
        assertEquals("/public/ajuda", called.encodedPath)
        assertEquals("suzy", called.parameters["project"])
        assertEquals("cliente", called.parameters["perfil"])
        assertEquals("app", called.parameters["surface"])
        assertEquals("pt-BR", called.parameters["lang"])
    }

    @Test
    fun `GET sem perfil omite o parametro`() = runTest {
        var url: io.ktor.http.Url? = null
        val engine = MockEngine { request ->
            url = request.url
            respond("""{"ativo":false,"fluxos":[],"videos":[]}""", HttpStatusCode.OK, jsonHeader)
        }
        RestHelpSource(config(engine, perfil = null)).loadCatalog()
        assertFalse(assertNotNull(url).parameters.contains("perfil"))
    }

    @Test
    fun `POST manda o corpo do contrato com a pergunta aparada`() = runTest {
        var body = ""
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            method = request.method
            body = (request.body as TextContent).text
            respond("""{"resultado":"sem_resposta","fluxo":null}""", HttpStatusCode.OK, jsonHeader)
        }
        val result = RestHelpSource(config(engine)).ask("  como cancelo?  ")
        assertEquals(HelpAnswer.NotFound, assertIs<ApiResult.Success<HelpAnswer>>(result).data)
        assertEquals(HttpMethod.Post, method)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("suzy", json["project"]?.jsonPrimitive?.content)
        assertEquals("cliente", json["perfil"]?.jsonPrimitive?.content)
        assertEquals("app", json["surface"]?.jsonPrimitive?.content)
        assertEquals("pt-BR", json["lang"]?.jsonPrimitive?.content)
        assertEquals("como cancelo?", json["pergunta"]?.jsonPrimitive?.content)
    }

    @Test
    fun `erro HTTP e corpo fora do contrato viram erro, sem lancar`() = runTest {
        val down = MockEngine { respondError(HttpStatusCode.TooManyRequests) }
        assertIs<ApiResult.Error>(RestHelpSource(config(down)).ask("como cancelo?"))
        val html = MockEngine { respond("<html>", HttpStatusCode.OK) }
        assertIs<ApiResult.Error>(RestHelpSource(config(html)).loadCatalog())
    }
}
