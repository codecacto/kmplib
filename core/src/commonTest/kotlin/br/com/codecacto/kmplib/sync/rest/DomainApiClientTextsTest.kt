package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainApiClientTextsTest {

    private fun cliente(
        status: HttpStatusCode,
        texts: DomainApiTexts = DomainApiTexts(),
        provider: (suspend () -> DomainApiTexts)? = null,
    ): DomainApiClient {
        val engine = MockEngine { respond(content = "", status = status) }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "t" }, "https://api.example.com", texts, provider)
    }

    @Test
    fun `sem provedor os textos fixos de sempre`() = runTest {
        val r = cliente(HttpStatusCode.TooManyRequests).getJson("/x") as DomainResult.Error
        assertEquals(DomainApiTexts().rateLimited, r.message)
    }

    @Test
    fun `provedor vence os fixos e e lido a cada erro`() = runTest {
        var idioma = "en"
        val provedor: suspend () -> DomainApiTexts = {
            DomainApiTexts(serverError = { "Server error ($it) [$idioma]" })
        }
        val api = cliente(HttpStatusCode.InternalServerError, DomainApiTexts(serverError = { "fixo" }), provedor)
        assertEquals("Server error (500) [en]", (api.getJson("/x") as DomainResult.Error).message)
        idioma = "es"
        assertEquals("Server error (500) [es]", (api.getJson("/x") as DomainResult.Error).message)
    }

    @Test
    fun `provedor que falha cai nos fixos`() = runTest {
        val api = cliente(HttpStatusCode.Unauthorized, DomainApiTexts(sessionExpired = "fixo"), { error("sem recursos") })
        assertEquals("fixo", (api.getJson("/x") as DomainResult.Error).message)
    }
}
