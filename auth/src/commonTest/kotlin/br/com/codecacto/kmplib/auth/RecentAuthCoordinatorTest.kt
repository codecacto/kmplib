package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.auth.social.SocialBrowserException
import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step-up de autenticação (2.261.0): o contrato do `requireRecentAuth()` da backlib, do lado do app.
 * Não desloga, confere a MESMA conta antes de trocar os tokens, repete UMA vez e nunca gira em laço.
 */
class RecentAuthCoordinatorTest {

    private val now = 1_700_000_000_000L

    private class Cenario(
        val coordinator: RecentAuthCoordinator,
        val tm: OwnAuthTokenManager,
        val captured: MutableList<CapturedRequest>,
    ) {
        fun logouts(): List<String> = captured.filter { it.url.endsWith("/logout") }.map { it.body }
        fun count(suffix: String): Int = captured.count { it.url.endsWith(suffix) }
    }

    /**
     * Sessão de `acc-1` (refresh `r1`). [reloginTokens] é a resposta dos logins SEGUINTES (a
     * reautenticação); `null` = senha errada (401).
     */
    private suspend fun logado(
        reloginTokens: List<String?> = listOf(tokensJson(fakeJwt("acc-1"), "r2")),
        social: SocialReauthenticator? = null,
        socialProviders: Set<SocialProvider> = emptySet(),
        maxAttempts: Int = RecentAuthCoordinator.DEFAULT_MAX_ATTEMPTS,
    ): Cenario {
        var logins = 0
        val (api, cap) = mockOwnAuthApi { path, _ ->
            when {
                path.endsWith("/login") -> {
                    logins++
                    if (logins == 1) {
                        HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r1")
                    } else {
                        val resposta = reloginTokens[minOf(logins - 2, reloginTokens.lastIndex)]
                        if (resposta == null) {
                            HttpStatusCode.Unauthorized to """{"message":"x","code":"INVALID_CREDENTIALS"}"""
                        } else {
                            HttpStatusCode.OK to resposta
                        }
                    }
                }
                path.endsWith("/logout") -> HttpStatusCode.NoContent to ""
                path.endsWith("/refresh") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "rx")
                else -> HttpStatusCode.NotFound to """{"message":"nf"}"""
            }
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        EmailPasswordAuthRepository(api, tm).signInWithEmail("ana@x.com", "senha").getOrThrow()
        val coordinator = RecentAuthCoordinator(api, tm, social, socialProviders, maxAttempts)
        return Cenario(coordinator, tm, cap)
    }

    private class Prompt(private val respostas: List<ReauthCredential?>) : ReauthPrompt {
        val pedidos = mutableListOf<ReauthRequest>()
        override suspend fun requestCredential(request: ReauthRequest): ReauthCredential? {
            pedidos += request
            return respostas[minOf(pedidos.size - 1, respostas.lastIndex)]
        }
    }

    private fun reauth() = Result.failure<String>(ReauthRequiredException(maxAgeSeconds = 300))

    @Test
    fun `mesma conta adota os tokens novos e repete a acao uma vez`() = runTest {
        val c = logado()
        val prompt = Prompt(listOf(ReauthCredential.Password("senha")))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) {
            chamadas++
            if (chamadas == 1) reauth() else Result.success("apagado")
        }

