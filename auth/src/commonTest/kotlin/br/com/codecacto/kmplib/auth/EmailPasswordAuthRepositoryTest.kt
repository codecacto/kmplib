package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailPasswordAuthRepositoryTest {

    private val now = 1_700_000_000_000L

    private fun repo(
        responder: (path: String, attempt: Int) -> Pair<HttpStatusCode, String>,
    ): EmailPasswordAuthRepository {
        val (api, _) = mockOwnAuthApi(responder = responder)
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        return EmailPasswordAuthRepository(api, tm)
    }

    @Test
    fun `login preenche currentUser com accountId do sub e provider password`() = runTest {
        val repo = repo { _, _ -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-42"), "r1") }
        val user = repo.signInWithEmail("ana@x.com", "senha").getOrThrow()
        assertEquals("acc-42", user.id)
        assertEquals("ana@x.com", user.email)
        assertEquals("password", user.providerId)
        assertTrue(repo.isLoggedInSync)
        assertEquals("acc-42", repo.currentUser.first()?.id)
    }

    @Test
    fun `register loga o usuario (adota sessao) com nome`() = runTest {
        val repo = repo { path, _ ->
            if (path.endsWith("register")) HttpStatusCode.Created to tokensJson(fakeJwt("acc-7"), "r1")
            else HttpStatusCode.OK to ""
        }
        val user = repo.register("Bruno", "b@x.com", "senha123", acceptedTerms = true).getOrThrow()
        assertEquals("acc-7", user.id)
        assertEquals("Bruno", user.displayName)
        assertTrue(repo.isLoggedInSync)
    }

    @Test
    fun `login 401 mapeia para AuthException InvalidCredentials`() = runTest {
        val repo = repo { _, _ -> HttpStatusCode.Unauthorized to "" }
        val e = repo.signInWithEmail("a@x.com", "errada").exceptionOrNull()
        assertIs<AuthException.InvalidCredentials>(e)
        assertFalse(repo.isLoggedInSync)
    }

    @Test
    fun `signOut limpa a sessao e chama logout`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { path, _ ->
            if (path.endsWith("logout")) HttpStatusCode.NoContent to ""
            else HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-abc")
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("a@x.com", "p")
        repo.signOut()
        assertFalse(repo.isLoggedInSync)
        assertNull(repo.currentUser.first())
        assertTrue(cap.any { it.url.endsWith("logout") && "r-abc" in it.body })
    }

    @Test
    fun `getIdToken devolve access token quando logado e NotAuthenticated quando nao`() = runTest {
        val repo = repo { _, _ -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r1") }
        assertIs<AuthException.NotAuthenticated>(repo.getIdToken().exceptionOrNull())
        repo.signInWithEmail("a@x.com", "p")
        assertTrue(repo.getIdToken().getOrThrow().isNotBlank())
    }

    @Test
    fun `sendPasswordResetEmail delega para forgot`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ -> HttpStatusCode.OK to "{}" }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        assertTrue(repo.sendPasswordResetEmail("a@x.com").isSuccess)
        assertTrue(cap.any { it.url.endsWith("password/forgot") })
    }

    @Test
    fun `operacoes sem endpoint falham explicitamente`() = runTest {
        val repo = repo { _, _ -> HttpStatusCode.OK to "" }
        assertTrue(repo.signUpWithEmail("a@x.com", "p", "Nome").isFailure)
        assertTrue(repo.signInWithGoogle("id").isFailure)
        assertTrue(repo.signInWithApple("id", "nonce").isFailure)
        assertTrue(repo.deleteAccount().isFailure)
        assertTrue(repo.sendEmailVerification().isFailure)
    }
}
