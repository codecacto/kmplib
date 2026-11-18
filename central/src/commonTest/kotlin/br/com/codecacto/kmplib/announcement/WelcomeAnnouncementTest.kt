@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.announcement

import br.com.codecacto.kmplib.ui.announcement.AnnouncementFrequency
import br.com.codecacto.kmplib.ui.announcement.AnnouncementKind
import br.com.codecacto.kmplib.ui.tour.ProductTourAccountStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class WelcomeAnnouncementTest {

    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val body = """
        [
          {"id":"n1","kind":"news","title":"Novidade"},
          {"id":"w1","kind":"welcome","title":"Boas-vindas ao LocAki!","frequency":"always",
           "action":{"label":"Falar no WhatsApp","url":"https://wa.me/5565998030240"}},
          {"id":"w0","kind":"welcome","title":"Antiga"}
        ]
    """.trimIndent()

    private class FakeStore(
        var seen: Boolean = false,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : ProductTourAccountStore {
        val marked = mutableListOf<String>()
        val reads = mutableListOf<String>()
        override suspend fun isSeen(key: String): Boolean {
            reads += key
            if (failRead) error("rede")
            return seen
        }
        override suspend fun markSeen(key: String) {
            if (failWrite) error("rede")
            marked += key
        }
    }

    private fun client(urls: MutableList<Url>): AnnouncementClient {
        val engine = MockEngine { request ->
            urls += request.url
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        return AnnouncementClient(AnnouncementConfig("locaki", HttpClient(engine), "https://apps-api.example"))
    }

    private fun loader(store: FakeStore?, createdAt: String?, urls: MutableList<Url> = mutableListOf(), enabled: Boolean = true) =
        WelcomeAnnouncementLoader(client(urls), WelcomeAnnouncementConfig(createdAt, store, enabled), clock)

    // ── conta nova / antiga ──────────────────────────────────────────────────────────────────────

    @Test
    fun `conta de ate 7 dias e nova, mais antiga nao`() {
        assertTrue(isNewAccount("2026-11-18T11:00:00Z", now))
        assertTrue(isNewAccount("2026-11-11T12:00:00Z", now))
        assertFalse(isNewAccount("2026-11-11T11:59:59Z", now))
        assertTrue(isNewAccount("2026-11-17T09:00:00-03:00", now))
        assertTrue(isNewAccount("2026-11-11T12:00:00Z", now, days = 7))
        assertFalse(isNewAccount("2026-11-15T12:00:00Z", now, days = 2))
    }

    @Test
    fun `sem data, ilegivel ou no futuro alem de 1 dia nao e nova`() {
        assertFalse(isNewAccount(null as String?, now))
        assertFalse(isNewAccount("", now))
        assertFalse(isNewAccount("ontem", now))
        assertFalse(isNewAccount("2026-11-20T12:00:00Z", now))
        assertTrue(isNewAccount("2026-11-19T11:00:00Z", now))
    }

    // ── loader ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `conta nova que nao viu recebe a boas-vindas mais nova, uma vez`() = runTest {
        val urls = mutableListOf<Url>()
        val store = FakeStore()
        val a = loader(store, "2026-11-17T12:00:00Z", urls).load(platform = "android", locale = "pt-BR")
        assertEquals("w1", a?.id)
        assertEquals(AnnouncementKind.WELCOME, a?.kind)
        assertEquals(AnnouncementFrequency.ONCE, a?.frequency)
        assertEquals(listOf(WELCOME_SEEN_KEY), store.reads)
        val url = urls.single()
        assertEquals("newAccount", url.parameters["audience"])
        assertEquals("locaki", url.parameters["project"])
        assertEquals("android", url.parameters["platform"])
    }

    @Test
    fun `conta antiga nao busca nada`() = runTest {
        val urls = mutableListOf<Url>()
        val store = FakeStore()
        assertNull(loader(store, "2026-01-01T00:00:00Z", urls).load("android", "pt-BR"))
        assertTrue(urls.isEmpty())
        assertTrue(store.reads.isEmpty())
    }

    @Test
    fun `sem data de criacao, sem store ou desligado nao mostra`() = runTest {
        val urls = mutableListOf<Url>()
        assertNull(loader(FakeStore(), null, urls).load("android", "pt-BR"))
        assertNull(loader(null, "2026-11-17T12:00:00Z", urls).load("android", "pt-BR"))
        assertNull(loader(FakeStore(), "2026-11-17T12:00:00Z", urls, enabled = false).load("android", "pt-BR"))
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `ja viu na conta nao busca`() = runTest {
        val urls = mutableListOf<Url>()
        assertNull(loader(FakeStore(seen = true), "2026-11-17T12:00:00Z", urls).load("android", "pt-BR"))
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `store falhando na leitura nao mostra`() = runTest {
        val urls = mutableListOf<Url>()
        assertNull(loader(FakeStore(failRead = true), "2026-11-17T12:00:00Z", urls).load("android", "pt-BR"))
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `marca welcome na conta uma vez so, e so para o tipo welcome`() = runTest {
        val store = FakeStore()
        val l = loader(store, "2026-11-17T12:00:00Z")
        val a = l.load("android", "pt-BR")!!
        l.markShown(a.copy(kind = AnnouncementKind.NEWS))
        assertTrue(store.marked.isEmpty())
        l.markShown(a)
        l.markShown(a)
        assertEquals(listOf(WELCOME_SEEN_KEY), store.marked)
    }

    @Test
    fun `falha ao gravar a marca nao lanca`() = runTest {
        val store = FakeStore(failWrite = true)
        val l = loader(store, "2026-11-17T12:00:00Z")
        l.markShown(l.load("android", "pt-BR")!!)
        assertTrue(store.marked.isEmpty())
    }

    // ── filtro e URL ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `filtro fica so com welcome`() {
        val list = br.com.codecacto.kmplib.ui.announcement.decodeAnnouncements(body)
        assertEquals("w1", pickWelcomeAnnouncement(list)?.id)
        assertNull(pickWelcomeAnnouncement(list.filter { it.kind != AnnouncementKind.WELCOME }))
    }

    @Test
    fun `sem audience a consulta nao manda o parametro`() = runTest {
        val urls = mutableListOf<Url>()
        client(urls).fetch(platform = "ios", locale = "en")
        assertNull(urls.single().parameters["audience"])
        client(urls).fetch(platform = "ios", locale = "en", audience = AnnouncementAudience.NEW_ACCOUNT)
        assertEquals("newAccount", urls.last().parameters["audience"])
    }
}
