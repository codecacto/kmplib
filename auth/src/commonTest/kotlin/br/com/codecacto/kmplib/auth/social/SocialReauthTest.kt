package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.AuthSessionStore
import br.com.codecacto.kmplib.auth.CapturedRequest
import br.com.codecacto.kmplib.auth.EmailPasswordAuthRepository
import br.com.codecacto.kmplib.auth.FakeSecureTokenStorage
import br.com.codecacto.kmplib.auth.OwnAuthApi
import br.com.codecacto.kmplib.auth.OwnAuthException
import br.com.codecacto.kmplib.auth.OwnAuthTokenManager
import br.com.codecacto.kmplib.auth.ReauthAccountMismatchException
import br.com.codecacto.kmplib.auth.ReauthCredential
import br.com.codecacto.kmplib.auth.ReauthPrompt
import br.com.codecacto.kmplib.auth.ReauthRequest
import br.com.codecacto.kmplib.auth.RecentAuthCoordinator
import br.com.codecacto.kmplib.auth.SOCIAL_REAUTH_REJECTED_REASON
import br.com.codecacto.kmplib.auth.SocialIdentityNotLinkedException
import br.com.codecacto.kmplib.auth.SocialProvider
import br.com.codecacto.kmplib.auth.SocialReauthRejectedException
import br.com.codecacto.kmplib.auth.fakeJwt
import br.com.codecacto.kmplib.auth.isSocialReauthRejected
import br.com.codecacto.kmplib.auth.mockOwnAuthApi
import br.com.codecacto.kmplib.auth.ownAuthTestJson
import br.com.codecacto.kmplib.auth.tokensJson
import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.ui.components.RecentAuthTexts
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reautenticação social (2.304.0, contrato da backlib 0.190.0): navegador por
 * `POST /social/reauth/start` com Bearer, nativo por `POST /social/reauth/nonce` com Bearer +
 * `POST /social`; `?erro=reautenticacao_necessaria`/401 `REAUTH_REQUIRED` e 409
 * `SOCIAL_IDENTITY_NOT_LINKED` como resultados próprios; a senha oferecida em seguida; a sessão
 * nova conferida contra a MESMA conta.
 */
class SocialReauthTest {

    private val now = 1_700_000_000_000L
    private val sessionAccess = fakeJwt("acc-1")
    private val authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth?state=s1&claims=x"

