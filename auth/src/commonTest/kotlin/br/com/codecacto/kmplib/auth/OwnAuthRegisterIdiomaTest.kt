package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import br.com.codecacto.kmplib.firebase.auth.User
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2.376.0 — "WhatsApp obrigatório no cadastro em pt-BR" (constituição, 16/nov/2026): o app manda
 * `idioma` no `POST …/register` e o backend recusa com 400 `WHATSAPP_OBRIGATORIO` + `details.telefone`.
 */
class OwnAuthRegisterIdiomaTest {

    private val whatsappObrigatorio =
        """{"message":"Informe o WhatsApp","code":"WHATSAPP_OBRIGATORIO","details":{"telefone":"Informe o WhatsApp"}}"""

    @Test
    fun `register leva o idioma e o omite quando nao ha`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ -> HttpStatusCode.Created to tokensJson(fakeJwt("a"), "r") }

        api.register("Ana", "ana@x.com", "s3nha123", acceptedTerms = true, idioma = " pt-BR ")
        assertTrue("\"idioma\":\"pt-BR\"" in cap.last().body, cap.last().body)

        api.register("Bia", "bia@x.com", "s3nha123", acceptedTerms = true, idioma = "  ")
        assertTrue("idioma" !in cap.last().body)
    }

    @Test
    fun `sem idioma o corpo e identico ao de antes e o locale nao vira idioma`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ -> HttpStatusCode.Created to tokensJson(fakeJwt("a"), "r") }

        api.register("Ana", "ana@x.com", "s3nha123", acceptedTerms = true, phone = "(65) 99999-0000", locale = "pt-BR")
        val body = cap.last().body
        assertTrue("idioma" !in body, body)
        assertTrue("\"locale\":\"pt-BR\"" in body, body)
    }

    @Test
    fun `400 WHATSAPP_OBRIGATORIO vira RegisterRejected com o erro no campo`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to whatsappObrigatorio }
        val e = api.register("Ana", "a@x.com", "s3nha123", true, idioma = "pt-BR").exceptionOrNull()
        assertIs<OwnAuthException.RegisterRejected>(e)
        assertEquals(OwnAuthErrorCodes.WHATSAPP_OBRIGATORIO, e.serverCode)
        assertEquals("Informe o WhatsApp", e.fieldError("telefone"))
        assertEquals("Informe o WhatsApp", e.message)
        assertEquals(400, e.code)
        assertTrue(e.isClientError)
        assertNull(e.fieldError("name"))
    }

    @Test
    fun `erro por campo com o code generico tambem vira RegisterRejected`() = runTest {
        val corpo = """{"message":"Nome é obrigatório","code":"VALIDATION_ERROR","details":{"name":"Nome é obrigatório"}}"""
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to corpo }
        val e = api.register("", "a@x.com", "s3nha123", true).exceptionOrNull()
        assertIs<OwnAuthException.RegisterRejected>(e)
        assertEquals("Nome é obrigatório", e.fieldError("name"))
    }

    @Test
    fun `422 com code proprio vira RegisterRejected`() = runTest {
        val corpo = """{"message":"Idioma não suportado","code":"LOCALE_NOT_SUPPORTED"}"""
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.UnprocessableEntity to corpo }
        val e = api.register("Ana", "a@x.com", "s3nha123", true).exceptionOrNull()
        assertIs<OwnAuthException.RegisterRejected>(e)
        assertEquals(422, e.code)
        assertEquals("LOCALE_NOT_SUPPORTED", e.serverCode)
        assertTrue(e.fieldErrors.isEmpty())
    }

    @Test
    fun `400 generico da senha curta continua WeakPassword`() = runTest {
        val corpo = """{"message":"A senha deve ter ao menos 8 caracteres","code":"VALIDATION_ERROR"}"""
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to corpo }
        val e = api.register("Ana", "a@x.com", "curta", true).exceptionOrNull()
        assertIs<OwnAuthException.WeakPassword>(e)
        assertEquals("A senha deve ter ao menos 8 caracteres", e.message)
    }

    @Test
    fun `400 sem corpo continua WeakPassword`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to "" }
        assertIs<OwnAuthException.WeakPassword>(api.register("Ana", "a@x.com", "x", true).exceptionOrNull())
    }

    @Test
    fun `fora do cadastro o 400 com code nao vira RegisterRejected`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to whatsappObrigatorio }
        assertIs<OwnAuthException.InvalidResetToken>(api.confirmPasswordReset("tok", "novaSenha1").exceptionOrNull())
    }

    @Test
    fun `409 do cadastro continua EmailAlreadyInUse mesmo com details`() = runTest {
        val corpo = """{"message":"E-mail em uso","code":"EMAIL_TAKEN","details":{"email":"E-mail em uso"}}"""
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.Conflict to corpo }
        assertIs<OwnAuthException.EmailAlreadyInUse>(api.register("Ana", "a@x.com", "s3nha123", true).exceptionOrNull())
    }

    private fun repo(status: HttpStatusCode, body: String, cap: MutableList<CapturedRequest> = mutableListOf()): EmailPasswordAuthRepository {
        val (a, _) = mockOwnAuthApi(cap) { _, _ -> status to body }
        val tm = OwnAuthTokenManager(a, AuthSessionStore(FakeSecureTokenStorage()), 60) { 1_700_000_000_000L }
        return EmailPasswordAuthRepository(a, tm)
    }

    @Test
    fun `registerWithPassword devolve a excecao tipada e nao abre sessao`() = runTest {
        val r = repo(HttpStatusCode.BadRequest, whatsappObrigatorio)
        val e = r.registerWithPassword("Ana", "a@x.com", "s3nha123", true, idioma = "pt-BR").exceptionOrNull()
        assertIs<OwnAuthException.RegisterRejected>(e)
        assertEquals("Informe o WhatsApp", e.fieldError("telefone"))
        assertFalse(r.isLoggedInSync)
    }

    @Test
    fun `register com idioma segue devolvendo AuthException com a frase do servidor`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val r = repo(HttpStatusCode.BadRequest, whatsappObrigatorio, cap)
        val e = r.register("Ana", "a@x.com", "s3nha123", true, null, null, idioma = "pt-BR").exceptionOrNull()
        assertIs<AuthException.UnknownError>(e)
        assertEquals("Informe o WhatsApp", e.message)
        assertTrue("\"idioma\":\"pt-BR\"" in cap.last().body)
    }

    @Test
    fun `register de sempre com senha curta continua AuthException WeakPassword`() = runTest {
        val r = repo(HttpStatusCode.BadRequest, """{"message":"A senha deve ter ao menos 8 caracteres","code":"VALIDATION_ERROR"}""")
        assertEquals(AuthException.WeakPassword, r.register("Ana", "a@x.com", "x", true).exceptionOrNull())
    }

    @Test
    fun `registerWithPassword com sucesso adota a sessao e manda o idioma`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val r = repo(HttpStatusCode.Created, tokensJson(fakeJwt("acc-7"), "r1"), cap)
        val u = r.registerWithPassword("Ana", "a@x.com", "s3nha123", true, "(65) 99999-0000", idioma = "pt-BR").getOrThrow()
        assertEquals("acc-7", u.id)
        assertTrue(r.isLoggedInSync)
        assertTrue("\"idioma\":\"pt-BR\"" in cap.first { it.url.endsWith("/register") }.body)
    }

    @Test
    fun `porta de fora da lib tem defaults que caem no register de sempre`() = runTest {
        var chamado: String? = null
        val porta = object : OwnAuthService {
            override suspend fun register(
                name: String, email: String, password: String, acceptedTerms: Boolean, phone: String?,
            ): Result<User> { chamado = "$name|$phone"; return Result.failure(IllegalStateException()) }
            override suspend fun requestPasswordReset(email: String) = Result.success(Unit)
            override suspend fun confirmPasswordReset(token: String, newPassword: String) = Result.success(Unit)
        }
        porta.register("Ana", "a@x.com", "s", true, "123", "pt-BR", "pt-BR")
        assertEquals("Ana|123", chamado)
        porta.registerWithPassword("Bia", "b@x.com", "s", true, "456", idioma = "pt-BR")
        assertEquals("Bia|456", chamado)
    }
}
