package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.firebase.auth.AuthException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Perfil own-auth (2.229.0): `username` no `GET /me` e a edição do próprio nome pelo `PATCH /me`
 * (backlib-auth-local 0.140.0).
 */
class OwnAuthProfileTest {

    private val now = 1_700_000_000_000L

    private class Cenario(
        val repo: EmailPasswordAuthRepository,
        val tm: OwnAuthTokenManager,
        val captured: MutableList<CapturedRequest>,
    )

    private fun cenario(
        tokensDoLogin: String = tokensJson(fakeJwt("acc-1"), "r1"),
        responder: (path: String, method: String) -> Pair<HttpStatusCode, String>,
    ): Cenario {
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { path, _ ->
            val method = cap.last().method
            if (path.endsWith("/login")) HttpStatusCode.OK to tokensDoLogin else responder(path, method)
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { now }
        return Cenario(EmailPasswordAuthRepository(api, tm), tm, cap)
    }

    private fun me(name: String? = "Ana Souza", username: String? = "ana", email: String = "ana@x.com", id: String = "acc-1") =
        buildString {
            append("""{"id":"$id","email":"$email"""")
            if (name != null) append(""","name":"$name"""")
            if (username != null) append(""","username":"$username"""")
            append("}")
        }

    // ---- GET /me ---------------------------------------------------------

    @Test
    fun `login le o me e o usuario ganha nome e username`() = runTest {
        val c = cenario { path, _ -> if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to "" }

        val user = c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        assertEquals("ana", user.username)
        assertEquals("Ana Souza", user.displayName)
        assertEquals("ana", c.repo.currentUser.first()?.username)
        val leitura = c.captured.single { it.url.endsWith("/v1/staff/auth/me") }
        assertEquals("GET", leitura.method)
        assertTrue(leitura.authorization.orEmpty().startsWith("Bearer "), "o /me vai com o token da sessão")
    }

    @Test
    fun `me que falha nao derruba o login`() = runTest {
        val c = cenario { _, _ -> HttpStatusCode.InternalServerError to """{"message":"boom"}""" }

        val user = c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        assertEquals("ana@x.com", user.email)
        assertNull(user.username)
        assertEquals("r1", c.tm.session.value?.refreshToken)
    }

    @Test
    fun `sessao restrita nao apaga email conhecido e username sai nulo`() = runTest {
        val c = cenario { path, _ ->
            if (path.endsWith("/me")) HttpStatusCode.OK to me(email = "", username = null) else HttpStatusCode.NotFound to ""
        }

        val user = c.repo.signInWithEmail("ana@x.com", "123456").getOrThrow()

        assertEquals("ana@x.com", user.email, "e-mail vazio do /me restrito não apaga o que se sabia")
        assertNull(user.username)
        assertEquals("Ana Souza", user.displayName)
    }

    @Test
    fun `perfil de outra conta nao e gravado na sessao`() = runTest {
        val c = cenario { path, _ -> if (path.endsWith("/me")) HttpStatusCode.OK to me(id = "acc-OUTRA") else HttpStatusCode.NotFound to "" }

        val user = c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        assertNull(user.username)
        assertNull(user.displayName)
    }

    @Test
    fun `refresh de token preserva o username`() = runTest {
        var agora = now
        val cap = mutableListOf<CapturedRequest>()
        val (api, _) = mockOwnAuthApi(cap) { path, _ ->
            when {
                path.endsWith("/login") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r1", expiresIn = 900)
                path.endsWith("/refresh") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r2", expiresIn = 900)
                path.endsWith("/me") -> HttpStatusCode.OK to me()
                else -> HttpStatusCode.NotFound to ""
            }
        }
        val tm = OwnAuthTokenManager(api, AuthSessionStore(FakeSecureTokenStorage()), 60) { agora }
        val repo = EmailPasswordAuthRepository(api, tm)
        repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        agora += 1_000_000
        tm.accessToken()

        assertEquals("r2", tm.session.value?.refreshToken)
        assertEquals("ana", repo.currentUserSync?.username)
    }

    @Test
    fun `username sobrevive a reabertura do app`() = runTest {
        val storage = FakeSecureTokenStorage()
        val (api, _) = mockOwnAuthApi { path, _ ->
            when {
                path.endsWith("/login") -> HttpStatusCode.OK to tokensJson(fakeJwt("acc-1"), "r1")
                path.endsWith("/me") -> HttpStatusCode.OK to me()
                else -> HttpStatusCode.NotFound to ""
            }
        }
        EmailPasswordAuthRepository(api, OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now })
            .signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val reaberto = OwnAuthTokenManager(api, AuthSessionStore(storage), 60) { now }
        reaberto.restore()

        assertEquals("ana", reaberto.session.value?.username)
    }

    @Test
    fun `sessao gravada antes da 2229 desserializa com username nulo`() {
        val antiga = """{"accessToken":"a","refreshToken":"r","accessExpiresAtEpochSeconds":1,"accountId":"acc-1","email":"ana@x.com"}"""
        val s = ownAuthTestJson.decodeFromString(OwnAuthSession.serializer(), antiga)
        assertNull(s.username)
    }

    @Test
    fun `refreshOwnProfile rele o me e atualiza o currentUser`() = runTest {
        var nome = "Ana"
        val c = cenario { path, _ -> if (path.endsWith("/me")) HttpStatusCode.OK to me(name = nome) else HttpStatusCode.NotFound to "" }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        nome = "Ana Maria"
        val user = c.repo.refreshOwnProfile().getOrThrow()

        assertEquals("Ana Maria", user.displayName)
        assertEquals("Ana Maria", c.repo.currentUserSync?.displayName)
    }

    @Test
    fun `refreshOwnProfile sem sessao falha NotAuthenticated sem chamar o servidor`() = runTest {
        val c = cenario { _, _ -> HttpStatusCode.OK to me() }

        assertIs<OwnAuthException.NotAuthenticated>(c.repo.refreshOwnProfile().exceptionOrNull())
        assertTrue(c.captured.isEmpty())
    }

    // ---- PATCH /me -------------------------------------------------------

    @Test
    fun `updateOwnProfile manda PATCH so com o nome e adota o perfil gravado`() = runTest {
        val c = cenario { path, method ->
            when {
                path.endsWith("/me") && method == "PATCH" -> HttpStatusCode.OK to me(name = "Ana Maria")
                path.endsWith("/me") -> HttpStatusCode.OK to me()
                else -> HttpStatusCode.NotFound to ""
            }
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val user = c.repo.updateOwnProfile(name = "  Ana Maria ").getOrThrow()

        assertEquals("Ana Maria", user.displayName)
        assertEquals("ana", user.username)
        assertEquals("Ana Maria", c.repo.currentUser.first()?.displayName)
        val patch = c.captured.single { it.method == "PATCH" }
        assertTrue(patch.url.endsWith("/v1/staff/auth/me"))
        assertTrue(patch.authorization.orEmpty().startsWith("Bearer "))
        assertEquals("""{"name":"  Ana Maria "}""", patch.body, "username nulo não vai no corpo; o servidor apara")
    }

    @Test
    fun `updateOwnProfile com username manda os dois campos`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") HttpStatusCode.OK to me(username = "ana.souza")
            else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val user = c.repo.updateOwnProfile(name = "Ana Souza", username = "Ana.Souza").getOrThrow()

        assertEquals("ana.souza", user.username)
        assertTrue(c.captured.single { it.method == "PATCH" }.body.contains("\"username\":\"Ana.Souza\""))
    }

    @Test
    fun `usuario em uso vira ProfileRejected com o erro no campo`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") {
                HttpStatusCode.Conflict to """{"message":"Este nome de usuário já está em uso","code":"USERNAME_TAKEN","details":{"username":"Este nome de usuário já está em uso"}}"""
            } else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val e = c.repo.updateOwnProfile(username = "joao").exceptionOrNull()

        assertIs<OwnAuthException.ProfileRejected>(e)
        assertEquals(409, e.code)
        assertEquals(OwnAuthErrorCodes.USERNAME_TAKEN, e.serverCode)
        assertEquals("Este nome de usuário já está em uso", e.fieldError("username"))
        assertEquals("ana", c.repo.currentUserSync?.username, "recusa não mexe na sessão")
    }

    @Test
    fun `nome invalido vem no campo name`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") {
                HttpStatusCode.BadRequest to """{"message":"Nome é obrigatório","code":"VALIDATION_ERROR","details":{"name":"Nome é obrigatório"}}"""
            } else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val e = c.repo.updateOwnProfile(name = " ").exceptionOrNull()