    private class Respostas(
        var start: Pair<HttpStatusCode, String> =
            HttpStatusCode.OK to """{"authorizationUrl":"https://accounts.google.com/o/oauth2/v2/auth?state=s1&claims=x"}""",
        var nonce: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"nonce":"n-reauth","expiresInSeconds":300}""",
        var exchange: Pair<HttpStatusCode, String> = HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-social"),
        var social: Pair<HttpStatusCode, String> = HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-nativo"),
        var relogin: Pair<HttpStatusCode, String> = HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r-senha"),
    )

    private class Cenario(
        val api: OwnAuthApi,
        val tm: OwnAuthTokenManager,
        val cap: MutableList<CapturedRequest>,
    ) {
        fun req(suffix: String): List<CapturedRequest> = cap.filter { it.url.substringBefore('?').endsWith(suffix) }
    }

    /** Sessão de `acc-1` com access token [sessionAccess]. */
    private suspend fun cenario(r: Respostas = Respostas()): Cenario {
        var logins = 0
        val (api, cap) = mockOwnAuthApi { path, _ ->
            when {
                path.endsWith("/social/reauth/start") -> r.start
                path.endsWith("/social/reauth/nonce") -> r.nonce
                path.endsWith("/social/exchange") -> r.exchange
                path.endsWith("/social") -> r.social
                path.endsWith("/login") -> {
                    logins++
                    if (logins == 1) HttpStatusCode.OK to tokensJson(sessionAccess, "r1") else r.relogin
                }
                path.endsWith("/logout") -> HttpStatusCode.NoContent to ""
                else -> HttpStatusCode.NotFound to """{"message":"nf"}"""
            }
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        EmailPasswordAuthRepository(api, tm).signInWithEmail("ana@x.com", "senha").getOrThrow()
        return Cenario(api, tm, cap)
    }

    private class Nativo(var resposta: (SocialProvider, String) -> Result<NativeSocialCredential> = { _, n ->
        Result.success(NativeSocialCredential("id-token-x", n, null, null))
    }) {
        val chamadas = mutableListOf<Triple<SocialProvider, String, Boolean>>()
        suspend fun invoke(p: SocialProvider, nonce: String, authTime: Boolean): Result<NativeSocialCredential> {
            chamadas += Triple(p, nonce, authTime)
            return resposta(p, nonce)
        }
    }

    private fun signIn(
        c: Cenario,
        mode: SocialLoginMode = SocialLoginMode.BACKEND,
        urls: MutableList<String> = mutableListOf(),
        nativo: Nativo = Nativo(),
        platform: String = "android",
        backendAppId: String = "app-x",
        browser: suspend (String) -> String = { "codigo-1" },
    ) = SocialSignIn(
        mode = mode,
        api = c.api,
        social = EmailPasswordAuthRepository(c.api, c.tm),
        nativeWebClientId = "web-client",
        backendAppId = backendAppId,
        redirectScheme = "brcodecacto.x",
        browser = { url, _ -> urls += url; browser(url) },
        nativeCredential = nativo::invoke,
        platform = platform,
    )

    private class Prompt(private val respostas: List<ReauthCredential?>) : ReauthPrompt {
        val pedidos = mutableListOf<ReauthRequest>()
        override suspend fun requestCredential(request: ReauthRequest): ReauthCredential? {
            pedidos += request
            return respostas[minOf(pedidos.size - 1, respostas.lastIndex)]
        }
    }

    private fun reauth() = Result.failure<String>(ReauthRequiredException(maxAgeSeconds = 300))

    private fun corpo(r: CapturedRequest): JsonObject = ownAuthTestJson.parseToJsonElement(r.body).jsonObject

    // ── Navegador ─────────────────────────────────────────────────────────────

    @Test
    fun `navegador comeca por POST reauth start com Bearer e abre a authorizationUrl`() = runTest {
        val c = cenario()
        val urls = mutableListOf<String>()
        val tokens = signIn(c, urls = urls).reauthenticate(SocialProvider.GOOGLE, sessionAccess).getOrThrow()

        assertEquals("r-social", tokens.refreshToken)
        val start = c.req("/social/reauth/start").single()
        assertEquals("POST", start.method)
        assertEquals("Bearer $sessionAccess", start.authorization)
        val body = corpo(start)
        assertEquals(JsonPrimitive("app-x"), body["app"])
        assertEquals(JsonPrimitive("google"), body["provider"])
        assertTrue((body["codeChallenge"] as JsonPrimitive).content.length >= 43)
        assertEquals(listOf(authorizationUrl), urls)
        // Nunca o GET /social/start (com ou sem ?reauth=1): a 0.190.0 responde 400 ali.
        assertTrue(c.cap.none { it.url.contains("/social/start") })
        val troca = corpo(c.req("/social/exchange").single())
        assertEquals(JsonPrimitive("codigo-1"), troca["code"])
    }

    @Test
    fun `authorizationUrl fora da allowlist nao abre o navegador`() = runTest {
        val ruins = listOf(
            "http://accounts.google.com/o/oauth2/v2/auth?state=s1",
            "https://accounts.google.com.evil.example/auth",
            "https://evil.example/auth?next=accounts.google.com",
            "https://user:pw@accounts.google.com/auth",
            "javascript:alert(1)",
            "intent://accounts.google.com/#Intent;scheme=https;end",
        )
        for (ruim in ruins) {
            val c = cenario(Respostas(start = HttpStatusCode.OK to """{"authorizationUrl":"$ruim"}"""))
            val urls = mutableListOf<String>()
            val e = signIn(c, urls = urls).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
            assertIs<SocialBrowserException>(e, ruim)
            assertTrue(urls.isEmpty(), ruim)
            assertTrue(c.req("/social/exchange").isEmpty(), ruim)
        }
    }

    @Test
    fun `allowlist aceita provedores e o host da propria API`() {
        assertTrue(isTrustedAuthorizationUrl("https://accounts.google.com/o/oauth2/v2/auth?x=1", null))
        assertTrue(isTrustedAuthorizationUrl("https://appleid.apple.com/auth/authorize", null))
        assertTrue(isTrustedAuthorizationUrl("https://API.example.com/v1/auth/social/callback", "api.example.com"))
        assertFalse(isTrustedAuthorizationUrl("https://outra.example.com/x", "api.example.com"))
        assertFalse(isTrustedAuthorizationUrl("https://accounts.google.com:8443.evil/x", null))
    }

    @Test
    fun `volta do navegador so casa o esquema pedido`() {
        assertTrue(isExpectedRedirect("brcodecacto.x://auth?codigo=1", "brcodecacto.x"))
        assertTrue(isExpectedRedirect("BRCODECACTO.X://auth?codigo=1", "brcodecacto.x"))
        assertFalse(isExpectedRedirect("outroapp://auth?codigo=1", "brcodecacto.x"))
        assertFalse(isExpectedRedirect("brcodecacto.x.evil://auth?codigo=1", "brcodecacto.x"))
        assertFalse(isExpectedRedirect("brcodecacto.x://auth?codigo=1", null))
        assertFalse(isExpectedRedirect("sem-esquema", "brcodecacto.x"))
    }

    @Test
    fun `login comum continua pelo GET social start sem marca de reauth`() = runTest {
        val c = cenario()
        val urls = mutableListOf<String>()
        signIn(c, urls = urls).signIn(SocialProvider.GOOGLE).getOrThrow()
        assertTrue(urls.single().contains("/social/start?"), urls.single())
        assertFalse(urls.single().contains("reauth"))
        assertTrue(c.req("/social/reauth/start").isEmpty())
    }

    @Test
    fun `erro reautenticacao_necessaria no deep link vira resultado proprio`() = runTest {
        val c = cenario()
        val e = signIn(c) { throw SocialBrowserException("x", reason = SOCIAL_REAUTH_REJECTED_REASON) }
            .reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<SocialReauthRejectedException>(e)
        assertEquals(SocialProvider.GOOGLE, e.provider)
        assertTrue(e.isSocialReauthRejected())
        assertTrue(c.req("/social/exchange").isEmpty())
    }

    @Test
    fun `deep link cancelado continua cancelado`() = runTest {
        val c = cenario()
        val e = signIn(c) { throw SocialBrowserException("x", reason = "cancelado") }
            .reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertTrue(e!!.foiCancelado())
    }

    @Test
    fun `409 SOCIAL_IDENTITY_NOT_LINKED no start nao abre o navegador`() = runTest {
        val c = cenario(Respostas(start = HttpStatusCode.Conflict to """{"message":"x","code":"SOCIAL_IDENTITY_NOT_LINKED"}"""))
        val urls = mutableListOf<String>()
        val e = signIn(c, urls = urls).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<SocialIdentityNotLinkedException>(e)
        assertEquals(SocialProvider.GOOGLE, e.provider)
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `401 sem codigo no start e sessao invalida, nao credencial social`() = runTest {
        val c = cenario(Respostas(start = HttpStatusCode.Unauthorized to """{"message":"x"}"""))
        val e = signIn(c).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<OwnAuthException.NotAuthenticated>(e)
    }

    @Test
    fun `401 REAUTH_REQUIRED na troca do codigo tambem vira resultado proprio`() = runTest {
        val c = cenario(Respostas(exchange = HttpStatusCode.Unauthorized to """{"message":"x","code":"REAUTH_REQUIRED"}"""))
        assertIs<SocialReauthRejectedException>(
            signIn(c).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull(),
        )
    }

    @Test
    fun `sem a sessao o SocialSignIn recusa a reautenticacao`() = runTest {
        val c = cenario()
        val urls = mutableListOf<String>()
        assertTrue(signIn(c, urls = urls).reauthenticate(SocialProvider.GOOGLE).isFailure)
        assertTrue(urls.isEmpty())
        assertTrue(c.req("/social/reauth/start").isEmpty())
    }

    // ── Nativo ────────────────────────────────────────────────────────────────

    @Test
    fun `Google nativo pede nonce de reauth com Bearer e auth_time e manda ao POST social`() = runTest {
        val c = cenario()
        val nativo = Nativo()
        val tokens = signIn(c, mode = SocialLoginMode.NATIVE, nativo = nativo)
            .reauthenticate(SocialProvider.GOOGLE, sessionAccess).getOrThrow()

        assertEquals("r-nativo", tokens.refreshToken)
        val nonceReq = c.req("/social/reauth/nonce").single()
        assertEquals("Bearer $sessionAccess", nonceReq.authorization)
        assertEquals(JsonPrimitive("google"), corpo(nonceReq)["provider"])
        assertEquals(JsonPrimitive("android"), corpo(nonceReq)["platform"])
        assertEquals(listOf(Triple(SocialProvider.GOOGLE, "n-reauth", true)), nativo.chamadas)
        val social = corpo(c.req("/social").single())
        assertEquals(JsonPrimitive("id-token-x"), social["idToken"])
        assertEquals(JsonPrimitive("n-reauth"), social["nonce"])
        assertTrue(c.cap.none { it.url.contains("/social/nonce") })
    }

    @Test
    fun `Apple no iOS vai sempre pelo nativo, sem pedir claim`() = runTest {
        val c = cenario()
        val nativo = Nativo()
        signIn(c, mode = SocialLoginMode.BACKEND, nativo = nativo, platform = "ios")
            .reauthenticate(SocialProvider.APPLE, sessionAccess).getOrThrow()
        val corpoNonce = corpo(c.req("/social/reauth/nonce").single())
        assertEquals(JsonPrimitive("apple"), corpoNonce["provider"])
        assertEquals(JsonPrimitive("ios"), corpoNonce["platform"])
        assertEquals(listOf(Triple(SocialProvider.APPLE, "n-reauth", false)), nativo.chamadas)
    }

    @Test
    fun `401 REAUTH_REQUIRED no POST social nativo vira resultado proprio`() = runTest {
        val c = cenario(Respostas(social = HttpStatusCode.Unauthorized to """{"message":"x","code":"REAUTH_REQUIRED"}"""))
        val e = signIn(c, mode = SocialLoginMode.NATIVE).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<SocialReauthRejectedException>(e)
    }

    @Test
    fun `401 generico no POST social nativo continua credencial invalida`() = runTest {
        val c = cenario(Respostas(social = HttpStatusCode.Unauthorized to """{"message":"nonce gasto"}"""))
        val e = signIn(c, mode = SocialLoginMode.NATIVE).reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<OwnAuthException.InvalidCredentials>(e)
    }

    @Test
    fun `403 SOCIAL_DIRECT_EXCHANGE_DISABLED refaz pelo navegador quando o app tem o BFF`() = runTest {
        val c = cenario(Respostas(nonce = HttpStatusCode.Forbidden to """{"message":"x","code":"SOCIAL_DIRECT_EXCHANGE_DISABLED"}"""))
        val nativo = Nativo()
        val urls = mutableListOf<String>()
        val tokens = signIn(c, mode = SocialLoginMode.NATIVE, nativo = nativo, urls = urls)
            .reauthenticate(SocialProvider.GOOGLE, sessionAccess).getOrThrow()
        assertEquals("r-social", tokens.refreshToken)
        assertTrue(nativo.chamadas.isEmpty())
        assertEquals(listOf(authorizationUrl), urls)
    }

    @Test
    fun `403 SOCIAL_DIRECT_EXCHANGE_DISABLED sem BFF configurado chega como erro`() = runTest {
        val c = cenario(Respostas(nonce = HttpStatusCode.Forbidden to """{"message":"x","code":"SOCIAL_DIRECT_EXCHANGE_DISABLED"}"""))
        val e = signIn(c, mode = SocialLoginMode.NATIVE, backendAppId = "")
            .reauthenticate(SocialProvider.GOOGLE, sessionAccess).exceptionOrNull()
        assertIs<br.com.codecacto.kmplib.auth.SocialDirectExchangeDisabledException>(e)
        assertEquals(SocialProvider.GOOGLE, e.provider)
    }

    // ── Coordenador ───────────────────────────────────────────────────────────

    @Test
    fun `coordenador manda o Bearer da sessao ao reautenticador social`() = runTest {
        val c = cenario()
        val coordinator = RecentAuthCoordinator(c.api, c.tm, signIn(c), setOf(SocialProvider.GOOGLE))
        var chamadas = 0
        val r = coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Social(SocialProvider.GOOGLE)))) {
            chamadas++; if (chamadas == 1) reauth() else Result.success("ok")
        }
        assertEquals("ok", r.getOrThrow())
        assertEquals("Bearer $sessionAccess", c.req("/social/reauth/start").single().authorization)
        assertEquals("r-social", c.tm.session.value?.refreshToken)
    }

    @Test
    fun `recusa social mostra o motivo e oferece a senha que conclui`() = runTest {
        val c = cenario()
        val s = signIn(c) { throw SocialBrowserException("x", reason = SOCIAL_REAUTH_REJECTED_REASON) }
        val coordinator = RecentAuthCoordinator(c.api, c.tm, s, setOf(SocialProvider.GOOGLE))
        val prompt = Prompt(
            listOf(ReauthCredential.Social(SocialProvider.GOOGLE), ReauthCredential.Password("senha")),
        )
        var chamadas = 0
        val r = coordinator.withRecentAuth(prompt) { chamadas++; if (chamadas == 1) reauth() else Result.success("ok") }

        assertEquals("ok", r.getOrThrow())
        val segundo = prompt.pedidos[1]
        assertIs<SocialReauthRejectedException>(segundo.previousError)
        assertTrue(segundo.passwordAvailable)
        assertEquals(setOf(SocialProvider.GOOGLE), segundo.socialProviders)
        assertEquals("r-senha", c.tm.session.value?.refreshToken)
    }

    @Test
    fun `conta sem vinculo tira o provedor do pedido e oferece a senha`() = runTest {
        val c = cenario(Respostas(start = HttpStatusCode.Conflict to """{"message":"x","code":"SOCIAL_IDENTITY_NOT_LINKED"}"""))
        val coordinator = RecentAuthCoordinator(c.api, c.tm, signIn(c), setOf(SocialProvider.GOOGLE))
        val prompt = Prompt(
            listOf(ReauthCredential.Social(SocialProvider.GOOGLE), ReauthCredential.Password("senha")),
        )
        var chamadas = 0
        val r = coordinator.withRecentAuth(prompt) { chamadas++; if (chamadas == 1) reauth() else Result.success("ok") }

        assertEquals("ok", r.getOrThrow())
        val segundo = prompt.pedidos[1]
        assertIs<SocialIdentityNotLinkedException>(segundo.previousError)
        assertTrue(segundo.socialProviders.isEmpty())
        assertTrue(segundo.passwordAvailable)
    }

    @Test
    fun `reautenticacao social com outra conta e recusada e revogada`() = runTest {
        val c = cenario(Respostas(exchange = HttpStatusCode.OK to tokensJson(fakeJwt("acc-2"), "r-outra")))
        val coordinator = RecentAuthCoordinator(c.api, c.tm, signIn(c), setOf(SocialProvider.GOOGLE))
        var chamadas = 0
        val r = coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Social(SocialProvider.GOOGLE)))) {
            chamadas++; reauth()
        }
        assertIs<ReauthAccountMismatchException>(r.exceptionOrNull())
        assertEquals(1, chamadas)
        assertEquals("r1", c.tm.session.value?.refreshToken)
        assertTrue(c.req("/logout").any { "r-outra" in it.body })
    }

    @Test
    fun `reautenticacao nativa com outra conta tambem e recusada`() = runTest {
        val c = cenario(Respostas(social = HttpStatusCode.OK to tokensJson(fakeJwt("acc-9"), "r-x")))
        val coordinator = RecentAuthCoordinator(
            c.api, c.tm, signIn(c, mode = SocialLoginMode.NATIVE), setOf(SocialProvider.GOOGLE),
        )
        val r = coordinator.withRecentAuth(Prompt(listOf(ReauthCredential.Social(SocialProvider.GOOGLE)))) { reauth() }
        assertIs<ReauthAccountMismatchException>(r.exceptionOrNull())
        assertEquals("r1", c.tm.session.value?.refreshToken)
    }

    // ── Textos ────────────────────────────────────────────────────────────────

    @Test
    fun `textos oferecem a senha so quando ela e um caminho`() {
        val t = RecentAuthTexts()
        val recenteSenha = t.socialNotRecent(SocialProvider.GOOGLE, passwordAvailable = true)
        val recente = t.socialNotRecent(SocialProvider.GOOGLE, passwordAvailable = false)
        assertTrue("senha" in recenteSenha && "Google" in recenteSenha)
        assertTrue("navegador" in recente && "senha" !in recente)
        val vinculoSenha = t.socialNotLinked(SocialProvider.APPLE, passwordAvailable = true)
        assertTrue("Apple" in vinculoSenha && "senha" in vinculoSenha)
        assertEquals(recente, t.errorMessage(SocialReauthRejectedException(SocialProvider.GOOGLE)))
        assertEquals(
            t.socialNotLinked(SocialProvider.GOOGLE, false),
            t.errorMessage(SocialIdentityNotLinkedException(SocialProvider.GOOGLE)),
        )
        assertNull(t.errorMessage(br.com.codecacto.kmplib.auth.ReauthCancelledException()))
    }
}
