package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.network.ReauthRequiredException
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 2.301.0 — exclusão com carência: as três chamadas do contrato ([RestAccountDeletionRepository]) e o
 * pedido pela tela de conta ([AccountDeletionService.scheduleAccountDeletion]).
 */
class AccountScheduledDeletionTest {

    private data class Chamada(val method: HttpMethod, val path: String, val body: String?)

    private class Servidor(val responder: (HttpMethod, String) -> Pair<HttpStatusCode, String>) {
        val chamadas = mutableListOf<Chamada>()
    }

    private fun api(servidor: Servidor): DomainApiClient {
        val engine = MockEngine { req ->
            servidor.chamadas += Chamada(req.method, req.url.encodedPath, (req.body as? TextContent)?.text)
            val (status, body) = servidor.responder(req.method, req.url.encodedPath)
            respond(body, status, headersOf("Content-Type", "application/json"))
        }
        return DomainApiClient(HttpClient(engine), DomainTokenProvider { "tok" }, "https://api.example.com")
    }

    private val pendente202 =
        """{"status":"PENDING_DELETION","requestedAt":"2026-10-14T12:00:00Z","scheduledFor":"2026-11-13T12:00:00Z","daysRemaining":30}"""

    // ---------------------------------------------------------------- caminhos

    @Test
    fun caminhos_derivadosDoMe() {
        val p = AccountDeletionPaths.forMe("/app/v1/me/data/")
        assertEquals("/app/v1/me/data/", p.request)
        assertEquals("/app/v1/me/data/deletion", p.status)
        assertEquals("/app/v1/me/data/deletion/restore", p.restore)
        assertEquals("/v1/me/data/deletion", AccountDeletionPaths.forMe().status)
        val proprio = AccountDeletionPaths(request = "/v1/conta", status = "/v1/conta/situacao", restore = "/v1/conta/volta")
        assertEquals("/v1/conta/volta", proprio.restore)
    }

    // ---------------------------------------------------------------- repositório

    @Test
    fun pedido_delete202_comConfirmacaoNoCorpo_naoMexeNoEstado() = runTest {
        val st = AccountDeletionState()
        val srv = Servidor { _, _ -> HttpStatusCode.Accepted to pendente202 }
        val repo = RestAccountDeletionRepository(api(srv), AccountDeletionPaths.forMe("/app/v1/me/data"), st)
        val r = repo.requestDeletion(" excluir ").getOrThrow()
        assertTrue(r.pending)
        assertEquals(30, r.daysRemaining)
        val c = srv.chamadas.single()
        assertEquals(HttpMethod.Delete, c.method)
        assertEquals("/app/v1/me/data", c.path)
        assertEquals("""{"confirmacao":"excluir"}""", c.body)
        assertFalse(st.isPending, "quem pediu está saindo: o portão não cobre a tela de conta")
    }

    @Test
    fun pedido_202SemCorpo_aindaEPendente() = runTest {
        val repo = RestAccountDeletionRepository(api(Servidor { _, _ -> HttpStatusCode.Accepted to "" }), state = AccountDeletionState())
        assertTrue(repo.requestDeletion().getOrThrow().pending)
    }

    @Test
    fun pedido_falha_eReauth() = runTest {
        val falha = RestAccountDeletionRepository(
            api(Servidor { _, _ -> HttpStatusCode.InternalServerError to """{"code":"X"}""" }),
            state = AccountDeletionState(),
        ).requestDeletion("EXCLUIR")
        val e = assertIs<AccountDeletionRequestException>(falha.exceptionOrNull())
        assertEquals(500, e.httpCode)
        assertFalse(e.isOffline)

        val reauth = RestAccountDeletionRepository(
            api(Servidor { _, _ -> HttpStatusCode.Unauthorized to """{"code":"REAUTH_REQUIRED","details":{"maxAgeSeconds":"300"}}""" }),
            state = AccountDeletionState(),
        ).requestDeletion("EXCLUIR")
        assertIs<ReauthRequiredException>(reauth.exceptionOrNull())
    }

