package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Teto do seletor de arquivo. O defeito que isto fecha: a lib lia o arquivo inteiro para a memória
 * **antes** de qualquer limite, e escolher um arquivo de centenas de MB derrubava o app (o
 * `catch (Exception)` não pega `OutOfMemoryError`).
 */
class FilePickLimitTest {

    @Test
    fun `teto invalido cai no default em vez de virar sem limite`() {
        // "Sem limite" não é opção: era exatamente o comportamento que derrubava o app.
        assertEquals(DEFAULT_MAX_FILE_BYTES, resolveMaxFileBytes(0L))
        assertEquals(DEFAULT_MAX_FILE_BYTES, resolveMaxFileBytes(-1L))
        assertEquals(DEFAULT_MAX_FILE_BYTES, resolveMaxFileBytes(Long.MIN_VALUE))
    }

    @Test
    fun `teto informado e respeitado`() {
        assertEquals(2048L, resolveMaxFileBytes(2048L))
    }

    @Test
    fun `tamanho conhecido acima do teto e recusado`() {
        assertTrue(exceedsFilePickLimit(sizeBytes = 1_001L, maxBytes = 1_000L))
    }

    @Test
    fun `tamanho exatamente no teto e aceito`() {
        assertFalse(exceedsFilePickLimit(sizeBytes = 1_000L, maxBytes = 1_000L))
    }

    @Test
    fun `tamanho desconhecido nao e recusado de antemao`() {
        // Provedor que não informa tamanho: quem decide é a leitura COM teto, não um palpite.
        assertFalse(exceedsFilePickLimit(sizeBytes = -1L, maxBytes = 1_000L))
    }

    @Test
    fun `leitura dentro do teto devolve os bytes exatos`() {
        val acumulador = BoundedByteAccumulator(limit = 1_000L)
        val bloco = ByteArray(10) { it.toByte() }
        repeat(5) { assertTrue(acumulador.append(bloco, bloco.size)) }

        assertFalse(acumulador.exceeded)
        assertEquals(50, acumulador.bytesRead)
        assertEquals(50, acumulador.toByteArray().size)
        assertEquals(0, acumulador.toByteArray()[0])
        assertEquals(9, acumulador.toByteArray()[9])
    }

    @Test
    fun `leitura para de copiar assim que estoura o teto`() {
        val acumulador = BoundedByteAccumulator(limit = 25L)
        val bloco = ByteArray(10) { 7 }

        assertTrue(acumulador.append(bloco, bloco.size))
        assertTrue(acumulador.append(bloco, bloco.size))
        // O terceiro bloco levaria a 30 > 25: aborta sem materializar.
        assertFalse(acumulador.append(bloco, bloco.size))

        assertTrue(acumulador.exceeded)
        // Não copiou nada além do que já estava — o buffer nunca cresce além do teto.
        assertEquals(20, acumulador.bytesRead)
    }

    @Test
    fun `apos estourar nao volta a aceitar`() {
        val acumulador = BoundedByteAccumulator(limit = 5L)
        assertFalse(acumulador.append(ByteArray(10), 10))
        assertFalse(acumulador.append(ByteArray(1), 1))
        assertTrue(acumulador.exceeded)
        assertEquals(0, acumulador.bytesRead)
    }

    @Test
    fun `bloco parcial respeita a contagem informada`() {
        // InputStream.read devolve quantos bytes leu, que pode ser menor que o array.
        val acumulador = BoundedByteAccumulator(limit = 100L)
        val bloco = ByteArray(64) { 3 }
        assertTrue(acumulador.append(bloco, count = 4))
        assertEquals(4, acumulador.bytesRead)
        assertEquals(4, acumulador.toByteArray().size)
    }

    @Test
    fun `fim de stream nao altera nada`() {
        val acumulador = BoundedByteAccumulator(limit = 100L)
        assertTrue(acumulador.append(ByteArray(8), 8))
        assertTrue(acumulador.append(ByteArray(8), 0))
        assertTrue(acumulador.append(ByteArray(8), -1))
        assertEquals(8, acumulador.bytesRead)
        assertFalse(acumulador.exceeded)
    }

    @Test
    fun `arquivo grande com teto grande cresce sem estourar`() {
        // Cresce o buffer várias vezes (bloco de 64 KiB, 300 KiB no total).
        val acumulador = BoundedByteAccumulator(limit = 1024L * 1024L)
        val bloco = ByteArray(64 * 1024) { 1 }
        repeat(4) { assertTrue(acumulador.append(bloco, bloco.size)) }
        assertFalse(acumulador.exceeded)
        assertEquals(4 * 64 * 1024, acumulador.bytesRead)
        assertTrue(acumulador.toByteArray().all { it == 1.toByte() })
    }

    @Test
    fun `desfechos sao distinguiveis - cancelado nao e erro`() {
        val cancelado: FilePickResult = FilePickResult.Cancelled
        val grande: FilePickResult = FilePickResult.TooLarge("v.mp4", 900_000_000L, DEFAULT_MAX_FILE_BYTES)
        val falhou: FilePickResult = FilePickResult.Failed("v.mp4", FilePickFailure.Unreadable)

        // O contrato antigo (FileData?) juntava os três num `null`, e a tela não tinha como
        // diferenciar "desistiu" de "não caberia" nem de "não deu para ler".
        assertTrue(cancelado is FilePickResult.Cancelled)
        assertTrue(grande is FilePickResult.TooLarge && grande.maxBytes == DEFAULT_MAX_FILE_BYTES)
        assertTrue(falhou is FilePickResult.Failed && falhou.reason == FilePickFailure.Unreadable)
    }
}
