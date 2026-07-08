package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.firebase.auth.AuthException
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.firebase.auth.User
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDeletionServiceTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    /** Fake mínimo — só os membros usados pelo serviço têm comportamento; o resto é inerte. */
    private class FakeAuth(
        private val user: User?,
        private val deleteResult: Result<Unit> = Result.success(Unit),
    ) : IAuthRepository {
        var deleteCalled = false
        override val currentUserSync: User? = user
        override val currentUser: Flow<User?> = flowOf(user)
        override val isLoggedIn: Flow<Boolean> = flowOf(user != null)
        override val isLoggedInSync: Boolean = user != null
        override suspend fun deleteAccount(password: String?): Result<Unit> {
            deleteCalled = true
            return deleteResult
        }
        override suspend fun signInWithEmail(email: String, password: String): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<User> = Result.failure(NotImplementedError())
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> = Result.success(Unit)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut() {}
        override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
        override suspend fun getIdToken(forceRefresh: Boolean): Result<String> = Result.success("tok")
    }

    private fun api(responder: (method: HttpMethod, path: String) -> Pair<HttpStatusCode, String>): DomainApiClient {
        val engine = MockEngine { req ->
            val (status, body) = responder(req.method, req.url.encodedPath)
            respond(content = body, status = status, headers = jsonHeader)
        }
        val provider = DomainTokenProvider { _ -> "tok" }
        return DomainApiClient(HttpClient(engine), provider, "https://api.example.com")
    }

    private val user = User(id = "uid-1", email = "u@x.com")

    @Test
    fun `sem usuario autenticado falha sem chamar backend`() = runTest {
        var called = false
        val service = AccountDeletionService(
            api = api { _, _ -> called = true; HttpStatusCode.OK to "" },
            auth = FakeAuth(user = null),
        )
        val r = service.deleteAccountAndData()
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is AuthException.NotAuthenticated)
        assertTrue(!called)
    }

    @Test
    fun `wipe ok e conta excluida retorna Completed`() = runTest {
        val auth = FakeAuth(user = user)
        val service = AccountDeletionService(
            api = api { _, path -> assertEquals("/v1/me/data", path); HttpStatusCode.NoContent to "" },
            auth = auth,
        )
        val r = service.deleteAccountAndData()
        assertEquals(AccountDeletionResult.Completed, r.getOrNull())
        assertTrue(auth.deleteCalled)
    }

    @Test
    fun `falha no wipe aborta sem excluir a conta`() = runTest {
        val auth = FakeAuth(user = user)
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.InternalServerError to "boom" },
            auth = auth,
        )
        val r = service.deleteAccountAndData()
        assertTrue(r.isFailure)
        assertTrue(!auth.deleteCalled)
    }

    @Test
    fun `wipe ok mas re-login recente retorna DataWipedAccountPending`() = runTest {
        val auth = FakeAuth(user = user, deleteResult = Result.failure(AuthException.RequiresRecentLogin))
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.NoContent to "" },
            auth = auth,
        )
        val r = service.deleteAccountAndData()
        assertEquals(AccountDeletionResult.DataWipedAccountPending, r.getOrNull())
    }

    @Test
    fun `export devolve corpo do backend`() = runTest {
        val service = AccountDeletionService(
            api = api { _, path -> assertEquals("/v1/me/export", path); HttpStatusCode.OK to """{"tomadores":[]}""" },
            auth = FakeAuth(user = user),
        )
        val r = service.exportData()
        assertEquals("""{"tomadores":[]}""", r.getOrNull())
    }
}
