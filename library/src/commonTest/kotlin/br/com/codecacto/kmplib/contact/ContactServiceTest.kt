package br.com.codecacto.kmplib.contact

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactServiceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")
    private val okBody = """{ "id": "c1", "projectSlug": "influencer", "createdAt": 1 }"""

    private data class Captured(var url: String? = null, var method: String? = null, var body: String? = null, var count: Int = 0)

    private fun init(
        captured: Captured = Captured(),
        status: HttpStatusCode = HttpStatusCode.Created,
        source: String = "app",
        userEmail: String = "",
    ) {
        val engine = MockEngine { request ->
            captured.url = request.url.toString()
            captured.method = request.method.value
            captured.body = (request.body as? io.ktor.http.content.TextContent)?.text
            captured.count++
            respond(content = okBody, status = status, headers = jsonHeader)
        }
        ContactService.initialize(
            ContactConfig(
                projectSlug = "influencer",
                httpClient = HttpClient(engine),
                source = source,
                userEmail = userEmail,
            )
        )
    }

    @Test
    fun `send faz POST em contact v1 com projectSlug e source`() = runTest {
        val cap = Captured()
        init(captured = cap)
        val r = ContactService.send(name = "Ana", email = "ana@x.com", message = "Oi")
        assertTrue(r.isSuccess)
        assertEquals("POST", cap.method)
        assertTrue(cap.url!!.endsWith("/contact/v1"))
        assertTrue(cap.body!!.contains("\"projectSlug\":\"influencer\""))
        assertTrue(cap.body!!.contains("\"source\":\"app\""))
    }

    @Test
    fun `campos opcionais em branco nao sao serializados`() = runTest {
        val cap = Captured()
        init(captured = cap)
        ContactService.send(name = "Ana", email = "ana@x.com", message = "Oi", whatsapp = "", subject = "")
        assertFalse(cap.body!!.contains("\"whatsapp\""))
        assertFalse(cap.body!!.contains("\"subject\""))
    }

    @Test
    fun `email vazio cai no userEmail do config`() = runTest {
        val cap = Captured()
        init(captured = cap, userEmail = "logado@x.com")
        ContactService.send(name = "Ana", email = "", message = "Oi")
        assertTrue(cap.body!!.contains("\"email\":\"logado@x.com\""))
    }

    @Test
    fun `429 vira failure best-effort sem lancar`() = runTest {
        ContactService.initialize(
            ContactConfig(
                projectSlug = "influencer",
                httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.TooManyRequests) }),
            )
        )
        val r = ContactService.send(name = "Ana", email = "ana@x.com", message = "Oi")
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is ContactSendException)
        assertEquals(429, (r.exceptionOrNull() as ContactSendException).code)
    }
}
