package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.locale.FactoryLocales
import br.com.codecacto.kmplib.firebase.auth.User
import br.com.codecacto.kmplib.ui.components.ForcePasswordChangeTexts
import br.com.codecacto.kmplib.ui.locale.uiLanguageTag
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnAuthLocaleTest {

    @Test
    fun `register leva o idioma da conta, e o omite quando nao ha`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ -> HttpStatusCode.Created to tokensJson(fakeJwt("a"), "r") }

        api.register("Ana", "ana@x.com", "s3nha123", acceptedTerms = true, locale = "pt-PT")
        assertTrue("\"locale\":\"pt-PT\"" in cap.last().body, cap.last().body)

        api.register("Bia", "bia@x.com", "s3nha123", acceptedTerms = true)
        assertTrue("locale" !in cap.last().body)

        api.register("Cida", "cida@x.com", "s3nha123", acceptedTerms = true, locale = "  ")
        assertTrue("locale" !in cap.last().body)
    }

    @Test
    fun `mensagens do app vencem as da lib`() = runTest {
        val config = OwnAuthConfig(
            mockHttpClient { _, _ -> HttpStatusCode.Unauthorized to "{}" },
            baseUrl = "https://api.example.com",
            texts = OwnAuthTexts(invalidCredentials = "credencial do app"),
        )
        val r = OwnAuthApi(config).login("a@x.com", "x")
        assertEquals("credencial do app", r.exceptionOrNull()?.message)
        assertEquals("credencial do app", config.texts.invalidCredentials)
    }

    @Test
    fun `sem mensagens do app, a lib responde no idioma da tela`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.Unauthorized to "{}" }
        val mensagem = api.login("a@x.com", "x").exceptionOrNull()?.message
        val esperado = if (uiLanguageTag() == FactoryLocales.PT_BR) OwnAuthTexts().invalidCredentials
        else loadOwnAuthTexts().invalidCredentials
        assertEquals(esperado, mensagem)
    }

    @Test
    fun `recursos pt-BR do own-auth batem com os defaults da classe`() = runTest {
        if (uiLanguageTag() != FactoryLocales.PT_BR) return@runTest
        val lido = loadOwnAuthTexts()
        val padrao = OwnAuthTexts()
        assertEquals(padrao.copy(server = lido.server, socialNonceMissing = lido.socialNonceMissing), lido)
        assertEquals(padrao.server(502), lido.server(502))
    }

    @Test
    fun `porta com idioma tem default que chama o register de sempre`() = runTest {
        var chamado: String? = null
        val porta = object : OwnAuthService {
            override suspend fun register(
                name: String, email: String, password: String, acceptedTerms: Boolean, phone: String?,
            ): Result<User> { chamado = "$name|$phone"; return Result.failure(IllegalStateException()) }
            override suspend fun requestPasswordReset(email: String) = Result.success(Unit)
            override suspend fun confirmPasswordReset(token: String, newPassword: String) = Result.success(Unit)
            override suspend fun changeOwnPassword(currentPassword: String, newPassword: String) =
                Result.success(PasswordChangeOutcome.SignInRequired)
            override suspend fun completeFirstAccess(newPassword: String): Result<User> =
                Result.failure(IllegalStateException())
        }
        porta.register("Ana", "a@x.com", "s", true, "123", locale = "en")
        assertEquals("Ana|123", chamado)
    }

    @Test
    fun `textos do primeiro acesso preenchem nome e minimo`() {
        val t = ForcePasswordChangeTexts()
        assertEquals("Olá, Ana!", t.greeting("Ana"))
        assertEquals("A senha deve ter ao menos 8 caracteres", t.minLength(8))
    }
}
