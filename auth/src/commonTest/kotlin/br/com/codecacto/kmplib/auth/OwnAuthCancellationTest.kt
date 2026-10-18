package br.com.codecacto.kmplib.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Robustez da sessão frente a cancelamento e rede fora (2.304.1):
 * - a rotação do refresh se completa mesmo se quem pediu o token for cancelado no meio;
 * - o `signOut` limpa o aparelho ANTES de falar com o servidor e não espera a rede além do teto.
 */
class OwnAuthCancellationTest {

    private val now = 1_700_000_000_000L
    private val jsonHeaders = headersOf("Content-Type", "application/json")

    private fun api(engine: MockEngine): OwnAuthApi =
        OwnAuthApi(OwnAuthConfig(HttpClient(engine), baseUrl = "https://api.example.com", authBasePath = "/v1/auth"))

    // ---- 1. Rotação do refresh atômica frente ao cancelamento ------------------------------

    @Test
    fun `cancelar quem pediu o token no meio do refresh nao deixa o refresh antigo no cofre`() = runTest {
        val storage = FakeSecureTokenStorage()
        val refreshStarted = CompletableDeferred<Unit>()
        val serverRotated = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("refresh")) {
                refreshStarted.complete(Unit)
                // O servidor já rotacionou r1 → r2; a resposta só chega depois do cancelamento.
                serverRotated.await()
                respond(tokensJson(fakeJwt("acc-1"), "r2-novo"), HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val mgr = OwnAuthTokenManager(api(engine), AuthSessionStore(storage), 60) { now }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1-velho", 5), "a@x.com", "Ana") // expirando → renova

        val caller = launch { mgr.accessToken() }
        // O MockEngine responde por outro dispatcher: espera, em tempo real e com teto, o pedido sair.
        withContext(Dispatchers.Default) { withTimeout(5_000) { refreshStarted.await() } }

        caller.cancel() // a tela saiu no meio da renovação
        serverRotated.complete(Unit)

        // A rotação termina mesmo assim: o par novo chega à memória…
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { mgr.session.first { it?.refreshToken == "r2-novo" } }
        }
        caller.join()
        assertTrue(caller.isCancelled, "quem chamou continua livre para deixar de esperar")
        // …e ao cofre: o próximo refresh manda r2, não o r1 já revogado (que seria reuso).
        assertEquals("r2-novo", AuthSessionStore(storage).load()?.refreshToken)
        assertEquals("r2-novo", OwnAuthTokenManager(api(engine), AuthSessionStore(storage), 60) { now }
            .restore()?.refreshToken)
    }

    @Test
    fun `cancelar no meio de um refresh recusado ainda encerra a sessao no cofre`() = runTest {
        val storage = FakeSecureTokenStorage()
        val refreshStarted = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("refresh")) {
                refreshStarted.complete(Unit)
                answer.await()
                respond("", HttpStatusCode.Unauthorized, jsonHeaders)
            } else {
                respond("", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val mgr = OwnAuthTokenManager(api(engine), AuthSessionStore(storage), 60) { now }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 5), "a@x.com", "Ana")

        val caller = launch { mgr.accessToken() }
        withContext(Dispatchers.Default) { withTimeout(5_000) { refreshStarted.await() } }
        caller.cancel()
        answer.complete(Unit)

