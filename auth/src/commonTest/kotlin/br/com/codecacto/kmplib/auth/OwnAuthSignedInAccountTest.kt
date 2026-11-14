package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.identity.SignedInAccount
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A sessão própria publica o id interno da conta para o relatório de erro (2.364.0). */
class OwnAuthSignedInAccountTest {

    private fun manager(
        storage: FakeSecureTokenStorage = FakeSecureTokenStorage(),
        responder: (path: String, attempt: Int) -> Pair<HttpStatusCode, String> = { _, _ -> HttpStatusCode.OK to "" },
    ): OwnAuthTokenManager {
        val (api, _) = mockOwnAuthApi(responder = responder)
        return OwnAuthTokenManager(api, AuthSessionStore(storage), refreshSkewSeconds = 60)
    }

    @Test
    fun `login publica o sub e logout limpa`() = runTest {
        SignedInAccount.publish(null)
        val mgr = manager()
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-7"), "r1", 3600), email = "ana@exemplo.com", name = "Ana")
        assertEquals("acc-7", SignedInAccount.id.value)
        mgr.clear()
        assertNull(SignedInAccount.id.value)
    }

    @Test
    fun `restaurar do cofre publica a conta`() = runTest {
        SignedInAccount.publish(null)
        val storage = FakeSecureTokenStorage()
        manager(storage).adopt(OwnAuthTokens(fakeJwt("acc-cofre"), "r1", 3600), "a@x.com", "Ana")
        SignedInAccount.publish(null) // processo novo
        val novo = manager(storage)
        novo.restore()
        assertEquals("acc-cofre", SignedInAccount.id.value)
        SignedInAccount.publish(null)
    }

    @Test
    fun `sub que e e-mail nao sai`() = runTest {
        SignedInAccount.publish(null)
        val mgr = manager()
        mgr.adopt(OwnAuthTokens(fakeJwt("ana@exemplo.com"), "r1", 3600), "ana@exemplo.com", "Ana")
        assertNull(SignedInAccount.id.value)
        mgr.clear()
    }

    @Test
    fun `refresh recusado derruba a sessao e limpa a conta`() = runTest {
        SignedInAccount.publish(null)
        val mgr = manager { path, _ ->
            if (path.endsWith("refresh")) HttpStatusCode.Unauthorized to """{"error":"invalid"}""" else HttpStatusCode.OK to ""
        }
        mgr.adopt(OwnAuthTokens(fakeJwt("acc-9"), "r1", 10), "a@x.com", "Ana")
        assertEquals("acc-9", SignedInAccount.id.value)
        mgr.accessToken()
        assertNull(mgr.session.value)
        assertNull(SignedInAccount.id.value)
    }
}