        assertEquals("apagado", r.getOrThrow())
        assertEquals(2, chamadas, "repete UMA vez")
        assertEquals(1, prompt.pedidos.size)
        val pedido = prompt.pedidos.single()
        assertEquals("ana@x.com", pedido.identifier)
        assertEquals(300L, pedido.maxAgeSeconds)
        assertNull(pedido.previousError)
        assertEquals("r2", c.tm.session.value?.refreshToken, "tokens novos adotados")
        assertEquals("ana@x.com", c.tm.session.value?.email, "identidade da sessão preservada")
        assertEquals(1, c.logouts().size, "a família antiga é revogada")
        assertTrue(c.logouts().single().contains("r1"))
        assertEquals(0, c.count("/refresh"), "reautenticar não passa por refresh")
    }

    @Test
    fun `401 REAUTH nao desloga nem chama refresh mesmo quando a pessoa desiste`() = runTest {
        val c = logado()
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(Prompt(listOf(null))) { chamadas++; reauth() }

        assertIs<ReauthCancelledException>(r.exceptionOrNull())
        assertTrue(r.exceptionOrNull()!!.isReauthCancelled())
        assertEquals(1, chamadas, "desistiu = a ação não roda de novo")
        assertEquals("r1", c.tm.session.value?.refreshToken, "a sessão continua de pé")
        assertEquals(0, c.count("/refresh"))
        assertEquals(0, c.count("/logout"))
    }

    @Test
    fun `outra conta aborta descarta e revoga os tokens novos`() = runTest {
        val c = logado(reloginTokens = listOf(tokensJson(fakeJwt("acc-2"), "r-outra")))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Password("senha-da-outra")))) {
            chamadas++
            reauth()
        }

        assertIs<ReauthAccountMismatchException>(r.exceptionOrNull())
        assertEquals(1, chamadas, "a ação NÃO é repetida com a conta de outra pessoa")
        assertEquals("r1", c.tm.session.value?.refreshToken, "a sessão do titular fica intacta")
        assertEquals("acc-1", c.tm.session.value?.accountId)
        assertEquals(1, c.logouts().size)
        assertTrue(c.logouts().single().contains("r-outra"), "os tokens da outra conta são revogados")
    }

    @Test
    fun `segunda recusa nao entra em laco`() = runTest {
        val c = logado()
        val prompt = Prompt(listOf(ReauthCredential.Password("senha")))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) { chamadas++; reauth() }

        assertIs<ReauthRequiredException>(r.exceptionOrNull())
        assertEquals(2, chamadas, "uma execução + UMA repetição, e para")
        assertEquals(1, prompt.pedidos.size, "não pede a senha de novo depois da repetição")
    }

    @Test
    fun `senha errada pergunta de novo com o erro e a sessao nao muda`() = runTest {
        val c = logado(reloginTokens = listOf(null, tokensJson(fakeJwt("acc-1"), "r3")))
        val prompt = Prompt(listOf(ReauthCredential.Password("errada"), ReauthCredential.Password("certa")))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) {
            chamadas++
            if (chamadas == 1) reauth() else Result.success("ok")
        }

        assertEquals("ok", r.getOrThrow())
        assertEquals(2, prompt.pedidos.size)
        assertIs<OwnAuthException.InvalidCredentials>(prompt.pedidos[1].previousError)
        assertEquals(2, prompt.pedidos[1].attempt)
        assertEquals("r3", c.tm.session.value?.refreshToken)
    }

    @Test
    fun `tentativas esgotadas devolvem o ultimo erro sem deslogar`() = runTest {
        val c = logado(reloginTokens = listOf(null), maxAttempts = 2)
        val prompt = Prompt(listOf(ReauthCredential.Password("errada")))

        val r = c.coordinator.withRecentAuth(prompt) { reauth() }

        assertIs<OwnAuthException.InvalidCredentials>(r.exceptionOrNull())
        assertEquals(2, prompt.pedidos.size)
        assertEquals("r1", c.tm.session.value?.refreshToken)
    }

    @Test
    fun `erro que nao e reauth atravessa sem perguntar nada`() = runTest {
        val c = logado()
        val prompt = Prompt(listOf(ReauthCredential.Password("senha")))

        val r = c.coordinator.withRecentAuth(prompt) { Result.failure<String>(IllegalStateException("500")) }

        assertIs<IllegalStateException>(r.exceptionOrNull())
        assertTrue(prompt.pedidos.isEmpty())
        assertEquals(1, c.count("/login"), "só o login do cenário")
    }

    @Test
    fun `acao que lanca o erro tipado tambem e atendida`() = runTest {
        val c = logado()
        var chamadas = 0
        val r = c.coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Password("senha")))) {
            chamadas++
            if (chamadas == 1) throw ReauthRequiredException(300) else Result.success(1)
        }
        assertEquals(1, r.getOrThrow())
    }

    @Test
    fun `login social da mesma conta adota sem adotar antes de conferir`() = runTest {
        var socialCalls = 0
        val social = SocialReauthenticator { provider ->
            socialCalls++
            assertEquals(SocialProvider.GOOGLE, provider)
            Result.success(OwnAuthTokens(fakeJwt("acc-1"), "r-social", 3600))
        }
        val c = logado(social = social, socialProviders = setOf(SocialProvider.GOOGLE))
        val prompt = Prompt(listOf(ReauthCredential.Social(SocialProvider.GOOGLE)))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) {
            chamadas++
            if (chamadas == 1) reauth() else Result.success("ok")
        }

        assertEquals("ok", r.getOrThrow())
        assertEquals(1, socialCalls)
        assertEquals(setOf(SocialProvider.GOOGLE), prompt.pedidos.single().socialProviders)
        assertEquals("r-social", c.tm.session.value?.refreshToken)
        assertEquals(1, c.count("/login"), "nenhuma senha foi enviada")
    }

    @Test
    fun `login social cancelado volta ao pedido sem erro a mostrar`() = runTest {
        var socialCalls = 0
        val social = SocialReauthenticator {
            socialCalls++
            if (socialCalls == 1) {
                Result.failure(SocialBrowserException("Login cancelado.", reason = "cancelado"))
            } else {
                Result.success(OwnAuthTokens(fakeJwt("acc-1"), "r-social", 3600))
            }
        }
        val c = logado(social = social, socialProviders = setOf(SocialProvider.GOOGLE))
        val prompt = Prompt(listOf(ReauthCredential.Social(SocialProvider.GOOGLE)))
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) {
            chamadas++
            if (chamadas == 1) reauth() else Result.success("ok")
        }

        assertTrue(r.isSuccess)
        assertEquals(2, prompt.pedidos.size)
        assertNull(prompt.pedidos[1].previousError, "cancelar não é erro")
    }

    @Test
    fun `provedor social nao oferecido e recusado sem chamar o provedor`() = runTest {
        var socialCalls = 0
        val social = SocialReauthenticator { socialCalls++; Result.success(OwnAuthTokens(fakeJwt("acc-1"), "r", 3600)) }
        val c = logado(social = social, socialProviders = setOf(SocialProvider.GOOGLE), maxAttempts = 1)

        val r = c.coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Social(SocialProvider.APPLE)))) { reauth() }

        assertIs<OwnAuthException.Unsupported>(r.exceptionOrNull())
        assertEquals(0, socialCalls)
    }

    @Test
    fun `sem sessao nao pergunta nada`() = runTest {
        val c = logado()
        c.tm.clear()
        val prompt = Prompt(listOf(ReauthCredential.Password("senha")))

        val r = c.coordinator.withRecentAuth(prompt) { reauth() }

        assertIs<OwnAuthException.NotAuthenticated>(r.exceptionOrNull())
        assertTrue(prompt.pedidos.isEmpty())
    }

    @Test
    fun `sessao encerrada durante o pedido nao adota e revoga os tokens novos`() = runTest {
        val c = logado()
        val prompt = ReauthPrompt {
            c.tm.clear() // a pessoa foi deslogada enquanto o diálogo estava aberto
            ReauthCredential.Password("senha")
        }
        var chamadas = 0

        val r = c.coordinator.withRecentAuth(prompt) { chamadas++; reauth() }

        assertIs<OwnAuthException.NotAuthenticated>(r.exceptionOrNull())
        assertEquals(1, chamadas)
        assertNull(c.tm.session.value, "não ressuscita a sessão encerrada")
        assertTrue(c.logouts().single().contains("r2"), "o par novo é revogado")
    }

    @Test
    fun `a senha nunca aparece no toString da credencial`() {
        assertFalse(ReauthCredential.Password("segredo123").toString().contains("segredo123"))
    }
}
