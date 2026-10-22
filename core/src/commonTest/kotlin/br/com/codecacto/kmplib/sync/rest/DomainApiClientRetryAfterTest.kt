package br.com.codecacto.kmplib.sync.rest

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 2.321.0: o `Retry-After` do 429/503 chega ao erro (a backlib escreve o recuo só no cabeçalho). */
class DomainApiClientRetryAfterTest {

    private fun cliente(status: HttpStatusCode, body: String, retryAfter: String?): DomainApiClient {
        val engine = MockEngine {
            val h = if (retryAfter != null) {
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf(retryAfter))
            } else {
                headersOf(HttpHeaders.ContentType, "application/json")
            }
            respond(body, status, h)
        }
        return DomainApiClient(HttpClient(engine) { expectSuccess = false }, DomainTokenProvider { "t" }, "https://api.example.com")
    }

    @Test
    fun `429 com Retry-After em segundos vira retryAfterSeconds`() = runTest {
        val r = cliente(HttpStatusCode.TooManyRequests, """{"message":"Aguarde","code":"OTP_RESEND_TOO_SOON"}""", "42").postJson("/x", "{}")
        val e = assertIs<DomainResult.Error>(r)
        assertEquals(42L, e.retryAfterSeconds)
        assertEquals("OTP_RESEND_TOO_SOON", e.serverCode)
    }

    @Test
    fun `o valor do corpo vence o cabecalho e data HTTP e ignorada`() = runTest {
        val corpo = cliente(HttpStatusCode.TooManyRequests, """{"message":"x","details":{"retryAfterSeconds":"7"}}""", "42").getJson("/x")
        assertEquals(7L, assertIs<DomainResult.Error>(corpo).retryAfterSeconds)
        val data = cliente(HttpStatusCode.ServiceUnavailable, """{"message":"x"}""", "Wed, 21 Oct 2026 07:28:00 GMT").getJson("/x")
        assertNull(assertIs<DomainResult.Error>(data).retryAfterSeconds)
    }

    @Test
    fun `fora do 429 e 503 o cabecalho nao entra`() = runTest {
        val r = cliente(HttpStatusCode.BadRequest, """{"message":"x"}""", "42").getJson("/x")
        assertNull(assertIs<DomainResult.Error>(r).retryAfterSeconds)
    }
}