    @Test
    fun consulta_pendenteCobre_naoPendenteLimpa() = runTest {
        val st = AccountDeletionState()
        var pendente = true
        val srv = Servidor { m, _ ->
            assertEquals(HttpMethod.Get, m)
            HttpStatusCode.OK to if (pendente) """{"pending":true,"daysRemaining":9}""" else """{"pending":false}"""
        }
        val repo = RestAccountDeletionRepository(api(srv), state = st)
        assertEquals(9, repo.deletionStatus().getOrThrow().daysRemaining)
        assertEquals(9, st.pending.value?.daysRemaining)
        assertEquals("/v1/me/data/deletion", srv.chamadas.last().path)
        pendente = false
        assertFalse(repo.deletionStatus().getOrThrow().pending)
        assertFalse(st.isPending)
    }

    @Test
    fun consulta_403DoPortaoEhAResposta() = runTest {
        val st = AccountDeletionState()
        val srv = Servidor { _, _ ->
            HttpStatusCode.Forbidden to """{"code":"ACCOUNT_PENDING_DELETION","details":{"daysRemaining":4,"scheduledFor":"2026-10-18T00:00:00Z"}}"""
        }
        val r = RestAccountDeletionRepository(api(srv), state = st).deletionStatus().getOrThrow()
        assertTrue(r.pending)
        assertEquals(4, r.daysRemaining)
        assertEquals("2026-10-18T00:00:00Z", st.pending.value?.scheduledFor)
    }

    @Test
    fun consulta_ilegivelFalha_semMexerNoEstado() = runTest {
        val st = AccountDeletionState()
        val r = RestAccountDeletionRepository(api(Servidor { _, _ -> HttpStatusCode.OK to "<html/>" }), state = st)
            .deletionStatus()
        assertTrue(r.isFailure)
        assertFalse(st.isPending)
    }

    @Test
    fun restaurar_limpaOEstado() = runTest {
        val st = AccountDeletionState()
        st.report(AccountDeletionStatus(pending = true, daysRemaining = 3))
        val srv = Servidor { m, _ ->
            assertEquals(HttpMethod.Post, m)
            HttpStatusCode.OK to """{"pending":false}"""
        }
        val r = RestAccountDeletionRepository(api(srv), AccountDeletionPaths.forMe("/app/v1/me/data"), st).restore()
        assertFalse(r.getOrThrow().pending)
        assertFalse(st.isPending)
        assertEquals("/app/v1/me/data/deletion/restore", srv.chamadas.single().path)
    }

    @Test
    fun restaurar_falhaMantemOBloqueio() = runTest {
        val st = AccountDeletionState()
        st.report(AccountDeletionStatus(pending = true))
        val r = RestAccountDeletionRepository(api(Servidor { _, _ -> HttpStatusCode.ServiceUnavailable to "" }), state = st)
            .restore()
        assertIs<AccountDeletionRequestException>(r.exceptionOrNull())
        assertTrue(st.isPending)
    }

    // ---------------------------------------------------------------- serviço (tela de conta)

