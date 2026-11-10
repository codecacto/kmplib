package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.auth.desktop.DesktopKeyProtector
import br.com.codecacto.kmplib.auth.desktop.SecureStorageUnavailableException
import br.com.codecacto.kmplib.auth.desktop.WindowsDpapiKeyProtector
import br.com.codecacto.kmplib.auth.desktop.defaultDesktopKeyProtector
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O cofre de tokens do desktop (2.353.0): cifra, durabilidade, descarte do irrecuperável e falha
 * fechada. A DPAPI de verdade só existe no Windows; aqui o protetor é um dublê REVERSÍVEL que muda
 * os bytes (prova que a chave nunca vai em claro ao arquivo).
 */
class DesktopSecureTokenStorageTest {

    private val dir: Path = Files.createTempDirectory("kmplib-cofre")

    @AfterTest
    fun limpar() {
        dir.toFile().deleteRecursively()
    }

    /** Protetor de teste: XOR com uma máscara + contadores; [failUnprotect] simula falha do SO. */
    private class FakeProtector(override val id: String = "fake") : DesktopKeyProtector {
        var protects = 0
        var unprotects = 0
        var failUnprotect = 0
        var unavailable = false
        override fun protect(secret: ByteArray): ByteArray {
            if (unavailable) throw SecureStorageUnavailableException("sem cofre")
            protects++
            return secret.map { (it.toInt() xor 0x5A).toByte() }.toByteArray() + byteArrayOf(7, 7)
        }
        override fun unprotect(blob: ByteArray): ByteArray {
            if (unavailable) throw SecureStorageUnavailableException("sem cofre")
            unprotects++
            if (failUnprotect > 0) {
                failUnprotect--
                throw RuntimeException("falha passageira do SO")
            }
            return blob.copyOf(blob.size - 2).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private fun storage(protector: DesktopKeyProtector?, service: String = "kmplib.ownauth") =
        DesktopSecureTokenStorage(service, { dir }, protector, retryBackoffMillis = 1)

    private val segredo = "refresh-token-SUPER-secreto-123"

    @Test
    fun grava_le_e_sobrevive_a_um_processo_novo() = runTest {
        val p = FakeProtector()
        storage(p).putString("own_auth_session", segredo)
        assertEquals(segredo, storage(p).getString("own_auth_session"))
        assertNull(storage(p).getString("outra"))
    }

    @Test
    fun nada_em_claro_no_disco_nem_a_chave() = runTest {
        val p = FakeProtector()
        storage(p).putString("own_auth_session", segredo)
        val arquivos = dir.listDirectoryEntries().filter { Files.isRegularFile(it) }
        assertTrue(arquivos.any { it.name == "secure_kmplib_ownauth.vault" })
        assertTrue(arquivos.any { it.name == "secure_kmplib_ownauth.key" })
        for (f in arquivos) {
            val conteudo = String(Files.readAllBytes(f), Charsets.ISO_8859_1)
            assertFalse(conteudo.contains(segredo), "segredo em claro em ${f.name}")
            assertFalse(conteudo.contains("own_auth_session"), "nome da chave em claro em ${f.name}")
        }
        assertEquals(1, p.protects, "a chave AES passou pelo protetor do SO")
    }

    @Test
    fun cada_gravacao_usa_iv_novo() = runTest {
        val p = FakeProtector()
        val s = storage(p)
        s.putString("k", segredo)
        val a = Files.readAllBytes(dir.resolve("secure_kmplib_ownauth.vault"))
        s.putString("k", segredo)
        val b = Files.readAllBytes(dir.resolve("secure_kmplib_ownauth.vault"))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun remove_e_clear_sao_duraveis() = runTest {
        val p = FakeProtector()
        val s = storage(p)
        s.putString("a", "1")
        s.putString("b", "2")
        s.remove("a")
        assertNull(storage(p).getString("a"))
        assertEquals("2", storage(p).getString("b"))
        s.remove("b")
        assertFalse(Files.exists(dir.resolve("secure_kmplib_ownauth.vault")), "cofre vazio some do disco")
        s.putString("c", "3")
        s.clear()
        assertNull(storage(p).getString("c"))
        s.remove("nao-existe") // idempotente
        s.clear()
    }

    @Test
    fun cofre_adulterado_e_descartado_e_a_proxima_gravacao_funciona() = runTest {
        val p = FakeProtector()
        storage(p).putString("k", segredo)
        val vault = dir.resolve("secure_kmplib_ownauth.vault")
        val bytes = Files.readAllBytes(vault)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        Files.write(vault, bytes)
        val s = storage(p)
        assertNull(s.getString("k"))
        assertFalse(Files.exists(vault))
        s.putString("k", "novo")
        assertEquals("novo", storage(p).getString("k"))
    }

    @Test
    fun arquivo_de_outro_servico_nao_abre() = runTest {
        val p = FakeProtector()
        storage(p, "servico.a").putString("k", segredo)
        // Trocar o cofre de serviço (mesma chave) reprova a tag GCM: o nome do serviço é dado autenticado.
        Files.copy(dir.resolve("secure_servico_a.vault"), dir.resolve("secure_servico_b.vault"))
        Files.copy(dir.resolve("secure_servico_a.key"), dir.resolve("secure_servico_b.key"))
        assertNull(storage(p, "servico.b").getString("k"))
    }

    @Test
    fun falha_passageira_do_so_nao_apaga_a_sessao() = runTest {
        val p = FakeProtector()
        storage(p).putString("k", segredo)
        val p2 = FakeProtector().apply { failUnprotect = DesktopSecureTokenStorage.OPEN_ATTEMPTS - 1 }
        assertEquals(segredo, storage(p2).getString("k"))
    }

    @Test
    fun chave_que_o_so_nunca_mais_abre_e_descartada_sem_crash() = runTest {
        val p = FakeProtector()
        storage(p).putString("k", segredo)
        val p2 = FakeProtector().apply { failUnprotect = 100 }
        assertNull(storage(p2).getString("k"))
        assertFalse(Files.exists(dir.resolve("secure_kmplib_ownauth.vault")))
    }

    @Test
    fun outro_protetor_nao_abre_a_chave() = runTest {
        storage(FakeProtector("dpapi-user")).putString("k", segredo)
        assertNull(storage(FakeProtector("dpapi-machine")).getString("k"))
    }

    @Test
    fun sem_protetor_o_cofre_fecha() = runTest {
        val s = storage(null)
        assertFailsWith<SecureStorageUnavailableException> { s.putString("k", segredo) }
        assertNull(s.getString("k"))
        s.remove("k")
        s.clear()
        assertTrue(dir.listDirectoryEntries().none { it.name.endsWith(".vault") || it.name.endsWith(".key") })
    }

    @Test
    fun protetor_indisponivel_fecha_sem_gravar_e_sem_apagar() = runTest {
        val p = FakeProtector()
        storage(p).putString("k", segredo)
        val p2 = FakeProtector().apply { unavailable = true }
        val s = storage(p2)
        assertNull(s.getString("k"), "leitura é best-effort")
        assertFailsWith<SecureStorageUnavailableException> { s.putString("k", "x") }
        assertEquals(segredo, storage(p).getString("k"), "indisponível NÃO é corrupção: o cofre fica")
    }

    @Test
    fun gravacao_que_nao_chega_ao_disco_lanca() = runTest {
        // A "pasta" do cofre é um ARQUIVO: nada pode ser gravado — putString tem de lançar.
        val bloqueio = Files.createTempFile("kmplib-nao-e-pasta", ".txt")
        try {
            val s = DesktopSecureTokenStorage("svc", { bloqueio }, FakeProtector(), 1)
            assertFailsWith<Exception> { s.putString("k", segredo) }
        } finally {
            Files.deleteIfExists(bloqueio)
        }
    }

    @Test
    fun formato_do_arquivo_de_chave() {
        val arquivo = DesktopSecureTokenStorage.buildKeyFile("dpapi-user", byteArrayOf(1, 2, 3))
        val (id, blob) = DesktopSecureTokenStorage.parseKeyFile(arquivo)
        assertEquals("dpapi-user", id)
        assertContentEquals(byteArrayOf(1, 2, 3), blob)
        assertFailsWith<GeneralSecurityException> { DesktopSecureTokenStorage.parseKeyFile(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun protetor_padrao_por_sistema() {
        assertIs<WindowsDpapiKeyProtector>(defaultDesktopKeyProtector("Windows 11"))
        assertIs<WindowsDpapiKeyProtector>(defaultDesktopKeyProtector("Windows Server 2022"))
        assertNull(defaultDesktopKeyProtector("Mac OS X"))
        assertNull(defaultDesktopKeyProtector("Linux"))
    }

    @Test
    fun dpapi_fora_do_windows_falha_fechado() {
        if (System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")) return
        // Sem Crypt32, a DPAPI não existe: tem de virar SecureStorageUnavailableException, nunca gravar em claro.
        assertFailsWith<Throwable> { WindowsDpapiKeyProtector().protect(byteArrayOf(1)) }
    }
}
