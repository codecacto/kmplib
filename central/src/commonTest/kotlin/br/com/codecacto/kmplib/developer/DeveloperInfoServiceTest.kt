package br.com.codecacto.kmplib.developer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeveloperInfoServiceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    private val fullBody = """
        {
          "contact": { "whatsapp": "5511999998888", "email": "dev@codecacto.com.br", "site": "https://codecacto.com.br" },
          "apps": [
            { "id": "b", "name": "B", "description": "desc b", "logoUrl": "https://l/b.png", "storeUrl": "https://s/b", "order": 2 },
            { "id": "a", "name": "A", "description": "desc a", "logoUrl": "https://l/a.png", "storeUrl": "https://s/a", "order": 1 }
          ]
        }
    """.trimIndent()

    private data class Captured(var url: String? = null, var method: String? = null, var count: Int = 0)

    private fun init(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = fullBody,
        baseUrl: String = "https://apps-api.codecacto.com.br",
    ) {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            captured.count++
            respond(content = body, status = status, headers = jsonHeader)
        }
        DeveloperInfoService.initialize(
            DeveloperConfig(httpClient = HttpClient(engine), appsApiBaseUrl = baseUrl)
        )
    }

    @Test
    fun `fetch faz GET em public developer`() = runTest {
        val cap = Captured()
        init(captured = cap)
        DeveloperInfoService.fetchContact()
        assertEquals("GET", cap.method)
        assertTrue(cap.url!!.endsWith("/public/developer"))
    }

    @Test
    fun `mapeia contato do bloco contact`() = runTest {
        init()
        val contact = DeveloperInfoService.fetchContact().getOrThrow()
        assertEquals("5511999998888", contact.whatsapp)
        assertEquals("dev@codecacto.com.br", contact.email)
        assertEquals("https://codecacto.com.br", contact.site)
    }

    @Test
    fun `mapeia apps e ordena por order`() = runTest {
        init()
        val apps = DeveloperInfoService.fetchApps().getOrThrow()
        assertEquals(listOf("a", "b"), apps.map { it.id })
        assertEquals("A", apps.first().name)
        assertEquals("https://s/a", apps.first().storeUrl)
    }

    @Test
    fun `cache faz um unico request para contato e apps`() = runTest {
        val cap = Captured()
        init(captured = cap)
        DeveloperInfoService.fetchContact()
        DeveloperInfoService.fetchApps()
        assertEquals(1, cap.count)
    }

    @Test
    fun `contato ausente cai no fallback`() = runTest {
        init(body = """{ "apps": [] }""")
        val contact = DeveloperInfoService.fetchContact().getOrThrow()
        assertEquals("55998030240", contact.whatsapp)
        assertEquals("raphael.am.dev@gmail.com", contact.email)
        assertEquals("", contact.site)
    }

    @Test
    fun `campos de contato em branco caem no fallback`() = runTest {
        init(body = """{ "contact": { "whatsapp": "", "email": "" }, "apps": [] }""")
        val contact = DeveloperInfoService.fetchContact().getOrThrow()
        assertEquals("55998030240", contact.whatsapp)
        assertEquals("raphael.am.dev@gmail.com", contact.email)
    }

    @Test
    fun `erro 500 vira contato fallback e lista vazia best-effort`() = runTest {
        DeveloperInfoService.initialize(
            DeveloperConfig(
                httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
            )
        )
        val contact = DeveloperInfoService.fetchContact().getOrThrow()
        val apps = DeveloperInfoService.fetchApps().getOrThrow()
        assertEquals("55998030240", contact.whatsapp)
        assertTrue(apps.isEmpty())
    }

    @Test
    fun `corpo invalido nao lanca e usa fallback`() = runTest {
        init(body = "nao e json")
        val contact = DeveloperInfoService.fetchContact().getOrThrow()
        assertEquals("55998030240", contact.whatsapp)
        assertTrue(DeveloperInfoService.fetchApps().getOrThrow().isEmpty())
    }
}