    private class FakeAuth(private val user: User?, private val signOutFails: Boolean = false) : IAuthRepository {
        var deleteCalled = false
        var signOutCalled = false
        override val currentUserSync: User? = user
        override val currentUser: Flow<User?> = flowOf(user)
        override val isLoggedIn: Flow<Boolean> = flowOf(user != null)
        override val isLoggedInSync: Boolean = user != null
        override suspend fun deleteAccount(password: String?): Result<Unit> { deleteCalled = true; return Result.success(Unit) }
        override suspend fun signInWithEmail(email: String, password: String): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> = Result.failure(NotImplementedError())
        override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<User> = Result.failure(NotImplementedError())
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> = Result.success(Unit)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut() {
            signOutCalled = true
            if (signOutFails) error("cofre indisponível")
        }
        override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)
        override suspend fun getIdToken(forceRefresh: Boolean): Result<String> = Result.success("tok")
    }

    private val user = User(id = "uid-1", email = "u@x.com")

    @Test
    fun servico_programa_saiDaSessao_eNuncaApagaACredencial() = runTest {
        val st = AccountDeletionState()
        st.report(AccountDeletionStatus(pending = true)) // um 403 de outra tela chegou antes do signOut
        val srv = Servidor { _, _ -> HttpStatusCode.Accepted to pendente202 }
        val auth = FakeAuth(user)
        val service = AccountDeletionService(
            api = api(srv),
            auth = auth,
            deletionRepository = RestAccountDeletionRepository(api(srv), state = st),
            deletionState = st,
            clearSharedFiles = false,
        )
        val r = service.scheduleAccountDeletion("EXCLUIR").getOrThrow()
        assertEquals("2026-11-13T12:00:00Z", r.status.scheduledFor)
        assertTrue(r.signedOut)
        assertTrue(auth.signOutCalled, "a sessão local sai")
        assertFalse(auth.deleteCalled, "a credencial fica: é com ela que se entra para restaurar")
        assertFalse(st.isPending, "o login seguinte não herda a cobertura")
        assertEquals(listOf(HttpMethod.Delete), srv.chamadas.map { it.method }, "nenhum wipe imediato")
    }

    @Test
    fun servico_signOutFalhaDepoisDo202_cobreComOPortao_eAvisaATela() = runTest {
        val st = AccountDeletionState()
        val srv = Servidor { _, _ -> HttpStatusCode.Accepted to pendente202 }
        val auth = FakeAuth(user, signOutFails = true)
        val r = AccountDeletionService(
            api = api(srv),
            auth = auth,
            deletionRepository = RestAccountDeletionRepository(api(srv), state = st),
            deletionState = st,
            clearSharedFiles = false,
        ).scheduleAccountDeletion("EXCLUIR").getOrThrow()
        assertFalse(r.signedOut, "a tela não pode fingir que voltou ao login")
        assertTrue(st.isPending, "a sessão ficou: o portão cobre a área logada (Restaurar/Sair)")
        assertEquals(30, st.pending.value?.daysRemaining)
        assertFalse(auth.deleteCalled)
    }

    @Test
    fun servico_falhaNoPedido_preservaASessao() = runTest {
        val srv = Servidor { _, _ -> HttpStatusCode.InternalServerError to "" }
        val auth = FakeAuth(user)
        val service = AccountDeletionService(
            api = api(srv),
            auth = auth,
            deletionRepository = RestAccountDeletionRepository(api(srv), state = AccountDeletionState()),
            deletionState = AccountDeletionState(),
            clearSharedFiles = false,
        )
        val r = service.scheduleAccountDeletion("EXCLUIR")
        assertTrue(r.isFailure)
        assertFalse(r.exceptionOrNull()?.message.isNullOrBlank(), "frase para a tela, nunca vazia")
        assertFalse(auth.signOutCalled)
    }

    @Test
    fun servico_semRepositorio_ouSemSessao_falha() = runTest {
        val srv = Servidor { _, _ -> HttpStatusCode.Accepted to pendente202 }
        val semRepo = AccountDeletionService(api = api(srv), auth = FakeAuth(user)).scheduleAccountDeletion("EXCLUIR")
        assertIs<IllegalStateException>(semRepo.exceptionOrNull())
        val semSessao = AccountDeletionService(
            api = api(srv),
            auth = FakeAuth(null),
            deletionRepository = RestAccountDeletionRepository(api(srv), state = AccountDeletionState()),
        ).scheduleAccountDeletion("EXCLUIR")
        assertIs<AuthException.NotAuthenticated>(semSessao.exceptionOrNull())
        assertTrue(srv.chamadas.isEmpty(), "nada chegou ao servidor")
    }

    @Test
    fun servico_reauthSobeTipado() = runTest {
        val srv = Servidor { _, _ -> HttpStatusCode.Unauthorized to """{"code":"REAUTH_REQUIRED"}""" }
        val auth = FakeAuth(user)
        val r = AccountDeletionService(
            api = api(srv),
            auth = auth,
            deletionRepository = RestAccountDeletionRepository(api(srv), state = AccountDeletionState()),
            deletionState = AccountDeletionState(),
        ).scheduleAccountDeletion("EXCLUIR")
        assertIs<ReauthRequiredException>(r.exceptionOrNull())
        assertFalse(auth.signOutCalled)
    }
}
