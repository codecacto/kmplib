package br.com.codecacto.kmplib.announcement

import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class AnnouncementServiceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private val body = """
        [
          {"id":"m1","kind":"maintenance","title":"Manutenção","window":{"start":"2026-10-18T22:00:00-03:00","end":"2026-10-19T02:00:00-03:00"},"frequency":"untilDismissed"},
          {"id":"bad","kind":"news"},
          {"id":"n1","kind":"news","title":"Novidade","action":{"label":"Ver","url":"https://x.y"}}
        ]
    """.trimIndent()

    private class Recorder {
        val urls = mutableListOf<io.ktor.http.Url>()
        val methods = mutableListOf<String>()
    }

    private fun client(
        rec: Recorder = Recorder(),
        baseUrl: String = "https://apps-api.example/",
        timeSource: TestTimeSource = TestTimeSource(),
        respondWith: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData =
            { respond(body, HttpStatusCode.OK, jsonHeader) },
    ): AnnouncementClient {
        val engine = MockEngine { request ->
            rec.urls += request.url
            rec.methods += request.method.value
            respondWith()
        }
        return AnnouncementClient(
            AnnouncementConfig(projectSlug = "meu-app", httpClient = HttpClient(engine), appsApiBaseUrl = baseUrl),
            timeSource,
        )
    }

    @AfterTest
    fun cleanup() = AnnouncementService.resetForTesting()

    // ── sucesso / query ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `sucesso decodifica e descarta o item malformado`() = runTest {
        val list = client().fetch(platform = "android", locale = "pt-BR")
        assertEquals(listOf("m1", "n1"), list.map { it.id })
        assertEquals(AnnouncementKind.MAINTENANCE, list[0].kind)
        assertEquals("https://x.y", list[1].action?.url)
    }

    @Test
    fun `GET em public announcements com project platform e locale`() = runTest {
        val rec = Recorder()
        client(rec).fetch(platform = "ios", locale = "en-US")
        val url = rec.urls.single()
        assertEquals("GET", rec.methods.single())
        assertEquals("apps-api.example", url.host)
        assertEquals("/public/announcements", url.encodedPath)
        assertEquals("meu-app", url.parameters["project"])
        assertEquals("ios", url.parameters["platform"])
        assertEquals("en-US", url.parameters["locale"])
    }

    @Test
    fun `defaults de platform e locale vem do aparelho`() = runTest {
        val rec = Recorder()
        client(rec).fetch()
        val url = rec.urls.single()
        assertTrue(url.parameters["platform"] in setOf("android", "ios"))
        assertFalse(url.parameters["locale"].isNullOrBlank())
    }

    // ── falhas: nunca lança ───────────────────────────────────────────────────────────────────────

    @Test
    fun `erro HTTP devolve lista vazia e fetchOrNull null`() = runTest {
        val c = client(respondWith = { respondError(HttpStatusCode.InternalServerError) })
        assertEquals(emptyList(), c.fetch("android", "pt-BR"))
        assertNull(c.fetchOrNull("android", "pt-BR"))
    }

    @Test
    fun `404 devolve lista vazia`() = runTest {
        val c = client(respondWith = { respondError(HttpStatusCode.NotFound) })
        assertEquals(emptyList(), c.fetch("android", "pt-BR"))
    }

    @Test
    fun `JSON invalido devolve lista vazia e fetchOrNull null`() = runTest {
        val c = client(respondWith = { respond("<html>erro do CDN</html>", HttpStatusCode.OK) })
        assertEquals(emptyList(), c.fetch("android", "pt-BR"))
        assertNull(c.fetchOrNull("android", "pt-BR"))
    }

    @Test
    fun `objeto em vez de array e falha, array vazio nao`() = runTest {
        assertNull(client(respondWith = { respond("{}", HttpStatusCode.OK, jsonHeader) }).fetchOrNull("android", "pt-BR"))
        assertEquals(emptyList(), client(respondWith = { respond("[]", HttpStatusCode.OK, jsonHeader) }).fetchOrNull("android", "pt-BR"))
    }

    @Test
    fun `erro de rede nao lanca`() = runTest {
        val c = client(respondWith = { throw IOException("sem rede") })
        assertEquals(emptyList(), c.fetch("android", "pt-BR"))
    }

    @Test
    fun `servico nao inicializado devolve vazio sem lancar`() = runTest {
        AnnouncementService.resetForTesting()
        assertFalse(AnnouncementService.isInitialized)
        assertEquals(emptyList(), AnnouncementService.fetch("android", "pt-BR"))
    }

    @Test
    fun `servico inicializado delega ao cliente`() = runTest {
        val engine = MockEngine { respond(body, HttpStatusCode.OK, jsonHeader) }
        AnnouncementService.initialize(AnnouncementConfig("meu-app", HttpClient(engine), "https://a.b"))
        assertEquals(listOf("m1", "n1"), AnnouncementService.fetch("android", "pt-BR").map { it.id })
    }

    // ── refresh: intervalo mínimo, falha mantém a anterior ────────────────────────────────────────

    @Test
    fun `refresh respeita o intervalo minimo`() = runTest {
        val rec = Recorder()
        val time = TestTimeSource()
        val c = client(rec, timeSource = time)
        assertTrue(c.refresh(5.minutes, "android", "pt-BR"))
        assertEquals(2, c.latest.value.size)

        time += 4.minutes
        assertFalse(c.refresh(5.minutes, "android", "pt-BR"))
        assertEquals(1, rec.urls.size)

        time += 61.seconds
        assertTrue(c.refresh(5.minutes, "android", "pt-BR"))
        assertEquals(2, rec.urls.size)
    }

    @Test
    fun `refresh com outro idioma busca de novo mesmo dentro do intervalo`() = runTest {
        val rec = Recorder()
        val c = client(rec)
        c.refresh(5.minutes, "android", "pt-BR")
        c.refresh(5.minutes, "android", "en-US")
        assertEquals(2, rec.urls.size)
    }

    @Test
    fun `falha no refresh mantem a lista anterior e nao conta para o intervalo`() = runTest {
        val rec = Recorder()
        var fail = false
        val c = client(rec, respondWith = {
            if (fail) respondError(HttpStatusCode.ServiceUnavailable) else respond(body, HttpStatusCode.OK, jsonHeader)
        })
        assertTrue(c.refresh(platform = "android", locale = "pt-BR"))
        fail = true
        assertFalse(c.refresh(platform = "android", locale = "pt-BR"))
        assertEquals(listOf("m1", "n1"), c.latest.value.map { it.id })
    }

    @Test
    fun `array vazio do servidor apaga os avisos`() = runTest {
        var empty = false
        val c = client(respondWith = { respond(if (empty) "[]" else body, HttpStatusCode.OK, jsonHeader) })
        c.refresh(platform = "android", locale = "pt-BR")
        empty = true
        c.refresh(platform = "android", locale = "pt-BR")
        assertEquals(emptyList(), c.latest.value)
    }

    @Test
    fun `refresh simultaneos fazem uma requisicao`() = runTest {
        val rec = Recorder()
        val c = client(rec)
        List(3) { async { c.refresh(5.minutes, "android", "pt-BR") } }.awaitAll()
        assertEquals(1, rec.urls.size)
    }

    // ── merge ─────────────────────────────────────────────────────────────────────────────────────

    private fun a(id: String, title: String = id) = Announcement(id = id, kind = AnnouncementKind.INFO, title = title)

    @Test
    fun `merge remoto vence no mesmo id e vem primeiro`() {
        val merged = mergeAnnouncements(
            local = listOf(a("x", "local"), a("l1")),
            remote = listOf(a("r1"), a("x", "remoto")),
        )
        assertEquals(listOf("r1", "x", "l1"), merged.map { it.id })
        assertEquals("remoto", merged.first { it.id == "x" }.title)
    }

    @Test
    fun `merge dedupe dentro da mesma lista fica com o primeiro`() {
        val merged = mergeAnnouncements(
            local = listOf(a("l", "1"), a("l", "2")),
            remote = listOf(a("r", "1"), a("r", "2")),
        )
        assertEquals(listOf("r" to "1", "l" to "1"), merged.map { it.id to it.title })
    }

    @Test
    fun `merge de vazios`() {
        assertEquals(emptyList(), mergeAnnouncements(emptyList(), emptyList()))
        assertEquals(listOf("l"), mergeAnnouncements(listOf(a("l")), emptyList()).map { it.id })
        assertEquals(listOf("r"), mergeAnnouncements(emptyList(), listOf(a("r"))).map { it.id })
    }
}