        withContext(Dispatchers.Default) { withTimeout(5_000) { mgr.session.first { it == null } } }
        caller.join()
        assertNull(AuthSessionStore(storage).load(), "refresh recusado é fail-closed também no cofre")
    }

    // ---- 2. signOut: aparelho primeiro, servidor depois ------------------------------------

    @Test
    fun `sem rede o signOut volta no teto curto com o cofre ja limpo`() = runTest {
        val storage = FakeSecureTokenStorage()
        var logoutCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("logout")) {
                logoutCalls++
                awaitCancellation() // rede pendurada: a resposta nunca chega
            } else {
                respond(tokensJson(fakeJwt("acc-1"), "r-abc"), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val api = api(engine)
        val tm = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("a@x.com", "p").getOrThrow()

        // Tempo VIRTUAL de propósito: o teto de 5 s corre no relógio do teste e o MockEngine nunca
        // responde — o signOut tem de voltar pelo teto, com o aparelho já limpo.
        val start = testScheduler.currentTime
        repo.signOut() // não lança, não espera a rede além do teto
        val elapsed = testScheduler.currentTime - start

        assertTrue(
            elapsed <= EmailPasswordAuthRepository.LOGOUT_REVOCATION_TIMEOUT_MILLIS,
            "o signOut esperou ${elapsed}ms; o teto é ${EmailPasswordAuthRepository.LOGOUT_REVOCATION_TIMEOUT_MILLIS}ms",
        )
        assertEquals(1, logoutCalls, "a revogação ainda é tentada")
        assertFalse(repo.isLoggedInSync)
        assertNull(repo.currentUser.first())
        assertNull(AuthSessionStore(storage).load(), "o par de tokens não fica no aparelho")
    }

    @Test
    fun `signOut publica o deslogado e limpa o cofre ANTES de chamar o servidor`() = runTest {
        val storage = FakeSecureTokenStorage()
        var loggedInWhenServerCalled: Boolean? = null
        var storedWhenServerCalled: OwnAuthSession? = null
        var revokedBody = ""
        lateinit var tm: OwnAuthTokenManager
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("logout")) {
                loggedInWhenServerCalled = tm.session.value != null
                storedWhenServerCalled = AuthSessionStore(storage).load()
                revokedBody = (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
                respond("", HttpStatusCode.NoContent, jsonHeaders)
            } else {
                respond(tokensJson(fakeJwt("acc-1"), "r-abc"), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val api = api(engine)
        tm = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("a@x.com", "p").getOrThrow()

        withContext(Dispatchers.Default) { repo.signOut() } // tempo real: ver nota no teste de teto

        assertEquals(false, loggedInWhenServerCalled, "o estado deslogado sai antes da rede")
        assertNull(storedWhenServerCalled, "o cofre é limpo antes da rede")
        assertTrue("r-abc" in revokedBody, "revoga o refresh que estava valendo")
    }

    @Test
    fun `signOut com erro no servidor nao lanca e deixa o aparelho limpo`() = runTest {
        val storage = FakeSecureTokenStorage()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("logout")) throw IllegalStateException("rede caiu no meio")
            respond(tokensJson(fakeJwt("acc-1"), "r-abc"), HttpStatusCode.OK, jsonHeaders)
        }
        val api = api(engine)
        val tm = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("a@x.com", "p").getOrThrow()

        repo.signOut() // sem runCatching: best-effort de verdade

        assertFalse(repo.isLoggedInSync)
        assertNull(AuthSessionStore(storage).load())
    }

    @Test
    fun `signOut antes do restore ainda revoga o refresh que estava no cofre`() = runTest {
        val storage = FakeSecureTokenStorage()
        val revokedBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("logout")) {
                revokedBodies += (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
            }
            respond("", HttpStatusCode.NoContent, jsonHeaders)
        }
        val api = api(engine)
        // Sessão gravada por um processo anterior; este ainda não chamou restore().
        AuthSessionStore(storage).save(
            OwnAuthSession(
                accessToken = fakeJwt("acc-1"),
                refreshToken = "r-do-cofre",
                accessExpiresAtEpochSeconds = now / 1000 + 3600,
                accountId = "acc-1",
                email = "a@x.com",
                name = "Ana",
            ),
        )
        val repo = EmailPasswordAuthRepository(api, OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now })

        withContext(Dispatchers.Default) { repo.signOut() }

        assertTrue(revokedBodies.single().contains("r-do-cofre"))
        assertNull(AuthSessionStore(storage).load())
    }

    @Test
    fun `signOut sem sessao nao chama o servidor`() = runTest {
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            respond("", HttpStatusCode.NoContent, jsonHeaders)
        }
        val api = api(engine)
        val repo = EmailPasswordAuthRepository(
            api,
            OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now },
        )
        repo.signOut()
        assertEquals(0, calls)
        assertFalse(repo.isLoggedInSync)
    }

    // ---- 3. signOut vence o refresh em voo --------------------------------------------------

    @Test
    fun `refresh em voo que termina DEPOIS do signOut nao grava tokens nem republica logado`() = runTest {
        val storage = FakeSecureTokenStorage()
        val refreshStarted = CompletableDeferred<Unit>()
        val serverRotated = CompletableDeferred<Unit>()
        val revoked = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("refresh") -> {
                    refreshStarted.complete(Unit)
                    serverRotated.await()
                    respond(tokensJson(fakeJwt("acc-1"), "r2-novo"), HttpStatusCode.OK, jsonHeaders)
                }
                path.endsWith("logout") -> {
                    revoked += (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
                    respond("", HttpStatusCode.NoContent, jsonHeaders)
                }
                else -> respond(tokensJson(fakeJwt("acc-1"), "r1-velho", 5), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val api = api(engine)
        val tm = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("a@x.com", "p").getOrThrow() // par que expira em 5 s → o próximo token renova

        val pending = launch { tm.accessToken() }
        withContext(Dispatchers.Default) { withTimeout(5_000) { refreshStarted.await() } }

        // "Sair" com o refresh pendurado na rede: não espera por ele.
        withContext(Dispatchers.Default) { withTimeout(5_000) { repo.signOut() } }
        assertFalse(repo.isLoggedInSync)
        assertNull(AuthSessionStore(storage).load())

        // O servidor responde agora com o par rotacionado.
        serverRotated.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { pending.join() } }

        assertNull(tm.session.value, "o refresh não republica estado logado")
        assertFalse(repo.isLoggedInSync)
        assertNull(AuthSessionStore(storage).load(), "o par novo não volta ao cofre")
        assertTrue(revoked.any { "r1-velho" in it }, "o signOut revoga o refresh que estava valendo")
        assertTrue(revoked.any { "r2-novo" in it }, "o par descartado também é revogado")
    }

    @Test
    fun `refresh recusado que volta depois de um login novo nao derruba a sessao nova`() = runTest {
        val storage = FakeSecureTokenStorage()
        val refreshStarted = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("refresh")) {
                refreshStarted.complete(Unit)
                answer.await()
                respond("", HttpStatusCode.Unauthorized, jsonHeaders)
            } else {
                respond("", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val tm = OwnAuthTokenManager(api(engine), AuthSessionStore(storage), 60) { now }
        tm.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 5), "a@x.com", "Ana")

        val pending = launch { tm.accessToken() }
        withContext(Dispatchers.Default) { withTimeout(5_000) { refreshStarted.await() } }
        tm.adopt(OwnAuthTokens(fakeJwt("acc-2"), "r-outra", 3600), "b@x.com", "Bia") // login novo
        answer.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { pending.join() } }

        assertEquals("r-outra", tm.session.value?.refreshToken)
        assertEquals("r-outra", AuthSessionStore(storage).load()?.refreshToken)
    }
}
