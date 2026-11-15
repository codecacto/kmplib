package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
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

/** 2.366.0 — o `Retry-After` do 429 chega no [OwnAuthException.TooManyRequests]. */
class OwnAuthRetryAfterTest {

    private val texts = OwnAuthTexts()

    private fun api(retryAfter: String?): OwnAuthApi {
        val headers = if (retryAfter == null) headersOf() else headersOf(HttpHeaders.RetryAfter, retryAfter)
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.TooManyRequests, headers) })
        return OwnAuthApi(OwnAuthConfig(client, baseUrl = "https://api.example.com", texts = texts))
    }

    @Test
    fun `429 com Retry-After em segundos leva os segundos e a frase com os minutos`() = runTest {
        val e = api("300").login("ana", "x").exceptionOrNull()
        assertIs<OwnAuthException.TooManyRequests>(e)
        assertEquals(300L, e.retryAfterSeconds)
        assertEquals("Muitas tentativas. Tente de novo em 5 minutos.", e.message)
        assertEquals(429, e.code)
    }

    @Test
    fun `429 com menos de um minuto diz 1 minuto no singular`() = runTest {
        val e = api("20").login("ana", "x").exceptionOrNull() as OwnAuthException.TooManyRequests
        assertEquals(20L, e.retryAfterSeconds)
        assertEquals("Muitas tentativas. Tente de novo em 1 minuto.", e.message)
    }

    @Test
    fun `429 com Retry-After em data HTTP no passado vira zero segundos`() = runTest {
        val e = api("Wed, 21 Oct 2015 07:28:00 GMT").login("ana", "x").exceptionOrNull()
        assertIs<OwnAuthException.TooManyRequests>(e)
        assertEquals(0L, e.retryAfterSeconds)
    }

    @Test
    fun `429 sem Retry-After mantem a frase sem prazo`() = runTest {
        val e = api(null).login("ana", "x").exceptionOrNull()
        assertIs<OwnAuthException.TooManyRequests>(e)
        assertNull(e.retryAfterSeconds)
        assertEquals(texts.tooManyRequests, e.message)
    }

    @Test
    fun `429 com Retry-After ilegivel mantem a frase sem prazo`() = runTest {
        val e = api("depois").login("ana", "x").exceptionOrNull() as OwnAuthException.TooManyRequests
        assertNull(e.retryAfterSeconds)
        assertEquals(texts.tooManyRequests, e.message)
    }

    @Test
    fun `construtor antigo continua valendo`() {
        val e = OwnAuthException.TooManyRequests("x")
        assertNull(e.retryAfterSeconds)
    }

    @Test
    fun `tooManyRequestsMessage escolhe a frase pelo prazo`() {
        assertEquals(texts.tooManyRequests, texts.tooManyRequestsMessage(null))
        assertEquals("Muitas tentativas. Tente de novo em 2 minutos.", texts.tooManyRequestsMessage(61))
        val custom = texts.copy(tooManyRequestsRetryIn = { m -> "espere $m" })
        assertEquals("espere 15", custom.tooManyRequestsMessage(900))
    }

    private fun repo(retryAfter: String?): EmailPasswordAuthRepository {
        val a = api(retryAfter)
        val tm = OwnAuthTokenManager(a, AuthSessionStore(FakeSecureTokenStorage()), 60) { 1_700_000_000_000L }
        return EmailPasswordAuthRepository(a, tm, texts)
    }

    @Test
    fun `signInWithPassword devolve a excecao tipada com o prazo`() = runTest {
        val r = repo("120")
        val e = r.signInWithPassword("ana", "x").exceptionOrNull()
        assertIs<OwnAuthException.TooManyRequests>(e)
        assertEquals(120L, e.retryAfterSeconds)
        assertFalse(r.isLoggedInSync)
    }

    @Test
    fun `signInWithEmail segue devolvendo a AuthException canonica`() = runTest {
        val e = repo("120").signInWithEmail("ana", "x").exceptionOrNull()
        assertEquals(AuthException.TooManyRequests, e)
    }

    @Test
    fun `signInWithPassword com sucesso adota a sessao`() = runTest {
        val (a, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-9"), "r1") }
        val tm = OwnAuthTokenManager(a, AuthSessionStore(FakeSecureTokenStorage()), 60) { 1_700_000_000_000L }
        val r = EmailPasswordAuthRepository(a, tm)
        assertEquals("acc-9", r.signInWithPassword("ana", "x").getOrThrow().id)
    }

    @Test
    fun `OwnAuthService de fora da lib recebe Unsupported por default`() = runTest {
        val externo = object : OwnAuthService {
            override suspend fun register(
                name: String,
                email: String,
                password: String,
                acceptedTerms: Boolean,
                phone: String?,
            ): Result<br.com.codecacto.kmplib.firebase.auth.User> = error("não usado")
            override suspend fun requestPasswordReset(email: String): Result<Unit> = error("não usado")
            override suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> =
                error("não usado")
        }
        assertIs<OwnAuthException.Unsupported>(externo.signInWithPassword("a", "b").exceptionOrNull())
    }
}
