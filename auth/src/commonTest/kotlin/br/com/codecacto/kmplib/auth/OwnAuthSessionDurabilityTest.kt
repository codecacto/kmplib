package br.com.codecacto.kmplib.auth

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A sessão sobrevive a fechar e reabrir o app (2.292.0) — o caso do Meu Advogado em que a 2ª
 * reabertura seguida caía no login.
 *
 * "Reabrir" aqui é uma instância NOVA de [OwnAuthTokenManager] sobre o MESMO cofre: é o que sobra de
 * um processo morto (só o que está no disco). O cofre do Android passou a gravar com `commit()` e o
 * do iOS a trocar o item no lugar; estes testes travam o contrato do lado comum — o par renovado é
 * gravado ANTES de ser usado, a reabertura apresenta o par NOVO (o velho seria reuso e derrubaria a
 * família), renovações concorrentes no boot fazem um refresh só e falha de rede não apaga nada.
 */
class OwnAuthSessionDurabilityTest {

    private val fixedNow = 1_700_000_000_000L

    private fun access(n: Int) = fakeJwt("acc-1").replace("signature", "sig$n")

    private fun refreshBodies(captured: List<CapturedRequest>): List<String> =
        captured.filter { it.url.endsWith("refresh") }.map { req ->
            Regex("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"").find(req.body)!!.groupValues[1]
        }

    /** Servidor que rotaciona: cada refresh devolve r(N+1) e conta as chamadas. */
    private class RotatingServer {
        var refreshes = 0
        var failWithNetwork = false
        val responder: (String, Int) -> Pair<HttpStatusCode, String> = { path, _ ->
            if (path.endsWith("refresh")) {
                if (failWithNetwork) throw RuntimeException("sem rede simulada")
                refreshes++
                val n = refreshes + 1
                HttpStatusCode.OK to tokensJson(
                    fakeJwt("acc-1").replace("signature", "sig$n"),
                    "r$n",
                    3600,
                )
            } else {
                HttpStatusCode.OK to ""
            }
        }
    }

    private fun expiredSession(refresh: String) = OwnAuthSession(
        accessToken = access(1),
        refreshToken = refresh,
        accessExpiresAtEpochSeconds = fixedNow / 1000 - 600, // venceu há 10 min
        accountId = "acc-1",
        email = "a@x.com",
        name = "Ana",
    )

    @Test
    fun `reabrir depois de um refresh apresenta o par NOVO ao servidor`() = runTest {
        val disk = FakeSecureTokenStorage()
        val server = RotatingServer()
        val captured = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(captured, responder = server.responder)

        // 1ª vida do processo: login com access quase vencido → renova r1 → r2.
        val first = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        first.adopt(OwnAuthTokens(access(1), "r1", 5), "a@x.com", "Ana")
        first.accessToken()

        // Processo morto. 2ª vida: só o disco sobrou.
        val second = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        assertEquals("r2", second.restore()?.refreshToken, "a reabertura leu o par velho do cofre")
        second.accessToken(forceRefresh = true)

        // 3ª vida.
        val third = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        assertEquals("r3", third.restore()?.refreshToken)

        // Nenhum refresh token foi apresentado duas vezes (seria reuso → família revogada).
        assertEquals(listOf("r1", "r2"), refreshBodies(captured))
    }

    @Test
    fun `o token renovado so e devolvido e publicado DEPOIS de o cofre gravar`() = runTest {
        val disk = SlowWriteStorage()
        val server = RotatingServer()
        val (api, _) = mockOwnAuthApi(responder = server.responder)
        val mgr = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        mgr.adopt(OwnAuthTokens(access(1), "r1", 5), "a@x.com", "Ana")

        disk.armed = true
        val call = async { mgr.accessToken() }
        // O MockEngine responde por outro dispatcher: espera, em tempo real e com teto, a gravação começar.
        withContext(Dispatchers.Default) { withTimeout(5_000) { disk.writeStarted.await() } }
        runCurrent()

        assertFalse(call.isCompleted, "o token novo foi entregue antes de o par estar no cofre")
        assertEquals("r1", mgr.session.value?.refreshToken, "o par novo foi publicado antes de gravado")

        disk.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(access(2), call.await())
        assertEquals("r2", mgr.session.value?.refreshToken)
        assertEquals("r2", AuthSessionStore(disk).load()?.refreshToken)
    }

    @Test
    fun `boot com sessao vencida - tres pedidos concorrentes fazem UM refresh`() = runTest {
        val disk = FakeSecureTokenStorage()
        AuthSessionStore(disk).save(expiredSession("r1"))
        val server = RotatingServer()
        val captured = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(captured, responder = server.responder)

        // Processo novo: nada em memória. Home, perfil e lista pedem token ao mesmo tempo.
        val mgr = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        val results = mutableListOf<String?>()
        val jobs = List(3) { launch { results += mgr.accessToken() } }
        advanceUntilIdle()
        jobs.forEach { it.join() }

        assertEquals(1, server.refreshes)
        assertEquals(listOf("r1"), refreshBodies(captured))
        assertEquals<List<String?>>(List(3) { access(2) }, results)
        assertEquals("r2", AuthSessionStore(disk).load()?.refreshToken)
    }

    @Test
    fun `falha de rede no refresh do boot nao desloga nem apaga o cofre`() = runTest {
        val disk = FakeSecureTokenStorage()
        AuthSessionStore(disk).save(expiredSession("r1"))
        val server = RotatingServer().apply { failWithNetwork = true }
        val (api, _) = mockOwnAuthApi(responder = server.responder)

        val mgr = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        val token = mgr.accessToken()

        assertEquals(access(1), token, "sem rede, a chamada segue com o access corrente")
        assertNotNull(mgr.session.value, "falha de rede deslogou")
        // A próxima abertura continua logada, com o MESMO refresh (que o servidor nunca consumiu).
        val reopened = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        assertEquals("r1", reopened.restore()?.refreshToken)
    }

    @Test
    fun `gravacao que falha nao derruba a sessao em memoria`() = runTest {
        val disk = SlowWriteStorage().apply { failWrites = true }
        val server = RotatingServer()
        val (api, _) = mockOwnAuthApi(responder = server.responder)
        val mgr = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        // Semeia pelo cofre (a gravação falha a partir daqui, como um disco cheio).
        disk.failWrites = false
        mgr.adopt(OwnAuthTokens(access(1), "r1", 5), "a@x.com", "Ana")
        disk.failWrites = true

        val token = mgr.accessToken()

        // O servidor já rotacionou: o par antigo morreu. O processo segue com o novo em memória.
        assertEquals(access(2), token)
        assertEquals("r2", mgr.session.value?.refreshToken)
        assertTrue(disk.failedWrites > 0)
    }

    @Test
    fun `logout cuja remocao falha limpa o cofre inteiro - fail-closed`() = runTest {
        val disk = SlowWriteStorage()
        val (api, _) = mockOwnAuthApi(responder = RotatingServer().responder)
        val mgr = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        mgr.adopt(OwnAuthTokens(access(1), "r1", 3600), "a@x.com", "Ana")

        disk.failRemoves = true
        mgr.clear()

        val reopened = OwnAuthTokenManager(api, AuthSessionStore(disk), 60) { fixedNow }
        assertEquals(null, reopened.restore(), "a sessão ressuscitou depois do logout")
    }

    /** Cofre cuja gravação, com [armed], só termina quando [gate] abre; ou que falha com [failWrites]. */
    private class SlowWriteStorage : SecureTokenStorage {
        private val delegate = FakeSecureTokenStorage()
        var armed = false
        var failWrites = false
        var failRemoves = false
        var failedWrites = 0
        val writeStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun getString(key: String): String? = delegate.getString(key)
        override suspend fun putString(key: String, value: String) {
            if (failWrites) {
                failedWrites++
                throw IllegalStateException("disco cheio simulado")
            }
            if (armed) {
                writeStarted.complete(Unit)
                gate.await()
            }
            delegate.putString(key, value)
        }
        override suspend fun remove(key: String) {
            if (failRemoves) throw IllegalStateException("remoção recusada (simulado)")
            delegate.remove(key)
        }
        override suspend fun clear() = delegate.clear()
    }
}
