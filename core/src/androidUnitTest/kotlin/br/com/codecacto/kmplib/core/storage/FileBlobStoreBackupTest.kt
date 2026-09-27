package br.com.codecacto.kmplib.core.storage

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Troca de diretório de backup sem perder a fila** (2.217.0).
 *
 * O default passou a ser `noBackupFilesDir`. Quem atualizou a lib com fotos pendentes em
 * `filesDir/kmplib_uploads` precisa encontrá-las — agora no diretório fora do backup — na primeira
 * operação, sem nenhuma chamada extra do app.
 */
class FileBlobStoreBackupTest {

    private val raiz: File = createTempDirectory("kmplib-blob").toFile()
    private val comBackup = File(raiz, "files/kmplib_uploads")
    private val semBackup = File(raiz, "no_backup/kmplib_uploads")

    @AfterTest
    fun limpar() {
        raiz.deleteRecursively()
    }

    private fun legado(nome: String, bytes: ByteArray) {
        comBackup.mkdirs()
        File(comBackup, nome).writeBytes(bytes)
    }

    @Test
    fun `blob gravado pela versao anterior e movido para fora do backup na primeira leitura`() = runTest {
        legado("up-1-0", byteArrayOf(1, 2, 3))
        val store = FileBlobStore(directory = semBackup, legacyDirectory = comBackup)

        assertContentEquals(byteArrayOf(1, 2, 3), store.read("up-1-0"))
        assertTrue(File(semBackup, "up-1-0").isFile)
        assertFalse(File(comBackup, "up-1-0").exists(), "a cópia no diretório com backup tem de sair")
        assertFalse(comBackup.exists(), "diretório antigo vazio sai junto")
    }

    @Test
    fun `listagem ja enxerga o que veio do diretorio antigo`() = runTest {
        legado("a", byteArrayOf(1))
        legado("b", byteArrayOf(2))
        val store = FileBlobStore(directory = semBackup, legacyDirectory = comBackup)

        assertEquals(setOf("a", "b"), store.ids().toSet())
    }

    @Test
    fun `temporario de escrita interrompida nao e adotado`() = runTest {
        legado("~a", byteArrayOf(9))
        val store = FileBlobStore(directory = semBackup, legacyDirectory = comBackup)

        assertEquals(emptyList(), store.ids())
        assertFalse(File(semBackup, "~a").exists())
    }

    @Test
    fun `quando os dois existem vale o do diretorio novo e o antigo sai`() = runTest {
        legado("a", byteArrayOf(1))
        semBackup.mkdirs()
        File(semBackup, "a").writeBytes(byteArrayOf(2))
        val store = FileBlobStore(directory = semBackup, legacyDirectory = comBackup)

        assertContentEquals(byteArrayOf(2), store.read("a"))
        assertFalse(File(comBackup, "a").exists())
    }

    @Test
    fun `sem diretorio antigo o store funciona normalmente`() = runTest {
        val store = FileBlobStore(directory = semBackup, legacyDirectory = comBackup)

        assertTrue(store.write("x", byteArrayOf(5)))
        assertContentEquals(byteArrayOf(5), store.read("x"))
        assertFalse(comBackup.exists())
    }
}