        assertIs<OwnAuthException.ProfileRejected>(e)
        assertEquals("Nome é obrigatório", e.fieldError("name"))
        assertEquals("Nome é obrigatório", e.message)
    }

    @Test
    fun `senha temporaria e usuario travado chegam como 403 com o codigo`() = runTest {
        for ((codigo, detalhe) in listOf(
            OwnAuthErrorCodes.PASSWORD_CHANGE_REQUIRED to "",
            OwnAuthErrorCodes.USERNAME_CHANGE_DISABLED to ""","details":{"username":"O nome de usuário não pode ser alterado"}""",
        )) {
            val c = cenario { path, method ->
                if (path.endsWith("/me") && method == "PATCH") {
                    HttpStatusCode.Forbidden to """{"message":"recusado","code":"$codigo"$detalhe}"""
                } else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
            }
            c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

            val e = c.repo.updateOwnProfile(name = "Ana", username = "ana2").exceptionOrNull()

            assertIs<OwnAuthException.ProfileRejected>(e)
            assertEquals(403, e.code)
            assertEquals(codigo, e.serverCode)
            assertEquals("r1", c.tm.session.value?.refreshToken, "403 de perfil não derruba a sessão")
        }
    }

    @Test
    fun `nada a alterar falha local sem ida ao servidor`() = runTest {
        val c = cenario { path, _ -> if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to "" }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()
        val antes = c.captured.size

        val e = c.repo.updateOwnProfile().exceptionOrNull()

        assertIs<OwnAuthException.ProfileRejected>(e)
        assertEquals(OwnAuthErrorCodes.NOTHING_TO_UPDATE, e.serverCode)
        assertEquals(antes, c.captured.size)
    }

    @Test
    fun `backend sem a rota de edicao responde Unsupported`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") HttpStatusCode.NotFound to ""
            else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        assertIs<OwnAuthException.Unsupported>(c.repo.updateOwnProfile(name = "Ana").exceptionOrNull())
    }

    @Test
    fun `erro 5xx do PATCH nao vaza a mensagem interna`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") HttpStatusCode.InternalServerError to """{"message":"relation users does not exist"}"""
            else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val e = c.repo.updateOwnProfile(name = "Ana").exceptionOrNull()

        assertIs<OwnAuthException.Server>(e)
        assertFalse(e.message.contains("relation"))
    }

    // ---- IAuthRepository.updateProfile -----------------------------------

    @Test
    fun `updateProfile do contrato grava o nome pelo PATCH`() = runTest {
        val c = cenario { path, method ->
            if (path.endsWith("/me") && method == "PATCH") HttpStatusCode.OK to me(name = "Ana Maria")
            else if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to ""
        }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        assertTrue(c.repo.updateProfile(displayName = "Ana Maria").isSuccess)
        assertEquals("Ana Maria", c.repo.currentUserSync?.displayName)
    }

    @Test
    fun `updateProfile com foto falha inteiro sem gravar o nome`() = runTest {
        val c = cenario { path, _ -> if (path.endsWith("/me")) HttpStatusCode.OK to me() else HttpStatusCode.NotFound to "" }
        c.repo.signInWithEmail("ana@x.com", "s3nha").getOrThrow()

        val e = c.repo.updateProfile(displayName = "Outro", photoUrl = "https://x/y.png").exceptionOrNull()

        assertIs<AuthException.UnknownError>(e)
        assertTrue(c.captured.none { it.method == "PATCH" })
    }

    // ---- API crua --------------------------------------------------------

    @Test
    fun `OwnAuthApi me desserializa backend anterior sem username`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.OK to """{"id":"acc-1","email":"ana@x.com","name":"Ana"}""" }

        val p = api.me("tok").getOrThrow()

        assertEquals(OwnAuthProfile(id = "acc-1", email = "ana@x.com", name = "Ana", username = null), p)
    }

    @Test
    fun `OwnAuthApi me com 401 vira NotAuthenticated`() = runTest {
        val (api, _) = mockOwnAuthApi { _, _ -> HttpStatusCode.Unauthorized to """{"message":"Conta não encontrada"}""" }

        assertIs<OwnAuthException.NotAuthenticated>(api.me("tok").exceptionOrNull())
    }
}
