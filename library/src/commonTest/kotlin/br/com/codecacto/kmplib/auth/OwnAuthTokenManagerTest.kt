package br.com.codecacto.kmplib.auth

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnAuthTokenManagerTest {

    private val fixedNow = 1_700_000_000_000L // epoch millis fixo

    private fun manager(
        captured: MutableList<CapturedRequest> = mutableListOf(),
        refreshCounter: (() -> Unit)? = null,
        responder: (path: String, attempt: Int) -> Pair<HttpStatusCode, String>,
    ): OwnAuthTokenManager {
        val (api, _) = mockOwnAuthApi(captured) { path, attempt ->
            if (path.endsWith("refresh")) {
                refreshCounter?.invoke()
            }
            responder(path, attempt)
        }
        return OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), refreshSkewSeconds = 60) { fixedNow }
    }

    private fun session(access: String, refresh: String, expiresInSeconds: Long) = OwnAuthSession(
        accessToken = access,
        refreshToken = refresh,
        accessExpiresAtEpochSeconds = fixedNow / 1000 + expiresInSeconds,
        accountId = "acc-1",
        email = "a@x.com",
        name = "Ana",
    )

    @Test
    fun `adopt persiste sessao e decodifica accountId do sub`() = runTest {
        val mgr = manager { _, _ -> HttpStatusCode.OK to "" }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-999"), "r1", 3600), email = "a@x.com", name = "Ana")
        val s = mgr.session.value
        assertNotNull(s)
        assertEquals("acc-999", s.accountId)
        assertEquals("a@x.com", s.email)
    }

    @Test
    fun `access token valido nao dispara refresh`() = runTest {
        var refreshes = 0
        val mgr = manager(refreshCounter = { refreshes++ }) { _, _ -> HttpStatusCode.OK to "" }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 3600), "a@x.com", "Ana")
        val token = mgr.accessToken()
        assertEquals(0, refreshes)
        assertTrue(token!!.isNotBlank())
    }

    @Test
    fun `access token proximo de expirar dispara refresh rotativo`() = runTest {
        var refreshes = 0
        val mgr = manager(refreshCounter = { refreshes++ }) { path, _ ->
            if (path.endsWith("refresh")) HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r2-novo", 3600)
            else HttpStatusCode.OK to ""
        }
        // token expira em 10s (< skew 60) → precisa renovar
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1-velho", 10), "a@x.com", "Ana")
        val token = mgr.accessToken()
        assertEquals(1, refreshes)
        // rotativo: o refresh guardado passou a ser o novo
        assertEquals("r2-novo", mgr.session.value?.refreshToken)
        assertNotNull(token)
    }

    @Test
    fun `forceRefresh renova mesmo com token ainda valido`() = runTest {
        var refreshes = 0
        val mgr = manager(refreshCounter = { refreshes++ }) { path, _ ->
            if (path.endsWith("refresh")) HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-novo", 3600)
            else HttpStatusCode.OK to ""
        }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 3600), "a@x.com", "Ana")
        mgr.accessToken(forceRefresh = true)
        assertEquals(1, refreshes)
        assertEquals("r-novo", mgr.session.value?.refreshToken)
    }

    @Test
    fun `single-flight - dois accessToken concorrentes fazem so um refresh`() = runTest {
        var refreshes = 0
        val (api, _) = mockOwnAuthApi { path, _ ->
            if (path.endsWith("refresh")) {
                refreshes++
                HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-rotacionado", 3600)
            } else HttpStatusCode.OK to ""
        }
        // MockEngine não é suspendível aqui; então controlamos a corrida pela ordem de agendamento.
        val mgr = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { fixedNow }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 5), "a@x.com", "Ana") // expira já → renova

        val results = mutableListOf<String?>()
        val j1 = launch { results += mgr.accessToken() }
        val j2 = launch { results += mgr.accessToken() }
        advanceUntilIdle()
        j1.join(); j2.join()

        // Só UM refresh apesar das duas chamadas concorrentes (rotação preservada).
        assertEquals(1, refreshes)
        assertEquals("r-rotacionado", mgr.session.value?.refreshToken)
        assertEquals(2, results.size)
    }

    @Test
    fun `falha de rede no refresh preserva sessao e devolve token corrente`() = runTest {
        var refreshes = 0
        val mgr = manager(refreshCounter = { refreshes++ }) { path, _ ->
            if (path.endsWith("refresh")) throw RuntimeException("sem rede simulada")
            else HttpStatusCode.OK to ""
        }
        val staleAccess = fakeJwt("acc-1")
        mgr.adopt(OwnAuthTokens(staleAccess, "r1", 5), "a@x.com", "Ana") // expirando
        val token = mgr.accessToken()
        // rede caiu → sessão preservada, refresh guardado intacto, devolve o access corrente
        assertNotNull(mgr.session.value)
        assertEquals("r1", mgr.session.value?.refreshToken)
        assertEquals(staleAccess, token)
    }

    @Test
    fun `4xx no refresh derruba a sessao (fail-closed)`() = runTest {
        val mgr = manager { path, _ ->
            if (path.endsWith("refresh")) HttpStatusCode.Unauthorized to ""
            else HttpStatusCode.OK to ""
        }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-1"), "r1", 5), "a@x.com", "Ana")
        val token = mgr.accessToken()
        assertNull(token)
        assertNull(mgr.session.value)
    }

    @Test
    fun `accessToken sem sessao devolve null`() = runTest {
        val mgr = manager { _, _ -> HttpStatusCode.OK to "" }
        assertNull(mgr.accessToken())
    }

    @Test
    fun `restore semeia sessao do cofre`() = runTest {
        val storage = FakeSecureTokenStorage()
        val store = AuthSessionStore(storage)
        store.save(session(fakeJwt("acc-7"), "r1", 3600))
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.OK to "" }
        val mgr = OwnAuthTokenManager(api, store, 60) { fixedNow }
        val restored = mgr.restore()
        assertEquals("r1", restored?.refreshToken)
        assertNotNull(mgr.session.value)
    }
}
