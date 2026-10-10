package br.com.codecacto.kmplib.platform

import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(KmpLibPlatformInternalApi::class)
class KmpLibTempFilesTest {

    private lateinit var cache: File

    /** `Context` mínimo: só o `cacheDir`, que é tudo o que a limpeza lê. */
    private inner class FakeContext : ContextWrapper(null) {
        override fun getCacheDir(): File = cache
    }

    @BeforeTest
    fun setUp() {
        cache = Files.createTempDirectory("kmplib-temp-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        cache.deleteRecursively()
    }

    private fun arquivo(dir: String, nome: String, idadeMillis: Long = 0L): File =
        File(cache, dir).apply { mkdirs() }.resolve(nome).apply {
            writeText("dado clínico")
            setLastModified(System.currentTimeMillis() - idadeMillis)
        }

    @Test
    fun `create marca em uso e release desmarca e apaga`() {
        val dir = File(cache, PDF_VIEWER_TEMP_DIRECTORY)
        val f = KmpLibTempFiles.create(dir, "pdf_", ".pdf")
        assertTrue(f.exists())
        assertTrue(KmpLibTempFiles.isInUse(f))
        KmpLibTempFiles.release(f)
        assertFalse(KmpLibTempFiles.isInUse(f))
        assertFalse(f.exists())
        KmpLibTempFiles.release(f) // idempotente
    }

    @Test
    fun `release sem apagar so desmarca`() {
        val f = KmpLibTempFiles.create(File(cache, PRINT_TEMP_DIRECTORY), "print-", ".pdf")
        KmpLibTempFiles.release(f, delete = false)
        assertTrue(f.exists())
        assertFalse(KmpLibTempFiles.isInUse(f))
    }

    @Test
    fun `purge com zero apaga as sobras e preserva o que esta em uso`() {
        val dir = File(cache, PDF_VIEWER_TEMP_DIRECTORY)
        val sobra = arquivo(PDF_VIEWER_TEMP_DIRECTORY, "sobra.pdf")
        val emUso = KmpLibTempFiles.create(dir, "uso_", ".pdf")
        File(dir, "subpasta").mkdirs()
        assertEquals(1, KmpLibTempFiles.purge(dir, olderThanMillis = 0L))
        assertFalse(sobra.exists())
        assertTrue(emUso.exists(), "o arquivo de uma abertura em curso não é vítima da limpeza")
        assertTrue(File(dir, "subpasta").exists(), "só arquivos")
        KmpLibTempFiles.release(emUso)
    }

    @Test
    fun `purge por idade so apaga o velho`() {
        val dir = File(cache, PRINT_TEMP_DIRECTORY)
        val velho = arquivo(PRINT_TEMP_DIRECTORY, "velho.pdf", idadeMillis = 2 * 60 * 60 * 1000L)
        val novo = arquivo(PRINT_TEMP_DIRECTORY, "novo.pdf")
        assertEquals(1, KmpLibTempFiles.purge(dir, olderThanMillis = DEFAULT_SHARED_FILE_TTL_MILLIS))
        assertFalse(velho.exists())
        assertTrue(novo.exists())
    }

    @Test
    fun `purge de pasta inexistente devolve zero`() {
        assertEquals(0, KmpLibTempFiles.purge(File(cache, "nao-existe"), 0L))
    }

    @Test
    fun `clearKmpLibTemporaryFiles limpa compartilhamento PDF e impressao`() {
        val compartilhado = arquivo(SHARED_FILES_DIRECTORY, "laudo.pdf")
        val pdf = arquivo(PDF_VIEWER_TEMP_DIRECTORY, "kmplib_pdfviewer_1.pdf")
        val impressao = arquivo(PRINT_TEMP_DIRECTORY, "print-1.pdf")
        val alheio = arquivo("outra_pasta", "do-app.bin")

        assertEquals(3, clearKmpLibTemporaryFiles(FakeContext(), olderThanMillis = 0L))
        assertFalse(compartilhado.exists())
        assertFalse(pdf.exists())
        assertFalse(impressao.exists())
        assertTrue(alheio.exists(), "só as pastas da lib")
    }

    @Test
    fun `clearKmpLibTemporaryFiles default respeita a idade`() {
        val novo = arquivo(PDF_VIEWER_TEMP_DIRECTORY, "novo.pdf")
        val velho = arquivo(PRINT_TEMP_DIRECTORY, "velho.pdf", idadeMillis = 2 * 60 * 60 * 1000L)
        assertEquals(1, clearKmpLibTemporaryFiles(FakeContext()))
        assertTrue(novo.exists())
        assertFalse(velho.exists())
    }

    @Test
    fun `clearKmpLibTemporaryFiles limpa a nota de voz gravada e a baixada`() {
        val gravada = arquivo(AUDIO_CAPTURE_TEMP_DIRECTORY, "nota-1.m4a")
        val baixada = arquivo(AUDIO_PLAYBACK_TEMP_DIRECTORY, "abc.m4a")
        assertEquals(2, clearKmpLibTemporaryFiles(FakeContext(), olderThanMillis = 0L))
        assertFalse(gravada.exists())
        assertFalse(baixada.exists())
    }
}
