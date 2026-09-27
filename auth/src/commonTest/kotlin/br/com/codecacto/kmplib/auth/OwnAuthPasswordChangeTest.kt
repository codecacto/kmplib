package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Troca de senha own-auth (2.216.0): a voluntária (`password/change`) e a do primeiro acesso
 * (`password/first-access`), no nível do repositório — que é onde mora o que o app esquecia de
 * fazer: renovar a sessão que o servidor acabou de revogar.
 */
class OwnAuthPasswordChangeTest {

    private val now = 1_700_000_000_000L

    private class Cenario(
        val repo: EmailPasswordAuthRepository,
        val tm: OwnAuthTokenManager,
        val captured: MutableList<CapturedRequest>,
    )

    private suspend fun logado(
        tokensDoLogin: String = tokensJson(fakeJwt("acc-1"), "r1"),
        responder: (path: String) -> Pair<HttpStatusCode, String>,
    ): Cenario {
        var primeiroLogin = true
        val (api, cap) = mockOwnAuthApi { path, _ ->
            if (path.endsWith("/login") && primeiroLogin) {
                primeiroLogin = false
                HttpStatusCode.OK to tokensDoLogin
            } else {
                responder(path)
            }
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("ana@x.com", "antiga").getOrThrow()
        return Cenario(repo, tm, cap)
    }

    @Test
    fun `troca aceita renova a sessao entrando de novo com a senha nova`() = runTest {
        val c = logado { path ->
            when {
                path.endsWith("/password/change") -> HttpStatusCode.NoContent to ""
                path.endsWith("/login") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r2")
                else -> HttpStatusCode.NotFound to ""
            }
        }

        val r = c.repo.changeOwnPassword("antiga", "novaSenha1").getOrThrow()

        assertIs<PasswordChangeOutcome.SessionRenewed>(r)
        assertEquals("r2", c.tm.session.value?.refreshToken, "o refresh revogado tem de ser trocado")
        assertEquals("ana@x.com", c.tm.session.value?.email)
        val troca = c.captured.single { it.url.endsWith("/password/change") }
        assertTrue(troca.body.contains("\"currentPassword\":\"antiga\""))
        assertTrue(troca.body.contains("\"newPassword\":\"novaSenha1\""))
        assertFalse(troca.body.contains("email"), "a conta vem do token, nunca do corpo")
        val relogin = c.captured.last { it.url.endsWith("/login") }
        assertTrue(relogin.body.contains("novaSenha1"))
    }

    @Test
    fun `senha atual errada vira InvalidCredentials com texto de senha atual e a sessao fica`() = runTest {
        val c = logado { HttpStatusCode.Unauthorized to """{"message":"Senha atual incorreta","code":"UNAUTHORIZED"}""" }

        val e = c.repo.changeOwnPassword("errada", "novaSenha1").exceptionOrNull()

        assertIs<OwnAuthException.InvalidCredentials>(e)
        assertEquals(OwnAuthTexts().currentPasswordIncorrect, e.message)
        assertEquals("r1", c.tm.session.value?.refreshToken, "recusa não mexe na sessão")
    }

    @Test
    fun `senha nova recusada traz o motivo do servidor`() = runTest {
        val c = logado { HttpStatusCode.BadRequest to """{"message":"A nova senha deve ser diferente da atual","code":"VALIDATION_ERROR"}""" }

        val e = c.repo.changeOwnPassword("antiga", "antiga").exceptionOrNull()

        assertIs<OwnAuthException.WeakPassword>(e)
        assertEquals("A nova senha deve ser diferente da atual", e.message)
    }

    @Test
    fun `sem conseguir entrar de novo a sessao local e encerrada e o desfecho pede login`() = runTest {
        val c = logado { path ->
            when {
                path.endsWith("/password/change") -> HttpStatusCode.NoContent to ""
                else -> HttpStatusCode.ServiceUnavailable to ""
            }
        }

        val r = c.repo.changeOwnPassword("antiga", "novaSenha1").getOrThrow()

        assertEquals(PasswordChangeOutcome.SignInRequired, r)
        assertNull(c.tm.session.value)
        assertFalse(c.repo.isLoggedInSync)
    }

    @Test
    fun `changePassword do IAuthRepository deixou de ser nao suportado`() = runTest {
        val ok = logado { path ->
            if (path.endsWith("/password/change")) HttpStatusCode.NoContent to ""
            else HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r2")
        }
        assertTrue(ok.repo.changePassword("antiga", "novaSenha1").isSuccess)

        val recusa = logado { HttpStatusCode.Unauthorized to "" }
        assertIs<AuthException.InvalidCredentials>(recusa.repo.changePassword("x", "novaSenha1").exceptionOrNull())
    }

    @Test
    fun `sem sessao falha como NotAuthenticated sem chamar o servidor`() = runTest {
        val (api, cap) = mockOwnAuthApi { _, _ -> HttpStatusCode.NoContent to "" }
        val repo = EmailPasswordAuthRepository(api, OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now })

        assertIs<OwnAuthException.NotAuthenticated>(repo.changeOwnPassword("a", "b").exceptionOrNull())
        assertIs<OwnAuthException.NotAuthenticated>(repo.completeFirstAccess("b").exceptionOrNull())
        assertTrue(cap.isEmpty())
    }

    @Test
    fun `primeiro acesso adota os tokens novos preservando identificador e nome`() = runTest {
        val temporaria = """{"accessToken":"${fakeJwt("acc-9")}","refreshToken":"t1","expiresInSeconds":3600,"passwordChangeRequired":true}"""
        val c = logado(tokensDoLogin = temporaria) { path ->
            if (path.endsWith("/password/first-access")) HttpStatusCode.OK to tokensJson(fakeJwt("acc-9"), "t2")
            else HttpStatusCode.NotFound to ""
        }
        assertTrue(c.tm.session.value?.passwordChangeRequired == true)

        val user = c.repo.completeFirstAccess("minhaSenha1").getOrThrow()

        assertEquals("acc-9", user.id)
        assertEquals("t2", c.tm.session.value?.refreshToken)
        assertFalse(c.tm.session.value!!.passwordChangeRequired, "o diálogo de primeiro acesso fecha")
        assertEquals("ana@x.com", c.tm.session.value?.email)
        val chamada = c.captured.single { it.url.endsWith("/password/first-access") }
        assertFalse(chamada.body.contains("currentPassword"))
    }

    @Test
    fun `changeOwnPassword com senha temporaria desvia para o primeiro acesso`() = runTest {
        val temporaria = """{"accessToken":"${fakeJwt("acc-9")}","refreshToken":"t1","expiresInSeconds":3600,"passwordChangeRequired":true}"""
        val c = logado(tokensDoLogin = temporaria) { path ->
            if (path.endsWith("/password/first-access")) HttpStatusCode.OK to tokensJson(fakeJwt("acc-9"), "t2")
            else HttpStatusCode.NotFound to ""
        }

        val r = c.repo.changeOwnPassword("qualquer", "minhaSenha1").getOrThrow()

        assertIs<PasswordChangeOutcome.SessionRenewed>(r)
        assertTrue(c.captured.none { it.url.endsWith("/password/change") })
    }
}
