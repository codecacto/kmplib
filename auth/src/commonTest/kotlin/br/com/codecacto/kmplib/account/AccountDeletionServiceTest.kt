package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.storage.AccountLocalDataPurger
import br.com.codecacto.kmplib.core.storage.LocalPendingChanges
import br.com.codecacto.kmplib.core.storage.LocalPurgeReport
import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
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
import kotlin.test.assertFalse
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
        var signOutCalled = false
        override suspend fun signOut() { signOutCalled = true }
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

    /**
     * **own-auth**: a credencial mora na mesma base que o wipe apaga. O `deleteAccount` do
     * `EmailPasswordAuthRepository` responde `UnsupportedOperation` de propósito — e traduzir isso
     * em `DataWipedAccountPending` fazia o app pedir um segundo login para remover um login que já
     * não existe.
     */
    @Test
    fun `own-auth - wipe apaga a credencial, encerra a sessao e retorna Completed`() = runTest {
        val auth = FakeAuth(user, deleteResult = Result.failure(AuthException.UnknownError("unsupported")))
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.NoContent to "" },
            auth = auth,
            credencialSaiNoWipe = true,
        )

        val r = service.deleteAccountAndData()

        assertEquals(AccountDeletionResult.Completed, r.getOrNull())
        assertTrue(auth.signOutCalled, "a sessão local precisa cair")
        assertFalse(auth.deleteCalled, "não há IdP externo a chamar em own-auth")
    }
    /** Registra a ordem das chamadas: a limpeza local vem DEPOIS de a sessão cair. */
    private class Purger(private val eventos: MutableList<String>, private val falha: Throwable? = null) :
        AccountLocalDataPurger {
        override suspend fun pendingChanges() = LocalPendingChanges.NONE
        override suspend fun purgeAccount(): LocalPurgeReport {
            eventos += "purge"
            falha?.let { throw it }
            return LocalPurgeReport()
        }
        override suspend fun purgeOnSignOut(pending: SignOutPendingPolicy) = LocalPurgeReport()
    }

    @Test
    fun `wipe ok apaga o dado local da conta depois de encerrar a sessao`() = runTest {
        val eventos = mutableListOf<String>()
        val auth = object : IAuthRepository by FakeAuth(user) {
            override suspend fun signOut() { eventos += "signOut" }
        }
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.NoContent to "" },
            auth = auth,
            credencialSaiNoWipe = true,
            localData = Purger(eventos),
        )

        val r = service.deleteAccountAndData()

        assertEquals(AccountDeletionResult.Completed, r.getOrNull())
        assertEquals(listOf("signOut", "purge"), eventos)
    }

    @Test
    fun `wipe falhou - o dado local da conta NAO e apagado`() = runTest {
        val eventos = mutableListOf<String>()
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.InternalServerError to """{"error":"x"}""" },
            auth = FakeAuth(user),
            localData = Purger(eventos),
        )

        assertTrue(service.deleteAccountAndData().isFailure)
        assertTrue(eventos.isEmpty(), "a conta continua existindo; a fila offline dela também")
    }

    @Test
    fun `re-login pendente tambem limpa o aparelho - os dados do servidor ja sairam`() = runTest {
        val eventos = mutableListOf<String>()
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.NoContent to "" },
            auth = FakeAuth(user, deleteResult = Result.failure(AuthException.RequiresRecentLogin)),
            localData = Purger(eventos),
        )

        assertEquals(AccountDeletionResult.DataWipedAccountPending, service.deleteAccountAndData().getOrNull())
        assertEquals(listOf("purge"), eventos)
    }

    @Test
    fun `falha na limpeza local nao desfaz o resultado da exclusao`() = runTest {
        val eventos = mutableListOf<String>()
        val service = AccountDeletionService(
            api = api { _, _ -> HttpStatusCode.NoContent to "" },
            auth = FakeAuth(user),
            credencialSaiNoWipe = true,
            localData = Purger(eventos, falha = IllegalStateException("disco")),
        )

        assertEquals(AccountDeletionResult.Completed, service.deleteAccountAndData().getOrNull())
    }
}
