package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Login social do own-auth (`GET /auth/social/nonce` + `POST /auth/social`).
 *
 * O que estes testes protegem, em ordem de gravidade: (1) o `accessToken` do Google **nunca** sai do
 * aparelho como prova de identidade; (2) o nonce vem do **servidor** e é de uso único; (3) a origem
 * do login (`providerId`) sobrevive à sessão e ao refresh; (4) falha não deixa ninguém logado.
 */
class OwnAuthSocialTest {

    private val now = 1_700_000_000_000L

    private fun repoWith(
        captured: MutableList<CapturedRequest> = mutableListOf(),
        responder: (path: String, attempt: Int) -> Pair<HttpStatusCode, String>,
    ): Pair<EmailPasswordAuthRepository, MutableList<CapturedRequest>> {
        val (api, cap) = mockOwnAuthApi(captured, responder = responder)
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        return EmailPasswordAuthRepository(api, tm) to cap
    }

    private fun nonceJson(nonce: String, expiresIn: Long = 300) =
        """{"nonce":"$nonce","expiresInSeconds":$expiresIn}"""

    /** Responde nonce no GET e tokens no POST /social. */
    private fun happyPath(
        nonce: String = "srv-nonce-1",
        sub: String = "acc-social",
    ): (String, Int) -> Pair<HttpStatusCode, String> = { path, _ ->
        when {
            path.endsWith("social/nonce") -> HttpStatusCode.OK to nonceJson(nonce)
            path.endsWith("/social") -> HttpStatusCode.OK to tokensJson(fakeJwt(sub), "ref-social")
            else -> HttpStatusCode.NotFound to ""
        }
    }

    // ---- contrato de fio -------------------------------------------------

