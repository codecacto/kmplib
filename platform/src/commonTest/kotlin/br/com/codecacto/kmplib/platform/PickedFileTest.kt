package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.audience.withParentalGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [PickedFile] (seletor de vários arquivos) e [FileOpener] — 2.311.0. */
class PickedFileTest {

    private fun arquivo(tamanho: Long, onRead: (Long) -> Unit = {}) =
        PickedFile(name = "cnh-joao-silva.pdf", mimeType = "application/pdf", sizeBytes = tamanho) { max ->
            onRead(max)
            FilePickResult.Picked(FileData("cnh-joao-silva.pdf", "application/pdf", ByteArray(3), 3))
        }

    @Test
    fun `exceeds so com tamanho conhecido acima do teto`() {
        assertTrue(arquivo(21L * 1024 * 1024).exceeds(20L * 1024 * 1024))
        assertFalse(arquivo(20L * 1024 * 1024).exceeds(20L * 1024 * 1024), "no limite cabe")
        assertFalse(arquivo(-1L).exceeds(1L), "tamanho desconhecido: quem decide é a leitura com teto")
    }

    @Test
    fun `teto invalido cai no default em exceeds e em read`() = runTest {
        assertTrue(arquivo(DEFAULT_MAX_FILE_BYTES + 1).exceeds(0L))
        var recebido = -1L
        arquivo(10L) { recebido = it }.read(maxBytes = -5L)
        assertEquals(DEFAULT_MAX_FILE_BYTES, recebido)
        arquivo(10L) { recebido = it }.read(maxBytes = 1234L)
        assertEquals(1234L, recebido)
    }

    @Test
    fun `toString nao expoe o nome do arquivo`() {
        val texto = arquivo(10L).toString()
        assertFalse(texto.contains("joao"), texto)
        assertTrue(texto.contains("application/pdf"))
    }

    @Test
    fun `extensao vem do nome`() {
        assertEquals("pdf", arquivo(1L).extension)
        assertEquals("", PickedFile("LEIAME", "text/plain", 1L) { FilePickResult.Cancelled }.extension)
    }

    @Test
    fun `mime de abertura normalizado`() {
        assertEquals("application/pdf", normalizeOpenMimeType("Application/PDF; charset=binary"))
        assertEquals("application/octet-stream", normalizeOpenMimeType(""))
        assertEquals("application/octet-stream", normalizeOpenMimeType("pdf"))
        assertEquals("application/octet-stream", normalizeOpenMimeType("image/"))
        assertEquals("image/jpeg", normalizeOpenMimeType(" image/jpeg "))
    }

    @Test
    fun `fora do modo infantil o portao repassa o desfecho e e idempotente`() {
        val chamadas = mutableListOf<String>()
        val base = object : FileOpener {
            override fun open(fileBytes: ByteArray, fileName: String, mimeType: String): FileOpenResult {
                chamadas += mimeType
                return FileOpenResult.NoAppAvailable
            }
        }
        val gated = base.withParentalGate()
        assertSame(gated, gated.withParentalGate())
        assertEquals(FileOpenResult.NoAppAvailable, gated.open(ByteArray(1), "a.bin", "application/x"))
        assertEquals(listOf("application/x"), chamadas)
    }
}
