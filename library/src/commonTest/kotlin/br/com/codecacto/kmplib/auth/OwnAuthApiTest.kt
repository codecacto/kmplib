package br.com.codecacto.kmplib.auth

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OwnAuthApiTest {

    @Test
    fun `register usa authBasePath configuravel e envia todos os campos`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap, authBasePath = "/v1/staff/auth") { _, _ ->
            HttpStatusCode.Created to tokensJson(fakeJwt("acc-1"), "r1")
        }
        val r = api.register("Ana", "ana@x.com", "s3nha123", acceptedTerms = true)
        assertTrue(r.isSuccess)
        assertEquals("https://api.example.com/v1/staff/auth/register", cap.first().url)
        val body = cap.first().body
        assertTrue("\"name\":\"Ana\"" in body)
        assertTrue("\"acceptedTerms\":true" in body)
    }

    /**
     * O telefone que a `RegisterScreen` coleta tem de CHEGAR ao servidor.
     *
     * Até a 2.122.0 o `RegisterBody` não tinha o campo: o app pedia o WhatsApp, a pessoa digitava, e
     * o valor morria no `ViewModel`. Este teste é o que impede a regressão — e trava a decisão de
     * OMITIR a chave quando não há telefone, para o corpo não dizer "informei nada".
     */
    @Test
    fun `register leva o telefone, e o omite quando nao ha`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ ->
            HttpStatusCode.Created to tokensJson(fakeJwt("acc-1"), "r1")
        }

        api.register("Ana", "ana@x.com", "s3nha123", acceptedTerms = true, phone = "(11) 98765-4321")
        // Vai como a pessoa digitou: a máscara é da tela, e normalizar aqui decidiria formato por
        // todos os produtos que usam a lib.
        assertTrue("\"phone\":\"(11) 98765-4321\"" in cap.last().body)

        api.register("Bia", "bia@x.com", "s3nha123", acceptedTerms = true)
        assertTrue("phone" !in cap.last().body)
    }

    @Test
    fun `authBasePath customizado (cliente) muda a rota`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap, authBasePath = "/v1/customer/auth") { _, _ ->
            HttpStatusCode.OK to tokensJson(fakeJwt("acc-2"), "r2")
        }
        api.login("c@x.com", "pass")
        assertEquals("https://api.example.com/v1/customer/auth/login", cap.first().url)
    }

    @Test
    fun `login parseia tokens`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.OK to tokensJson("acc-tok", "ref-tok", 900) }
        val tokens = api.login("a@x.com", "p").getOrThrow()
        assertEquals("acc-tok", tokens.accessToken)
        assertEquals("ref-tok", tokens.refreshToken)
        assertEquals(900, tokens.expiresInSeconds)
    }

    @Test
    fun `login 401 vira InvalidCredentials`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.Unauthorized to "" }
        val e = api.login("a@x.com", "p").exceptionOrNull()
        assertIs<OwnAuthException.InvalidCredentials>(e)
        assertTrue(e.isClientError)
    }

    @Test
    fun `register 409 vira EmailAlreadyInUse`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.Conflict to "" }
        val e = api.register("A", "a@x.com", "p", true).exceptionOrNull()
        assertIs<OwnAuthException.EmailAlreadyInUse>(e)
    }

    @Test
    fun `password reset com 400 vira InvalidResetToken`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to "" }
        val e = api.confirmPasswordReset("tok", "novaSenha1").exceptionOrNull()
        assertIs<OwnAuthException.InvalidResetToken>(e)
    }

    @Test
    fun `429 vira TooManyRequests`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.TooManyRequests to "" }
        val e = api.login("a@x.com", "p").exceptionOrNull()
        assertIs<OwnAuthException.TooManyRequests>(e)
    }

    @Test
    fun `forgot sempre resolve (200 generico) e logout resolve (204)`() = runTest {
        val (api, _) = mockOwnAuthApi { path, _ ->
            if (path.endsWith("logout")) HttpStatusCode.NoContent to "" else HttpStatusCode.OK to "{}"
        }
        assertTrue(api.requestPasswordReset("a@x.com").isSuccess)
        assertTrue(api.logout("r1").isSuccess)
    }
}
