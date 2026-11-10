package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.auth.desktop.DesktopKeyProtector
import br.com.codecacto.kmplib.auth.social.SocialBrowserException
import br.com.codecacto.kmplib.auth.social.SocialBrowserLogin
import br.com.codecacto.kmplib.auth.social.provedoresSociaisDaPlataforma
import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.context.DesktopAppInfo
import br.com.codecacto.kmplib.core.initKmpLibCore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O own-auth inteiro no alvo `jvm` (2.353.0): login → sessão no cofre cifrado do desktop → um
 * processo novo restaura a sessão do disco → logout apaga. Servidor de mentira (MockEngine).
 */
class OwnAuthDesktopTest {

    private val dir = Files.createTempDirectory("kmplib-ownauth-desktop")

    @AfterTest
    fun limpar() {
        dir.toFile().deleteRecursively()
    }

    private object XorProtector : DesktopKeyProtector {
        override val id = "xor"
        override fun protect(secret: ByteArray) = secret.map { (it.toInt() xor 0x33).toByte() }.toByteArray()
        override fun unprotect(blob: ByteArray) = protect(blob)
    }

    private val responder: (String, Int) -> Pair<HttpStatusCode, String> = { path, _ ->
        when {
            path.endsWith("/login") -> HttpStatusCode.OK to tokensJson(fakeJwt("usuario-1"), "r1", 3600)
            path.endsWith("/me") -> HttpStatusCode.OK to """{"id":"usuario-1","email":"ana@codecacto.com.br","name":"Ana","username":"ana"}"""
            path.endsWith("/logout") -> HttpStatusCode.NoContent to ""
            else -> HttpStatusCode.NotFound to ""
        }
    }

    private fun montar(captured: MutableList<CapturedRequest> = mutableListOf()): OwnAuth =
        ownAuth(
            OwnAuthConfig(mockHttpClient(captured, responder), baseUrl = "https://api.example.com", authBasePath = "/v1/auth"),
            storage = desktopSecureTokenStorage(directory = dir, keyProtector = XorProtector),
        )

    @Test
    fun login_persiste_cifrado_e_um_processo_novo_restaura() = runTest {
        val auth = montar()
        val user = auth.repository.signInWithEmail("ana", "12345678").getOrThrow()
        assertEquals("usuario-1", user.id)

        val vault = Files.list(dir).use { s -> s.filter { it.toString().endsWith(".vault") }.findFirst().orElse(null) }
        assertNotNull(vault, "a sessão foi ao disco")
        assertTrue(!String(Files.readAllBytes(vault), Charsets.ISO_8859_1).contains("r1"), "refresh token não fica em claro")

        val restaurada = montar().restore()
        assertNotNull(restaurada)
        assertEquals("r1", restaurada.refreshToken)
    }

    @Test
    fun logout_apaga_a_sessao_do_disco() = runTest {
        val auth = montar()
        auth.repository.signInWithEmail("ana", "12345678").getOrThrow()
        auth.repository.signOut()
        assertNull(montar().restore())
    }

    @Test
    fun cofre_padrao_usa_a_pasta_de_dados_do_app() = runTest {
        initKmpLibCore(DesktopAppInfo(appId = "kmplib-teste", dataDirectory = dir.resolve("app")))
        try {
            val s = desktopSecureTokenStorage(keyProtector = XorProtector)
            s.putString("k", "v")
            assertTrue(Files.isRegularFile(dir.resolve("app").resolve(DESKTOP_SECURE_DIRECTORY).resolve("secure_kmplib_ownauth.vault")))
            assertEquals("v", desktopSecureTokenStorage(keyProtector = XorProtector).getString("k"))
        } finally {
            DesktopAppContext.init(DesktopAppInfo(appId = "kmplib-teste-fim", dataDirectory = dir.resolve("fim")))
        }
    }

    @Test
    fun social_no_desktop_e_recusado_e_some_da_tela() = runTest {
        assertTrue(provedoresSociaisDaPlataforma.isEmpty())
        val e = assertFailsWith<SocialBrowserException> { SocialBrowserLogin().authenticate("https://x", "app") }
        assertEquals("unsupported", e.reason)
    }
}
