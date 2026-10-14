package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.monetization.access.SubscriptionBlockReason
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
import kotlin.test.assertTrue

/** 2.300.0 — o 402 de conta bloqueada chega ao ViewModel com o `serverCode`, distinto da cota. */
class DomainApiClientSubscriptionBlockTest {

    private fun cliente(status: HttpStatusCode, body: String): DomainApiClient {
        val engine = MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "t" }, "https://api.example.com")
    }

    @Test
    fun `402 TRIAL_EXPIRED vira Error com serverCode e isSubscriptionBlocked`() = runTest {
        val r = cliente(HttpStatusCode.PaymentRequired, """{"code":"TRIAL_EXPIRED","message":"Teste acabou"}""")
            .getJson("/v1/locacoes")
        assertIs<DomainResult.Error>(r)
        assertEquals(402, r.code)
        assertEquals(SubscriptionBlockReason.TRIAL_EXPIRED.code, r.serverCode)
        assertEquals("Teste acabou", r.serverMessage)
        assertTrue(r.isSubscriptionBlocked)
    }

    @Test
    fun `402 de cota continua Quota`() = runTest {
        val r = cliente(
            HttpStatusCode.PaymentRequired,
            """{"code":"QUOTA_EXCEEDED","details":{"feature":"items","limite":"5","contagem":"5"}}""",
        ).getJson("/v1/itens")
        assertIs<DomainResult.Quota>(r)
    }

    @Test
    fun `402 sem corpo continua Error sem bloqueio`() = runTest {
        val r = cliente(HttpStatusCode.PaymentRequired, "").getJson("/v1/x")
        assertIs<DomainResult.Error>(r)
        assertFalse(r.isSubscriptionBlocked)
    }
}
