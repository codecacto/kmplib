package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.RecentAuthChallenge
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O 401 `REAUTH_REQUIRED` (backlib 0.151.0, RFC 9470) no [DomainApiClient]: chega tipado e NÃO
 * renova o token nem repete — o 401 comum continua como sempre (refresh + 1 retry).
 */
class DomainApiClientReauthTest {

    private val reauthBody =
        """{"message":"Confirme sua identidade para continuar.","code":"REAUTH_REQUIRED","details":{"maxAgeSeconds":"300"}}"""
    private val challenge =
        """Bearer error="insufficient_user_authentication", error_description="A more recent authentication is required", max_age=300"""

    private class Rec {
        var requests = 0
        val forceRefresh = mutableListOf<Boolean>()
    }

    private fun cliente(rec: Rec, responder: (attempt: Int) -> Triple<HttpStatusCode, String, String?>): DomainApiClient {
        val engine = MockEngine {
            rec.requests++
            val (status, body, www) = responder(rec.requests)
            val headers = if (www == null) {
                headersOf(HttpHeaders.ContentType, "application/json")
            } else {
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.WWWAuthenticate to listOf(www))
            }
            respond(content = body, status = status, headers = headers)
        }
        val provider = DomainTokenProvider { force -> rec.forceRefresh += force; if (force) "t2" else "t1" }
        return DomainApiClient(HttpClient(engine), provider, "https://api.example.com")
    }

    @Test
    fun `401 REAUTH nao renova o token nem repete e chega tipado`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { Triple(HttpStatusCode.Unauthorized, reauthBody, challenge) }.delete("/v1/me")

        assertIs<DomainResult.Error>(r)
        assertTrue(r.isReauthRequired)
        assertEquals(300L, r.reauthMaxAgeSeconds)
        assertEquals(DomainApiTexts().reauthRequired, r.message)
        assertEquals(1, rec.requests, "sem retry")
        assertEquals(listOf(false), rec.forceRefresh, "sem refresh forçado (o refresh não renova o auth_time)")
        assertEquals(300L, r.toReauthRequiredException().maxAgeSeconds)
    }

    @Test
    fun `so o cabecalho RFC 9470 ja basta`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { Triple(HttpStatusCode.Unauthorized, "", challenge) }.delete("/v1/me")

        assertIs<DomainResult.Error>(r)
        assertTrue(r.isReauthRequired)
        assertEquals(RecentAuthChallenge.CODE, r.serverCode)
        assertEquals(300L, r.reauthMaxAgeSeconds, "max_age lido do WWW-Authenticate")
        assertEquals(1, rec.requests)
    }

    @Test
    fun `401 comum continua renovando e repetindo uma vez`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { n ->
            if (n == 1) Triple(HttpStatusCode.Unauthorized, """{"message":"x","code":"UNAUTHORIZED"}""", "Bearer")
            else Triple(HttpStatusCode.OK, "{}", null)
        }.getJson("/v1/x")

        assertIs<DomainResult.Success<String>>(r)
        assertEquals(2, rec.requests)
        assertEquals(listOf(false, true), rec.forceRefresh)
    }

    @Test
    fun `401 comum que persiste e sessao expirada e nao reauth`() = runTest {
        val rec = Rec()
        val r = cliente(rec) { Triple(HttpStatusCode.Unauthorized, """{"message":"x","code":"UNAUTHORIZED"}""", null) }
            .delete("/v1/me")

        assertIs<DomainResult.Error>(r)
        assertFalse(r.isReauthRequired)
        assertEquals(DomainApiTexts().sessionExpired, r.message)
        assertEquals(2, rec.requests)
    }

    @Test
    fun `reconhecimento puro do desafio`() {
        assertTrue(RecentAuthChallenge.matches(401, "REAUTH_REQUIRED", null))
        assertTrue(RecentAuthChallenge.matches(401, null, challenge))
        assertTrue(RecentAuthChallenge.matches(401, null, "Bearer error=insufficient_user_authentication"))
        assertFalse(RecentAuthChallenge.matches(403, "REAUTH_REQUIRED", challenge), "só 401")
        assertFalse(RecentAuthChallenge.matches(401, null, "Bearer error=\"invalid_token\""))
        assertFalse(RecentAuthChallenge.matches(401, null, "Basic realm=\"x\", error=\"insufficient_user_authentication\""))
        assertFalse(RecentAuthChallenge.matches(401, "UNAUTHORIZED", null))
        // `error=` dentro do texto entre aspas de OUTRO parâmetro não é o `error` do desafio.
        assertFalse(
            RecentAuthChallenge.matches(
                401, null,
                "Bearer error_description=\"veja error=insufficient_user_authentication\", error=\"invalid_token\"",
            ),
        )
        assertTrue(RecentAuthChallenge.matches(401, null, "bearer realm=\"api\", error=\"insufficient_user_authentication\""))
        assertEquals(120L, RecentAuthChallenge.maxAgeSeconds(mapOf("maxAgeSeconds" to "120"), challenge))
        assertEquals(300L, RecentAuthChallenge.maxAgeSeconds(emptyMap(), challenge))
        assertNull(RecentAuthChallenge.maxAgeSeconds(mapOf("maxAgeSeconds" to "abc"), "Bearer"))
    }
}