    @Test
    fun `nonce sai de um GET na rota social nonce e parseia`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ -> HttpStatusCode.OK to nonceJson("n-42", 300) }
        val nonce = api.socialNonce().getOrThrow()
        assertEquals("n-42", nonce.nonce)
        assertEquals(300, nonce.expiresInSeconds)
        assertEquals("https://api.example.com/v1/staff/auth/social/nonce", cap.single().url)
        // GET não tem corpo — o nonce é pedido, não proposto pelo cliente.
        assertEquals("", cap.single().body)
    }

    @Test
    fun `social posta provider idToken e nonce na rota social`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ ->
            HttpStatusCode.OK to tokensJson(fakeJwt("a1"), "r1")
        }
        api.social(SocialProvider.GOOGLE, idToken = "id-tok", nonce = "n-1").getOrThrow()
        assertEquals("https://api.example.com/v1/staff/auth/social", cap.single().url)
        val body = cap.single().body
        assertTrue("\"provider\":\"google\"" in body, body)
        assertTrue("\"idToken\":\"id-tok\"" in body, body)
        assertTrue("\"nonce\":\"n-1\"" in body, body)
    }

    @Test
    fun `apple usa o wire apple e o nonce cru`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ ->
            HttpStatusCode.OK to tokensJson(fakeJwt("a1"), "r1")
        }
        api.social(SocialProvider.APPLE, "apple-jwt", "raw-nonce").getOrThrow()
        assertTrue("\"provider\":\"apple\"" in cap.single().body)
        assertTrue("\"nonce\":\"raw-nonce\"" in cap.single().body)
    }

    @Test
    fun `name e email em branco nao viram campos vazios no corpo`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ ->
            HttpStatusCode.OK to tokensJson(fakeJwt("a1"), "r1")
        }
        api.social(SocialProvider.APPLE, "t", "n", name = "   ", email = null).getOrThrow()
        val body = cap.single().body
        assertFalse("\"name\"" in body, body)
        assertFalse("\"email\"" in body, body)
    }

    @Test
    fun `name e email vao no corpo quando o provedor os entrega`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { _, _ ->
            HttpStatusCode.OK to tokensJson(fakeJwt("a1"), "r1")
        }
        api.social(SocialProvider.APPLE, "t", "n", name = " Ana ", email = "ana@x.com").getOrThrow()
        val body = cap.single().body
        assertTrue("\"name\":\"Ana\"" in body, body)
        assertTrue("\"email\":\"ana@x.com\"" in body, body)
    }

    @Test
    fun `sufixos sociais sao configuraveis sem tocar no resto`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val config = OwnAuthConfig(
            httpClient = mockHttpClient(cap) { _, _ -> HttpStatusCode.OK to nonceJson("n") },
            baseUrl = "https://api.example.com",
            authBasePath = "/api/v1/auth",
            socialSuffix = "oidc",
            socialNonceSuffix = "oidc/challenge",
        )
        val api = OwnAuthApi(config)
        api.socialNonce()
        assertEquals("https://api.example.com/api/v1/auth/oidc/challenge", cap.single().url)
    }

    @Test
    fun `sufixo social vazio e recusado na construcao da config`() {
        // Vazio casaria com TODA rota no roteamento de erro (`"login".startsWith("")`) e faria o 401
        // genérico do login por senha passar a vazar a mensagem do servidor.
        val client = mockHttpClient { _, _ -> HttpStatusCode.OK to "" }
        assertFailsWith<IllegalArgumentException> {
            OwnAuthConfig(client, baseUrl = "https://x", socialSuffix = "  ")
        }
        assertFailsWith<IllegalArgumentException> {
            OwnAuthConfig(client, baseUrl = "https://x", socialNonceSuffix = "/")
        }
    }

    // ---- mapeamento de erro ---------------------------------------------

    @Test
    fun `recusa do idToken vira InvalidCredentials com a mensagem do servidor`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ ->
            HttpStatusCode.Unauthorized to """{"message":"nonce expirado"}"""
        }
        val e = api.social(SocialProvider.GOOGLE, "t", "n").exceptionOrNull()
        assertIs<OwnAuthException.InvalidCredentials>(e)
        // A mensagem do servidor é o ÚNICO lugar que distingue nonce vencido de `aud` errado ou
        // e-mail não verificado — por isso ela tem prioridade sobre o texto local (ao contrário do
        // 401 de senha, que é genérico de propósito).
        assertEquals("nonce expirado", e.message)
    }

    @Test
    fun `email nao verificado (403) tambem e recusa de credencial, nao erro de servidor`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ ->
            HttpStatusCode.Forbidden to """{"message":"e-mail não verificado pelo provedor"}"""
        }
        val e = api.social(SocialProvider.GOOGLE, "t", "n").exceptionOrNull()
        assertIs<OwnAuthException.InvalidCredentials>(e)
        assertTrue(e.isClientError)
    }

    @Test
    fun `400 no social nao vira WeakPassword`() = runTest {
        // Regressão: o mapeamento de senha trata 400 como "senha fraca" em rotas de register/password.
        // Social tem vocabulário próprio e não pode herdar aquele texto.
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.BadRequest to "" }
        val e = api.social(SocialProvider.GOOGLE, "t", "n").exceptionOrNull()
        assertIs<OwnAuthException.InvalidCredentials>(e)
    }

    @Test
    fun `500 no social vira erro de servidor, nao credencial invalida`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.InternalServerError to "" }
        val e = api.social(SocialProvider.GOOGLE, "t", "n").exceptionOrNull()
        assertIs<OwnAuthException.Server>(e)
        assertEquals(500, e.code)
    }

    // ---- repositório: sessão, origem e segurança -------------------------

    @Test
    fun `signInWithSocial adota a sessao e marca providerId google`() = runTest {
        val (repo, _) = repoWith(responder = happyPath(sub = "acc-g"))
        val user = repo.signInWithSocial(
            SocialProvider.GOOGLE,
            idToken = "id",
            nonce = "n",
            name = "Ana",
            email = "ana@x.com",
        ).getOrThrow()
        assertEquals("acc-g", user.id)
        assertEquals("google.com", user.providerId)
        assertTrue(user.isGoogleProvider)
        assertEquals("Ana", user.displayName)
        assertTrue(repo.isLoggedInSync)
        assertEquals("google.com", repo.currentUser.first()?.providerId)
    }

    @Test
    fun `signInWithApple marca providerId apple`() = runTest {
        val (repo, _) = repoWith(responder = happyPath(sub = "acc-a"))
        val user = repo.signInWithApple(idToken = "apple-jwt", nonce = "raw").getOrThrow()
        assertEquals("apple.com", user.providerId)
        assertTrue(user.isAppleProvider)
        assertFalse(user.isEmailProvider)
    }

    @Test
    fun `signInWithGoogle usa o nonce que o SERVIDOR emitiu`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (repo, _) = repoWith(cap, happyPath(nonce = "srv-abc"))
        assertEquals("srv-abc", repo.socialNonce().getOrThrow().nonce)
        repo.signInWithGoogle(idToken = "id-tok").getOrThrow()
        val post = cap.last { it.url.endsWith("/social") }
        assertTrue("\"nonce\":\"srv-abc\"" in post.body, post.body)
    }

    @Test
    fun `signInWithGoogle sem nonce do servidor falha explicito e nao toca a rede`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (repo, _) = repoWith(cap, happyPath())
        val e = repo.signInWithGoogle(idToken = "id-tok").exceptionOrNull()
        assertIs<AuthException.UnknownError>(e)
        assertTrue("socialNonce" in e.message, e.message)
        // Nada foi enviado: melhor falhar em cima do erro de programação do que inventar um nonce e
        // receber do servidor um "credencial inválida" que aponta para o lugar errado.
        assertTrue(cap.isEmpty())
        assertFalse(repo.isLoggedInSync)
    }

    @Test
    fun `o accessToken do Google NUNCA vai para o servidor`() = runTest {
        val cap = mutableListOf<CapturedRequest>()
        val (repo, _) = repoWith(cap, happyPath())
        repo.socialNonce().getOrThrow()
        repo.signInWithGoogle(idToken = "id-tok", accessToken = "ya29.SEGREDO").getOrThrow()
        val post = cap.last { it.url.endsWith("/social") }
        assertFalse("ya29.SEGREDO" in post.body, post.body)
        assertFalse("accessToken" in post.body, post.body)
    }

    @Test
    fun `nonce e de uso unico - a segunda tentativa exige um novo`() = runTest {
        val (repo, _) = repoWith(responder = happyPath())
        repo.socialNonce().getOrThrow()
        repo.signInWithGoogle(idToken = "id-1").getOrThrow()
        val e = repo.signInWithGoogle(idToken = "id-2").exceptionOrNull()
        assertIs<AuthException.UnknownError>(e)
    }

    @Test
    fun `falha no social nao deixa ninguem logado`() = runTest {
        val (repo, _) = repoWith { path, _ ->
            if (path.endsWith("social/nonce")) HttpStatusCode.OK to nonceJson("n")
            else HttpStatusCode.Unauthorized to """{"message":"assinatura inválida"}"""
        }
        repo.socialNonce().getOrThrow()
        val e = repo.signInWithGoogle("id").exceptionOrNull()
        assertIs<AuthException.InvalidCredentials>(e)
        assertFalse(repo.isLoggedInSync)
        assertNull(repo.currentUserSync)
    }

    @Test
    fun `falha ao pedir o nonce vira AuthException e nao deixa nonce sujo`() = runTest {
        val (repo, _) = repoWith { _, _ -> HttpStatusCode.ServiceUnavailable to "" }
        assertIs<AuthException.UnknownError>(repo.socialNonce().exceptionOrNull())
        assertIs<AuthException.UnknownError>(repo.signInWithGoogle("id").exceptionOrNull())
    }

    @Test
    fun `login por senha continua marcando providerId password`() = runTest {
        val (repo, _) = repoWith { _, _ -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-p"), "r") }
        val user = repo.signInWithEmail("a@x.com", "senha").getOrThrow()
        assertEquals("password", user.providerId)
        assertTrue(user.isEmailProvider)
    }

    // ---- persistência: a origem sobrevive ao processo e ao refresh -------

    @Test
    fun `a origem do login sobrevive ao restore do cofre`() = runTest {
        val storage = FakeSecureTokenStorage()
        val (api, _) = mockOwnAuthApi(responder = happyPath(sub = "acc-x"))
        val store = AuthSessionStore(storage)
        val tm = OwnAuthTokenManager(api, store, 60) { now }
        EmailPasswordAuthRepository(api, tm)
            .signInWithApple("t", "n").getOrThrow()

        // Novo processo: mesmo cofre, gerenciador novo.
        val tm2 = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        val repo2 = EmailPasswordAuthRepository(api, tm2)
        tm2.restore()
        assertEquals("apple.com", repo2.currentUserSync?.providerId)
    }

    @Test
    fun `refresh nao converte login social em login por senha`() = runTest {
        val storage = FakeSecureTokenStorage()
        var clock = now
        val (api, _) = mockOwnAuthApi { path, _ ->
            when {
                path.endsWith("social/nonce") -> HttpStatusCode.OK to nonceJson("n")
                path.endsWith("/social") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-x"), "r1", 60)
                path.endsWith("refresh") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-x"), "r2", 3600)
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { clock }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithSocial(SocialProvider.GOOGLE, "t", "n").getOrThrow()

        clock += 120_000 // access token vencido → força o refresh rotativo
        repo.getIdToken().getOrThrow()
        assertEquals("google.com", repo.currentUserSync?.providerId)
        assertEquals("r2", tm.session.value?.refreshToken)
    }

    @Test
    fun `sessao gravada antes da 2 98 0 (sem providerId) le como password`() {
        val legacy = """
            {"accessToken":"a","refreshToken":"r","accessExpiresAtEpochSeconds":1,"accountId":"acc"}
        """.trimIndent()
        val session = ownAuthTestJson.decodeFromString(OwnAuthSession.serializer(), legacy)
        assertEquals("password", session.providerId)
    }

    // ---- vocabulário -----------------------------------------------------

    @Test
    fun `SocialProvider casa wire e providerId canonico do User`() {
        assertEquals("google", SocialProvider.GOOGLE.wire)
        assertEquals("apple", SocialProvider.APPLE.wire)
        assertEquals("google.com", SocialProvider.GOOGLE.userProviderId)
        assertEquals("apple.com", SocialProvider.APPLE.userProviderId)
        assertEquals(SocialProvider.APPLE, SocialProvider.fromWireOrNull(" Apple "))
        assertNull(SocialProvider.fromWireOrNull("facebook"))
        assertNull(SocialProvider.fromWireOrNull(null))
    }

    @Test
    fun `SocialNonce tolera resposta sem expiresInSeconds`() {
        val n = ownAuthTestJson.decodeFromString(SocialNonce.serializer(), """{"nonce":"abc"}""")
        assertEquals("abc", n.nonce)
        assertEquals(0, n.expiresInSeconds)
    }
}
